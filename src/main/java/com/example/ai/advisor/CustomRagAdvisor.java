package com.example.ai.advisor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 自定义 RAG Advisor
 * <p>
 * 实现 BaseAdvisor 接口（框架已默认实现 adviseCall → before → chain.nextCall → after 链路）
 * 在 before 阶段做向量检索，把相关文档片段注入 system prompt
 * 包含相对分数过滤逻辑（复用 RagService 的经验）
 * <p>
 * ============ 执行链路（框架自动编排） ============
 * 用户请求 → CustomRagAdvisor.before()（向量检索+注入上下文）
 *          → chain.nextCall()（调下一个 Advisor 或 ChatModel）
 *          → CustomRagAdvisor.after()（无需处理，直接返回）
 *          → 返回用户
 */
@Slf4j
@RequiredArgsConstructor
public class CustomRagAdvisor implements BaseAdvisor {

    private final VectorStore vectorStore;
    private final int topK;
    private final double threshold;

    /**
     * before 阶段：调 LLM 之前执行
     * <p>
     * 1. 从 request.prompt() 拿到用户消息
     * 2. 向量检索
     * 3. 相对分数过滤
     * 4. 用 prompt.augmentSystemMessage() 注入上下文
     * 5. 用 request.mutate() 构造新的 request 返回
     */
    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        log.info("[RAG Advisor]before-->");
        // 1. 从 prompt 拿到用户消息文本
        String userMessage = request.prompt().getUserMessage() != null
                ? request.prompt().getUserMessage().getText() : "";
        if (userMessage.isBlank()) {
            return request;
        }
        log.info("[RAG Advisor] 收到用户消息: {}", userMessage);

        // 2. 向量检索
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(userMessage)
                        .topK(topK)
                        .similarityThreshold(threshold)
                        .build()
        );

        if (docs == null || docs.isEmpty()) {
            log.info("[RAG Advisor] 未检索到相关文档，跳过注入");
            return request;
        }

        log.info("[RAG Advisor] 向量检索到 {} 个片段，分数: {}",
                docs.size(), docs.stream().map(Document::getScore).toList());

        // 3. 相对分数过滤
        docs = filterByRelativeScore(docs);

        if (docs.isEmpty()) {
            log.info("[RAG Advisor] 相对分数过滤后无相关文档，跳过注入");
            return request;
        }

        // 4. 拼装上下文，用 augmentSystemMessage() 追加到 system prompt 末尾
        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        log.info("[RAG Advisor] 注入 {} 个文档片段到 system prompt", docs.size());

        // 5. 构造新的 request：修改 prompt（追加 RAG 上下文），保留 context（如 CONVERSATION_ID）
        return request.mutate()
                .prompt(request.prompt().augmentSystemMessage(
                        "\n\n参考资料（仅作辅助，如果与工具查询结果冲突，以工具结果为准）：\n" + context
                ))
                .build();
    }

    /**
     * after 阶段：LLM 回复之后执行
     * RAG Advisor 不需要处理响应，直接返回
     */
    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        log.info("[RAG Advisor]after-->");
        return response;
    }

    @Override
    public int getOrder() {
        return 0; // 执行顺序，数字越小越先执行
    }

    /**
     * 相对分数过滤（复用 RagService 的实战经验）
     * <p>
     * 1. 如果 top1 < 0.45，全部拒绝（绝对值兜底）
     * 2. 如果 top1 和 top2 分差 < 0.08，且 top1 < 0.7，说明无区分度，全部拒绝
     * 3. 只保留 score > top1 * 0.85 的 chunk（与最高分相差不超过 15%）
     */
    private List<Document> filterByRelativeScore(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return Collections.emptyList();
        }

        double top1Score = docs.get(0).getScore() != null ?
                docs.get(0).getScore() : 0.0;

        // 绝对值兜底
        if (top1Score < 0.45) {
            log.info("[RAG Advisor] top1 分数 {} 过低，返回空结果", top1Score);
            return Collections.emptyList();
        }

        // 无区分度检测
        if (docs.size() > 1) {
            double top2Score = docs.get(1).getScore() != null ?
                    docs.get(1).getScore() : 0.0;
            double gap = top1Score - top2Score;
            if (gap < 0.08 && top1Score < 0.7) {
                log.info("[RAG Advisor] top1={} top2={} 差值 {} 不足，判断无明确答案",
                        top1Score, top2Score, gap);
                return Collections.emptyList();
            }
        }

        // 只保留与 top1 分差在 15% 以内的 chunk
        double minScore = top1Score * 0.85;
        return docs.stream()
                .filter(d -> d.getScore() != null && d.getScore() >= minScore)
                .collect(Collectors.toList());
    }
}
