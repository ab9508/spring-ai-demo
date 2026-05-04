package com.example.ai.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;

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
 * ============ 序列化方案（v3 Jackson） ============
 * 使用 Jackson ObjectMapper + DTO 中转，替代手动 JSON 拼接（v2）。
 * <p>
 * 为什么不直接用 Jackson 反序列化 Message 子类？
 * Spring AI 的 UserMessage / AssistantMessage / ToolResponseMessage 没有默认构造方法，
 * Jackson 无法直接实例化。
 * <p>
 * 解决方案：自定义 DTO（MessageDto / ToolCallDto / ToolResponseDto）做中间层，
 * 序列化时 Message → DTO → JSON，反序列化时 JSON → DTO → Message。
 * DTO 是普通 POJO（@Data 提供默认构造+getter/setter），Jackson 无障碍处理。
 * <p>
 * ============ 序列化方案（v2，支持 Tool Calling） ============
 * 手动 JSON 序列化/反序列化，不依赖 Jackson/FastJSON 自动反序列化。
 * 原因：Message 子类（UserMessage/AssistantMessage/SystemMessage）没有默认构造方法，
 * 自动反序列化会失败。
 * <p>
 * 存储格式（Redis List 中每条消息的 JSON 结构）：
 * - USER/SYSTEM:           {"messageType":"USER","text":"内容"}
 * - ASSISTANT(纯文本):      {"messageType":"ASSISTANT","text":"AI回复"}
 * - ASSISTANT(含tool_calls):{"messageType":"ASSISTANT","text":"","toolCalls":[{"id":"call_xxx","type":"function","name":"queryOrder","arguments":"{\"orderId\":\"ORD-001\"}"}]}
 * - TOOL:                  {"messageType":"TOOL","toolResponses":[{"id":"call_xxx","name":"queryOrder","responseData":"{\"status\":\"已发货\"}"}]}
 */
@Slf4j
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String KEY_PREFIX = "chat:memory:";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisChatMemoryRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
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
        List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
        if (jsonList == null || jsonList.isEmpty()) {
            log.info("【查询会话】key:{},历史对话为空", key);
            return Collections.emptyList();
        }
        log.info("【查询会话】key:{},历史对话数量:{}", key, jsonList.size());
