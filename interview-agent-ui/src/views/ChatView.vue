<template>
  <div class="chat-view">
    <!-- Toolbar -->
    <header class="chat-toolbar">
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
        <span class="toolbar-sep"></span>
        <label class="toolbar-toggle">
          <el-switch v-model="ragEnabled" size="small" />
          <el-tooltip content="开启后，AI 会从知识库中检索相关文档来增强回答" placement="top">
            <span class="toggle-label">知识库增强</span>
          </el-tooltip>
        </label>
        <label class="toolbar-toggle">
          <el-switch v-model="thinkingEnabled" size="small" :disabled="!supportsThinking" />
          <el-tooltip :content="supportsThinking ? '模型将展示推理过程' : '当前模型不支持深度思考'" placement="top">
            <span class="toggle-label" :class="{ disabled: !supportsThinking }">深度思考</span>
          </el-tooltip>
        </label>
      </div>
    </header>

    <!-- Messages -->
    <main class="chat-messages" ref="messagesContainer" @scroll="handleScroll">
      <!-- Welcome screen -->
      <div v-if="messages.length === 0" class="welcome">
        <div class="welcome-icon">
          <svg viewBox="0 0 120 120" width="72" height="72">
            <defs>
              <linearGradient id="welcomeGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" style="stop-color:#409eff;stop-opacity:1" />
                <stop offset="100%" style="stop-color:#67c23a;stop-opacity:1" />
              </linearGradient>
            </defs>
            <circle cx="60" cy="60" r="56" fill="url(#welcomeGrad)" opacity="0.08" />
            <circle cx="60" cy="60" r="42" fill="url(#welcomeGrad)" opacity="0.12" />
            <path d="M40 50 Q60 35 80 50 Q80 75 60 85 Q40 75 40 50Z" fill="url(#welcomeGrad)" opacity="0.6" />
            <circle cx="52" cy="58" r="4" fill="white" />
            <circle cx="68" cy="58" r="4" fill="white" />
            <path d="M52 70 Q60 78 68 70" stroke="white" stroke-width="2.5" fill="none" stroke-linecap="round" />
          </svg>
        </div>
        <h1 class="welcome-title">面试智能助手</h1>
        <p class="welcome-sub">基于 RAG 检索增强的智能面试辅导，支持知识库问答与面试题生成</p>
        <div class="quick-grid">
          <button class="quick-card" v-for="q in quickQuestions" :key="q.text" @click="sendQuickMessage(q.text)">
            <span class="quick-icon">{{ q.icon }}</span>
            <span class="quick-label">{{ q.label }}</span>
          </button>
        </div>
      </div>

      <!-- Message list -->
      <div v-for="(msg, index) in messages" :key="index" :class="['msg', msg.role]">
        <div class="msg-avatar">
          <div v-if="msg.role === 'user'" class="avatar avatar-user">你</div>
          <div v-else class="avatar avatar-ai">AI</div>
        </div>
        <div class="msg-body">
          <div class="msg-role">{{ msg.role === 'user' ? '你' : 'AI 助手' }}</div>
          <div
            class="msg-bubble"
            :class="{ 'bubble-ai': msg.role !== 'user', 'bubble-user': msg.role === 'user' }"
            :ref="el => setMsgRef(el, index)"
            v-html="msg.renderedHtml"
          ></div>
        </div>
      </div>

      <!-- Typing indicator -->
      <div v-if="loading && !streamHasContent" class="msg assistant">
        <div class="msg-avatar"><div class="avatar avatar-ai">AI</div></div>
        <div class="msg-body">
          <div class="msg-role">AI 助手</div>
          <div class="msg-bubble bubble-ai typing-bubble">
            <span class="typing-dot"></span>
            <span class="typing-dot"></span>
            <span class="typing-dot"></span>
          </div>
        </div>
      </div>
    </main>

    <!-- Input area -->
    <footer class="chat-input-area">
      <div class="input-row">
        <textarea
          ref="inputEl"
          v-model="inputMessage"
          class="input-textarea"
          placeholder="输入你的问题..."
          rows="1"
          @input="autoResize"
          @keydown.enter.ctrl.prevent="sendMessage"
          @keydown.enter.meta.prevent="sendMessage"
          :disabled="loading"
        ></textarea>
        <button class="send-btn" :class="{ active: canSend }" @click="sendMessage" :disabled="!canSend">
          <svg v-if="!loading" viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
          </svg>
          <span v-else class="send-loading"></span>
        </button>
      </div>
      <div class="input-hint">Ctrl + Enter 发送</div>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, inject, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import request from '../utils/request'

