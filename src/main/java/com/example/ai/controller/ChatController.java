package com.example.ai.controller;

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
        System.out.println("【chat】message==>" + message);
        String content = chatClient.prompt()
                .user(message)
                .call()
                .content();
        System.out.println("【chat】return==>" + content);
        return content;
    }

    /**
     * 带System Prompt的对话（AI扮演特定角色）
     */
    @GetMapping("/chat/role")
    public String chatWithRole(@RequestParam String message) {
        System.out.println("【chat role】message==>" + message);
        String content = chatClient.prompt()
                .system("你是一个专业的Java技术顾问，回答要简洁、准确、有技术深度。")
                .user(message)
                .call()
                .content();
        System.out.println("【chat role】return==>" + content);
        return content;
    }
}
