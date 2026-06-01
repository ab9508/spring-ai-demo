package com.example.ai.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SummarizingChatMemory 单元测试
 * <p>
 * 覆盖对话摘要压缩的核心逻辑。
 * <p>
 * 对应功能测试案例：TC-06（多轮对话记忆验证）
 */
@ExtendWith(MockitoExtension.class)
class SummarizingChatMemoryTest {

    @Mock
    private ChatMemory delegate;

    @Mock
    private ChatClient summaryClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    private SummarizingChatMemory chatMemory;

    @BeforeEach
    void setUp() {
        // 模拟 ChatClient 链式调用
        when(summaryClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);

        chatMemory = new SummarizingChatMemory(delegate, summaryClient);
    }

    @Test
    @DisplayName("Smoke: add 方法应委托给底层 delegate")
    void addShouldDelegateToUnderlying() {
        String convId = "conv-001";
        List<Message> messages = List.of(new UserMessage("你好"));
        chatMemory.add(convId, messages);

        verify(delegate).add(convId, messages);
    }

    @Nested
    @DisplayName("触发压缩逻辑 — triggerCompressionIfNeeded")
    class CompressionTest {

        @Test
        @DisplayName("消息数未超过 MAX_MESSAGES(20) 时不触发压缩")
        void shouldNotCompressWhenUnderThreshold() {
            // 准备：15 条消息（< 20）
            List<Message> history = createMessages(15);
            when(delegate.get(anyString())).thenReturn(history);

            // 执行
            chatMemory.add("conv-under", List.of(new UserMessage("第16条")));

            // 验证：没有调用 clear 和 add（未触发压缩）
            verify(delegate, never()).clear(anyString());
            // 只调了 delegate.add() 一次（写入新消息）
            verify(delegate, times(1)).add(anyString(), anyList());
            // 没有调用 summaryClient（未触发 LLM 摘要）
            verify(summaryClient, never()).prompt();
        }

        @Test
        @DisplayName("消息数刚好等于 MAX_MESSAGES(20) 时不触发压缩")
        void shouldNotCompressWhenExactlyAtThreshold() {
            // 准备：20 条消息
            List<Message> history = createMessages(20);
            when(delegate.get(anyString())).thenReturn(history);

            chatMemory.add("conv-exact", List.of(new UserMessage("第21条")));

            verify(delegate, never()).clear(anyString());
        }

        @Test
        @DisplayName("消息数超过 MAX_MESSAGES 时调用 LLM 生成摘要并压缩")
        void shouldCompressWhenOverThreshold() {
            // 准备：25 条消息（> 20）
            List<Message> history = createMessages(25);
            when(delegate.get(anyString())).thenReturn(history);
            // LLM 返回摘要
            when(responseSpec.content()).thenReturn("用户咨询了订单状态和物流信息");

            // 执行：add 第26条消息
            chatMemory.add("conv-over", List.of(new UserMessage("新的消息")));

            // 验证：LLM 被调用了
            verify(summaryClient).prompt();
            verify(requestSpec).system(anyString());
            verify(requestSpec).user(contains("请压缩以下对话历史"));

            // 验证：清空旧历史 + 写入压缩后的
            verify(delegate).clear("conv-over");
            verify(delegate, times(2)).add(eq("conv-over"), anyList());
        }

        @Test
        @DisplayName("LLM 摘要失败时应跳过压缩，不影响后续使用")
        void shouldSkipCompressionWhenLLMFails() {
            // 准备：25 条消息
            List<Message> history = createMessages(25);
            when(delegate.get(anyString())).thenReturn(history);
            // LLM 返回 null（模拟失败）
            when(responseSpec.content()).thenReturn(null);

            chatMemory.add("conv-fail", List.of(new UserMessage("新消息")));

            // 验证：调用了 LLM 但失败了
            verify(summaryClient).prompt();
            // 没有清空（跳过压缩）
            verify(delegate, never()).clear(anyString());
        }

        @Test
        @DisplayName("LLM 抛出异常时应安全跳过压缩")
        void shouldSkipCompressionWhenLLMThrows() {
            // 准备：25 条消息
            List<Message> history = createMessages(25);
            when(delegate.get(anyString())).thenReturn(history);
            // LLM 抛异常
            when(responseSpec.content()).thenThrow(new RuntimeException("API超时"));

            // 应该不抛异常地下发
            assertDoesNotThrow(() ->
                    chatMemory.add("conv-error", List.of(new UserMessage("新消息"))));

            // 没有清空（跳过压缩）
            verify(delegate, never()).clear(anyString());
        }

        @Test
        @DisplayName("压缩后消息列表结构应为：[摘要SystemMessage] + [近期的KEEP_RECENT条]")
        void afterCompressionStructureShouldBeCorrect() throws Exception {
            // 准备：30 条消息（触发压缩，保留最近10条）
            List<Message> history = createMessages(30);
            when(delegate.get(anyString())).thenReturn(history);
            when(responseSpec.content()).thenReturn("摘要内容");

            // 执行
            chatMemory.add("conv-struct", List.of(new UserMessage("新消息")));

            // 验证：第一次 add（写入新消息） + 第二次 add（写入压缩后消息）
            verify(delegate, times(2)).add(eq("conv-struct"), anyList());

            // 验证 clear 被调用
            verify(delegate).clear("conv-struct");
        }
    }

    @Nested
    @DisplayName("get / clear 操作")
    class GetAndClearTest {

        @Test
        @DisplayName("get 应委托给底层 delegate")
        void getShouldDelegate() {
            String convId = "conv-get";
            when(delegate.get(convId)).thenReturn(List.of(new UserMessage("历史")));

            List<Message> result = chatMemory.get(convId);

            assertEquals(1, result.size());
            verify(delegate).get(convId);
        }

        @Test
        @DisplayName("clear 应委托给底层 delegate")
        void clearShouldDelegate() {
            chatMemory.clear("conv-clear");
            verify(delegate).clear("conv-clear");
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建指定数量的消息（USER/ASSISTANT 交替）
     */
    private List<Message> createMessages(int count) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                messages.add(new UserMessage("用户消息" + (i / 2 + 1)));
            } else {
                messages.add(new AssistantMessage("AI回复" + ((i + 1) / 2)));
            }
        }
        return messages;
    }
}
