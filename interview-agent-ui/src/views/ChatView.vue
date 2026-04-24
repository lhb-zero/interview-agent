<template>
  <div class="chat-view">
    <div class="chat-toolbar">
      <div class="toolbar-left">
        <el-select v-model="domain" placeholder="选择领域" style="width: 130px" size="default">
          
          <el-option label="Java" value="java" />
          <el-option label="Python" value="python" />
          <el-option label="AI/机器学习" value="ai" />
          <el-option label="前端" value="frontend" />
          <el-option label="数据库" value="database" />
          <el-option label="系统设计" value="system" />
        </el-select>
        <el-select v-model="difficulty" placeholder="难度" style="width: 110px" size="default">
          <el-option label="基础" value="基础" />
          <el-option label="中级" value="中级" />
          <el-option label="高级" value="高级" />
        </el-select>
        <div class="toolbar-divider"></div>
        <div class="toolbar-switch">
          <el-switch v-model="ragEnabled" active-text="知识库增强" inactive-text="" />
          <el-tooltip content="开启后，AI 会从知识库中检索相关文档来增强回答" placement="top">
            <el-icon class="switch-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
        <div class="toolbar-switch">
          <el-switch v-model="thinkingEnabled" active-text="深度思考" inactive-text="" :disabled="!supportsThinking" />
          <el-tooltip v-if="!supportsThinking" content="当前模型不支持深度思考" placement="top">
            <el-icon class="switch-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
      </div>
    </div>

    <div class="chat-messages" ref="messagesContainer" @scroll="handleScroll">
      <div v-if="messages.length === 0" class="empty-chat">
        <div class="welcome-icon">
          <svg viewBox="0 0 120 120" width="80" height="80">
            <defs>
              <linearGradient id="grad1" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" style="stop-color:#409eff;stop-opacity:1" />
                <stop offset="100%" style="stop-color:#67c23a;stop-opacity:1" />
              </linearGradient>
            </defs>
            <circle cx="60" cy="60" r="56" fill="url(#grad1)" opacity="0.1" />
            <circle cx="60" cy="60" r="44" fill="url(#grad1)" opacity="0.15" />
            <path d="M40 50 Q60 35 80 50 Q80 75 60 85 Q40 75 40 50Z" fill="url(#grad1)" opacity="0.6" />
            <circle cx="52" cy="58" r="4" fill="white" />
            <circle cx="68" cy="58" r="4" fill="white" />
            <path d="M52 70 Q60 78 68 70" stroke="white" stroke-width="2.5" fill="none" stroke-linecap="round" />
          </svg>
        </div>
        <h2 class="welcome-title">面试智能助手</h2>
        <p class="welcome-desc">基于 RAG 的智能面试辅导，为你生成面试题、解析知识点</p>
        <div class="quick-actions">
          <div class="quick-card" @click="sendQuickMessage('请生成3道Java基础面试题')">
            <el-icon :size="20"><Document /></el-icon>
            <span>Java 基础题</span>
          </div>
          <div class="quick-card" @click="sendQuickMessage('总结Java线程池的核心知识点')">
            <el-icon :size="20"><Cpu /></el-icon>
            <span>线程池知识点</span>
          </div>
          <div class="quick-card" @click="sendQuickMessage('HashMap的底层原理是什么？')">
            <el-icon :size="20"><Key /></el-icon>
            <span>HashMap 原理</span>
          </div>
          <div class="quick-card" @click="sendQuickMessage('Spring Boot自动装配的原理？')">
            <el-icon :size="20"><SetUp /></el-icon>
            <span>Spring Boot 原理</span>
          </div>
        </div>
      </div>

      <div v-for="(msg, index) in messages" :key="index" :class="['message-item', msg.role]">
        <div class="message-avatar">
          <el-avatar v-if="msg.role === 'user'" :icon="UserFilled" :size="36" class="avatar-user" />
          <el-avatar v-else :icon="Monitor" :size="36" class="avatar-ai" />
        </div>
        <div class="message-content">
          <div class="message-role">{{ msg.role === 'user' ? '你' : 'AI 助手' }}</div>
          <div class="message-text" :ref="el => setMessageRef(el, index)" v-html="msg.renderedHtml"></div>
        </div>
      </div>

      <div v-if="loading" class="message-item assistant">
        <div class="message-avatar"><el-avatar :icon="Monitor" :size="36" class="avatar-ai" /></div>
        <div class="message-content">
          <div class="message-role">AI 助手</div>
          <div class="message-text typing-indicator">
            <span class="dot"></span><span class="dot"></span><span class="dot"></span>
          </div>
        </div>
      </div>
    </div>

    <div class="chat-input-wrapper">
      <div class="chat-input">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="2"
          placeholder="输入你的问题，Ctrl+Enter 发送..."
          @keydown.enter.ctrl="sendMessage"
          :disabled="loading"
          resize="none"
        />
        <el-button
          type="primary"
          :icon="Promotion"
          circle
          size="large"
          @click="sendMessage"
          :loading="loading"
          class="send-btn"
        />
      </div>
      <div class="input-hint">按 Ctrl+Enter 快速发送</div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, inject, watch } from 'vue'
