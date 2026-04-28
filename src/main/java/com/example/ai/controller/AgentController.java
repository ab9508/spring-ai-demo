package com.example.ai.controller;

import com.example.ai.tool.OrderTools;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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
 * Tool Calling 智能体控制器
 * <p>
 * ============ defaultTools() 参数变化说明 ============
 * <p>
 * 旧写法（报错）：
 * chatClientBuilder.defaultTools("queryOrder")   ← 传 Bean 名称字符串
 * 此方式要求传入的是带 @Tool 注解的对象，字符串代表 Spring Bean 名称，
 * 但 Spring 会去找该 Bean 上的 @Tool 方法，旧的 Function Bean 没有 @Tool，所以报错。
 * <p>
 * 新写法（正确）：
 * chatClientBuilder.defaultTools(OrderTools.class)  ← 传 Class（Spring AI 自动扫描 @Tool 方法）
 * 或
 * chatClientBuilder.defaultTools(orderTools)         ← 传已注入的实例
 * <p>
 * 推荐传实例（方便 Spring 管理依赖注入，Service 注入到 OrderTools 也没问题）
 */
@Slf4j
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final ChatClient chatClient;
    @Resource
    private VectorStore vectorStore;

    // 注入 OrderTools 实例（Spring 管理，方便工具类内部注入 Service）
    public AgentController(ChatClient.Builder chatClientBuilder, OrderTools orderTools) {
        this.chatClient = chatClientBuilder
                .defaultTools(orderTools)   // 传实例，Spring AI 自动扫描其 @Tool 方法
                .build();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(message)
                        .topK(1)
                        .build()
        );
        log.info("【agent】检索到{}个相关片段", documents.size());
        String context = documents.stream().map(Document::getText)
                .collect(Collectors.joining("\n\n--\n\n"));


        String content = chatClient.prompt()
                .system("你是一个电商智能客服助手。" +
                        "你可以帮助用户查询订单状态、推荐商品、处理售后问题。" +
                        "当用户提到订单号或订单相关问题时，使用工具查询订单信息后再回答。" +
                        "参考资料：\n" + context)
                .user(message)
                .call()
                .content();
        log.info("【agent】回复==》{}", content);
        return content;
    }
}
