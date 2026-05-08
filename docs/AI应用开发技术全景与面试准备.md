# AI 应用开发技术全景与面试准备

> 版本：v2.0
> 日期：2026-05-05
> 用途：梳理 AI 应用开发岗位的完整技术体系，明确自身差距和学习路径
> 参考来源：Boss直聘/拉勾 JD 分析、GitHub Trending、知乎/掘金面经、各大框架官方文档
> v2.0 更新：基于 2026 年 5 月最新行业调研，验证并更新技术选型、补充 A2A 协议等新趋势

---

## 一、为什么有"AI 应用开发"这个岗位？

### 1.1 行业背景

2023-2024 年是大模型元年，各家公司争相训练自己的大模型。但到了 2025-2026 年，行业重心已经从**"谁有模型"**转向了**"谁能用模型做出产品"**。

这就是 AI 应用开发工程师存在的原因：

- **模型科学家**负责训练和优化模型（偏算法/数学）
- **AI 应用开发**负责把模型能力变成用户可用的产品（偏工程/产品）
- 两者分工不同，后者更接近传统软件工程师，但需要理解 AI 的特殊性

### 1.2 岗位核心价值

```
用户需求 → AI 应用开发 → 可用的 AI 产品
                │
                ├─ 选型：用哪个模型？本地还是云端？
                ├─ 架构：RAG？Agent？工作流？
                ├─ 工程：API 设计、前后端、部署
                ├─ 优化：效果评估、成本控制、性能调优
                └─ 运维：监控、日志、迭代
```

**关键认知：AI 应用开发不是"调 API"，而是"用 AI 解决实际问题的软件工程师"。**

---

## 二、AI 应用开发技术全景图

### 2.1 整体架构分层

```
┌─────────────────────────────────────────────────┐
│              用户交互层                           │
│   Web UI / 移动端 / VS Code 插件 / CLI / API    │
├─────────────────────────────────────────────────┤
│           应用编排层 (Orchestration)              │
│   ┌──────┐  ┌──────┐  ┌──────────┐  ┌────────┐ │
│   │ RAG  │  │Agent │  │ Workflow │  │ Prompt │ │
│   └──┬───┘  └──┬───┘  └────┬─────┘  └────────┘ │
├──────┼─────────┼───────────┼────────────────────┤
│      │    MCP Protocol ◄───┘                    │
│      ▼         ▼                                │
│  ┌───────┐ ┌──────┐ ┌──────────┐               │
│  │向量DB │ │图数据库│ │工具/API  │               │
│  └───────┘ └──────┘ └──────────┘               │
├─────────────────────────────────────────────────┤
│             模型服务层 (Serving)                  │
│  vLLM / TGI / Ollama / 云API (OpenAI兼容接口)   │
├─────────────────────────────────────────────────┤
│             模型层 (Foundation Models)            │
│  GPT-4o / Claude 4.6 / DeepSeek V3.1 / Qwen3.6 / 微调模型 │
├─────────────────────────────────────────────────┤
│             基础设施层 (Infra)                    │
│  GPU / K8s / Docker / 云服务 / 监控              │
└─────────────────────────────────────────────────┘
```

### 2.2 九大技术领域详解

---

#### 领域 1：RAG（检索增强生成）— 你已经在做的

**是什么**：让 LLM 基于外部知识库回答问题，而不是只靠训练数据。

**完整技术栈**：

| 环节 | 技术选型 | 说明 |
|------|---------|------|
| 文档解析 | Unstructured, LlamaParse, Marker, PDFPlumber | PDF/Word/HTML/图片 → 结构化文本 |
| 文本清洗 | 正则清洗、去噪、断行修复 | 你已做 DocumentTextCleaner ✅ |
| 分块策略 | 递归分块、语义分块、Agentic Chunking | 你已做 SmartTextSplitter ✅ |
| Embedding | OpenAI text-embedding-3, BGE-M3, Jina | 你用 bge-m3 ✅ |
| 向量存储 | Milvus, Qdrant, Weaviate, Chroma, PGVector | 你用 PGVector ✅ |
| 检索策略 | 向量检索、关键词检索、混合检索 | 你已做混合检索+RRF ✅ |
| 精排 | Cross-Encoder Reranking, Cohere Rerank | 你已做 TEI Reranker ✅ |
| 查询改写 | HyDE, Multi-Query, Query Rewriting | 你已做 QueryRewriting ✅ |
| 上下文压缩 | LLM 提取关键信息，减少 token | 未做 ❌ |
| 评估 | RAGAS, DeepEval, TruLens | 未做 ❌ |

