/**
 * 测试脚本：验证 SSE 流式输出时的 Markdown 渲染逻辑
 * 
 * 测试内容：
 * 1. parseSSEChunk 是否正确过滤 sessionId JSON
 * 2. extractSessionId 是否正确提取并移除 sessionId
 * 3. renderToHtml 是否在流式模式下正确补全代码块
 * 4. 完整流程模拟：SSE chunk -> 解析 -> 渲染
 */

const MarkdownIt = require('markdown-it')

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  highlight: function (str, lang) {
    return '<pre class="hljs"><code>' + str + '</code></pre>'
  }
})

// ============ 从 ChatView.vue 提取的核心函数（保持一致） ============

function parseSSEChunk(chunk) {
  let result = ''
  const lines = chunk.split('\n')
  for (const line of lines) {
    if (line.startsWith('data:')) {
      const data = line.slice(5).trim()
      if (!data || data === '[DONE]') continue
      // 过滤掉 sessionId 控制消息
      if (data.startsWith('{') && data.includes('sessionId')) continue
      result += data
    }
  }
  return result
}

function extractSessionId(buffer) {
  let extractedSessionId = null
  const lines = buffer.split('\n')
  let cleanedBuffer = ''
  for (const line of lines) {
    if (line.startsWith('data:')) {
      const data = line.slice(5).trim()
      if (data.startsWith('{') && data.includes('sessionId')) {
        try {
          const parsed = JSON.parse(data)
          if (parsed.sessionId) {
            extractedSessionId = parsed.sessionId
          }
        } catch (_) {
          cleanedBuffer += line + '\n'
        }
        continue
      }
    }
    cleanedBuffer += line + '\n'
  }
  return { cleanedBuffer, extractedSessionId }
}

