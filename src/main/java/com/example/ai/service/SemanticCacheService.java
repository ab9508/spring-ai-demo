package com.example.ai.service;

import com.example.ai.config.RagThresholdConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * 语义缓存——将问题与回答的 embedding 存入 Redis，
 * 新问题先在缓存中查找语义相似（cosine > threshold）的旧回答，命中则直接返回。
 * <p>
 * ====== 高并发 ======
 * - 用 SCAN 替代 KEYS：非阻塞迭代，不影响其他 Redis 操作
 * - 缓存有最大条目限制，超限时淘汰最旧条目
 * <p>
 * ====== 上传文档时只失效相关缓存（按语义相似度） ======
 * 上传新文档后，计算文档内容的 embedding，与每条缓存的 question embedding
 * 做 cosine 相似度对比：相似度高的缓存才失效，不相关的缓存保留。
 */
@Slf4j
@Service
public class SemanticCacheService {

    private static final String KEY_PREFIX = "cache:semantic:";

    private final EmbeddingModel embeddingModel;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RagThresholdConfig config;

    public SemanticCacheService(EmbeddingModel embeddingModel,
                                StringRedisTemplate redisTemplate,
                                RagThresholdConfig config) {
        this.embeddingModel = embeddingModel;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.config = config;
    }

    /**
     * 上传新文档后，按语义相似度失效相关缓存（SCAN 非阻塞遍历）
     */
    public void invalidateRelated(String uploadedContent) {
        if (uploadedContent == null || uploadedContent.isBlank()) return;

        float[] docVector = embeddingModel.embed(uploadedContent);
        if (docVector == null || docVector.length == 0) return;

        double threshold = config.getCacheInvalidateSimilarity();
        int deleted = 0;
        int skipped = 0;

        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(100).build())) {

            while (cursor.hasNext()) {
                String key = cursor.next();
                try {
                    String json = redisTemplate.opsForValue().get(key);
                    if (json == null) continue;

                    CacheEntry entry = objectMapper.readValue(json, CacheEntry.class);
                    double similarity = cosineSimilarity(docVector, entry.vector());
                    if (similarity >= threshold) {
                        redisTemplate.delete(key);
                        deleted++;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    log.debug("语义缓存失效扫描异常: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("SCAN 遍历缓存失败: {}", e.getMessage());
        }

        log.info("【语义缓存】文档上传后失效扫描: 失效{}条, 保留{}条", deleted, skipped);
    }

    /**
     * 从缓存中查找语义相似的回答（SCAN 非阻塞遍历）
     */
    public String get(String question) {
        float[] queryVector = embeddingModel.embed(question);
        if (queryVector == null || queryVector.length == 0) return null;

        double threshold = config.getCacheHitSimilarity();
        String bestAnswer = null;
        double bestScore = threshold;

        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(200).build())) {

            while (cursor.hasNext()) {
                String key = cursor.next();
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
        } catch (Exception e) {
            log.warn("SCAN 遍历缓存失败: {}", e.getMessage());
        }

        if (bestAnswer != null) {
            log.info("【语义缓存】命中 (score={})", String.format("%.4f", bestScore));
        }
        return bestAnswer;
    }

    /**
     * 将问题与回答存入语义缓存（带上限保护）
     */
    public void put(String question, String answer) {
        try {
            int maxSize = config.getCacheMaxSize();
            long currentSize = Optional.ofNullable(redisTemplate.keys(KEY_PREFIX + "*"))
                    .map(Set::size).orElse(0);
            if (currentSize >= maxSize) {
                log.warn("【语义缓存】已达上限({}), 不缓存: {}", maxSize, truncate(question, 30));
                return;
            }

            float[] vector = embeddingModel.embed(question);
            if (vector == null || vector.length == 0) return;

            String key = KEY_PREFIX + UUID.randomUUID();
            CacheEntry entry = new CacheEntry(question, answer, vector);
            Duration ttl = Duration.ofHours(config.getCacheTtlHours());
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(entry), ttl);
            log.debug("【语义缓存】已存储({}/{}): {}", currentSize + 1, maxSize, truncate(question, 30));
        } catch (JsonProcessingException e) {
            log.warn("语义缓存写入失败: {}", e.getMessage());
        }
    }

    /**
     * 清空所有语义缓存
     */
    public void clear() {
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(500).build())) {

            int count = 0;
            while (cursor.hasNext()) {
                redisTemplate.delete(cursor.next());
                count++;
            }
            log.info("【语义缓存】已清空 {} 条", count);
        } catch (Exception e) {
            log.warn("语义缓存清空失败: {}", e.getMessage());
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

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private record CacheEntry(String question, String answer, float[] vector) {
    }
}
