<template>
  <div class="eval-container">
    <!-- 顶部统计卡片 -->
    <div class="dashboard-cards">
      <div class="stat-card card-datasets">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" width="28" height="28"><path d="M20 6H4c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zM4 8h16v2H4V8zm0 8v-2h16v2H4z" fill="currentColor"/></svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ dashboard.totalDatasets || 0 }}</div>
          <div class="stat-label">数据集</div>
        </div>
      </div>
      <div class="stat-card card-experiments">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" width="28" height="28"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 14H5V7h7v10zm2-8h4v2h-4V9zm0 4h4v2h-4v-2z" fill="currentColor"/></svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ dashboard.totalExperiments || 0 }}</div>
          <div class="stat-label">实验总数</div>
        </div>
      </div>
      <div class="stat-card card-cases">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" width="28" height="28"><path d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zM6 20V4h7v5h5v11H6z" fill="currentColor"/></svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ dashboard.totalTestCases || 0 }}</div>
          <div class="stat-label">测试用例</div>
        </div>
      </div>
      <div class="stat-card" :class="latestScoreClass">
        <div class="stat-icon">
          <svg viewBox="0 0 24 24" width="28" height="28"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" fill="currentColor"/></svg>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ latestScore }}</div>
          <div class="stat-label">最新综合分</div>
        </div>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <el-button type="primary" class="action-btn-primary" @click="showImportDialog = true">
        <el-icon><Upload /></el-icon>导入数据集
      </el-button>
      <el-button type="success" class="action-btn-success" @click="showExperimentDialog = true" :disabled="datasets.length === 0">
        <el-icon><VideoPlay /></el-icon>创建实验
      </el-button>
    </div>

    <!-- Tab 切换 -->
    <div class="section">
      <div class="tab-header">
        <div class="tab-item" :class="{ active: activeTab === 'experiments' }" @click="activeTab = 'experiments'">
          <svg viewBox="0 0 24 24" width="16" height="16"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 14H5V7h7v10z" fill="currentColor"/></svg>
          评估实验
          <span class="tab-count">{{ experiments.length }}</span>
        </div>
        <div class="tab-item" :class="{ active: activeTab === 'datasets' }" @click="activeTab = 'datasets'">
          <svg viewBox="0 0 24 24" width="16" height="16"><path d="M20 6H4c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2z" fill="currentColor"/></svg>
          评估数据集
          <span class="tab-count">{{ datasets.length }}</span>
        </div>
      </div>

      <!-- 实验列表 -->
      <div v-show="activeTab === 'experiments'" v-loading="loadingExperiments">
        <div v-if="experiments.length === 0" class="empty-state">
          <svg viewBox="0 0 24 24" width="48" height="48" class="empty-icon"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 14H5V7h7v10z" fill="currentColor"/></svg>
          <p>暂无实验，点击"创建实验"开始评估</p>
        </div>
        <div v-else class="experiment-cards">
          <div v-for="exp in experiments" :key="exp.id" class="exp-card" :class="{ 'exp-running': exp.status === 'RUNNING' }" @click="viewDetail(exp)">
            <div class="exp-card-header">
              <div class="exp-name">{{ exp.name }}</div>
              <el-tag :type="statusType(exp.status)" size="small" effect="plain">{{ statusLabel(exp.status) }}</el-tag>
            </div>
            <div class="exp-card-meta">
              <span class="exp-dataset">
                <svg viewBox="0 0 24 24" width="12" height="12"><path d="M20 6H4c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2z" fill="currentColor"/></svg>
                {{ exp.datasetName }}
              </span>
              <span class="exp-time">{{ formatTime(exp.createdAt) }}</span>
            </div>
            <div class="exp-card-config">
              <span class="config-chip" :class="{ active: exp.queryRewriteEnabled }">改写</span>
              <span class="config-chip" :class="{ active: exp.hybridSearchEnabled }">混合</span>
              <span class="config-chip" :class="{ active: exp.rerankerEnabled }">精排</span>
            </div>
            <!-- 运行中显示进度 -->
            <div v-if="exp.status === 'RUNNING'" class="exp-progress">
              <div class="progress-bar">
                <div class="progress-fill" :style="{ width: exp.progressPercent + '%' }"></div>
              </div>
              <span class="progress-text">{{ exp.completedCases }}/{{ exp.totalCases }}</span>
            </div>
            <!-- 已完成显示指标条 -->
            <div v-else-if="exp.status === 'COMPLETED' && exp.overallScore != null" class="exp-metrics-mini">
              <div class="metric-mini-item">
                <span class="metric-mini-label">P</span>
                <div class="metric-mini-bar"><div class="metric-mini-fill" :style="{ width: (exp.avgContextPrecision || 0) * 100 + '%', background: metricColor(exp.avgContextPrecision) }"></div></div>
                <span class="metric-mini-val" :class="scoreClass(exp.avgContextPrecision)">{{ pct(exp.avgContextPrecision) }}</span>
              </div>
              <div class="metric-mini-item">
                <span class="metric-mini-label">R</span>
                <div class="metric-mini-bar"><div class="metric-mini-fill" :style="{ width: (exp.avgContextRecall || 0) * 100 + '%', background: metricColor(exp.avgContextRecall) }"></div></div>
                <span class="metric-mini-val" :class="scoreClass(exp.avgContextRecall)">{{ pct(exp.avgContextRecall) }}</span>
              </div>
              <div class="metric-mini-item">
                <span class="metric-mini-label">F</span>
                <div class="metric-mini-bar"><div class="metric-mini-fill" :style="{ width: (exp.avgFaithfulness || 0) * 100 + '%', background: metricColor(exp.avgFaithfulness) }"></div></div>
                <span class="metric-mini-val" :class="scoreClass(exp.avgFaithfulness)">{{ pct(exp.avgFaithfulness) }}</span>
              </div>
              <div class="metric-mini-item">
                <span class="metric-mini-label">A</span>
                <div class="metric-mini-bar"><div class="metric-mini-fill" :style="{ width: (exp.avgAnswerRelevancy || 0) * 100 + '%', background: metricColor(exp.avgAnswerRelevancy) }"></div></div>
                <span class="metric-mini-val" :class="scoreClass(exp.avgAnswerRelevancy)">{{ pct(exp.avgAnswerRelevancy) }}</span>
              </div>
            </div>
            <div class="exp-card-footer">
              <span class="exp-score" :class="scoreClass(exp.overallScore)" v-if="exp.overallScore != null">
                {{ (exp.overallScore * 100).toFixed(1) }}%
              </span>
              <div class="exp-actions">
                <el-button v-if="exp.status === 'RUNNING'" link type="warning" size="small" @click.stop="cancelExperiment(exp)">取消</el-button>
                <el-button link type="danger" size="small" @click.stop="deleteExperiment(exp)">删除</el-button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 数据集列表 -->
      <div v-show="activeTab === 'datasets'" v-loading="loadingDatasets">
        <el-table :data="datasets" stripe class="eval-table">
          <el-table-column prop="name" label="名称" min-width="150" />
          <el-table-column prop="domain" label="领域" width="100">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.domain }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="testCaseCount" label="用例数" width="100" align="center" />
          <el-table-column prop="createdAt" label="创建时间" width="180">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center">
            <template #default="{ row }">
              <el-button link type="danger" size="small" @click="deleteDataset(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 导入数据集对话框 -->
    <el-dialog v-model="showImportDialog" title="导入评估数据集" width="640px" class="eval-dialog">
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
    <el-dialog v-model="showExperimentDialog" title="创建评估实验" width="540px" class="eval-dialog">
      <el-form :model="experimentForm" label-width="100px">
        <el-form-item label="实验名称">
          <el-input v-model="experimentForm.name" placeholder="如：全量优化效果评估" />
        </el-form-item>
        <el-form-item label="选择数据集">
          <el-select v-model="experimentForm.datasetId" placeholder="选择数据集" style="width:100%">
            <el-option v-for="ds in datasets" :key="ds.id" :label="`${ds.name} (${ds.testCaseCount}题)`" :value="ds.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="RAG 策略">
          <div class="strategy-switches">
            <div class="strategy-item">
              <el-switch v-model="experimentForm.queryRewriteEnabled" />
              <span class="strategy-label">查询改写</span>
              <span class="strategy-desc">LLM 改写模糊提问</span>
            </div>
            <div class="strategy-item">
              <el-switch v-model="experimentForm.hybridSearchEnabled" />
              <span class="strategy-label">混合检索</span>
              <span class="strategy-desc">向量 + 关键词 RRF 融合</span>
            </div>
            <div class="strategy-item">
              <el-switch v-model="experimentForm.rerankerEnabled" />
              <span class="strategy-label">Reranker 精排</span>
              <span class="strategy-desc">Cross-Encoder 重排序</span>
            </div>
          </div>
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
const activeTab = ref('experiments')
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
    let payload
    if (Array.isArray(parsed)) {
      payload = {
        name: importForm.value.name,
        description: importForm.value.description,
        domain: importForm.value.domain,
        testCases: parsed
      }
    } else if (parsed.testCases) {
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
  if (score == null) return ''
  if (score >= 0.8) return 'score-high'
  if (score >= 0.6) return 'score-mid'
  return 'score-low'
}