//        log.info("【查询会话】key:{},历史对话数量:{},内容:{}", key, jsonList.size(), jsonList);

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
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        log.info("【保存会话记忆】conversationId:{},消息数量:{}", conversationId, messages.size());
//        log.info("【保存会话记忆】conversationId:{},消息数量:{},messages:{}", conversationId, messages.size(), messages);
        if (messages == null || messages.isEmpty()) {
            return;
        }

        String key = KEY_PREFIX + conversationId;

        // 先删旧数据，再全量写入（MessageWindowChatMemory 每次传入的是完整的窗口消息列表）
        redisTemplate.delete(key);

        List<String> jsonList = new ArrayList<>(messages.size());
        // qaIndex 标识一次问答
        int qaIndex = 0;
        for (Message m : messages) {
            if (Objects.equals(MessageType.USER.name(), m.getMessageType().name())) {
                qaIndex += 1; // 遇到 USER 消息就 +1
            }
            jsonList.add(serializeMessage(m, qaIndex));
        }

        if (!jsonList.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(key, jsonList);
        }

        log.debug("【保存会话记忆】key:{} 的 {} 条消息", conversationId, messages.size());
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        String key = KEY_PREFIX + conversationId;
        redisTemplate.delete(key);
        log.debug("【删除会话记忆】key: {}", conversationId);
    }

    // ============ 序列化 ============

    /**
     * 序列化 Message → JSON 字符串
     * Message → MessageDto（DTO） → JSON（Jackson）
     */
    private String serializeMessage(Message message, int qaIndex) {
        try {
            MessageDto dto = toDto(message, qaIndex);
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.error("序列化消息失败: {}", message, e);
            throw new RuntimeException("消息序列化失败", e);
        }
    }

    /**
     * 反序列化 JSON 字符串 → Message
     * JSON（Jackson） → MessageDto（DTO） → Message
     */
    private Message deserializeMessage(String json) {
        try {
            MessageDto dto = objectMapper.readValue(json, MessageDto.class);
            return fromDto(dto);
        } catch (Exception e) {
            log.warn("反序列化消息失败，跳过: {}", json, e);
            return null;
        }
    }

    /**
     * Message → DTO 转换
     */
    private MessageDto toDto(Message message, int qaIndex) {
        MessageDto dto = new MessageDto();
        dto.setMessageType(message.getMessageType().name());
        dto.setQaIndex(qaIndex);

        switch (message.getMessageType()) {
            case USER, SYSTEM -> dto.setText(message.getText());
            case ASSISTANT -> {
                AssistantMessage am = (AssistantMessage) message;
                dto.setText(am.getText());
                if (am.hasToolCalls()) {
                    List<ToolCallDto> toolCallDtos = new ArrayList<>();
                    for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                        ToolCallDto d = new ToolCallDto();
                        d.setId(tc.id());
                        d.setType(tc.type());
                        d.setName(tc.name());
                        d.setArguments(tc.arguments());
                        toolCallDtos.add(d);
                    }
                    dto.setToolCalls(toolCallDtos);
                }
            }
            case TOOL -> {
                ToolResponseMessage trm = (ToolResponseMessage) message;
                List<ToolResponseDto> responseDtos = new ArrayList<>();
                for (ToolResponseMessage.ToolResponse tr : trm.getResponses()) {
                    ToolResponseDto d = new ToolResponseDto();
                    d.setId(tr.id());
                    d.setName(tr.name());
                    d.setResponseData(tr.responseData());
                    responseDtos.add(d);
                }
                dto.setToolResponses(responseDtos);
            }
        }
        return dto;
    }

    /**
     * DTO → Message 转换
     */
    private Message fromDto(MessageDto dto) {
        String type = dto.getMessageType();
        if (type == null) {
            log.warn("消息缺少 messageType 字段，跳过: {}", dto);
            return null;
        }

        return switch (type) {
            case "USER" -> new UserMessage(dto.getText());
            case "SYSTEM" -> new SystemMessage(dto.getText());
            case "ASSISTANT" -> {
                if (dto.getToolCalls() != null && !dto.getToolCalls().isEmpty()) {
                    List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
                    for (ToolCallDto tc : dto.getToolCalls()) {
                        toolCalls.add(new AssistantMessage.ToolCall(
                                tc.getId() != null ? tc.getId() : "",
                                tc.getType() != null ? tc.getType() : "function",
                                tc.getName(),
                                tc.getArguments() != null ? tc.getArguments() : "{}"
                        ));
                    }
                    yield new AssistantMessage(
                            dto.getText() != null ? dto.getText() : "", null, toolCalls);
                }
                yield new AssistantMessage(dto.getText() != null ? dto.getText() : "");
            }
            case "TOOL" -> {
                if (dto.getToolResponses() != null && !dto.getToolResponses().isEmpty()) {
                    List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                    for (ToolResponseDto tr : dto.getToolResponses()) {
                        responses.add(new ToolResponseMessage.ToolResponse(
                                tr.getId() != null ? tr.getId() : "",
                                tr.getName(),
                                tr.getResponseData() != null ? tr.getResponseData() : ""
                        ));
                    }
                    yield new ToolResponseMessage(responses);
                }
                // 兼容旧格式：TOOL 降级为 AssistantMessage
                log.warn("TOOL 消息缺少 toolResponses 字段，降级为 AssistantMessage");
                yield new AssistantMessage(dto.getText() != null ? dto.getText() : "");
            }
            default -> {
                log.warn("未知消息类型: {}", type);
                yield null;
            }
        };
    }

    // ============ DTO 定义 ============

    /**
     * 消息 DTO —— Jackson 序列化/反序列化的中间层
     * <p>
     * 为什么需要 DTO 而不是直接反序列化 Message？
     * Message 子类（UserMessage/AssistantMessage/ToolResponseMessage）没有默认构造方法，
     * Jackson 无法直接实例化。DTO 是普通 POJO，@Data 提供默认构造+getter/setter。
     */
    @Data
    public static class MessageDto {
        private String messageType;
        private String text;
        private List<ToolCallDto> toolCalls;
        private List<ToolResponseDto> toolResponses;
        private int qaIndex;
    }

    /**
     * ToolCall DTO
     * 对应 AssistantMessage.ToolCall 的字段
     * arguments 存储原始 JSON 字符串（如 {"orderId":"ORD-001"}）
     */
    @Data
    public static class ToolCallDto {
        private String id;
        private String type;
        private String name;
        private String arguments;
    }

    /**
     * ToolResponse DTO
     * 对应 ToolResponseMessage.ToolResponse 的字段
     * responseData 存储原始 JSON 字符串（如 {"status":"已发货"}）
     */
    @Data
    public static class ToolResponseDto {
        private String id;
        private String name;
        private String responseData;
    }
}
