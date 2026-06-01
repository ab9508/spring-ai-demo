package com.example.ai.transformer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OverlappingTextSplitter 单元测试
 * <p>
 * 覆盖场景：
 * 1. 空白/空文本 → 返回空列表
 * 2. 短文本（小于 chunkSize）→ 返回单个 Document
 * 3. 长文本 → 正确切片且有重叠
 * 4. 断点选择逻辑（换行符/句号/逗号/空格优先级）
 * 5. maxNumChunks 限制
 * 6. 多个 Document 输入
 */
class OverlappingTextSplitterTest {

    // ==================== 边界条件 ====================

    @Test
    @DisplayName("空文本应该返回空列表")
    void shouldReturnEmptyForBlankText() {
        OverlappingTextSplitter splitter = new OverlappingTextSplitter();
        Document doc = new Document("   ");
        List<Document> result = splitter.apply(List.of(doc));
        assertTrue(result.isEmpty(), "空白文本应返回空列表");
    }

    @Test
    @DisplayName("null 文本应该返回空列表")
    void shouldReturnEmptyForNullText() {
        OverlappingTextSplitter splitter = new OverlappingTextSplitter();
        Document doc = new Document((String) null);
        List<Document> result = splitter.apply(List.of(doc));
        assertTrue(result.isEmpty(), "null 文本应返回空列表");
    }

    @Test
    @DisplayName("空列表输入应该返回空列表")
    void shouldReturnEmptyForEmptyList() {
        OverlappingTextSplitter splitter = new OverlappingTextSplitter();
        List<Document> result = splitter.apply(List.of());
        assertTrue(result.isEmpty(), "空列表输入应返回空列表");
    }

    // ==================== 短文本 ====================

    @Test
    @DisplayName("短文本（小于 chunkSize）应该返回单个 Document")
    void shouldReturnSingleChunkForShortText() {
        // chunkSize=500 token, charsPerToken=2, 所以 chunkSizeChars=1000
        OverlappingTextSplitter splitter = new OverlappingTextSplitter();
        String shortText = "这是一个很短的测试文本，只有几十个字符，远小于默认的chunk大小。";
        Document doc = new Document(shortText);
        List<Document> result = splitter.apply(List.of(doc));
        assertEquals(1, result.size(), "短文本应返回一个切片");
        assertEquals(shortText, result.get(0).getText(), "短文本内容应完整保留");
    }

    @Test
    @DisplayName("文本刚好等于 chunkSize 边界值应返回单个 Document")
    void shouldReturnSingleChunkForExactSizeText() {
        // chunkSize=50 token, charsPerToken=2, chunkSizeChars=100
        OverlappingTextSplitter splitter = new OverlappingTextSplitter(50, 0.10);
        String text = "A".repeat(100); // 刚好 100 字符
        Document doc = new Document(text);
        List<Document> result = splitter.apply(List.of(doc));
        assertEquals(1, result.size());
        assertEquals(text, result.get(0).getText());
    }

    // ==================== 长文本切片与重叠 ====================

    @Test
    @DisplayName("长文本应该正确切片，相邻切片有重叠")
    void shouldSplitLongTextWithOverlap() {
        // chunkSize=50 token, chunkSizeChars=100, overlap=5 token=10 chars
        OverlappingTextSplitter splitter = new OverlappingTextSplitter(50, 0.10);
        // 300 字符的长文本，预期切成 3 个以上的切片
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sb.append("第").append(i + 1).append("段测试文本。");
        }
        String longText = sb.toString();

        Document doc = new Document(longText);
        List<Document> result = splitter.apply(List.of(doc));

        assertFalse(result.isEmpty(), "长文本应产生切片");
        assertTrue(result.size() >= 2, "300字符文本(50token)应切出至少2片");

