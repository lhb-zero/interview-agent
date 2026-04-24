<template>
  <el-container class="app-container">
    <el-aside width="260px" class="app-aside">
      <div class="logo">
        <div class="logo-icon">
          <svg viewBox="0 0 32 32" width="28" height="28">
            <defs>
              <linearGradient id="logoGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" style="stop-color:#409eff" />
                <stop offset="100%" style="stop-color:#67c23a" />
              </linearGradient>
            </defs>
            <circle cx="16" cy="16" r="14" fill="url(#logoGrad)" opacity="0.2" />
            <circle cx="16" cy="16" r="10" fill="url(#logoGrad)" opacity="0.4" />
            <path d="M11 14 Q16 10 21 14 Q21 20 16 23 Q11 20 11 14Z" fill="url(#logoGrad)" />
            <circle cx="14" cy="16" r="1.5" fill="white" />
            <circle cx="18" cy="16" r="1.5" fill="white" />
            <path d="M14 19 Q16 21 18 19" stroke="white" stroke-width="1.2" fill="none" stroke-linecap="round" />
          </svg>
        </div>
        <span class="logo-text">面试智能助手</span>
      </div>

      <el-menu
        :default-active="currentRoute"
        class="aside-menu"
        @select="handleMenuSelect"
      >
        <el-menu-item index="/">
          <el-icon><ChatDotRound /></el-icon>
          <span>智能对话</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Folder /></el-icon>
          <span>知识库管理</span>
        </el-menu-item>
      </el-menu>

      <div class="new-chat-btn-wrapper" v-if="currentRoute === '/'">
        <el-button type="primary" class="new-chat-btn" @click="createNewChat">
          <el-icon><Plus /></el-icon>
          <span>新增对话</span>
        </el-button>
      </div>

      <div class="session-section" v-if="currentRoute === '/'">
        <template v-for="group in groupedSessions" :key="group.label">
          <div class="session-group-label">{{ group.label }}</div>
          <div class="session-list">
            <div
              v-for="session in group.sessions"
              :key="session.sessionId"
              class="session-item"
              :class="{ active: activeSessionId === session.sessionId }"
              @click="selectSession(session)"
              @mouseenter="hoveredSessionId = session.sessionId"
              @mouseleave="hoveredSessionId = null"
            >
              <el-icon class="session-icon"><ChatLineSquare /></el-icon>
              <span class="session-name" v-if="editingSessionId !== session.sessionId">
                {{ getSessionTitle(session) }}
              </span>
              <input
                v-else
                class="session-rename-input"
                v-model="renameValue"
                @blur="confirmRename(session)"
                @keydown.enter="confirmRename(session)"
                @keydown.escape="cancelRename"
                ref="renameInput"
              />
              <div class="session-actions" v-if="hoveredSessionId === session.sessionId && editingSessionId !== session.sessionId">
                <el-button class="action-btn" size="small" text @click.stop="startRename(session)">
                  <el-icon><Edit /></el-icon>
                </el-button>
                <el-button class="action-btn delete" size="small" text @click.stop="deleteSession(session)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
        </template>
      </div>

      <div class="aside-footer">
        <div class="footer-info">Powered by Spring AI + RAG</div>
      </div>
    </el-aside>

    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted, provide, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from './utils/request'

const router = useRouter()
const route = useRoute()

const currentRoute = computed(() => route.path)
const sessions = ref([])
const activeSessionId = ref(null)
const hoveredSessionId = ref(null)
const editingSessionId = ref(null)
const renameValue = ref('')
const renameInput = ref(null)

provide('activeSessionId', activeSessionId)
provide('sessions', sessions)
provide('loadSessions', loadSessions)
provide('setActiveSession', (id) => { activeSessionId.value = id })

onMounted(() => {
  loadSessions()
})

function handleMenuSelect(index) {
  router.push(index)
}

async function loadSessions() {
  try {
    const res = await request.get('/chat/sessions')
    sessions.value = res.data || []
  } catch (e) {
    // ignore
  }
}

function createNewChat() {
  activeSessionId.value = null
}

function selectSession(session) {
  if (editingSessionId.value) return
  activeSessionId.value = session.sessionId
}

function getSessionTitle(session) {
  if (session.title) return session.title
  return session.domain || '新对话'
}

const groupedSessions = computed(() => {
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const sevenDaysAgo = new Date(today.getTime() - 7 * 24 * 60 * 60 * 1000)
  const thirtyDaysAgo = new Date(today.getTime() - 30 * 24 * 60 * 60 * 1000)

  const groups = {
    today: { label: '今天', sessions: [] },
    week: { label: '过去 7 天', sessions: [] },
    month: { label: '过去 30 天', sessions: [] },
    older: { label: '更早', sessions: [] }
  }

  for (const session of sessions.value) {
    const dt = session.createdAt ? new Date(session.createdAt) : null
    if (!dt) {
      groups.older.sessions.push(session)
      continue
    }
    if (dt >= today) {
      groups.today.sessions.push(session)
    } else if (dt >= sevenDaysAgo) {
      groups.week.sessions.push(session)
    } else if (dt >= thirtyDaysAgo) {
      groups.month.sessions.push(session)
    } else {
      groups.older.sessions.push(session)
    }
  }

  return [groups.today, groups.week, groups.month, groups.older].filter(g => g.sessions.length > 0)
})

