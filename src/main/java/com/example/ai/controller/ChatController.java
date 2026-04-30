package com.example.ai.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基础AI对话接口
 * <p>
 * 访问地址：
 * 1. 基础对话: http://localhost:8080/chat?message=你好
 * 2. 带角色设定: http://localhost:8080/chat/role?message=帮我写一个冒泡排序
 */
@Slf4j
@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 基础对话接口
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
}
