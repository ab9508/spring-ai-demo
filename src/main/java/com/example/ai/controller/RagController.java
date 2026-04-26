package com.example.ai.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.ai.service.RagService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG（检索增强生成）接口
 *
 * ============ 提供两个接口 ============
 * POST /rag/upload  - 上传 PDF，解析后存入向量库
 * GET  /rag/ask     - 基于向量库内容回答问题
 *
 * ============ 一次 ask 请求中两个 AI 的分工 ============
 * ① vectorStore.similaritySearch() → 内部调 智谱AI Embedding → 把问题转向量 → 检索相关片段
 * ② chatClient.prompt().call()     → 内部调 DeepSeek Chat    → 基于检索结果生成回答
 */
@RestController
@RequestMapping("/rag")
public class RagController {

    private final ChatClient chatClient;      // 对话（DeepSeek）
    private final VectorStore vectorStore;    // 向量检索（底层用智谱AI Embedding）
    private final RagService ragService;       // PDF处理服务

    public RagController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore,
                         @Autowired RagService ragService) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.ragService = ragService;
    }

    /**
     * 上传文档
     * POST http://localhost:8080/rag/upload
     * form-data: file=xxx.pdf
     */
    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) throws Exception {
        String result = ragService.uploadDocument(file);
        System.out.println("【upload】" + result);
        return result;
    }

    /**
     * RAG 问答
     * GET http://localhost:8080/rag/ask?question=你的问题
     *
     * 流程：
     * ① 用户提问
     * ② vectorStore.similaritySearch → 智谱AI把问题转向量 → 找最相关的文档片段
     * ③ 把片段拼成上下文
     * ④ chatClient → DeepSeek 基于上下文生成最终回答
     */
    @GetMapping("/ask")
    public String ask(@RequestParam String question) {
        // ① 向量检索：找最相关的 5 个文档片段
        //    这里内部会调智谱AI的embedding-3把question转成向量
        List<Document> relevantDocs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(5)
                        .build()
        );
        System.out.println("【ask】检索到 " + relevantDocs.size() + " 个相关片段");

        // ② 拼装上下文
        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        // ③ DeepSeek 基于上下文回答（这里是 ChatClient，走 DeepSeek）
        String content = chatClient.prompt()
                .system("你是一个专业知识库助手。请严格基于以下参考资料回答用户问题。" +
                        "如果参考资料中没有相关信息，请明确告知用户。" +
                        "不要编造参考资料之外的内容。\n\n" +
                        "参考资料：\n" + context)
                .user(question)
                .call()
                .content();
        System.out.println("【ask】回答内容==》" + content);
        return content;
    }
}
