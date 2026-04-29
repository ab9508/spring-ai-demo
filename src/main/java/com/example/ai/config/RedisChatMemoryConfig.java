package com.example.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis ChatMemory 配置类
 * <p>
 * 注册两个 Bean：
 * 1. RedisChatMemoryRepository — 手写的 Redis 存储（实现 ChatMemoryRepository 接口）
 * 2. ChatMemory — 基于 RedisChatMemoryRepository 的 MessageWindowChatMemory
 * <p>
 * 此配置生效后，Spring AI 自动配置的 InMemoryChatMemory 将被覆盖，
 * AgentController 中注入的 ChatMemory 将使用 Redis 持久化存储。
 */
@Slf4j
@Configuration
public class RedisChatMemoryConfig {

    @Bean
    public RedisChatMemoryRepository redisChatMemoryRepository(StringRedisTemplate redisTemplate) {
        log.info("【RedisChatMemory】初始化 RedisChatMemoryRepository");
        return new RedisChatMemoryRepository(redisTemplate);
    }

    @Bean
    public ChatMemory chatMemory(RedisChatMemoryRepository redisChatMemoryRepository) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(20)  // 每个会话最多保留 20 条消息
                .build();
        log.info("【RedisChatMemory】ChatMemory 已切换到 Redis 存储，maxMessages=20");
        return chatMemory;
    }
}
