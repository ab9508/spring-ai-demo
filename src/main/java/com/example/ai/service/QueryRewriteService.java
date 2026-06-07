package com.example.ai.service;

import com.example.ai.config.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 查询改写服务——在 RAG 检索前，将用户的模糊/指代问题改写成独立完整的查询。
 * <p>
 * 示例：
 *   原始："那个退了"
 *   改写："查询退货流程"
 * <p>
 * 价值：改写后的查询向量化效果更好，RAG 检索质量显著提升。
 */
@Slf4j
@Service
public class QueryRewriteService {

    private final ChatClient chatClient;

    public QueryRewriteService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 改写用户问题
     *
     * @param userMessage 用户原始输入
     * @param history     最近 N 条对话历史（用于消解指代）
     * @return 改写后的独立查询，改写失败时返回原始输入
     */
    public String rewrite(String userMessage, String history) {
        // 短问题不需要改写
        if (userMessage == null || userMessage.length() < 3) {
            return userMessage;
        }

        try {
            String prompt = buildRewritePrompt(userMessage, history);
            String rewritten = chatClient.prompt()
                    .system(prompt)
                    .user(userMessage)
                    .call()
                    .content();

            if (rewritten != null && !rewritten.isBlank()
                    && !rewritten.contains("无法改写") && !rewritten.contains("无需改写")) {
                log.debug("【查询改写】'{}' → '{}'", truncate(userMessage, 30), truncate(rewritten, 50));
                return rewritten.trim();
            }
        } catch (Exception e) {
            log.warn("【查询改写】失败: {}", e.getMessage());
        }

        return userMessage;
    }

    private String buildRewritePrompt(String userMessage, String history) {
        return """
                你是一个查询改写助手。你的任务是把用户的问题改写成**独立完整**的查询语句。

                规则：
                1. 如果问题本身已经明确完整，直接返回原文
                2. 如果问题包含"它"、"那个"、"这个"等指代词，根据对话历史恢复成具体实体
                3. 如果问题不完整（如"退了吧"），补全为完整的查询意图
                4. 只返回改写后的查询文本，不要加解释、不要加引号

                对话历史：
                """
                + (history != null && !history.isBlank() ? history : "无历史记录")
                + "\n用户的新消息：";
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
