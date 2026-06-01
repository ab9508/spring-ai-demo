package com.example.ai.aspect;

import com.example.ai.dao.LLMCallLogDao;
import com.example.ai.entity.LLMCallLog;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 调用日志切面
 * 拦截 ChatModel.call() —— 所有 ChatClient 调用的底层入口，
 * 记录完整的输入输出、耗时、token 消耗，异步写入数据库。
 * <p>
 * 数据流向：
 * 用户请求 → ChatClient → [AOP拦截] → LLM → [AOP拦截] → 异步写DB
 * 不影响主流程，日志写入失败不打乱业务。
 */
@Slf4j
@Aspect
@Component
public class LLMCallLoggerAspect {

    private final LLMCallLogDao llmCallLogDao;
    private final ConcurrentHashMap<Integer, Long> recentCalls = new ConcurrentHashMap<>();

    public LLMCallLoggerAspect(LLMCallLogDao llmCallLogDao) {
        this.llmCallLogDao = llmCallLogDao;
    }

    /**
     * 拦截所有 ChatModel.call(Prompt) 调用
     * 这是所有 ChatClient.request().call() 最终都会走到的底层方法
     * 注意：Spring CGLIB 代理会导致同一个调用被拦截两次（代理类+实际类），
     * 通过 Prompt identity 去重。
     */
    @Around("execution(* org.springframework.ai.chat.model.ChatModel.call(org.springframework.ai.chat.prompt.Prompt))")
    public Object logChatModelCall(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        boolean success = true;
        String errorMsg = null;
        Prompt prompt = null;
        String modelName = "unknown";

        // 提取参数
        Object[] args = pjp.getArgs();
        if (args != null && args.length > 0 && args[0] instanceof Prompt p) {
            prompt = p;
        }

        // CGLIB 去重：同一个 Prompt 对象在 100ms 内只记一次
        if (prompt != null) {
            int hash = System.identityHashCode(prompt);
            long now = System.currentTimeMillis();
            Long lastTime = recentCalls.get(hash);
            if (lastTime != null && (now - lastTime) < 100) {
                // 重复调用（CGLIB 代理二次拦截），跳过日志
                return pjp.proceed();
            }
            recentCalls.put(hash, now);
            // 清理过期条目（防止内存泄漏）
            if (recentCalls.size() > 500) {
                recentCalls.entrySet().removeIf(e -> (now - e.getValue()) > 5000);
            }
        }

        // 获取模型名称（从pjp.getTarget()获取实现类名）
        Object target = pjp.getTarget();
        if (target != null) {
            modelName = target.getClass().getSimpleName();
        }

        Object result = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Exception e) {
            success = false;
            errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
            throw e;
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            // 异步记录日志
            saveLogAsync(prompt, result, modelName, (int) elapsed, success, errorMsg);
        }
    }

    @Async("logExecutor")
    protected void saveLogAsync(Prompt prompt, Object result,
                                String modelName, int latencyMs,
                                boolean success, String errorMsg) {
        try {
            if (prompt == null) return;

            LLMCallLog.LLMCallLogBuilder builder = LLMCallLog.builder()
                    .id(UUID.randomUUID().toString())
                    .timestamp(OffsetDateTime.now())
                    .model(modelName)
                    .latencyMs(latencyMs)
                    .success(success)
                    .errorMsg(errorMsg);

            // 提取 System / User 消息
            StringBuilder questionBuilder = new StringBuilder();
            StringBuilder systemBuilder = new StringBuilder();
            if (prompt.getInstructions() != null) {
                for (var msg : prompt.getInstructions()) {
                    String content = msg.getText();
                    if (content == null) continue;
                    if (msg.getMessageType() == MessageType.SYSTEM
                            || msg.getMessageType() == MessageType.SYSTEM) {
                        systemBuilder.append(content).append("\n");
                    } else {
                        questionBuilder.append(content).append("\n");
                    }
                }
            }
            builder.question(truncate(questionBuilder.toString(), 2000));
            builder.systemPrompt(truncate(systemBuilder.toString(), 3000));

            // 提取响应和 tokens
            if (result instanceof ChatResponse chatResponse) {
                var genResult = chatResponse.getResult();
                if (genResult != null && genResult.getOutput() != null) {
                    builder.response(truncate(genResult.getOutput().getText(), 5000));
                }
                var metadata = chatResponse.getMetadata();
                if (metadata != null && metadata.getUsage() != null) {
                    var usage = metadata.getUsage();
                    builder.tokenInput(usage.getPromptTokens() != null ? usage.getPromptTokens() : 0);
                    builder.tokenOutput(usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0);
                }
            } else if (result instanceof String text) {
                builder.response(truncate(text, 5000));
            }

            // 简化费用估算
            int totalTokens = (builder.build().getTokenInput() != null ? builder.build().getTokenInput() : 0)
                    + (builder.build().getTokenOutput() != null ? builder.build().getTokenOutput() : 0);
            builder.cost(BigDecimal.valueOf(totalTokens * 0.001 / 1000.0));

            llmCallLogDao.insert(builder.build());

        } catch (Exception e) {
            log.debug("LLM日志异步写入异常(不影响主流程): {}", e.getMessage());
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
