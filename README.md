# 面试智能助手 (Interview Agent)

> 基于 Spring Boot 3 + Spring AI + Ollama/DeepSeek + PGVector 的面试智能助手，支持多 Provider LLM 切换、RAG 检索增强生成、混合检索 + Re-ranking 精排

## 项目介绍

Interview Agent 是一个面向 Java 求职者的智能面试辅助平台。系统利用大语言模型（本地 Ollama / 云端 DeepSeek API）和向量数据库（PGVector），为求职者提供智能面试问答、知识库管理和 RAG 检索增强生成服务。

**核心亮点**：

- 🔄 **多 Provider 切换** — 本地 Ollama 与云端 DeepSeek API 一键切换（`spring.ai.model.chat=ollama/deepseek`），零改造业务代码
- 📚 **生产级 RAG 架构** — 混合检索（向量 + 关键词 RRF 融合）→ Re-ranking 精排（TEI Cross-Encoder）→ 查询改写 → 上下文注入，四阶段检索流水线
- 🔧 **Tool Calling** — LLM 自主调用知识库检索工具，实现"开卷考试"式智能问答
- 🧠 **深度思考模式** — DeepSeek thinking 参数通过 HTTP 拦截器 + ThreadLocal 注入，支持流式推理过程展示
- 📊 **RAG 评估体系** — Java 自实现 RAGAS 核心指标（Context Precision/Recall、Faithfulness、Answer Relevancy），LLM-as-Judge 量化评估
- 💬 **SSE 流式响应** — 打字机式逐字输出，Markdown 实时渲染，代码高亮 + 复制按钮

## 技术栈

### 后端技术

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.3.6 | 应用框架 |
| Java | 17 | 开发语言 |
| Spring AI | 1.1.0 | AI 集成框架（Ollama + OpenAI 兼容接口） |
| MyBatis-Plus | 3.5.9 | ORM 框架 |
| PostgreSQL + PGVector | 16 | 关系数据库 + 向量存储 |
| HuggingFace TEI | 1.9 | Reranker 推理服务（Cross-Encoder 精排） |
| Knife4j | 4.5.0 | API 接口文档 |
| Hutool | 5.8.34 | 工具类库 |

### 前端技术

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5 | UI 框架 |
| Vite | 6.0 | 构建工具 |
| Element Plus | 2.9 | UI 组件库 |
| Markdown-it | 14.1 | Markdown 渲染 |
| Highlight.js | 11.11 | 代码高亮 |

### AI 模型

| 模型 | 用途 | 说明 |
|------|------|------|
| DeepSeek V4 Pro | 云端对话生成 | 默认模型，通过 DeepSeek API 调用，支持 thinking 模式 |
| Qwen3:1.7b | 本地对话生成 | 轻量快速，适合低显存设备，通过 Ollama 调用 |
| Qwen3.5:4b | 本地对话生成 | 推荐本地模型，效果更好，需 4GB+ 显存 |
| BGE-M3 | 文本向量化 | 中文优化，多语言多粒度，1024 维向量 |
| bce-reranker-base_v1 | Reranker 精排 | 279MB BERT Cross-Encoder，中文优化，TEI 推理 |

> 💡 **Provider 切换**：修改 `application.yml` 中 `spring.ai.model.chat` 为 `deepseek`（云端）或 `ollama`（本地），Embedding 始终使用 Ollama（bge-m3）。

## 功能特性

### 智能对话模块

- **SSE 流式响应**：基于 Server-Sent Events 实现打字机式输出，逐字显示
- **Markdown 实时渲染**：对话内容支持代码高亮、表格、列表等 Markdown 语法，流式自动补全未闭合标签
- **深度思考模式**：可开关的深度思考模式（需模型支持），展示模型推理过程
- **多 Provider 切换**：本地 Ollama 与云端 DeepSeek API 一键切换，业务代码零改造
- **对话历史管理**：支持新增、重命名、删除对话，按时间分组展示
- **领域选择**：支持 Java、数据库、网络、操作系统等面试方向

