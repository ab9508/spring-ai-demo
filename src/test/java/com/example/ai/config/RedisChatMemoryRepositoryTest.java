package com.example.ai.config;

import com.example.ai.config.RedisChatMemoryRepository.MessageDto;
import com.example.ai.config.RedisChatMemoryRepository.ToolCallDto;
import com.example.ai.config.RedisChatMemoryRepository.ToolResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.*;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * RedisChatMemoryRepository 单元测试
 * <p>
 * 覆盖：
 * 1. 消息序列化/反序列化（USER / SYSTEM / ASSISTANT / TOOL）
 * 2. Tool Calling 消息（含 toolCalls / toolResponses）
 * 3. 查找/保存/删除会话
 * 4. 异常消息的容错处理
 */
@ExtendWith(MockitoExtension.class)
class RedisChatMemoryRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOps;

    private RedisChatMemoryRepository repository;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForList()).thenReturn(listOps);
        repository = new RedisChatMemoryRepository(redisTemplate);
    }

    // ==================== 序列化/反序列化 ====================

    @Nested
    @DisplayName("消息序列化与反序列化")
    class Serialization {

        @Test
        @DisplayName("USER 消息应正确序列化和反序列化")
        void shouldRoundTripUserMessage() {
            // 序列化前先存入 Redis
            UserMessage original = new UserMessage("帮我查一下订单");
            List<Message> messages = List.of(original);
            repository.saveAll("conv-001", messages);

            // 验证写入 Redis 的数据
            verify(listOps).rightPushAll(eq("chat:memory:conv-001"), anyList());

            // 模拟从 Redis 读取
            String json = serializeMessage(original);
            when(listOps.range("chat:memory:conv-001", 0, -1))
                    .thenReturn(Collections.singletonList(json));

            // 反序列化检查
            List<Message> loaded = repository.findByConversationId("conv-001");
            assertEquals(1, loaded.size());
            assertInstanceOf(UserMessage.class, loaded.get(0));
            assertEquals("帮我查一下订单", loaded.get(0).getText());
        }

        @Test
        @DisplayName("SYSTEM 消息应正确序列化和反序列化")
        void shouldRoundTripSystemMessage() {
            SystemMessage original = new SystemMessage("你是智能客服助手。");
            List<Message> messages = List.of(original);
            repository.saveAll("conv-sys", messages);

            String json = serializeMessage(original);
            when(listOps.range("chat:memory:conv-sys", 0, -1))
                    .thenReturn(Collections.singletonList(json));

            List<Message> loaded = repository.findByConversationId("conv-sys");
            assertEquals(1, loaded.size());
            assertInstanceOf(SystemMessage.class, loaded.get(0));
            assertEquals("你是智能客服助手。", loaded.get(0).getText());
        }

        @Test
        @DisplayName("ASSISTANT 纯文本消息应正确序列化和反序列化")
        void shouldRoundTripAssistantTextMessage() {
            AssistantMessage original = new AssistantMessage("您好，订单已为您查询。");
            List<Message> messages = List.of(original);
            repository.saveAll("conv-ast", messages);

            String json = serializeMessage(original);
            when(listOps.range("chat:memory:conv-ast", 0, -1))
                    .thenReturn(Collections.singletonList(json));

            List<Message> loaded = repository.findByConversationId("conv-ast");
            assertEquals(1, loaded.size());
            assertInstanceOf(AssistantMessage.class, loaded.get(0));
            assertEquals("您好，订单已为您查询。", loaded.get(0).getText());
        }

        @Test
        @DisplayName("ASSISTANT 含 ToolCall 的消息应正确序列化和反序列化")
        void shouldRoundTripAssistantWithToolCalls() {
            List<AssistantMessage.ToolCall> toolCalls = List.of(
                    new AssistantMessage.ToolCall("call_001", "function",
                            "queryOrder", "{\"orderId\":\"ORD-001\"}")
            );
            AssistantMessage original = new AssistantMessage("正在查询订单...", null, toolCalls);
            List<Message> messages = List.of(original);
            repository.saveAll("conv-tc", messages);

            String json = serializeMessage(original);
            when(listOps.range("chat:memory:conv-tc", 0, -1))
                    .thenReturn(Collections.singletonList(json));

            List<Message> loaded = repository.findByConversationId("conv-tc");
            assertEquals(1, loaded.size());
            assertInstanceOf(AssistantMessage.class, loaded.get(0));
            AssistantMessage am = (AssistantMessage) loaded.get(0);
            assertTrue(am.hasToolCalls(), "应包含 tool calls");
            assertEquals(1, am.getToolCalls().size());
            assertEquals("call_001", am.getToolCalls().get(0).id());
            assertEquals("queryOrder", am.getToolCalls().get(0).name());
            assertEquals("{\"orderId\":\"ORD-001\"}", am.getToolCalls().get(0).arguments());
        }

        @Test
        @DisplayName("TOOL 响应消息应正确序列化和反序列化")
        void shouldRoundTripToolResponseMessage() {
            List<ToolResponseMessage.ToolResponse> responses = List.of(
                    new ToolResponseMessage.ToolResponse("call_001", "queryOrder",
                            "{\"status\":\"已发货\"}")
            );
            ToolResponseMessage original = new ToolResponseMessage(responses);
            List<Message> messages = List.of(original);
            repository.saveAll("conv-tr", messages);

            String json = serializeMessage(original);
            when(listOps.range("chat:memory:conv-tr", 0, -1))
                    .thenReturn(Collections.singletonList(json));

            List<Message> loaded = repository.findByConversationId("conv-tr");
            assertEquals(1, loaded.size());
            assertInstanceOf(ToolResponseMessage.class, loaded.get(0));
            ToolResponseMessage trm = (ToolResponseMessage) loaded.get(0);
            assertEquals(1, trm.getResponses().size());
            assertEquals("call_001", trm.getResponses().get(0).id());
            assertEquals("queryOrder", trm.getResponses().get(0).name());
        }

        @Test
        @DisplayName("USER + ASSISTANT + TOOL 混合消息应全部正确反序列化")
        void shouldHandleMixedMessageTypes() {
            List<String> jsonList = List.of(
                    "{\"messageType\":\"USER\",\"text\":\"查一下订单\"}",
                    "{\"messageType\":\"ASSISTANT\",\"text\":\"好的\"}",
                    "{\"messageType\":\"SYSTEM\",\"text\":\"你是一个客服\"}"
            );
            when(listOps.range("chat:memory:mixed", 0, -1)).thenReturn(jsonList);

            List<Message> loaded = repository.findByConversationId("mixed");
            assertEquals(3, loaded.size());
            assertInstanceOf(UserMessage.class, loaded.get(0));
            assertInstanceOf(AssistantMessage.class, loaded.get(1));
            assertInstanceOf(SystemMessage.class, loaded.get(2));
        }
    }

    // ==================== 异常消息容错 ====================

    @Nested
    @DisplayName("异常数据容错")
    class ErrorTolerance {

        @Test
        @DisplayName("格式异常的 JSON 应被跳过而不是抛异常")
        void shouldSkipMalformedJson() {
            List<String> jsonList = List.of(
                    "{\"messageType\":\"USER\",\"text\":\"正常消息\"}",
                    "这不是一个 JSON 字符串",
                    "{\"messageType\":\"ASSISTANT\",\"text\":\"正常回复\"}"
            );
            when(listOps.range("chat:memory:bad-json", 0, -1)).thenReturn(jsonList);

            List<Message> loaded = repository.findByConversationId("bad-json");
            // 异常消息被跳过，但还是可以正常拿到 2 条
            assertEquals(2, loaded.size(), "格式异常的消息应被跳过");
        }

        @Test
        @DisplayName("未知 messageType 应被跳过")
        void shouldSkipUnknownType() {
            List<String> jsonList = List.of(
                    "{\"messageType\":\"UNKNOWN_TYPE\",\"text\":\"什么类型\"}"
            );
            when(listOps.range("chat:memory:unknown", 0, -1)).thenReturn(jsonList);

            List<Message> loaded = repository.findByConversationId("unknown");
            assertTrue(loaded.isEmpty(), "未知类型应被跳过");
        }

        @Test
        @DisplayName("Redis 返回 null 时应返回空列表")
        void shouldHandleNullFromRedis() {
            when(listOps.range(anyString(), anyLong(), anyLong())).thenReturn(null);

            List<Message> loaded = repository.findByConversationId("null-key");
            assertTrue(loaded.isEmpty(), "Redis null 返回空列表");
        }

        @Test
        @DisplayName("消息列表为 null 时 saveAll 应安全跳过")
        void shouldSkipSaveAllWhenMessagesNull() {
            repository.saveAll("conv-null", null);
            verify(listOps, never()).rightPushAll(anyString(), anyList());
        }

        @Test
        @DisplayName("消息列表为空时 saveAll 应安全跳过")
        void shouldSkipSaveAllWhenMessagesEmpty() {
            repository.saveAll("conv-empty", Collections.emptyList());
            verify(listOps, never()).rightPushAll(anyString(), anyList());
        }
    }

    // ==================== CRUD 操作 ====================

    @Nested
    @DisplayName("会话 CRUD 操作")
    class CrudOperations {

        @Test
        @DisplayName("deleteByConversationId 应删除 Redis key")
        void shouldDeleteConversation() {
            repository.deleteByConversationId("conv-del");
            verify(redisTemplate).delete("chat:memory:conv-del");
        }

        @Test
        @DisplayName("findConversationIds 应返回所有会话 ID")
        void shouldFindAllConversationIds() {
            when(redisTemplate.keys("chat:memory:*")).thenReturn(
                    java.util.Set.of("chat:memory:conv-001", "chat:memory:conv-002")
            );
            List<String> ids = repository.findConversationIds();
            assertEquals(2, ids.size());
            assertTrue(ids.contains("conv-001"));
            assertTrue(ids.contains("conv-002"));
        }

        @Test
        @DisplayName("saveAll 应先删除旧 key 再全量写入")
        void saveAllShouldDeleteThenWrite() {
            List<Message> messages = List.of(new UserMessage("test"));
            repository.saveAll("conv-rw", messages);

            // 先删
            verify(redisTemplate).delete("chat:memory:conv-rw");
            // 再写
            verify(listOps).rightPushAll(eq("chat:memory:conv-rw"), anyList());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 通过反射访问 private 的 serializeMessage 方法，
     * 获取消息对应的 JSON 字符串，用于模拟 Redis 读取
     */
    private String serializeMessage(Message message) {
        try {
            var method = RedisChatMemoryRepository.class.getDeclaredMethod(
                    "serializeMessage", Message.class, int.class);
            method.setAccessible(true);
            return (String) method.invoke(repository, message, 0);
        } catch (Exception e) {
            throw new RuntimeException("序列化消息失败", e);
        }
    }
}
