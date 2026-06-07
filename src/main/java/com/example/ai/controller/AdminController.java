package com.example.ai.controller;

import com.example.ai.dao.LLMCallLogDao;
import com.example.ai.entity.LLMCallLog;
import com.example.ai.service.ABTestService;
import com.example.ai.service.RagEvaluator;
import com.example.ai.service.SemanticCacheService;
import com.example.ai.config.RagThresholdConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

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
    private final ABTestService abTestService;
    private final RagThresholdConfig thresholdConfig;

    public AdminController(LLMCallLogDao llmCallLogDao,
                           RagEvaluator ragEvaluator,
                           SemanticCacheService semanticCacheService,
                           ABTestService abTestService,
                           RagThresholdConfig thresholdConfig) {
        this.llmCallLogDao = llmCallLogDao;
        this.ragEvaluator = ragEvaluator;
        this.semanticCacheService = semanticCacheService;
        this.abTestService = abTestService;
        this.thresholdConfig = thresholdConfig;
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

    // ===== A/B 测试 =====

    /**
     * 查看当前 A/B 测试状态
     * GET /admin/abtest
     */
    @GetMapping("/abtest")
    public Map<String, Object> abTestStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", abTestService.isEnabled());
        result.put("config", abTestService.getCurrentConfig());
        result.put("group", abTestService.getGroup("test-user"));
        return result;
    }

    /**
     * 开始一个 A/B 试验
     * POST /admin/abtest/start?name=test_v1&promptVariant=prompt_v2&retrieval=vector_only&model=qwen2.5:3b
     */
    @PostMapping("/abtest/start")
    public Map<String, String> startABTest(
            @RequestParam String name,
            @RequestParam(defaultValue = "prompt_v1") String promptVariant,
            @RequestParam(defaultValue = "vector_only") String retrieval,
            @RequestParam(defaultValue = "qwen2.5:3b") String model) {
        abTestService.startExperiment(name, promptVariant, retrieval, model);
        Map<String, String> result = new HashMap<>();
        result.put("status", "ok");
        result.put("experiment", name);
        return result;
    }

    /**
     * 停止 A/B 测试
     * POST /admin/abtest/stop
     */
    @PostMapping("/abtest/stop")
    public Map<String, String> stopABTest() {
        abTestService.stopExperiment();
        Map<String, String> result = new HashMap<>();
        result.put("status", "ok");
        result.put("message", "A/B 测试已停止");
        return result;
    }

    // ===== 参数评估 =====

    /**
     * 查看当前所有阈值配置
     * GET /admin/thresholds
     */
    @GetMapping("/thresholds")
    public RagThresholdConfig getThresholds() {
        return thresholdConfig;
    }

    /**
     * 参数扫描：遍历 top1Absolute 的不同取值，评估对 recall 的影响
     * GET /admin/thresholds/sweep
     */
    @GetMapping("/thresholds/sweep")
    public List<Map<String, Object>> sweepTop1Threshold() {
        List<RagEvaluator.TestCase> testCases = List.of(
                new RagEvaluator.TestCase("退换货期限是几天",
                        List.of("7天", "无理由", "退货")),
                new RagEvaluator.TestCase("包邮条件是什么",
                        List.of("99元", "包邮", "运费")),
                new RagEvaluator.TestCase("积分有什么用",
                        List.of("积分", "兑换", "抵扣")),
                new RagEvaluator.TestCase("今天天气怎么样",
                        List.of())
        );

        // 遍历 0.30 ~ 0.60，步长 0.05
        List<Map<String, Object>> results = new ArrayList<>();
        for (double th = 0.30; th <= 0.60; th += 0.05) {
            RagEvaluator.EvaluationReport report = ragEvaluator.evaluate(testCases, 5, th);
            Map<String, Object> row = new HashMap<>();
            row.put("threshold", th);
            row.put("recall", String.format("%.2f", report.recall()));
            row.put("precision", String.format("%.2f", report.precision()));
            row.put("totalHits", report.totalHits());
            row.put("totalCases", report.totalCases());
            row.put("details", report.details().stream()
                    .map(d -> Map.of(
                            "question", d.question(),
                            "hit", d.hit(),
                            "scores", d.scores()
                    )).collect(Collectors.toList()));
            results.add(row);
        }

        return results;
    }
}