import { UserFilled, Monitor, QuestionFilled, Promotion, Document, Cpu, Key, SetUp } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import request from '../utils/request'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  highlight(str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return '<pre class="hljs"><code>' + hljs.highlight(str, { language: lang, ignoreIllegals: true }).value + '</code></pre>'
      } catch (_) {}
    }
    return '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + '</code></pre>'
  }
})

const domain = ref('java')
const difficulty = ref('中级')
const ragEnabled = ref(false)
const thinkingEnabled = ref(false)
const inputMessage = ref('')
const THINKING_MODELS = ['qwen3', 'qwen3.5', 'deepseek-r1', 'deepseek-v3.1']
const currentModel = ref('qwen3.5:4b')
const supportsThinking = ref(true)

function checkThinkingSupport() {
  const modelLower = currentModel.value.toLowerCase()
  supportsThinking.value = THINKING_MODELS.some(m => modelLower.startsWith(m))
  if (!supportsThinking.value) thinkingEnabled.value = false
}
checkThinkingSupport()

const messages = ref([])
const loading = ref(false)
const messagesContainer = ref(null)
const currentSessionId = ref(null)
const isUserAtBottom = ref(true)

const messageRefs = ref({})

const activeSessionId = inject('activeSessionId', ref(null))
const loadSessions = inject('loadSessions', async () => {})
const setActiveSession = inject('setActiveSession', (id) => {})

watch(activeSessionId, (newId) => {
  if (newId !== currentSessionId.value) {
    currentSessionId.value = newId
    if (newId) loadHistoryMessages(newId)
    else { messages.value = []; messageRefs.value = {} }
  }
})

function setMessageRef(el, index) {
  if (el) messageRefs.value[index] = el
  else delete messageRefs.value[index]
}

