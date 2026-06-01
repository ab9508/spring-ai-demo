package com.example.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/**
 * 降级 ChatModel——优先使用本地模型，失败后自动切换到 DeepSeek API。
 * <p>
 * 降级策略：
 *   一级：尝试本地模型（Ollama/qwen2.5:3b），返回空或异常时
 *   二级：自动切换到 DeepSeek API
 *   三级：全部失败 → 返回固定兜底回复
 * <p>
 * useCase：
 *   DegradationChatModel degradationModel = new DegradationChatModel(ollamaModel, openaiModel, 10s, "busy");
 *   ChatClient.builder(degradationModel).build() → 所有调用自动带降级
 */
@Slf4j
public class DegradationChatModel implements ChatModel {

    private final ChatModel primaryModel;
    private final ChatModel fallbackModel;
    private final Duration timeout;
    private final String fallbackResponse;

    public DegradationChatModel(ChatModel primaryModel, ChatModel fallbackModel,
                                Duration timeout, String fallbackResponse) {
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
        this.timeout = timeout;
        this.fallbackResponse = fallbackResponse;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        // 一级：本地模型
        ChatResponse result = tryModel(primaryModel, prompt, "本地模型");
        if (result != null) return result;

        // 二级：DeepSeek API
        result = tryModel(fallbackModel, prompt, "DeepSeek API");
        if (result != null) return result;

        // 三级：兜底
        log.warn("【降级】三级：全部模型失败，返回兜底回复");
        return ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(fallbackResponse))))
                .build();
    }

    /**
     * 尝试调用一个模型，成功返回ChatResponse，失败返回null
     */
    private ChatResponse tryModel(ChatModel model, Prompt prompt, String modelName) {
        try {
            log.info("【降级】一级：尝试 {}", modelName);
            ChatResponse response = callWithTimeout(model, prompt);
            if (response != null && response.getResult() != null
                    && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null
                    && !response.getResult().getOutput().getText().isBlank()) {
                log.info("【降级】{} 成功", modelName);
                return response;
            }
            log.warn("【降级】{} 返回空结果", modelName);
        } catch (TimeoutException e) {
            log.warn("【降级】{} 超时 ({}s)", modelName, timeout.getSeconds());
        } catch (Exception e) {
            log.warn("【降级】{} 异常: {}", modelName, e.getMessage());
        }
        return null;
    }

    private ChatResponse callWithTimeout(ChatModel model, Prompt prompt)
            throws TimeoutException, Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ChatResponse> future = executor.submit(() -> model.call(prompt));
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new TimeoutException("超时 (" + timeout.getSeconds() + "s)");
        } catch (ExecutionException e) {
            throw (Exception) e.getCause();
        } finally {
            executor.shutdownNow();
        }
    }
}
