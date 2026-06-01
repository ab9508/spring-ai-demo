package com.example.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * 语义缓存——将问题与回答的 embedding 存入 Redis，
 * 新问题先在缓存中查找语义相似（cosine > threshold）的旧回答，命中则直接返回。
 * <p>
 * 适用场景：高频重复问题（退货流程、包邮条件等），缓存命中可减少 30%-50% 的 LLM 调用。
 */
@Slf4j
@Service
public class SemanticCacheService {

    private static final String KEY_PREFIX = "cache:semantic:";
    private static final double SIMILARITY_THRESHOLD = 0.92;
    private static final Duration TTL = Duration.ofHours(1);

    private final EmbeddingModel embeddingModel;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SemanticCacheService(EmbeddingModel embeddingModel,
                                StringRedisTemplate redisTemplate) {
        this.embeddingModel = embeddingModel;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 从缓存中查找语义相似的回答
     *
     * @param question 用户问题
     * @return 命中缓存返回回答，否则返回 null
     */
    public String get(String question) {
        float[] queryVector = embeddingModel.embed(question);
        if (queryVector == null || queryVector.length == 0) {
            return null;
        }

        // 扫描所有缓存条目
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return null;
        }

        String bestAnswer = null;
        double bestScore = SIMILARITY_THRESHOLD;

        for (String key : keys) {
            try {
                String json = redisTemplate.opsForValue().get(key);
                if (json == null) continue;

                CacheEntry entry = objectMapper.readValue(json, CacheEntry.class);
                double similarity = cosineSimilarity(queryVector, entry.vector());
                if (similarity >= bestScore) {
                    bestScore = similarity;
                    bestAnswer = entry.answer();
                }
            } catch (Exception e) {
                log.debug("语义缓存读取失败: {}", e.getMessage());
            }
        }

        if (bestAnswer != null) {
            log.info("【语义缓存】命中 (score={})", String.format("%.4f", bestScore));
        }
        return bestAnswer;
    }

    /**
     * 将问题与回答存入语义缓存
     */
    public void put(String question, String answer) {
        try {
            float[] vector = embeddingModel.embed(question);
            if (vector == null || vector.length == 0) return;

            String key = KEY_PREFIX + UUID.randomUUID();
            CacheEntry entry = new CacheEntry(question, answer, vector);
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(entry), TTL);
            log.debug("【语义缓存】已存储: {}", question);
        } catch (JsonProcessingException e) {
            log.warn("语义缓存写入失败: {}", e.getMessage());
        }
    }

    /**
     * 清空所有语义缓存
     */
    public void clear() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("【语义缓存】已清空 {} 条", keys.size());
        }
    }

    // ===== 内部 =====

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(na) * Math.sqrt(nb);
        return denom == 0 ? 0 : dot / denom;
    }

    private record CacheEntry(String question, String answer, float[] vector) {
    }
}
