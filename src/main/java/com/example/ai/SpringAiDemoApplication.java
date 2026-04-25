package com.example.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AI 快速入门启动类
 *
 * 运行前请确保：
 * 1. 已配置 DeepSeek API Key（见 application.yml）
 * 2. 网络能访问 api.deepseek.com
 */
@SpringBootApplication
public class SpringAiDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiDemoApplication.class, args);
    }
}
