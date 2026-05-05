package com.example.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * ChatClient 配置：根据 app.chat.provider 开关决定注入哪个模型
 * <p>
 * 已排除 ChatClientAutoConfiguration，此配置类提供所有 ChatClient 相关 Bean：
 * - chatClient：基础对话用（ChatController 注入）
 * - ChatClient.Builder：Agent 构建用（AgentController 注入）
 * <p>
 * app.chat.provider=ollama  → 使用 ollamaChatModel（本地免费，默认）
 * app.chat.provider=openai  → 使用 openAiChatModel（DeepSeek API）
 */
@Slf4j
@Configuration
public class ChatClientConfig {

    /**
     * 根据 app.chat.provider 选择实际的 ChatModel（@Primary 解决多 Bean 歧义）
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "selectedChatModel")
    public ChatModel selectedChatModel(
            @Qualifier("ollamaChatModel") ChatModel ollamaModel,
            @Qualifier("openAiChatModel") ChatModel openaiModel,
            @Value("${app.chat.provider:ollama}") String provider) {

        if ("openai".equalsIgnoreCase(provider)) {
            log.info("【配置】ChatModel → DeepSeek API (openAiChatModel)");
            return openaiModel;
        } else {
            log.info("【配置】ChatModel → Ollama 本地 (ollamaChatModel, deepseek-r1:8b)");
            return ollamaModel;
        }
    }

    /**
     * ChatClient Bean：ChatController 直接注入使用
     */
    @Bean
    public ChatClient chatClient(ChatModel selectedChatModel) {
        return ChatClient.builder(selectedChatModel).build();
    }

    /**
     * ChatClient.Builder Bean：AgentController 用来自定义构建（挂载 Tools、Advisors）
     * ChatClientAutoConfiguration 被排除后，需要手动提供
     */
    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel selectedChatModel) {
        return ChatClient.builder(selectedChatModel);
    }
}
