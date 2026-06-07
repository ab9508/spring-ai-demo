package com.example.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A/B 测试服务——将用户请求按比例分配到不同的模型/策略组合。
 * <p>
 * 用于验证：
 * - 新模型 vs 旧模型的效果差异
 * - 不同 System Prompt 的回复质量
 * - RAG 不同参数配置的检索效果
 */
@Slf4j
@Service
public class ABTestService {

    @Value("${app.abtest.enabled:false}")
    private boolean abTestEnabled;

    @Value("${app.abtest.b-percentage:50}")
    private int bPercentage;

    private volatile ExperimentConfig currentConfig;

    @PostConstruct
    public void init() {
        this.currentConfig = new ExperimentConfig(
                "default",
                "prompt_v1",
                "检索: 向量+关键词",
                "qwen2.5:3b"
        );
        log.info("【A/B测试】初始化: enabled={}, B组比例={}%",
                abTestEnabled, bPercentage);
    }

    /**
     * 获取当前请求的试验分组
     *
     * @param userId 用户标识（同一用户始终分配到同一组）
     * @return A 或 B
     */
    public String getGroup(String userId) {
        if (!abTestEnabled) {
            return "A";
        }
        // 基于 userId 的 hash 做一致性分配
        int hash = userId != null ? userId.hashCode() & 0x7FFFFFFF : 0;
        return (hash % 100 < bPercentage) ? "B" : "A";
    }

    /**
     * 启用 A/B 测试并设置 B 组策略
     */
    public void startExperiment(String experimentName, String promptVariant,
                                 String retrievalStrategy, String modelName) {
        this.currentConfig = new ExperimentConfig(
                experimentName, promptVariant, retrievalStrategy, modelName
        );
        log.info("【A/B测试】开始试验: name={}, prompt={}, retrieval={}, model={}",
                experimentName, promptVariant, retrievalStrategy, modelName);
    }

    /**
     * 停止 A/B 测试
     */
    public void stopExperiment() {
        this.abTestEnabled = false;
        log.info("【A/B测试】已停止");
    }

    public boolean isEnabled() {
        return abTestEnabled;
    }

    public ExperimentConfig getCurrentConfig() {
        return currentConfig;
    }

    /**
     * 试验配置
     */
    public record ExperimentConfig(
            String experimentName,
            String promptVariant,
            String retrievalStrategy,
            String modelName
    ) {
    }
}
