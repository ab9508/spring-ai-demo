package com.example.ai.controller;

import com.example.ai.advisor.CustomRagAdvisor;
import com.example.ai.entity.IntentRecord;
import com.example.ai.tool.OrderTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * Tool Calling 智能体控制器（含多轮对话记忆 + 结构化输出）
 * <p>
 * ============ ChatMemory 实现原理 ============
 * <p>
 * 1. ChatMemory（MessageWindowChatMemory）维护一个消息窗口，默认保留最近 20 条消息
 * 2. MessageChatMemoryAdvisor 是 ChatClient 的拦截器：
 * - 请求前：从 ChatMemory 加载历史消息，注入到 prompt
 * - 响应后：把本次用户消息和 AI 回复保存到 ChatMemory
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

    private final ChatClient chatClient;          // 带工具+记忆+RAG的（用于 /chat）
    private final ChatClient jsonOnlyClient;       // 纯净的（用于 /analyzeIntent）
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 注入 OrderTools 实例 + ChatMemory + ChatModel + VectorStore
    public AgentController(ChatClient.Builder chatClientBuilder,
                          OrderTools orderTools,
                          ChatMemory chatMemory,
                          ChatModel chatModel,
                          VectorStore vectorStore) {
        log.info("当前使用的 ChatMemory 实现类：" + chatMemory.getClass().getName());

        // jsonOnlyClient：用 ChatModel 创建全新 builder，完全不受下面工具配置的影响
        // ChatClient.builder(chatModel) 每次返回全新的 Builder，无任何历史配置
        this.jsonOnlyClient = ChatClient.builder(chatModel).build();
        log.info("【agent】jsonOnlyClient 初始化完成（无工具/无记忆，专用于结构化输出）");

        // chatClient：带 Tool Calling + ChatMemory + 自定义 RAG Advisor
        this.chatClient = chatClientBuilder
                .defaultTools(orderTools)
                .defaultAdvisors(
                        // advisor执行顺序受order影响，与代码先后无关
                        // 自定义 RAG Advisor：自动检索向量库并注入上下文（含相对分数过滤）
                        new CustomRagAdvisor(vectorStore, 3, 0.3),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                        )
                .build();
        log.info("【agent】ChatClient 初始化完成，已挂载 ChatMemory + OrderTools + 自定义RAG Advisor");
    }

    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "default") String conversationId,
                       @RequestParam String message) {
        long t1 = System.currentTimeMillis();
        log.info("【T1】请求进入 conversationId={}, message={}", conversationId, message);

        // RAG 检索已由 QuestionAnswerAdvisor 自动完成，无需手动调用
        // Advisor 会在 before 阶段自动检索向量库并注入上下文到 system prompt

        String content = chatClient.prompt()
                .system("你是一个电商智能客服助手。" +
                        "你可以帮助用户查询订单状态、推荐商品、处理售后问题。" +
                        "当用户提到订单号或订单相关问题时，使用工具查询订单信息后再回答。")
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        long t2 = System.currentTimeMillis();
        log.info("【T2】请求完成 总耗时{}ms", t2 - t1);

        log.info("【agent】回复==》{}", content);
        return content;
    }

    /**
     * 结构化输出演示：意图识别
     * <p>
     * Spring AI 1.0.5 没有 entity(Class) 这个 API，
     * 所以让 LLM 返回 JSON 字符串，再用 Jackson 手动反序列化。
     * 这是最稳妥的方案，不依赖 Spring AI 内部结构。
     *
     * 	LLM: Large Language Model，大语言模型。本项目用的是 DeepSeek-V3（通过 API 调用），本质是一个概率模型，根据输入文本预测下一个 token
     */
    @GetMapping("/analyzeIntent")
    public IntentRecord analyzeIntent(@RequestParam String message) {
        log.info("【结构化输出】analyzeIntent 请求进入 message={}", message);

        // === 关键：使用 jsonOnlyClient（无工具/无记忆），避免 DeepSeek 调用 OrderTools ===
        // 如果用 chatClient，DeepSeek 看到 OrderTools 会直接查订单并返回完整回复，而不是 JSON

        String systemPrompt = """
                你是电商客服系统的意图识别模块，你的唯一职责是识别用户意图并返回 JSON。
                
                严格按以下 JSON 格式返回，不要添加任何其他文字、解释或格式：
                {"intent":"意图类型","params":"参数或null","confidence":置信度数字,"userMessage":"原始消息"}
                
                字段说明：
                - intent: 必填，可选值 query_order / query_stock / after_sale / rag_query / unknown
                - params: 提取到的参数（如订单号），JSON字符串格式，没有则为 null
                - confidence: 0到1之间的数字
                - userMessage: 原样返回用户消息
                
                禁止事项：
                1. 禁止调用任何工具
                2. 禁止查询订单
                3. 禁止回答用户问题
                4. 只返回JSON，不要用markdown代码块包裹
                """;

        String jsonResponse = jsonOnlyClient.prompt()
                .system(systemPrompt)
                .user(message)
                .call()
                .content();

        log.info("【结构化输出】LLM 原始返回: {}", jsonResponse);

        try {
            String cleaned = cleanJsonResponse(jsonResponse);
            log.info("【结构化输出】清洗后: {}", cleaned);
            return objectMapper.readValue(cleaned, IntentRecord.class);
        } catch (Exception e) {
            log.error("【结构化输出】解析失败，原始返回: {}", jsonResponse, e);
            return new IntentRecord("unknown", null, 0.0, message);
        }
    }

    /**
     * 清洗 DeepSeek 返回的 JSON 文本
     * <p>
     * DeepSeek 常见不可控格式：
     * 1. 正常 JSON: {"intent":"..."}
     * 2. markdown 代码块: ```json\n{"intent":"..."}\n```
     * 3. 带前后文: "以下是结果：\n```json\n{...}\n```\n希望对你有帮助"
     * 4. 纯文本包裹: "结果为：{\"intent\":\"...\"}"
     */
    private String cleanJsonResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("LLM 返回为空");
        }

        String text = raw.trim();

        // 去掉 markdown 代码块标记（```json 或 ``` 等）
        if (text.startsWith("```")) {
            // 去掉第一行的 ```json
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1);
            }
            // 去掉结尾的 ```
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3).trim();
            }
        }

        // 兜底：提取第一个 { 到最后一个 } 之间的内容
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            text = text.substring(firstBrace, lastBrace + 1);
        } else {
            throw new IllegalArgumentException("未找到 JSON 对象: " + raw);
        }

        return text;
    }

}
