<template>
  <div class="chat-view">
    <!-- 顶部工具栏 -->
    <div class="chat-toolbar">
      <el-select v-model="domain" placeholder="选择领域" style="width: 140px" size="default">
        <el-option label="Java" value="Java" />
        <el-option label="Python" value="Python" />
        <el-option label="AI/机器学习" value="AI" />
        <el-option label="前端" value="Frontend" />
        <el-option label="数据库" value="Database" />
        <el-option label="系统设计" value="System" />
      </el-select>
      <el-select v-model="difficulty" placeholder="难度" style="width: 120px; margin-left: 12px" size="default">
        <el-option label="基础" value="基础" />
        <el-option label="中级" value="中级" />
        <el-option label="高级" value="高级" />
      </el-select>
      <el-switch v-model="ragEnabled" active-text="RAG" inactive-text="" style="margin-left: 16px" />
      <el-switch v-model="thinkingEnabled" active-text="深度思考" inactive-text="" style="margin-left: 16px" :disabled="!supportsThinking" />
      <el-tooltip v-if="!supportsThinking" content="当前模型不支持深度思考" placement="top">
        <el-icon style="margin-left: 4px; color: #909399; cursor: help;"><QuestionFilled /></el-icon>
      </el-tooltip>
    </div>

    <!-- 消息列表 -->
    <div class="chat-messages" ref="messagesContainer" @scroll="handleScroll">
      <div v-if="messages.length === 0" class="empty-chat">
        <el-icon :size="48" color="#409eff"><ChatDotRound /></el-icon>
        <h3>面试智能助手</h3>
        <p>输入你的问题，AI 为你生成面试题和知识点</p>
        <div class="quick-actions">
          <el-button @click="sendQuickMessage('请生成3道Java基础面试题')">Java 基础题</el-button>
          <el-button @click="sendQuickMessage('总结Java线程池的核心知识点')">线程池知识点</el-button>
          <el-button @click="sendQuickMessage('Python常见面试题有哪些？')">Python 面试题</el-button>
        </div>
      </div>

      <div v-for="(msg, index) in messages" :key="index" :class="['message-item', msg.role]">
        <div class="message-avatar">
          <el-avatar v-if="msg.role === 'user'" :icon="UserFilled" :size="36" />
          <el-avatar v-else :icon="Monitor" :size="36" style="background: #409eff" />
        </div>
        <div class="message-content">
          <div class="message-role">{{ msg.role === 'user' ? '你' : 'AI助手' }}</div>
          <div class="message-text" :ref="el => setMessageRef(el, index)" v-html="msg.renderedHtml"></div>
        </div>
      </div>

      <div v-if="loading" class="message-item assistant">
        <div class="message-avatar"><el-avatar :icon="Monitor" :size="36" style="background: #409eff" /></div>
        <div class="message-content">
          <div class="message-role">AI助手</div>
          <div class="message-text typing"><span class="dot"></span><span class="dot"></span><span class="dot"></span></div>
        </div>
      </div>
    </div>

    <!-- 输入框 -->
    <div class="chat-input">
      <el-input v-model="inputMessage" type="textarea" :rows="2" placeholder="输入你的问题，例如：请生成3道Java中级面试题..." @keydown.enter.ctrl="sendMessage" :disabled="loading" />
      <el-button type="primary" :icon="Promotion" circle size="large" @click="sendMessage" :loading="loading" style="margin-left: 12px; align-self: flex-end" />
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, inject, watch } from 'vue'
import { UserFilled, Monitor, QuestionFilled, Promotion, ChatDotRound } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import request from '../utils/request'

// ==================== Markdown 渲染器配置 ====================
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

// ==================== 状态管理 ====================
const domain = ref('Java')
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

// 消息 DOM 引用（用于直接 innerHTML 操作）
const messageRefs = ref({})

// App.vue inject 共享状态
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

// ==================== Markdown 渲染函数 ====================
/**
 * 将原始 Markdown 文本渲染为 HTML
 *
 * 参考 ChatGPT / streaming-markdown 的策略：
 * - 流式输出时自动补全未关闭的代码块
 */