function startRename(session) {
  editingSessionId.value = session.sessionId
  renameValue.value = getSessionTitle(session)
  nextTick(() => {
    if (renameInput.value) {
      const inputs = Array.isArray(renameInput.value) ? renameInput.value : [renameInput.value]
      const el = inputs[0]
      if (el) {
        el.focus()
        el.select()
      }
    }
  })
}

async function confirmRename(session) {
  const newTitle = renameValue.value.trim()
  if (!newTitle) {
    cancelRename()
    return
  }
  if (newTitle === getSessionTitle(session)) {
    cancelRename()
    return
  }
  try {
    await request.put(`/chat/sessions/${session.sessionId}/rename`, { title: newTitle })
    session.title = newTitle
    ElMessage.success('重命名成功')
  } catch (e) {
    ElMessage.error('重命名失败')
  }
  editingSessionId.value = null
  renameValue.value = ''
}

function cancelRename() {
  editingSessionId.value = null
  renameValue.value = ''
}

async function deleteSession(session) {
  try {
    await ElMessageBox.confirm('确定删除该对话？删除后不可恢复。', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await request.delete(`/chat/sessions/${session.sessionId}`)
    if (activeSessionId.value === session.sessionId) {
      activeSessionId.value = null
    }
    await loadSessions()
    ElMessage.success('删除成功')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.app-container {
  height: 100vh;
}

.app-aside {
  background: linear-gradient(180deg, #1a1b2e 0%, #16172b 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  border-right: none;
  overflow: hidden;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 16px;
  margin-bottom: 4px;
}

.logo-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-text {
  font-size: 17px;
  font-weight: 700;
  background: linear-gradient(135deg, #409eff, #67c23a);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: 0.5px;
}

.aside-menu {
  border-right: none;
  background: transparent;
  padding: 0 8px;
}

.aside-menu .el-menu-item {
  color: #a0a4b8;
  border-radius: 10px;
  margin-bottom: 2px;
  height: 44px;
  line-height: 44px;
  transition: all .2s;
}

.aside-menu .el-menu-item:hover {
  background: rgba(64, 158, 255, 0.08);
  color: #c0c4cc;
}

.aside-menu .el-menu-item.is-active {
  background: rgba(64, 158, 255, 0.15);
  color: #409eff;
}

.new-chat-btn-wrapper {
  padding: 8px 12px;
}

.new-chat-btn {
  width: 100%;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  height: 40px;
  background: linear-gradient(135deg, #409eff, #5b8def);
  border: none;
  transition: all .2s;
}

.new-chat-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(64,158,255,.3);
}

.session-section {
  flex: 1;
  padding: 0 12px 12px;
  overflow-y: auto;
}

.session-section::-webkit-scrollbar {
  width: 4px;
}

.session-section::-webkit-scrollbar-track {
  background: transparent;
}

.session-section::-webkit-scrollbar-thumb {
  background: rgba(255,255,255,.1);
  border-radius: 2px;
}

.session-group-label {
  padding: 10px 10px 6px;
  color: #5a5e72;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.8px;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 10px;
  border-radius: 10px;
  cursor: pointer;
  color: #a0a4b8;
  font-size: 13px;
  transition: all .2s;
  position: relative;
}

.session-item:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #d0d4e0;
}

.session-item.active {
  background: rgba(64, 158, 255, 0.12);
  color: #409eff;
}

.session-icon {
  font-size: 14px;
  flex-shrink: 0;
  opacity: 0.7;
}

.session-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

.session-rename-input {
  flex: 1;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid #409eff;
  border-radius: 6px;
  color: #fff;
  padding: 3px 8px;
  font-size: 13px;
  outline: none;
}

.session-actions {
  display: flex;
  gap: 0;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity .15s;
}

.session-item:hover .session-actions {
  opacity: 1;
}

.action-btn {
  color: #6b7280 !important;
  padding: 4px !important;
  border-radius: 6px !important;
  min-width: 28px !important;
  height: 28px !important;
}

.action-btn:hover {
  color: #409eff !important;
  background: rgba(64, 158, 255, 0.1) !important;
}

.action-btn.delete:hover {
  color: #f56c6c !important;
  background: rgba(245, 108, 108, 0.1) !important;
}

.aside-footer {
  padding: 12px 16px;
  border-top: 1px solid rgba(255,255,255,.05);
}

.footer-info {
  font-size: 11px;
  color: #4a4e62;
  text-align: center;
  letter-spacing: 0.3px;
}

.app-main {
  background: #f0f2f5;
  padding: 0;
}
</style>