function pct(v) {
  if (v == null) return '-'
  return (v * 100).toFixed(0) + '%'
}

function metricColor(v) {
  if (v == null) return '#dcdfe6'
  if (v >= 0.8) return '#67c23a'
  if (v >= 0.6) return '#e6a23c'
  return '#f56c6c'
}
</script>

<style scoped>
.eval-container {
  padding: 24px;
  max-width: 1280px;
  margin: 0 auto;
  height: 100%;
  overflow-y: auto;
}

/* ── Dashboard Cards ── */
.dashboard-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,.04), 0 1px 2px rgba(0,0,0,.06);
  border: 1px solid #f0f0f0;
  transition: all .2s;
}

.stat-card:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,.08);
  transform: translateY(-1px);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.card-datasets .stat-icon { background: linear-gradient(135deg, #e8f4fd, #d1ecf9); color: #409eff; }
.card-experiments .stat-icon { background: linear-gradient(135deg, #fdf0e6, #fce4cc); color: #e6a23c; }
.card-cases .stat-icon { background: linear-gradient(135deg, #e8f8e8, #d4f0d4); color: #67c23a; }
.stat-card.score-high .stat-icon { background: linear-gradient(135deg, #e8f8e8, #d4f0d4); color: #67c23a; }
.stat-card.score-mid .stat-icon { background: linear-gradient(135deg, #fdf0e6, #fce4cc); color: #e6a23c; }
.stat-card.score-low .stat-icon { background: linear-gradient(135deg, #fde8e8, #fcd4d4); color: #f56c6c; }

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1a1a2e;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #8c8c9a;
  margin-top: 2px;
}

.stat-card.score-high .stat-value { color: #67c23a; }
.stat-card.score-mid .stat-value { color: #e6a23c; }
.stat-card.score-low .stat-value { color: #f56c6c; }

/* ── Action Bar ── */
.action-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.action-btn-primary {
  border-radius: 8px;
  font-weight: 500;
}

.action-btn-success {
  border-radius: 8px;
  font-weight: 500;
}

/* ── Section with Tabs ── */
.section {
  background: #fff;
  border-radius: 12px;
  padding: 0;
  margin-bottom: 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,.04), 0 1px 2px rgba(0,0,0,.06);
  border: 1px solid #f0f0f0;
  overflow: hidden;
}

.tab-header {
  display: flex;
  border-bottom: 1px solid #f0f0f0;
  padding: 0 20px;
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 14px 20px;
  font-size: 14px;
  font-weight: 500;
  color: #8c8c9a;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all .2s;
}

.tab-item:hover { color: #409eff; }

.tab-item.active {
  color: #409eff;
  border-bottom-color: #409eff;
}

.tab-item svg { opacity: .5; }
.tab-item.active svg { opacity: 1; }

.tab-count {
  background: #f0f2f5;
  color: #8c8c9a;
  font-size: 11px;
  padding: 1px 6px;
  border-radius:10px;
  font-weight: 600;
}

.tab-item.active .tab-count {
  background: #ecf5ff;
  color: #409eff;
}

/* ── Experiment Cards ── */
.experiment-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
  padding: 20px;
}

.exp-card {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 16px;
  cursor: pointer;
  transition: all .2s;
  position: relative;
}

.exp-card:hover {
  border-color: #c0c4cc;
  box-shadow: 0 4px 12px rgba(0,0,0,.06);
  transform: translateY(-1px);
}

.exp-card.exp-running {
  border-color: #e6a23c;
  border-left: 3px solid #e6a23c;
}

.exp-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 10px;
}

.exp-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
  flex: 1;
  margin-right: 8px;
}

.exp-card-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #8c8c9a;
  margin-bottom: 10px;
}

.exp-dataset {
  display: flex;
  align-items: center;
  gap: 4px;
}

.exp-card-config {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
}

.config-chip {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #f0f2f5;
  color: #8c8c9a;
}

.config-chip.active {
  background: #ecf5ff;
  color: #409eff;
}

/* ── Progress Bar ── */
.exp-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.progress-bar {
  flex: 1;
  height: 6px;
  background: #f0f2f5;
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #e6a23c, #f0c78a);
  border-radius: 3px;
  transition: width .3s;
}

.progress-text {
  font-size: 12px;
  color: #8c8c9a;
  flex-shrink: 0;
}

/* ── Mini Metrics ── */
.exp-metrics-mini {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 4px 12px;
  margin-bottom: 10px;
}

.metric-mini-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.metric-mini-label {
  font-size: 10px;
  font-weight: 700;
  color: #8c8c9a;
  width: 12px;
  flex-shrink: 0;
}

.metric-mini-bar {
  flex: 1;
  height: 4px;
  background: #f0f2f5;
  border-radius: 2px;
  overflow: hidden;
}

.metric-mini-fill {
  height: 100%;
  border-radius: 2px;
  transition: width .3s;
}

.metric-mini-val {
  font-size: 11px;
  font-weight: 600;
  width: 32px;
  text-align: right;
  flex-shrink: 0;
}

/* ── Card Footer ── */
.exp-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid #f5f5f5;
}

.exp-score {
  font-size: 20px;
  font-weight: 700;
}

.exp-actions {
  display: flex;
  gap: 4px;
}

/* ── Empty State ── */
.empty-state {
  text-align: center;
  padding: 48px 20px;
  color: #c0c4cc;
}

.empty-icon { margin-bottom: 12px; opacity: .4; }
.empty-state p { font-size: 14px; }

/* ── Score Colors ── */
.score-high { color: #67c23a; }
.score-mid { color: #e6a23c; }
.score-low { color: #f56c6c; }

/* ── Dataset Table ── */
.eval-table {
  padding: 0 20px 20px;
}

/* ── Dialog Enhancements ── */
.eval-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 16px;
}

.strategy-switches {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.strategy-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #fafbfc;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
}

.strategy-label {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  min-width: 80px;
}

.strategy-desc {
  font-size: 12px;
  color: #8c8c9a;
}

.form-tip {
  margin-left: 12px;
  font-size: 13px;
  color: #909399;
}
</style>
