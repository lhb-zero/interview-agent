# DeepSeek API 集成 — 技术选型与实现文档

> 版本：v1.0
> 日期：2026-05-08
> 模块：LLM Provider 多模型切换（Ollama 本地 ↔ DeepSeek 云端 API）
> 框架：Spring AI 1.1.0 + Spring Boot 3.3.6

---

## 目录

- [一、为什么需要调用 LLM API](#一为什么需要调用-llm-api)
- [二、业界方案调研](#二业界方案调研)
- [三、最终选型决策](#三最终选型决策)
- [四、落地实现方案](#四落地实现方案)
- [五、集成位置与扩展](#五集成位置与扩展)
- [六、参考资料](#六参考资料)

---

## 一、为什么需要调用 LLM API

### 1.1 问题背景

| 痛点 | 具体表现 | 影响 |
|------|---------|------|
| **本地算力瓶颈** | RTX 2060 (6GB VRAM) 只能跑 1.7b 小模型，4b 模型需 CPU 卸载 | 回答质量差，复杂问题答不上来 |
| **推理速度慢** | qwen3:1.7b 本地推理 21.7 tokens/s，qwen3.5:4b 仅 1.6 tokens/s | 用户等待时间长，体验差 |
| **模型能力天花板** | 1.7b 模型对长文本理解、复杂推理、指令遵循能力弱 | RAG 查询改写质量不稳定，评估打分不准 |
| **无云端对比基准** | 只有本地模型，无法量化"本地 vs 云端"的效果差距 | 面试时无法展示多模型对比能力 |

### 1.2 技术价值

**核心思路**：在不改变现有代码架构的前提下，通过 Spring AI 的 `ChatModel` 接口抽象 + 条件装配，实现本地 Ollama 和云端 DeepSeek API 的一键切换。

**引入前后的链路对比**：

```
【引入前 — 仅本地 Ollama】

用户请求 → ChatService → ChatClient → OllamaChatModel → Ollama 本地 (qwen3:1.7b)
                                                              ↓
                                                    受限于 GPU 6GB VRAM
                                                    最大只能跑 1.7b 模型


【引入后 — Ollama + DeepSeek 双 Provider】

用户请求 → ChatService → ChatClient → ChatModel (接口)
                                            ↓
                              ┌───────────────┴───────────────┐
                              ↓                               ↓
                    OllamaChatModel                   DeepSeekChatModel
                    (本地 Ollama)                     (云端 DeepSeek API)
                              ↓                               ↓
                    qwen3:1.7b (21.7 tok/s)         deepseek-v4-pro (云端高速)
                    适合日常对话、快速响应              适合复杂推理、高质量回答
                              ↑                               ↑
                              └─────── spring.ai.model.chat ──┘
                                    配置一键切换，代码零改动
```

### 1.3 效果示例

| 指标 | 优化前（仅 Ollama 1.7b） | 优化后（DeepSeek v4-pro） |
|------|--------------------------|--------------------------|
| 模型参数量 | 1.7B | 未公开（推测 200B+ MoE） |
| 推理速度 | 21.7 tokens/s（本地 GPU） | 云端高速（~50-100 tokens/s） |
| 复杂问题回答质量 | 较差，经常答非所问 | 优秀，接近 GPT-4 水平 |
| 深度思考模式 | 1.7b 模型 thinking 质量差 | deepseek-v4-pro 原生支持，推理链清晰 |
| 成本 | 电费 + 硬件折旧 | ~¥0.5/百万 token（极低） |
| 部署依赖 | 需要 GPU + Ollama 服务 | 仅需网络连接 |

---

## 二、业界方案调研

### 2.1 方案全景图

```
LLM 调用方案
├── 1. 直接 HTTP 调用
│   ├── OkHttp / RestClient 手写请求
│   └── 最灵活，但需自己处理序列化/流式/重试
│
├── 2. 厂商官方 SDK
│   ├── OpenAI Java SDK (openai-java)
│   ├── DeepSeek Java SDK（社区维护）
│   └── 智谱 / 通义千问 SDK
│
├── 3. 统一框架 SDK
│   ├── Spring AI（Spring 官方，本项目使用）
│   ├── LangChain4j（LangChain Java 版）
│   └── Semantic Kernel（Microsoft）
│
└── 4. 网关/代理层
    ├── OpenRouter（多模型统一网关）
    ├── LiteLLM（Python 生态，Java 不友好）
    └── One API / New API（开源 API 管理）
```

### 2.2 各方案详解

#### 方案 1：直接 HTTP 调用

**原理**：用 `RestClient` / `OkHttp` 直接向 `https://api.deepseek.com/chat/completions` 发送 HTTP POST 请求。

**优点**：
- 零依赖，最灵活
- 可以完全控制请求体（如注入自定义 `thinking` 参数）

**缺点**：
- 需自己处理 JSON 序列化、SSE 流式解析、错误重试、超时控制
- 无法复用 Spring AI 的 ChatClient/Tool Calling/Advisor 等高级能力
- 切换模型时需要改代码

#### 方案 2：厂商官方 SDK

**原理**：使用 DeepSeek 或 OpenAI 官方 Java SDK 调用。

**优点**：
- 官方维护，API 兼容性好
- 类型安全，IDE 提示完善

**缺点**：
- 每个厂商一个 SDK，切换模型需要改代码
- 无法与 Spring AI 的 ChatClient/Tool Calling 集成
- DeepSeek 官方无 Java SDK（仅 Python/Node）

#### 方案 3：统一框架 SDK（Spring AI）

**原理**：Spring AI 是 Spring 官方的 AI 应用框架，提供统一的 `ChatModel` 接口，底层支持 OpenAI/Ollama/Azure/Anthropic 等多种 Provider。

**优点**：
- 统一接口：`ChatModel.call(Prompt)` / `ChatClient.prompt().call()`
- 内置 Tool Calling、Advisor 链、RAG 集成
- 通过配置切换 Provider，代码零改动
- Spring 生态无缝集成（DI/AOP/Boot Starter）

**缺点**：
- 框架版本迭代快，API 可能变化
- 某些 Provider 特有功能（如 DeepSeek thinking）需自行扩展

#### 方案 4：网关/代理层

**原理**：部署一个 API 网关，将请求转发到不同的 LLM Provider。

**优点**：
- 统一管理 API Key、限流、计费
- 可以在网关层做负载均衡和故障转移

**缺点**：
- 多一层网络跳转，增加延迟
- 需要额外部署和维护网关服务
- 对本项目（单用户面试助手）过度设计

### 2.3 横向对比

| 维度 | 直接 HTTP | 厂商 SDK | Spring AI | API 网关 |
|------|----------|----------|-----------|---------|
| 实现成本 | 低（但维护成本高） | 中 | 低（已有基础） | 高 |
| Provider 切换 | 改代码 | 改代码 | 改配置 | 改配置 |
| 流式输出 | 自己实现 SSE 解析 | SDK 内置 | 框架内置（Flux） | 网关处理 |
| Tool Calling | 自己实现 | 部分支持 | 框架内置 | 不支持 |
| 与 Spring 集成 | 无 | 无 | 原生 | 无 |
| 自定义参数注入 | 完全可控 | 受 SDK 限制 | 需扩展（本项目方案） | 受网关限制 |
| 适用场景 | 简单调用 | 单一 Provider | 多 Provider 统一 | 企业级多模型管理 |

### 2.4 核心启示

1. **统一接口是关键**：无论底层用哪个 LLM，上层代码应该只依赖抽象接口（`ChatModel`），不依赖具体实现
2. **配置驱动切换**：Provider 切换应该是配置问题，不是代码问题
3. **自定义参数需要拦截器机制**：厂商特有参数（如 DeepSeek thinking）无法通过标准接口传递，需要在 HTTP 层注入
4. **流式输出需要端到端支持**：从 LLM API → 后端 → SSE → 前端，全链路都需要支持流式

---

## 三、最终选型决策

### 3.1 决策理由

**选择方案 3（Spring AI）+ 自定义扩展**，原因：

| 因素 | 分析 |
|------|------|
| **已有基础** | 项目已使用 Spring AI 1.1.0 + Ollama，`ChatModel` 接口抽象已就位 |
| **切换成本** | 只需新增一个 `ChatModel` Bean + 配置项，不改动任何 Service 代码 |
| **DeepSeek 兼容性** | DeepSeek API 兼容 OpenAI 格式，可复用 Spring AI 的 `OpenAiChatModel` |
| **thinking 参数** | DeepSeek 特有，Spring AI 不原生支持，需要自定义扩展 |
| **Tool Calling** | 项目已有 Tool Calling 机制（KnowledgeSearchTool、QuestionGenerateTool），需要框架级支持 |

### 3.2 核心策略

**DeepSeek thinking 参数的注入策略**是本方案的核心难点。

DeepSeek API 的 `thinking` 参数是一个**请求体级别的自定义字段**：
```json
{
  "model": "deepseek-v4-pro",
  "messages": [...],
  "thinking": {          // ← 这是 DeepSeek 自定义字段
    "type": "enabled"    // ← Spring AI 的 OpenAiChatModel 不会生成这个字段
  }
}
```

**三种注入方案对比**：

| 方案 | 原理 | 优点 | 缺点 |
|------|------|------|------|
| A. 修改请求体 | HTTP 拦截器在发送前修改 JSON | 完全透明，调用方无感知 | 需要 ThreadLocal 控制 |
| B. 自定义序列化 | 扩展 Jackson 序列化器 | 更底层 | 复杂，影响全局 |
| C. 继承重写 | 继承 OpenAiChatModel 重写 call() | 直接 | 需要跟踪父类变化 |

**选择方案 A**：HTTP 拦截器 + ThreadLocal 控制。

理由：
- 拦截器只在请求发送前修改 JSON 字体，不影响 Spring AI 内部逻辑
- ThreadLocal 确保每个请求独立，避免并发污染
- 仅修改 `/chat/completions` 请求，不影响 Embedding 等其他调用
- 失败安全：JSON 解析失败时降级为原始请求

---

## 四、落地实现方案

### 4.1 文件结构

```
interview-agent-service/src/main/java/com/interview/agent/service/chat/
├── deepseek/                              ← 新增 DeepSeek 包
│   ├── DeepSeekChatModel.java             ← ChatModel 实现，包装 OpenAiChatModel
│   ├── DeepSeekChatOptions.java           ← ChatOptions 实现，包装 OpenAiChatOptions + thinking
│   ├── DeepSeekThinkingInterceptor.java   ← HTTP 拦截器，注入 thinking 参数
│   └── DeepSeekRestClientConfig.java      ← 自动配置，创建 Bean
├── ChatModelConfig.java                   ← 修改：新增 deepseek 条件 Bean
├── ChatOptionsFactory.java                ← 新增：Provider 无关的 Options 工厂
└── ChatProviderProperties.java            ← 修改：新增 isDeepSeek()/isOllama()

interview-agent-web/src/main/resources/
├── application.yml                        ← 修改：新增 spring.ai.openai.* 配置
└── prompts/*.st                           ← 修改：模板语法从 <> 改为 {}

interview-agent-service/pom.xml            ← 修改：新增 spring-ai-starter-model-openai 依赖
```

### 4.2 核心流程

#### 4.2.1 请求流转全链路

```
用户发送消息 "什么是多态"
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│  ChatController.chatStream(request)                         │
│      │                                                      │
│      ▼                                                      │
│  ChatServiceImpl.chatStream(request)                        │
│      │                                                      │
│      ├─ 1. buildPrompt(request)                             │
│      │      ├─ 读取 interview-system.st 模板                 │
│      │      ├─ template.render({domain:"java", difficulty}) │
│      │      ├─ 加载历史消息                                   │
│      │      └─ new Prompt(messages, chatOptionsFactory      │
│      │              .buildThinkingOptions(thinkingEnabled))  │
│      │                                                      │
│      ├─ 2. chatClient.prompt(prompt).stream().content()     │
│      │      │                                               │
│      │      ▼                                               │
│      │  ChatModel (接口)                                    │
│      │      │                                               │
│      │      ├─ spring.ai.model.chat=ollama                  │
│      │      │   └─ OllamaChatModel → Ollama 本地             │
│      │      │                                               │
│      │      └─ spring.ai.model.chat=deepseek                │
│      │          └─ DeepSeekChatModel.call(prompt)           │
│      │              │                                       │
│      │              ├─ 1. 从 Prompt 中提取 DeepSeekChatOptions│
│      │              ├─ 2. DeepSeekThinkingInterceptor       │
│      │              │      .setThinkingEnabled(true)        │
│      │              ├─ 3. 提取内部 OpenAiChatOptions         │
│      │              ├─ 4. delegate.call(actualPrompt)       │
│      │              │      │                                │
│      │              │      ▼                                │
│      │              │  OpenAiChatModel                      │
│      │              │      │                                │
│      │              │      ▼                                │
│      │              │  HTTP POST https://api.deepseek.com   │
│      │              │      /chat/completions                │
│      │              │      │                                │
│      │              │      ▼  (拦截器修改请求体)              │
│      │              │  DeepSeekThinkingInterceptor          │
│      │              │      │                                │
│      │              │      ├─ THINKING_ENABLED=true?        │
│      │              │      │   └─ 注入 {"thinking":         │
│      │              │      │       {"type":"enabled"}}      │
│      │              │      │                                │
│      │              │      └─ THINKING_ENABLED=false?       │
│      │              │          └─ 不修改，原始请求            │
│      │              │                                       │
│      │              └─ 5. DeepSeekThinkingInterceptor.clear()│
│      │                                                      │
│      └─ 3. Flux<String> 流式返回给前端                        │
└─────────────────────────────────────────────────────────────┘
```

#### 4.2.2 Bean 装配流程

```
Spring Boot 启动
        │
        ▼
┌───────────────────────────────────────────────────┐
│  读取 application.yml                              │
│  spring.ai.model.chat = "deepseek"                │
│  spring.ai.model.embedding = "ollama"             │
│  spring.ai.openai.base-url = "https://api..."    │
│  spring.ai.openai.api-key = "sk-xxx"             │
└───────────────────────┬───────────────────────────┘
                        │
            ┌───────────┴───────────┐
            ▼                       ▼
  ChatModelConfig              OpenAI 自动配置
  @ConditionalOnProperty       @ConditionalOnProperty
  (chat=deepseek)              (chat=openai, matchIfMissing)
            │                       │
            │                  不激活！(chat=deepseek ≠ openai)
            ▼
  DeepSeekRestClientConfig
  @ConditionalOnProperty(chat=deepseek)
            │
            ├─ @Bean deepSeekOpenAiApi()
            │     └─ OpenAiApi.builder()
            │           .baseUrl(@Value("${spring.ai.openai.base-url}"))
            │           .apiKey(@Value("${spring.ai.openai.api-key}"))
            │           .restClientBuilder(带 DeepSeekThinkingInterceptor)
            │           .build()
            │
            ├─ @Bean deepSeekOpenAiChatModel(api)
            │     └─ OpenAiChatModel.builder()
            │           .openAiApi(api)
            │           .defaultOptions(model=deepseek-v4-pro, temp=0.5)
            │           .build()
            │
            └─ @Bean deepSeekChatModel(openAiChatModel)
                  └─ new DeepSeekChatModel(openAiChatModel)

  ChatModelConfig
  @ConditionalOnProperty(chat=deepseek)
  @Primary
            │
            ▼
  ChatModel Bean = DeepSeekChatModel (Primary)
            │
            ▼
  SpringAiConfig
  @Bean ChatClient(ChatModel chatModel)
            │
            ▼
  ChatClient 使用 DeepSeekChatModel
```

### 4.3 关键实现

#### 4.3.1 DeepSeekThinkingInterceptor — HTTP 拦截器注入 thinking 参数

这是整个方案的核心，解决了"Spring AI 不支持 DeepSeek 自定义参数"的问题。

```java
/**
 * 核心设计：
 * 1. ThreadLocal 控制 — 每个请求独立设置 thinking 开关
 * 2. 仅修改 /chat/completions 请求 — 不影响 Embedding 等其他调用
 * 3. 失败安全 — JSON 解析失败时降级为原始请求
 */
public class DeepSeekThinkingInterceptor implements ClientHttpRequestInterceptor {

    // ThreadLocal：每个请求线程独立的 thinking 开关
    private static final ThreadLocal<Boolean> THINKING_ENABLED =
        ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static void setThinkingEnabled(boolean enabled) {
        THINKING_ENABLED.set(enabled);
    }

    public static void clear() {
        THINKING_ENABLED.remove();  // 防止内存泄漏
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                         ClientHttpRequestExecution execution) throws IOException {
        // 条件检查：thinking 未开启 / 无请求体 / 非 chat/completions → 原样放行
        if (!THINKING_ENABLED.get() || body == null || body.length == 0) {
            return execution.execute(request, body);
        }

        String uri = request.getURI().toString();
        if (!uri.contains("/chat/completions")) {
            return execution.execute(request, body);
        }

        try {
            // 修改请求体：注入 {"thinking": {"type": "enabled"}}
            String jsonBody = new String(body, StandardCharsets.UTF_8);
            ObjectNode root = (ObjectNode) objectMapper.readTree(jsonBody);

            ObjectNode thinking = objectMapper.createObjectNode();
            thinking.put("type", "enabled");
            root.set("thinking", thinking);

            byte[] modifiedBody = objectMapper.writeValueAsBytes(root);
            return execution.execute(request, modifiedBody);
        } catch (Exception e) {
            // 失败安全：JSON 解析失败时降级为原始请求
            return execution.execute(request, body);
        }
    }
}
```

**为什么要用 ThreadLocal？**

```
并发场景：
  线程 A: 用户开启了 thinking → setThinkingEnabled(true)
  线程 B: 用户关闭了 thinking → setThinkingEnabled(false)

如果用普通 boolean 变量：
  线程 A 读到 false（被线程 B 覆盖）→ thinking 参数丢失 ❌

用 ThreadLocal：
  线程 A 读到 true（自己的副本）
  线程 B 读到 false（自己的副本）
  互不干扰 ✅
```

#### 4.3.2 DeepSeekChatModel — ChatModel 包装器

```java
/**
 * 核心设计：
 * 1. 实现 ChatModel 接口 — 对外透明，调用方无需感知 Provider 差异
 * 2. 从 Prompt 中提取 DeepSeekChatOptions — 获取 thinking 标志
 * 3. 管理 ThreadLocal 生命周期 — call() 用 try-finally，stream() 用 doFinally()
 */
public class DeepSeekChatModel implements ChatModel {

    private final OpenAiChatModel delegate;  // 底层实际调用者

    @Override
    public ChatResponse call(Prompt prompt) {
        boolean thinking = false;
        Prompt actualPrompt = prompt;

        // 从 Prompt 中提取 DeepSeekChatOptions
        if (prompt.getOptions() instanceof DeepSeekChatOptions dsOptions) {
            thinking = dsOptions.isThinkingEnabled();
            // 提取内部 OpenAiChatOptions，创建新的 Prompt
            actualPrompt = new Prompt(prompt.getInstructions(), dsOptions.getOpenAiOptions());
        }

        try {
            // 设置 ThreadLocal → 拦截器会在发送请求时读取
            DeepSeekThinkingInterceptor.setThinkingEnabled(thinking);
            // 委托给 OpenAiChatModel 执行实际调用
            return delegate.call(actualPrompt);
        } finally {
            // 清理 ThreadLocal，防止内存泄漏
            DeepSeekThinkingInterceptor.clear();
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // ... 类似逻辑，但用 doFinally() 清理 ThreadLocal
        // 因为流式调用是异步的，不能用 try-finally
        return delegate.stream(actualPrompt)
                .doFinally(signal -> DeepSeekThinkingInterceptor.clear());
    }
}
```

**为什么需要包装器？直接用 OpenAiChatModel 不行吗？**

```
直接用 OpenAiChatModel 的问题：

  ChatService → OpenAiChatModel.call(prompt)
                      │
                      └─ prompt.getOptions() 是 OllamaChatOptions 或 DeepSeekChatOptions
                         OpenAiChatModel 不认识 DeepSeekChatOptions
                         → thinking 标志丢失 ❌

用 DeepSeekChatModel 包装后：

  ChatService → DeepSeekChatModel.call(prompt)
                      │
                      ├─ 1. 检测 options 类型，提取 thinking 标志
                      ├─ 2. 设置 ThreadLocal
                      ├─ 3. 转换为 OpenAiChatOptions
                      └─ 4. 委托 OpenAiChatModel.call(actualPrompt)
                              │
                              └─ OpenAiChatModel 只看到标准的 OpenAiChatOptions ✅
                                 thinking 参数由拦截器在 HTTP 层注入 ✅
```

#### 4.3.3 DeepSeekChatOptions — 选项包装器

```java
/**
 * 设计思路：
 * - 实现 ChatOptions 接口，可直接传入 Prompt 构造函数
 * - 内部包装 OpenAiChatOptions，委托所有标准方法
 * - 额外持有 thinkingEnabled 标志
 */
@Getter
public class DeepSeekChatOptions implements ChatOptions {

    private final OpenAiChatOptions openAiOptions;  // 内部标准选项
    private final boolean thinkingEnabled;           // DeepSeek 特有标志

    // 所有标准 ChatOptions 方法委托给 openAiOptions
    @Override
    public String getModel() { return openAiOptions.getModel(); }

    @Override
    public Double getTemperature() { return openAiOptions.getTemperature(); }

    // ... 其他方法同理

    // Builder 模式
    public static class Builder {
        private final OpenAiChatOptions.Builder openAiBuilder = OpenAiChatOptions.builder();
        private boolean thinkingEnabled = false;

        public Builder enableThinking() { this.thinkingEnabled = true; return this; }
        public Builder disableThinking() { this.thinkingEnabled = false; return this; }

        public DeepSeekChatOptions build() {
            return new DeepSeekChatOptions(openAiBuilder.build(), thinkingEnabled);
        }
    }
}
```

#### 4.3.4 ChatOptionsFactory — Provider 无关的选项工厂

```java
/**
 * 设计目的：消除 6 个 Service 文件中对 OllamaChatOptions 的硬编码依赖
 *
 * 之前（每个 Service 都写）：
 *   OllamaChatOptions.Builder builder = OllamaChatOptions.builder();
 *   if (thinking) builder.enableThinking(); else builder.disableThinking();
 *   return new Prompt(messages, builder.build());
 *
 * 之后（统一工厂）：
 *   return new Prompt(messages, chatOptionsFactory.buildThinkingOptions(thinking));
 */
@Component
public class ChatOptionsFactory {

    private final ChatProviderProperties properties;

    public ChatOptions buildThinkingOptions(boolean thinkingEnabled) {
        if (properties.isDeepSeek()) {
            DeepSeekChatOptions.Builder builder = DeepSeekChatOptions.builder();
            return thinkingEnabled ? builder.enableThinking().build()
                                  : builder.disableThinking().build();
        }
        // 默认 Ollama
        OllamaChatOptions.Builder builder = OllamaChatOptions.builder();
        return thinkingEnabled ? builder.enableThinking().build()
                              : builder.disableThinking().build();
    }

    public ChatOptions buildJudgeOptions(String model, double temperature, int numCtx) {
        if (properties.isDeepSeek()) {
            return DeepSeekChatOptions.builder().model(model).temperature(temperature).build();
        }
        return OllamaChatOptions.builder().model(model).temperature(temperature).numCtx(numCtx).build();
    }
}
```

#### 4.3.5 PromptTemplate 模板语法说明

Spring AI 1.1.0 的 `PromptTemplate` 使用 `StTemplateRenderer`（基于 StringTemplate 4 引擎），**默认分隔符是 `{` 和 `}`**（单花括号）。

```java
// StTemplateRenderer.Builder 构造函数（字节码确认）
bipush 123    // startDelimiterToken = '{' (ASCII 123)
putfield startDelimiterToken
bipush 125    // endDelimiterToken = '}' (ASCII 125)
putfield endDelimiterToken
```

**正确语法**：
```
你是一位专业的面试智能助手，专注于{domain}领域。
当前面试领域：{domain}
难度偏好：{difficulty}
```

**常见误区**：
- ❌ `<domain>` — 这是 StringTemplate 4 的默认分隔符，但 Spring AI 覆盖为 `{}`
- ❌ `{{domain}}` — 这是 Mustache/Handlebars 语法，Spring AI 不使用
- ✅ `{domain}` — Spring AI 1.1.0 的正确语法

#### 4.3.6 application.yml 配置

```yaml
spring:
  ai:
    # Provider 切换开关
    model:
      chat: deepseek          # ollama / deepseek
      embedding: ollama       # 始终用 Ollama（bge-m3）
      model-name: deepseek-v4-pro  # 用于日志和会话记录

    # Ollama 配置（本地）
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: qwen3:1.7b
          temperature: 0.5
          num-ctx: 4096
      embedding:
        options:
          model: bge-m3

    # DeepSeek 配置（云端 API，通过 OpenAI 兼容接口）
    openai:
      base-url: https://api.deepseek.com
      api-key: sk-xxx           # 替换为真实 API Key
      chat:
        options:
          model: deepseek-v4-pro
          temperature: 0.5
```

**关键配置说明**：

| 配置项 | 作用 | 注意事项 |
|--------|------|---------|
| `spring.ai.model.chat` | 控制使用哪个 ChatModel | `ollama` / `deepseek` |
| `spring.ai.model.embedding` | 控制使用哪个 EmbeddingModel | 必须显式指定，否则两个 starter 都会加载 |
| `spring.ai.model.model-name` | 日志和会话记录用 | 不影响实际调用的模型 |
| `spring.ai.openai.base-url` | DeepSeek API 地址 | 因 OpenAI 自动配置不加载，由 DeepSeekRestClientConfig 用 @Value 读取 |
| `spring.ai.openai.api-key` | API Key | 明文存储（项目要求） |

---

## 五、集成位置与扩展

### 5.1 代码集成点

| 调用入口 | 文件 | 改动说明 |
|----------|------|---------|
| **普通对话** | `ChatServiceImpl.buildPrompt()` | 通过 `chatOptionsFactory.buildThinkingOptions()` 构建选项 |
| **RAG 对话** | `RagServiceImpl.buildRagPrompt()` | 同上 |
| **查询改写** | `QueryRewriteService.rewrite()` | 使用 `ChatModel` 接口，无 Provider 依赖 |
| **面试题生成** | `QuestionGenerateTool.generateQuestions()` | 使用 `ChatModel` 接口 + `@Lazy` |
| **RAG 评估 Judge** | `LlmJudgeClient.judge()` | 通过 `chatOptionsFactory.buildJudgeOptions()` 构建选项 |
| **评估实验** | `EvalExperimentRunner.generateAnswer()` | 通过 `chatOptionsFactory.buildThinkingOptions(false)` 构建选项 |

### 5.2 效果预期

**收益**：

| 收益 | 说明 |
|------|------|
| 回答质量大幅提升 | deepseek-v4-pro 推理能力远超 1.7b 小模型 |
| 深度思考模式可用 | DeepSeek thinking 模式推理链清晰，面试回答更有深度 |
| 多模型对比能力 | 同一问题可分别用 Ollama 和 DeepSeek 回答，量化效果差距 |
| 成本极低 | DeepSeek API 约 ¥0.5/百万 token，日常使用几乎无成本 |

**副作用**：

| 副作用 | 说明 | 缓解措施 |
|--------|------|---------|
| 网络依赖 | 云端 API 需要网络连接 | 有 Ollama 作为降级方案 |
| 延迟增加 | 网络往返 + 云端推理 | 流式输出掩盖延迟，用户感知不明显 |
| API Key 安全 | 明文存储在 yml | 开发环境可接受，生产环境需加密 |

### 5.3 后续优化方向

| 方向 | 说明 | 优先级 |
|------|------|--------|
| **流式 thinking 输出** | 前端实时显示 DeepSeek 的推理过程（类似 DeepSeek 官网） | 中 |
| **API Key 加密** | 使用 Jasypt 或 Vault 加密存储 API Key | 低 |
| **多模型路由** | 根据问题复杂度自动选择 Provider（简单问题用 Ollama，复杂问题用 DeepSeek） | 低 |
| **Token 用量统计** | 记录每次调用的 input/output tokens，估算成本 | 中 |
| **故障自动降级** | DeepSeek API 不可用时自动切换到 Ollama | 中 |
| **更多 Provider** | 接入通义千问、智谱 GLM 等国产模型 | 低 |

---

## 六、参考资料

### 官方文档

- [Spring AI 1.1.0 官方文档](https://docs.spring.io/spring-ai/reference/)
- [DeepSeek API 官方文档](https://platform.deepseek.com/api-docs)
- [DeepSeek thinking 模式说明](https://platform.deepseek.com/api-docs/zh-cn/thinking)
- [StringTemplate 4 文档](https://github.com/antlr/stringtemplate4/blob/master/doc/cheatsheet.md)

### 源码参考

- [Spring AI OpenAI 自动配置源码](https://github.com/spring-projects/spring-ai/blob/main/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/)
- [Spring AI StTemplateRenderer 源码](https://github.com/spring-projects/spring-ai/blob/main/spring-ai-model/src/main/java/org/springframework/ai/template/st/StTemplateRenderer.java)

### 技术博客

- [Spring AI 多 Provider 切换实践](https://spring.io/blog/spring-ai)
- [DeepSeek API 与 OpenAI 兼容性说明](https://platform.deepseek.com/api-docs/zh-cn/quick-start)
- [HTTP 拦截器模式在 Spring 中的应用](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)

### 项目踩坑记录

- [踩坑记录 3.61](../踩坑记录.md#361-spring-ai-prompttemplate-变量语法--variable-而非-variable-或-variable) — PromptTemplate 变量语法
- [踩坑记录 3.62](../踩坑记录.md#362-openai-自动配置-conditionalonproperty-阻止-bean-加载) — OpenAI 自动配置条件冲突
- [踩坑记录 3.63](../踩坑记录.md#363-embedding-模型冲突--ollama-和-openai-自动配置同时加载) — Embedding 模型冲突
