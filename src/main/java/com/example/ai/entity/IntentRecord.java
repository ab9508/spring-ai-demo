package com.example.ai.entity;

/**
 * 用户意图识别结果（结构化输出）
 * <p>
 * Spring AI 的 BeanOutputConverter 可以让大模型直接返回 Java 对象，
 * 而不是纯文本。底层原理：在 system prompt 中注入 JSON Schema，
 * 要求大模型按格式输出，然后自动反序列化为 Java 对象。
 * <p>
 * 使用场景：前端需要结构化数据（下拉选项、表单填充、路由分发等），
 * 而不是让用户读一段文字。
 */
public record IntentRecord(
        /** 意图类型 */
        String intent,
        /** 提取的参数（JSON 字符串，灵活存放 orderId、productId 等） */
        String params,
        /** 置信度 0~1
         * LLM 对自己判断的把握程度，0~1 的浮点数。0.95 表示很确定，0.3 表示不太确定。生产中低于阈值的走兜底逻辑
         * */
        double confidence,
        /** 原始用户消息 */
        String userMessage
) {
}
