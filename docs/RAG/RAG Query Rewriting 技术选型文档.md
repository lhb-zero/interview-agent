# RAG Query Rewriting 技术选型文档

> 版本：v1.0
> 日期：2026-04-29
> 用途：记录 Query Rewriting 技术选型过程和实现方案
> 项目：面试智能助手（Spring AI + Ollama + PGVector + Vue3）

---

## 一、为什么需要 Query Rewriting

### 1.1 问题背景

RAG 系统的核心链路是：`用户问题 → 向量检索 → LLM 生成回答`。

但现实场景中，用户提问的质量往往参差不齐：

| 问题类型 | 示例 | 检索效果 |
|---------|------|---------|
| 口语化问题 | "线程池是啥"、"咋解决" | ❌ 口语无法精确匹配专业术语 |
| 过于简短 | "死锁"、"AQS" | ❌ 语义信息不足，向量表达模糊 |
| 包含指代 | "它的线程安全吗"、"上面说的" | ❌ 指代对象不明，检索失焦 |
| 技术缩写 | "JVM"、"IOC" | ❌ 需要展开才能精确匹配 |

这些问题会导致：
- **召回率低**：相关文档根本没被检索到
- **检索噪声**：不相关文档排在前面，稀释上下文质量
- **回答质量差**：LLM 基于错误的上下文给出不准确回答

### 1.2 Query Rewriting 的作用

Query Rewriting 的核心思路：**在向量检索之前，先把用户问题"翻译"成更适合检索的形式**。

```
用户原始问题 → [Query Rewriting] → 改写后的问题 → 向量检索 → 更精准的召回
```

效果示例：

| 原始问题 | 改写后 |
|---------|--------|
| "怎么解决死锁？" | "Java多线程死锁的原因、检测方法和避免策略" |
| "线程池是啥" | "Java线程池ThreadPoolExecutor的原理、参数配置和使用方法" |
| "ReentrantLock咋用" | "ReentrantLock公平锁非公平锁的使用方法和示例" |
| "HashMap的put方法" | "HashMap的put方法"（本身清晰，不改写）|

---

## 二、业界方案调研

### 2.1 方案全景图

```
用户原始问题
     │
     ▼
┌────────────────────────────────────────────────────────────┐
│                    Query Rewriting 策略                      │
├──────────────┬──────────────┬──────────────┬───────────────┤
│  提示词改写   │    HyDE      │  查询分解     │   回退检索     │
│  Rewrite     │  Hypothetical│  Decompose   │  Step-back    │
│              │  Document    │              │  Prompting    │
├──────────────┼──────────────┼──────────────┼───────────────┤
│  ⭐ 最简单   │  ⭐⭐ 中等   │  ⭐⭐⭐ 复杂 │  ⭐⭐ 中等    │
└──────────────┴──────────────┴──────────────┴───────────────┘
```

### 2.2 各方案详解

#### 方案 1：Prompt Rewrite（提示词改写）✅ 选用

**原理**：调用一次 LLM，把用户问题改写成更适合检索的表述。

```
"怎么解决死锁？" → LLM → "Java多线程死锁的原因、检测方法和避免策略"
```

**代表产品**：
- **Dify**：`rewrite-angular` 策略，专门处理指代消解问题
- **LangChain**：`ConversationalRetrievalChain` 的 `rephrase_question` 模式
- **LlamaIndex**：`SimpleQueryTransform`

**优点**：
- 实现最简单，1 个 Service + 1 个 Prompt 模板搞定
- 效果可控，可随时调整 Prompt 优化改写质量
- 零新依赖

**缺点**：
- 多一次 LLM 调用（约 200-500ms）
- 小模型（qwen3:1.7b）改写质量有限，可能改坏

---

#### 方案 2：HyDE（Hypothetical Document Embeddings）

**原理**：让 LLM 生成一个"假答案"，然后用这个假答案去检索。核心假设是：假答案的向量表达和真实文档的向量表达更接近。

```
用户问题 Q → LLM 生成假答案 D* → D* 向量化 → 向量检索 → 找到真实文档
```