### RAG 知识库模块

- **文档智能处理**：支持 PDF、Markdown、TXT 等多种格式的自动上传、分块与向量化
- **文本清洗优化**：自定义 `DocumentTextCleaner`，自动去除页眉页脚、空行、噪声、PDF 断行修复
- **智能分块策略**：自定义 `SmartTextSplitter`，段落优先分割 + 12.5% 重叠窗口 + 中文感知
- **PDF 解析优化**：优先使用 `ParagraphPdfDocumentReader`（按段落提取），降级 `PagePdfDocumentReader`（按页提取）
- **混合检索**：向量检索（Dense）+ 关键词检索（Sparse）→ RRF 加权融合排序，兼顾语义和精确匹配
- **Re-ranking 精排**：TEI Cross-Encoder（bce-reranker-base_v1）对 Top-20 候选精排为 Top-5
- **查询改写**：口语化/模糊提问自动改写为更适合检索的形式
- **Tool Calling**：LLM 自主调用 `KnowledgeSearchTool` 检索知识库，集成 Reranker 精排

### RAG 评估模块

- **RAGAS 指标**：Java 自实现 Context Precision、Context Recall、Faithfulness、Answer Relevancy 四大核心指标
- **LLM-as-Judge**：使用 LLM 作为评判器自动打分，支持配置 Judge 模型
- **种子数据集**：内置 15 道 Java 面试题，支持自定义数据集管理
- **实验管理**：创建实验、选择数据集和题数、异步执行、结果可视化
- **前端仪表盘**：指标环形图、实验对比、详情查看

### 知识库管理模块

- **文档上传**：支持拖拽上传，自动识别文件类型（PDF/MD/TXT）
- **统计面板**：文档数、向量数、领域数统计卡片
- **文档管理**：卡片式文档列表，支持删除（同步清理 PGVector 向量数据）
- **文件类型徽章**：直观展示文档格式

## 系统架构

```
┌──────────────────────────────────────────────────────────────────┐
│                         Vue 3 前端                                │
│       (SSE 流式对话 + 知识库管理 + RAG 评估 + Markdown 渲染)       │
└──────────────────────────┬───────────────────────────────────────┘
                           │ HTTP / SSE
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                       Spring Boot 后端                            │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐          │
│  │ ChatService  │  │  RagService  │  │  EvalService  │          │
│  │ (对话管理)    │  │ (RAG 检索增强)│  │ (RAG 评估)    │          │
│  └──────┬───────┘  └──────┬───────┘  └───────────────┘          │
│         │                 │                                      │
│         ▼                 ▼                                      │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                 ChatOptionsFactory                         │  │
│  │         (Provider 无关的 Options 构建工厂)                  │  │
│  └──────────────────┬────────────────────────────────────────┘  │
│                     │                                           │
│         ┌───────────┴───────────┐                               │
│         ▼                       ▼                               │
│  ┌──────────────┐      ┌──────────────────┐                    │
│  │ OllamaChat   │      │ DeepSeekChat     │                    │
│  │ Model        │      │ Model (wrapper)  │                    │
│  │ (本地 LLM)   │      │ ┌──────────────┐ │                    │
│  └──────┬───────┘      │ │OpenAiChatModel│ │                    │
│         │              │ └──────┬───────┘ │                    │
│         │              │ Interceptor注入   │                    │
│         │              │ thinking 参数     │                    │
│         │              └────────┬─────────┘                    │
│         │                       │                              │
│  ┌──────┴───────────────────────┴──────────────────────────┐   │
│  │              Spring AI (1.1.0)                           │   │
│  │  ┌──────────┐ ┌────────────┐ ┌───────────────────────┐ │   │
│  │  │ Ollama   │ │  PGVector  │ │  Tool Calling         │ │   │
│  │  │ Embedding│ │ VectorStore│ │  (KnowledgeSearchTool) │ │   │
│  │  └────┬─────┘ └─────┬──────┘ └───────────────────────┘ │   │
│  └───────┼─────────────┼──────────────────────────────────┘   │
└──────────┼─────────────┼──────────────────────────────────────┘
           │             │
           ▼             ▼
    ┌────────────┐ ┌──────────────┐ ┌───────────────┐
    │   Ollama   │ │  PostgreSQL  │ │  TEI Reranker │
    │ :11434     │ │  + PGVector  │ │  :18081       │
    │ (Embedding)│ │  :5600       │ │ (Cross-Encoder)│
    └────────────┘ └──────────────┘ └───────────────┘
                    ▲
                    │
           ┌────────┴────────┐
           │  DeepSeek API   │
           │  (云端 LLM)     │
           │  api.deepseek.com│
           └─────────────────┘
```

