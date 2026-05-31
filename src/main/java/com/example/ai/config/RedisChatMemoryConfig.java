package com.example.ai.config;

import com.example.ai.memory.SummarizingChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis ChatMemory 配置类
 * <p>
 * 注册两个 Bean：
 * 1. RedisChatMemoryRepository — 手写的 Redis 存储（实现 ChatMemoryRepository 接口）
 * 2. ChatMemory — SummarizingChatMemory（带摘要压缩），包装底层 Redis 存储
 * <p>
 * 此配置生效后，Spring AI 自动配置的 InMemoryChatMemory 将被覆盖，
 * AgentController 中注入的 ChatMemory 将使用带摘要压缩的 Redis 持久化存储。
 */
@Slf4j
@Configuration
@Profile("!mcp-server")
public class RedisChatMemoryConfig {

    @Bean
    public RedisChatMemoryRepository redisChatMemoryRepository(StringRedisTemplate redisTemplate) {
        log.info("【RedisChatMemory】初始化 RedisChatMemoryRepository");
        return new RedisChatMemoryRepository(redisTemplate);
    }

    /**
     * 带摘要压缩的 ChatMemory Bean。
     * <p>
     * 使用装饰器模式：
     * SummarizingChatMemory（负责压缩触发）
     *   └── MessageWindowChatMemory（负责窗口管理）
     *         └── RedisChatMemoryRepository（负责 Redis 存储）
     * <p>
     * 参数直接注入具体类 RedisChatMemoryRepository，避免与 Spring AI 自动配置的
     * InMemoryChatMemoryRepository（同样实现 ChatMemoryRepository）产生歧义。
     */
    @Bean
    public ChatMemory chatMemory(RedisChatMemoryRepository redisChatMemoryRepository,
                                 ChatModel chatModel) {
        // 底层 Redis 存储，maxMessages = MAX_VALUE（不在此限制，由 SummarizingChatMemory 控制）
        ChatMemory redisBased = MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(Integer.MAX_VALUE)
                .build();

        // 纯净 Client（无工具），专门用于摘要生成
        ChatClient summaryClient = ChatClient.builder(chatModel).build();

        log.info("【RedisChatMemory】ChatMemory 已切换到 SummarizingChatMemory（Redis + 摘要压缩）");
        // 带摘要压缩的 Memory（装饰器，包装 Redis 存储）
        return new SummarizingChatMemory(redisBased, summaryClient);
    }
}

