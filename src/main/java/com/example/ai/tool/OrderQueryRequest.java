package com.example.ai.tool;

/**
 * Tool的请求参数 —— 会自动从AI的JSON参数中反序列化
 *
 * 字段名要语义清晰，AI更容易理解
 */
public record OrderQueryRequest(
        String orderId
) {}