function renderToHtml(text, streaming = false) {
  if (!text) return ''
  // 清理 DeepSeek/Qwen 等模型的思考标签
  let cleaned = text.replace(/<think[\s\S]*?<\/think>/g, '').trim()

  // 流式输出时补全未关闭的代码块（parseIncompleteMarkdown 思路）
  if (streaming && cleaned) {
    const codeBlocks = (cleaned.match(/```/g) || []).length
    if (codeBlocks % 2 !== 0) cleaned += '\n```'
  }

  return md.render(cleaned)
}

/**
 * 更新消息内容并同步渲染 HTML（双保险机制）
 */
function updateMessageContent(msgIndex, content, streaming) {
  const msg = messages.value[msgIndex]
  const renderedHtml = renderToHtml(content, streaming)

  // 1. Vue 响应式更新
  messages.value[msgIndex] = { ...msg, content, streaming, renderedHtml }

  // 2. 直接 DOM 操作确保刷新
  nextTick(() => {
    const el = messageRefs.value[msgIndex]
    if (el && el.innerHTML !== renderedHtml) el.innerHTML = renderedHtml
  })
}

// ==================== 滚动控制 ====================
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

// ==================== 消息发送 ====================
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
    if (ragEnabled.value) await streamRagChat(text)
    else await streamChat(text)
    await loadSessions()
  } catch (e) {
    console.error('对话错误', e)
    messages.value.push({ role: 'assistant', content: '抱歉，生成回答时出现错误，请稍后重试。', streaming: false, renderedHtml: renderToHtml('抱歉，生成回答时出现错误，请稍后重试。', false) })
  } finally {
    loading.value = false
    scrollToBottom(true)
  }
}

// ==================== SSE 流式读取（核心重写）====================

/**
 * 从 buffer 中提取完整的 data: 行内容，返回 { content, remainingBuffer }
 *
 * 【关键改进】正确处理不完整的 data: 行：
 * - 只提取已完成的 data: xxx\n 行
 * - 未完成的部分保留在 remainingBuffer 中等待下一次数据到达
 * - 这就是之前文字丢失的根本原因 —— 之前的实现直接清空了整个 buffer!
 *
 * 后端格式示例（Spring WebFlux Flux<String>）:
 *   data: {"sessionId":"xxx"}\n
 *   data: 你好我是助手。\n
 *   data: ### 面试题解析\n
 *   data: **参考答案**\n
 */
function extractSSEDatas(buffer) {
  let content = ''
  let remainingBuffer = ''
  const lines = buffer.split('\n')

  // 检查 buffer 是否以换行结尾（判断最后一行是否完整）
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

    // 非空内容直接拼接（token 级别不加换行），空内容加换行（段落分隔）
    if (trimmed) content += trimmed
    else content += '\n'
  }

  if (!endsWithNewline) remainingBuffer = lines[lines.length - 1]
  return { content, remainingBuffer }
}

/**
 * 读取 SSE 流并实时渲染 Markdown
 *
 * 参考主流实现（CSDN 文章 / OpenAI SDK / Vercel AI SDK）的标准模式：
 * 1. 使用 TextDecoder 正确解码 UTF-8（stream 模式）
 * 2. 使用 buffer 缓存不完整的数据帧
 * 3. 只从 buffer 中提取完整的 data: 行，未完成部分保留
 * 4. 每收到有效数据就立即更新 UI
 */