**你的 RAG 水平：中上**。核心链路都做了，缺评估和上下文压缩。

**进阶方向**：
- **Graph RAG**：知识图谱 + 向量检索，解决多跳推理问题（微软 GraphRAG）
  - 2026 新进展：**NoLLMRAG** — 索引和检索阶段不依赖 LLM 的 Graph RAG，解决 GraphRAG 构建成本高的问题
  - **GEAR** — 图增强 Agent RAG，将图检索融入多步 Agent 框架
- **Agentic RAG**：Agent 自主决定是否检索、检索几次、是否需要追问
  - 2026 新进展：**R1-Router** 通过 RL 在异构知识库间动态路由查询，**CogPlanner** 通过并行/串行规划策略迭代优化检索
- **Multi-modal RAG**：支持图片、表格、公式的检索和理解
  - 2026 新进展：**ColPali 2** 纯视觉路线（完全绕过 OCR，直接将文档页面作为图片处理），**VisionRAG** 基于 ColPali 2 的端到端视觉检索
- **RAG+**：双语料库（知识+应用示例）联合检索，让 LLM 不仅获取信息还能学会如何应用

---

#### 领域 2：AI Agent（智能体）— 你只接触了皮毛

**是什么**：让 LLM 不只是回答问题，而是能自主规划、调用工具、执行任务。

**核心概念**：

```
Agent = LLM（大脑） + Tools（手脚） + Memory（记忆） + Planning（规划）
```

**典型工作流**：

```
用户："帮我分析这份简历，给出改进建议"
  │
  ├─ [规划] Agent 分析任务 → 需要：1.读取简历 2.分析内容 3.给出建议
  ├─ [工具调用] 调用文件读取工具 → 获取简历内容
  ├─ [推理] 分析简历优缺点
  ├─ [工具调用] 调用知识库检索 → 获取简历优化最佳实践
  └─ [生成] 综合生成改进建议
```

**技术栈**：

| 层次 | 技术 | 说明 |
|------|------|------|
| Agent 框架 | LangGraph, CrewAI, AutoGen, Dify, Spring AI Alibaba | 编排 Agent 工作流 |
| 工具调用 | Function Calling, Tool Use | LLM 决定调用哪个工具 |
| MCP 协议 | Model Context Protocol (Anthropic) | 标准化的工具连接协议（见领域3） |
| A2A 协议 | Agent-to-Agent Protocol (Google) | 标准化的 Agent 间通信协议（见领域3新增） |
| 记忆系统 | 短期(上下文窗口)、长期(向量存储)、工作记忆 | 让 Agent 记住之前的交互 |
| 规划模式 | ReAct, Plan-and-Execute, Reflexion | Agent 如何分解和执行任务 |
| 多 Agent | 任务分配、协调、结果汇总 | 多个 Agent 协作完成复杂任务 |
| 可观测性 | LangSmith, Phoenix(Arize), AgentOps | 追踪 Agent 每一步决策 |

**你目前的 Agent 水平：初级**。你有 Tool Calling（KnowledgeSearchTool），但缺少：
- Agent 自主规划能力（当前是手动控制是否检索）
- 多步工具调用链
- 记忆管理
- Agent 可观测性

**面试高频问题**：
- "Agent 和普通 RAG 有什么区别？" → RAG 是单次检索+生成，Agent 能自主决定多步操作
- "如何防止 Agent 陷入死循环？" → 设置最大迭代次数、超时、回退策略
- "多 Agent 系统如何协调？" → 任务分配、共享记忆、消息传递

---

#### 领域 3：MCP（Model Context Protocol）— 2025 年最热门的新标准

**是什么**：Anthropic 提出的开放协议，标准化 LLM 与外部工具/数据源的连接方式。

**类比**：就像 USB-C 统一了各种充电接口，MCP 统一了 AI 调用各种工具的接口。

