package com.example.ai.config;

import com.example.ai.interceptor.RateLimitInterceptor;
import com.example.ai.interceptor.TenantInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置——注册拦截器
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final TenantInterceptor tenantInterceptor;

    public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor,
                        TenantInterceptor tenantInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.tenantInterceptor = tenantInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 租户拦截器（最先执行，为后续请求提供租户上下文）
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/rag/**", "/agent/**", "/chat/**");

        // 限流拦截器
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/rag/**", "/agent/**", "/chat/**", "/admin/**")
                .excludePathPatterns("/admin/logs/**");
    }
}
