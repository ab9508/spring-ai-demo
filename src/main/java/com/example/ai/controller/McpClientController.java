package com.example.ai.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP Client 测试控制器
 * <p>
 * ============ 使用说明 ============
 * 1. 先用 application-mcp-server.yml 启动 MCP Server（8081端口）
 * 2. 再用 application-mcp-client.yml 启动 MCP Client（8082端口）
 * 3. 访问 http://localhost:8082/mcp/chat?msg=查一下订单ORD001
 * <p>
 * ============ 核心原理 ============
 * MCP Client 启动时自动完成：
 * 1. 连接 Server 的 /sse 端点建立 SSE 长连接
 * 2. 发送 JSON-RPC "tools/list" 请求
 * 3. Server 返回可用的工具列表（queryOrder, queryStock, handleAftersale）
 * 4. Spring AI 自动将远程工具封装为 ToolCallbackProvider Bean
 * 5. ChatClient 注入后，LLM 可以自主决定调用这些远程工具
 * <p>
 * ============ 和 Tool Calling 的区别 ============
 * Tool Calling：@Tool 方法在同一个JVM进程内直接调用
 * MCP Client：@Tool 方法在远程 Server 进程，通过 JSON-RPC 跨进程调用
 * 对 LLM 来说，两者使用体验完全一样，区别只在传输层：
 * - Tool Calling：HTTP 请求体 → 模型API → 响应含 tool_calls → 本地调用
 * - MCP Client：HTTP 请求体 → 模型API → 响应含 tool_calls → JSON-RPC → 远程调用
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@ConditionalOnProperty(name = "app.mcp.client.enabled", havingValue = "true")
public class McpClientController {

    private final ChatClient chatClient;

    /**
     * ToolCallbackProvider 由 MCP Client Starter 自动装配：
     * 它会在启动时连接远程 MCP Server（见 application-mcp-client.yml），
     * 获取工具列表，并封装为 ToolCallbackProvider。
     * <p>
     * 当 LLM 决定调用工具时，调用请求会通过 JSON-RPC 转发给远程 Server 执行，
     * 对 ChatClient 来说这个过程是透明的。
     */
    public McpClientController(ChatClient.Builder builder,
                               ToolCallbackProvider mcpTools) {
        ToolCallback[] callbacks = mcpTools.getToolCallbacks();
        log.info("【MCP Client】从Server获取到 {} 个远程工具", callbacks.length);
        for (ToolCallback cb : callbacks) {
            log.info("  - 工具: {} - {}", cb.getToolDefinition().name(), cb.getToolDefinition().description());
        }

        this.chatClient = builder
                .defaultToolCallbacks(callbacks)
                .build();
        log.info("【MCP Client】ChatClient 已注入MCP远程工具");
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String msg) {
        log.info("【MCP Client】收到消息: {}", msg);
        String response = chatClient.prompt(msg).call().content();
        log.info("【MCP Client】回复: {}", response);
        return response;
    }
}
