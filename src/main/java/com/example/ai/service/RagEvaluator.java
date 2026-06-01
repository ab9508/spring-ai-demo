package com.example.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 质量评估——使用预定义的 QA 测试集评估检索和回答质量。
 * <p>
 * 指标：
 * - recall@K：相关文档被检索到的比例
 * - precision@K：检索结果中相关文档的比例
 * - answerRate：LLM 能正确回答的比例（需人工判断）
 */
@Slf4j
@Service
public class RagEvaluator {

    private final VectorStore vectorStore;

    public RagEvaluator(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 运行检索评估
     *
     * @param testCases        测试用例列表
     * @param topK             检索 Top-K
     * @param similarityThreshold 相似度阈值
     * @return 评估报告
     */
    public EvaluationReport evaluate(List<TestCase> testCases, int topK, double similarityThreshold) {
        int totalHits = 0;
        int totalRetrieved = 0;
        List<TestCaseResult> details = new ArrayList<>();

        for (TestCase tc : testCases) {
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(tc.question())
                            .topK(topK)
                            .similarityThreshold(similarityThreshold)
                            .build()
            );

            boolean hitFound = results.stream()
                    .anyMatch(doc -> containsAnyKeyword(doc.getText(), tc.expectedKeywords()));

            totalHits += hitFound ? 1 : 0;
            totalRetrieved += results.size();

            details.add(new TestCaseResult(
                    tc.question(),
                    hitFound,
                    results.size(),
                    results.isEmpty() ? Collections.emptyList() :
                            results.stream().map(d -> String.format("%.4f", d.getScore()))
                                    .collect(Collectors.toList())
            ));
        }

        double recall = testCases.isEmpty() ? 0 : (double) totalHits / testCases.size();
        double precision = totalRetrieved == 0 ? 0 : (double) totalHits / totalRetrieved;

        return new EvaluationReport(testCases.size(), totalHits, totalRetrieved, recall, precision, details);
    }

    private boolean containsAnyKeyword(String text, List<String> keywords) {
        if (text == null || keywords == null) return false;
        String lower = text.toLowerCase();
        return keywords.stream().anyMatch(kw -> lower.contains(kw.toLowerCase()));
    }

    // ===== 数据结构 =====

    /**
     * 测试用例
     *
     * @param question         问题
     * @param expectedKeywords 期望出现在检索结果中的关键词
     */
    public record TestCase(String question, List<String> expectedKeywords) {
    }

    /**
     * 单个测试用例的评估结果
     */
    public record TestCaseResult(
            String question,
            boolean hit,
            int retrievedCount,
            List<String> scores
    ) {
    }

    /**
     * 评估报告
     */
    public record EvaluationReport(
            int totalCases,
            int totalHits,
            int totalRetrieved,
            double recall,
            double precision,
            List<TestCaseResult> details
    ) {
    }
}
