<template>
  <div class="knowledge-view">
    <div class="knowledge-header">
      <div class="header-info">
        <h2>知识库管理</h2>
        <p class="header-desc">管理面试知识文档，上传后自动分块并向量化存储</p>
      </div>
      <el-button type="primary" :icon="Upload" @click="uploadDialogVisible = true" class="upload-btn">
        上传文档
      </el-button>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon" style="background: linear-gradient(135deg, #409eff, #5b8def)">
          <el-icon :size="22"><Document /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ documents.length }}</div>
          <div class="stat-label">文档总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: linear-gradient(135deg, #67c23a, #85ce61)">
          <el-icon :size="22"><Grid /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ totalChunks }}</div>
          <div class="stat-label">向量分块</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: linear-gradient(135deg, #e6a23c, #f0c78a)">
          <el-icon :size="22"><Folder /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ domainCount }}</div>
          <div class="stat-label">技术领域</div>
        </div>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="filterDomain" placeholder="筛选领域" clearable style="width: 160px" @change="loadDocuments">
        <el-option label="Java" value="java" />
        <el-option label="Python" value="python" />
        <el-option label="AI/机器学习" value="ai" />
        <el-option label="前端" value="frontend" />
        <el-option label="数据库" value="database" />
        <el-option label="系统设计" value="system" />
      </el-select>
    </div>

    <div class="doc-grid" v-if="filteredDocuments.length > 0">
      <div v-for="doc in filteredDocuments" :key="doc.id" class="doc-card">
        <div class="doc-card-header">
          <div class="doc-type-badge" :class="doc.fileType">
            {{ doc.fileType?.toUpperCase() }}
          </div>
          <el-dropdown trigger="click" @command="(cmd) => handleCommand(cmd, doc)">
            <el-button text size="small" class="more-btn">
              <el-icon><MoreFilled /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="delete" style="color: #f56c6c">
                  <el-icon><Delete /></el-icon>删除文档
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div class="doc-card-body">
          <h3 class="doc-title">{{ doc.title }}</h3>
          <el-tag size="small" :type="getDomainTagType(doc.domain)" class="domain-tag">{{ doc.domain }}</el-tag>
        </div>
        <div class="doc-card-footer">
          <div class="doc-meta">
            <span class="meta-item">
              <el-icon><Grid /></el-icon>
              {{ doc.chunkCount }} 分块
            </span>
            <span class="meta-item">
              <el-icon><Clock /></el-icon>
              {{ formatDate(doc.createdAt) }}
            </span>
          </div>
          <el-tag :type="doc.status === 'ACTIVE' ? 'success' : 'danger'" size="small" effect="light">
            {{ doc.status === 'ACTIVE' ? '有效' : '已删除' }}
          </el-tag>
        </div>
      </div>
    </div>

    <div v-else class="empty-state">
      <el-icon :size="48" color="#c0c4cc"><FolderOpened /></el-icon>
      <p>暂无文档，点击上方按钮上传</p>
    </div>

    <el-dialog v-model="uploadDialogVisible" title="上传知识文档" width="500px" class="upload-dialog">
      <el-form :model="uploadForm" label-width="80px">
        <el-form-item label="文档标题">
          <el-input v-model="uploadForm.title" placeholder="请输入文档标题" />
        </el-form-item>
        <el-form-item label="技术领域">
          <el-select v-model="uploadForm.domain" placeholder="请选择领域" style="width: 100%">
            <el-option label="Java" value="java" />
            <el-option label="Python" value="python" />
            <el-option label="AI/机器学习" value="ai" />
            <el-option label="前端" value="frontend" />
            <el-option label="数据库" value="database" />
            <el-option label="系统设计" value="system" />
          </el-select>
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".pdf,.md,.txt"
            :on-change="handleFileChange"
            drag
          >
            <el-icon :size="32" class="upload-icon"><Upload /></el-icon>
            <div class="upload-text">将文件拖到此处，或<em>点击上传</em></div>
            <template #tip>
              <div class="upload-tip">支持 PDF、Markdown、TXT 文件</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitUpload" :loading="uploading">确认上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Upload, Document, Grid, Folder, FolderOpened, MoreFilled, Delete, Clock } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const documents = ref([])
const filterDomain = ref('')
const uploadDialogVisible = ref(false)
const uploading = ref(false)
const uploadRef = ref(null)
const uploadForm = ref({
  title: '',
  domain: 'java'
})
const selectedFile = ref(null)

