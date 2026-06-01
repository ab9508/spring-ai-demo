package com.example.ai.config;

/**
 * 多租户上下文——通过 ThreadLocal 存储当前请求的租户 ID。
 * <p>
 * 使用方式：
 * 1. TenantInterceptor 从请求头 X-Tenant-Id 中提取并设置
 * 2. 向量检索时通过 getCurrentTenantId() 获取并拼入 filterExpression
 * 3. 请求结束后由 interceptor 自动清理
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final String DEFAULT_TENANT = "default";

    private TenantContext() {
    }

    /**
     * 设置当前租户 ID
     */
    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId != null && !tenantId.isBlank() ? tenantId : DEFAULT_TENANT);
    }

    /**
     * 获取当前租户 ID
     */
    public static String getTenantId() {
        String tenantId = CURRENT_TENANT.get();
        return tenantId != null ? tenantId : DEFAULT_TENANT;
    }

    /**
     * 清空租户上下文（请求结束时调用）
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