function renderToHtml(text, streaming = false) {
  if (!text) return ''
  let cleaned = text.replace(/<think[\s\S]*?<\/think>/g, '').trim()
  if (streaming && cleaned) {
    const codeBlocks = (cleaned.match(/```/g) || []).length
    if (codeBlocks % 2 !== 0) cleaned += '\n```'
  }
  return md.render(cleaned)
}

function updateMessageContent(msgIndex, content, streaming) {
  const msg = messages.value[msgIndex]
  const renderedHtml = renderToHtml(content, streaming)
  messages.value[msgIndex] = { ...msg, content, streaming, renderedHtml }
  nextTick(() => {
    const el = messageRefs.value[msgIndex]
    if (el && el.innerHTML !== renderedHtml) el.innerHTML = renderedHtml
  })
}

function scrollToBottom(force = false) {
  nextTick(() => {
    if (messagesContainer.value && (force || isUserAtBottom.value)) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

function handleScroll() {
  const el = messagesContainer.value
  if (!el) return
  isUserAtBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

function sendQuickMessage(text) {
  inputMessage.value = text
  sendMessage()
}

async function loadHistoryMessages(sessionId) {
  try {
    const res = await request.get(`/chat/sessions/${sessionId}/messages`)
    const historyList = res.data || []
    messages.value = historyList.map(msg => ({
      role: msg.role,
      content: msg.content,
      streaming: false,
      renderedHtml: renderToHtml(msg.content, false)
    }))
    messageRefs.value = {}
    scrollToBottom(true)
  } catch (e) {
    console.error('加载历史消息失败', e)
    messages.value = []
  }
}

async function sendMessage() {
  const text = inputMessage.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', content: text, streaming: false, renderedHtml: renderToHtml(text, false) })
  inputMessage.value = ''
  loading.value = true
  isUserAtBottom.value = true
  scrollToBottom(true)

  try {
    await streamChat(text)
    await loadSessions()
  } catch (e) {
    console.error('对话错误', e)
    messages.value.push({ role: 'assistant', content: '抱歉，生成回答时出现错误，请稍后重试。', streaming: false, renderedHtml: renderToHtml('抱歉，生成回答时出现错误，请稍后重试。', false) })
  } finally {
    loading.value = false
    scrollToBottom(true)
  }
}

function extractSSEDatas(buffer) {
  let content = ''
  let remainingBuffer = ''
  const lines = buffer.split('\n')
  const endsWithNewline = buffer.endsWith('\n')
  const limit = endsWithNewline ? lines.length : lines.length - 1

  for (let i = 0; i < limit; i++) {
    const line = lines[i]
    if (line === '' || line.startsWith(':')) continue
    if (!line.startsWith('data:')) continue

    const data = line.slice(5)
    const trimmed = data.trim()
    if (!trimmed || trimmed === '[DONE]') continue
    if (trimmed.startsWith('{') && trimmed.includes('sessionId')) {
      try {
        const parsed = JSON.parse(trimmed)
        if (parsed.sessionId && !currentSessionId.value) {
          currentSessionId.value = parsed.sessionId
          setActiveSession(parsed.sessionId)
        }
      } catch (_) {}
      continue
    }

    if (trimmed) content += trimmed
    else content += '\n'
  }

  if (!endsWithNewline) remainingBuffer = lines[lines.length - 1]
  return { content, remainingBuffer }
}

async function readSSEStream(response, msgIndex) {
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let assistantContent = ''
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const { content, remainingBuffer } = extractSSEDatas(buffer)
    buffer = remainingBuffer

    if (content) {
      assistantContent += content
      updateMessageContent(msgIndex, assistantContent, true)
      scrollToBottom()
    }
  }

  buffer += decoder.decode(new Uint8Array(0), { stream: false })
  if (buffer) {
    const finalParts = []
    for (const line of buffer.split('\n')) {
      if (!line.startsWith('data:')) continue
      const data = line.slice(5).trim()
      if (!data || data === '[DONE]') continue
      if (data.startsWith('{') && data.includes('sessionId')) continue
      finalParts.push(data)
    }
    const finalContent = finalParts.join('\n')
    if (finalContent) {
      assistantContent += finalContent
    }
  }

  updateMessageContent(msgIndex, assistantContent, false)
  return assistantContent
}

async function streamChat(text) {
  const params = new URLSearchParams({
    message: text,
    domain: domain.value,
    difficulty: difficulty.value,
    thinkingEnabled: thinkingEnabled.value
  })
  if (currentSessionId.value) params.append('sessionId', currentSessionId.value)

  const baseUrl = ragEnabled.value ? '/api/rag/chat/stream' : '/api/chat/stream'
  const response = await fetch(`${baseUrl}?${params.toString()}`)
  if (!response.ok) throw new Error('请求失败')

  messages.value.push({ role: 'assistant', content: '', streaming: false, renderedHtml: '' })
  await readSSEStream(response, messages.value.length - 1)
}
</script>

<style scoped>
.chat-view {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
}

.chat-toolbar {
  display: flex;
  align-items: center;
  padding: 10px 24px;
  background: #fff;
  border-bottom: 1px solid #e8ecf0;
  box-shadow: 0 1px 6px rgba(0,0,0,.04);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar-divider {
  width: 1px;
  height: 24px;
  background: #e0e0e0;
  margin: 0 6px;
}

.toolbar-switch {
  display: flex;
  align-items: center;
  gap: 4px;
}

.switch-help {
  color: #909399;
  cursor: help;
  font-size: 14px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
  scroll-behavior: smooth;
}

.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 40px;
}

.welcome-icon {
  margin-bottom: 20px;
  animation: floatUp 3s ease-in-out infinite;
}

@keyframes floatUp {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

.welcome-title {
  font-size: 26px;
  font-weight: 700;
  background: linear-gradient(135deg, #409eff, #67c23a);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin-bottom: 8px;
}

.welcome-desc {
  color: #909399;
  font-size: 15px;
  margin-bottom: 32px;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  max-width: 460px;
  width: 100%;
}

.quick-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  background: #fff;
  border: 1px solid #e8ecf0;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.25s ease;
  font-size: 14px;
  color: #606266;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
}

.quick-card:hover {
  border-color: #409eff;
  color: #409eff;
  box-shadow: 0 4px 12px rgba(64,158,255,.12);
  transform: translateY(-2px);
}

.quick-card .el-icon {
  color: #409eff;
  flex-shrink: 0;
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  padding: 0 24px;
  animation: msgFadeIn .35s ease;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
  margin-top: 2px;
}

.avatar-user {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
}

.avatar-ai {
  background: linear-gradient(135deg, #409eff, #67c23a) !important;
}

.message-content {
  max-width: 72%;
  min-width: 0;
}

.message-role {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
  font-weight: 500;
}

.message-item.user .message-role {
  text-align: right;
}

.message-text {
  background: #fff;
  padding: 14px 18px;
  border-radius: 16px;
  border-top-left-radius: 4px;
  font-size: 14px;
  line-height: 1.75;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
  word-wrap: break-word;
  overflow-wrap: break-word;
}

.message-item.user .message-text {
  background: linear-gradient(135deg, #409eff, #5b8def);
  color: #fff;
  border-top-left-radius: 16px;
  border-top-right-radius: 4px;
}

.message-item.user .message-text :deep(pre),
.message-item.user .message-text :deep(code) {
  background: rgba(255,255,255,.15);
}

.message-text :deep(pre) {
  background: #1e1e2e;
  padding: 16px;
  border-radius: 10px;
  overflow-x: auto;
  margin: 10px 0;
  position: relative;
}

.message-text :deep(pre.hljs) {
  background: #1e1e2e;
  padding: 16px;
  border-radius: 10px;
}

.message-text :deep(pre code) {
  font-family: 'JetBrains Mono', 'Fira Code', Menlo, Monaco, 'Courier New', monospace;
  font-size: 13px;
  color: #cdd6f4;
  line-height: 1.6;
}

.message-text :deep(code) {
  font-family: 'JetBrains Mono', 'Fira Code', Menlo, Monaco, 'Courier New', monospace;
  font-size: 13px;
  background: #f0f2f5;
  padding: 2px 6px;
  border-radius: 4px;
  color: #e74c3c;
}

.message-text :deep(h1),
.message-text :deep(h2),
.message-text :deep(h3) {
  margin: 14px 0 8px;
  color: #303133;
}

.message-text :deep(h1) { font-size: 18px; }
.message-text :deep(h2) { font-size: 16px; }
.message-text :deep(h3) { font-size: 15px; }

.message-text :deep(ul),
.message-text :deep(ol) {
  padding-left: 22px;
  margin: 6px 0;
}

.message-text :deep(li) {
  margin: 4px 0;
  line-height: 1.7;
}

.message-text :deep(p) {
  margin: 6px 0;
}

.message-text :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 10px 0;
  border-radius: 8px;
  overflow: hidden;
}

.message-text :deep(th),
.message-text :deep(td) {
  border: 1px solid #e8ecf0;
  padding: 10px 14px;
  text-align: left;
  font-size: 13px;
}

.message-text :deep(th) {
  background: #f5f7fa;
  font-weight: 600;
}

.message-text :deep(blockquote) {
  border-left: 4px solid #409eff;
  padding: 10px 16px;
  margin: 10px 0;
  background: #f0f7ff;
  color: #606266;
  border-radius: 0 8px 8px 0;
}

.message-text :deep(strong) {
  color: #303133;
  font-weight: 600;
}

.typing-indicator {
  display: flex;
  gap: 5px;
  padding: 18px;
  align-items: center;
}

.typing-indicator .dot {
  width: 8px;
  height: 8px;
  background: #409eff;
  border-radius: 50%;
  animation: typingBounce 1.4s infinite ease-in-out;
}

.typing-indicator .dot:nth-child(2) { animation-delay: .2s; }
.typing-indicator .dot:nth-child(3) { animation-delay: .4s; }

@keyframes typingBounce {
  0%, 80%, 100% { transform: scale(.5); opacity: .4; }
  40% { transform: scale(1); opacity: 1; }
}

@keyframes msgFadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.chat-input-wrapper {
  background: #fff;
  border-top: 1px solid #e8ecf0;
  padding: 16px 24px 12px;
  box-shadow: 0 -2px 8px rgba(0,0,0,.03);
}

.chat-input {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  max-width: 860px;
  margin: 0 auto;
}

.chat-input :deep(.el-textarea__inner) {
  resize: none;
  border-radius: 12px;
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.6;
  border: 1px solid #e0e0e0;
  transition: border-color .2s;
}

.chat-input :deep(.el-textarea__inner):focus {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64,158,255,.1);
}

.send-btn {
  flex-shrink: 0;
  width: 44px !important;
  height: 44px !important;
  border-radius: 12px !important;
  transition: all .2s;
}

.send-btn:hover {
  transform: scale(1.05);
}

.input-hint {
  text-align: center;
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 6px;
}
</style>
