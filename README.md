# 面试智能助手 (Interview Agent)

> 基于 Spring Boot 3 + Spring AI + Ollama + PGVector 的面试智能助手，支持 RAG 检索增强生成，本地部署零成本运行

## 项目介绍

Interview Agent 是一个面向 Java 求职者的智能面试辅助平台。系统利用本地部署的大语言模型（Ollama）和向量数据库（PGVector），为求职者提供智能面试问答、知识库管理和 RAG 检索增强生成服务。

**核心亮点**：

- 🏠 **完全本地运行** — 基于 Ollama 本地部署 LLM，无需 API Key，零成本，数据隐私有保障
- 📚 **RAG 检索增强** — 上传面试知识文档 → 智能清洗分块 → 向量化存储 → 检索增强生成，让 AI 基于真实资料回答
- 🔧 **Tool Calling** — LLM 自主调用知识库检索工具，实现"开卷考试"式智能问答
- 🎯 **数据预处理优化** — 自定义文本清洗器 + 语义感知分块器，解决 PDF 提取噪声问题
- 💬 **SSE 流式响应** — 打字机式逐字输出，Markdown 实时渲染，体验流畅

## 技术栈

### 后端技术

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.3.6 | 应用框架 |
| Java | 17 | 开发语言 |
| Spring AI | 1.1.0 | AI 集成框架（Ollama + PGVector） |
| MyBatis-Plus | 3.5.9 | ORM 框架 |
| PostgreSQL + PGVector | 16 | 关系数据库 + 向量存储 |
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
| Qwen3:1.7b | 对话生成 | 默认模型，轻量快速，适合低显存设备 |
| Qwen3.5:4b | 对话生成 | 推荐模型，效果更好，需 4GB+ 显存 |
| BGE-M3 | 文本向量化 | 中文优化，多语言多粒度，1024 维向量 |

> 💡 **模型切换**：修改 `interview-agent-web/src/main/resources/application.yml` 中的 `spring.ai.ollama.chat.options.model` 即可切换对话模型，Ollama 会自动拉取对应模型。

## 功能特性

### 智能对话模块

- **SSE 流式响应**：基于 Server-Sent Events 实现打字机式输出，逐字显示
- **Markdown 实时渲染**：对话内容支持代码高亮、表格、列表等 Markdown 语法
- **深度思考模式**：可开关的深度思考模式（需模型支持），展示模型推理过程
- **对话历史管理**：支持新增、重命名、删除对话，按时间分组展示
- **领域选择**：支持 Java、数据库、网络、操作系统等面试方向

### RAG 知识库模块

- **文档智能处理**：支持 PDF、Markdown、TXT 等多种格式的自动上传、分块与向量化
- **文本清洗优化**：自定义 `DocumentTextCleaner`，自动去除页眉页脚、空行、噪声、PDF 断行修复
- **智能分块策略**：自定义 `SmartTextSplitter`，段落优先分割 + 12.5% 重叠窗口 + 中文感知
- **PDF 解析优化**：优先使用 `ParagraphPdfDocumentReader`（按段落提取），降级 `PagePdfDocumentReader`（按页提取）
- **相似度阈值过滤**：低于阈值（0.5）的检索结果不参与增强，避免噪声
- **元数据过滤**：按领域（domain）过滤，缩小检索范围
- **Tool Calling**：LLM 自主调用 `KnowledgeSearchTool` 检索知识库

### 知识库管理模块

- **文档上传**：支持拖拽上传，自动识别文件类型（PDF/MD/TXT）
- **统计面板**：文档数、向量数、领域数统计卡片
- **文档管理**：卡片式文档列表，支持删除（同步清理 PGVector 向量数据）
- **文件类型徽章**：直观展示文档格式

## 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                      Vue 3 前端                          │
│          (SSE 流式对话 + 知识库管理 + Markdown 渲染)       │
└──────────────────────┬──────────────────────────────────┘
                       │ HTTP / SSE
                       ▼