**为什么重要**：

```
之前：每个 AI 平台都要为每个工具写一套集成代码
      OpenAI 调用数据库 → 写一套
      Claude 调用数据库 → 写一套
      国产模型调用数据库 → 又写一套

现在：工具提供 MCP Server，任何支持 MCP 的 AI 客户端都能直接调用
      数据库 MCP Server ← 一次构建，OpenAI/Claude/Dify 都能用
```

**架构**：

```
AI 应用 (MCP Client) ←→ MCP Server ←→ 外部工具/数据
                     ↑
               标准化协议
            (Tools + Resources + Prompts)
```

**三大能力**：
- **Tools**：让 LLM 调用外部函数（如查数据库、发邮件）
- **Resources**：让 LLM 读取外部数据（如文件、API 响应）
- **Prompts**：预定义的提示词模板

**生态现状（2025-2026，已验证）**：
- **2025年12月**：Anthropic 将 MCP 捐赠给 Linux Foundation 下的 **Agentic AI Foundation (AAIF)**，转为社区治理的开放标准
- **协议版本**：最新稳定版 2025-11-25，引入 Streamable HTTP 传输、OAuth/OpenID Connect 支持、图标元数据、增量范围同意等
- **生态规模**：17,000+ 公开 MCP Server（2026年4月数据），Remote MCP Server 较 2025年5月增长近 4 倍
- **官方 SDK**：TypeScript SDK 11,255+ GitHub stars；Java SDK 由 Spring 团队共同维护
- OpenAI、Google、Microsoft 都已支持 MCP
- Cursor、VS Code、Windsurf 等 AI IDE 全面集成
- ⚠️ **安全问题**：437,000+ 安装受安全漏洞影响，MCP 安全已成为行业关注焦点

**Spring AI MCP 集成（2026年5月最新）**：
- **MCP Client Boot Starter**：Spring Boot 应用作为 MCP 客户端连接 MCP Server
- **MCP Server Boot Starter**：Spring Boot 应用作为 MCP Server 暴露工具/资源
- **MCP Annotations**：注解式方法处理，简化 MCP Server/Client 开发
- **MCP Security**：OAuth 2.0 和 API Key 安全支持
- **Streamable HTTP**：支持 2025-03-26 规范的 Streamable HTTP 传输模式
- 可通过 Spring Initializr 直接引导项目

**你需要了解的**：
- MCP 和你项目中的 Tool Calling 是什么关系 → Tool Calling 是底层能力，MCP 是上层协议标准
- 如何用 Java 开发 MCP Server → Spring AI MCP Boot Starter + 注解，开箱即用
- MCP 安全最佳实践 → 验证来源、权限最小化、审计日志

---

#### 领域 3.5：A2A 协议（Agent-to-Agent）— 2026 年 Agent 通信新标准 ⭐新增

**是什么**：Google 于 2025年4月发布的开放协议，标准化 AI Agent 之间的通信和协作方式。

**与 MCP 的关系**：

```
MCP：Agent ↔ 工具/数据源（一个 Agent 调用外部工具）
A2A：Agent ↔ Agent（多个 Agent 之间协作）

类比：
  MCP = 一个人用工具干活（人→锤子/螺丝刀）
  A2A = 多个人协作干活（人→人→人→工具）
```

**为什么重要**：

```
2025-2026 年趋势：从单 Agent → 多 Agent 协作

之前：每个 Agent 系统都是孤岛
      Agent A 的结果无法直接传给 Agent B
      需要人工编排或自定义集成

现在：Agent 通过 A2A 协议自动发现、协商、协作
      Agent A 发布能力卡片 → Agent B 发现并请求协作 → 标准化消息交换
```

**核心概念**：
- **Agent Card**：JSON 格式的能力描述文件（`/.well-known/agent-card.json`），类似网站的 robots.txt
- **Message & Part**：Agent 间交换的消息，支持文本、文件、结构化数据等多种模态
- **Task**：Agent 间的协作任务，支持长时运行和状态追踪
- **HTTP + JSON-RPC 2.0**：基于 Web 标准的传输层