function renderToHtml(text, streaming = false) {
  if (!text) return ''
  let cleaned = text.replace(/<think[\s\S]*?<\/think>/g, '').trim()
  
  if (streaming) {
    const codeBlockCount = (cleaned.match(/```/g) || []).length
    if (codeBlockCount % 2 !== 0) {
      cleaned += '\n```'
    }
  }
  
  return md.render(cleaned)
}

// ============ 测试用例 ============

let passed = 0
let failed = 0

function test(name, fn) {
  try {
    fn()
    console.log(`✅ PASS: ${name}`)
    passed++
  } catch (e) {
    console.log(`❌ FAIL: ${name}`)
    console.log(`   ${e.message}`)
    failed++
  }
}

function assertEqual(actual, expected, msg) {
  if (actual !== expected) throw new Error(`${msg}\n   期望: ${JSON.stringify(expected)}\n   实际: ${JSON.stringify(actual)}`)
}

function assertIncludes(haystack, needle, msg) {
  if (!haystack.includes(needle)) throw new Error(`${msg}\n   应包含: "${needle}"\n   实际: "${haystack.substring(0, 200)}"`)
}

function assertNotIncludes(haystack, needle, msg) {
  if (haystack.includes(needle)) throw new Error(`${msg}\n   不应包含: "${needle}"`)
}

console.log('=' .repeat(60))
console.log('测试 SSE 流式输出 & Markdown 渲染逻辑')
console.log('='.repeat(60))

// ---- 测试 1：parseSSEChunk 过滤 sessionId ----
test('parseSSEChunk 过滤 sessionId JSON', () => {
  const input = `data:{"sessionId":"abc123"}
data:你好，我是AI助手
data: ### 标题
data:- 列表项1`
  const result = parseSSEChunk(input)
  assertNotIncludes(result, 'sessionId', '不应包含 sessionId')
  assertNotIncludes(result, 'abc123', '不应包含 session ID')
  assertIncludes(result, '你好，我是AI助手', '应包含正常文本')
})

test('parseSSEChunk 过滤纯 sessionId 行', () => {
  const input = `data:{"sessionId":"4c23c3da2c5543199c7d4558f1eb45a1"}
data:JavaScript 是一种轻量级脚本语言`
  const result = parseSSEChunk(input)
  assertEqual(result, 'JavaScript 是一种轻量级脚本语言', '应只保留正常文本')
})

// ---- 测试 2：extractSessionId 正确提取并移除 ----
test('extractSessionId 提取 sessionId 并从 buffer 中移除', () => {
  const input = `data:{"sessionId":"my-session-001"}
data:这是正常内容`
  const { cleanedBuffer, extractedSessionId } = extractSessionId(input)
  assertEqual(extractedSessionId, 'my-session-001', '应提取到正确的 sessionId')
  assertNotIncludes(cleanedBuffer, 'sessionId', '清理后的 buffer 不应有 sessionId')
  assertIncludes(cleanedBuffer, '这是正常内容', '应保留正常内容')
})

test('extractSessionId 处理无 sessionId 的 buffer', () => {
  const input = `data:普通文本1
data:普通文本2`
  const { cleanedBuffer, extractedSessionId } = extractSessionId(input)
  assertEqual(extractedSessionId, null, '不应有 sessionId')
  assertIncludes(cleanedBuffer, '普通文本1', '应保留原始内容')
})

// ---- 测试 3：renderToHtml 流式模式代码块补全 ----
test('renderToHtml 补全未关闭的代码块（流式）', () => {
  const text = `这是一个代码示例：

\`\`\`javascript
const x = 1;`
  const html = renderToHtml(text, true)
  // 流式模式下应该自动闭合代码块，所以应该能渲染出 <pre><code>
  assertIncludes(html, '<pre class="hljs"><code>', '流式模式应渲染代码块（自动补全闭合标记）')
})

test('renderToHtml 不补全已关闭的代码块', () => {
  const text = `代码示例：

\`\`\`javascript
const x = 1;
\`\`\``
  const html = renderToHtml(text, false)
  assertIncludes(html, '<pre class="hljs"><code>', '完整代码块应正常渲染')
})

// ---- 测试 4：Markdown 标题渲染 ----
test('renderToHtml 渲染 h3 标题', () => {
  const text = '### 面试题解析：什么是 FastAPI？'
  const html = renderToHtml(text, true)
  assertIncludes(html, '<h3>', '应渲染为 <h3> 标签')
  assertIncludes(html, 'FastAPI', '应保留标题文本')
  assertNotIncludes(html, '### ', '不应有原始的 ### ')
})

test('renderToHtml 渲染列表', () => {
  const text = `- 自动文档生成\n- 高性能\n- 类型提示驱动`
  const html = renderToHtml(text, true)
  assertIncludes(html, '<li>', '应渲染为列表项')
  assertNotIncludes(html, '- 自动文档生成', '不应有原始的 - ')
})

test('renderToHtml 渲染加粗文本', () => {
  const text = '**Vue 3** 的核心概念'
  const html = renderToHtml(text, true)
  assertIncludes(html, '<strong>', '应渲染为加粗标签')
  assertIncludes(html, 'Vue 3', '应保留文本')
})

// ---- 测试 5：完整流程模拟 ----
test('完整流程：SSE chunks -> 解析 -> 渲染（模拟用户报告的场景）', () => {
  // 模拟后端发送的 SSE 数据（第一条是 sessionId）
  const sseChunks = [
    `data:{"sessionId":"4c23c3da2c5543199c7d4558f1eb45a1"}\n`,
    `data:你好！我是你的专业面试智能助手。针对你提出的 "什么是 FastAPI" 这一基础概念问题，我将为你提供一份详细的面试题解析。\n\n### 面试题解析：什么是 FastAPI？\n\n**问题背景**：在面试中，当被问到 "什么是 FastAPI" 时，面试官通常希望考察你对现代 Python Web 框架的理解。\n\n【参考回答】\n\n> FastAPI 是一个用于构建高性能异步 Web API 的 Python 框架。它基于 Starlette 和 Pydantic 构建。\n\n它的核心优势在于：\n\n1. **自动文档生成**：利用 Pydantic 的数据模型，它能自动生成符合 OpenAPI 3.0 标准的交互式 API 文档。\n2. **高性能**：基于 ASGI 协议，支持异步 I/O。\n3. **类型提示驱动**：通过 Python 类型注解来推导数据结构。\n`
  ]
  
  // 模拟 readSSEStream 的逻辑
  let buffer = ''
  let assistantContent = ''
  
  for (const chunk of sseChunks) {
    buffer += chunk
    
    // 始终提取并移除 sessionId
    buffer = extractSessionId(buffer).cleanedBuffer
    
    // 解析 SSE 内容
    const parsed = parseSSEChunk(buffer)
    if (parsed) {
      assistantContent += parsed
      buffer = '' // 清空已处理的 buffer
    }
  }
  
  // 最终渲染
  const finalHtml = renderToHtml(assistantContent, false)
  
  // 验证：不应有 sessionId 残留
  assertNotIncludes(finalHtml, '4c23c3da2c5543199c7d4558f1eb45a1', '最终 HTML 不应有 sessionId')
  assertNotIncludes(assistantContent, 'sessionId', '最终内容不应有 sessionId')
  assertNotIncludes(assistantContent, '{"', '最终内容不应有 JSON 片段')
  
  // 验证：Markdown 应该被正确渲染
  assertIncludes(finalHtml, '<h3>', '标题应被渲染为 h3 标签')
  assertIncludes(finalHtml, '<strong>', '加粗文本应被渲染为 strong 标签')
  assertIncludes(finalHtml, '<ol>|<ul>', '列表应被渲染为有序或无序列表')
  assertIncludes(finalHtml, '<blockquote>', '引用应被渲染为 blockquote')
  
  // 关键：不应该出现原始的 Markdown 标记
  assertNotIncludes(finalHtml, '### ', '不应出现原始的 ### 标记')
  assertNotIncludes(finalHtml, '**问题背景**', '不应出现原始的 ** 加粗标记')
})

// ---- 测试 6：模拟多次对话的 sessionId 过滤 ----
test('第二次及后续对话也应正确过滤 sessionId', () => {
  // 第一次对话已有 sessionId
  let currentSessionId = 'first-session-id'
  
  // 第二次对话的数据
  const sseData2 = `data:{"sessionId":"second-session-id"}
data:JavaScript 是一种轻量级脚本语言，主要用于 Web 前端交互，支持异步编程。`
  
  const { cleanedBuffer, extractedSessionId } = extractSessionId(sseData2)
  const content = parseSSEChunk(cleanedBuffer)
  
  assertEqual(extractedSessionId, 'second-session-id', '应提取到新的 sessionId')
  assertEqual(content, 'JavaScript 是一种轻量级脚本语言，主要用于 Web 前端交互，支持异步编程。', '内容不应包含 sessionId')
  assertNotIncludes(content, '{"sessionId":', '内容不应包含任何 JSON 片段')
})

// ============ 结果汇总 ============
console.log('\n' + '=' .repeat(60))
console.log(`结果：${passed} 通过 / ${failed} 失败 / ${passed + failed} 总计`)
console.log('='.repeat(60))

if (failed > 0) {
  process.exit(1)
}
