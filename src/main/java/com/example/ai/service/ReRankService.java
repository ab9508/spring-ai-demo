package com.example.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 重排序服务——对向量检索的 Top-K 结果进行二次排序，提升相关性。
 * <p>
 * 向量检索的 cosine 分数只是 粗略相关度，
 * 重排序让 LLM 或更精确的逻辑判断"哪个文档和问题最相关"。
 * <p>
 * 流程：向量检索 Top-10 → 重排序取 Top-3
 */
@Slf4j
@Service
public class ReRankService {

    private static final int RERANK_TOP_K = 3;

    /**
     * 基于分数的重排序（轻量级，不调 LLM）
     * 策略：去掉低分 + 去重 + 按分数排序
     *
     * @param documents 原始检索结果
     * @param minScore  最低保留分数
     * @return 重排序后的结果
     */
    public List<Document> reRankByScore(List<Document> documents, double minScore) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }

        // 1. 过滤低分
        // 2. 按 content 去重（跳过内容相同的切片）
        // 3. 按分数降序排列
        // 4. 取 Top-K
        return documents.stream()
                .filter(d -> d.getScore() != null && d.getScore() >= minScore)
                .sorted(Comparator.comparing(Document::getScore, Comparator.reverseOrder()))
                .distinct()
                .limit(RERANK_TOP_K)
                .collect(Collectors.toList());
    }

    /**
     * 基于 LLM 的重排序（精确但慢）
     * 让 LLM 判断每个文档与问题的相关度，返回排序后的结果
     *
     * @param question  用户问题
     * @param documents 待排序的文档
     * @param chatClient  用于评分的 ChatClient
     * @return 按相关性排序的文档列表
     */
    public List<Document> reRankByLLM(String question, List<Document> documents,
                                       ChatClient chatClient) {
        if (documents == null || documents.size() <= 1) {
            return documents;
        }

        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(documents.size(), 10); i++) {
                Document doc = documents.get(i);
                sb.append("[").append(i).append("] ")
                        .append(truncate(doc.getText(), 200)).append("\n");
            }

            String prompt = """
                    以下是多个文档片段，请判断每个片段与用户问题的相关度。
                    对每个片段输出 0（不相关）或 1（相关）。
                    只输出 JSON 数组，如 [1,0,0,1,1]，不要其他文字。

                    用户问题：%s

                    文档片段：
                    %s
                    """.formatted(question, sb);

            String response = chatClient.prompt()
                    .system("你是一个文档相关性评估助手。只输出 JSON 数组。")
                    .user(prompt)
                    .call()
                    .content();

            // 解析 LLM 返回的排序结果
            return parseAndReorder(response, documents);

        } catch (Exception e) {
            log.warn("【重排序-LLM】失败，退回分数排序: {}", e.getMessage());
            return reRankByScore(documents, 0);
        }
    }

    private List<Document> parseAndReorder(String response, List<Document> documents) {
        try {
            // 提取 JSON 数组
            int start = response.indexOf('[');
            int end = response.indexOf(']') + 1;
            if (start < 0 || end <= start) return documents;

            String json = response.substring(start, end);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            int[] scores = mapper.readValue(json, int[].class);

            // 按相关性排序
            List<Document> relevant = new java.util.ArrayList<>();
            for (int i = 0; i < Math.min(scores.length, documents.size()); i++) {
                if (scores[i] == 1) {
                    relevant.add(documents.get(i));
                }
            }
            return relevant.isEmpty() ? documents.subList(0, Math.min(3, documents.size())) : relevant;
        } catch (Exception e) {
            log.debug("LLM排序结果解析失败: {}", e.getMessage());
            return documents;
        }
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
