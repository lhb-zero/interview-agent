<template>
  <el-container class="app-container">
    <!-- 侧边栏 -->
    <el-aside width="260px" class="app-aside">
      <div class="logo">
        <el-icon :size="28"><Monitor /></el-icon>
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

      <!-- 新增对话按钮（固定在菜单下方） -->
      <div class="new-chat-btn-wrapper" v-if="currentRoute === '/'">
        <el-button type="primary" class="new-chat-btn" @click="createNewChat">
          <el-icon><Plus /></el-icon>
          <span>新增对话</span>
        </el-button>
      </div>

      <!-- 会话列表 -->
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
              <el-icon><ChatLineSquare /></el-icon>
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
                  <span>重命名</span>
                </el-button>
                <el-button class="action-btn delete" size="small" text @click.stop="deleteSession(session)">
                  <el-icon><Delete /></el-icon>
                  <span>删除</span>
                </el-button>
              </div>
            </div>
          </div>
        </template>
      </div>
    </el-aside>

    <!-- 主内容区 -->
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

// 通过 provide/inject 与 ChatView 共享状态
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

// ==================== 时间分组 ====================

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

  // 只返回有会话的分组
  return [groups.today, groups.week, groups.month, groups.older].filter(g => g.sessions.length > 0)
})

// ==================== 重命名 ====================

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

// ==================== 删除 ====================

async function deleteSession(session) {
  try {
    await ElMessageBox.confirm('确定删除该对话？删除后不可恢复。', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await request.delete(`/chat/sessions/${session.sessionId}`)
    // 如果删除的是当前活跃会话，清空
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
  background: #1d1e2c;
  color: #fff;
  display: flex;
  flex-direction: column;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px;
  font-size: 18px;
  font-weight: 600;
  color: #409eff;
}

.logo-text {
  background: linear-gradient(135deg, #409eff, #67c23a);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.aside-menu {
  border-right: none;
  background: transparent;
}

.aside-menu .el-menu-item {
  color: #c0c4cc;
}

.aside-menu .el-menu-item:hover,
.aside-menu .el-menu-item.is-active {
  background: rgba(64, 158, 255, 0.1);
  color: #409eff;
}

.new-chat-btn-wrapper {
  padding: 8px 12px;
}

.new-chat-btn {
  width: 100%;
  border-radius: 8px;
  font-size: 14px;
}

.session-section {
  flex: 1;
  padding: 0 12px 12px;
  overflow-y: auto;
}

.session-group-label {
  padding: 8px 8px 4px;
  color: #6b7280;
  font-size: 12px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
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
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: #c0c4cc;
  font-size: 14px;
  transition: all 0.2s;
  position: relative;
}

.session-item:hover {
  background: rgba(255, 255, 255, 0.05);
}

.session-item.active {
  background: rgba(64, 158, 255, 0.15);
  color: #409eff;
}

.session-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-rename-input {
  flex: 1;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid #409eff;
  border-radius: 4px;
  color: #fff;
  padding: 2px 8px;
  font-size: 14px;
  outline: none;
}

.session-actions {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}

.action-btn {
  color: #a0a4b0 !important;
  padding: 4px 6px !important;
  font-size: 12px !important;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 3px;
}

.action-btn:hover {
  color: #409eff !important;
  background: rgba(64, 158, 255, 0.1) !important;
}

.action-btn.delete:hover {
  color: #f56c6c !important;
  background: rgba(245, 108, 108, 0.1) !important;
}

.app-main {
  background: #f5f7fa;
  padding: 0;
}
</style>