**生态现状（2026年4月，已验证）**：
- 2025年4月 Google 发布，随后捐赠给 Linux Foundation
- **2026年4月**：150+ 组织支持，Google/Microsoft/AWS 深度集成，多行业生产部署
- 当前版本 v0.2.5，仍在快速迭代
- 与 MCP 互补而非竞争：MCP 解决 Agent 连接工具，A2A 解决 Agent 连接 Agent

**面试相关**：
- "MCP 和 A2A 有什么区别？" → MCP 是 Agent↔工具协议，A2A 是 Agent↔Agent 协议，两者互补
- "多 Agent 系统如何通信？" → A2A 提供标准化通信：发现（Agent Card）→ 协商 → 协作（Task）→ 交换（Message）

---

#### 领域 4：Prompt Engineering（提示词工程）— 基础中的基础

**是什么**：通过设计好的提示词，让 LLM 输出更准确、更符合预期的结果。

**核心技术**：

| 技术 | 说明 | 示例 |
|------|------|------|
| Zero-shot | 不给示例，直接提问 | "请总结以下文章" |
| Few-shot | 给几个示例 | "示例1:... 示例2:... 请按同样格式处理" |
| Chain-of-Thought | 引导逐步推理 | "请一步步思考..." |
| ReAct | 推理+行动交替 | "思考→行动→观察→思考→..." |
| Tree-of-Thought | 多路径探索 | 同时考虑多个推理方向 |
| Self-Consistency | 多次采样取共识 | 生成多个答案，投票选最优 |
| 结构化输出 | 指定输出格式 | JSON mode, XML 格式 |

**安全相关**：

| 问题 | 说明 |
|------|------|
| Prompt 注入 | 用户在输入中嵌入恶意指令，覆盖系统提示 |
| Prompt 泄露 | 用户诱导 LLM 暴露系统提示词 |
| 越狱攻击 | 绕过安全限制让 LLM 生成有害内容 |

**你的水平**：你有 6 个 .st Prompt 模板，但主要是基础用法。需要深入了解 CoT、ReAct 等高级技术。

---

#### 领域 5：模型微调（Fine-tuning）— 你完全没接触

**是什么**：用自己的数据对预训练模型进行二次训练，让模型更擅长特定任务。

**为什么要微调**：

| 场景 | 为什么不直接用 Prompt/RAG |
|------|--------------------------|
| 特定领域术语 | RAG 检索不到时模型就不懂 |
| 特定输出风格 | Prompt 难以精确控制 |
| 降低推理成本 | 微调后的小模型可以替代大模型 |
| 数据安全敏感 | 不能把数据发给外部 API |

**主流微调方法**：

| 方法 | 说明 | 显存需求 |
|------|------|---------|
| Full Fine-tuning | 更新全部参数 | 极高（7B 模型需要 ~60GB） |
| LoRA | 冻结原权重，只训练低秩矩阵 | 低（7B 模型 ~16GB） |
| QLoRA | LoRA + 4bit 量化（NF4 格式） | 更低（7B 模型 ~6GB） |
| LowRA | 2bit LoRA，极致压缩 | 极低（2026 新方法，适合嵌入式/移动端） |
| DoRA | LoRA 改进版（权重分解） | 与 LoRA 相近 |
| IA3 | 学习重缩放向量，比 LoRA 更轻量 | 极低 |
| RLHF/DPO | 基于人类反馈的对齐训练 | 高 |

**LoRA 核心原理（面试必问）**：

```
原权重 W（冻结）
旁路 ΔW = B × A（训练）
  - B ∈ R^(d×r), A ∈ R^(r×d)
  - r << d（通常 4~64）
  - 可训练参数 < 1%
推理时可合并：W' = W + B×A（零额外延迟）
```

**工具链**：
- Hugging Face PEFT + TRL（最主流）
- LLaMA-Factory（国内主流一站式框架）
- Unsloth（2x 加速）
- Axolotl

**你需要知道但不一定马上要做的**：
- LoRA 的原理和适用场景
- 什么时候该微调 vs 什么时候 RAG 就够了
- 数据集构建和清洗

---

#### 领域 6：模型部署与推理优化 — 你的项目有但不深

**是什么**：让模型高效、低成本地在生产环境运行。

