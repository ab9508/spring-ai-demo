package com.example.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * LLM 调用日志实体
 * 记录每次 LLM 调用的完整链路信息，用于可观测性排查
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMCallLog {
    private String id;
    private OffsetDateTime timestamp;
    private String sessionId;
    private String question;
    private String systemPrompt;
    private String response;
    private String model;
    private Integer tokenInput;
    private Integer tokenOutput;
    private Integer latencyMs;
    private String ragDocs;         // JSON 字符串，记录检索到的文档来源
    private BigDecimal cost;
    private Boolean success;
    private String errorMsg;
}
