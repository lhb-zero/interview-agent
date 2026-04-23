# RAG 检索增强生成 — 实现与优化指南

> 版本：v1.0  
> 日期：2026-04-23  
> 用途：RAG 技术学习 + 项目实践记录

---

## 一、RAG 核心概念

### 1.1 什么是 RAG

**RAG = Retrieval-Augmented Generation（检索增强生成）**

核心思路：让 LLM "开卷考试"——先从知识库检索相关内容，再基于检索结果生成回答。

```
传统 LLM：用户提问 → LLM 直接回答（可能幻觉）
RAG：     用户提问 → 检索知识库 → 注入上下文 → LLM 基于真实资料回答
```

### 1.2 为什么需要 RAG

| LLM 固有缺陷 | RAG 如何解决 |
|-------------|------------|
| 知识截止（训练数据有截止日期） | 知识库可实时更新 |
| 幻觉（编造不存在的信息） | 检索结果做锚点，回答有依据 |
| 缺少私有知识 | 导入企业/个人文档 |
| 不可溯源 | 回答可追溯至具体文档 |

### 1.3 RAG vs 微调

| 对比 | RAG | 微调 |
|------|-----|------|
| 知识更新 | 实时，改文档即可 | 需重新训练 |
| 成本 | 低（向量数据库） | 高（GPU + 训练数据） |
| 幻觉控制 | 好（有检索锚点） | 一般 |
| 可解释性 | 高（可溯源） | 低 |
| 适用场景 | 知识密集型问答 | 风格/格式适配 |

---

## 二、RAG 全链路流程（本项目实现）

### 2.1 离线阶段：文档导入

```
用户上传文档 (PDF/MD/TXT)
       │
       ▼
① 文件解析 → 提取纯文本
   - PDF: PagePdfDocumentReader（Apache PdfBox，按页提取）
   - MD/TXT: TextReader（纯文本直接读取）
       │
       ▼
② 文本分块 (Chunking)
   - TokenTextSplitter: 800 token/段
   - 分块过小 → 语义碎片化，检索噪声大
   - 分块过大 → 向量稀释，检索精度差
       │
       ▼
③ 文本 → 向量化 (Embedding)
   - bge-m3 模型: 文本 → 1024维浮点向量
   - 语义相近的文本 → 向量距离近
       │
       ▼
④ 向量 + 元数据 → PGVector 存储
   - metadata: {domain, title, file_type, chunk_index}
       │
       ▼
⑤ 记录文档信息 → knowledge_document 表
```

### 2.2 在线阶段：检索增强

```
用户提问 "Java线程池核心参数有哪些？"
       │
       ▼
① 问题 → Embedding (bge-m3) → 1024维向量
       │
       ▼
② 向量相似度检索 PGVector
   - 余弦相似度 (Cosine Similarity)
   - Top-K=5 最相关文档片段
   - 相似度阈值 > 0.5 过滤噪声
       │
       ▼
③ 构建增强 Prompt
   System: "根据以下参考资料回答..."
   Context: [检索到的5段文档]
   User: "Java线程池核心参数有哪些？"
       │
       ▼
④ LLM 生成回答 (qwen3.5:4b)
   - 基于真实文档内容 → 减少幻觉 → 准确性↑
```

### 2.3 两种 RAG 模式

本项目支持两种 RAG 模式：

| 模式 | 接口 | 原理 | 适用场景 |
|------|------|------|----------|
| **直接 RAG** | `POST /api/rag/chat` | 先检索 → 注入上下文 → LLM 生成 | 需要强制检索的场景 |
| **Tool Calling RAG** | `POST /api/chat/send` + `ragEnabled=true` | LLM 自主决定是否调用 KnowledgeSearchTool | 更智能，LLM 判断何时需要检索 |

---

## 三、RAG 优化策略

### 3.1 Chunking 优化（已实现）

| 策略 | 说明 | 本项目配置 |
|------|------|-----------|
| chunk_size | 每段最大 Token 数 | 800 |
| minChunkSizeChars | 分块最小字符数 | 50 |
| keepSeparator | 保留分隔符维持段落结构 | true |
| 中文分隔标点 | 优先在中文标点处分割 | 。！？.!? |

**调优建议**：
- 面试知识文档（问答形式）→ chunk_size 800~1000 较合适
- 长篇技术文档 → chunk_size 500~800
- 对话记录 → chunk_size 300~500

### 3.2 相似度阈值过滤（已实现）

```java
SearchRequest.builder()
    .query(query)
    .topK(5)
    .similarityThreshold(0.5)  // 低于 0.5 的结果不返回
    .build();
```

**为什么需要阈值**：Top-K 返回的 K 个结果不一定都相关。阈值过滤掉"最不相关"的结果，避免噪声污染 LLM 上下文。

