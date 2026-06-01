package com.example.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 限流服务——令牌桶算法
 * <p>
 * 两级限流：
 *   用户级别：每个用户独立令牌桶（控制单个用户调用频率）
 *   全局级别：每日总调用上限（控制成本预算）
 */
@Slf4j
@Service
public class RateLimitService {

    @Value("${app.rate-limit.user.capacity:20}")
    private int userCapacity;

    @Value("${app.rate-limit.user.refill-rate:1}")
    private int userRefillRate;

    @Value("${app.rate-limit.user.refill-interval-ms:3000}")
    private int userRefillIntervalMs;

    @Value("${app.rate-limit.global.daily-max:10000}")
    private long globalDailyMax;

    private final Map<String, TokenBucket> userBuckets = new ConcurrentHashMap<>();
    private final AtomicLong todayCallCount = new AtomicLong(0);
    private volatile int lastResetDay = -1;

    @PostConstruct
    public void init() {
        log.info("【限流】初始化: 每用户容量={} / 补充速率={}/{}ms / 日限额={}",
                userCapacity, userRefillRate, userRefillIntervalMs, globalDailyMax);
    }

    /**
     * 检查当前请求是否允许通过
     *
     * @param userId 用户标识（可以是IP、sessionId或固定值）
     * @return true=允许通过, false=被限流
     */
    public boolean tryAcquire(String userId) {
        // 1. 全局日限额检查
        if (!checkGlobalLimit()) {
            return false;
        }

        // 2. 用户级别限流
        TokenBucket bucket = userBuckets.computeIfAbsent(userId, k ->
                new TokenBucket(userCapacity, userRefillRate, userRefillIntervalMs));
        return bucket.tryConsume();
    }

    /**
     * 检查全局日限额
     */
    private synchronized boolean checkGlobalLimit() {
        int today = today();
        if (today != lastResetDay) {
            todayCallCount.set(0);
            lastResetDay = today;
        }
        long count = todayCallCount.incrementAndGet();
        if (count > globalDailyMax) {
            log.warn("【限流】全局日限额已达上限 {}/{}", count, globalDailyMax);
            return false;
        }
        return true;
    }

    private int today() {
        return (int) (System.currentTimeMillis() / 86400000);
    }

    /**
     * 令牌桶
     */
    static class TokenBucket {
        private final int capacity;
        private final int refillRate;
        private final long refillIntervalMs;
        private double tokens;
        private long lastRefillTime;

        TokenBucket(int capacity, int refillRate, long refillIntervalMs) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.refillIntervalMs = refillIntervalMs;
            this.tokens = capacity;
            this.lastRefillTime = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            if (elapsed < refillIntervalMs) return;

            long cycles = elapsed / refillIntervalMs;
            double newTokens = cycles * refillRate;
            tokens = Math.min(capacity, tokens + newTokens);
            lastRefillTime = now;
        }
    }
}