**技术栈**：

| 方向 | 技术 | 说明 |
|------|------|------|
| 推理框架 | vLLM, TGI, SGLang, LMDeploy, TensorRT-LLM | 高性能推理服务；2026年 SGLang/LMDeploy 速度领先（~16,200 tok/s vs vLLM ~12,500 tok/s），vLLM 生态最成熟 |
| 量化 | GPTQ, AWQ, GGUF, INT4/INT8, FP8 | 降低显存占用和推理成本 |
| 缓存 | KV Cache, Prompt Cache, Semantic Cache | 减少重复计算 |
| 加速 | Speculative Decoding（投机解码） | 用小模型加速大模型推理 |
| 边缘部署 | Ollama, llama.cpp, Core ML, ONNX | 本地/端侧运行 |
| API 网关 | LiteLLM, OneAPI | 统一多模型 API 接口 |
| 容器化 | Docker, K8s, Helm | 生产级部署 |

**你的水平**：你用了 Ollama 本地部署 + Flash Attention 优化，但缺少：
- 量化部署实践（GPTQ/AWQ）
- vLLM 等高性能推理框架
- API 网关（多模型统一接口）
- 生产级部署（K8s、负载均衡）

---

#### 领域 7：多模态（Multimodal）— 你完全没接触

**是什么**：让 AI 不只是处理文本，还能理解图片、音频、视频。

**技术方向**：

| 方向 | 代表模型 | 应用场景 |
|------|---------|---------|
| 视觉理解 | GPT-4o, Claude Opus 4.6, Qwen-VL | 图片识别、OCR、图表分析 |
| 图像生成 | DALL-E 3, Midjourney, Stable Diffusion | AI 绘画、设计 |
| 语音理解 | Whisper, Qwen-Audio | 语音转文字、语音助手 |
| 语音合成 | TTS, ElevenLabs | AI 播客、语音助手 |
| 视频理解 | Gemini, GPT-4o | 视频分析、摘要 |
| 多模态 RAG | ColPali 2, ColQwen, VisionRAG | 图文混合文档的检索（纯视觉路线，绕过 OCR） |

**面试相关**：
- "如何实现一个多模态 RAG 系统？" → 文档解析（图片+表格+文字）→ 多模态 Embedding → 检索 → 多模态 LLM 生成
- "图片和文本如何做联合检索？" → CLIP 模型做跨模态 Embedding

---

#### 领域 8：评估与可观测性 — 你完全没接触，但非常重要

**是什么**：量化 AI 应用的效果，监控生产环境的表现。

**为什么重要**：没有评估就没有优化方向。"感觉还行"在面试中是致命的回答。

**RAG 评估指标**：

| 指标 | 说明 | 怎么算 |
|------|------|--------|
| Context Precision | 检索到的文档中有多少是相关的 | 相关文档数 / 检索到的文档数 |
| Context Recall | 相关文档有多少被检索到了 | 检索到的相关文档 / 总相关文档数 |
| Faithfulness | 生成的回答是否忠于检索到的内容 | 回答中基于文档的信息比例 |
| Answer Relevancy | 回答与问题的相关程度 | 回答是否真正回答了用户的问题 |

**评估框架**：
- **RAGAS**：最流行的 RAG 评估框架，首创 RAG 四大核心指标，生态极好（无缝对接 LangChain/LlamaIndex）
- **DeepEval**：通用 LLM 评估，v3.9.9 提供 50+ 内置指标，类似 Pytest 的使用体验
- **TruLens**：端到端追踪和评估，RAG Triad 框架（Context Relevance / Answer Relevance / Groundedness）
- **Phoenix (Arize)**：完全开源的可观测性平台，可升级到 Arize AX 企业版
- **Langfuse**：开源 LLM 工程平台，2026 年热度很高的 LangSmith 替代品
- **Braintrust**：2026 年新兴的评估+可观测性平台

**生产监控指标**：

| 指标 | 说明 |
|------|------|
| Token 用量 | 每次对话消耗多少 token |
| 延迟 | 首 token 时间、总响应时间 |
| 错误率 | API 调用失败率 |
| 成本 | 每次对话的费用 |
| 用户满意度 | 点赞/点踩、反馈 |

