package com.example.ai.transformer;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 带重叠（overlap）的文本切片器
 * <p>
 * 解决 TokenTextSplitter 无 overlap 导致切片边界语义丢失的问题。
 * <p>
 * 实现原理（两层切片法，效果最好）：
 * 1. 第一层：用 TokenTextSplitter 粗切（chunkSize*2，保留语义块）
 * 2. 第二层：对粗切结果做滑动窗口细切，相邻切片有重叠部分
 * <p>
 * 默认 chunkSize=500（token 近似值），overlap=10%，即 50 token 重叠。
 * 字符/token 估算：保守取 2 字符 = 1 token（中英文混合场景）。
 * <p>
 * 为什么不用纯字符滑动窗口？
 * - 直接用字符窗口切，容易在句子中间切断，破坏语义。
 * - TokenTextSplitter 内部会尽量在句子/段落边界断开（keepSeparator），
 *   所以第一层粗切先用它保语义边界，第二层再做滑动重叠。
 *
 * @author Spring AI Demo
 */
public class OverlappingTextSplitter implements DocumentTransformer {

    /**
     * 默认每个切片的 token 数（近似值，非精确）
     */
    private final int chunkSizeInTokens;

    /**
     * 相邻切片重叠的 token 数（近似值）
     */
    private final int overlapInTokens;

    /**
     * 字符/token 估算比（保守：2 字符 = 1 token，适合中英文混合）
     */
    private static final int CHARS_PER_TOKEN = 2;

    /**
     * 最小切片字符数（低于此值会合并到上一个切片）
     */
    private final int minChunkSizeChars;

    /**
     * 最大切片数（防止超大文档产生过多 chunk）
     */
    private final int maxNumChunks;

    /**
     * 全参构造
     */
    public OverlappingTextSplitter(int chunkSizeInTokens,
                                   int overlapInTokens,
                                   int minChunkSizeChars,
                                   int maxNumChunks) {
        this.chunkSizeInTokens = chunkSizeInTokens;
        this.overlapInTokens = overlapInTokens;
        this.minChunkSizeChars = minChunkSizeChars;
        this.maxNumChunks = maxNumChunks;
    }

    /**
     * 简化构造：指定 chunkSize 和 overlap 比例
     */
    public OverlappingTextSplitter(int chunkSizeInTokens, double overlapRatio) {
        this(chunkSizeInTokens,
             (int) (chunkSizeInTokens * overlapRatio),
             50,       // minChunkSizeChars
             10000);   // maxNumChunks
    }

    /**
     * 最简构造：chunkSize=500, overlap=10%
     */
    public OverlappingTextSplitter() {
        this(500, 0.10);
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        List<Document> result = new ArrayList<>();
        for (Document doc : documents) {
            result.addAll(splitDocument(doc));
        }
        return result;
    }

    /**
     * 对单个 Document 做两层切片
     */
    private List<Document> splitDocument(Document document) {
        String text = document.getText();
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // 第一层：粗切（chunkSize * 2，减少切片次数，保留较大语义块）
        int coarseChunkSize = chunkSizeInTokens * 2;
        TokenTextSplitter coarseSplitter = new TokenTextSplitter(
                coarseChunkSize, 50, 30, maxNumChunks, true);
        List<Document> coarseChunks = coarseSplitter.apply(
                List.of(new Document(text, document.getMetadata())));

        // 第二层：对每个粗切结果做滑动窗口细切 + overlap
        List<Document> result = new ArrayList<>();
        for (Document coarseChunk : coarseChunks) {
            List<Document> fineChunks = slidingWindowWithOverlap(
                    coarseChunk.getText(), document.getMetadata());
            result.addAll(fineChunks);
        }

        // 限制总切片数
        if (result.size() > maxNumChunks) {
            return result.subList(0, maxNumChunks);
        }
        return result;
    }

    /**
     * 滑动窗口细切 + overlap 核心逻辑
     * <p>
     * 窗口大小 = chunkSizeChars
     * 滑动步长 = chunkSizeChars - overlapChars
     * 每个窗口生成一个新的 Document，相邻窗口有重叠部分
     */
    private List<Document> slidingWindowWithOverlap(String text, Map<String, Object> metadata) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        int chunkSizeChars = chunkSizeInTokens * CHARS_PER_TOKEN;
        int overlapChars = overlapInTokens * CHARS_PER_TOKEN;

        // 文本小于一个 chunk，直接返回
        if (text.length() <= chunkSizeChars) {
            return List.of(new Document(text, metadata));
        }

        List<Document> result = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSizeChars, text.length());

            // 不是最后一块时，尽量在句子/段落边界断开
            if (end < text.length()) {
                end = findGoodBreakPoint(text, end);
            }

            String chunkText = text.substring(start, end);
            result.add(new Document(chunkText, metadata));

            // 下一块的起始位置 = 当前 end - overlapChars
            start = end - overlapChars;

            // 防止死循环（overlap 太大或文本极短）
            if (start >= end) {
                start = end;
            }
            if (start >= text.length()) {
                break;
            }
        }

        return result;
    }

    /**
     * 在 pos 附近寻找合适的断开点
     * 优先级：换行符 > 句号/问号/感叹号 > 逗号/分号 > 空格
     * 搜索范围：pos 向前 150 字符
     */
    private int findGoodBreakPoint(String text, int pos) {
        int searchStart = Math.max(pos - 150, 0);

        // 1. 换行符（段落边界，最优）
        for (int i = pos; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                return Math.min(i + 1, text.length());
            }
        }

        // 2. 中文句号/问号/感叹号
        for (int i = pos; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '？' || c == '！') {
                return Math.min(i + 1, text.length());
            }
        }

        // 3. 英文标点
        for (int i = pos; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '?' || c == '!') {
                return Math.min(i + 1, text.length());
            }
        }

        // 4. 逗号/分号（短语边界）
        for (int i = pos; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '，' || c == '；' || c == ',' || c == ';') {
                return Math.min(i + 1, text.length());
            }
        }

        // 5. 空格（单词边界）
        for (int i = pos; i >= searchStart; i--) {
            if (text.charAt(i) == ' ') {
                return Math.min(i + 1, text.length());
            }
        }

        // 找不到合适边界，直接用 pos
        return pos;
    }
}
