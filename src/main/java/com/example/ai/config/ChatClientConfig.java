package com.example.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.Executor;

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
     * provider=ollama → 返回 DegradationChatModel（先本地，失败切 DeepSeek API）
     * provider=openai → 直接走 DeepSeek API（不需要降级）
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "selectedChatModel")
    public ChatModel selectedChatModel(
            @Qualifier("ollamaChatModel") ChatModel ollamaModel,
            @Qualifier("openAiChatModel") ChatModel openaiModel,
            @Value("${app.chat.provider:ollama}") String provider,
            @Value("${app.degradation.timeout:15s}") Duration timeout,
            @Value("${app.degradation.fallback:服务繁忙，请稍后再试}") String fallback) {

        if ("openai".equalsIgnoreCase(provider)) {
            log.info("【配置】ChatModel → DeepSeek API (openAiChatModel) — 直接使用，无降级");
            return openaiModel;
        } else {
            log.info("【配置】ChatModel → DegradationChatModel（本地={} → 失败降级 → DeepSeek API）",
                    ollamaModel.getClass().getSimpleName());
            return new DegradationChatModel(ollamaModel, openaiModel, timeout, fallback);
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

    /**
     * 异步日志写入线程池
     */
    @Bean("logExecutor")
    public Executor logExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("log-async-");
        executor.setRejectedExecutionHandler((r, e) ->
                log.warn("日志队列已满，丢弃一条日志记录"));
        executor.initialize();
        return executor;
    }
}