const filteredDocuments = computed(() => {
  if (!filterDomain.value) return documents.value
  return documents.value.filter(d => d.domain === filterDomain.value)
})

const totalChunks = computed(() => {
  return filteredDocuments.value.reduce((sum, d) => sum + (d.chunkCount || 0), 0)
})

const domainCount = computed(() => {
  const domains = new Set(filteredDocuments.value.map(d => d.domain))
  return domains.size
})

onMounted(() => {
  loadDocuments()
})

async function loadDocuments() {
  try {
    const params = filterDomain.value ? { domain: filterDomain.value } : {}
    const res = await request.get('/knowledge/documents', { params })
    documents.value = res.data || []
  } catch (e) {
    // ignore
  }
}

function handleFileChange(file) {
  selectedFile.value = file.raw
}

async function submitUpload() {
  if (!uploadForm.value.title) {
    ElMessage.warning('请输入文档标题')
    return
  }
  if (!uploadForm.value.domain) {
    ElMessage.warning('请选择技术领域')
    return
  }
  if (!selectedFile.value) {
    ElMessage.warning('请选择文件')
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    formData.append('title', uploadForm.value.title)
    formData.append('domain', uploadForm.value.domain)

    await request.post('/knowledge/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    })

    ElMessage.success('文档上传成功，正在处理中...')
    uploadDialogVisible.value = false
    uploadForm.value = { title: '', domain: 'java' }
    selectedFile.value = null
    loadDocuments()
  } catch (e) {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

function handleCommand(cmd, doc) {
  if (cmd === 'delete') deleteDocument(doc.id)
}

async function deleteDocument(id) {
  try {
    await ElMessageBox.confirm('删除文档将同时清理关联的向量数据，确定删除？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await request.delete(`/knowledge/documents/${id}`)
    ElMessage.success('删除成功，向量数据已同步清理')
    loadDocuments()
  } catch (e) {
    // 用户取消
  }
}

function getDomainTagType(domain) {
  const map = {
    java: '',
    python: 'success',
    ai: 'warning',
    frontend: 'danger',
    database: 'info',
    system: 'success'
  }
  return map[domain] || ''
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hours}:${minutes}`
}
</script>

<style scoped>
.knowledge-view {
  padding: 28px;
  height: 100vh;
  overflow-y: auto;
  background: #f0f2f5;
}

.knowledge-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.header-info h2 {
  font-size: 22px;
  color: #303133;
  font-weight: 700;
  margin-bottom: 4px;
}

.header-desc {
  color: #909399;
  font-size: 14px;
}

.upload-btn {
  border-radius: 10px;
  font-weight: 500;
  padding: 10px 20px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 1px 4px rgba(0,0,0,.05);
  transition: transform .2s;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 2px;
}

.filter-bar {
  margin-bottom: 20px;
}

.doc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.doc-card {
  background: #fff;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0,0,0,.05);
  transition: all .25s ease;
  border: 1px solid transparent;
  display: flex;
  flex-direction: column;
}

.doc-card:hover {
  box-shadow: 0 6px 16px rgba(0,0,0,.08);
  border-color: #e0e6ed;
  transform: translateY(-2px);
}

.doc-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.doc-type-badge {
  font-size: 11px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 6px;
  letter-spacing: 0.5px;
}

.doc-type-badge.pdf {
  background: #fef0f0;
  color: #f56c6c;
}

.doc-type-badge.md {
  background: #f0f9eb;
  color: #67c23a;
}

.doc-type-badge.txt {
  background: #f4f4f5;
  color: #909399;
}

.more-btn {
  color: #c0c4cc;
  padding: 4px !important;
}

.more-btn:hover {
  color: #606266;
}

.doc-card-body {
  flex: 1;
  margin-bottom: 14px;
}

.doc-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.domain-tag {
  border-radius: 6px;
}

.doc-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
}

.doc-meta {
  display: flex;
  gap: 14px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

.meta-item .el-icon {
  font-size: 13px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  color: #c0c4cc;
}

.empty-state p {
  margin-top: 16px;
  font-size: 14px;
}

.upload-icon {
  color: #c0c4cc;
  margin-bottom: 8px;
}

.upload-text {
  color: #606266;
  font-size: 14px;
}

.upload-text em {
  color: #409eff;
  font-style: normal;
}

.upload-tip {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
</style>
