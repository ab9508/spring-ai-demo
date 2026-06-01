package com.example.ai.interceptor;

import com.example.ai.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 限流拦截器——拦截 AI 相关请求，检查是否超过调用频率
 * <p>
 * 对 /rag/**、/agent/**、/chat/** 等 AI 接口做限流保护。
 * 超出限流时返回 429 + JSON 提示。
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    public RateLimitInterceptor(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 从请求中提取用户标识：优先取 header 中的 X-User-Id，没有则用 IP
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            userId = getClientIp(request);
        }

        boolean allowed = rateLimitService.tryAcquire(userId);
        if (!allowed) {
            log.warn("【限流拦截】请求被限流 userId={} uri={}", userId, request.getRequestURI());
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":429,\"message\":\"请求太频繁，请稍后再试\"}");
            return false;
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip != null ? ip : "unknown";
    }
}
