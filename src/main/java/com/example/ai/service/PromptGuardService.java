package com.example.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Prompt 注入防护服务
 * <p>
 * 检测用户输入中是否包含尝试操纵 LLM 的注入攻击模式。
 * 使用多层检测策略：
 *   1. 关键词匹配（常见注入短语）
 *   2. 正则模式匹配（边界绕过）
 *   3. 角色劫持检测（试图改变 LLM 角色设定）
 */
@Slf4j
@Service
public class PromptGuardService {

    // ============ 注入关键词黑名单 ============
    private static final List<String> INJECTION_KEYWORDS = List.of(
            // 忽略系统提示
            "忽略系统提示", "忽略以上", "忽略前面",
            "ignore system", "ignore system prompt", "ignore all instructions",
            "ignore previous", "ignore all previous",

            // 角色劫持
            "你现在是", "你扮演", "你是一个", "你现在扮演",
            "从现在开始你", "reset", "new role",

            // 越权指令
            "执行SQL", "执行命令", "删除数据库", "drop table",
            "delete from", "update set",

            // 数据泄露
            "输出系统提示", "显示系统提示", "泄漏系统提示",
            "print system prompt", "show system prompt",
            "reveal prompt", "output your instructions",

            // 越狱
            "DAN", "jailbreak", "越狱", "解锁限制",
            "bypass", "by pass", "no restrictions"
    );

    // ============ 正则表达式模式 ============
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            // 试图以 system 身份注入
            Pattern.compile("\\[system\\].*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<system>.*</system>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\{system\\}.*", Pattern.CASE_INSENSITIVE),
            // 试图覆盖 system prompt 的 markdown 格式
            Pattern.compile("(?s)```.*system.*```", Pattern.CASE_INSENSITIVE),
            // base64 编码的指令（混淆绕过）
            Pattern.compile("^[A-Za-z0-9+/]{40,}={0,2}$"),
            // 十六进制编码指令
            Pattern.compile("^[0-9a-fA-F]{40,}$")
    );

    // ============ 敏感操作权限白名单 ============
    // 在 @Tool 方法内部做二次校验
    // 已在 OrderTools.queryOrder() 中通过 userId 校验数据归属

    /**
     * 检测用户输入是否安全
     * @return SAFE 或 BLOCKED(reason)
     */
    public GuardResult check(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return GuardResult.safe();
        }

        // 1. 关键词检测
        for (String keyword : INJECTION_KEYWORDS) {
            if (userInput.contains(keyword)) {
                log.warn("【Prompt注入】关键词命中: '{}' 输入: {}", keyword, truncate(userInput, 100));
                return GuardResult.blocked("输入包含非法指令关键词");
            }
        }

        // 2. 正则检测
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(userInput).find()) {
                log.warn("【Prompt注入】正则命中: '{}' 输入: {}", pattern, truncate(userInput, 100));
                return GuardResult.blocked("输入包含不安全的指令格式");
            }
        }

        return GuardResult.safe();
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /**
     * 检测结果。使用 sealed class 确保编译器检查所有分支。
     */
    public sealed abstract static class GuardResult {
        private final boolean blocked;
        private final String reason;

        private GuardResult(boolean blocked, String reason) {
            this.blocked = blocked;
            this.reason = reason;
        }

        public boolean isBlocked() { return blocked; }
        public String reason() { return reason; }

        public static final class Safe extends GuardResult {
            public Safe() { super(false, null); }
        }
        public static final class Blocked extends GuardResult {
            public static final String DEFAULT_MESSAGE = "输入包含不安全的内容";
            public Blocked(String reason) { super(true, reason); }
        }

        static GuardResult safe() { return new Safe(); }
        static GuardResult blocked(String reason) { return new Blocked(reason); }
    }
}
