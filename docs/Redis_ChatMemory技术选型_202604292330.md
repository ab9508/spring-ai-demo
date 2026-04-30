# Redis ChatMemory 技术选型记录

> 日期：2026-04-29
> 背景：Spring AI Demo 项目从内存 ChatMemory 升级到 Redis 持久化存储

---

## 目录

- [一、需求背景](#一需求背景)
- [二、方案对比](#二方案对比)
- [三、最终决策](#三最终决策)
- [四、实现细节](#四实现细节)
- [五、踩坑总结](#五踩坑总结)
- [六、面试包装](#六面试包装)

---

## 一、需求背景

当前项目已完成内存版 ChatMemory（InMemoryChatMemoryRepository），多轮对话测试通过。
下一步需要将对话记忆持久化，重启不丢失。

**项目技术栈约束：**
- Spring Boot **3.4.5**
- Spring AI **1.0.5**
- 本地已有 Docker 部署：PostgreSQL + Redis

---

## 二、方案对比

### 2.1 三个候选方案

| 维度 | ① JDBC ChatMemory | ② Spring AI Alibaba | ③ 手写 RedisChatMemory |
|------|-------------------|---------------------|------------------------|
| Spring AI 原生支持 | ✅ 开箱即用 | ❌ 第三方扩展 | ✅ 实现标准接口 |
| artifactId | `spring-ai-starter-model-chat-memory-repository-jdbc` | `spring-ai-alibaba-starter-memory-redis` | 无需额外依赖 |
| 版本兼容性 | ✅ 1.0.5 BOM 包含 | ❌ 要求 Spring Boot 3.5.x | ✅ 零版本冲突 |
| 零部署成本 | ✅ 已有 PostgreSQL | ✅ 已有 Redis | ✅ 已有 Redis |
| 面试亮点 | 一般，太基础 | 有但受限 | **最高** |
| 工作量 | 极低（加一个依赖） | 高（升降级框架版本） | 中（手写实现类） |
| 踩坑学习价值 | 低 | 中（版本冲突排查） | **高**（接口设计+序列化+Redis数据结构） |
| 企业场景匹配度 | 低（聊天记录存PG不常见） | 中 | **高**（Redis 是会话存储标配） |

### 2.2 方案② Spring AI Alibaba 的致命问题

**版本不兼容：**

| 你当前版本 | SAA 1.0.x 要求 | SAA 1.1.x 要求 |
|-----------|----------------|----------------|
| Spring Boot 3.4.5 | Spring AI 1.0.0 | Spring Boot 3.5.x |
| Spring AI 1.0.5 | Spring Boot 3.4.x | Spring AI 1.1.x |

SAA 没有任何版本同时兼容 Spring Boot 3.4.5 + Spring AI 1.0.5。

**如果强行用 SAA，需要：**
- 升级 Spring Boot 3.4.5 → 3.5.x（影响整个项目）
- 降级 Spring AI 1.0.5 → 1.0.0（丢失 bug fix）
- 全量回归测试

**结论：不可行，代价远大于收益。**

### 2.3 `spring-ai-redis-store-spring-boot-starter` 的误解

这个 artifactId 在 1.0.5 BOM 中 **不存在**（Maven 报错：`version is missing`）。

实际存在的是 `spring-ai-starter-vector-store-redis`——它是 **Redis Vector Store**（向量存储），
跟 ChatMemory（对话记忆）是两个完全不同的功能。

---

## 三、最终决策

**方案③：手写 RedisChatMemoryRepository**

**决策依据：**
1. **零版本风险**：不碰框架版本，继续用 Spring Boot 3.4.5 + Spring AI 1.0.5
2. **Redis 是核心技能栈**：9年 Java 后端，Redis 是简历核心关键词
3. **面试有话聊**：接口设计、Redis 数据结构选择、序列化方案都是面试加分点
4. **企业真实场景**：会话存 Redis 是生产环境标配，比 JDBC 更有说服力
5. **Spring AI 开放接口**：`ChatMemoryRepository` 是标准接口，`MessageWindowChatMemory.builder().chatMemoryRepository()` 直接支持注入

---

## 四、实现细节

### 4.1 核心类关系

```
ChatMemoryRepository (接口，Spring AI 定义)
    ├── InMemoryChatMemoryRepository (内置，内存)
    ├── JdbcChatMemoryRepository (内置，JDBC)
    ├── CassandraChatMemoryRepository (内置)
    ├── Neo4jChatMemoryRepository (内置)
    └── RedisChatMemoryRepository (手写，本项目) ← implements ChatMemoryRepository
```

### 4.2 ChatMemoryRepository 接口方法

```java
public interface ChatMemoryRepository {
    List<String> findConversationIds();          // 查找所有会话ID
    List<Message> findByConversationId(String id); // 获取指定会话的消息
    void saveAll(String id, List<Message> messages); // 保存消息（覆盖式）
    void deleteByConversationId(String id);      // 删除指定会话
}
```

### 4.3 Redis 数据结构

| 用途 | 数据结构 | Key 格式 | 说明 |
|------|---------|----------|------|
| 存储消息 | **List** | `chat:memory:{conversationId}` | 左旧右新，按时间顺序排列 |
| 查找会话 | KEYS 命令 | `chat:memory:*` | 遍历所有会话Key |

**为什么选 List？**
- 消息是按时间有序的，List 天然支持顺序
- `RPUSH` 写入、`LRANGE` 范围读取，O(1) 操作
- `saveAll` 是覆盖式写入（MessageWindowChatMemory 每次传入完整窗口），先 DELETE 再 RPUSH

### 4.4 序列化方案

**问题**：Spring AI 的 `UserMessage`、`AssistantMessage` 等类**没有默认构造方法**，
Jackson/FastJSON 自动反序列化会失败。

**解决**：手动 JSON 序列化/反序列化，只提取 `messageType` + `text` 两个字段。

```java
// 序列化：只保留 messageType + text，丢弃 metadata
{"messageType":"USER","text":"你好"}

// 反序列化：根据 messageType 手动创建 Message 子类
switch (messageType) {
    case USER -> new UserMessage(text);
    case SYSTEM -> new SystemMessage(text);
    case ASSISTANT -> new AssistantMessage(text);
}
```

**为什么不用 Jackson/FastJSON？**
- 避免引入额外依赖（项目目前没有 FastJSON）
- 只需处理两个字段，手写字符串拼接比引入库更轻量
- `getMessageType()` 返回 `MessageType` 枚举（不是 String），手动 `.name()` 转换

### 4.5 配置类

```java
@Configuration
public class RedisChatMemoryConfig {
    @Bean
    public RedisChatMemoryRepository redisChatMemoryRepository(StringRedisTemplate redisTemplate) {
        return new RedisChatMemoryRepository(redisTemplate);
    }

    @Bean
    public ChatMemory chatMemory(RedisChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(20)
                .build();
    }
}
```

**此 Bean 注册后，Spring AI 自动配置的 InMemoryChatMemory 被覆盖，**
**AgentController 注入的 ChatMemory 自动切换到 Redis 存储。无需修改 Controller 代码。**

### 4.6 依赖变更

| 变更 | artifactId | 说明 |
|------|-----------|------|
| 新增 | `spring-boot-starter-data-redis` | Spring Data Redis 基础依赖 |
| 废弃（注释） | ~~`spring-ai-redis-store-spring-boot-starter`~~ | 1.0.5 BOM 中不存在 |

---

## 五、踩坑总结

| # | 问题 | 根因 | 解决 |
|---|------|------|------|
| 1 | `spring-ai-redis-store-spring-boot-starter` 下载失败 | artifactId 在 1.0.5 BOM 中不存在 | 这个是 Redis Vector Store 不是 ChatMemory |
| 2 | Spring AI Alibaba 版本冲突 | 要求 Spring Boot 3.5.x | 放弃，改手写实现 |
| 3 | `MessageChatMemoryAdvisor(chatMemory)` 编译报错（上轮） | 1.0.x 只有 Builder 模式 | `.builder(chatMemory).build()` |
| 4 | Message 子类无法自动反序列化 | 没有默认构造方法 | 手动 JSON 解析 + switch 创建实例 |

---

## 六、面试包装

### 可聊的技术点

1. **为什么选 Redis 而不是 JDBC？**
   - 会话数据具有临时性、高并发读写的特点，Redis 更适合
   - 支持过期自动清理（TTL），JDBC 需要定时任务
   - 分布式部署时天然共享，JDBC 需要额外的连接池管理

2. **Redis 数据结构选择？**
   - List：消息有序、支持范围查询、写入性能好
   - 没选 Hash（单个 Key 存所有消息，无法保证顺序、大 Value 性能差）
   - 没选 ZSet（不需要按时间戳排序，List 本身就是有序的）

3. **序列化方案？**
   - 没用 JDK 序列化（Message 没实现 Serializable）
   - 没用 Jackson 自动反序列化（Message 子类没有默认构造方法）
   - 只提取 messageType + text 两个字段，轻量手动解析

4. **接口设计模式？**
   - Spring AI 采用策略模式：ChatMemoryRepository 接口 + 多种实现
   - 自定义实现只需 implements 接口，通过 Builder 注入，无需改业务代码
   - 符合开闭原则

### 简历可写

> 基于 Redis 手写实现 ChatMemoryRepository，替代 InMemory 方案，
> 支持多轮对话持久化存储，解决 Spring AI 1.0.x 原生不支持 Redis 的问题。
