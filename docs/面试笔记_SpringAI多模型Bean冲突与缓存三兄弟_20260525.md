# 面试笔记：Spring AI 多模型 Bean 冲突 & 缓存三兄弟

> 生成时间：2026-05-25 | 项目：spring-ai-demo

---

## 目录索引

| 序号 | 专题 | 核心知识点 | 状态 |
|------|------|-----------|------|
| [1](#1-spring-ai-多模型共存时的-bean-冲突歧义) | Spring AI 多模型 Bean 冲突 | @Primary、@Qualifier、exclude、工厂路由 | ✅ |
| [1.1](#11-问题根因) | └ 问题根因 | NoUniqueBeanDefinitionException | |
| [1.2](#12-解决思路核心代码) | └ 解决思路（核心代码） | selectedChatModel 工厂方法 | |
| [1.3](#13-用到的注解逐一说明) | └ 注解清单 | 5 个关键注解详解 | |
| [1.4](#14-面试回答节奏逐层递进) | └ 面试回答节奏 | 3 层递进话术 | |
| [1.5](#15-简历写法) | └ 简历写法 | 简历 bullet point | |
| [2](#2-缓存击穿穿透雪崩三兄弟) | 缓存击穿 / 穿透 / 雪崩 | 三者对比 + 解决方案 | ✅ |
| [2.1](#21-一张表说清三者区别) | └ 一张表说清区别 | 定义 / 场景 / 比喻 / 方案 | |
| [2.2](#22-缓存穿透详解) | └ 缓存穿透 | 布隆过滤器 / 空值缓存 | |
| [2.3](#23-缓存击穿详解) | └ 缓存击穿 | 互斥锁 / 逻辑过期 | |
| [2.4](#24-缓存雪崩详解) | └ 缓存雪崩 | TTL 随机化 / 多级缓存 / 限流 | |
| [2.5](#25-面试回答话术30-秒版本) | └ 面试回答话术 | 30 秒快速版 | |
| [3](#3-常用大模型为什么不用国外的--没用过-cursor-怎么办) | 常用大模型 + 不用国外原因 + Cursor 变通 | DeepSeek/Qwen/GLM + 合理性 + 替代方案 | ✅ |
| [3.1](#31-常用大模型全景) | └ 大模型全景 | 国产模型 vs 国外模型对比表 | |
| [3.2](#32-为什么不用国外的合理解释) | └ 为什么不用国外 | 5 个客观理由（不是崇洋媚外那种） | |
| [3.3](#33-没用过-cursor-怎么挽回) | └ 没用过 Cursor 怎么挽回 | 3 段话术把劣势变优势 | |
| [4](#4-大模型在你的实际工作里做了什么) | 大模型在开发中的具体应用 | 辅助开发 / 思路 / 排查 / 学习 / 生成 | ✅ |
| [4.1](#41-一张表说清大模型怎么用的) | └ 一张表说清用法 | 5 大类 + 具体场景 + 频率 | |
| [4.2](#42-面试怎么讲让面试官觉得你很会用) | └ 面试怎么讲 | 3 阶段递进话术 | |

---

## 1. Spring AI 多模型共存时的 Bean 冲突歧义

### 1.1 问题根因

```
项目依赖：
├── spring-ai-starter-model-ollama  → 自动注册 OllamaChatModel（Bean 名: ollamaChatModel）
└── spring-ai-starter-model-openai  → 自动注册 OpenAiChatModel（Bean 名: openAiChatModel）
                                      ↓
                    两个都实现了 ChatModel 接口
                                      ↓
               Spring 注入时 → NoUniqueBeanDefinitionException
```

不仅 ChatModel，**EmbeddingModel 也有同样问题**——Ollama 和 OpenAI 各自注册 EmbeddingModel，PgVectorStore 注入时也会报歧义。

### 1.2 解决思路（核心代码）

```java
// ============ ChatClientConfig.java ============

@Slf4j
@Configuration
public class ChatClientConfig {

    /**
     * 核心方法：工厂/路由模式，根据配置开关返回对应 ChatModel
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "selectedChatModel")
    public ChatModel selectedChatModel(
            @Qualifier("ollamaChatModel") ChatModel ollamaModel,
            @Qualifier("openAiChatModel") ChatModel openaiModel,
            @Value("${app.chat.provider:ollama}") String provider) {

        if ("openai".equalsIgnoreCase(provider)) {
            log.info("【配置】ChatModel → DeepSeek API (openAiChatModel)");
            return openaiModel;
        } else {
            log.info("【配置】ChatModel → Ollama 本地 (ollamaChatModel)");
            return ollamaModel;
        }
    }

    @Bean
    public ChatClient chatClient(ChatModel selectedChatModel) {
        return ChatClient.builder(selectedChatModel).build();
    }

    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel selectedChatModel) {
        return ChatClient.builder(selectedChatModel);
    }
}


// ============ SpringAiDemoApplication.java ============

@SpringBootApplication(exclude = {
        ChatClientAutoConfiguration.class,   // 禁自动 ChatClient，手动管理
        OpenAiEmbeddingAutoConfiguration.class // 禁 OpenAI Embedding，只用 Ollama 的
})
public class SpringAiDemoApplication { ... }
```

```yaml
# application.yml — 一行配置切换模型
app:
  chat:
    provider: openai   # ollama | openai → 重启生效
```

### 1.3 用到的注解逐一说明

| 注解 | 所在位置 | 作用 | 为什么必须用 |
|------|---------|------|-------------|
| **`@Primary`** | `selectedChatModel` 方法 | 标记为**首选 Bean**——多同类型 Bean 时，Spring 默认注入它 | 消解 `NoUniqueBeanDefinitionException`；ChatController → ChatClient → selectedChatModel 整条链无歧义 |
| **`@Qualifier("beanName")`** | `selectedChatModel` 的**方法参数** | 按 **Bean 名称**精准注入，不被类型歧义干扰 | 不同 Starter 注册的 Bean 名固定（`ollamaChatModel` / `openAiChatModel`），非自定义 |
| **`@ConditionalOnMissingBean`** | `selectedChatModel` 方法 | **条件创建**：容器已有同名 Bean 时跳过 | 防御性——避免与其他手动 ChatModel 定义冲突 |
| **`@Value("${...}")`** | `selectedChatModel` 参数 | 读取配置文件，支持冒号后默认值 | 实现**零代码重启切换**，改 yml 即可 |
| **`exclude = {ChatClientAutoConfiguration.class}`** | 启动类 `@SpringBootApplication` | 排除**自动配置** | 不排除的话 AutoConfig 也创建 ChatClient，和手动配置的 Bean 重复 |

### 1.4 面试回答节奏（逐层递进）

#### 第一层（30 秒）—— 抛出问题

> "Spring AI 两个 Starter 都会自动注册 ChatModel 实现类，Spring IoC 容器注入时发现有多个同类型 Bean，启动直接报 `NoUniqueBeanDefinitionException`。EmbeddingModel 同理。"

#### 第二层（60 秒）—— 说出方案 + 注解

> "我在 `ChatClientConfig` 里写了一个 `@Bean` + `@Primary` 的工厂方法 `selectedChatModel`，方法参数用 `@Qualifier` 精准注入 Ollama 和 OpenAI 两个具体 ChatModel，再根据 `@Value` 读取的配置开关，决定返回哪一个。同时启动类上 `exclude` 掉 `ChatClientAutoConfiguration`，避免自动配置的 ChatClient 和手动配置的 Bean 打架。"

#### 第三层（面试官追问时展开）

> **追问点 1：EmbeddingModel 怎么处理的？**
> "用 `exclude = {OpenAiEmbeddingAutoConfiguration.class}` 禁掉 OpenAI 的 EmbeddingModel，只留 Ollama 本地的 nomic-embed-text，PgVectorStore 注入时不会再有歧义。"
>
> **追问点 2：为什么还要手动建 ChatClient.Builder？**
> "排除 `ChatClientAutoConfiguration` 后，容器里没有现成的 `ChatClient.Builder`，但 AgentController 需要它来挂载 Tools、Advisors、Memory，所以我在 Config 里手动注册了一个 Builder Bean，注入的是已经去歧义的 `selectedChatModel`。"
>
> **追问点 3：本质是什么设计模式？**
> "本质上是**工厂模式 + 策略模式**——`selectedChatModel` 是路由代理，封装了选择逻辑，对调用方透明。契合**开闭原则**：切换模型改配置，不动代码。"

### 1.5 简历写法

> 解决 Spring AI 多模型共存时的 Bean 冲突歧义：通过 `@Primary` + `@Qualifier` 双注解注入 + 工厂路由模式，实现 Ollama 本地模型与 DeepSeek API 的动态切换；排除 `ChatClientAutoConfiguration` 自动配置避免二重身冲突；手动管理 ChatClient.Builder，保证 ChatClient / ChatMemory / Tool Calling / RAG Advisor 全链路在双模型环境下稳定运行。

---

## 2. 缓存击穿 / 穿透 / 雪崩（三兄弟）

### 2.1 一张表说清三者区别

| 维度 | 缓存穿透 | 缓存击穿 | 缓存雪崩 |
|------|---------|---------|---------|
| **本质** | 查的 key **根本不存在** | 一个**热点 key 过期** | 大量 key **同时过期** 或 **Redis 宕机** |
| **请求打到哪** | 直奔数据库 | 数据库（瞬间高并发） | 数据库（大面积） |
| **比喻** | 去图书馆借一本**不存在**的书 | 所有人抢同一本**刚被借走的**畅销书 | 图书馆**闭馆**了 |
| **关键特征** | 恶意攻击 / 业务数据缺失 | 热点商品秒杀 / 高并发 | 批量过期 / 服务器故障 |
| **解决方向** | 拦截非法请求 + 缓存空值 | 保证热点 key 不失效 + 串行化 | TTL 打散 + 多级降级 + 高可用 |

### 2.2 缓存穿透详解

![缓存穿透示意](https://via.placeholder.com/600x200/e74c3c/ffffff?text=请求→缓存(miss)→数据库(miss)→返回空→攻击者换个key继续)

**场景**：查询一个**数据库中根本不存在的 key**（如 `id=-1`），缓存永远查不到，每次都穿透到数据库。

**危害**：恶意攻击者构造大量不存在的 key，打崩数据库。

**解决方案（3 种，按推荐度排序）**：

| 方案 | 原理 | 优缺点 |
|------|------|--------|
| **布隆过滤器**（⭐推荐） | 在缓存前加一层 BitMap，快速判断「**一定不存在**」 | 内存极小；有 1% 误判率（说不存在就真不存在） |
| **缓存空值** | 把 `null` 也缓存起来，设短 TTL（2-5 分钟） | 简单粗暴；短时间可能缓存大量无用 key |
| **接口层校验** | 校验参数合法性（id 不能为负、范围检查） | 最基础防线；防不了业务上不存在的合法 id |

**布隆过滤器代码示意**：

```java
// Redisson 提供的布隆过滤器
RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter("product:bloom");
bloomFilter.tryInit(100_0000, 0.01); // 100万容量，1% 误判率

// 查询时先过布隆
public Product getById(Long id) {
    if (!bloomFilter.contains(id)) {
        return null; // 一定不存在，拦截
    }
    // 可能存在，正常走缓存→数据库
    Product p = cache.get(id);
    if (p == null) {
        p = db.select(id);
        cache.set(id, p, 30, TimeUnit.MINUTES);
    }
    return p;
}
```

### 2.3 缓存击穿详解

![缓存击穿示意](https://via.placeholder.com/600x200/f39c12/ffffff?text=N个请求→缓存(key过期!)→全部穿透→数据库→瞬间高并发)

**场景**：一个**热点 key**（如秒杀商品）过期瞬间，大量并发请求同时打到数据库。

**危害**：瞬间高并发打爆数据库连接池，拖垮整个服务。

**解决方案（2 种）**：

| 方案 | 原理 | 适用场景 |
|------|------|---------|
| **互斥锁**（⭐最常用） | 缓存 miss 后，只让**一个线程**查数据库并回写缓存，其他线程等待 | 一致性要求高 |
| **逻辑过期** | 不设 TTL，value 里存过期时间戳；查到时判断是否过期，过期则异步重建 | 高可用优先 |

**互斥锁方案代码**：

```java
public Product getById(Long id) {
    // 1. 查缓存
    Product p = cache.get(id);
    if (p != null) return p;

    // 2. 缓存 miss → 加分布式锁，只让一个线程查库
    String lockKey = "lock:product:" + id;
    RLock lock = redissonClient.getLock(lockKey);
    try {
        // 等 1 秒，锁 30 秒自动释放（防止死锁）
        if (lock.tryLock(1, 30, TimeUnit.SECONDS)) {
            // 双重检查：其他线程可能已更新
            p = cache.get(id);
            if (p != null) return p;

            p = db.select(id);
            cache.set(id, p, 30, TimeUnit.MINUTES);
            return p;
        } else {
            // 没拿到锁 → 等一会重试
            Thread.sleep(100);
            return getById(id); // 递归重试
        }
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

**逻辑过期方案对比**：

```java
// value 结构：包含数据和过期时间戳
class RedisData {
    Object data;           // 商品信息
    LocalDateTime expireTime; // 逻辑过期时间
}

public Product getById(Long id) {
    RedisData redisData = cache.get(id);
    if (redisData == null) return null;

    Product p = redisData.getData();
    // 判断是否逻辑过期
    if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
        return p; // 未过期，直接返回
    }
    // 已过期 → 返回旧数据 + 异步重建缓存
    if (tryLock("lock:product:" + id)) {
        threadPool.execute(() -> {
            Product fresh = db.select(id);
            RedisData freshData = new RedisData(fresh, now().plusMinutes(30));
            cache.set(id, freshData);
        });
    }
    return p; // 返回旧数据，用户体验不受影响
}
```

### 2.4 缓存雪崩详解

![缓存雪崩示意](https://via.placeholder.com/600x200/c0392b/ffffff?text=大量key同时过期→请求洪峰→数据库→服务不可用)

**场景**：
- **批量过期**：大量 key 设了相同的 TTL，同一时刻集体过期
- **Redis 宕机**：缓存服务本身挂了，所有请求穿透

**危害**：数据库瞬间承受巨大压力，可能引发级联故障，整个系统雪崩。

**解决方案（3 层防御）**：

| 方案 | 原理 | 层级 |
|------|------|------|
| **TTL 加随机值** | 过期时间 = 基础时间 + 随机偏移（如 30min ± 5min） | 事前预防 |
| **多级缓存** | 本地 Caffeine → Redis → DB，逐级降级 | 事中降级 |
| **限流 + 熔断** | Sentinel / Hystrix 控制并发量，过载时快速失败 | 事后兜底 |
| **Redis 高可用** | 主从 + 哨兵 / Cluster | 基础设施 |

**TTL 随机化代码**：

```java
// 核心：不要写死 TTL
public void setWithRandomTTL(String key, Object value, int baseMinutes, int randomRange) {
    int random = ThreadLocalRandom.current().nextInt(randomRange);
    int ttl = baseMinutes + random; // 30min + [0, 10)min
    redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.MINUTES);
}
```

**多级缓存降级链路**：

```
请求 → 本地缓存(Caffeine) miss → Redis miss → 数据库
                ↑ 命中返回       ↑ 命中返回     ↑ 兜底
```

### 2.5 面试回答话术（30 秒版本）

> **一句话区分**：
> - **穿透**是查**不存在**的东西 → 布隆过滤器
> - **击穿**是**热点 key 过期**了 → 互斥锁
> - **雪崩**是**大量 key 同时过期**或 **Redis 挂了** → TTL 随机化 + 多级缓存
>
> **记忆口诀**：穿透打穿缓存直奔 DB（key 不存在），击穿打穿孔让热点 key 单独带崩 DB（单 key 过期），雪崩大面积塌方（批量过期 / 宕机）。

---

## 3. 常用大模型 + 为什么不用国外的 + 没用过 Cursor 怎么办

### 3.1 常用大模型全景

#### 国产大模型（你在用的）

| 模型 | 开发商 | 定位 | 你项目里的角色 |
|------|--------|------|--------------|
| **DeepSeek-V3 / R1** | 深度求索 | 通用对话 + 推理，OpenAI 兼容 API | `openAiChatModel` → 主力对话模型 |
| **DeepSeek-Coder** | 同上 | 代码生成专用 | 可替代 V3 做代码补全 |
| **通义千问 Qwen-max** | 阿里云 | 通用，多模态强 | 备选，Spring AI 有 Qwen 适配 |
| **智谱 GLM-4** | 智谱 AI | 中文理解强，Agent 能力强 | 初期考虑过，余额不足改用 Ollama |
| **文心一言 ERNIE-4.0** | 百度 | 搜索整合 + 知识增强 | 未使用，可作为对比项 |
| **Kimi (Moonshot)** | 月之暗面 | 长上下文（200 万 token） | 文档分析场景可选 |
| **豆包** | 字节跳动 | C 端产品化 | 不涉及 |

#### 国外大模型（为什么知道但不用）

| 模型 | 开发商 | 优势 | 为什么你项目没用 |
|------|--------|------|-----------------|
| **GPT-4o / GPT-4** | OpenAI | 综合最强，多模态 | API 需海外支付 + 数据出境合规 + 贵 |
| **Claude 3.5/4** | Anthropic | 代码 + 长文本最佳 | 同上，且 Spring AI 适配不如 OpenAI 兼容协议成熟 |
| **Gemini 2.5 Pro** | Google | 多模态 + 100 万上下文 | 国内网络不稳定 |
| **Llama 3/4** | Meta | 开源可私有化 | 本地部署成本高（需要 GPU），Ollama 跑 8B 级别够用 |

### 3.2 为什么不用国外的（合理解释）

> ⚠️ 面试时千万别只说「用不了」，而要从**成本、合规、生态**三个维度讲出你的选择逻辑。

| 原因 | 具体说明 | 面试怎么措辞 |
|------|---------|------------|
| **成本** | GPT-4 API $30/百万 token，DeepSeek 官网免费 + API 极低 | "DeepSeek API 成本不到 GPT-4 的 1/30，但中文场景效果差异不大，做 Demo 和学习完全够用" |
| **网络可达性** | 直连不稳定，需要代理 | "国内环境下 DeepSeek API 延迟更低，对实时对话体验更友好" |
| **数据合规** | 金融/信贷项目数据不能出境 | "我之前的信贷项目涉及敏感数据，合规要求只能用国内模型或本地部署" |
| **生态适配** | Spring AI 对 OpenAI 兼容协议支持最成熟，DeepSeek 天然兼容 | "Spring AI 的 OpenAiChatModel 直接配 base-url 就能接 DeepSeek，零额外适配成本" |
| **本地备份** | Ollama 可离线跑 8B 模型，不依赖网络 | "用 Ollama 本地部署 deepseek-r1:8b 做 Embedding 和离线场景，免费且无网络依赖" |

**面试口径一句话**：
> "我项目里是 DeepSeek 为主（API）+ Ollama 本地作备份，选型逻辑是成本优先、合规可行、Spring AI 生态最适配。国外的 GPT-4/Claude 我了解能力和适用场景，但在当前项目阶段没必要。"

### 3.3 没用过 Cursor 怎么挽回

> 面试官问：「用过 Cursor 吗？」
> 别慌张。**没做过 =/= 不懂这个概念。** Cursor 只是 AI 辅助编码的工具之一。

#### 策略：把「没用过 Cursor」转化为「我用的是更深层的方案」

**话术第一段（承认 + 转折）**：
> "Cursor 是 IDE 层的 AI 集成方案，我主要是用 CLI/终端侧的工具——既有通用对话的 AI 助手做需求分析和方案讨论，也能在终端里直接让 AI 读代码、改代码、跑命令。这种方式让我对每一步生成的代码都有完整的掌控，不会出现 IDE 里一键 apply 完自己都不知道改了什么的问题。"

**话术第二段（具体替代方案说明）**：
> "比如我在做 Spring AI 项目的时候，遇到 Bean 冲突的问题，我先让 AI 帮我分析了 OlaamaChatModel 和 OpenAiChatModel 的 AutoConfiguration 源码，确认了冲突链路，然后我们一起设计了 @Primary + @Qualifier + 工厂模式的解决方案，代码结构是讨论出来的，不是一键生成的。"

**话术第三段（升华，如果你用过 CodeBuddy/WorkBuddy）**：
> "其实 Cursor 的思路是对的，我理解它的核心价值是：上下文感知（读整个项目）+ 内联编辑 + 多轮对话。但我现在的方案在上下文深度上更强——AI 助手能看到我完整的项目结构、配置文件、依赖关系，做出的建议更贴合实际代码，而不是泛泛的自动补全。"

**关键点**：
- 不否认没用过
- 展示你**知道自己需要什么**（不是盲目用工具）
- 展示你**实际在用的方案更有深度**（代码理解 + 架构决策的参与度更高）

---

## 4. 大模型在你的实际工作里做了什么

### 4.1 一张表说清——大模型怎么用的

| 类别 | 具体场景 | 你能举的实例 | 频率 |
|------|---------|------------|------|
| **辅助开发** | 写代码、生成模板、格式转换 | "Spring AI 的 ChatClientConfig 框架是 AI 帮搭的，但 @Qualifier 的 bean 名称、exclude 哪个 AutoConfiguration 是查了源码后自己定的" | 天天 |
| **思路讨论** | 方案选型、架构设计、设计模式 | "RAG 切片策略选 OverlappingTextSplitter 还是按标题切，讨论了 chunk_size 和 overlap 的权衡" | 每次新功能 |
| **排查问题** | 报错分析、日志解读、性能瓶颈 | "DeepSeek R1 返回 Think 标签导致 Ollama 对话卡死，AI 帮我分析了 R1 的响应结构，定了在返回值里 strip Think 标签的方案" | 出问题时 |
| **学习新东西** | 概念理解、技术对比、快速入门 | "MCP 协议学习时，让 AI 画了 MCP Server/Client 的交互流程图，3 天完成从零到 demo" | 每周 |
| **内容生成** | 文档、注释、测试用例、配置文件 | "application.yml 的多环境配置、Docker Compose 的 PostgreSQL + Redis 编排文件" | 需要时 |
| **代码审查** | 检查逻辑漏洞、不合理的写法 | "AgentController 里 ConcurrentHashMap 线程安全问题，AI 提醒换成 ChatMemory 抽象" | 关键节点 |

### 4.2 面试怎么讲（让面试官觉得你很会用）

#### 第一层（30 秒）—— 快速覆盖场景

> "我在日常开发中主要用大模型做五件事：写代码时做骨架搭建，选型时做方案对比，出 bug 时辅助排查，学新技术时快速理解概念，最后是生成文档和配置。它不是替代我的判断，而是帮我节省查文档、写模板和试错的时间。"

#### 第二层（60 秒）—— 举一个具体实例

> "比如我在 Spring AI 项目中做 Tool Calling Agent，我有个关键问题：ChatMemory 多轮对话和 RAG Advisor 同时挂载时，执行顺序会不会互相干扰。我先让 AI 帮我读了 MessageChatMemoryAdvisor 和 QuestionAnswerAdvisor 的源码，确认了 Advisor 链的执行模型——before 阶段按注册顺序执行。最后我们决定把 RAG Advisor 放在 Memory Advisor 前面，因为先注入知识上下文再加载历史消息，比反过来更合理。这个决策是讨论出来的，不是生成出来的。"

#### 第三层（面试官追问）—— 亮出判断力

> **追问：你觉得大模型的局限是什么？**
> "两个痛点：一是版本滞后，Spring AI 最新 API 它可能不知道，必须查官方文档核对；二是过度自信，它会给看起来很合理的方案但实际跑不通——比如它建议我用 `entity()` 方法做结构化输出，但 Spring AI 1.0.5 根本没这个 API，我最后用 `call() + Jackson` 手动反序列化。所以我的原则是：AI 生成框架、我决定细节；AI 提供方向、我验证可行性。"

---

*后续新专题按编号 [5]、[6]…追加，并在目录索引中登记。*
