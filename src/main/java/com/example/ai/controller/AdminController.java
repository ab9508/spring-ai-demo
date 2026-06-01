package com.example.ai.controller;

import com.example.ai.dao.LLMCallLogDao;
import com.example.ai.entity.LLMCallLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理接口——LLM 调用日志查询
 * 用于排查 AI 应用问题，查看每次 LLM 调用的完整链路
 */
@Slf4j
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final LLMCallLogDao llmCallLogDao;

    public AdminController(LLMCallLogDao llmCallLogDao) {
        this.llmCallLogDao = llmCallLogDao;
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
}