/* ── Markdown-it setup ── */
const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: true,
  highlight(str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return '<pre class="hljs"><code>' + hljs.highlight(str, { language: lang, ignoreIllegals: true }).value + '</code></pre>'
      } catch (_) {}
    }
    return '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + '</code></pre>'
  }
})

/* ── State ── */
const domain = ref('java')
const difficulty = ref('中级')
const ragEnabled = ref(false)
const thinkingEnabled = ref(false)
const inputMessage = ref('')
const inputEl = ref(null)
const messages = ref([])
const loading = ref(false)
const messagesContainer = ref(null)
const currentSessionId = ref(null)
const isUserAtBottom = ref(true)
const msgRefs = ref({})

const THINKING_MODELS = ['qwen3', 'qwen3.5', 'deepseek-r1', 'deepseek-v3.1']
const currentModel = ref('qwen3.5:4b')
const supportsThinking = computed(() => THINKING_MODELS.some(m => currentModel.value.toLowerCase().startsWith(m)))
if (!supportsThinking.value) thinkingEnabled.value = false

const quickQuestions = [
  { icon: '📝', label: 'Java 基础题', text: '请生成3道Java基础面试题' },
  { icon: '🧵', label: '线程池知识点', text: '总结Java线程池的核心知识点' },
  { icon: '🔑', label: 'HashMap 原理', text: 'HashMap的底层原理是什么？' },
  { icon: '⚙️', label: 'Spring Boot 原理', text: 'Spring Boot自动装配的原理？' }
]

const streamHasContent = ref(false)

const canSend = computed(() => {
  const text = inputMessage.value.trim()
  return text.length > 0 && !loading.value
})

/* ── Inject from parent ── */
const activeSessionId = inject('activeSessionId', ref(null))
const loadSessions = inject('loadSessions', async () => {})
const setActiveSession = inject('setActiveSession', () => {})

watch(activeSessionId, (newId) => {
  if (newId !== currentSessionId.value) {
    currentSessionId.value = newId
    if (newId) loadHistoryMessages(newId)
    else { messages.value = []; msgRefs.value = {} }
  }
})

/* ── Refs & event delegation ── */
function setMsgRef(el, index) {
  if (el) msgRefs.value[index] = el
  else delete msgRefs.value[index]
}

function setupCopyDelegation() {
  if (!messagesContainer.value) return
  messagesContainer.value.addEventListener('click', (e) => {
    const btn = e.target.closest('.code-copy-btn')
    if (!btn) return
    const code = btn.getAttribute('data-code')
    if (!code) return
    navigator.clipboard.writeText(code).then(() => {
      btn.textContent = '已复制'
      setTimeout(() => { btn.textContent = '复制' }, 1500)
    }).catch(() => {})
  })
}

onMounted(() => { setupCopyDelegation() })

/* ── Utilities ── */
function autoResize() {
  const el = inputEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}

