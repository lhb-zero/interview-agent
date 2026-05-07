<template>
  <div class="eval-container">
    <!-- 顶部统计卡片 -->
    <div class="dashboard-cards">
      <div class="stat-card">
        <div class="stat-value">{{ dashboard.totalDatasets || 0 }}</div>
        <div class="stat-label">数据集</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ dashboard.totalExperiments || 0 }}</div>
        <div class="stat-label">实验总数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ dashboard.totalTestCases || 0 }}</div>
        <div class="stat-label">测试用例</div>
      </div>
      <div class="stat-card" :class="latestScoreClass">
        <div class="stat-value">{{ latestScore }}</div>
        <div class="stat-label">最新综合分</div>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <el-button type="primary" @click="showImportDialog = true">
        <el-icon><Upload /></el-icon>导入数据集
      </el-button>
      <el-button type="success" @click="showExperimentDialog = true" :disabled="datasets.length === 0">
        <el-icon><VideoPlay /></el-icon>创建实验
      </el-button>
    </div>

    <!-- 数据集列表 -->
    <div class="section">
      <h3 class="section-title">评估数据集</h3>
      <el-table :data="datasets" stripe class="eval-table" v-loading="loadingDatasets">
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="domain" label="领域" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.domain }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="testCaseCount" label="用例数" width="100" align="center" />
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="deleteDataset(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 实验列表 -->
    <div class="section">
      <h3 class="section-title">评估实验</h3>
      <el-table :data="experiments" stripe class="eval-table" v-loading="loadingExperiments">
        <el-table-column prop="name" label="实验名称" min-width="140" />
        <el-table-column prop="datasetName" label="数据集" width="130" />
        <el-table-column label="配置" width="200">
          <template #default="{ row }">
            <el-tag size="small" :type="row.queryRewriteEnabled ? 'success' : 'info'" class="config-tag">改写</el-tag>
            <el-tag size="small" :type="row.hybridSearchEnabled ? 'success' : 'info'" class="config-tag">混合</el-tag>
            <el-tag size="small" :type="row.rerankerEnabled ? 'success' : 'info'" class="config-tag">精排</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="130">
          <template #default="{ row }">
            <el-progress
              v-if="row.status === 'RUNNING'"
              :percentage="row.progressPercent"
              :stroke-width="6"
              :format="() => `${row.completedCases}/${row.totalCases}`"
            />
            <span v-else-if="row.status === 'COMPLETED'">{{ row.completedCases }}/{{ row.totalCases }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="综合分" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.overallScore != null" class="score-value" :class="scoreClass(row.overallScore)">
              {{ (row.overallScore * 100).toFixed(1) }}%
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'RUNNING'" link type="warning" size="small" @click="cancelExperiment(row)">取消</el-button>
            <el-button link type="danger" size="small" @click="deleteExperiment(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 导入数据集对话框 -->
    <el-dialog v-model="showImportDialog" title="导入评估数据集" width="600px">
      <el-form :model="importForm" label-width="80px">
        <el-form-item label="数据集名">
          <el-input v-model="importForm.name" placeholder="如：Java并发面试题" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="importForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="领域">
          <el-input v-model="importForm.domain" placeholder="java" />
        </el-form-item>
        <el-form-item label="JSON数据">
          <el-input v-model="importForm.jsonContent" type="textarea" :rows="10"
            placeholder='粘贴 JSON 数组，格式：[{"question":"...","groundTruthAnswer":"...","groundTruthContexts":["..."],"difficulty":"中级"}]' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showImportDialog = false">取消</el-button>
        <el-button type="primary" @click="handleImport" :loading="importing">导入</el-button>
      </template>
    </el-dialog>

    <!-- 创建实验对话框 -->
    <el-dialog v-model="showExperimentDialog" title="创建评估实验" width="500px">
      <el-form :model="experimentForm" label-width="100px">
        <el-form-item label="实验名称">
          <el-input v-model="experimentForm.name" placeholder="如：全量优化效果评估" />
        </el-form-item>
        <el-form-item label="选择数据集">
          <el-select v-model="experimentForm.datasetId" placeholder="选择数据集" style="width:100%">
            <el-option v-for="ds in datasets" :key="ds.id" :label="`${ds.name} (${ds.testCaseCount}题)`" :value="ds.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="查询改写">
          <el-switch v-model="experimentForm.queryRewriteEnabled" />
        </el-form-item>
        <el-form-item label="混合检索">
          <el-switch v-model="experimentForm.hybridSearchEnabled" />
        </el-form-item>
        <el-form-item label="Reranker精排">
          <el-switch v-model="experimentForm.rerankerEnabled" />
        </el-form-item>
        <el-form-item label="运行题数">
          <el-input-number v-model="experimentForm.maxCases" :min="0" :max="selectedDatasetCaseCount" :step="1" />
          <span class="form-tip">{{ experimentForm.maxCases > 0 ? `运行前 ${experimentForm.maxCases} 题` : '全部运行' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showExperimentDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreateExperiment" :loading="creating">创建并运行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, VideoPlay } from '@element-plus/icons-vue'
import request from '../utils/request'

const router = useRouter()

const dashboard = ref({})
const datasets = ref([])
const experiments = ref([])
const loadingDatasets = ref(false)
const loadingExperiments = ref(false)
const showImportDialog = ref(false)
const showExperimentDialog = ref(false)
const importing = ref(false)
const creating = ref(false)
let pollTimer = null

const importForm = ref({ name: '', description: '', domain: 'java', jsonContent: '' })
const experimentForm = ref({ name: '', datasetId: null, queryRewriteEnabled: true, hybridSearchEnabled: true, rerankerEnabled: true, maxCases: 0 })

const selectedDatasetCaseCount = computed(() => {
  const ds = datasets.value.find(d => d.id === experimentForm.value.datasetId)
  return ds ? ds.testCaseCount : 99
})

const latestScore = computed(() => {
  const recent = dashboard.value.recentExperiments
  if (!recent || recent.length === 0) return '-'
  const completed = recent.find(e => e.status === 'COMPLETED' && e.overallScore != null)
  return completed ? (completed.overallScore * 100).toFixed(1) + '%' : '-'
})

const latestScoreClass = computed(() => {
  const recent = dashboard.value.recentExperiments
  if (!recent || recent.length === 0) return ''
  const completed = recent.find(e => e.status === 'COMPLETED' && e.overallScore != null)
  if (!completed) return ''
  return scoreClass(completed.overallScore)
})

onMounted(() => {
  loadData()
  startPolling()
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})

function startPolling() {
  pollTimer = setInterval(() => {
    const hasRunning = experiments.value.some(e => e.status === 'RUNNING' || e.status === 'PENDING')
    if (hasRunning) {
      loadExperiments()
      loadDashboard()
    }
  }, 3000)
}

async function loadData() {
  await Promise.all([loadDashboard(), loadDatasets(), loadExperiments()])
}

async function loadDashboard() {
  try {
    const res = await request.get('/eval/dashboard')
    dashboard.value = res.data || {}
  } catch (e) { /* ignore */ }
}

async function loadDatasets() {
  loadingDatasets.value = true
  try {
    const res = await request.get('/eval/datasets')
    datasets.value = res.data || []
  } catch (e) { /* ignore */ }
  loadingDatasets.value = false
}

async function loadExperiments() {
  loadingExperiments.value = true
  try {
    const res = await request.get('/eval/experiments')
    experiments.value = res.data || []
  } catch (e) { /* ignore */ }
  loadingExperiments.value = false
}

async function handleImport() {
  if (!importForm.value.name || !importForm.value.jsonContent) {
    ElMessage.warning('请填写数据集名称和JSON数据')
    return
  }
  importing.value = true
  try {
    const parsed = JSON.parse(importForm.value.jsonContent)
    // 兼容两种格式：完整对象 {name, testCases} 或纯数组 [{question,...}]
    let payload
    if (Array.isArray(parsed)) {
      // 纯数组格式：用表单填写的 name/description/domain
      payload = {
        name: importForm.value.name,
        description: importForm.value.description,
        domain: importForm.value.domain,
        testCases: parsed
      }
    } else if (parsed.testCases) {
      // 完整对象格式：用 JSON 中的字段，表单字段作为 fallback
      payload = {
        name: importForm.value.name || parsed.name,
        description: importForm.value.description || parsed.description,
        domain: importForm.value.domain || parsed.domain,
        testCases: parsed.testCases
      }
    } else {
      throw new Error('JSON 格式不正确，需要数组或包含 testCases 的对象')
    }
    await request.post('/eval/datasets/import', payload)
    ElMessage.success('导入成功')
    showImportDialog.value = false
    importForm.value = { name: '', description: '', domain: 'java', jsonContent: '' }
    loadData()
  } catch (e) {
    ElMessage.error('导入失败：' + (e.message || 'JSON格式错误'))
  }
  importing.value = false
}

async function handleCreateExperiment() {
  if (!experimentForm.value.name || !experimentForm.value.datasetId) {
    ElMessage.warning('请填写实验名称并选择数据集')
    return
  }
  creating.value = true
  try {
    await request.post('/eval/experiments', experimentForm.value)
    ElMessage.success('实验已创建，正在后台运行')
    showExperimentDialog.value = false
    experimentForm.value = { name: '', datasetId: null, queryRewriteEnabled: true, hybridSearchEnabled: true, rerankerEnabled: true, maxCases: 0 }
    loadExperiments()
    loadDashboard()
  } catch (e) {
    ElMessage.error('创建失败')
  }
  creating.value = false
}

async function cancelExperiment(row) {
  try {
    await ElMessageBox.confirm('确定取消该实验？', '确认')
    await request.post(`/eval/experiments/${row.id}/cancel`)
    ElMessage.success('已取消')
    loadExperiments()
  } catch (e) { if (e !== 'cancel') ElMessage.error('取消失败') }
}

async function deleteExperiment(row) {
  try {
    await ElMessageBox.confirm('确定删除该实验及所有结果？', '确认', { type: 'warning' })
    await request.delete(`/eval/experiments/${row.id}`)
    ElMessage.success('已删除')
    loadExperiments()
    loadDashboard()
  } catch (e) { if (e !== 'cancel') ElMessage.error('删除失败') }
}

async function deleteDataset(row) {
  try {
    await ElMessageBox.confirm(`确定删除数据集"${row.name}"？关联的测试用例也会被删除。`, '确认', { type: 'warning' })
    await request.delete(`/eval/datasets/${row.id}`)
    ElMessage.success('已删除')
    loadData()
  } catch (e) { if (e !== 'cancel') ElMessage.error('删除失败') }
}

function viewDetail(row) {
  router.push(`/eval/${row.id}`)
}

function formatTime(t) {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

function statusType(s) {
  return { PENDING: 'info', RUNNING: 'warning', COMPLETED: 'success', FAILED: 'danger', CANCELLED: 'info' }[s] || 'info'
}

function statusLabel(s) {
  return { PENDING: '待执行', RUNNING: '运行中', COMPLETED: '已完成', FAILED: '失败', CANCELLED: '已取消' }[s] || s
}

function scoreClass(score) {
  if (score >= 0.8) return 'score-high'
  if (score >= 0.6) return 'score-mid'
  return 'score-low'
}
</script>

<style scoped>
.eval-container {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
  height: 100%;
  overflow-y: auto;
}

.dashboard-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.stat-card.score-high .stat-value { color: #67c23a; }
.stat-card.score-mid .stat-value { color: #e6a23c; }
.stat-card.score-low .stat-value { color: #f56c6c; }

.action-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.section {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}

.config-tag {
  margin-right: 4px;
}

.score-value {
  font-weight: 600;
}

.score-high { color: #67c23a; }
.score-mid { color: #e6a23c; }
.score-low { color: #f56c6c; }

.form-tip {
  margin-left: 12px;
  font-size: 13px;
  color: #909399;
}
</style>