**出处**：论文 [Precise Zero-Shot Dense Retrieval without Relevance Labels](https://arxiv.org/abs/2212.10496)（2022）

**代表产品**：
- LangChain：`HyDEChain`
- LlamaIndex：`HyDEQueryTransform`
- Perplexity：部分场景使用

**优点**：
- 改写效果比直接 Rewrite 更好，因为检索的是"答案风格"的文本

**缺点**：
- **多两次 LLM 调用**（一次生成假答案 + 一次最终回答）
- 如果假答案本身有幻觉/错误，会误导检索方向

---

#### 方案 3：Query Decomposition（查询分解）

**原理**：把复杂问题拆成多个简单子问题，分别检索，再合并结果。

```
"解释Spring Bean生命周期并说明@PostConstruct区别" → 拆解
  子问题1: Spring Bean生命周期
  子问题2: @PostConstruct注解
  分别检索 → 合并答案
```

**代表产品**：LlamaIndex `SubQuestionQueryEngine`

**结论**：适合多跳问题，面试助手场景不需要，**实现成本过高**。

---

#### 方案 4：Step-back Prompting（回退提示）

**原理**：先抽象出一个更高层次的问题去检索，找到更全面的上下文后，再回答原问题。

```
"为什么ThreadPoolExecutor用位运算计算ctl？" → 回退抽象
"ThreadPoolExecutor ctl变量设计原理" → 检索 → 回答原问题
```

**出处**：论文 [Step-back Prompting](https://arxiv.org/abs/2310.06117)（Google DeepMind, 2023）

**结论**：对"问得太细"的场景效果好，但实现比 Prompt Rewrite 复杂。

---

#### 方案 5：Query Expansion（同义词/关键词扩展）

**原理**：不用 LLM，用规则/同义词词表扩展查询关键词。

```
"线程池" → ["线程池", "ThreadPoolExecutor", "corePoolSize", ...]
```

**结论**：你的 **Phase 2 混合检索**（向量+关键词+RRF）已经部分实现了这个思路。

---

### 2.3 三大平台实现对比

| 维度 | Dify | RAGFlow | LlamaIndex |
|------|------|---------|------------|
| **Query Rewriting 策略** | rewrite-angular + query-background | 意图识别 + 文档知识联动 | HyDE + SubQuestion + Router |
| **是否需要改写** | 通过检测指代词决定 | 通过意图分类决定 | Router 自动选择 |
| **改写触发条件** | 问题含指代词（它/这个）| 意图为 description/comparison | 根据问题复杂度自动判断 |
| **多跳问题处理** | ❌ 不支持 | ❌ 不支持 | ✅ SubQuestionQueryEngine |
| **对话历史利用** | ✅ 用于指代消解 | ✅ 用于意图识别 | ✅ 可选 |
| **实现复杂度** | ⭐⭐ 中等 | ⭐⭐⭐⭐ 复杂 | ⭐⭐⭐ 中等偏高 |

### 2.4 核心启示

**1. 业界主流不是"要不要改写"，而是"什么时候改写"**

共识：
- **事实性/技术术语明确的问题** → 不改写，直接检索
- **口语化/简短/有指代的问题** → 改写

**2. 改写的核心价值是"指代消解 + 术语扩展"**

主要解决的问题：
- `"它的线程安全吗"` → `"ReentrantLock的线程安全性"`
- `"线程池是啥"` → `"Java线程池ThreadPoolExecutor原理"`
- `"corePoolSize啥意思"` → `"ThreadPoolExecutor中corePoolSize参数的作用"`

---

## 三、最终选型：Prompt Rewrite + 智能触发

### 3.1 为什么选 Prompt Rewrite

| 维度 | Prompt Rewrite | HyDE | Query Decomposition |
|------|---------------|------|---------------------|
| 额外LLM调用 | 1次 | 2次 | N次 |
| 延迟增加 | ~200-500ms | ~500-1000ms | 显著增加 |
| 效果提升 | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| 实现成本 | ⭐ | ⭐⭐ | ⭐⭐⭐⭐ |
| 1.7b模型适配 | ✅ 效果一般但可用 | ❌ 易生成错误假答案 | ❌ 不适合 |

**结论**：对于 1.7b 小模型，Prompt Rewrite 是最稳妥的选择：
- 只改写"需要改写"的问题，减少不必要的 LLM 调用
- 不引入额外幻觉风险（只改写查询，不生成答案）
- 实现成本最低，随时可开关降级

### 3.2 智能触发策略

参考 Dify 的思路，**不是所有问题都需要改写**：

```
RagServiceImpl.searchSimilarDocs()
     │
     ├─ 问题 < 10字 或 含口语化词汇 或 含指代词
     │    → Prompt Rewrite（展开缩写 + 口语→技术化）
     │
     └─ 问题本身清晰完整
          → 不改写，直接检索（省一次 LLM 调用）
```

**触发条件**：

| 条件 | 示例 | 是否改写 |
|------|------|---------|
| 问题过短（<10字） | "AQS"、"死锁" | ✅ 改写 |
| 口语化词汇 | "啥意思"、"咋用"、"怎么调" | ✅ 改写 |
| 指代词 | "它"、"上面说的"、"那个" | ✅ 改写 |
| 技术术语完整 | "HashMap的put方法" | ❌ 不改写 |

---

## 四、实现方案

### 4.1 文件结构

```
interview-agent-service/src/main/java/com/interview/agent/service/rag/rewrite/
├── QueryRewriteService.java       # 查询改写核心服务
└── QueryRewriteProperties.java     # 配置属性类

interview-agent-web/src/main/resources/prompts/
└── query-rewrite.st               # 改写提示词模板
```

### 4.2 核心流程

```
用户提问 → analyzeIfRewriteNeeded() 判断是否需要改写
           ├── 需要 → rewrite() 调用 LLM 改写 → 返回改写后 query
           └── 不需要 → 直接返回原始 query
                │
                ▼
           searchSimilarDocs() 用改写后的 query 检索
```

### 4.3 提示词设计

提示词核心设计原则（Dify 风格）：
1. **展开缩写**：线程池 → Java线程池ThreadPoolExecutor
2. **口语→技术化**：啥意思 → 是什么意思/作用
3. **保持简洁**：不超过 40 字
4. **清晰问题不改写**：本身清晰则原样输出

### 4.4 配置项

```yaml
app:
  query-rewrite:
    enabled: true                    # 是否启用
    min-query-length: 10            # 问题过短阈值
    timeout-ms: 30000               # 改写超时
    need-rewrite-when-short: true   # 问题过短时触发
    need-rewrite-when-colloquial: true  # 口语化时触发
    need-rewrite-when-has-pronoun: true # 指代词时触发
```

---

## 五、集成位置

### 5.1 直接 RAG 模式

**RagServiceImpl.searchSimilarDocs()**：

```java
private List<Document> searchSimilarDocs(String query, String domain) {
    String rewrittenQuery = queryRewriteService.rewriteIfNeeded(query);
    String searchQuery = rewrittenQuery;

    // 后续检索使用改写后的 searchQuery
    ...
}
```

### 5.2 Tool Calling 模式

**KnowledgeSearchTool.searchKnowledge()**：

```java
public String searchKnowledge(String query, String domain, int topK) {
    String rewrittenQuery = queryRewriteService.rewriteIfNeeded(query);
    String searchQuery = rewrittenQuery;

    // 后续检索使用改写后的 searchQuery
    ...
}
```

两处都集成，保证两种 RAG 模式都能享受查询改写的能力。

---

## 六、效果预期

### 6.1 预期改善的场景

| 场景 | 原始问题 | 改写后 | 预期效果 |
|------|---------|--------|---------|
| 口语化 | "线程池是啥" | "Java线程池ThreadPoolExecutor的原理和使用方法" | 召回率 ↑ |
| 过短 | "AQS" | "AbstractQueuedSynchronizer AQS同步器原理" | 召回率 ↑ |
| 缩写 | "JVM调优" | "JVM性能调优方法和参数配置" | 召回率 ↑ |
| 清晰问题 | "HashMap和Hashtable的区别" | 不改写 | 无额外延迟 |

### 6.2 延迟影响

- Query Rewrite 额外调用：~200-500ms（qwen3:1.7b）
- 仅在触发条件满足时调用，非所有问题都调用
- 整体 RAG 链路延迟增加约 10-20%

---

## 七、扩展方向

### 7.1 可选的进阶优化

| 优化项 | 说明 | 实现成本 |
|--------|------|---------|
| Step-back Prompting | 技术深度问题先抽象再精准检索 | ⭐⭐⭐ |
| 对话历史指代消解 | 结合上下文补全"它"、"上面"等指代 | ⭐⭐⭐ |
| HyDE（谨慎使用） | 生成假答案辅助检索，适用于模型较强时 | ⭐⭐ |

### 7.2 监控指标

建议后续监控以下指标来评估 Query Rewrite 效果：

- **改写触发率**：触发改写的问题占比（过高说明 Prompt 需要调整）
- **改写后召回率**：改写后检索命中率对比原始问题
- **端到端回答质量**：用户反馈或自动化评估

---

## 八、参考资料

1. [Dify RAG 模块设计](https://docs.dify.ai/)
2. [RAGFlow 架构设计](https://github.com/infiniflow/ragflow)
3. [LlamaIndex Query Transformations](https://docs.llamaindex.ai/en/stable/examples/query_transformations/)
4. [HyDE 论文](https://arxiv.org/abs/2212.10496)
5. [Step-back Prompting 论文](https://arxiv.org/abs/2310.06117)