---

#### 领域 9：应用开发平台（低代码）— 了解即可

**是什么**：让非开发者也能快速搭建 AI 应用的平台。

| 平台 | 特点 | 适合场景 |
|------|------|---------|
| **Dify** | 开源 LLM 应用开发平台，RAG+Agent+工作流，134k+ GitHub Stars，400万+ Docker 下载 | 企业内部 AI 应用 |
| **Coze（扣子）** | 字节跳动，零代码 Agent 构建 | 快速原型、个人项目 |
| **FastGPT** | 开源，知识库+工作流 | 知识库问答 |
| **RAGFlow** | 开源 RAG 引擎，深度文档理解 | 文档密集型场景 |
| **MaxKB** | 基于 LLM 的知识库问答 | 企业知识管理 |
| **n8n** | 开源工作流自动化，内置 AI Agent 节点，支持 LangChain 集成 | 自动化+AI 混合场景 |

**面试相关**：
- "为什么不直接用 Dify 而要自己开发？" → 自研可控性更高，能深度定制 RAG 策略和 Agent 逻辑，低代码平台适合快速验证但不够灵活
- "Dify 的架构是怎样的？" → 了解即可，展示你知道行业生态

---

## 三、你的项目 vs 行业要求 — 差距分析

### 3.1 覆盖度评估

| 技术领域 | 行业要求 | 你的项目 | 差距 |
|---------|---------|---------|------|
| RAG | 核心必备 | ✅ 中上水平 | 缺评估、上下文压缩 |
| Agent | 核心必备 | ⚠️ 初级 | 只有 Tool Calling，缺规划/记忆/编排 |
| MCP | **必备**（部分 JD 已列为硬性要求） | ❌ 未接触 | 需要掌握 Spring AI MCP 集成 |
| A2A | 加分项→快速变必备 | ❌ 未接触 | 需了解协议概念和与 MCP 的区别 |
| Prompt Engineering | 基础必备 | ⚠️ 基础 | 需深入了解 CoT/ReAct 等高级技术 |
| 模型微调 | 中高级必备 | ❌ 未接触 | 需了解 LoRA 原理和适用场景 |
| 部署与推理 | 生产必备 | ⚠️ 初级 | 只有 Ollama 本地，缺生产级部署 |
| 多模态 | 加分项 | ❌ 未接触 | 可选，但面试加分 |
| 评估与可观测 | 核心必备 | ❌ 未接触 | 必须补，面试高频考点 |
| 工程能力 | 基础必备 | ✅ 不错 | Java/Spring/Vue 全栈能力已有 |

### 3.2 面试竞争力评估

```
你的技术栈覆盖：

RAG              ████████████████░░░░  80%  ← 核心优势
Agent            ████░░░░░░░░░░░░░░░░  20%  ← 最大短板
MCP              ░░░░░░░░░░░░░░░░░░░░   0%  ← 必须补（JD硬性要求）
A2A              ░░░░░░░░░░░░░░░░░░░░   0%  ← 需要了解
Prompt           ████████░░░░░░░░░░░░  40%  ← 需要深化
微调              ░░░░░░░░░░░░░░░░░░░░   0%  ← 了解原理即可
部署              ██████░░░░░░░░░░░░░░  30%  ← 需要补
多模态            ░░░░░░░░░░░░░░░░░░░░   0%  ← 可选
评估              ░░░░░░░░░░░░░░░░░░░░   0%  ← 必须补
工程能力          ████████████████░░░░  80%  ← 你的底牌
```

---

## 四、学习路径建议

### 4.1 优先级排序

| 优先级 | 方向 | 原因 | 学习时间 |
|--------|------|------|---------|
| P0 | RAG 评估 | 面试必问，你已有 RAG 基础，只差评估 | 2-3天 |
| P0 | Agent 基础 | 行业核心方向，你目前最大短板 | 1-2周 |
| P0 | MCP 协议 | **部分 JD 已列为硬性要求**，Spring AI MCP 成熟可用 | 2-3天 |
| P1 | A2A 协议 | 2026 年 Agent 通信新标准，了解概念和与 MCP 区别 | 1-2天 |
| P1 | Prompt 进阶 | CoT/ReAct 是面试高频考点 | 2-3天 |
| P1 | 部署优化 | Docker Compose + 量化部署 | 2-3天 |
| P2 | 微调原理 | 面试问到能答出来即可，不需要实操 | 2-3天 |
| P2 | 多模态 | 了解概念和应用场景 | 1-2天 |
| P3 | 知识图谱 | 加分项，学习成本高 | 按需 |

