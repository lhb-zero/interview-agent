-- =====================================================
-- 面试智能助手 数据库初始化脚本
-- 数据库：PostgreSQL 16 + PGVector 扩展
-- =====================================================

-- 启用 PGVector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- =====================================================
-- 0. Spring AI PGVector 默认向量表（VectorStore）
--
-- Spring AI PgVectorVectorStore 默认使用 public.vector_store 表，字段为：
-- id, content, metadata(jsonb), embedding(vector)
-- （与日志中的 INSERT 语句保持一致）
-- =====================================================
CREATE TABLE IF NOT EXISTS vector_store (
    id        VARCHAR(255) PRIMARY KEY,
    content   TEXT,
    metadata  JSONB,
    embedding vector(1024),
    text_hash VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_vector_store_embedding_hnsw
    ON vector_store
    USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_vector_store_text_hash
    ON vector_store(text_hash);

-- =====================================================
-- 1. 知识库文档表
-- =====================================================
CREATE TABLE IF NOT EXISTS knowledge_document (
    id          BIGSERIAL       PRIMARY KEY,
    title       VARCHAR(255)    NOT NULL,
    domain      VARCHAR(50)     NOT NULL,
    file_type   VARCHAR(20),
    file_path   VARCHAR(500),
    chunk_count INT             DEFAULT 0,
    status      VARCHAR(20)     DEFAULT 'ACTIVE',
    created_at  TIMESTAMP       DEFAULT NOW(),
    updated_at  TIMESTAMP       DEFAULT NOW()
);

COMMENT ON TABLE  knowledge_document IS '知识库文档表';
COMMENT ON COLUMN knowledge_document.title IS '文档标题';
COMMENT ON COLUMN knowledge_document.domain IS '技术领域：java/python/ai等';
COMMENT ON COLUMN knowledge_document.file_type IS '文件类型：pdf/md/txt';
COMMENT ON COLUMN knowledge_document.file_path IS '文件存储路径';
COMMENT ON COLUMN knowledge_document.chunk_count IS '分段数量';
COMMENT ON COLUMN knowledge_document.status IS '状态：ACTIVE/PROCESSING/DELETED';

CREATE INDEX idx_knowledge_document_domain ON knowledge_document(domain);
CREATE INDEX idx_knowledge_document_status ON knowledge_document(status);

-- =====================================================
-- 2. 对话会话表
-- =====================================================
CREATE TABLE IF NOT EXISTS chat_session (
    id          BIGSERIAL       PRIMARY KEY,
    session_id  VARCHAR(64)     NOT NULL,
    title       VARCHAR(255),
    domain      VARCHAR(50),
    model_name  VARCHAR(50),
    created_at  TIMESTAMP       DEFAULT NOW()
);

COMMENT ON TABLE  chat_session IS '对话会话表';
COMMENT ON COLUMN chat_session.session_id IS '会话ID（UUID）';
COMMENT ON COLUMN chat_session.domain IS '面试领域';
COMMENT ON COLUMN chat_session.model_name IS '使用的模型名称';

CREATE INDEX idx_chat_session_session_id ON chat_session(session_id);
CREATE INDEX idx_chat_session_domain ON chat_session(domain);

-- =====================================================
-- 3. 对话消息表
-- =====================================================
CREATE TABLE IF NOT EXISTS chat_message (
    id          BIGSERIAL       PRIMARY KEY,
    session_id  VARCHAR(64)     NOT NULL,
    role        VARCHAR(20)     NOT NULL,
    content     TEXT            NOT NULL,
    tokens_used INT,
    created_at  TIMESTAMP       DEFAULT NOW()
);

COMMENT ON TABLE  chat_message IS '对话消息表';
COMMENT ON COLUMN chat_message.session_id IS '关联会话ID';
COMMENT ON COLUMN chat_message.role IS '角色：user/assistant/system';
COMMENT ON COLUMN chat_message.content IS '消息内容';
COMMENT ON COLUMN chat_message.tokens_used IS 'Token消耗';

CREATE INDEX idx_chat_message_session_id ON chat_message(session_id);

-- =====================================================
-- 4. 面试题收藏表
-- =====================================================
CREATE TABLE IF NOT EXISTS favorite_question (
    id          BIGSERIAL       PRIMARY KEY,
    session_id  VARCHAR(64),
    question    TEXT            NOT NULL,
    answer      TEXT,
    domain      VARCHAR(50),
    difficulty  VARCHAR(20),
    created_at  TIMESTAMP       DEFAULT NOW()
);

COMMENT ON TABLE  favorite_question IS '面试题收藏表';
COMMENT ON COLUMN favorite_question.question IS '面试题内容';
COMMENT ON COLUMN favorite_question.answer IS '参考答案';
COMMENT ON COLUMN favorite_question.domain IS '技术领域';
COMMENT ON COLUMN favorite_question.difficulty IS '难度级别';

CREATE INDEX idx_favorite_question_session_id ON favorite_question(session_id);
CREATE INDEX idx_favorite_question_domain ON favorite_question(domain);
