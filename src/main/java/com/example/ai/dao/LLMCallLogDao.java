package com.example.ai.dao;

import com.example.ai.entity.LLMCallLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * LLM 调用日志 DAO
 * 异步写入 + 按 session/时间范围查询
 */
@Slf4j
@Repository
public class LLMCallLogDao {

    private final JdbcTemplate jdbcTemplate;

    public LLMCallLogDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 异步写入日志（由 @Async 调用）
     */
    public void insert(LLMCallLog logEntry) {
        try {
            jdbcTemplate.update("""
                INSERT INTO llm_call_log (id, timestamp, session_id, question, system_prompt,
                    response, model, token_input, token_output, latency_ms, rag_docs, cost, success, error_msg)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                logEntry.getId() != null ? logEntry.getId() : UUID.randomUUID().toString(),
                logEntry.getTimestamp(),
                logEntry.getSessionId(),
                truncate(logEntry.getQuestion(), 2000),
                truncate(logEntry.getSystemPrompt(), 3000),
                truncate(logEntry.getResponse(), 5000),
                logEntry.getModel(),
                logEntry.getTokenInput() != null ? logEntry.getTokenInput() : 0,
                logEntry.getTokenOutput() != null ? logEntry.getTokenOutput() : 0,
                logEntry.getLatencyMs() != null ? logEntry.getLatencyMs() : 0,
                logEntry.getRagDocs(),
                logEntry.getCost() != null ? logEntry.getCost() : java.math.BigDecimal.ZERO,
                logEntry.getSuccess() != null ? logEntry.getSuccess() : true,
                logEntry.getErrorMsg()
            );
        } catch (Exception e) {
            // 日志写入失败不能影响主流程，只打 warn
            log.warn("LLM调用日志写入失败: {}", e.getMessage());
        }
    }

    /**
     * 按会话ID查询最近的日志
     */
    public List<LLMCallLog> findBySessionId(String sessionId, int limit) {
        String sql = "SELECT * FROM llm_call_log WHERE session_id = ? ORDER BY timestamp DESC LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, sessionId, limit);
    }

    /**
     * 按时间范围倒序查询
     */
    public List<LLMCallLog> findRecent(int limit) {
        String sql = "SELECT * FROM llm_call_log ORDER BY timestamp DESC LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, limit);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    private static final java.sql.ResultSetMetaData[] META_HOLDER = new java.sql.ResultSetMetaData[0];

    private static final org.springframework.jdbc.core.RowMapper<LLMCallLog> ROW_MAPPER = (rs, rowNum) -> {
        LLMCallLog log = new LLMCallLog();
        log.setId(rs.getString("id"));
        log.setTimestamp(rs.getObject("timestamp", java.time.OffsetDateTime.class));
        log.setSessionId(rs.getString("session_id"));
        log.setQuestion(rs.getString("question"));
        log.setSystemPrompt(rs.getString("system_prompt"));
        log.setResponse(rs.getString("response"));
        log.setModel(rs.getString("model"));
        log.setTokenInput(rs.getInt("token_input"));
        log.setTokenOutput(rs.getInt("token_output"));
        log.setLatencyMs(rs.getInt("latency_ms"));
        log.setRagDocs(rs.getString("rag_docs"));
        log.setCost(rs.getBigDecimal("cost"));
        log.setSuccess(rs.getBoolean("success"));
        log.setErrorMsg(rs.getString("error_msg"));
        return log;
    };
}