### 4.2 具体学习资源

#### Agent 方向（P0，最紧急）

| 资源 | 类型 | 说明 |
|------|------|------|
| LangGraph 官方文档 | 文档 | Agent 编排框架，理解状态图、节点、边 |
| CrewAI 官方教程 | 文档 | 多 Agent 协作框架，理解角色/任务/流程 |
| Spring AI Function Calling | 文档 | 你用的框架，深入理解 Tool Calling 机制 |
| "Building Effective Agents" (Anthropic) | 文章 | Anthropic 官方的 Agent 设计指南 |
| AutoGen 官方文档 | 文档 | 微软的多 Agent 框架 |

#### 评估方向（P0）

| 资源 | 类型 | 说明 |
|------|------|------|
| RAGAS 官方文档 | 文档 | RAG 评估框架，理解四大指标 |
| DeepEval 文档 | 文档 | 通用 LLM 评估 |
| "Your RAG is Broken" | 文章 | RAG 常见问题和优化方向 |

#### MCP 方向（P0，已升级）

| 资源 | 类型 | 说明 |
|------|------|------|
| modelcontextprotocol.io | 文档 | MCP 官方规范（最新版 2025-11-25） |
| Spring AI MCP 集成文档 | 文档 | Java 生态的 MCP 实现（Client/Server Starter + 注解 + 安全） |
| MCP Java SDK (GitHub) | 代码 | 官方 Java SDK，Spring 团队共同维护 |
| MCP Server 示例 | GitHub | 了解 MCP Server 的结构 |
| Spring AI MCP Getting Started | 文档 | Spring 官方 MCP 入门指南 |

#### Prompt 进阶（P1）

| 资源 | 类型 | 说明 |
|------|------|------|
| promptingguide.ai | 文档 | 最全面的 Prompt 技术指南 |
| Anthropic Prompt Engineering | 文档 | Claude 官方提示词指南 |
| OpenAI Prompt Engineering | 文档 | GPT 官方提示词指南 |

#### 微调方向（P2）

| 资源 | 类型 | 说明 |
|------|------|------|
| Hugging Face PEFT 文档 | 文档 | LoRA/QLoRA 的标准实现 |
| LLaMA-Factory GitHub | 项目 | 国内最流行的微调框架 |
| "LoRA 论文精读" | 视频/文章 | 理解低秩分解的数学原理 |

---

## 五、面试高频问题清单

### 5.1 RAG 相关

| 问题 | 要点 |
|------|------|
| RAG 的完整流程是什么？ | 文档解析→分块→Embedding→存储→检索→精排→Prompt拼接→LLM生成 |
| 如何优化 RAG 的检索效果？ | 混合检索、Reranking、查询改写、分块策略优化 |
| 向量检索和关键词检索的区别？ | 向量擅长语义匹配，关键词擅长精确匹配 |
| 什么是 Reranking？为什么需要？ | 粗排+精排两阶段，提高最终结果质量 |
| 如何评估 RAG 系统？ | RAGAS 四大指标：Precision, Recall, Faithfulness, Relevancy |
| Chunk 大小怎么选？ | 太大→噪声多，太小→语义不完整，通常 500-1000 token |
| 如何处理 RAG 的幻觉问题？ | 提高检索质量、Prompt 约束、后处理验证 |

### 5.2 Agent 相关

