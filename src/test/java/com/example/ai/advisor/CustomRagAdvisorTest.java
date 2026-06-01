package com.example.ai.advisor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CustomRagAdvisor 单元测试
 * <p>
 * 覆盖 filterByRelativeScore 方法的核心逻辑（相对分数过滤策略）。
 * 包括：
 * 1. top1 < 0.45 绝对值兜底
 * 2. top1-top2 分差 < 0.08 且 top1 < 0.7 无区分度检测
 * 3. 只保留 score > top1 * 0.85 的 chunk
 * 4. 边界值测试（分差刚好等于 0.08、top1 刚好等于 0.45/0.7）
 */
@ExtendWith(MockitoExtension.class)
class CustomRagAdvisorTest {

    @Mock
    private VectorStore vectorStore;

    private CustomRagAdvisor advisor;

    @BeforeEach
    void setUp() {
        // topK=5, threshold=0.3
        advisor = new CustomRagAdvisor(vectorStore, 5, 0.3);
    }

    @Nested
    @DisplayName("filterByRelativeScore — 绝对值兜底")
    class AbsoluteThreshold {

        @Test
        @DisplayName("top1 < 0.45 时应全部拒绝，返回空列表")
        void shouldRejectAllWhenTop1BelowThreshold() {
            List<Document> docs = List.of(
                    docWithScore("chunk1", 0.35),
                    docWithScore("chunk2", 0.28)
            );
            List<Document> result = invokeFilter(docs);
            assertTrue(result.isEmpty(), "top1 0.35 < 0.45 应返回空");
        }

        @Test
        @DisplayName("top1 = 0.45 正好等于阈值时应保留")
        void shouldKeepWhenTop1EqualsThreshold() {
            List<Document> docs = List.of(
                    docWithScore("chunk1", 0.45),
                    docWithScore("chunk2", 0.40)
            );
            List<Document> result = invokeFilter(docs);
            assertFalse(result.isEmpty(), "top1 = 0.45 应通过");
            assertEquals("chunk1", result.get(0).getText());
        }

        @Test
        @DisplayName("top1 = 0.449 略低于阈值时应拒绝")
        void shouldRejectWhenTop1JustBelowThreshold() {
            List<Document> docs = List.of(
                    docWithScore("chunk1", 0.449),
                    docWithScore("chunk2", 0.40)
            );
            List<Document> result = invokeFilter(docs);
            assertTrue(result.isEmpty(), "top1 0.449 < 0.45 应返回空");
        }
    }

    @Nested
    @DisplayName("filterByRelativeScore — 无区分度检测")
    class DistinctionCheck {

        @Test
        @DisplayName("分差 < 0.08 且 top1 < 0.7 时应拒绝（无区分度）")
        void shouldRejectWhenGapTooSmallAndScoreModerate() {
            List<Document> docs = List.of(
                    docWithScore("chunk1", 0.65),
                    docWithScore("chunk2", 0.58) // 分差 0.07 < 0.08
            );
            List<Document> result = invokeFilter(docs);
            assertTrue(result.isEmpty(), "分差0.07且top1<0.7应拒绝");
        }

        @Test
        @DisplayName("分差 >= 0.08 时应通过（有区分度）")
        void shouldKeepWhenGapSufficient() {
            List<Document> docs = List.of(
                    docWithScore("chunk1", 0.68),
                    docWithScore("chunk2", 0.60) // 分差 0.08 >= 0.08
            );
            List<Document> result = invokeFilter(docs);
            assertFalse(result.isEmpty(), "分差0.08应通过");
            assertTrue(result.size() >= 1);
        }

        @Test
        @DisplayName("top1 >= 0.7 时即使分差小也通过（高置信度）")
        void shouldKeepWhenTop1Confident() {
            List<Document> docs = List.of(
                    docWithScore("chunk1", 0.72), // >= 0.7
                    docWithScore("chunk2", 0.68)  // 分差 0.04 < 0.08
            );
            List<Document> result = invokeFilter(docs);
            assertFalse(result.isEmpty(), "top1=0.72>=0.7应通过");
        }