### RAG 检索流水线

```
离线阶段（文档导入）:
  PDF/MD/TXT → 文件解析 → 文本清洗 → 智能分块 → Embedding(bge-m3) → PGVector 存储

在线阶段（四阶段检索增强）:
  用户提问 → 查询改写(可选) → 混合检索(向量+关键词 RRF 融合) → Re-ranking 精排(Top-20→Top-5) → Prompt 拼接 → LLM 生成回答
```

## 项目结构

```
interview-agent/
├── interview-agent-common/          # 公共模块
│   └── result/                      # 统一响应封装
│   └── exception/                   # 全局异常处理
│   └── constant/                    # 常量定义
├── interview-agent-model/           # 数据模型
│   └── entity/                      # 数据库实体
│   └── enums/                       # 枚举定义
│   └── dto/vo/                      # 数据传输对象
├── interview-agent-dao/             # 数据访问层
│   └── mapper/                      # MyBatis-Plus Mapper
│   └── sql/                         # 数据库建表脚本
├── interview-agent-service/         # 业务逻辑层
│   └── chat/                        # 对话服务
│       ├── deepseek/                #   DeepSeek 集成（ChatModel/Options/Interceptor）
│       ├── ChatOptionsFactory.java  #   Provider 无关的 Options 工厂
│       ├── ChatProviderProperties.java # Provider 配置映射
│       └── ChatModelConfig.java     #   ChatModel Bean 装配
│   └── rag/                         # RAG 服务
│       ├── transformer/             #   文本清洗 + 智能分块
│       ├── reranker/                #   TEI Reranker 精排
│       ├── rewrite/                 #   查询改写
│       └── hybrid/                  #   混合检索（向量+关键词 RRF）
│   └── eval/                        # RAG 评估模块
│       └── metric/                  #   RAGAS 指标计算
│   └── knowledge/                   # 知识库管理
│   └── tool/                        # Tool Calling 工具
├── interview-agent-web/             # Web 层（启动入口）
│   └── controller/                  # REST 控制器
│   └── config/                      # 配置类（Spring AI、CORS 等）
│   └── resources/
│       ├── application.yml          # 应用配置
│       ├── prompts/                 # AI 提示词模板（.st 文件）
│       ├── eval/                    # 评估种子数据集
│       └── sql/                     # 数据库初始化脚本
├── interview-agent-ui/              # Vue 3 前端
│   └── src/
│       ├── views/                   # 页面组件（Chat/Knowledge/Eval）
│       ├── components/              # 公共组件
│       └── api/                     # API 接口封装
├── docs/                            # 项目文档
│   ├── 技术指导/                    # 技术选型与实现文档
│   ├── RAG/                         # RAG 评估模块技术文档
│   ├── 项目进度与环境记录.md          # 进度追踪 + 环境信息
│   ├── 踩坑记录.md                   # 历史问题与解决方案
│   ├── 需求分析.md                   # 需求定义
│   ├── 方案设计文档.md               # 技术方案
│   └── RAG实现与优化指南.md          # RAG 技术详解
└── docker-compose.yml               # Docker 编排（PostgreSQL + PGVector + TEI Reranker）
```

## 快速开始

### 环境要求

