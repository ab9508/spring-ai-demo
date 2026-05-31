package com.example.ai.service;

import com.example.ai.transformer.OverlappingTextSplitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * RAG 核心服务（支持多格式文档）
 * <p>
 * ============ 支持的文档格式 ============
 * - PDF：PagePdfDocumentReader（Spring AI 专用，按页读取）
 * - Word/Excel/PPT/TXT/HTML 等：TikaDocumentReader（基于 Apache Tika，通用格式解析）
 * <p>
 * ============ 统一处理流程 ============
 * 文件上传 → 格式识别 → DocumentReader 解析 → TokenTextSplitter 分段
 * → EmbeddingModel 文本转向量(Ollama nomic-embed-text) → PgVectorStore 存储到 PostgreSQL
 * <p>
 * ============ 格式选型说明 ============
 * 为什么 PDF 不用 Tika？因为 PagePdfDocumentReader 按页切割，天然保持页面结构，
 * 对中文 PDF 表格、排版保留更好。Tika 对 PDF 的纯文本提取质量一般。
 * <p>
 * Tika 的优势在于"万能"，一个 Reader 搞定 Word/Excel/PPT/TXT/HTML 等几十种格式，
 * 不用为每种格式单独写解析逻辑。
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

    // 文件存放目录（改为你自己的路径，确保目录存在且有写入权限）
    private static final String UPLOAD_DIR = "D:\\test";

    // 支持的文件扩展名（小写）
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "html", "htm", "md", "csv", "json", "xml"
    );

    // 使用 Tika 解析的格式（PDF 单独处理）
    private static final Set<String> TIKA_FORMATS = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "html", "htm", "md", "csv", "json", "xml"
    );

    public RagService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 上传文档并处理入库（自动识别格式）
     *
     * @param file 上传的文档文件
     * @return 处理结果
     */
    public String uploadDocument(MultipartFile file) throws IOException {
        // 1. 校验文件名
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        // 2. 提取扩展名并校验格式
        String extension = getExtension(originalFilename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "不支持的文件格式: " + extension + "，支持: " + SUPPORTED_EXTENSIONS);
        }

        log.info("【upload】文件格式: {}，原始文件名: {}", extension, originalFilename);

        // 3. 保存到固定目录
        Path savedFile = saveTempFile(file, originalFilename);

        try {
            // 4. 根据格式选择 Reader 解析文档
            DocumentReader reader = createReader(savedFile, extension);
            List<Document> documents = reader.get();
            log.info("【upload】文档解析完成，共 {} 个文档块", documents.size());

            // 打印前 3 个文档块的前 100 字符（方便调试）
            documents.stream().limit(3).forEach(doc ->
                    log.info("【upload】文档块预览: {}", doc.getText().substring(0, Math.min(100, doc.getText().length()))));

            // 5. 文本分段（OverlappingTextSplitter，带重叠避免语义丢失）
            //    chunkSize=500: 每段最大500 token，适合中文文档
            //    overlap=10%: 相邻切片重叠50 token，保证边界语义连续
            //    两层切片法：先用 TokenTextSplitter 粗切（保语义边界），
            //    再滑动窗口细切+重叠（保证检索质量）
            OverlappingTextSplitter splitter = new OverlappingTextSplitter(50, 0.10);
            List<Document> chunks = splitter.apply(documents);
            log.info("【upload】分段完成，共 {} 个片段", chunks.size());

            // 6. 向量化并存储（内部调 Ollama nomic-embed-text → PostgreSQL）
            int batchSize = 100;
            for (int i = 0; i < chunks.size(); i += batchSize) {
                int toIndex = Math.min(i + batchSize, chunks.size());
                List<Document> batch = chunks.subList(i, toIndex);
                vectorStore.add(batch);
                log.info("[RAG] 分批入库 {}/{}", toIndex, chunks.size());
            }


            String result = String.format("文档[%s]处理完成，共 %d 个片段已入库", originalFilename, chunks.size());
            log.info("【upload】{}", result);
            return result;

        } finally {
            // 7. 清理临时文件（向量已存到 PostgreSQL，原始文件不再需要）
            Files.deleteIfExists(savedFile);
            log.info("【upload】已清理临时文件");
        }
    }

    /**
     * 根据文件扩展名选择合适的 DocumentReader
     * <p>
     * PDF → PagePdfDocumentReader（按页读取，中文排版保留更好）
     * 其他 → TikaDocumentReader（Apache Tika 通用解析，支持几十种格式）
     */
    private DocumentReader createReader(Path file, String extension) {
        if ("pdf".equals(extension)) {
            log.info("【upload】使用 PagePdfDocumentReader 解析 PDF");
            return new PagePdfDocumentReader(new FileSystemResource(file.toFile()));
        } else {
            log.info("【upload】使用 TikaDocumentReader 解析 {}", extension.toUpperCase());
            return new TikaDocumentReader(new FileSystemResource(file.toFile()));
        }
    }

    /**
     * 保存上传文件到临时目录
     */
    private Path saveTempFile(MultipartFile file, String originalFilename) throws IOException {
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        // 文件名加时间戳前缀，避免重复
        String fileName = System.currentTimeMillis() + "_" + originalFilename;
        Path savedFile = uploadPath.resolve(fileName);
        file.transferTo(savedFile.toFile());

        log.info("【upload】保存文件到: {}，大小: {} bytes", savedFile.toAbsolutePath(), Files.size(savedFile));
        return savedFile;
    }

    /**
     * 提取文件扩展名（小写）
     */
    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * 获取支持的文件格式列表
     */
    public static Set<String> getSupportedFormats() {
        return SUPPORTED_EXTENSIONS;
    }

    /**
     * 相对分数过滤
     * 1.如果top1 < 0.45,全部拒绝(绝对值兜底)
     * 2.如果top1 和 top2 分差<0.05,且top1 <0.6,说明无区分度,全部拒绝
     * 3.只保留score>top1 * 0.85的chunk(与最高分相差不超过15%)
     */
    public List<Document> filterByRelativeScore(String question) {
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(3)
                        .similarityThreshold(0.3)
                        .build()
        );
        if (CollectionUtils.isEmpty(documents)) {
            log.info("【rag过滤】知识库返回为空");
            return Collections.emptyList();
        }
        log.info("知识库返回数量:{},分数:{}", documents.size(), documents.stream().map(Document::getScore).toList());
        double top1Score = documents.get(0).getScore() != null ? documents.get(0).getScore() : 0.0;
        if (top1Score < 0.45) {
            log.info("【rag过滤】top1分数{}过低，返回空结果", top1Score);
            return Collections.emptyList();
        }
        // 去重：连续切片重叠可能导致相同分数，跳过重复
        // 用 distinct 按 score 去重后检查 gap
        List<Double> distinctScores = documents.stream()
                .map(d -> d.getScore() != null ? d.getScore() : 0.0)
                .distinct()
                .sorted((a, b) -> Double.compare(b, a))
                .toList();
        if (distinctScores.size() > 1) {
            double gap = distinctScores.get(0) - distinctScores.get(1);
            if (gap < 0.05 && distinctScores.get(0) < 0.6) {
                log.info("【rag过滤】top1={} top2Distinct={} 差值{}不足，判断无明确答案",
                        distinctScores.get(0), distinctScores.get(1), gap);
                return Collections.emptyList();
            }
        }
        double minScore = top1Score * 0.85;
        List<Document> result = documents.stream()
                .filter(d -> d.getScore() != null && d.getScore() > minScore)
                .toList();
        log.info("【rag过滤】最终数量:{}", result.size());
        return result;
    }
}
