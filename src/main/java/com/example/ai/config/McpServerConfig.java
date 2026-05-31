package com.example.ai.config;

import com.example.ai.tool.OrderTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 配置
 * <p>
 * 核心流程：
 * 1. 自动注册 SSE 端点（/sse）
 * 2. MethodToolCallbackProvider 将 OrderTools 的 @Tool 方法注册为 ToolCallback Bean
 * 3. MCP Server Starter 自动将这些 ToolCallback 暴露给外部 Client
 * 4. Client 通过 JSON-RPC tools/list 发现工具，tools/call 远程调用
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.mcp.server.enabled", havingValue = "true")
public class McpServerConfig {

    public McpServerConfig() {
        log.info("【MCP Server】配置已激活，@Tool 方法将通过MCP协议暴露给外部Client");
    }

    /**
     * 将 OrderTools 的 @Tool 方法注册为 ToolCallback Bean
     * 这是关键：MCP Server 自动配置靠扫描 ToolCallback 来注册工具
     * 如果没人创建 ToolCallback Bean，Server 的 tools/list 会返回空列表
     */
    @Bean
    public MethodToolCallbackProvider orderToolsToolCallbackProvider(OrderTools orderTools) {
        log.info("【MCP Server】注册 OrderTools 中的 @Tool 方法为远程可调用工具");
        return MethodToolCallbackProvider.builder()
                .toolObjects(orderTools)
                .build();
    }
}
