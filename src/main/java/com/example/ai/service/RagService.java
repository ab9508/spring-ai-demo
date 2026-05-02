package com.example.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * RAG 核心服务
 * <p>
 * ============ RAG 整体流程 ============
 * <p>
 * 离线阶段（本类 uploadDocument）：
 * PDF文件 → PagePdfDocumentReader按页读取 → TokenTextSplitter分段
 * → EmbeddingModel文本转向量(Ollama nomic-embed-text) → PgVectorStore存储到PostgreSQL
 * <p>
 * 在线阶段（RagController.ask 调用 vectorStore.similaritySearch）：
 * 用户问题 → EmbeddingModel转向量(Ollama nomic-embed-text) → PgVectorStore相似度检索
 * → 拼装上下文 → ChatClient生成回答(DeepSeek)
 * <p>
 * ============ 数据持久化 ============
 * 向量数据存储在 PostgreSQL + pgvector 扩展，应用重启后数据不丢失。
 * 无需重新 upload PDF。
 */
@Slf4j
@Service
public class RagService {

    private final VectorStore vectorStore;

    // PDF 文件存放目录（改为你自己的路径，确保目录存在且有写入权限）
    private static final String UPLOAD_DIR = "D:\\test";

    public RagService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 上传PDF文档并处理入库
     *
     * @param file 上传的 PDF 文件
     * @return 处理结果
     */
    public String uploadDocument(MultipartFile file) throws IOException {
        // 1. 确保上传目录存在
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 2. 保存到固定目录（用简单文件名，避免特殊字符导致 reader 读取失败）
        String originalFilename = file.getOriginalFilename();
        String fileName = (originalFilename != null && originalFilename.endsWith(".pdf"))
                ? originalFilename : "upload.pdf";
        // 文件名加时间戳前缀，避免重复
        Path savedFile = uploadPath.resolve(System.currentTimeMillis() + "_" + fileName);

        log.info("【upload】保存文件到: " + savedFile.toAbsolutePath());
        file.transferTo(savedFile.toFile());

        // 验证文件确实存在
        log.info("【upload】文件是否存在: " + Files.exists(savedFile));
        log.info("【upload】文件大小: " + Files.size(savedFile) + " bytes");

        try {
            // 3. 读取PDF
            //    使用 toAbsolutePath().toString() 给出完整路径，避免 PagePdfDocumentReader 找不到文件
            String absolutePath = savedFile.toAbsolutePath().toString();
            log.info("【upload】开始读取PDF: " + absolutePath);

            PagePdfDocumentReader reader = new PagePdfDocumentReader(
                    new org.springframework.core.io.FileSystemResource(savedFile.toFile())
            );
            List<Document> documents = reader.get();
            log.info("【upload】PDF解析完成，共 " + documents.size() + " 页");

            // 4. 文本分段（按 Token 切分）英文：1 Token ≈ 0.7-0.8 个单词 中文：1 Token ≈ 1-2 个汉字
            // 参数：chunkSize=500, minChunkSizeChars=100, minChunkLengthToEmbed=50, maxNumChunks=10000, keepSeparator=true
            // chunkSize=500: 每段最大500 token，适合中文文档（默认800对中文太大）
            // minChunkSizeChars=100: 最小100字符，避免切出无意义的短片段
            // maxNumChunks=10000: 最大分块数（防止超长文档切出过多块）
            // keepSeparator=true: 保留分隔符（如换行符、标点、段落标记）
            TokenTextSplitter splitter = new TokenTextSplitter(500, 50, 30, 10000, true);
            List<Document> chunks = splitter.apply(documents);
            log.info("【upload】分段完成，共 " + chunks.size() + " 个片段");

            // 5. 向量化并存储（内部调 Ollama nomic-embed-text，文本→向量→PostgreSQL）
            vectorStore.add(chunks);

            String result = "文档处理完成，共 " + chunks.size() + " 个文档片段已入库";
            log.info("【upload】" + result);
            return result;
        } finally {
            // 6. 清理原始文件（向量已存到内存，原始 PDF 不再需要）
            Files.deleteIfExists(savedFile);
            log.info("【upload】已清理临时文件");
        }
    }

    /**
     * 相对分数过滤
     * 1.如果top1 < 0.45,全部拒绝(绝对值兜底)
     * 2.如果top1 和 topK2 分差<0.08,且top1 <0.7,说明五区分度,全部拒绝
     * 3.自保留score>top1 * 0.85的chunk(与最高分相差不超过15%)
     *
     * @param question
     * @return
     */
    public List<Document> filterByRelativeScore(String question) {
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(3)
                        .similarityThreshold(0.3)
                        .build()
        );
        log.info("知识库返回数量:{}", documents.size());
        if (CollectionUtils.isEmpty(documents)) {
            return Collections.emptyList();
        }
        double top1Score = documents.get(0).getScore() != null ? documents.get(0).getScore() : 0.0;
        if (top1Score < 0.45) {
            log.info("【rag过滤】top1分数{}过低，返回空结果", top1Score);
            return Collections.emptyList();
        }
        if (documents.size() > 2) {
            double top2Score = documents.get(1).getScore() != null ? documents.get(1).getScore() : 0.0;
            double gap = top1Score - top2Score;
            if (gap < 0.08 && top1Score < 0.75) {
                log.info("【rag过滤】top1={} top2={} 差值{}不足，判断无明确答案", top1Score, top2Score, gap);
                return Collections.emptyList();
            }
        }
        double minScore = top1Score * 0.85;
        List<Document> result = documents.stream()
                .filter(d -> d.getScore() != null && d.getScore() > minScore)
                .toList();
        log.info("【rag过滤】最终数量:{}", documents.size());
        return result;
    }
}