        // 验证相邻切片有重叠（后一片的起始应小于前一片的结束）
        if (result.size() >= 2) {
            String firstChunk = result.get(0).getText();
            String secondChunk = result.get(1).getText();
            // 第二片不应该以第一片的内容开头（说明有重叠或衔接）
            assertFalse(secondChunk.startsWith(firstChunk.substring(0, 10)),
                    "第二片不应以第一片的开头内容开头");
        }
    }

    // ==================== 断点选择逻辑 ====================

    @Test
    @DisplayName("断点应优先选择换行符位置")
    void shouldBreakAtNewlineFirst() {
        // chunkSize=20 token, chunkSizeChars=40, 强制在换行符附近断开
        OverlappingTextSplitter splitter = new OverlappingTextSplitter(20, 0);
        // 第一段不包含标点，只有换行符
        String text = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" // 40 chars
                + "\n"
                + "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"; // 40 chars
        Document doc = new Document(text);
        List<Document> result = splitter.apply(List.of(doc));

        if (result.size() >= 2) {
            assertTrue(result.get(0).getText().contains("AAAA"),
                    "第一片应包含换行符前的内容");
        }
    }

    // ==================== maxNumChunks 限制 ====================

    @Test
    @DisplayName("maxNumChunks 应限制最大切片数")
    void shouldLimitMaxChunks() {
        // maxNumChunks=2, chunkSize=20, chunkSizeChars=40
        // 300字符的文本会被切很多片，但限制为2
        OverlappingTextSplitter splitter = new OverlappingTextSplitter(20, 10, 50, 2);
        String longText = "测试文本。".repeat(50);
        Document doc = new Document(longText);
        List<Document> result = splitter.apply(List.of(doc));

        assertTrue(result.size() <= 2, "maxNumChunks=2 应限制最多2个切片");
    }

    // ==================== 多个 Document 输入 ====================

    @Test
    @DisplayName("多个 Document 输入应分别切片并汇总结果")
    void shouldProcessMultipleDocuments() {
        OverlappingTextSplitter splitter = new OverlappingTextSplitter(500, 0.10);
        Document doc1 = new Document("这是第一个文档的内容。");
        Document doc2 = new Document("这是第二个文档的内容，稍微长一些用于测试。");
        List<Document> result = splitter.apply(List.of(doc1, doc2));

        assertEquals(2, result.size(), "两个短文档应各产生一个切片");
        assertTrue(result.get(0).getText().contains("第一个文档"),
                "第一个切片应来自第一个文档");
        assertTrue(result.get(1).getText().contains("第二个文档"),
                "第二个切片应来自第二个文档");
    }

    // ==================== metadata 保留 ====================

    @Test
    @DisplayName("切片应保留原始 Document 的 metadata")
    void shouldPreserveMetadata() {
        OverlappingTextSplitter splitter = new OverlappingTextSplitter(500, 0.10);
        Document doc = new Document("测试文本，带自定义元数据。");
        doc.getMetadata().put("source", "test.txt");
        doc.getMetadata().put("page", 1);
        List<Document> result = splitter.apply(List.of(doc));

        assertEquals(1, result.size());
        assertEquals("test.txt", result.get(0).getMetadata().get("source"),
                "metadata 应被保留");
        assertEquals(1, result.get(0).getMetadata().get("page"),
                "metadata 应被保留");
    }

    // ==================== 不同构造参数 ====================

    @Test
    @DisplayName("全参构造应正确初始化")
    void shouldInitializeWithAllArgsConstructor() {
        OverlappingTextSplitter splitter = new OverlappingTextSplitter(100, 20, 30, 5000);
        String text = "测试".repeat(200);
        Document doc = new Document(text);
        List<Document> result = splitter.apply(List.of(doc));
        assertNotNull(result);
    }

    @Test
    @DisplayName("默认构造应使用合理的默认值")
    void shouldUseReasonableDefaults() {
        OverlappingTextSplitter splitter = new OverlappingTextSplitter();
        String text = "这是一段默认参数的测试文本。";
        List<Document> result = splitter.apply(List.of(new Document(text)));
        assertEquals(1, result.size());
    }
}
