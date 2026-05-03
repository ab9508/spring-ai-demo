package com.example.ai.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 带摘要压缩的 ChatMemory
 * 当对话历史超过 maxMessages 时，把前半段消息用 LLM 压缩成摘要
 */
@Slf4j
public class SummarizingChatMemory implements ChatMemory {

    // 最大消息数，超过触发压缩
    private static final int MAX_MESSAGES = 20;
    // 保留最近消息数
    private static final int KEEP_RECENT = 10;

    // 底层 Redis 存储（复用已有的 RedisChatMemoryRepository）
    private final ChatMemory delegate;
    // 纯净 ChatClient（无工具，专门用于 LLM 压缩）
    private final ChatClient summaryClient;

    public SummarizingChatMemory(ChatMemory delegate, ChatClient summaryClient) {
        this.delegate = delegate;
        this.summaryClient = summaryClient;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        // 先写入底层存储
        delegate.add(conversationId, messages);
        // 写入后检查是否需要压缩
        triggerCompressionIfNeeded(conversationId);
    }

    /**
     * ChatMemory 接口 get() 只接收 conversationId，不接收 lastN。
     * 消息条数限制由 MessageWindowChatMemory（底层 delegate）在 saveAll 时已裁剪好。
     */
    @Override
    public List<Message> get(String conversationId) {
        return delegate.get(conversationId);
    }

    @Override
    public void clear(String conversationId) {
        delegate.clear(conversationId);
    }

    /**
     * 触发压缩：超过 MAX_MESSAGES 时，把前半段消息压缩为摘要
     * 必须同步执行，确保 LLM 读取历史前已完成压缩
     */
    private void triggerCompressionIfNeeded(String conversationId) {
        log.info("[ChatMemory] conversationId={}", conversationId);
        // 取全部消息（不限制条数）
        List<Message> all = delegate.get(conversationId);

        if (all.size() <= MAX_MESSAGES) {
            return; // 未超过阈值，不压缩
        }

        log.info("[ChatMemory] conversationId={} 消息数={}，触发摘要压缩", conversationId, all.size());

        // 需要压缩的旧消息（前半段）
        int toCompressCount = all.size() - KEEP_RECENT;
        List<Message> toCompress = all.subList(0, toCompressCount);
        // 需要保留的近期消息（后半段）
        List<Message> recent = all.subList(toCompressCount, all.size());

        // 用 LLM 生成摘要
        String summary = summarize(conversationId, toCompress);
        if (summary == null) {
            log.warn("[ChatMemory] 摘要生成失败，跳过压缩");
            return;
        }

        // 重建消息列表：摘要（1条 SystemMessage）+ 最近 N 条
        List<Message> compressed = new ArrayList<>();
        compressed.add(new SystemMessage("[对话摘要] " + summary));
        compressed.addAll(recent);

        // 清空 Redis 中旧的历史，写入压缩后的版本
        delegate.clear(conversationId);
        delegate.add(conversationId, compressed);

        log.info("[ChatMemory] 压缩完成：{}条 → {}条（摘要1条 + 近期{}条）",
                all.size(), compressed.size(), recent.size());
    }

    /**
     * 调用 LLM 生成对话摘要
     *
     * @param messages 需要压缩的消息列表
     * @return 摘要文本，失败返回 null
     */
    private String summarize(String conversationId, List<Message> messages) {
        // 把消息列表转为文本，方便 LLM 理解
        String historyText = messages.stream()
                .map(msg -> {
                    String role = msg.getMessageType() == MessageType.USER ? "用户" : "助手";
                    return role + "：" + msg.getText();
                })
                .collect(Collectors.joining("\n"));

        String systemPrompt = """
                你是对话摘要助手。将以下对话历史压缩为简洁的摘要，保留关键信息点。
                要求：
                1. 摘要控制在100字以内
                2. 保留用户的核心诉求、已确认的事项、重要的上下文
                3. 只输出摘要文本，不要有任何前缀或解释
                """;

        try {
            String result = summaryClient.prompt()
                    .system(systemPrompt)
                    .user("请压缩以下对话历史：\n" + historyText)
                    .call()
                    .content();
            log.info("[ChatMemory] conversationId={} 摘要结果：{}", conversationId, result);
            return result != null ? result.trim() : null;
        } catch (Exception e) {
            log.error("[ChatMemory] LLM 摘要生成失败", e);
            return null;
        }
    }
}