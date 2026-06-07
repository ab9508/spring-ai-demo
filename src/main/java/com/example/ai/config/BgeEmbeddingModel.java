package com.example.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

/**
 * BGE Embedding 模型包装器——为输入文本添加任务前缀。
 */
@Slf4j
public class BgeEmbeddingModel implements EmbeddingModel {

    private static final String QUERY_INSTRUCTION = "为这个句子生成表示以用于检索相关文章：";

    private final EmbeddingModel delegate;

    public BgeEmbeddingModel(EmbeddingModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public float[] embed(String text) {
        return delegate.embed(addPrefix(text));
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return delegate.embed(texts.stream().map(this::addPrefix).toList());
    }

    @Override
    public EmbeddingResponse embedForResponse(List<String> texts) {
        return delegate.embedForResponse(texts.stream().map(this::addPrefix).toList());
    }

    @Override
    public float[] embed(Document document) {
        return delegate.embed(addPrefix(document.getText()));
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> prefixed = request.getInstructions().stream()
                .map(this::addPrefix)
                .toList();
        EmbeddingRequest prefixedRequest = new EmbeddingRequest(prefixed, request.getOptions());
        return delegate.call(prefixedRequest);
    }

    private String addPrefix(String text) {
        if (text == null || text.startsWith(QUERY_INSTRUCTION)) {
            return text;
        }
        return QUERY_INSTRUCTION + text;
    }
}