| 依赖 | 版本 | 必需 | 说明 |
|------|------|------|------|
| JDK | 17+ | 是 | 开发语言 |
| Maven | 3.9+ | 是 | 构建工具 |
| Docker | - | 推荐 | 一键启动 PostgreSQL + PGVector |
| Ollama | - | 是 | 本地运行 LLM 和 Embedding 模型 |
| Node.js | 18+ | 是 | 前端构建 |

### 1. 安装 Ollama 并拉取模型

```bash
# 安装 Ollama（参考 https://ollama.ai）

# 拉取对话模型（本地模式使用，二选一）
ollama pull qwen3:1.7b       # 轻量快速，适合低显存设备
ollama pull qwen3.5:4b       # 推荐模型，效果更好，需 4GB+ 显存

# 拉取 Embedding 模型（必须，向量化用）
ollama pull bge-m3
```

> 💡 **本地 vs 云端？**
> - **云端 DeepSeek API**（默认）：无需本地显存，`deepseek-v4-pro` 效果好，需 API Key，按 Token 计费
> - **本地 Ollama**：零成本，数据隐私好，受本地硬件限制
> - 修改 `application.yml` 中 `spring.ai.model.chat` 为 `deepseek` 或 `ollama` 即可切换

### 2. 启动 PostgreSQL + PGVector

> 💡 **这是什么？为什么需要它？**
>
> 本项目使用 **PostgreSQL**（关系数据库）存储对话记录、文档信息等结构化数据，同时通过 **PGVector** 扩展存储和检索向量数据（RAG 的核心）。
> 简单来说：PGVector 让数据库不仅能存文字，还能存"语义向量"，从而实现"语义相似度搜索"——你问一个问题，它能找到意思最接近的文档片段。
>
> 我们通过 Docker 一键启动，无需手动安装 PostgreSQL 和配置 PGVector 扩展。

**前置条件**：安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)（Windows/Mac 均可）

```bash
# 1️⃣ 启动数据库 + Reranker（首次启动会自动拉取镜像）
#    在项目目录下启动终端执行
docker-compose up -d

# 2️⃣ 确认服务启动成功
docker-compose ps
# 应看到 interview-agent-postgres 和 interview-agent-reranker 状态为 Up

# 3️⃣ （可选）查看日志
docker-compose logs -f postgres
docker-compose logs -f tei-reranker
```

**常用命令**：

```bash
# 停止数据库（数据保留，下次启动数据还在）
docker-compose down

# 停止并清除所有数据（慎用！会删除所有对话记录和向量数据）
docker-compose down -v
```

启动后默认账号：

| 服务 | 地址 | 说明 |
|------|------|------|
| PostgreSQL | localhost:5600 | 用户名 `interview_agent`，密码 `interview_agent_2026`，自动初始化表结构 |
| TEI Reranker | localhost:18081 | bce-reranker-base_v1 Cross-Encoder 精排服务，CPU 模式运行 |

> ⚠️ **首次启动说明**：数据库表结构会通过 `docker-compose.yml` 中挂载的 `schema.sql` 自动初始化，无需手动建表。PGVector 的 `vector_store` 表由 Spring AI 在后端启动时自动创建（需确保 `application.yml` 中 `spring.ai.vectorstore.pgvector.initialize-schema: true`）。

### 3. 构建并启动后端

```bash
# 构建项目
mvn clean package -DskipTests

# 启动后端
cd interview-agent-web
java -jar target/interview-agent-web-1.0.0-SNAPSHOT.jar
```

后端服务启动于 http://localhost:2180

### 4. 启动前端

```bash
cd interview-agent-ui
npm install
npm run dev
```

前端服务启动于 http://localhost:3311

### 5. 访问

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端页面 | http://localhost:3311 | 用户访问入口 |
| 后端 API | http://localhost:2180 | RESTful API |
| 接口文档 | http://localhost:2180/doc.html | Knife4j 接口文档 |

## 模型配置说明

本项目支持 **多 Provider 切换**，通过 `application.yml` 配置选择使用本地 Ollama 或云端 DeepSeek API。

### Provider 切换

编辑 `interview-agent-web/src/main/resources/application.yml`：

