package com.example.ai;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * ============ 两个 AI 各管各的事 ============
 * <p>
 * ChatClient（对话）     → DeepSeek
 * EmbeddingModel（向量化）→ 智谱AI（手动创建）
 * VectorStore（向量存储）→ SimpleVectorStore（内存）
 * <p>
 * ============ 为什么要手动创建 EmbeddingModel？ ============
 * spring-ai-starter-model-openai 只包含 Chat 自动装配，不包含 Embedding 自动装配。
 * 所以必须手动创建 EmbeddingModel Bean 指向智谱 AI。
 * <p>
 * ============ URL 拼接原理（踩坑关键）============
 * OpenAiApi 使用 Spring RestClient 构建 HTTP 客户端：
 * RestClient.builder().baseUrl(baseUrl).build()
 * <p>
 * 发请求时：
 * restClient.post().uri(embeddingsPath)
 * <p>
 * ⚠️ 如果 embeddingsPath 以 "/" 开头（绝对路径），RestClient 会忽略 baseUrl！
 * ⚠️ 导致请求直接发到 "/" + embeddingsPath，没有域名前缀，返回 404。
 * <p>
 * 所以：
 * baseUrl       = https://open.bigmodel.cn/api/paas/v4
 * embeddingsPath = embeddings    （不以 / 开头）
 * 最终 URL       = https://open.bigmodel.cn/api/paas/v4/embeddings  ✅
 */
@SpringBootApplication
public class SpringAiDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiDemoApplication.class, args);
    }

    @Value("${spring.ai.openai.embedding.api-key}")
    private String embeddingApiKey;

    @Bean
    public EmbeddingModel embeddingModel() {
        System.out.println("【Bean初始化】创建智谱AI EmbeddingModel...");

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(embeddingApiKey)
                .baseUrl("https://open.bigmodel.cn/api/paas/v4")
                .embeddingsPath("/embeddings")  // 不以 / 开头！否则 RestClient 会忽略 baseUrl
                .build();

        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model("embedding-3").build());

        // 启动时测试一次，确保能调通
        try {
            EmbeddingResponse response = embeddingModel.call(
                    new EmbeddingRequest(List.of("测试"), null));
            System.out.println("【Bean初始化】✅ 智谱AI Embedding 连通！向量维度="
                    + response.getResults().get(0).getOutput().length);
        } catch (Exception e) {
            System.out.println("【Bean初始化】❌ 智谱AI Embedding 失败：" + e.getMessage());
            e.printStackTrace();
        }

        return embeddingModel;
    }

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        System.out.println("【Bean初始化】创建 SimpleVectorStore");
        return SimpleVectorStore.builder(embeddingModel).build();
    }

}
