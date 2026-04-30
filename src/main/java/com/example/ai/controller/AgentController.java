package com.example.ai.controller;

import com.example.ai.tool.OrderTools;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tool Calling 智能体控制器（含多轮对话记忆）
 * <p>
 * ============ ChatMemory 实现原理 ============
 * <p>
 * 1. ChatMemory（MessageWindowChatMemory）维护一个消息窗口，默认保留最近 20 条消息
 * 2. MessageChatMemoryAdvisor 是 ChatClient 的拦截器：
 *    - 请求前：从 ChatMemory 加载历史消息，注入到 prompt
 *    - 响应后：把本次用户消息和 AI 回复保存到 ChatMemory
 * 3. 通过 CONVERSATION_ID 区分不同用户的对话
 * <p>
 * ============ 调用流程 ============
 * <p>
 * 第1次请求："查ORD-001" → AI 回答"ORD-001已发货"
 * 第2次请求："到哪了？"   → Advisor 自动加载第1次对话 → AI 理解"到哪了"指 ORD-001
 */
@Slf4j
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final ChatClient chatClient;
    @Resource
    private VectorStore vectorStore;

    // 注入 OrderTools 实例 + ChatMemory
    public AgentController(ChatClient.Builder chatClientBuilder, OrderTools orderTools, ChatMemory chatMemory) {
        log.info("当前使用的 ChatMemory 实现类：" + chatMemory.getClass().getName());
        this.chatClient = chatClientBuilder
                .defaultTools(orderTools)   // 传实例，Spring AI 自动扫描其 @Tool 方法
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()  // Builder 模式构造
                )
                .build();
        log.info("【agent】ChatClient 初始化完成，已挂载 ChatMemory");
    }

    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "default") String conversationId,
                       @RequestParam String message) {
        // T1: 请求进入
        long t1 = System.currentTimeMillis();
        log.info("【T1】请求进入 conversationId={}, message={}", conversationId, message);

        // RAG 检索：从向量数据库查找相关文档
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(message)
                        .topK(1)
                        .build()
        );// 查询结果可能为空
        long t2 = System.currentTimeMillis();
        log.info("【T2】RAG检索完成 耗时{}ms 检索到{}个片段", t2 - t1, documents.size());
        String context = documents.stream().map(Document::getText)
                .collect(Collectors.joining("\n\n--\n\n"));

        // 使用前端的 conversationId，如果没传就用 "default"
        String finalConversationId = conversationId;

        String content = chatClient.prompt()
                .system("你是一个电商智能客服助手。" +
                        "你可以帮助用户查询订单状态、推荐商品、处理售后问题。" +
                        "当用户提到订单号或订单相关问题时，使用工具查询订单信息后再回答。" +
                        "参考资料：\n" + context)
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, finalConversationId))  // 传入会话ID
                .call()
                .content();
        long t3 = System.currentTimeMillis();
        log.info("【T3】ChatClient.call()完成 耗时{}ms (含DeepSeek调用+ChatMemory读写)", t3 - t2);

        log.info("【agent】回复==》{}", content);

        // T4: 方法结束
        long t4 = System.currentTimeMillis();
        log.info("【T4】请求完成 总耗时{}ms (T1→T2: RAG {}ms | T2→T3: LLM {}ms | T3→T4: 序列化 {}ms)",
                t4 - t1, t2 - t1, t3 - t2, t4 - t3);

        return content;
    }
}