```yaml
spring:
  ai:
    model:
      chat: deepseek       # 切换 LLM Provider：deepseek（云端）/ ollama（本地）
      embedding: ollama     # Embedding 始终用 Ollama（bge-m3）
      model-name: deepseek-v4-pro

    # DeepSeek API 配置（云端）
    openai:
      base-url: https://api.deepseek.com
      api-key: sk-xxxx      # 替换为你的 DeepSeek API Key
      chat:
        options:
          model: deepseek-v4-pro
          temperature: 0.5

    # Ollama 配置（本地）
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: qwen3:1.7b    # 可切换 qwen3.5:4b 等
          temperature: 0.5
          top-p: 0.9
          num-ctx: 4096
      embedding:
        options:
          model: bge-m3        # 中文优化，推荐不变
```

### GPU 加速（本地 Ollama）

Ollama 默认使用 GPU 加速（CUDA / Metal），如需进一步优化：

```bash
# 启用 Flash Attention（提升推理速度）
OLLAMA_FLASH_ATTENTION=1

# 调整上下文长度（影响显存占用和速度）
# 在 application.yml 中修改 num-ctx: 4096
```

## 常见问题

### Q: Ollama 连接失败？

1. 确认 Ollama 已启动：浏览器访问 http://localhost:11434，应返回 "Ollama is running"
2. 确认模型已拉取：`ollama list` 查看已安装模型
3. 确认端口未被占用：`application.yml` 中 `spring.ai.ollama.base-url` 默认为 `http://localhost:11434`

### Q: DeepSeek API 调用失败？

1. 确认 `application.yml` 中 `spring.ai.openai.api-key` 已配置正确的 API Key
2. 确认 `spring.ai.model.chat` 设为 `deepseek`
3. 确认网络可访问 `api.deepseek.com`

### Q: PGVector vector_store 表未自动创建？

在 `application.yml` 中确认：

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        initialize-schema: true  # 开发环境设为 true，自动创建表
```

### Q: Reranker 连接失败？

1. 确认 TEI Reranker Docker 容器已启动：`docker ps | grep reranker`
2. 确认端口 18081 可访问：`curl http://localhost:18081/health`
3. 首次启动需等待模型加载（约 1-2 分钟）
4. 如不需要 Reranker，可在 `application.yml` 中设置 `app.reranker.enabled: false`

### Q: 上传 PDF 后向量化失败？

1. 确认 `bge-m3` 模型已拉取：`ollama pull bge-m3`
2. 确认 Ollama 服务正常运行
3. 查看后端日志确认错误信息

### Q: 如何清理旧向量数据并重新导入？

1. 在前端知识库页面删除对应文档（会同步清理 PGVector 向量数据）
2. 重新上传 PDF，新数据将经过文本清洗 + 智能分块后入库

### Q: 模型推理速度慢？（本地 Ollama）

1. 确认 GPU 加速是否生效：`nvidia-smi` 查看 GPU 占用
2. 启用 Flash Attention：设置环境变量 `OLLAMA_FLASH_ATTENTION=1`
3. 减小上下文窗口：`num-ctx: 2048`（牺牲长文本能力换速度）
4. 使用更小的模型：`qwen3:1.7b` 比 `qwen3.5:4b` 快 3~5 倍
5. 或切换为云端 DeepSeek API：`spring.ai.model.chat: deepseek`

## TODO

- [x] 混合检索（Dense + Sparse + RRF 融合排序）
- [x] Re-ranking 重排序（TEI Cross-Encoder 精排）
- [x] 查询改写（Query Rewriting）
- [x] 接云端 LLM API（DeepSeek API，多 Provider 切换）
- [x] RAG 评估模块（RAGAS 指标 + LLM-as-Judge）
- [ ] 迁移到 RetrievalAugmentationAdvisor 架构
- [ ] 模拟面试模式（连续出题 + 评分）
- [ ] Token 用量统计 + 成本估算
- [ ] Docker 一键部署（前后端 + 数据库 + Ollama + TEI）
- [ ] 用户反馈机制（点赞/点踩）