┌─────────────────────────────────────────────────────────┐
│                  Spring Boot 后端                        │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ ChatService │  │  RagService  │  │KnowledgeSvc   │  │
│  │ (对话管理)   │  │ (RAG 检索增强)│  │ (文档管理)     │  │
│  └──────┬──────┘  └──────┬───────┘  └───────┬───────┘  │
│         │                │                   │          │
│         ▼                ▼                   ▼          │
│  ┌──────────────────────────────────────────────────┐   │
│  │              Spring AI (1.1.0)                    │   │
│  │  ┌──────────┐ ┌────────────┐ ┌───────────────┐  │   │
│  │  │  Ollama  │ │  PGVector  │ │ Tool Calling  │  │   │
│  │  │  Chat    │ │ VectorStore│ │  (知识库检索)  │  │   │
│  │  └────┬─────┘ └─────┬──────┘ └───────────────┘  │   │
│  └───────┼─────────────┼────────────────────────────┘   │
└──────────┼─────────────┼────────────────────────────────┘
           │             │
           ▼             ▼
    ┌────────────┐ ┌──────────────┐
    │   Ollama   │ │  PostgreSQL  │
    │ (LLM+Emb) │ │  + PGVector  │
    │ :11434     │ │  :5600       │
    └────────────┘ └──────────────┘
```

### RAG 数据流

```
离线阶段（文档导入）:
  PDF/MD/TXT → 文件解析 → 文本清洗 → 智能分块 → Embedding(bge-m3) → PGVector 存储

在线阶段（检索增强）:
  用户提问 → 问题向量化 → PGVector 相似度检索 → Prompt 拼接 → LLM 生成回答
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
│   └── rag/                         # RAG 服务
│       └── transformer/             # 文本清洗 + 智能分块
│   └── knowledge/                   # 知识库管理
│   └── tool/                        # Tool Calling 工具
├── interview-agent-web/             # Web 层（启动入口）
│   └── controller/                  # REST 控制器
│   └── config/                      # 配置类（Spring AI、CORS 等）
│   └── resources/
│       ├── application.yml          # 应用配置
│       ├── prompts/                 # AI 提示词模板（.st 文件）
│       └── sql/                     # 数据库初始化脚本
├── interview-agent-ui/              # Vue 3 前端
│   └── src/
│       ├── views/                   # 页面组件
│       ├── components/              # 公共组件
│       └── api/                     # API 接口封装
├── docs/                            # 项目文档
│   ├── 项目进度与环境记录.md          # 进度追踪 + 环境信息
│   ├── 踩坑记录.md                   # 历史问题与解决方案
│   ├── 需求分析.md                   # 需求定义
│   ├── 方案设计文档.md               # 技术方案
│   └── RAG实现与优化指南.md          # RAG 技术详解
└── docker-compose.yml               # Docker 编排（PostgreSQL + PGVector）
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

# 拉取对话模型（二选一）
ollama pull qwen3:1.7b       # 默认模型，轻量快速，适合低显存设备
ollama pull qwen3.5:4b       # 推荐模型，效果更好，需 4GB+ 显存

# 拉取 Embedding 模型（中文优化，必须）
ollama pull bge-m3
```

> 💡 **如何选择模型？**
> - `qwen3:1.7b` — 显存 < 4GB 或追求速度时使用，效果尚可
> - `qwen3.5:4b` — 显存 ≥ 4GB 时推荐，回答质量明显更好
> - 也可尝试其他 Ollama 支持的模型（如 `deepseek-r1:7b`、`llama3:8b` 等），修改 `application.yml` 中的 `spring.ai.ollama.chat.options.model` 即可

### 2. 启动 PostgreSQL + PGVector

> 💡 **这是什么？为什么需要它？**
>
> 本项目使用 **PostgreSQL**（关系数据库）存储对话记录、文档信息等结构化数据，同时通过 **PGVector** 扩展存储和检索向量数据（RAG 的核心）。
> 简单来说：PGVector 让数据库不仅能存文字，还能存"语义向量"，从而实现"语义相似度搜索"——你问一个问题，它能找到意思最接近的文档片段。
>
> 我们通过 Docker 一键启动，无需手动安装 PostgreSQL 和配置 PGVector 扩展。

**前置条件**：安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)（Windows/Mac 均可）

```bash
# 1️⃣ 启动数据库（首次启动会自动拉取 pgvector/pgvector:pg16 镜像，约 300MB）
#    在项目目录下启动终端执行
#    端口映射为 5600（避免与本地已有的 PostgreSQL 5432 端口冲突）
docker-compose up -d

# 2️⃣ 确认数据库启动成功
docker-compose ps
# 应看到 interview-agent-postgres 状态为 Up (healthy)