function scrollToBottom(force = false) {
  nextTick(() => {
    const el = messagesContainer.value
    if (el && (force || isUserAtBottom.value)) {
      el.scrollTop = el.scrollHeight
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

/* ── Markdown rendering ── */
function renderToHtml(text, streaming = false) {
  if (!text) return ''
  // Strip <think> blocks
  let cleaned = text.replace(/<think>[\s\S]*?<\/think>/g, '').trim()
  if (!cleaned) return ''

  if (streaming) {
    // Auto-close unfinished fenced code blocks
    const fenceCount = (cleaned.match(/```/g) || []).length
    if (fenceCount % 2 !== 0) cleaned += '\n```'
    // Strip incomplete HTML tags at the end
    cleaned = cleaned.replace(/<(strong|em|b|i|del|code|a)[^>]*$/, '')
  }

  return md.render(cleaned)
}

function postProcessCodeBlocks(html) {
  return html.replace(/<pre class="hljs"><code>([\s\S]*?)<\/code><\/pre>/g, (_, codeContent) => {
    return `<div class="code-block-wrapper"><div class="code-block-tools"><span class="code-lang-label"></span><button class="code-copy-btn" data-code="${encodeURIComponent(codeContent.replace(/<[^>]+>/g, ''))}">复制</button></div><pre class="hljs"><code>${codeContent}</code></pre></div>`
  })
}

function updateMsg(idx, content, streaming) {
  const msg = messages.value[idx]
  const rawHtml = renderToHtml(content, streaming)
  const renderedHtml = postProcessCodeBlocks(rawHtml)
  messages.value[idx] = { ...msg, content, streaming, renderedHtml }
  nextTick(() => {
    const el = msgRefs.value[idx]
    if (el && el.innerHTML !== renderedHtml) el.innerHTML = renderedHtml
  })
}

/* ── History loading ── */
async function loadHistoryMessages(sessionId) {
  try {
    const res = await request.get(`/chat/sessions/${sessionId}/messages`)
    const list = res.data || []
    messages.value = list.map(msg => {
      const rawHtml = renderToHtml(msg.content, false)
      return { role: msg.role, content: msg.content, streaming: false, renderedHtml: postProcessCodeBlocks(rawHtml) }
    })
    msgRefs.value = {}
    scrollToBottom(true)
  } catch (e) {
    console.error('加载历史消息失败', e)
    messages.value = []
  }
}

/* ── Send message ── */
async function sendMessage() {
  const text = inputMessage.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', content: text, streaming: false, renderedHtml: postProcessCodeBlocks(renderToHtml(text, false)) })
  inputMessage.value = ''
  loading.value = true
  streamHasContent.value = false
  isUserAtBottom.value = true
  nextTick(() => { autoResize(); scrollToBottom(true) })

  try {
    await streamChat(text)
    // 流式结束后，从服务器重新加载消息，确保 Markdown 完整渲染
    if (currentSessionId.value) {
      await loadHistoryMessages(currentSessionId.value)
    }
    await loadSessions()
  } catch (e) {
    console.error('对话错误', e)
    const errMsg = '抱歉，生成回答时出现错误，请稍后重试。'
    messages.value.push({ role: 'assistant', content: errMsg, streaming: false, renderedHtml: `<p>${errMsg}</p>` })
  } finally {
    loading.value = false
    scrollToBottom(true)
  }
}

/* ── SSE streaming ── */
function extractSSEDatas(buffer) {
  let content = ''
  let remainingBuffer = ''
  const lines = buffer.split('\n')
  const endsWithNewline = buffer.endsWith('\n')
  const limit = endsWithNewline ? lines.length : lines.length - 1

  let dataLineCount = 0

  for (let i = 0; i < limit; i++) {
    const line = lines[i]

    if (line === '') {
      // Event boundary: join collected data lines with \n
      if (dataLineCount > 1) content += '\n'
      dataLineCount = 0
      continue
    }
    if (line.startsWith(':')) continue
    if (!line.startsWith('data:')) continue

    const data = line.slice(5).trim()
    if (!data || data === '[DONE]') continue

    // Session ID control message
    if (data.startsWith('{') && data.includes('sessionId')) {
      try {
        const parsed = JSON.parse(data)
        if (parsed.sessionId && !currentSessionId.value) {
          currentSessionId.value = parsed.sessionId
          setActiveSession(parsed.sessionId)
        }
      } catch (_) {}
      continue
    }

    // Content data
    if (dataLineCount > 0) content += '\n'
    content += data
    dataLineCount++
  }

  // Handle last data line at event boundary if buffer ends with newline
  if (endsWithNewline && dataLineCount > 1) content += '\n'

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
      streamHasContent.value = true
      updateMsg(msgIndex, assistantContent, true)
      scrollToBottom()
    }
  }

  // Flush remaining buffer
  buffer += decoder.decode(new Uint8Array(0), { stream: false })
  if (buffer.trim()) {
    for (const line of buffer.split('\n')) {
      if (!line.startsWith('data:')) continue
      const data = line.slice(5).trim()
      if (!data || data === '[DONE]') continue
      if (data.startsWith('{') && data.includes('sessionId')) continue
      assistantContent += data
    }
  }

  updateMsg(msgIndex, assistantContent, false)
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

  const url = ragEnabled.value ? '/api/rag/chat/stream' : '/api/chat/stream'
  const response = await fetch(`${url}?${params.toString()}`)
  if (!response.ok) throw new Error('请求失败')

  messages.value.push({ role: 'assistant', content: '', streaming: false, renderedHtml: '' })
  await readSSEStream(response, messages.value.length - 1)
}
</script>

<style scoped>
/* ── Layout ── */
.chat-view {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f6fa;
}

/* ── Toolbar ── */
.chat-toolbar {
  display: flex;
  align-items: center;
  padding: 10px 24px;
  background: #fff;
  border-bottom: 1px solid #ebedf0;
  flex-shrink: 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-sep {
  width: 1px;
  height: 20px;
  background: #e0e3e8;
}

.toolbar-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  user-select: none;
}

.toggle-label {
  font-size: 13px;
  color: #606266;
  transition: color .2s;
}

.toggle-label.disabled {
  color: #c0c4cc;
  cursor: not-allowed;
}

/* ── Messages area ── */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
}

/* ── Welcome screen ── */
.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 40px 24px;
}

.welcome-icon {
  margin-bottom: 16px;
  animation: welcomeFloat 3s ease-in-out infinite;
}

@keyframes welcomeFloat {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

.welcome-title {
  font-size: 26px;
  font-weight: 700;
  background: linear-gradient(135deg, #409eff, #67c23a);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin-bottom: 8px;
}

.welcome-sub {
  color: #909399;
  font-size: 14px;
  margin-bottom: 36px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  max-width: 440px;
  width: 100%;
}

.quick-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #ebedf0;
  border-radius: 12px;
  cursor: pointer;
  transition: all .25s ease;
  font-size: 14px;
  color: #4e5969;
  text-align: left;
}

.quick-card:hover {
  border-color: #409eff;
  color: #409eff;
  box-shadow: 0 4px 14px rgba(64, 158, 255, .1);
  transform: translateY(-2px);
}

.quick-icon {
  font-size: 18px;
  flex-shrink: 0;
  width: 28px;
  text-align: center;
}

.quick-label {
  font-weight: 500;
}

/* ── Messages ── */
.msg {
  display: flex;
  gap: 12px;
  padding: 0 24px 20px;
  animation: msgIn .3s ease;
}

@keyframes msgIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.msg.user {
  flex-direction: row-reverse;
}

.msg-avatar {
  flex-shrink: 0;
  padding-top: 22px;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
}

.avatar-user {
  background: linear-gradient(135deg, #667eea, #764ba2);
}

.avatar-ai {
  background: linear-gradient(135deg, #409eff, #36d399);
}

.msg-body {
  max-width: 76%;
  min-width: 0;
}

.msg-role {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
  font-weight: 500;
  padding: 0 2px;
}

.msg.user .msg-role {
  text-align: right;
}

/* ── Message bubbles ── */
.msg-bubble {
  padding: 14px 18px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.75;
  word-wrap: break-word;
  overflow-wrap: break-word;
}

.bubble-ai {
  background: #fff;
  border: 1px solid #ebedf0;
  border-top-left-radius: 4px;
  color: #303133;
}

.bubble-user {
  background: linear-gradient(135deg, #409eff, #5b8def);
  color: #fff;
  border-top-right-radius: 4px;
}

/* ── Typing indicator ── */
.typing-bubble {
  display: flex;
  gap: 6px;
  padding: 16px 20px;
  align-items: center;
}

.typing-dot {
  width: 7px;
  height: 7px;
  background: #409eff;
  border-radius: 50%;
  animation: typingPulse 1.4s infinite ease-in-out;
}

.typing-dot:nth-child(2) { animation-delay: .2s; }
.typing-dot:nth-child(3) { animation-delay: .4s; }

@keyframes typingPulse {
  0%, 80%, 100% { transform: scale(.5); opacity: .3; }
  40% { transform: scale(1); opacity: 1; }
}

/* ── Markdown content styles (inside bubbles) ── */
.msg-bubble :deep(p) {
  margin: 6px 0;
}

.msg-bubble :deep(strong) {
  color: #1d2129;
  font-weight: 600;
}

.bubble-user :deep(strong) {
  color: #fff;
}

.msg-bubble :deep(h1),
.msg-bubble :deep(h2),
.msg-bubble :deep(h3) {
  margin: 16px 0 8px;
  color: #1d2129;
  font-weight: 600;
}

.msg-bubble :deep(h1) { font-size: 18px; }
.msg-bubble :deep(h2) { font-size: 16px; }
.msg-bubble :deep(h3) { font-size: 15px; }

.msg-bubble :deep(ul),
.msg-bubble :deep(ol) {
  padding-left: 22px;
  margin: 6px 0;
}

.msg-bubble :deep(li) {
  margin: 4px 0;
  line-height: 1.7;
}

.msg-bubble :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 10px 0;
  font-size: 13px;
}

.msg-bubble :deep(th),
.msg-bubble :deep(td) {
  border: 1px solid #e5e6eb;
  padding: 8px 12px;
  text-align: left;
}

.msg-bubble :deep(th) {
  background: #f7f8fa;
  font-weight: 600;
  color: #1d2129;
}

.msg-bubble :deep(blockquote) {
  border-left: 3px solid #409eff;
  padding: 8px 14px;
  margin: 8px 0;
  background: #f0f6ff;
  color: #4e5969;
  border-radius: 0 6px 6px 0;
}

.msg-bubble :deep(hr) {
  border: none;
  border-top: 1px solid #e5e6eb;
  margin: 12px 0;
}

.msg-bubble :deep(img) {
  max-width: 100%;
  border-radius: 8px;
}

/* Inline code */
.msg-bubble :deep(code) {
  font-family: 'JetBrains Mono', 'Fira Code', Menlo, Monaco, 'Courier New', monospace;
  font-size: 13px;
  background: #f0f2f5;
  padding: 2px 6px;
  border-radius: 4px;
  color: #c7254e;
}

.bubble-user :deep(code) {
  background: rgba(255, 255, 255, .18);
  color: #fff;
}

/* Code block wrapper */
.msg-bubble :deep(.code-block-wrapper) {
  position: relative;
  margin: 10px 0;
  border-radius: 10px;
  overflow: hidden;
}

.msg-bubble :deep(.code-block-tools) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 14px;
  background: #282a36;
  border-bottom: 1px solid #383a4e;
}

.msg-bubble :deep(.code-lang-label) {
  font-size: 11px;
  color: #7a7f8e;
  font-family: 'JetBrains Mono', monospace;
  text-transform: uppercase;
  letter-spacing: .5px;
}

.msg-bubble :deep(.code-copy-btn) {
  font-size: 11px;
  color: #7a7f8e;
  background: none;
  border: 1px solid #383a4e;
  border-radius: 4px;
  padding: 2px 10px;
  cursor: pointer;
  transition: all .2s;
  line-height: 1.5;
}

.msg-bubble :deep(.code-copy-btn:hover) {
  color: #cdd0d8;
  border-color: #5a5e72;
}

.msg-bubble :deep(.code-block-wrapper > pre) {
  margin: 0;
  border-radius: 0;
  padding: 14px 16px;
  background: #282a36;
}

.msg-bubble :deep(.code-block-wrapper > pre code) {
  font-family: 'JetBrains Mono', 'Fira Code', Menlo, Monaco, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.65;
  color: #f8f8f2;
  background: none;
  padding: 0;
  border-radius: 0;
}

/* ── Input area ── */
.chat-input-area {
  background: #fff;
  border-top: 1px solid #ebedf0;
  padding: 14px 24px 10px;
  flex-shrink: 0;
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  max-width: 860px;
  margin: 0 auto;
}

.input-textarea {
  flex: 1;
  resize: none;
  border: 1px solid #dcdfe6;
  border-radius: 12px;
  padding: 10px 14px;
  font-size: 14px;
  font-family: inherit;
  line-height: 1.6;
  color: #303133;
  background: #fff;
  outline: none;
  transition: border-color .2s, box-shadow .2s;
  min-height: 40px;
  max-height: 120px;
}

.input-textarea:focus {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, .1);
}

.input-textarea:disabled {
  background: #f5f7fa;
  cursor: not-allowed;
}

.input-textarea::placeholder {
  color: #c0c4cc;
}

.send-btn {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  border: none;
  background: #dcdfe6;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all .2s;
}

.send-btn.active {
  background: linear-gradient(135deg, #409eff, #5b8def);
  box-shadow: 0 2px 8px rgba(64, 158, 255, .25);
}

.send-btn.active:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 14px rgba(64, 158, 255, .35);
}

.send-btn:disabled {
  cursor: not-allowed;
  transform: none;
}

.send-loading {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, .3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin .8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.input-hint {
  text-align: center;
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 6px;
}

/* ── Scrollbar ── */
.chat-messages::-webkit-scrollbar {
  width: 6px;
}

.chat-messages::-webkit-scrollbar-track {
  background: transparent;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: #dcdfe6;
  border-radius: 3px;
}

.chat-messages::-webkit-scrollbar-thumb:hover {
  background: #c0c4cc;
}

/* ── Responsive ── */
@media (max-width: 768px) {
  .chat-toolbar { padding: 8px 14px; }
  .toolbar-left { gap: 8px; }
  .msg { padding: 0 14px 16px; }
  .msg-body { max-width: 85%; }
  .chat-input-area { padding: 10px 14px 8px; }
  .quick-grid { grid-template-columns: 1fr; max-width: 280px; }
}
</style>
