package com.example.ai.controller;

import com.example.ai.entity.RagResponse;
import com.example.ai.service.PromptGuardService;
import com.example.ai.service.QueryRewriteService;
import com.example.ai.service.RagService;
import com.example.ai.service.ReRankService;
import com.example.ai.service.SemanticCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG（检索增强生成）接口
 * <p>
 * ============ 提供的接口 ============
 * POST /rag/upload     - 上传文档（支持 PDF/Word/Excel/PPT/TXT 等多格式）
 * GET  /rag/ask        - 基于向量库内容回答问题
 * GET  /rag/formats    - 查询支持的文件格式
 * GET  /rag/getDocument - 获取知识库检索结果（调试用）
 * <p>
 * ============ 文档格式支持 ============
 * - PDF：PagePdfDocumentReader（按页读取，中文排版保留好）
 * - Word/Excel/PPT/TXT/HTML 等：TikaDocumentReader（Apache Tika 通用解析）
 * <p>
 * ============ 一次 ask 请求中两个 AI 的分工 ============
 * ① vectorStore.similaritySearch() → 内部调 Ollama nomic-embed-text → 把问题转向量 → 检索相关片段
 * ② chatClient.prompt().call()     → 内部调 DeepSeek Chat    → 基于检索结果生成回答
 */
@Slf4j
@RestController
@RequestMapping("/rag")
public class RagController {

    private final ChatClient chatClient;      // 对话（DeepSeek）
    private final VectorStore vectorStore;    // 向量检索（底层用 Ollama nomic-embed-text）
    private final RagService ragService;      // 文档处理服务
    private final PromptGuardService promptGuardService;
    private final SemanticCacheService semanticCacheService;
    private final QueryRewriteService queryRewriteService;
    private final ReRankService reRankService;

