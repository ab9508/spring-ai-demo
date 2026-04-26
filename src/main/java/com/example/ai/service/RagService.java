package com.example.ai.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * RAG 核心服务
 *
 * ============ RAG 整体流程 ============
 *
 * 离线阶段（本类 uploadDocument）：
 *   PDF文件 → PagePdfDocumentReader按页读取 → TokenTextSplitter分段
 *   → EmbeddingModel文本转向量(智谱AI) → VectorStore存储到内存
 *
 * 在线阶段（RagController.ask 调用 vectorStore.similaritySearch）：
 *   用户问题 → EmbeddingModel转向量(智谱AI) → VectorStore相似度检索
 *   → 拼装上下文 → ChatClient生成回答(DeepSeek)
 *
 * ============ 向量数据存在哪？ ============
 * SimpleVectorStore 是内存存储，数据在 JVM 堆内存中。
 * 重启应用 → 数据清空，需要重新 upload PDF。
 * 验证方式：upload 后调 /rag/ask，能回答就说明数据在内存中。
 * 如果想持久化，可以调用 SimpleVectorStore.save(file) 保存到本地 JSON 文件。
 */
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

        System.out.println("【upload】保存文件到: " + savedFile.toAbsolutePath());
        file.transferTo(savedFile.toFile());

        // 验证文件确实存在
        System.out.println("【upload】文件是否存在: " + Files.exists(savedFile));
        System.out.println("【upload】文件大小: " + Files.size(savedFile) + " bytes");

        try {
            // 3. 读取PDF
            //    使用 toAbsolutePath().toString() 给出完整路径，避免 PagePdfDocumentReader 找不到文件
            String absolutePath = savedFile.toAbsolutePath().toString();
            System.out.println("【upload】开始读取PDF: " + absolutePath);

            PagePdfDocumentReader reader = new PagePdfDocumentReader(
                    new org.springframework.core.io.FileSystemResource(savedFile.toFile())
            );
            List<Document> documents = reader.get();
            System.out.println("【upload】PDF解析完成，共 " + documents.size() + " 页");

            // 4. 文本分段（按 Token 切分）
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> chunks = splitter.apply(documents);
            System.out.println("【upload】分段完成，共 " + chunks.size() + " 个片段");

            // 5. 向量化并存储（内部调智谱AI embedding-3，文本→向量→内存）
            vectorStore.add(chunks);

            String result = "文档处理完成，共 " + chunks.size() + " 个文档片段已入库";
            System.out.println("【upload】" + result);
            return result;
        } finally {
            // 6. 清理原始文件（向量已存到内存，原始 PDF 不再需要）
            Files.deleteIfExists(savedFile);
            System.out.println("【upload】已清理临时文件");
        }
    }
}
