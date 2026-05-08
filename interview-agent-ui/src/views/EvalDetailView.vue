<template>
  <div class="detail-container">
    <!-- 返回按钮 -->
    <div class="back-bar">
      <el-button link @click="router.push('/eval')" class="back-btn">
        <el-icon><ArrowLeft /></el-icon> 返回评估列表
      </el-button>
    </div>

    <!-- 实验概览 -->
    <div class="overview-card" v-loading="loading">
      <div class="overview-header">
        <div class="overview-info">
          <h2>{{ experiment.name }}</h2>
          <div class="overview-meta">
            <el-tag :type="statusType(experiment.status)" size="small" effect="plain">{{ statusLabel(experiment.status) }}</el-tag>
            <span class="meta-item">
              <svg viewBox="0 0 24 24" width="14" height="14"><path d="M20 6H4c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2z" fill="currentColor"/></svg>
              {{ experiment.datasetName }}
            </span>
            <span class="meta-item">
              <svg viewBox="0 0 24 24" width="14" height="14"><path d="M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z" fill="currentColor"/></svg>
              {{ formatTime(experiment.createdAt) }}
            </span>
          </div>
          <div class="overview-config">
            <span class="config-chip" :class="{ active: experiment.queryRewriteEnabled }">查询改写</span>
            <span class="config-chip" :class="{ active: experiment.hybridSearchEnabled }">混合检索</span>
            <span class="config-chip" :class="{ active: experiment.rerankerEnabled }">Reranker 精排</span>
          </div>
        </div>
        <div class="overview-progress" v-if="experiment.status === 'RUNNING'">
          <el-progress type="circle" :percentage="experiment.progressPercent" :width="80" />
          <div class="progress-text">{{ experiment.completedCases }}/{{ experiment.totalCases }}</div>
        </div>
      </div>

      <!-- 指标环形图 -->
      <div class="metric-rings" v-if="experiment.status === 'COMPLETED'">
        <div class="ring-card" v-for="m in metricDefs" :key="m.key">
          <div class="ring-svg">
            <svg viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="#f0f2f5" stroke-width="7" />
              <circle cx="50" cy="50" r="40" fill="none" :stroke="metricColor(experiment[m.key])"
                stroke-width="7" stroke-linecap="round"
                :stroke-dasharray="`${(experiment[m.key] || 0) * 251.33} 251.33`"
                class="ring-fill" />
            </svg>
            <div class="ring-value">{{ pct(experiment[m.key]) }}</div>
          </div>
          <div class="ring-label">{{ m.en }}</div>
          <div class="ring-desc">{{ m.zh }}</div>
        </div>
        <!-- 综合分 -->
        <div class="ring-card ring-overall">
          <div class="ring-svg">
            <svg viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="#f0f2f5" stroke-width="9" />
              <circle cx="50" cy="50" r="40" fill="none" :stroke="metricColor(experiment.overallScore)"
                stroke-width="9" stroke-linecap="round"
                :stroke-dasharray="`${(experiment.overallScore || 0) * 251.33} 251.33`"
                class="ring-fill" />
            </svg>
            <div class="ring-value ring-value-lg">{{ pct(experiment.overallScore) }}</div>
          </div>
          <div class="ring-label">Overall Score</div>
          <div class="ring-desc">综合分</div>
        </div>
      </div>
    </div>

    <!-- 结果明细 -->
    <div class="section" v-if="results.length > 0">
      <h3 class="section-title">评估结果明细</h3>
      <el-table :data="results" stripe class="eval-table" row-key="id" @expand-change="handleExpandChange">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-content">
              <div class="expand-row">
                <div class="expand-col">
                  <h4>
                    <svg viewBox="0 0 24 24" width="14" height="14"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" fill="currentColor"/></svg>
                    生成的回答
                  </h4>
                  <div class="answer-box generated">{{ row.generatedAnswer || '-' }}</div>
                </div>
                <div class="expand-col">
                  <h4>
                    <svg viewBox="0 0 24 24" width="14" height="14"><path d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm-1 7V3.5L18.5 9H13z" fill="currentColor"/></svg>
                    标准答案
                  </h4>
                  <div class="answer-box ground-truth">{{ row.groundTruthAnswer || '-' }}</div>
                </div>
              </div>
              <div class="expand-row" v-if="row.retrievedContexts && row.retrievedContexts.length > 0">
                <div class="expand-full">
                  <h4>
                    <svg viewBox="0 0 24 24" width="14" height="14"><path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5z" fill="currentColor"/></svg>
                    检索到的上下文 ({{ row.retrievedContexts.length }}条)
                  </h4>
                  <div v-for="(ctx, i) in row.retrievedContexts" :key="i" class="context-item">
                    <span class="context-idx">#{{ i + 1 }}</span>
                    <span class="context-text">{{ ctx.substring(0, 300) }}{{ ctx.length > 300 ? '...' : '' }}</span>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="问题" min-width="200">
          <template #default="{ row }">
            <span class="question-text" :title="row.question">{{ row.question ? row.question.substring(0, 60) + (row.question.length > 60 ? '...' : '') : '-' }}</span>
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
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'danger'" size="small" effect="plain">
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

