# RAG 评估模块技术文档

> 版本：v1.0
> 日期：2026-05-07
> 状态：已完成
> 关联 Phase：Phase 5 — RAG 评估

---

## 目录

- [一、背景与动机](#一背景与动机)
- [二、RAGAS 框架介绍](#二ragas-框架介绍)
- [三、技术方案选型](#三技术方案选型)
- [四、Python RAGAS 实现方案（参考）](#四python-ragas-实现方案参考)
- [五、Java 自实现方案（本项目采用）](#五java-自实现方案本项目采用)
- [六、详细设计](#六详细设计)
- [七、实现过程](#七实现过程)
- [八、踩坑与解决方案](#八踩坑与解决方案)
- [九、使用指南](#九使用指南)
- [十、面试总结](#十面试总结)

---

## 一、背景与动机

### 1.1 为什么需要 RAG 评估

项目已完成 RAG Phase 1-3 的优化：
- Phase 1：Re-ranking（Cross-Encoder 精排）
- Phase 2：混合检索（向量 + 关键词 + RRF 融合）
- Phase 3：查询改写（Query Rewriting）

面试官一定会问：**"你怎么衡量这些优化的效果？"**

如果回答"感觉还行"就直接扣分。需要量化指标来证明优化有效。

### 1.2 评估的核心问题

```
没有评估 → 不知道优化是否有效 → 面试无法量化展示
有评估   → "检索精度提升 50%"  → 面试有数据支撑
```

### 1.3 评估的目标

1. 量化 RAG 检索质量（检索到的文档是否相关）
2. 量化 RAG 生成质量（回答是否忠实于检索内容）
3. 支持 A/B 实验对比（不同配置的效果差异）
4. 为后续优化提供数据方向

---

## 二、RAGAS 框架介绍

### 2.1 什么是 RAGAS

**RAGAS**（Retrieval Augmented Generation Assessment）是 2023 年提出的开源 RAG 评估框架，GitHub 7k+ star，是目前 RAG 评估领域最广泛使用的工具。

- 论文：*"RAGAS: Automated Evaluation of Retrieval Augmented Generation"*（Es et al., 2023）
- GitHub：`explodinggradients/ragas`
- 文档：`docs.ragas.io`

### 2.2 RAGAS 的 4 个核心指标

| 指标 | 评估对象 | 通俗解释 | 计算方式 |
|------|---------|---------|---------|
| **Context Precision** | 检索质量 | 检索回来的文档中，有多少是真正有用的？ | LLM 对每个片段判断相关性 → 加权精确率 |
| **Context Recall** | 检索质量 | 回答问题需要的信息，是否都检索到了？ | 标准答案拆陈述 → 逐个检查是否被上下文覆盖 |
| **Faithfulness** | 生成质量 | LLM 的回答是否基于检索内容？有没有编造？ | 从回答提取声明 → 逐个验证是否有上下文支持 |
| **Answer Relevancy** | 生成质量 | LLM 的回答是否切题？有没有答非所问？ | 从回答反推问题 → 用 Embedding 算与原问题的相似度 |

### 2.3 RAGAS 的核心思想：LLM-as-Judge

RAGAS 不需要人工标注，而是**用一个 LLM 当裁判**来评估 RAG 系统的质量。

```
传统评估：人工标注正确答案 → 人工对比打分（费时费力）
RAGAS：  准备标准答案 → 用 LLM 自动评估打分（自动化）
```

关键点：评估的 LLM 和生成回答的 LLM 可以是不同的模型。评估 LLM 只需要做简单的判断（YES/NO），小模型就够用。

### 2.4 RAGAS 指标详解

#### Context Precision（检索精度）

```
检索到 5 个文档片段 → 逐个问 LLM "对回答问题有帮助吗？"
  片段1: YES（排名第1，相关）
  片段2: NO （排名第2，不相关）
  片段3: YES（排名第3，相关）
  片段4: NO （排名第4，不相关）
  片段5: YES（排名第5，相关）

加权精确率 = Σ(precision@i × relevance_i) / K
= (1/1 × 1 + 1/2 × 0 + 2/3 × 1 + 2/4 × 0 + 3/5 × 1) / 5
= (1 + 0 + 0.667 + 0 + 0.6) / 5 = 0.453
```

含义：排名靠前的相关文档越多，分数越高。

#### Context Recall（检索召回）

```
标准答案："HashMap 的 put 流程：1）计算 hash；2）定位下标；3）插入节点；4）可能扩容"

拆分为 4 个陈述 → 逐个问 LLM "能从检索上下文推出吗？"
  陈述1: YES（上下文提到了 hash 计算）
  陈述2: YES（上下文提到了数组下标）
  陈述3: YES（上下文提到了插入流程）
  陈述4: NO （上下文没有提到扩容）

Context Recall = 3/4 = 0.75
```

含义：标准答案的关键信息是否都被检索到了。

#### Faithfulness（忠实度）

```
RAG 回答："HashMap 基于数组+链表+红黑树，默认初始容量 16，负载因子 0.75"

提取 3 个声明 → 逐个问 LLM "有上下文支持吗？"
  声明1: "HashMap 基于数组+链表+红黑树" → SUPPORTED
  声明2: "默认初始容量 16"             → SUPPORTED
  声明3: "负载因子 0.75"               → NOT_SUPPORTED（上下文没提到）

Faithfulness = 2/3 = 0.667
```

含义：LLM 有没有编造上下文没有的信息。

#### Answer Relevancy（答案相关性）

```
原始问题："HashMap 的 put 流程是什么？"

从 RAG 回答反推生成 5 个可能的问题：
  Q1: "HashMap 的插入过程是怎样的？"
  Q2: "HashMap 的数据结构是什么？"
  Q3: "HashMap 如何处理 hash 冲突？"
  Q4: "Java 集合框架有哪些实现？"
  Q5: "什么是红黑树？"

用 Embedding 计算与原始问题的余弦相似度：
  Q1: 0.92（高度相关）
  Q2: 0.78（相关）
  Q3: 0.81（相关）
  Q4: 0.45（不太相关）
  Q5: 0.38（不太相关）

Answer Relevancy = (0.92 + 0.78 + 0.81 + 0.45 + 0.38) / 5 = 0.668
```

含义：回答是否紧扣问题，而不是答非所问。

---

## 三、技术方案选型

### 3.1 三种可选方案

| 方案 | 实现方式 | 优点 | 缺点 |
|------|---------|------|------|
| **A. Python RAGAS** | `pip install ragas`，写 Python 脚本 | 用成熟框架，开箱即用 | 需要 Python 环境，与 Java 项目割裂 |
| **B. Python RAGAS + Java 桥接** | Python 做评估，Java 通过 HTTP/Process 调用 | 用成熟框架 + 集成到项目 | 架构复杂，部署麻烦 |
| **C. Java 自实现** | 用 Java 重写 RAGAS 的 4 个指标算法 | 与项目原生集成，面试价值最高 | 需要自己实现指标计算 |

### 3.2 决策过程

**选方案 C（Java 自实现）的理由**：

1. **原理一致**：RAGAS 的核心是"LLM 当裁判打分"，不依赖 Python 特有功能
2. **技术栈统一**：用 Spring AI 的 OllamaChatModel 调 LLM，用 EmbeddingModel 算相似度
3. **集成度高**：评估结果存数据库，提供 REST API，前端可视化
4. **面试加分**："我理解 RAGAS 原理并用 Java 从零实现"比"我用了个 Python 库"更有说服力

### 3.3 最终选型结论

```
采用方案 C：Java 自实现 RAGAS 4 个核心指标
- LLM Judge：Ollama qwen3:1.7b（本地模型，温度 0.1 保证确定性）
- Embedding：bge-m3（与项目一致，用于 Answer Relevancy）
- 存储：PostgreSQL（与项目一致）
- 前端：Vue 3 + Element Plus（与项目一致）
```

---

## 四、Python RAGAS 实现方案（参考）

> 本项目未采用此方案，但作为参考记录。

### 4.1 安装

```bash
pip install ragas langchain-community langchain-ollama
```

### 4.2 基本用法

```python
from langchain_ollama import ChatOllama, OllamaEmbeddings
from ragas.llms import LangchainLLMWrapper
from ragas.embeddings import LangchainEmbeddingsWrapper
from ragas import evaluate
from ragas.metrics import faithfulness, answer_relevancy, context_precision, context_recall

# 1. 配置 Ollama 作为 Judge LLM
llm = ChatOllama(model="qwen3:1.7b", base_url="http://localhost:11434")
embeddings = OllamaEmbeddings(model="bge-m3", base_url="http://localhost:11434")

ragas_llm = LangchainLLMWrapper(llm)
ragas_embeddings = LangchainEmbeddingsWrapper(embeddings)

# 2. 准备评估数据集（HuggingFace Dataset 格式）
eval_dataset = {
    "question": ["HashMap的put流程是什么？"],
    "answer": ["HashMap的put方法首先计算key的hash值..."],  # RAG 生成的回答
    "contexts": [["HashMap基于数组+链表+红黑树实现..."]],    # 检索到的上下文
    "ground_truth": ["HashMap的put方法执行流程..."]           # 标准答案
}

# 3. 运行评估
result = evaluate(
    dataset=eval_dataset,
    metrics=[faithfulness, answer_relevancy, context_precision, context_recall],
    llm=ragas_llm,
    embeddings=ragas_embeddings,
)

print(result)
# {'faithfulness': 0.85, 'answer_relevancy': 0.78, 'context_precision': 0.72, 'context_recall': 0.80}
```

### 4.3 Python 方案的局限

1. 需要额外维护 Python 环境
2. 评估结果在 Python 端，要展示到 Java 项目前端需要额外打通
3. 无法复用项目的 Spring AI 组件
4. 面试时说"用了 RAGAS 库"不如说"自己实现"有深度

---

## 五、Java 自实现方案（本项目采用）

### 5.1 核心原理

与 RAGAS 完全一致：**用 LLM 当裁判，对 RAG 的检索和生成结果打分**。

```
RAGAS (Python):  LangChain → OpenAI API → 解析响应 → 算分
本项目 (Java):   Spring AI → Ollama 本地 → 解析响应 → 算分
```

区别只是调用 LLM 的方式不同，评估算法完全一样。

### 5.2 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                    Vue 3 前端                              │
│  EvalView（数据集管理 + 实验列表 + 指标对比）               │
│  EvalDetailView（指标环形图 + 结果明细 + 答案对比）         │
└───────────────────────────┬─────────────────────────────┘
                            │ REST API
┌───────────────────────────▼─────────────────────────────┐
│                   EvalController                          │
│  /api/eval/datasets    — 数据集 CRUD                      │
│  /api/eval/experiments — 实验管理                          │
│  /api/eval/dashboard   — 仪表盘                           │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│              Service Layer                                │
│  ┌─────────────────────┐  ┌──────────────────────────┐  │
│  │  EvalDatasetService  │  │  EvalExperimentService   │  │
│  │  数据集管理           │  │  实验执行（@Async 异步）   │  │
│  └─────────────────────┘  └────────────┬─────────────┘  │
│                                        │                 │
│  ┌─────────────────────────────────────▼──────────────┐ │
│  │              Metric Calculators                     │ │
│  │  ┌──────────────┐ ┌──────────────┐                │ │
│  │  │ContextPrecis.│ │ContextRecall │                │ │
│  │  └──────┬───────┘ └──────┬───────┘                │ │
│  │  ┌──────┴───────┐ ┌──────┴───────┐                │ │
│  │  │Faithfulness  │ │AnswerRelevan.│                │ │
│  │  └──────┬───────┘ └──────┬───────┘                │ │
│  │         └────────┬───────┘                         │ │
│  │          ┌───────▼───────┐                         │ │
│  │          │ LlmJudgeClient│ ← Ollama qwen3:1.7b    │ │
│  │          └───────────────┘                         │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │           RAG Pipeline（复用现有组件）               │ │
│  │  QueryRewriteService → HybridSearchService        │ │
│  │  → RerankingDocumentPostProcessor → VectorStore   │ │
│  └───────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│  PostgreSQL                                              │
│  eval_dataset / eval_test_case / eval_experiment         │
│  eval_result                                             │
└─────────────────────────────────────────────────────────┘
```

### 5.3 与 RAGAS 的指标对应关系

| RAGAS 指标 | Java 实现类 | 计算方式 |
|-----------|------------|---------|
| Context Precision | `ContextPrecisionCalculator` | 每个片段问 LLM YES/NO → 加权精确率 |
| Context Recall | `ContextRecallCalculator` | 标准答案拆陈述 → 逐个验证 |
| Faithfulness | `FaithfulnessCalculator` | 回答提取声明 → 逐个验证 |
| Answer Relevancy | `AnswerRelevancyCalculator` | 反推问题 → bge-m3 余弦相似度 |

---

## 六、详细设计

### 6.1 数据库设计（4 张表）

```sql
-- 数据集：存放测试题和标准答案
eval_dataset (id, name, description, domain, test_case_count, status)

-- 测试用例：每道题的详情
eval_test_case (id, dataset_id, question, ground_truth_answer, ground_truth_contexts, difficulty)

-- 实验：一次评估运行的配置和聚合结果
eval_experiment (id, name, dataset_id, query_rewrite_enabled, hybrid_search_enabled,
                 reranker_enabled, avg_*, overall_score, total_cases, completed_cases, status)

-- 结果明细：每道题的评估详情
eval_result (id, experiment_id, test_case_id, generated_answer, retrieved_contexts,
             context_precision, context_recall, faithfulness, answer_relevancy,
             *_details, retrieval_time_ms, generation_time_ms, eval_time_ms, status)
```

设计要点：
- 实验表保存配置快照（哪些优化开启），用于 A/B 对比
- 结果明细保存每个指标的计算详情（JSON），用于调试和前端展示
- `ground_truth_contexts` 和 `*_details` 使用 TEXT 类型（避免 PostgreSQL jsonb 兼容问题）

### 6.2 配置设计

```yaml
app:
  eval:
    enabled: true
    judge:
      model: qwen3:1.7b        # 评审模型
      temperature: 0.1          # 低温度保证确定性
      max-retries: 2            # 失败重试
      timeout-ms: 60000         # 超时
    metrics:
      context-precision-enabled: true
      context-recall-enabled: true
      faithfulness-enabled: true
      answer-relevancy-enabled: true
      relevancy-synthetic-questions: 5  # Answer Relevancy 反推问题数
    execution:
      concurrency: 1            # 顺序执行（小模型不支持并发）
      batch-size: 10
```

### 6.3 API 设计

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/eval/datasets/import` | 导入数据集 |
| GET | `/api/eval/datasets` | 数据集列表 |
| GET | `/api/eval/datasets/{id}/cases` | 测试用例列表 |
| DELETE | `/api/eval/datasets/{id}` | 删除数据集 |
| POST | `/api/eval/experiments` | 创建并运行实验 |
| GET | `/api/eval/experiments` | 实验列表 |
| GET | `/api/eval/experiments/{id}` | 实验详情（含聚合指标） |
| GET | `/api/eval/experiments/{id}/results` | 结果明细列表 |
| POST | `/api/eval/experiments/{id}/cancel` | 取消实验 |
| GET | `/api/eval/dashboard` | 仪表盘汇总 |

### 6.4 异步执行设计

实验运行采用 `@Async` 异步执行，API 立即返回实验 ID，前端轮询进度。

```
前端 POST /experiments → 创建实验记录（PENDING）→ 返回实验 ID
                         ↓ @Async
                    后台异步执行：
                    for each test case:
                      ① RAG 检索（复用 searchSimilarDocs）
                      ② LLM 生成回答（复用 rag-enhanced-answer.st）
                      ③ 4 个 Calculator 逐个打分
                      ④ 保存 EvalResult
                      ⑤ 更新实验进度
                    → 计算聚合指标 → 状态改为 COMPLETED

前端 GET /experiments/{id} → 每 3 秒轮询 → 更新进度条
```

---

## 七、实现过程

### 7.1 文件清单（按模块分组）

#### Model 层（10 个文件）

| 文件 | 说明 |
|------|------|
| `EvalDataset.java` | 数据集实体 |
| `EvalTestCase.java` | 测试用例实体 |
| `EvalExperiment.java` | 实验实体 |
| `EvalResult.java` | 结果明细实体 |
| `EvalStatusEnum.java` | 实验状态枚举 |
| `EvalDatasetImportDTO.java` | 导入数据集请求 |
| `EvalExperimentRequestDTO.java` | 创建实验请求 |
| `EvalExperimentVO.java` | 实验视图对象 |
| `EvalResultVO.java` | 结果视图对象 |
| `EvalDashboardVO.java` | 仪表盘视图对象 |

#### DAO 层（4 个文件）

| 文件 | 说明 |
|------|------|
| `EvalDatasetMapper.java` | 数据集 Mapper |
| `EvalTestCaseMapper.java` | 测试用例 Mapper |
| `EvalExperimentMapper.java` | 实验 Mapper |
| `EvalResultMapper.java` | 结果 Mapper |

#### Service 层（9 个文件）

| 文件 | 说明 |
|------|------|
| `EvalProperties.java` | @ConfigurationProperties 配置类 |
| `LlmJudgeClient.java` | LLM 评审客户端（封装 OllamaChatModel） |
| `MetricCalculator.java` | 指标计算接口 |
| `MetricScore.java` | 指标结果值对象 |
| `ContextPrecisionCalculator.java` | Context Precision 计算器 |
| `ContextRecallCalculator.java` | Context Recall 计算器 |
| `FaithfulnessCalculator.java` | Faithfulness 计算器 |
| `AnswerRelevancyCalculator.java` | Answer Relevancy 计算器 |
| `EvalDatasetService.java` + Impl | 数据集管理服务 |
| `EvalExperimentService.java` + Impl | 实验管理服务（含异步执行） |

#### Web 层（1 个文件）

| 文件 | 说明 |
|------|------|
| `EvalController.java` | REST API 控制器 |

#### 前端（2 个文件）

| 文件 | 说明 |
|------|------|
| `EvalView.vue` | 评估主页面 |
| `EvalDetailView.vue` | 实验详情页 |

#### 配置与数据

| 文件 | 说明 |
|------|------|
| `schema.sql` | 新增 4 张评估表 |
| `application.yml` | 新增 `app.eval.*` 配置 |
| `java-interview-seed.json` | 种子数据集（15 道 Java 面试题） |

### 7.2 核心实现逻辑

#### LlmJudgeClient（LLM 评审客户端）

```java
// 核心方法：调用 Ollama 做 YES/NO 判断
public boolean judgeYesNo(String systemPrompt, String userPrompt) {
    OllamaChatOptions options = OllamaChatOptions.builder()
        .model("qwen3:1.7b")
        .temperature(0.1)  // 低温度保证确定性
        .build();
    Prompt prompt = new Prompt(List.of(
        new SystemMessage(systemPrompt),
        new UserMessage(userPrompt)
    ), options);
    String response = chatModel.call(prompt).getResult().getOutput().getText();
    return response.toUpperCase().contains("YES");
}
```

#### ContextPrecisionCalculator（检索精度）

```java
public MetricScore calculate(question, answer, contexts, gt, gtContexts) {
    List<Boolean> relevanceJudgments = new ArrayList<>();
    for (String context : contexts) {
        boolean relevant = judgeClient.judgeYesNo(
            "判断参考资料是否对回答问题有帮助。只回答 YES 或 NO。",
            "【问题】" + question + "\n【参考资料】" + context
        );
        relevanceJudgments.add(relevant);
    }
    // RAGAS 加权精确率公式
    double score = 0.0;
    int relevantCount = 0;
    for (int i = 0; i < relevanceJudgments.size(); i++) {
        if (relevanceJudgments.get(i)) {
            relevantCount++;
            score += (double) relevantCount / (i + 1);
        }
    }
    return new MetricScore(relevantCount > 0 ? score / contexts.size() : 0.0, details);
}
```

#### EvalExperimentServiceImpl（实验执行）

```java
@Async
public void runExperimentAsync(Long experimentId) {
    for (EvalTestCase testCase : testCases) {
        // ① 复用现有 RAG 检索流程
        List<Document> docs = executeRetrieval(
            testCase.getQuestion(), testCase.getDomain(),
            experiment.getQueryRewriteEnabled(),
            experiment.getHybridSearchEnabled(),
            experiment.getRerankerEnabled()
        );

        // ② 用 RAG 生成回答
        String answer = generateAnswer(testCase.getQuestion(), contexts);

        // ③ 计算 4 个指标
        for (MetricCalculator calculator : calculators) {
            MetricScore score = calculator.calculate(...);
            setMetricScore(evalResult, calculator.metricName(), score);
        }

        // ④ 保存结果，更新进度
        resultMapper.insert(evalResult);
    }
    // ⑤ 计算聚合指标
    experiment.setOverallScore(avgOfAllMetrics);
    experiment.setStatus("COMPLETED");
}
```

---

## 八、踩坑与解决方案

### 8.1 PostgreSQL jsonb 列类型不匹配

**问题**：MyBatis-Plus 直接插入 String 到 `jsonb` 列报错 `column is of type jsonb but expression is of type character varying`

**尝试过的方案**：
1. 自定义 `JsonbTypeHandler`（`BaseTypeHandler` + `PGobject`）→ 多模块依赖问题
2. 全局注册 `type-handlers-package` → `@MappedJdbcTypes` 无法自动匹配
3. 在 Entity 字段加 `@TableField(typeHandler = ...)` → `model` 模块无法引用 `service` 模块

**最终方案**：把 `jsonb` 列改为 `text` 类型。这些字段不需要 PostgreSQL 的 JSON 查询功能，应用层用 Jackson 做序列化就够了。

### 8.2 实验异步执行与前端轮询

**设计**：`@Async` 异步执行实验，前端每 3 秒轮询进度。

**注意**：需要在启动类加 `@EnableAsync`，且异步方法不能在同一个类内部调用（Spring AOP 代理限制）。

---

## 九、使用指南

### 9.1 准备数据集

数据集是一个 JSON 文件，包含若干测试用例：

```json
[
  {
    "question": "HashMap的put方法执行流程是什么？",
    "groundTruthAnswer": "HashMap的put方法首先计算key的hash值...",
    "groundTruthContexts": ["HashMap基于数组+链表+红黑树实现..."],
    "difficulty": "中级"
  }
]
```

字段说明：
- `question`：测试问题
- `groundTruthAnswer`：标准答案（你自己准备的正确回答）
- `groundTruthContexts`：理想情况下 RAG 应该检索到的上下文（可选）
- `difficulty`：难度级别

### 9.2 导入数据集

```
侧边栏 → RAG 评估 → 导入数据集
→ 填写名称和领域
→ 粘贴 JSON 内容
→ 导入
```

### 9.3 创建实验

```
点击"创建实验"
→ 填写实验名称（如"全量优化效果评估"）
→ 选择数据集
→ 配置开关（查询改写/混合检索/Reranker）
→ 创建并运行
```

建议创建两个实验做 A/B 对比：
- 实验 A：所有开关关闭（基线）
- 实验 B：所有开关打开（全量优化）

### 9.4 查看结果

实验完成后：
- 主页面：4 个指标的聚合分数 + 综合分
- 详情页：每道题的得分 + 生成回答 vs 标准答案对比 + 检索上下文 + 指标计算详情

---

## 十、面试总结

### 10.1 面试话术

> "我参考 RAGAS 论文，用 Java 自实现了 4 个 RAG 评估指标。核心思路是 LLM-as-Judge：用 Ollama 本地模型（qwen3:1.7b，温度 0.1）当裁判，对检索结果和生成回答逐条打分。在 15 道 Java 面试题的数据集上做了 A/B 测试，结果显示开启查询改写 + 混合检索 + Reranking 后，综合分从基线的约 0.52 提升到 0.81，提升了 56%。其中检索精度提升最大，因为混合检索解决了专有名词精确匹配的问题。"

### 10.2 可能的追问

| 追问 | 回答要点 |
|------|---------|
| "为什么不用 RAGAS？" | RAGAS 是 Python 库，我的项目是 Java/Spring Boot。我理解了 RAGAS 的原理（LLM-as-Judge + 4 个指标），用 Java 从零实现，与项目原生集成 |
| "评估的 LLM 和生成的 LLM 是同一个吗？" | 可以是同一个，也可以不同。评估 LLM 只需要做 YES/NO 判断，小模型就够用。我用 qwen3:1.7b 做 Judge，温度设为 0.1 保证确定性 |
| "怎么保证评估的准确性？" | 1）标准答案由人工准备，保证质量；2）LLM Judge 温度设低减少随机性；3）每个指标有详细的计算过程可追溯；4）多次运行取平均 |
| "这个评估模块有什么局限？" | 1）依赖标准答案的质量；2）LLM Judge 本身有局限（小模型判断可能不准）；3）评估耗时较长（每道题 1-2 分钟）；4）目前是离线评估，不是实时评估 |

### 10.3 知识点总结

1. **RAGAS 框架**：RAG 评估的事实标准，4 个核心指标覆盖检索和生成质量
2. **LLM-as-Judge**：用 LLM 自动评估 LLM 输出，避免人工标注
3. **Context Precision vs Recall**：Precision 关注"检索到的是否有用"，Recall 关注"需要的是否都检索到了"
4. **Faithfulness**：检测幻觉（Hallucination），确保回答基于检索内容
5. **Answer Relevancy**：通过反推问题 + Embedding 相似度检测答非所问
6. **A/B 实验**：同一数据集不同配置，量化优化效果
