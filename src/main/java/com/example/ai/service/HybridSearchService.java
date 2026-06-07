package com.example.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索服务——稠密向量检索 + 关键词检索（BM25）融合。
 * <p>
 * 融合策略：向量 Top-K + 关键词 Top-K → 去重 → 按融合分数排序 → 取最终 Top-K
 */
@Slf4j
@Service
public class HybridSearchService {

    private static final double VECTOR_WEIGHT = 0.6;
    private static final double KEYWORD_WEIGHT = 0.4;

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public HybridSearchService(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 混合检索
     */
    public List<Document> hybridSearch(String question, int topK) {
        List<Document> vectorResults = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(topK * 2)
                        .similarityThreshold(0.3)
                        .build()
        );

        List<Document> keywordResults = keywordSearch(question, topK * 2);
        return fuse(vectorResults, keywordResults, topK);
    }

    /**
     * 基于 PostgreSQL 全文检索的关键词搜索
     */
    private List<Document> keywordSearch(String question, int limit) {
        if (question == null || question.isBlank()) {
            return Collections.emptyList();
        }

        String[] keywords = question.split("[\\s,，。！？、；：\"\"''（）【】《》]+");
        List<String> validKeywords = Arrays.stream(keywords)
                .filter(kw -> kw.length() >= 2)
                .collect(Collectors.toList());

        if (validKeywords.isEmpty()) {
            return Collections.emptyList();
        }

        String tsquery = validKeywords.stream()
                .map(kw -> kw + ":*")
                .collect(Collectors.joining(" & "));

        try {
            String sql = """
                    SELECT id, content, metadata,
                           ts_rank(to_tsvector('simple', COALESCE(content, '')),
                                   to_tsquery('simple', ?)) AS score
                    FROM vector_store
                    WHERE to_tsvector('simple', COALESCE(content, '')) @@ to_tsquery('simple', ?)
                    ORDER BY score DESC
                    LIMIT ?
                    """;

            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> meta = new HashMap<>();
                meta.put("source", "keyword");
                meta.put("keywordScore", rs.getDouble("score"));
                Document doc = new Document(rs.getString("content"), meta);
                return doc;
            }, tsquery, tsquery, limit);

        } catch (Exception e) {
            log.debug("关键词检索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 融合排序
     */
    private List<Document> fuse(List<Document> vectorResults, List<Document> keywordResults, int topK) {
        Map<String, FuseEntry> fused = new LinkedHashMap<>();

        for (Document doc : vectorResults) {
            double score = doc.getScore() != null ? doc.getScore() : 0;
            fused.put(doc.getText(), new FuseEntry(doc, score * VECTOR_WEIGHT));
        }

        for (Document doc : keywordResults) {
            Object kwScoreObj = doc.getMetadata().get("keywordScore");
            double kwScore = kwScoreObj instanceof Number n ? n.doubleValue() / 10.0 : 0;
            fused.merge(doc.getText(), new FuseEntry(doc, kwScore * KEYWORD_WEIGHT),
                    (existing, incoming) -> {
                        existing.fusedScore += incoming.fusedScore;
                        return existing;
                    });
        }

        return fused.values().stream()
                .sorted((a, b) -> Double.compare(b.fusedScore, a.fusedScore))
                .limit(topK)
                .map(e -> e.doc)
                .collect(Collectors.toList());
    }

    private static class FuseEntry {
        final Document doc;
        double fusedScore;

        FuseEntry(Document doc, double score) {
            this.doc = doc;
            this.fusedScore = score;
        }
    }
}