| 问题 | 要点 |
|------|------|
| Agent 和 RAG 有什么区别？ | RAG 是单次检索+生成，Agent 能自主规划多步操作 |
| 什么是 ReAct 模式？ | Reasoning + Acting 交替：思考→行动→观察→思考 |
| 如何设计 Agent 的工具？ | 工具定义要清晰、单一职责、有错误处理 |
| 多 Agent 系统如何协调？ | 任务分配、共享记忆、消息传递、层级管理 |
| 如何防止 Agent 陷入死循环？ | 最大迭代次数、超时、回退策略、人工介入 |
| Agent 的记忆怎么管理？ | 短期(上下文)、长期(向量存储)、工作记忆(当前任务状态) |
| MCP 和 A2A 有什么区别？ | MCP 是 Agent↔工具协议，A2A 是 Agent↔Agent 协议，两者互补 |
| 如何用 Spring AI 开发 MCP Server？ | MCP Server Boot Starter + @Tool 注解，开箱即用 |
| MCP 的安全风险有哪些？ | 供应链攻击、权限越界、Prompt 注入通过工具链传播 |

### 5.3 工程化相关

| 问题 | 要点 |
|------|------|
| 如何控制 LLM 调用成本？ | Prompt 缓存、语义缓存、模型选择、Token 限制 |
| 如何监控 AI 应用？ | Token 用量、延迟、错误率、用户满意度 |
| 如何处理 LLM 的不确定性？ | Temperature 控制、多次采样、结构化输出 |
| 如何做 LLM 应用的测试？ | 回归测试、评估数据集、A/B 测试 |
| 本地模型 vs 云端模型怎么选？ | 成本、延迟、质量、数据安全四个维度对比 |

### 5.4 基础知识

| 问题 | 要点 |
|------|------|
| Transformer 的 Self-Attention 是什么？ | Q/K/V 矩阵、注意力分数计算、多头注意力 |
| 什么是 Tokenization？ | BPE 算法、词表大小对模型的影响 |
| LLM 为什么会产生幻觉？ | 训练数据偏差、解码策略、缺乏外部知识 |
| LoRA 的原理是什么？ | 低秩矩阵分解、冻结原权重、旁路训练 |
| 什么是 RLHF/DPO？ | 人类反馈对齐、偏好学习 |

---

## 六、给自己的定位建议

### 6.1 你的优势

1. **Java 全栈能力**：Spring Boot + Vue3 + 数据库，这是很多 AI 应用开发岗位需要的
2. **RAG 实战经验**：完整的文档解析→分块→检索→精排→生成链路，踩过坑有故事讲
3. **工程化意识**：日志系统、错误处理、UI 交互优化，不是只会写 demo
4. **学习能力**：从零开始做 RAG，涉及了多个技术组件的集成

### 6.2 你的定位

**不要定位为"AI 算法工程师"，定位为"AI 应用开发工程师"。**

```
你不需要：训练模型、推导公式、写 CUDA kernel
你需要：  选型、集成、优化、部署、评估 AI 应用

面试时强调：
- "我能用 Spring AI + Ollama 搭建完整的 RAG 应用"
- "我理解 RAG 的优化策略（混合检索/Reranking/查询改写）"
- "我有工程化思维（日志/监控/错误处理）"
- "我了解 Agent 的设计模式和 MCP/A2A 协议"
- "我知道如何评估和优化 AI 应用的效果"
```

### 6.3 一句话总结

**AI 应用开发 = 软件工程能力 + AI 技术理解 + 产品思维。**

你的软件工程能力已经不错，AI 技术理解需要从 RAG 扩展到 Agent/MCP/评估，产品思维需要从"功能做了"升级到"效果好不好、成本高不高、用户满不满意"。

---

## 七、更新日志

| 日期 | 更新内容 |
|------|---------|
| 2026-05-02 | 初版创建，梳理 AI 应用开发九大技术领域 |
| 2026-05-05 | v2.0 基于行业调研全面验证更新：① MCP 生态数据更新（17,000+ Server、AAIF 治理、Spring AI MCP 完整集成）② 新增 A2A 协议领域 ③ RAG 进阶方向补充（NoLLMRAG/GEAR/RAG+/ColPali 2）④ 模型更新至 Claude 4.6/Qwen3.6/DeepSeek V3.1 ⑤ 推理框架补充 LMDeploy 及 2026 性能对比 ⑥ 微调方法补充 LowRA/IA3 ⑦ 评估框架补充 Langfuse/Braintrust ⑧ MCP 优先级从 P1 升至 P0 ⑨ 应用平台补充 n8n ⑩ 面试问题补充 MCP/A2A 相关 |
