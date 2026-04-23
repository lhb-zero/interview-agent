<template>
  <div class="knowledge-view">
    <div class="knowledge-header">
      <h2>知识库管理</h2>
      <el-button type="primary" :icon="Upload" @click="uploadDialogVisible = true">
        上传文档
      </el-button>
    </div>

    <!-- 筛选栏 -->
    <div class="knowledge-filter">
      <el-select v-model="filterDomain" placeholder="筛选领域" clearable style="width: 160px" @change="loadDocuments">
        <el-option label="Java" value="java" />
        <el-option label="Python" value="python" />
        <el-option label="AI/机器学习" value="ai" />
        <el-option label="前端" value="frontend" />
        <el-option label="数据库" value="database" />
        <el-option label="系统设计" value="system" />
      </el-select>
    </div>

    <!-- 文档列表 -->
    <el-table :data="documents" stripe style="width: 100%">
      <el-table-column prop="title" label="文档标题" min-width="200" />
      <el-table-column prop="domain" label="领域" width="120">
        <template #default="{ row }">
          <el-tag size="small">{{ row.domain }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="fileType" label="类型" width="80" />
      <el-table-column prop="chunkCount" label="分块数" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
            {{ row.status === 'ACTIVE' ? '有效' : '已删除' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="上传时间" width="180" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button type="danger" link size="small" @click="deleteDocument(row.id)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 上传对话框 -->
    <el-dialog v-model="uploadDialogVisible" title="上传知识文档" width="500px">
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
          >
            <el-button type="primary">选择文件</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 PDF、Markdown、TXT 文件</div>
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
import { ref, onMounted } from 'vue'
import { Upload } from '@element-plus/icons-vue'
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

async function deleteDocument(id) {
  try {
    await ElMessageBox.confirm('确定删除该文档？', '提示', { type: 'warning' })
    await request.delete(`/knowledge/documents/${id}`)
    ElMessage.success('删除成功')
    loadDocuments()
  } catch (e) {
    // 用户取消
  }
}
</script>

<style scoped>
.knowledge-view {
  padding: 24px;
  height: 100vh;
  overflow-y: auto;
}

.knowledge-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.knowledge-header h2 {
  font-size: 20px;
  color: #303133;
}

.knowledge-filter {
  margin-bottom: 16px;
}
</style>