    public RagController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore,
                         @Autowired RagService ragService,
                         PromptGuardService promptGuardService,
                         SemanticCacheService semanticCacheService,
                         QueryRewriteService queryRewriteService,
                         ReRankService reRankService) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.ragService = ragService;
        this.promptGuardService = promptGuardService;
        this.semanticCacheService = semanticCacheService;
        this.queryRewriteService = queryRewriteService;
        this.reRankService = reRankService;
    }

    /**
     * 查询支持的文件格式
     * GET http://localhost:8080/rag/formats
     */
    @GetMapping("/formats")
    public Map<String, Object> supportedFormats() {
        Map<String, Object> result = new HashMap<>();
        result.put("supportedFormats", RagService.getSupportedFormats());
        result.put("total", RagService.getSupportedFormats().size());
        result.put("pdfReader", "PagePdfDocumentReader（按页读取，中文排版保留好）");
        result.put("otherReader", "TikaDocumentReader（Apache Tika 通用解析）");
        return result;
    }

    /**
     * 上传文档（支持多格式）
     * POST http://localhost:8080/rag/upload
     * form-data: file=xxx.pdf / file=xxx.docx / file=xxx.txt
     * <p>
     * 支持：pdf, doc, docx, xls, xlsx, ppt, pptx, txt, html, htm, md, csv, json, xml
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String upload(@RequestParam("file") MultipartFile file) throws Exception {
        long t1 = System.currentTimeMillis();
        log.info("【T1】文档上传开始 filename={} size={}", file.getOriginalFilename(), file.getSize());
        String result = ragService.uploadDocument(file);
        long t2 = System.currentTimeMillis();
        log.info("【T2】文档上传完成 耗时{}ms result={}", t2 - t1, result);
        return result;
    }

    /**
     * RAG 问答
     * GET http://localhost:8080/rag/ask?question=你的问题
     * <p>
     * 返回格式（JSON）：
     * - 正常回答：{"type":"answer", "content":"...", "reason":null}
     * - 转人工：  {"type":"handoff", "content":"...", "reason":"知识库无匹配结果"}
     * <p>
     * 流程：
     * ① 用户提问
     * ② filterByRelativeScore → 三规则过滤（绝对值/分差/相对分数）
     * ③ 无匹配 → 直接返回转人工，不走 LLM
     * ④ 有匹配 → DeepSeek 基于上下文生成回答
     */
    @GetMapping("/ask")
    public RagResponse ask(@RequestParam String question) {
        PromptGuardService.GuardResult guard = promptGuardService.check(question);
        if (guard.isBlocked()) {
            return RagResponse.handoff(guard.reason(), "Prompt注入被拦截");
        }

        long t1 = System.currentTimeMillis();
        log.info("【T1】rag/ask请求进入 question={}", question);

        // ① 语义缓存查询（相同/相似问题直接返回，省一次 LLM 调用）
        String cached = semanticCacheService.get(question);
        if (cached != null) {
            log.info("【T1-cache】语义缓存命中, 省去向量检索+LLM调用");
            return RagResponse.answer(cached);
        }

        // ② 查询改写（消解指代、补全模糊查询）
        String rewrittenQuery = queryRewriteService.rewrite(question, "");
        log.info("【T2-rewrite】改写前='{}' 改写后='{}'", question, rewrittenQuery);

        // ③ 相对分数过滤检索（三规则：绝对值<0.45拒绝 / 分差<0.05拒绝 / 只保留top1*0.85以上chunk）
        List<Document> filteredDocs = ragService.filterByRelativeScore(rewrittenQuery);
        long t2 = System.currentTimeMillis();
        log.info("【T3】相对分数过滤完成 耗时{}ms 通过{}个片段", t2 - t1, filteredDocs.size());

        // ====== 知识库无匹配 → 转人工 ======
        if (filteredDocs.isEmpty()) {
            log.warn("【转人工】知识库无匹配结果 question='{}' rewritten='{}'", question, rewrittenQuery);
            return RagResponse.handoff(
                    "抱歉，知识库中没有找到相关的信息，已为您转接人工客服处理。"
            );
        }

        // ④ 拼装上下文
        String context = filteredDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        // ⑤ DeepSeek 基于上下文回答
        String content = chatClient.prompt()
                .system("你是一个专业知识库助手。请严格基于以下参考资料回答用户问题。" +
                        "如果参考资料中没有相关信息，请明确告知用户。" +
                        "不要编造参考资料之外的内容。\n\n" +
                        "参考资料：\n" + context)
                .user(question)
                .call()
                .content();
        long t3 = System.currentTimeMillis();

        // ⑥ 写入语义缓存（供后续相同/相似问题直接命中）
        if (content != null && !content.isBlank()) {
            semanticCacheService.put(question, content);
        }

        log.info("【ask】回复==》{}", content);
        log.info("【T3】rag/ask请求完成 总耗时{}ms (检索过滤:{}ms | DeepSeek生成:{}ms)",
                t3 - t1, t2 - t1, t3 - t2);
        return RagResponse.answer(content);
    }

    /**
     * 获取知识库检索结果（调试用，含相对分数过滤）
     */
    @GetMapping("/getDocument")
    public List<Document> getDocument(@RequestParam String question) {
        return ragService.filterByRelativeScore(question);
    }

    /**
     * 调试：原始向量检索结果（不加过滤，看BGE实际分数分布）
     */
    @GetMapping("/debug")
    public String debug(@RequestParam String question) {
        List<Document> docs = vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.builder()
                        .query(question)
                        .topK(5)
                        .similarityThreshold(0.0)
                        .build()
        );
        StringBuilder sb = new StringBuilder();
        sb.append("查询：").append(question).append("\n\n");
        if (docs.isEmpty()) {
            sb.append("结果：空（无匹配文档）\n");
        } else {
            for (int i = 0; i < docs.size(); i++) {
                Document d = docs.get(i);
                sb.append("--- 结果 ").append(i + 1).append(" ---\n");
                sb.append("分数: ").append(String.format("%.4f", d.getScore())).append("\n");
                sb.append("内容: ").append(d.getText().substring(0, Math.min(150, d.getText().length()))).append("\n\n");
            }
        }
        return sb.toString();
    }
}