        @Test
        @DisplayName("只有一个 chunk 时跳过无区分度检测")
        void shouldSkipDistinctionCheckWhenOnlyOneChunk() {
            List<Document> docs = List.of(
                    docWithScore("chunk1", 0.60)
            );
            List<Document> result = invokeFilter(docs);
            assertFalse(result.isEmpty(), "单个chunk应跳过无区分度检测");
            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("filterByRelativeScore — 高分段保留")
    class KeepHighScore {

        @Test
        @DisplayName("应该只保留 score > top1 * 0.85 的 chunks")
        void shouldKeepChunksWithin85Percent() {
            // top1=0.85, 0.85*0.85=0.7225
            // 所以应保留 score > 0.7225 的
            List<Document> docs = List.of(
                    docWithScore("chunk1", 0.85),
                    docWithScore("chunk2", 0.75), // 0.75 > 0.7225 ✓ 保留
                    docWithScore("chunk3", 0.70), // 0.70 < 0.7225 ✗ 丢弃
                    docWithScore("chunk4", 0.90), // 0.90 > 0.7225 ✓ 保留（虽然 > top1，但实际测试里不可能）
                    docWithScore("chunk5", 0.72)  // 0.72 < 0.7225 ✗ 丢弃
            );
            List<Document> result = invokeFilter(docs);
            assertEquals(2, result.size(), "应保留2个chunk");
            assertTrue(result.stream().anyMatch(d -> d.getText().equals("chunk1")));
            assertTrue(result.stream().anyMatch(d -> d.getText().equals("chunk2")));
        }

        @Test
        @DisplayName("所有 chunk 分数接近时应全部保留")
        void shouldKeepAllWhenScoresAreClose() {
            List<Document> docs = List.of(
                    docWithScore("chunk1", 0.90),
                    docWithScore("chunk2", 0.88), // 0.88 > 0.765 ✓
                    docWithScore("chunk3", 0.85)  // 0.85 > 0.765 ✓
            );
            List<Document> result = invokeFilter(docs);
            assertEquals(3, result.size(), "分数接近应全部保留");
        }
    }

    @Nested
    @DisplayName("filterByRelativeScore — 边界与异常")
    class EdgeCases {

        @Test
        @DisplayName("null 输入应返回空列表")
        void shouldHandleNullInput() {
            List<Document> result = invokeFilter(null);
            assertTrue(result.isEmpty(), "null输入应返回空");
        }

        @Test
        @DisplayName("空列表输入应返回空列表")
        void shouldHandleEmptyInput() {
            List<Document> result = invokeFilter(Collections.emptyList());
            assertTrue(result.isEmpty(), "空列表输入应返回空");
        }

        @Test
        @DisplayName("score 为 null 的 chunk 应视为 0.0")
        void shouldTreatNullScoreAsZero() {
            Document docWithNullScore = new Document("null-score");
            // 不设置 score
            List<Document> docs = List.of(docWithNullScore);
            // top1 score = 0.0 < 0.45，应返回空
            List<Document> result = invokeFilter(docs);
            assertTrue(result.isEmpty(), "null score 的 top1 视为 0.0 应被拒绝");
        }

        @Test
        @DisplayName("score = 1.0 的完美匹配应通过")
        void shouldKeepPerfectMatch() {
            List<Document> docs = List.of(
                    docWithScore("perfect", 1.0)
            );
            List<Document> result = invokeFilter(docs);
            assertFalse(result.isEmpty());
            assertEquals(1, result.size());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建指定分数的 Document
     * <p>
     * Spring AI 1.0.x 的 Document 没有公开的 setScore() 方法，
     * 分数由 VectorStore 在 similaritySearch 时设置。测试中通过反射注入。
     */
    private Document docWithScore(String text, double score) {
        Document doc = new Document(text);
        try {
            java.lang.reflect.Field scoreField = Document.class.getDeclaredField("score");
            scoreField.setAccessible(true);
            scoreField.set(doc, score);
        } catch (Exception e) {
            throw new RuntimeException("通过反射设置 Document.score 失败", e);
        }
        return doc;
    }

    /**
     * 通过模拟 VectorStore 调用 filterByRelativeScore
     * （advisor.before 内部调用了 filterByRelativeScore）
     * <p>
     * 注意：CustomRagAdvisor 的 filterByRelativeScore 是 private 方法，
     * 无法直接调用。但 before() 方法会在 vectorStore.similaritySearch() 后
     * 调用 filterByRelativeScore。通过 Mock vectorStore 返回指定数据，
     * 间接测试 filterByRelativeScore 逻辑。
     * <p>
     * 替代方案：反射调用 private 方法或提取为 package-private 方法。
     * 当前使用反射方式。
     */
    private List<Document> invokeFilter(List<Document> docs) {
        try {
            var method = CustomRagAdvisor.class.getDeclaredMethod(
                    "filterByRelativeScore", List.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Document> result = (List<Document>) method.invoke(advisor, docs);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("通过反射调用 filterByRelativeScore 失败", e);
        }
    }
}
