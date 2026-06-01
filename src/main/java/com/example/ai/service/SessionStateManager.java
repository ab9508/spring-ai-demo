package com.example.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务状态管理——在 Redis 中维护每个会话的操作上下文。
 * <p>
 * 对话记忆只能记住「聊过什么」，
 * 业务状态管理记住「做了什么」。
 * <p>
 * 例如：用户说"退了我刚买的那个手机"
 * - 对话记忆：知道用户提到了"退"和"手机"
 * - 业务状态：知道用户最后查看的订单是 ORD-002（已维护在上下文中）
 */
@Slf4j
@Service
public class SessionStateManager {

    private static final String KEY_PREFIX = "session:ctx:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;

    public SessionStateManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取会话的上下文快照（所有字段以 JSON 字符串返回）
     */
    public Map<Object, Object> getContext(String sessionId) {
        String key = buildKey(sessionId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        return entries.isEmpty() ? new ConcurrentHashMap<>() : entries;
    }

    /**
     * 更新上下文字段
     */
    public void setField(String sessionId, String field, String value) {
        String key = buildKey(sessionId);
        redisTemplate.opsForHash().put(key, field, value);
        redisTemplate.expire(key, DEFAULT_TTL);
        log.debug("【SessionState】{}: {} = {}", sessionId, field, value);
    }

    /**
     * 读取上下文字段
     */
    public String getField(String sessionId, String field) {
        String key = buildKey(sessionId);
        Object value = redisTemplate.opsForHash().get(key, field);
        return value != null ? value.toString() : null;
    }

    /**
     * 记录用户操作轨迹（追加到操作历史）
     */
    public void recordAction(String sessionId, String action, String detail) {
        String key = buildKey(sessionId);
        long seq = redisTemplate.opsForHash().size(key);
        String field = "action_" + seq;
        redisTemplate.opsForHash().put(key, field, action + "|" + detail);
        // 同时更新最后操作
        redisTemplate.opsForHash().put(key, "lastAction", action);
        redisTemplate.opsForHash().put(key, "lastActionDetail", detail);
        redisTemplate.expire(key, DEFAULT_TTL);
        log.debug("【SessionState】{} action[{}]: {} | {}", sessionId, seq, action, detail);
    }

    /**
     * 获取最后操作类型
     */
    public String getLastAction(String sessionId) {
        return getField(sessionId, "lastAction");
    }

    /**
     * 清除会话上下文
     */
    public void clear(String sessionId) {
        redisTemplate.delete(buildKey(sessionId));
    }

    private String buildKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
