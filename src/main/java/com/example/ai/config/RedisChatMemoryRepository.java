package com.example.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Redis 实现的 ChatMemoryRepository
 * <p>
 * ============ 技术选型背景 ============
 * Spring AI 1.0.x 原生只支持 InMemory / JDBC / Cassandra / Neo4j 四种 ChatMemory 存储，
 * 不支持 Redis。Spring AI Alibaba 的 Redis ChatMemory 依赖要求 Spring Boot 3.5.x，
 * 与本项目（Spring Boot 3.4.5 + Spring AI 1.0.5）版本冲突。
 * 因此手写实现 ChatMemoryRepository 接口，基于 Redis List 存储。
 * <p>
 * ============ Redis 数据结构 ============
 * Key: chat:memory:{conversationId}  →  List<String>
 * 每个元素是一条消息的 JSON 字符串，按时间顺序排列（左旧右新）
 * <p>
 * ============ 序列化方案 ============
 * 手动 JSON 序列化/反序列化，不依赖 Jackson/FastJSON 自动反序列化。
 * 原因：Message 子类（UserMessage/AssistantMessage/SystemMessage）没有默认构造方法，
 * 自动反序列化会失败。
 */
@Slf4j
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String KEY_PREFIX = "chat:memory:";
    private final StringRedisTemplate redisTemplate;

    public RedisChatMemoryRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<String> findConversationIds() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        for (String key : keys) {
            ids.add(key.substring(KEY_PREFIX.length()));
        }
        log.info("keys:{} ==> ids:{}", keys, ids);
        return ids;
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        log.info("会话key:{}", key);
        List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
        log.info("jsonList数量{},内容:{}", jsonList.size(), jsonList);
        if (jsonList == null || jsonList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Message> messages = new ArrayList<>();
        for (String json : jsonList) {
            try {
                Message message = deserializeMessage(json);
                if (message != null) {
                    messages.add(message);
                }
            } catch (Exception e) {
                log.warn("反序列化消息失败，跳过: {}", json, e);
            }
        }
        log.info("messages数量:,内容{}", messages.size(), messages);
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        log.info("【保存会话记忆】conversationId:{},messages:{}", conversationId, messages);
        if (messages == null || messages.isEmpty()) {
            return;
        }

        String key = KEY_PREFIX + conversationId;

        // 先删旧数据，再全量写入（MessageWindowChatMemory 每次传入的是完整的窗口消息列表）
        redisTemplate.delete(key);

        List<String> jsonList = new ArrayList<>(messages.size());
        for (Message m : messages) {
            jsonList.add(serializeMessage(m));
        }

        if (!jsonList.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(key, jsonList);
        }

        log.debug("【RedisChatMemory】保存会话 {} 的 {} 条消息", conversationId, messages.size());
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        redisTemplate.delete(key);
        log.debug("【RedisChatMemory】删除会话 {}", conversationId);
    }

    // ============ 序列化 ============

    /**
     * 序列化 Message → JSON 字符串
     * 只提取 messageType + text，丢弃 metadata（简化存储，避免复杂嵌套）
     */
    private String serializeMessage(Message message) {
        String type = message.getMessageType().name();
        String text = message.getText();
        // 简单拼接，避免引入额外 JSON 库依赖
        // 格式: {"messageType":"USER","text":"内容"}
        String escapedText = text != null ? text.replace("\"", "\\\"").replace("\n", "\\n") : "";
        return "{\"messageType\":\"" + type + "\",\"text\":\"" + escapedText + "\"}";
    }

    /**
     * 反序列化 JSON 字符串 → Message
     * 根据 messageType 手动创建对应的 Message 子类实例
     */
    private Message deserializeMessage(String json) {
        // 手动解析简单 JSON，避免额外依赖
        String type = extractJsonValue(json, "messageType");
        String text = extractJsonValue(json, "text");

        if (!StringUtils.hasText(type) || !StringUtils.hasText(text)) {
            return null;
        }

        // text 中的转义还原
        text = text.replace("\\n", "\n").replace("\\\"", "\"");

        try {
            MessageType messageType = MessageType.valueOf(type.toUpperCase());
            return switch (messageType) {
                case USER -> new UserMessage(text);
                case SYSTEM -> new SystemMessage(text);
                case ASSISTANT -> new AssistantMessage(text);
                case TOOL -> new AssistantMessage(text); // ToolResponseMessage 需要复杂构造，简化处理
                default -> {
                    log.warn("未知消息类型: {}", type);
                    yield null;
                }
            };
        } catch (IllegalArgumentException e) {
            log.warn("无法识别消息类型: {}", type);
            return null;
        }
    }

    /**
     * 简易 JSON 值提取，从 {"key":"value"} 格式中提取指定 key 的 value
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int start = json.indexOf(searchKey);
        if (start < 0) {
            return null;
        }
        start += searchKey.length();
        int end = json.indexOf("\"", start);
        if (end < 0) {
            return json.substring(start);
        }
        return json.substring(start, end);
    }
}
