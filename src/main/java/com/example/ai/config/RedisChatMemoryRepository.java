package com.example.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

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
                qaIndex += 1;// 遇到 USER 消息就 +1
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
     * <p>
     * 格式说明：
     * - USER/SYSTEM/ASSISTANT(纯文本): {"messageType":"USER","text":"内容"}
     * - ASSISTANT(含 tool_calls): {"messageType":"ASSISTANT","text":"xxx","toolCalls":[{"id":"call_xxx","type":"function","name":"queryOrder","arguments":"{\"orderId\":\"ORD-001\"}"}]}
     * - TOOL(ToolResponseMessage): {"messageType":"TOOL","toolResponses":[{"id":"call_xxx","name":"queryOrder","responseData":"{\"status\":\"已发货\"}"}]}
     */
    private String serializeMessage(Message message, int qaIndex) {
        String type = message.getMessageType().name();

        return switch (message.getMessageType()) {
            case USER, SYSTEM -> {
                String escapedText = escapeJson(message.getText());
                yield "{\"messageType\":\"" + type + "\",\"text\":\"" + escapedText + "\",\"qaIndex\":" + qaIndex + "}";
            }
            case ASSISTANT -> {
                AssistantMessage am = (AssistantMessage) message;
                String escapedText = escapeJson(am.getText());
                StringBuilder sb = new StringBuilder();
                sb.append("{\"messageType\":\"ASSISTANT\",\"text\":\"").append(escapedText).append("\"");
                // 序列化 toolCalls（如果有）
                if (am.hasToolCalls()) {
                    sb.append(",\"toolCalls\":[");
                    List<AssistantMessage.ToolCall> toolCalls = am.getToolCalls();
                    for (int i = 0; i < toolCalls.size(); i++) {
                        AssistantMessage.ToolCall tc = toolCalls.get(i);
                        if (i > 0) sb.append(",");
                        sb.append("{\"id\":\"").append(escapeJson(tc.id()))
                                .append("\",\"type\":\"").append(escapeJson(tc.type()))
                                .append("\",\"name\":\"").append(escapeJson(tc.name()))
                                .append("\",\"arguments\":\"").append(escapeJson(tc.arguments())).append("\"}");
                    }
                    sb.append("]");
                }
                sb.append(",\"qaIndex\":" + qaIndex + "}");
                yield sb.toString();
            }
            case TOOL -> {
                ToolResponseMessage trm = (ToolResponseMessage) message;
                StringBuilder sb = new StringBuilder();
                sb.append("{\"messageType\":\"TOOL\"");
                // 序列化 toolResponses
                List<ToolResponseMessage.ToolResponse> responses = trm.getResponses();
                if (responses != null && !responses.isEmpty()) {
                    sb.append(",\"toolResponses\":[");
                    for (int i = 0; i < responses.size(); i++) {
                        ToolResponseMessage.ToolResponse tr = responses.get(i);
                        if (i > 0) sb.append(",");
                        sb.append("{\"id\":\"").append(escapeJson(tr.id()))
                                .append("\",\"name\":\"").append(escapeJson(tr.name()))
                                .append("\",\"responseData\":\"").append(escapeJson(tr.responseData())).append("\"}");
                    }
                    sb.append("]");
                }
                sb.append(",\"qaIndex\":" + qaIndex + "}");
                yield sb.toString();
            }
        };
    }

    /**
     * 反序列化 JSON 字符串 → Message
     * <p>
     * 支持的消息格式：
     * - 纯文本消息: {"messageType":"USER","text":"内容"}
     * - 含 tool_calls 的 AssistantMessage: {"messageType":"ASSISTANT","text":"xxx","toolCalls":[...]}
     * - ToolResponseMessage: {"messageType":"TOOL","toolResponses":[...]}
     */
    private Message deserializeMessage(String json) {
        String type = extractJsonValue(json, "messageType");
        if (!StringUtils.hasText(type)) {
            log.warn("消息缺少 messageType 字段，跳过: {}", json);
            return null;
        }

        try {
            MessageType messageType = MessageType.valueOf(type.toUpperCase());
            return switch (messageType) {
                case USER -> {
                    String text = unescapeJson(extractJsonValue(json, "text"));
                    yield text != null ? new UserMessage(text) : null;
                }
                case SYSTEM -> {
                    String text = unescapeJson(extractJsonValue(json, "text"));
                    yield text != null ? new SystemMessage(text) : null;
                }
                case ASSISTANT -> {
                    String text = unescapeJson(extractJsonValue(json, "text"));
                    // 尝试还原 toolCalls
                    String toolCallsJson = extractJsonArray(json, "toolCalls");
                    List<AssistantMessage.ToolCall> toolCalls = parseToolCalls(toolCallsJson);
                    if (toolCalls != null && !toolCalls.isEmpty()) {
                        yield new AssistantMessage(text != null ? text : "", null, toolCalls);
                    }
                    yield new AssistantMessage(text != null ? text : "");
                }
                case TOOL -> {
                    // 还原 ToolResponseMessage
                    String toolResponsesJson = extractJsonArray(json, "toolResponses");
                    List<ToolResponseMessage.ToolResponse> responses = parseToolResponses(toolResponsesJson);
                    if (responses != null && !responses.isEmpty()) {
                        yield new ToolResponseMessage(responses);
                    }
                    // 兼容旧格式：TOOL 降级为 AssistantMessage
                    log.warn("TOOL 消息缺少 toolResponses 字段，降级为 AssistantMessage: {}", json);
                    String text = unescapeJson(extractJsonValue(json, "text"));
                    yield text != null ? new AssistantMessage(text) : null;
                }
                default -> {
                    log.warn("未知消息类型: {}", type);
                    yield null;
                }
            };
        } catch (Exception e) {
            log.warn("反序列化消息失败，跳过: {}", json, e);
            return null;
        }
    }

    // ============ JSON 工具方法 ============

    /**
     * JSON 字符串转义（用于序列化）
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * JSON 字符串反转义（用于反序列化）
     */
    private String unescapeJson(String text) {
        if (text == null) return null;
        return text.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    /**
     * 从 JSON 中提取数组字段的原始字符串（含方括号）
     * 例如从 {"toolCalls":[...]} 中提取 [...]
     * 如果找不到返回 null
     */
    private String extractJsonArray(String json, String key) {
        String searchKey = "\"" + key + "\":[";
        int start = json.indexOf(searchKey);
        if (start < 0) return null;
        start += searchKey.length() - 1; // 包含 [
        // 找到匹配的 ]
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return json.substring(start, i + 1);
            }
        }
        return null;
    }

    /**
     * 解析 toolCalls JSON 数组 → List<AssistantMessage.ToolCall>
     * 输入格式: [{"id":"call_xxx","type":"function","name":"queryOrder","arguments":"{\"orderId\":\"ORD-001\"}"}]
     */
    private List<AssistantMessage.ToolCall> parseToolCalls(String jsonArray) {
        if (jsonArray == null || jsonArray.equals("[]")) return Collections.emptyList();
        List<AssistantMessage.ToolCall> result = new ArrayList<>();
        // 按 },{ 分割每个元素
        String[] items = jsonArray.substring(1, jsonArray.length() - 1).split("\\},\\{");
        for (String item : items) {
            item = item.replace("{", "").replace("}", "");
            String id = unescapeJson(extractJsonValue(item, "id"));
            String type = unescapeJson(extractJsonValue(item, "type"));
            String name = unescapeJson(extractJsonValue(item, "name"));
            String arguments = unescapeJson(extractJsonValue(item, "arguments"));
            if (StringUtils.hasText(name)) {
                result.add(new AssistantMessage.ToolCall(
                        id != null ? id : "",
                        type != null ? type : "function",
                        name,
                        arguments != null ? arguments : "{}"
                ));
            }
        }
        return result;
    }

    /**
     * 解析 toolResponses JSON 数组 → List<ToolResponseMessage.ToolResponse>
     * 输入格式: [{"id":"call_xxx","name":"queryOrder","responseData":"{\"status\":\"已发货\"}"}]
     */
    private List<ToolResponseMessage.ToolResponse> parseToolResponses(String jsonArray) {
        if (jsonArray == null || jsonArray.equals("[]")) return Collections.emptyList();
        List<ToolResponseMessage.ToolResponse> result = new ArrayList<>();
        String[] items = jsonArray.substring(1, jsonArray.length() - 1).split("\\},\\{");
        for (String item : items) {
            item = item.replace("{", "").replace("}", "");
            String id = unescapeJson(extractJsonValue(item, "id"));
            String name = unescapeJson(extractJsonValue(item, "name"));
            String responseData = unescapeJson(extractJsonValue(item, "responseData"));
            if (StringUtils.hasText(name)) {
                result.add(new ToolResponseMessage.ToolResponse(
                        id != null ? id : "",
                        name,
                        responseData != null ? responseData : ""
                ));
            }
        }
        return result;
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
