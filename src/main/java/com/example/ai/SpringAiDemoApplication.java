package com.example.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ============ 双模型配置 ============
 * <p>
 * Chat（对话）：Ollama (deepseek-r1:8b) 或 DeepSeek API (deepseek-chat)
 *   → 通过 app.chat.provider 开关切换
 * EmbeddingModel（向量化）→ Ollama (nomic-embed-text)，本地免费
 * VectorStore（向量存储）→ PgVectorStore（PostgreSQL + pgvector 持久化）
 * <p>
 * ============ 为什么用 Ollama 做 Embedding？ ============
 * 1. 智谱AI 余额不足，Ollama 本地免费
 * 2. nomic-embed-text 模型体积小、效果好，768维向量
 * 3. 只引入 spring-ai-ollama 核心 jar（不是 starter），
 * 避免 OllamaChatAutoConfiguration 自动配置与 DeepSeek ChatModel 冲突
 * <p>
 * ============ 为什么加 @Primary？ ============
 * spring-ai-starter-model-openai 会自动注册 OpenAiEmbeddingModel（即使 exclude 了其 AutoConfig）
 * PgVectorStoreAutoConfiguration 需要注入唯一的 EmbeddingModel，有两个时报错
 *应该也不用scanBasePackages扫包
 * @Primary 告诉 Spring：有歧义时优先用 ollamaEmbeddingModel
 * <p>
 * ============ 关键配置 ============
 * 1. exclude ChatClientAutoConfiguration：禁用自动 ChatClient，由 ChatClientConfig 手动创建
 * 2. exclude OpenAiEmbeddingAutoConfiguration：禁用 OpenAI Embedding，只用 Ollama 的
 * 3. ChatClientConfig 根据 app.chat.provider 决定注入哪个 ChatModel
 */
@Slf4j
@SpringBootApplication(exclude = {
        ChatClientAutoConfiguration.class,
        OpenAiEmbeddingAutoConfiguration.class
})
public class SpringAiDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiDemoApplication.class, args);
    }

//    @Primary   // 有多个 EmbeddingModel 时，PgVectorStore 自动配置优先用这个
//    @Bean
//    public OllamaEmbeddingModel ollamaEmbeddingModel() {
//        log.info("【Bean初始化】创建 Ollama EmbeddingModel（nomic-embed-text）...");
//
//        OllamaApi ollamaApi = OllamaApi.builder().build();
//
//        OllamaEmbeddingModel embeddingModel = new OllamaEmbeddingModel(
//                ollamaApi,
//                OllamaOptions.builder().model("nomic-embed-text").build(),
//                ObservationRegistry.NOOP,
//                ModelManagementOptions.builder().build()
//        );
//
//        // 启动时测试连通性
//        try {
//            float[] result = embeddingModel.embed("连通测试");
//            log.info("【Bean初始化】✅ Ollama Embedding 连通！向量维度=" + result.length);
//        } catch (Exception e) {
//            log.info("【Bean初始化】❌ Ollama Embedding 失败：" + e.getMessage());
//            log.info("【Bean初始化】请确认：1) ollama 服务已启动  2) 已执行 ollama pull nomic-embed-text");
//            e.printStackTrace();
//        }
//
//        return embeddingModel;
//    }


}