async function readSSEStream(response, msgIndex) {
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let assistantContent = ''
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    // 关键：使用 stream:true 让 TextDecoder 处理被切断的多字节 UTF-8 字符
    buffer += decoder.decode(value, { stream: true })

    // 从 buffer 中提取完整的 data: 行内容
    const { content, remainingBuffer } = extractSSEDatas(buffer)

    // 更新 buffer 为剩余未完成的部分
    buffer = remainingBuffer

    if (content) {
      assistantContent += content
      updateMessageContent(msgIndex, assistantContent, true)
      scrollToBottom()
    }
  }

  // 流结束：用 stream:false 刷新 TextDecoder 内部缓冲区
  // 并处理 buffer 中可能残留的最后一点数据
  buffer += decoder.decode(new Uint8Array(0), { stream: false })
  if (buffer) {
    // 最后一次解析（此时不需要担心未完成行，直接全部取出）
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

  // 最终渲染（streaming=false 确保代码块正确闭合）
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

  const response = await fetch(`/api/chat/stream?${params.toString()}`)
  if (!response.ok) throw new Error('请求失败')

  messages.value.push({ role: 'assistant', content: '', streaming: false, renderedHtml: '' })
  await readSSEStream(response, messages.value.length - 1)
}

async function streamRagChat(text) {
  const params = new URLSearchParams({
    message: text,
    domain: domain.value,
    difficulty: difficulty.value,
    thinkingEnabled: thinkingEnabled.value
  })
  if (currentSessionId.value) params.append('sessionId', currentSessionId.value)

  const response = await fetch(`/api/rag/chat/stream?${params.toString()}`)
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
}
.chat-toolbar {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  box-shadow: 0 1px 4px rgba(0,0,0,.05);
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}
.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
}
.empty-chat h3 { margin: 16px 0 8px; color: #303133; font-size: 20px; }
.empty-chat p { margin-bottom: 24px; }
.quick-actions { display: flex; gap: 12px; }

.message-item {
  display: flex; gap: 12px; margin-bottom: 20px;
  animation: fadeIn .3s ease;
}
.message-item.user { flex-direction: row-reverse; }
.message-avatar { flex-shrink: 0; }
.message-content { max-width: 70%; }
.message-role { font-size: 12px; color: #909399; margin-bottom: 4px; }
.message-item.user .message-role { text-align: right; }

.message-text {
  background: #fff; padding: 12px 16px; border-radius: 12px;
  font-size: 14px; line-height: 1.8;
  box-shadow: 0 1px 3px rgba(0,0,0,.08);
  word-wrap: break-word; overflow-wrap: break-word;
}
.message-item.user .message-text { background: #409eff; color: #fff; }
.message-item.user .message-text :deep(pre),
.message-item.user .message-text :deep(code) { background: rgba(255,255,255,.15); }

.message-text :deep(pre) {
  background: #f6f8fa; padding: 12px; border-radius: 6px;
  overflow-x: auto; margin: 8px 0;
}
.message-text :deep(code) { font-family: Menlo,Monaco,'Courier New',monospace; font-size: 13px; }
.message-text :deep(h1), .message-text :deep(h2), .message-text :deep(h3) { margin: 12px 0 8px; }
.message-text :deep(ul), .message-text :deep(ol) { padding-left: 20px; }
.message-text :deep(p) { margin: 6px 0; }
.message-text :deep(table) { border-collapse: collapse; width: 100%; margin: 8px 0; }
.message-text :deep(th), .message-text :deep(td) { border: 1px solid #dcdfe6; padding: 8px 12px; text-align: left; }
.message-text :deep(th) { background: #f5f7fa; }
.message-text :deep(blockquote) {
  border-left: 4px solid #409eff; padding: 8px 16px; margin: 8px 0;
  background: #f0f7ff; color: #606266;
}

.typing { display: flex; gap: 4px; padding: 16px; }
.typing .dot {
  width: 8px; height: 8px; background: #c0c4cc; border-radius: 50%;
  animation: typingDot 1.4s infinite ease-in-out;
}
.typing .dot:nth-child(2) { animation-delay: .2s; }
.typing .dot:nth-child(3) { animation-delay: .4s; }

@keyframes typingDot {
  0%,80%,100% { transform: scale(.6); opacity: .4; }
  40% { transform: scale(1); opacity: 1; }
}
@keyframes fadeIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }

.chat-input {
  display: flex; align-items: end; padding: 16px 20px;
  background: #fff; border-top: 1px solid #ebeef5;
  box-shadow: 0 -1px 4px rgba(0,0,0,.05);
}
.chat-input :deep(.el-textarea__inner) { resize: none; }
</style>
