package com.example.ai;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * ============ 两个 AI 各管各的事 ============
 * <p>
 * ChatClient（对话）     → DeepSeek（spring-ai-starter-model-openai 自动装配）
 * EmbeddingModel（向量化）→ Ollama nomic-embed-text（手动创建，本地运行，免费无限制）
 * VectorStore（向量存储）→ PgVectorStore（PostgreSQL + pgvector 持久化）
 * <p>
 * ============ 为什么用 Ollama 做 Embedding？ ============
 * 1. 智谱AI 余额不足，Ollama 本地免费
 * 2. nomic-embed-text 模型体积小、效果好，768维向量
 * 3. 只引入 spring-ai-ollama 核心 jar（不是 starter），
 *    避免 OllamaChatAutoConfiguration 自动配置与 DeepSeek ChatModel 冲突
 * <p>
 * ============ 为什么加 @Primary？ ============
 * spring-ai-starter-model-openai 会自动注册 OpenAiEmbeddingModel（即使 exclude 了其 AutoConfig）
 * PgVectorStoreAutoConfiguration 需要注入唯一的 EmbeddingModel，有两个时报错
 * @Primary 告诉 Spring：有歧义时优先用 ollamaEmbeddingModel
 */
@SpringBootApplication(exclude = {OpenAiEmbeddingAutoConfiguration.class})
public class SpringAiDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiDemoApplication.class, args);
    }

    @Primary   // 有多个 EmbeddingModel 时，PgVectorStore 自动配置优先用这个
    @Bean
    public OllamaEmbeddingModel ollamaEmbeddingModel() {
        System.out.println("【Bean初始化】创建 Ollama EmbeddingModel（nomic-embed-text）...");

        OllamaApi ollamaApi = OllamaApi.builder().build();

        OllamaEmbeddingModel embeddingModel = new OllamaEmbeddingModel(
                ollamaApi,
                OllamaOptions.builder().model("nomic-embed-text").build(),
                ObservationRegistry.NOOP,
                ModelManagementOptions.builder().build()
        );

        // 启动时测试连通性
        try {
            float[] result = embeddingModel.embed("连通测试");
            System.out.println("【Bean初始化】✅ Ollama Embedding 连通！向量维度=" + result.length);
        } catch (Exception e) {
            System.out.println("【Bean初始化】❌ Ollama Embedding 失败：" + e.getMessage());
            System.out.println("【Bean初始化】请确认：1) ollama 服务已启动  2) 已执行 ollama pull nomic-embed-text");
            e.printStackTrace();
        }

        return embeddingModel;
    }


}