const metricDefs = [
  { key: 'avgContextPrecision', en: 'Precision', zh: '检索精度' },
  { key: 'avgContextRecall', en: 'Recall', zh: '检索召回' },
  { key: 'avgFaithfulness', en: 'Faithfulness', zh: '忠实度' },
  { key: 'avgAnswerRelevancy', en: 'Relevancy', zh: '答案相关性' },
]

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

function formatTime(t) {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}
</script>

<style scoped>
.detail-container {
  padding: 24px;
  max-width: 1280px;
  margin: 0 auto;
  height: 100%;
  overflow-y: auto;
}

/* ── Back Bar ── */
.back-bar { margin-bottom: 16px; }
.back-btn { font-size: 13px; color: #8c8c9a; }
.back-btn:hover { color: #409eff; }

/* ── Overview Card ── */
.overview-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,.04), 0 1px 2px rgba(0,0,0,.06);
  border: 1px solid #f0f0f0;
}

.overview-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.overview-header h2 {
  font-size: 20px;
  font-weight: 700;
  color: #1a1a2e;
  margin-bottom: 8px;
}

.overview-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 13px;
  color: #8c8c9a;
  margin-bottom: 10px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.overview-config {
  display: flex;
  gap: 8px;
}

.config-chip {
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 4px;
  background: #f0f2f5;
  color: #8c8c9a;
  font-weight: 500;
}

.config-chip.active {
  background: #ecf5ff;
  color: #409eff;
}

.progress-text { text-align: center; font-size: 12px; color: #8c8c9a; margin-top: 4px; }

/* ── Metric Rings ── */
.metric-rings {
  display: flex;
  justify-content: space-around;
  gap: 12px;
  padding-top: 20px;
  border-top: 1px solid #f5f5f5;
}

.ring-card {
  text-align: center;
  flex: 1;
  padding: 8px;
  border-radius: 10px;
  transition: background .2s;
}

.ring-card:hover { background: #fafbfc; }

.ring-card.ring-overall {
  border-left: 1px solid #f0f0f0;
  padding-left: 20px;
}

.ring-svg {
  position: relative;
  width: 90px;
  height: 90px;
  margin: 0 auto 8px;
}

.ring-svg svg { width: 100%; height: 100%; }

.ring-fill {
  transform: rotate(-90deg);
  transform-origin: center;
  transition: stroke-dasharray 0.8s ease-out;
}

.ring-value {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 18px;
  font-weight: 700;
  color: #1a1a2e;
}

.ring-value-lg { font-size: 20px; }

.ring-label { font-size: 13px; font-weight: 600; color: #303133; }
.ring-desc { font-size: 11px; color: #8c8c9a; }

/* ── Section ── */
.section {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,.04), 0 1px 2px rgba(0,0,0,.06);
  border: 1px solid #f0f0f0;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f5f5f5;
}

/* ── Table ── */
.eval-table { width: 100%; }

.score-high { color: #67c23a; font-weight: 600; }
.score-mid { color: #e6a23c; font-weight: 600; }
.score-low { color: #f56c6c; font-weight: 600; }

.question-text { font-size: 13px; color: #303133; }

/* ── Expand Content ── */
.expand-content { padding: 16px 20px; background: #fafbfc; }

.expand-row {
  display: flex;
  gap: 20px;
  margin-bottom: 16px;
}

.expand-col { flex: 1; }
.expand-full { width: 100%; }

.expand-content h4 {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.answer-box {
  border-radius: 8px;
  padding: 14px;
  font-size: 13px;
  line-height: 1.7;
  color: #303133;
  max-height: 240px;
  overflow-y: auto;
  white-space: pre-wrap;
}

.answer-box.generated {
  background: #f0f7ff;
  border: 1px solid #d9ecff;
}

.answer-box.ground-truth {
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
  border-left: 3px solid #67c23a;
}

.context-item {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 6px;
  font-size: 12px;
  line-height: 1.6;
  display: flex;
  gap: 8px;
}

.context-idx {
  color: #409eff;
  font-weight: 700;
  flex-shrink: 0;
  font-size: 11px;
}

.context-text { color: #606266; }
</style>
