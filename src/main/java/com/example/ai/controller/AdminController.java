package com.example.ai.controller;

import com.example.ai.dao.LLMCallLogDao;
import com.example.ai.entity.LLMCallLog;
import com.example.ai.service.RagEvaluator;
import com.example.ai.service.SemanticCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理接口——LLM 调用日志查询、RAG 评估、缓存管理
 */
@Slf4j
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final LLMCallLogDao llmCallLogDao;
    private final RagEvaluator ragEvaluator;
    private final SemanticCacheService semanticCacheService;

    public AdminController(LLMCallLogDao llmCallLogDao,
                           RagEvaluator ragEvaluator,
                           SemanticCacheService semanticCacheService) {
        this.llmCallLogDao = llmCallLogDao;
        this.ragEvaluator = ragEvaluator;
        this.semanticCacheService = semanticCacheService;
    }

    /**
     * 查询最近的 LLM 调用日志
     * GET /admin/logs?limit=10
     */
    @GetMapping("/logs")
    public Map<String, Object> getRecentLogs(
            @RequestParam(defaultValue = "20") int limit) {
        List<LLMCallLog> logs = llmCallLogDao.findRecent(limit);
        Map<String, Object> result = new HashMap<>();
        result.put("total", logs.size());
        result.put("logs", logs);
        return result;
    }

    /**
     * 按会话ID查询日志
     * GET /admin/logs/session?sessionId=xxx&limit=20
     */
    @GetMapping("/logs/session")
    public Map<String, Object> getSessionLogs(
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "20") int limit) {
        List<LLMCallLog> logs = llmCallLogDao.findBySessionId(sessionId, limit);
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("total", logs.size());
        result.put("logs", logs);
        return result;
    }

    /**
     * 运行 RAG 质量评估
     * GET /admin/evaluate?topK=5&threshold=0.3
     */
    @GetMapping("/evaluate")
    public RagEvaluator.EvaluationReport evaluate(
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(defaultValue = "0.3") double threshold) {

        List<RagEvaluator.TestCase> testCases = List.of(
                new RagEvaluator.TestCase("退换货期限是几天",
                        List.of("7天", "无理由", "退货")),
                new RagEvaluator.TestCase("包邮条件是什么",
                        List.of("99元", "包邮", "运费")),
                new RagEvaluator.TestCase("积分有什么用",
                        List.of("积分", "兑换", "抵扣")),
                new RagEvaluator.TestCase("今天天气怎么样",
                        List.of())  // 预期无匹配，测试拒答率
        );

        return ragEvaluator.evaluate(testCases, topK, threshold);
    }

    /**
     * 清空语义缓存
     * POST /admin/cache/clear
     */
    @PostMapping("/cache/clear")
    public Map<String, String> clearCache() {
        semanticCacheService.clear();
        Map<String, String> result = new HashMap<>();
        result.put("status", "ok");
        result.put("message", "语义缓存已清空");
        return result;
    }
}
