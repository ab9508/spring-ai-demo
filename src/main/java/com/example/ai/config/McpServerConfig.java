package com.example.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 配置
 * <p>
 * ============ 原理说明 ============
 * MCP Server 的本质：把现有的 @Tool 方法通过标准化协议暴露给外部 Client 调用。
 * <p>
 * 核心流程（全自动，无需手动注册Bean）：
 * 1. spring-ai-starter-mcp-server-webmvc 自动扫描所有 @Tool 注解方法
 * 2. 自动注册 SSE 端点（默认 /mcp/message）
 * 3. 外部 Client 连接后，通过 JSON-RPC "tools/list" 发现可用工具
 * 4. Client 发送 "tools/call" 请求时，Server 自动路由到对应的 @Tool 方法执行
 * <p>
 * ============ 和 Tool Calling 的关系 ============
 * Tool Calling：LLM 直接调 @Tool 方法（同进程内，HTTP请求体内嵌tools字段）
 * MCP Server：通过 JSON-RPC 长连接暴露 @Tool 方法（跨进程，独立协议）
 * <p>
 * 两者共用同一套 @Tool 方法定义，只是暴露方式不同：
 * - Tool Calling：嵌入 HTTP 请求体的 tools 字段 → DeepSeek/OpenAI协议
 * - MCP Server：通过 JSON-RPC tools/list 动态返回 → Anthropic MCP协议
 * <p>
 * ============ 激活方式 ============
 * mvn spring-boot:run -Dspring-boot.run.profiles=mcp-server
 * <p>
 * ============ 验证方式 ============
 * 启动后访问 http://localhost:8081/mcp/message（SSE端点），Client连接后会收到工具列表
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.mcp.server.enabled", havingValue = "true")
public class McpServerConfig {

    // MCP Server 的 Starter 会自动完成以下工作：
    // 1. 扫描所有带 @Tool 注解的方法（如 OrderTools.queryOrder）
    // 2. 将其转换为 MCP 协议的 Tool 定义
    // 3. 注册 SSE 端点，监听 Client 连接
    // 4. 响应 tools/list 和 tools/call 请求
    //
    // 不需要手动创建 @Bean，只需要确保：
    // - @Tool 所在的类是 @Component（Spring 管理）
    // - application-mcp-server.yml 中 spring.ai.mcp.server.enabled=true
    // - 引入了 spring-ai-starter-mcp-server-webflux 依赖

    public McpServerConfig() {
        log.info("【MCP Server】配置已激活，@Tool 方法将通过MCP协议暴露给外部Client");
    }
}
