package com.example.ai.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * 基础AI对话接口
 * <p>
 * 访问地址：
 * 1. 基础对话(GET): http://localhost:8080/chat?message=你好
 * 2. 带角色设定(GET): http://localhost:8080/chat/role?message=帮我写一个冒泡排序
 * 3. 前端API-POST(非流式): http://localhost:8080/api/chat
 * 4. 前端API-POST(流式SSE): http://localhost:8080/api/chat/stream
 */
@Slf4j
@RestController
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 基础对话接口(GET方式，用于简单测试)
     */
    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        long t1 = System.currentTimeMillis();
        log.info("【T1】chat请求进入 message={}", message);

        String content = chatClient.prompt()
                .user(message)
                .call()
                .content();

        long t2 = System.currentTimeMillis();
        log.info("【T2】chat请求完成 耗时{}ms 回复==》{}", t2 - t1, content);
        return content;
    }

    /**
     * 带System Prompt的对话（AI扮演特定角色）
     */
    @GetMapping("/chat/role")
    public String chatWithRole(@RequestParam String message) {
        long t1 = System.currentTimeMillis();
        log.info("【T1】chatRole请求进入 message={}", message);

        String content = chatClient.prompt()
                .system("你是一个专业的Java技术顾问，回答要简洁、准确、有技术深度。")
                .user(message)
                .call()
                .content();

        long t2 = System.currentTimeMillis();
        log.info("【T2】chatRole请求完成 耗时{}ms 回复==》{}", t2 - t1, content);
        return content;
    }

    /**
     * 前端API接口(POST方式，非流式，返回JSON)
     * 带system prompt约束 + 控制回复长度
     */
    @PostMapping(value = "/api/chat", produces = "application/json;charset=UTF-8")
    public ChatResponse chatApi(@RequestBody ChatRequest request) {
        long t1 = System.currentTimeMillis();
        log.info("【API】chat请求进入 message={}, conversationId={}",
                request.getMessage(), request.getConversationId());
        if(Objects.equals(request.getMessage(),"ping")){
            ChatResponse resp = new ChatResponse();
            resp.setContent("ping ok");
            resp.setConversationId(request.getConversationId());
            resp.setTimestamp(System.currentTimeMillis());
            return resp;
        }

        String content = chatClient.prompt()
                .system("你是AI智能助手，回答要求：" +
                         "1. 简洁明了，单次回复控制在200字以内；" +
                         "2. 除非用户明确要求，不要输出代码块或SQL；" +
                         "3. 使用中文回答；" +
                         "4. 如果问题不清楚，先追问而不是假设。")
                .user(request.getMessage())
                .call()
                .content();

        long t2 = System.currentTimeMillis();
        log.info("【API】chat请求完成 耗时{}ms", t2 - t1);

        ChatResponse resp = new ChatResponse();
        resp.setContent(content);
        resp.setConversationId(request.getConversationId());
        resp.setTimestamp(System.currentTimeMillis());
        return resp;
    }

    /**
     * 前端API接口(POST方式，流式SSE返回)
     * 前端用 EventSource 或 fetch + ReadableStream 消费
     * 返回格式：text/event-stream
     */
    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatApiStream(@RequestBody ChatRequest request) {
        log.info("【API-STREAM】请求进入 message={}", request.getMessage());

        return chatClient.prompt()
                .system("你是AI智能助手，回答要求：" +
                         "1. 简洁明了，单次回复控制在200字以内；" +
                         "2. 除非用户明确要求，不要输出代码块或SQL；" +
                         "3. 使用中文回答；" +
                         "4. 如果问题不清楚，先追问而不是假设。")
                .user(request.getMessage())
                .stream()
                .content();
    }

    /**
     * 清空对话历史接口
     */
    @PostMapping(value = "/api/chat/clear", produces = "application/json;charset=UTF-8")
    public ClearResponse clearChat(@RequestBody ClearRequest request) {
        log.info("【API】清空对话历史 conversationId={}", request.getConversationId());
        // TODO: 实际清空逻辑需要在ChatMemory中实现
        return new ClearResponse(true, "对话历史已清空");
    }

    // ========== 内部DTO类 ==========

    @Data
    public static class ChatRequest {
        private String message;
        private String conversationId;
        private Boolean stream;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatResponse {
        private String content;
        private String conversationId;
        private Long timestamp;
    }

    @Data
    public static class ClearRequest {
        private String conversationId;
    }

    @Data
    public static class ClearResponse {
        private Boolean success;
        private String message;

        public ClearResponse(Boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