**调优建议**：
- 0.3~0.5：宽松，召回率高但可能有噪声
- 0.5~0.7：适中，推荐默认值
- 0.7~0.9：严格，精确但可能遗漏相关内容

### 3.3 元数据过滤（已实现）

```java
// 按领域过滤，缩小检索范围
builder.filterExpression("domain == '" + domain + "'");
```

**好处**：
1. 缩小检索范围 → 提高精度
2. 支持多领域知识库隔离
3. 减少 Token 消耗

### 3.4 混合检索（Dense + Sparse）— 升级方向

当前使用 **Dense Retrieval**（稠密向量检索），BGE-M3 还支持：

| 检索方式 | 原理 | 优势 |
|---------|------|------|
| Dense | 文本→向量→余弦相似度 | 语义理解强 |
| Sparse | 关键词匹配（类似 BM25） | 精确匹配强 |
| ColBERT | Token 级交互 | 细粒度匹配 |

**升级方案**：Dense + Sparse 混合检索
- Dense 擅长语义匹配（"线程池" ↔ "Thread Pool"）
- Sparse 擅长精确匹配（"ThreadPoolExecutor" ↔ "ThreadPoolExecutor"）
- 两者融合 → 召回率和精度都提升

### 3.5 Re-ranking（重排序）— 升级方向

```
初检：向量检索 Top-20（快但粗糙）
         │
         ▼
重排：Cross-Encoder 精排 Top-5（慢但精确）
```

**原理**：向量检索是双编码器（Bi-Encoder），快但不够精确；Re-ranking 用交叉编码器（Cross-Encoder），慢但精确。

**适用场景**：知识库文档量大（>10000 条）时，先粗排再精排。

### 3.6 其他优化方向

| 优化 | 说明 | 难度 |
|------|------|------|
| 查询改写 | LLM 改写用户问题，提升检索效果 | 中 |
| 父文档检索 | 检索小块，返回大块上下文 | 中 |
| 多路召回 | 多种检索策略并行，结果融合 | 高 |
| 缓存 | 相同问题缓存检索结果 | 低 |
| 增量更新 | 文档修改时只更新变化部分 | 高 |

---

## 四、面试高频问题

1. **RAG 和微调的区别？什么时候用 RAG？**
   - RAG 适合知识密集型、需要实时更新的场景
   - 微调适合风格/格式适配

2. **Chunking 策略如何选择？chunk_size 多大合适？**
   - 问答类文档：800~1000 token
   - 长文档：500~800 token
   - 太小→碎片化，太大→稀释

3. **Embedding 模型如何选？**
   - 中文场景：bge-m3（智源 BAAI）最优
   - 英文场景：text-embedding-3-large（OpenAI）
   - 向量维度越高，表达力越强，但存储成本也越高

4. **为什么用余弦相似度而不是欧氏距离？**
   - 余弦相似度只看方向，不看长度 → 对文本长度不敏感
   - 欧氏距离受向量长度影响 → 长文本和短文本不公平

5. **RAG 的幻觉问题完全解决了吗？**
   - 没有，但大幅缓解
   - 检索结果提供了"事实锚点"
   - Prompt 中要求标注来源，进一步提升可追溯性

---

## 五、本项目 RAG 架构总结

```
┌─────────────────────────────────────────────────────────┐
│                    用户界面 (Vue 3)                       │
│     ChatView: [领域选择] [RAG开关] [深度思考] [输入框]     │
└───────────────────────┬─────────────────────────────────┘
                        │
           ┌────────────┴────────────┐
           │  ragEnabled=false       │  ragEnabled=true
           ▼                         ▼
    ChatController               ChatController
    (普通对话)                    (Tool Calling)
           │                         │
           ▼                         ▼
    ChatServiceImpl            ChatServiceImpl
    (直接调LLM)                 (.tools()注册Tool)
                                       │
                              ┌────────┴────────┐
                              ▼                 ▼
                      KnowledgeSearch    QuestionGenerate
                      Tool (RAG检索)     Tool (生成面试题)
                              │
                              ▼
                      VectorStore.similaritySearch()
                              │
                              ▼
                      PGVector (bge-m3 向量)
                              │
                              ▼
                      Ollama LLM (qwen3.5:4b)
```

---

## 六、测试验证步骤

1. **启动服务**：Docker PostgreSQL + Ollama + Spring Boot
2. **上传文档**：通过 `/api/knowledge/upload` 上传 PDF
3. **观察日志**：确认 文档解析 → 分块 → Embedding → PGVector 存储 全链路
4. **开启 RAG 对话**：前端打开"知识库增强"开关
5. **验证检索**：问文档中的问题，确认 AI 回答基于文档内容
6. **验证 Tool Calling**：观察日志中 LLM 是否自主调用 KnowledgeSearchTool
