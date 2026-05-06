<template>
  <div class="detail-container">
    <!-- 返回按钮 -->
    <div class="back-bar">
      <el-button link @click="router.push('/eval')">
        <el-icon><ArrowLeft /></el-icon> 返回评估列表
      </el-button>
    </div>

    <!-- 实验概览 -->
    <div class="overview-card" v-loading="loading">
      <div class="overview-header">
        <div>
          <h2>{{ experiment.name }}</h2>
          <div class="overview-meta">
            <el-tag :type="statusType(experiment.status)" size="small">{{ statusLabel(experiment.status) }}</el-tag>
            <span>数据集：{{ experiment.datasetName }}</span>
            <span>配置：
              <el-tag size="small" :type="experiment.queryRewriteEnabled ? 'success' : 'info'">改写</el-tag>
              <el-tag size="small" :type="experiment.hybridSearchEnabled ? 'success' : 'info'">混合</el-tag>
              <el-tag size="small" :type="experiment.rerankerEnabled ? 'success' : 'info'">精排</el-tag>
            </span>
          </div>
        </div>
        <div class="overview-progress" v-if="experiment.status === 'RUNNING'">
          <el-progress type="circle" :percentage="experiment.progressPercent" :width="80" />
          <div class="progress-text">{{ experiment.completedCases }}/{{ experiment.totalCases }}</div>
        </div>
      </div>

      <!-- 指标卡片 -->
      <div class="metric-cards" v-if="experiment.status === 'COMPLETED'">
        <div class="metric-card">
          <div class="metric-ring">
            <svg viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="#ebeef5" stroke-width="8" />
              <circle cx="50" cy="50" r="40" fill="none" :stroke="metricColor(experiment.avgContextPrecision)"
                stroke-width="8" stroke-linecap="round"
                :stroke-dasharray="`${(experiment.avgContextPrecision || 0) * 251.2} 251.2`"
                transform="rotate(-90 50 50)" />
            </svg>
            <div class="metric-value">{{ pct(experiment.avgContextPrecision) }}</div>
          </div>
          <div class="metric-name">Context Precision</div>
          <div class="metric-desc">检索精度</div>
        </div>
        <div class="metric-card">
          <div class="metric-ring">
            <svg viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="#ebeef5" stroke-width="8" />
              <circle cx="50" cy="50" r="40" fill="none" :stroke="metricColor(experiment.avgContextRecall)"
                stroke-width="8" stroke-linecap="round"
                :stroke-dasharray="`${(experiment.avgContextRecall || 0) * 251.2} 251.2`"
                transform="rotate(-90 50 50)" />
            </svg>
            <div class="metric-value">{{ pct(experiment.avgContextRecall) }}</div>
          </div>
          <div class="metric-name">Context Recall</div>
          <div class="metric-desc">检索召回</div>
        </div>
        <div class="metric-card">
          <div class="metric-ring">
            <svg viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="#ebeef5" stroke-width="8" />
              <circle cx="50" cy="50" r="40" fill="none" :stroke="metricColor(experiment.avgFaithfulness)"
                stroke-width="8" stroke-linecap="round"
                :stroke-dasharray="`${(experiment.avgFaithfulness || 0) * 251.2} 251.2`"
                transform="rotate(-90 50 50)" />
            </svg>
            <div class="metric-value">{{ pct(experiment.avgFaithfulness) }}</div>
          </div>
          <div class="metric-name">Faithfulness</div>
          <div class="metric-desc">忠实度</div>
        </div>
        <div class="metric-card">
          <div class="metric-ring">
            <svg viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="#ebeef5" stroke-width="8" />
              <circle cx="50" cy="50" r="40" fill="none" :stroke="metricColor(experiment.avgAnswerRelevancy)"
                stroke-width="8" stroke-linecap="round"
                :stroke-dasharray="`${(experiment.avgAnswerRelevancy || 0) * 251.2} 251.2`"
                transform="rotate(-90 50 50)" />
            </svg>
            <div class="metric-value">{{ pct(experiment.avgAnswerRelevancy) }}</div>
          </div>
          <div class="metric-name">Answer Relevancy</div>
          <div class="metric-desc">答案相关性</div>
        </div>
        <div class="metric-card overall">
          <div class="metric-ring">
            <svg viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="#ebeef5" stroke-width="8" />
              <circle cx="50" cy="50" r="40" fill="none" :stroke="metricColor(experiment.overallScore)"
                stroke-width="8" stroke-linecap="round"
                :stroke-dasharray="`${(experiment.overallScore || 0) * 251.2} 251.2`"
                transform="rotate(-90 50 50)" />
            </svg>
            <div class="metric-value">{{ pct(experiment.overallScore) }}</div>
          </div>
          <div class="metric-name">Overall Score</div>
          <div class="metric-desc">综合分</div>
        </div>
      </div>
    </div>

    <!-- 结果明细 -->
    <div class="section" v-if="results.length > 0">
      <h3 class="section-title">评估结果明细</h3>
      <el-table :data="results" stripe class="eval-table" @expand-change="handleExpandChange">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-content">
              <div class="expand-row">
                <div class="expand-col">
                  <h4>生成的回答</h4>
                  <div class="answer-box">{{ row.generatedAnswer || '-' }}</div>
                </div>
                <div class="expand-col">
                  <h4>标准答案</h4>
                  <div class="answer-box gt">{{ row.groundTruthAnswer || '-' }}</div>
                </div>
              </div>
              <div class="expand-row" v-if="row.retrievedContexts && row.retrievedContexts.length > 0">
                <div class="expand-full">
                  <h4>检索到的上下文 ({{ row.retrievedContexts.length }}条)</h4>
                  <div v-for="(ctx, i) in row.retrievedContexts" :key="i" class="context-item">
                    <span class="context-idx">#{{ i + 1 }}</span>
                    <span class="context-text">{{ ctx.substring(0, 200) }}{{ ctx.length > 200 ? '...' : '' }}</span>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="问题" min-width="200">
          <template #default="{ row }">
            {{ row.question ? row.question.substring(0, 60) + (row.question.length > 60 ? '...' : '') : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="Precision" width="100" align="center">
          <template #default="{ row }">
            <span :class="scoreClass(row.contextPrecision)">{{ pct(row.contextPrecision) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Recall" width="100" align="center">
          <template #default="{ row }">
            <span :class="scoreClass(row.contextRecall)">{{ pct(row.contextRecall) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Faithful" width="100" align="center">
          <template #default="{ row }">
            <span :class="scoreClass(row.faithfulness)">{{ pct(row.faithfulness) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Relevancy" width="100" align="center">
          <template #default="{ row }">
            <span :class="scoreClass(row.answerRelevancy)">{{ pct(row.answerRelevancy) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="检索耗时" width="90" align="center">
          <template #default="{ row }">{{ row.retrievalTimeMs ? row.retrievalTimeMs + 'ms' : '-' }}</template>
        </el-table-column>
        <el-table-column label="评估耗时" width="90" align="center">
          <template #default="{ row }">{{ row.evalTimeMs ? row.evalTimeMs + 'ms' : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'danger'" size="small">
              {{ row.status === 'COMPLETED' ? '完成' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import request from '../utils/request'

const router = useRouter()
const route = useRoute()
const experimentId = route.params.experimentId

const experiment = ref({})
const results = ref([])
const loading = ref(false)
let pollTimer = null

onMounted(() => {
  loadExperiment()
  loadResults()
  pollTimer = setInterval(() => {
    if (experiment.value.status === 'RUNNING' || experiment.value.status === 'PENDING') {
      loadExperiment()
      loadResults()
    }
  }, 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})

async function loadExperiment() {
  loading.value = true
  try {
    const res = await request.get(`/eval/experiments/${experimentId}`)
    experiment.value = res.data || {}
  } catch (e) { /* ignore */ }
  loading.value = false
}

async function loadResults() {
  try {
    const res = await request.get(`/eval/experiments/${experimentId}/results`)
    results.value = res.data || []
  } catch (e) { /* ignore */ }
}

function handleExpandChange() { /* placeholder */ }

function pct(v) {
  if (v == null) return '-'
  return (v * 100).toFixed(1) + '%'
}

function metricColor(v) {
  if (v == null) return '#dcdfe6'
  if (v >= 0.8) return '#67c23a'
  if (v >= 0.6) return '#e6a23c'
  return '#f56c6c'
}

function scoreClass(v) {
  if (v == null) return ''
  if (v >= 0.8) return 'score-high'
  if (v >= 0.6) return 'score-mid'
  return 'score-low'
}

function statusType(s) {
  return { PENDING: 'info', RUNNING: 'warning', COMPLETED: 'success', FAILED: 'danger', CANCELLED: 'info' }[s] || 'info'
}

function statusLabel(s) {
  return { PENDING: '待执行', RUNNING: '运行中', COMPLETED: '已完成', FAILED: '失败', CANCELLED: '已取消' }[s] || s
}
</script>

<style scoped>
.detail-container {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
  height: 100%;
  overflow-y: auto;
}

.back-bar { margin-bottom: 16px; }

.overview-card {
  background: #fff;
  border-radius: 10px;
  padding: 24px;
  margin-bottom: 20px;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
}

.overview-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.overview-header h2 { font-size: 20px; color: #303133; margin-bottom: 8px; }

.overview-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #909399;
}

.progress-text { text-align: center; font-size: 12px; color: #909399; margin-top: 4px; }

.metric-cards {
  display: flex;
  justify-content: space-around;
  gap: 16px;
}

.metric-card {
  text-align: center;
  flex: 1;
}

.metric-card.overall {
  border-left: 1px solid #ebeef5;
  padding-left: 16px;
}

.metric-ring {
  position: relative;
  width: 80px;
  height: 80px;
  margin: 0 auto 8px;
}

.metric-ring svg { width: 100%; height: 100%; }

.metric-value {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 16px;
  font-weight: 700;
  color: #303133;
}

.metric-name { font-size: 13px; font-weight: 600; color: #303133; }
.metric-desc { font-size: 11px; color: #909399; }

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

.score-high { color: #67c23a; font-weight: 600; }
.score-mid { color: #e6a23c; font-weight: 600; }
.score-low { color: #f56c6c; font-weight: 600; }

.expand-content { padding: 12px 20px; }

.expand-row {
  display: flex;
  gap: 20px;
  margin-bottom: 16px;
}

.expand-col { flex: 1; }
.expand-full { width: 100%; }

.expand-content h4 {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}

.answer-box {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  color: #303133;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
}

.answer-box.gt { border-left: 3px solid #67c23a; }

.context-item {
  background: #fafafa;
  border-radius: 4px;
  padding: 8px 12px;
  margin-bottom: 6px;
  font-size: 12px;
  line-height: 1.5;
  display: flex;
  gap: 8px;
}

.context-idx {
  color: #409eff;
  font-weight: 600;
  flex-shrink: 0;
}

.context-text { color: #606266; }
</style>
