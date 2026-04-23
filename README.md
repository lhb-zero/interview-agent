# 面试智能助手 (Interview Agent)

> 基于 Spring Boot 3 + Spring AI 1.0 + Ollama 的面试智能助手，支持 RAG 检索增强生成

## 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 后端 | Spring Boot 3.3 + Spring AI 1.0 | 企业级 AI 应用框架 |
| LLM | Ollama qwen3.5:4b | 本地运行，免费 |
| Embedding | Ollama bge-m3 | 中文优化，多语言多粒度 |
| 数据库 | PostgreSQL 16 + PGVector | 关系库 + 向量库一体 |
| 前端 | Vue 3 + Vite + Element Plus | 轻量现代化 |

## 快速开始

### 1. 环境准备

- JDK 17+
- Maven 3.9+
- Docker Desktop（用于运行 PostgreSQL）
- [Ollama](https://ollama.ai)（用于运行 LLM）

### 2. 安装 Ollama 模型

```bash
# 安装 Chat 模型
ollama pull qwen3.5:4b

# 安装 Embedding 模型（中文优化）
ollama pull bge-m3
```

### 3. 启动 PostgreSQL + PGVector

```bash
docker-compose up -d
```

### 4. 构建并启动后端

```bash
mvn clean package -DskipTests
cd interview-agent-web
java -jar target/interview-agent-web-1.0.0-SNAPSHOT.jar
```

### 5. 启动前端

```bash
cd interview-agent-ui
npm install
npm run dev
```

### 6. 访问

- 前端页面：http://localhost:5173
- 后端 API：http://localhost:8080
- 接口文档：http://localhost:8080/doc.html

## 项目结构

```
interview-agent/
├── interview-agent-common/    # 公共模块
├── interview-agent-model/     # 数据模型
├── interview-agent-dao/       # 数据访问层
├── interview-agent-service/   # 业务逻辑层
├── interview-agent-web/       # Web 层（启动入口）
├── interview-agent-ui/        # Vue 3 前端
├── docs/                      # 项目文档
└── docker-compose.yml         # Docker 编排
```

## 核心功能

- **智能对话**：输入岗位/方向，AI 生成面试题与知识点
- **流式响应**：SSE 推送，逐字显示
- **RAG 检索增强**：上传知识文档 → 自动分块 → 向量化 → 检索增强
- **Tool Calling**：LLM 自主调用知识库检索工具
- **Prompt 外部化管理**：.st 模板文件，解耦 Prompt 与代码