# 3️⃣ （可选）查看数据库日志
docker-compose logs -f postgres
```

**常用命令**：

```bash
# 停止数据库（数据保留，下次启动数据还在）
docker-compose down

# 停止并清除所有数据（慎用！会删除所有对话记录和向量数据）
docker-compose down -v
```

启动后默认账号：

| 服务 | 地址 | 用户名 | 密码 | 说明 |
|------|------|--------|------|------|
| PostgreSQL | localhost:5600 | interview_agent | interview_agent_2026 | 数据库会自动创建并初始化表结构 |

> ⚠️ **首次启动说明**：数据库表结构会通过 `docker-compose.yml` 中挂载的 `schema.sql` 自动初始化，无需手动建表。PGVector 的 `vector_store` 表由 Spring AI 在后端启动时自动创建（需确保 `application.yml` 中 `spring.ai.vectorstore.pgvector.initialize-schema: true`）。

### 3. 构建并启动后端

```bash
# 构建项目
mvn clean package -DskipTests

# 启动后端
cd interview-agent-web
java -jar target/interview-agent-web-1.0.0-SNAPSHOT.jar
```

后端服务启动于 http://localhost:8180

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
| 后端 API | http://localhost:8180 | RESTful API |
| 接口文档 | http://localhost:8180/doc.html | Knife4j 接口文档 |

## 模型配置说明

本项目基于 **Ollama 本地部署**，所有模型均在本地运行，无需 API Key，零成本。

### 修改对话模型

编辑 `interview-agent-web/src/main/resources/application.yml`：

```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          # 切换对话模型（需先 ollama pull 对应模型）
          model: qwen3.5:4b    # 推荐
          # model: qwen3:1.7b  # 轻量
          # model: deepseek-r1:7b  # 深度推理
          temperature: 0.5      # 创造性（0~1，越高越随机）
          top-p: 0.9           # 核采样
          num-ctx: 4096        # 上下文窗口大小
```

### 修改 Embedding 模型

```yaml
spring:
  ai:
    ollama:
      embedding:
        options:
          model: bge-m3  # 中文优化，推荐不变
```

### GPU 加速

Ollama 默认使用 GPU 加速（CUDA / Metal），如需进一步优化：

```bash
# 启用 Flash Attention（提升推理速度）
# 在 Ollama 服务端设置环境变量
OLLAMA_FLASH_ATTENTION=1

# 调整上下文长度（影响显存占用和速度）
# 在 application.yml 中修改
num-ctx: 4096  # 默认值，可根据显存调整
```

## 常见问题

### Q: Ollama 连接失败？

1. 确认 Ollama 已启动：浏览器访问 http://localhost:11434，应返回 "Ollama is running"
2. 确认模型已拉取：`ollama list` 查看已安装模型
3. 确认端口未被占用：`application.yml` 中 `spring.ai.ollama.base-url` 默认为 `http://localhost:11434`

### Q: PGVector vector_store 表未自动创建？

在 `application.yml` 中确认：

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        initialize-schema: true  # 开发环境设为 true，自动创建表
```

### Q: 上传 PDF 后向量化失败？

1. 确认 `bge-m3` 模型已拉取：`ollama pull bge-m3`
2. 确认 Ollama 服务正常运行
3. 查看后端日志确认错误信息

### Q: 如何清理旧向量数据并重新导入？

1. 在前端知识库页面删除对应文档（会同步清理 PGVector 向量数据）
2. 重新上传 PDF，新数据将经过文本清洗 + 智能分块后入库

### Q: 模型推理速度慢？

1. 确认 GPU 加速是否生效：`nvidia-smi` 查看 GPU 占用
2. 启用 Flash Attention：设置环境变量 `OLLAMA_FLASH_ATTENTION=1`
3. 减小上下文窗口：`num-ctx: 2048`（牺牲长文本能力换速度）
4. 使用更小的模型：`qwen3:1.7b` 比 `qwen3.5:4b` 快 3~5 倍

## TODO

- [ ] 混合检索（Dense + Sparse），利用 BGE-M3 的多检索能力
- [ ] Re-ranking 重排序，提升检索精度
- [ ] 查询改写（Query Rewriting），优化用户查询的检索效果
- [ ] 模拟面试模式（连续出题 + 评分）
- [ ] 多模型切换（前端可选模型）
- [ ] Docker 一键部署（前后端 + 数据库 + Ollama）
- [ ] 对话历史持久化优化

