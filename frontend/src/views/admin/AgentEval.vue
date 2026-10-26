<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, Refresh, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import {
  fetchAgentEvalCases,
  fetchAgentEvalJob,
  fetchAgentEvalJobs,
  fetchAgentEvalResult,
  fetchAgentEvalResults,
  startAgentEval,
  stopAgentEvalJob,
} from '@/api/agentEval'
import type {
  AgentEvalCase,
  AgentEvalEvidence,
  AgentEvalJob,
  AgentEvalRecall,
  AgentEvalResult,
  AgentEvalResultFile,
  AgentEvalRow,
} from '@/api/agentEval'

const loading = ref(false)
const resultLoading = ref(false)
const starting = ref(false)
const stopping = ref(false)
const cases = ref<AgentEvalCase[]>([])
const judgeModel = ref('')
const configErrors = ref<string[]>([])
const warnings = ref<string[]>([])
const results = ref<AgentEvalResultFile[]>([])
const selectedResultName = ref('')
const result = ref<AgentEvalResult | null>(null)
const job = ref<AgentEvalJob | null>(null)
const detailVisible = ref(false)
const detailRow = ref<AgentEvalRow | null>(null)
const reportVisible = ref(false)

const form = reactive({
  cases: [] as string[],
  limit: 3,
  runs: 1,
  web: false,
  keep: false,
})

let pollTimer: number | undefined

const ACTIVE_STATUSES = ['queued', 'running']
const STATUS_META: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' | 'primary' }> = {
  queued: { label: '排队中', type: 'info' },
  running: { label: '运行中', type: 'warning' },
  completed: { label: '已完成', type: 'success' },
  stopped: { label: '已停止', type: 'warning' },
  failed: { label: '失败', type: 'danger' },
}

const active = computed(() => Boolean(job.value && ACTIVE_STATUSES.includes(job.value.status)))
const jobPercent = computed(() => Math.max(0, Math.min(100, Number(job.value?.percent ?? 0))))
const caseCount = computed(() => {
  if (form.cases.length) return form.cases.length
  if (form.limit > 0) return Math.min(form.limit, cases.value.length)
  return cases.value.length
})
const estimate = computed(() => {
  const runs = Math.max(1, Math.min(10, Number(form.runs) || 1))
  return `预计回放 ${caseCount.value * runs} 次（${caseCount.value} 条 × ${runs} 次）`
})
const summary = computed(() => result.value?.summary ?? null)
const rows = computed(() => result.value?.rows ?? [])
const metrics = computed(() => {
  const s = summary.value
  if (!s) return []
  return [
    {
      key: 'groundedness',
      label: 'Groundedness',
      value: fmtPercent(s.groundedness_mean),
      hint: `≥0.9 通过率 ${fmtPercent(s.groundedness_pass_rate)}`,
      tone: s.groundedness_mean != null && s.groundedness_mean >= 0.9 ? 'good' : 'bad',
    },
    {
      key: 'relevance',
      label: '相关性',
      value: fmtNumber(s.answer_relevance_mean, 1),
      hint: '满分 5',
      tone: '',
    },
    {
      key: 'recall',
      label: 'Recall@K',
      value: fmtPercent(s.recall_mean),
      hint: `MRR ${fmtNumber(s.mrr_mean, 3)} · ${s.recall_cases ?? 0} 条有 gold`,
      tone: '',
    },
    {
      key: 'refusal',
      label: '拒答准确率',
      value: fmtPercent(s.refusal_accuracy),
      hint: '仅期望拒答的用例',
      tone: '',
    },
    {
      key: 'judge',
      label: 'Judge',
      value: `${s.judge_ok ?? 0}/${s.runs ?? 0}`,
      hint: s.judge_errors ? `${s.judge_errors} 条失败` : '全部成功',
      tone: s.judge_errors ? 'bad' : 'good',
    },
    {
      key: 'cost',
      label: '成本 / 耗时',
      value: `¥${fmtNumber(s.total_cost, 4)}`,
      hint: formatDuration(s.total_elapsed_ms),
      tone: '',
    },
  ]
})

function fmtNumber(value: number | null | undefined, digits = 3): string {
  if (value == null || !Number.isFinite(Number(value))) return '—'
  return Number(value).toFixed(digits)
}

function fmtPercent(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(Number(value))) return '—'
  return `${(Number(value) * 100).toFixed(1)}%`
}

function formatDuration(ms: number | null | undefined): string {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms} ms`
  const seconds = ms / 1000
  if (seconds < 60) return `${seconds.toFixed(1)} s`
  return `${Math.floor(seconds / 60)}m ${Math.round(seconds % 60)}s`
}

function formatTime(epochSeconds: number | null | undefined): string {
  if (epochSeconds == null) return '—'
  const date = new Date(Number(epochSeconds) * 1000)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function statusMeta(status: string) {
  return STATUS_META[status] ?? { label: status || '—', type: 'info' as const }
}

function scoreClass(value: number | null | undefined): string {
  if (value == null) return 'score-empty'
  if (value < 0.6) return 'score-bad'
  if (value < 0.9) return 'score-warn'
  return 'score-good'
}

function outcomeType(outcome?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (outcome === 'answered') return 'success'
  if (outcome === 'error') return 'danger'
  if (outcome === 'no_match' || outcome === 'rejected' || outcome === 'blocked') return 'warning'
  return 'info'
}

function refusalText(value: boolean | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return value ? '✓' : '✗'
}

function recallText(recall?: AgentEvalRecall): string {
  if (!recall?.eligible) return '—'
  return recall.recall === 1 ? '命中' : '未命中'
}

function noteOf(row: AgentEvalRow): string {
  return row.judge_error || row.judge?.notes || ''
}

function evidenceLabel(item: AgentEvalEvidence): string {
  return `[${item.source || '?'}] ${item.title || item.chunk_id || item.url || ''}`
}

function openDetail(row: AgentEvalRow) {
  detailRow.value = row
  detailVisible.value = true
}

async function loadCases() {
  loading.value = true
  try {
    const data = await fetchAgentEvalCases()
    cases.value = data.items
    judgeModel.value = data.judgeModel
    configErrors.value = data.configErrors
    warnings.value = data.warnings
  } finally {
    loading.value = false
  }
}

async function loadResults(preferred?: string) {
  try {
    const data = await fetchAgentEvalResults()
    results.value = data.items
    const target = preferred || selectedResultName.value || data.items[0]?.name
    if (target) {
      await loadResult(target)
    }
  } catch {
    // 拦截器已提示具体错误
  }
}

async function loadResult(name: string) {
  if (!name) return
  selectedResultName.value = name
  resultLoading.value = true
  try {
    result.value = await fetchAgentEvalResult(name)
  } catch {
    // 拦截器已提示具体错误
  } finally {
    resultLoading.value = false
  }
}

function ensurePolling() {
  if (pollTimer === undefined) {
    pollTimer = window.setInterval(() => void pollJob(), 2000)
  }
}

function stopPolling() {
  if (pollTimer !== undefined) {
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }
}

async function pollJob() {
  if (!job.value) {
    stopPolling()
    return
  }
  try {
    const next = await fetchAgentEvalJob(job.value.id)
    job.value = next
    if (ACTIVE_STATUSES.includes(next.status)) return
    stopPolling()
    if (next.status === 'completed') {
      ElMessage.success('评测完成')
    } else if (next.status === 'stopped') {
      ElMessage.warning('评测已停止')
    } else if (next.status === 'failed') {
      ElMessage.error(next.error || '评测失败')
    }
    await loadResults(next.result?.jsonl)
  } catch {
    stopPolling()
  }
}

async function start() {
  if (configErrors.value.length) {
    ElMessage.error(`judge 配置有问题：${configErrors.value.join('；')}`)
    return
  }
  if (!caseCount.value) {
    ElMessage.warning('没有可运行的评测用例')
    return
  }
  try {
    await ElMessageBox.confirm(
      `${estimate.value}，评测会真实调用大模型并产生费用。确定启动吗？`,
      '启动智能体评测',
      { type: 'warning', confirmButtonText: '开始评测', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  starting.value = true
  try {
    job.value = await startAgentEval({
      cases: form.cases,
      limit: form.limit,
      runs: form.runs,
      web: form.web,
      keep: form.keep,
    })
    ensurePolling()
    ElMessage.success('评测已启动')
  } catch {
    // 拦截器已提示具体错误
  } finally {
    starting.value = false
  }
}

async function stop() {
  if (!job.value) return
  try {
    await ElMessageBox.confirm(
      '当前用例会跑完，下一轮开始前退出；已产出的结果会保留。确定停止吗？',
      '停止智能体评测',
      { type: 'warning', confirmButtonText: '停止', cancelButtonText: '继续运行' },
    )
  } catch {
    return
  }
  stopping.value = true
  try {
    job.value = await stopAgentEvalJob(job.value.id)
    ElMessage.info('已请求停止')
  } catch {
    // 拦截器已提示具体错误
  } finally {
    stopping.value = false
  }
}

async function refreshAll() {
  await Promise.allSettled([loadCases(), loadResults()])
}

onMounted(async () => {
  await refreshAll()
  try {
    const jobs = await fetchAgentEvalJobs()
    const running = jobs.items.find((item) => ACTIVE_STATUSES.includes(item.status))
    if (running) {
      job.value = running
      ensurePolling()
    }
  } catch {
    // 拦截器已提示具体错误
  }
})

onBeforeUnmount(stopPolling)
</script>

<template>
  <div class="admin-page agent-eval-page">
    <div class="page-head">
      <div>
        <h2>智能体评测</h2>
        <p class="page-desc">
          回放真实问答，用独立 judge 模型检查答案是否被证据支撑。评测在 rag_helper 后台执行，
          浏览器只访问本管理端。
        </p>
      </div>
      <div class="head-actions">
        <el-tag effect="plain">judge {{ judgeModel || '未配置' }}</el-tag>
        <el-button :icon="Refresh" :loading="loading" @click="refreshAll">刷新</el-button>
      </div>
    </div>

    <el-alert
      v-if="configErrors.length"
      type="error"
      show-icon
      :closable="false"
      class="config-alert"
      title="judge 配置有问题，暂时不能启动评测"
    >
      <div v-for="item in configErrors" :key="item">{{ item }}</div>
    </el-alert>
    <el-alert
      v-else-if="warnings.length"
      type="warning"
      show-icon
      :closable="false"
      class="config-alert"
      title="评测配置提示"
    >
      <div v-for="item in warnings" :key="item">{{ item }}</div>
    </el-alert>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="8">
        <el-card class="config-card" shadow="never" v-loading="loading">
          <template #header>
            <div class="card-head">
              <span>新建评测</span>
              <span class="muted">{{ cases.length }} 条用例</span>
            </div>
          </template>

          <el-form label-position="top">
            <el-form-item label="用例范围">
              <el-select
                v-model="form.cases"
                multiple
                filterable
                clearable
                collapse-tags
                collapse-tags-tooltip
                placeholder="不选用例 = 按条数上限截取"
                class="full-width"
              >
                <el-option
                  v-for="item in cases"
                  :key="item.id"
                  :label="item.id"
                  :value="item.id"
                >
                  <div class="case-option">
                    <span>{{ item.id }}</span>
                    <span class="case-question">{{ item.question }}</span>
                  </div>
                </el-option>
              </el-select>
              <div class="field-hint">可多选；不选则按下方的条数上限截取。</div>
            </el-form-item>

            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="条数上限">
                  <el-input-number
                    v-model="form.limit"
                    :min="0"
                    :max="100"
                    :disabled="form.cases.length > 0"
                    controls-position="right"
                    class="full-width"
                  />
                  <div class="field-hint">0 = 全部；明确选用例时不生效。</div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="每例重复">
                  <el-input-number
                    v-model="form.runs"
                    :min="1"
                    :max="10"
                    controls-position="right"
                    class="full-width"
                  />
                  <div class="field-hint">1–10 次，用于观察稳定性。</div>
                </el-form-item>
              </el-col>
            </el-row>

            <div class="switch-row">
              <el-checkbox v-model="form.web">允许联网搜索</el-checkbox>
              <el-checkbox v-model="form.keep">保留评测会话现场</el-checkbox>
            </div>

            <el-button
              type="primary"
              :icon="VideoPlay"
              :loading="starting"
              :disabled="active || configErrors.length > 0"
              class="run-button"
              @click="start"
            >
              开始评测
            </el-button>
            <div class="estimate">{{ estimate }} · 评测会真实产生模型费用</div>
          </el-form>
        </el-card>

        <el-card v-if="job" class="job-card" shadow="never">
          <template #header>
            <div class="card-head">
              <span>当前任务</span>
              <el-tag size="small" :type="statusMeta(job.status).type">
                {{ statusMeta(job.status).label }}
              </el-tag>
            </div>
          </template>
          <el-progress
            :percentage="jobPercent"
            :status="job.status === 'failed' ? 'exception' : job.status === 'completed' ? 'success' : undefined"
          />
          <div class="job-meta">
            <span>{{ job.done }} / {{ job.total }}</span>
            <span>{{ formatDuration(job.elapsedMs) }}</span>
          </div>
          <div v-if="job.currentCase" class="job-current">正在跑：{{ job.currentCase }}</div>
          <el-alert
            v-if="job.error"
            type="error"
            :closable="false"
            show-icon
            class="job-error"
            :title="job.error"
          />
          <el-button
            v-if="active"
            type="danger"
            plain
            :icon="VideoPause"
            :loading="stopping"
            class="stop-button"
            @click="stop"
          >
            停止评测
          </el-button>
        </el-card>

        <el-card class="history-card" shadow="never">
          <template #header>
            <div class="card-head">
              <span>历史报告</span>
              <el-button link type="primary" :icon="Refresh" @click="loadResults()">刷新</el-button>
            </div>
          </template>
          <el-scrollbar v-if="results.length" max-height="260px">
            <button
              v-for="item in results"
              :key="item.name"
              type="button"
              class="history-item"
              :class="{ active: selectedResultName === item.name }"
              @click="loadResult(item.name)"
            >
              <span class="history-name">{{ item.name }}</span>
              <span class="history-meta">
                <span>{{ formatTime(item.mtime) }}</span>
                <span>{{ item.rows }} 条</span>
              </span>
            </button>
          </el-scrollbar>
          <el-empty v-else :image-size="54" description="还没有评测报告" />
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="16">
        <el-card v-if="result" class="result-card" shadow="never" v-loading="resultLoading">
          <template #header>
            <div class="card-head result-head">
              <div>
                <div class="result-title">{{ result.name }}</div>
                <div class="muted">{{ result.rows.length }} 条结果</div>
              </div>
              <el-button
                :icon="Document"
                :disabled="!result.reportContent"
                @click="reportVisible = true"
              >
                Markdown 报告
              </el-button>
            </div>
          </template>

          <div class="metric-grid">
            <div v-for="item in metrics" :key="item.key" class="metric" :class="item.tone">
              <div class="metric-label">{{ item.label }}</div>
              <div class="metric-value">{{ item.value }}</div>
              <div class="metric-hint">{{ item.hint }}</div>
            </div>
          </div>

          <el-table
            :data="rows"
            border
            stripe
            class="result-table"
            empty-text="没有结果行"
            @row-click="openDetail"
          >
            <el-table-column prop="case_id" label="用例" min-width="190" show-overflow-tooltip />
            <el-table-column prop="category" label="类型" width="100" />
            <el-table-column label="结局" width="105">
              <template #default="{ row }">
                <el-tag size="small" :type="outcomeType(row.rag_outcome)">
                  {{ row.rag_outcome || '—' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="接地性" width="90" align="right">
              <template #default="{ row }">
                <span :class="scoreClass(row.judge?.groundedness)">
                  {{ fmtPercent(row.judge?.groundedness) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="相关性" width="78" align="right">
              <template #default="{ row }">{{ fmtNumber(row.judge?.answer_relevance, 1) }}</template>
            </el-table-column>
            <el-table-column label="拒答" width="65" align="center">
              <template #default="{ row }">{{ refusalText(row.refusal_correct) }}</template>
            </el-table-column>
            <el-table-column label="Recall" width="82">
              <template #default="{ row }">{{ recallText(row.recall) }}</template>
            </el-table-column>
            <el-table-column label="claims" width="72" align="right">
              <template #default="{ row }">{{ row.judge?.total_claims ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="备注" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="muted">{{ noteOf(row) || '—' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card v-else class="result-card empty-card" shadow="never" v-loading="resultLoading">
          <el-empty description="从左侧开始一次评测，或打开历史报告" />
        </el-card>
      </el-col>
    </el-row>

    <el-drawer v-model="detailVisible" title="评测详情" size="62%">
      <template v-if="detailRow">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="用例">{{ detailRow.case_id }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detailRow.category }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ formatDuration(detailRow.elapsed_ms) }}</el-descriptions-item>
          <el-descriptions-item label="期望 / 实际">
            {{ detailRow.expected_outcome || '—' }} / {{ detailRow.rag_outcome || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="Groundedness">
            {{ fmtPercent(detailRow.judge?.groundedness) }}
          </el-descriptions-item>
          <el-descriptions-item label="相关性">
            {{ fmtNumber(detailRow.judge?.answer_relevance, 1) }} / 5
          </el-descriptions-item>
          <el-descriptions-item label="Recall / MRR">
            {{ detailRow.recall?.eligible
              ? `${detailRow.recall.recall ?? '—'} / ${fmtNumber(detailRow.recall.rr, 3)}`
              : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="claims">
            {{ detailRow.judge?.total_claims ?? '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="Judge 备注" :span="1">
            {{ detailRow.judge?.notes || '—' }}
          </el-descriptions-item>
        </el-descriptions>

        <section class="detail-section">
          <h4>问题</h4>
          <pre class="text-block">{{ detailRow.question }}</pre>
        </section>
        <section class="detail-section">
          <h4>最终答案</h4>
          <pre class="text-block">{{ detailRow.reply || '（空）' }}</pre>
        </section>

        <section v-if="detailRow.judge?.claims?.length" class="detail-section">
          <h4>Claim 判定（{{ detailRow.judge.claims.length }}）</h4>
          <el-table :data="detailRow.judge.claims" border size="small">
            <el-table-column prop="claim" label="claim" min-width="240" />
            <el-table-column label="结论" width="86">
              <template #default="{ row }">
                <span :class="row.supported ? 'score-good' : 'score-bad'">
                  {{ row.supported ? '支撑' : '未支撑' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="证据编号" width="120">
              <template #default="{ row }">{{ row.evidence_ids?.join(', ') || '—' }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="理由" min-width="220" show-overflow-tooltip />
          </el-table>
        </section>

        <section v-if="detailRow.evidence?.length" class="detail-section">
          <h4>参考证据（{{ detailRow.evidence.length }}）</h4>
          <el-collapse>
            <el-collapse-item
              v-for="(item, index) in detailRow.evidence"
              :key="`${item.source}-${item.chunk_id || item.url || index}`"
              :title="evidenceLabel(item)"
            >
              <pre class="text-block evidence-text">{{ item.text || '' }}</pre>
            </el-collapse-item>
          </el-collapse>
        </section>

        <section v-if="detailRow.errors?.length" class="detail-section">
          <h4>错误</h4>
          <ul class="error-list">
            <li v-for="item in detailRow.errors" :key="item">{{ item }}</li>
          </ul>
        </section>
      </template>
    </el-drawer>

    <el-dialog v-model="reportVisible" title="Markdown 报告" width="70%">
      <pre class="report-block">{{ result?.reportContent || '' }}</pre>
    </el-dialog>
  </div>
</template>

<style scoped>
.agent-eval-page {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.page-desc {
  max-width: 720px;
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.7;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.config-alert {
  margin-bottom: 14px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 14px;
  font-weight: 600;
}

.result-head {
  align-items: flex-start;
}

.result-title {
  font-family: var(--el-font-family-mono, ui-monospace, monospace);
  font-size: 12px;
  word-break: break-all;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 400;
}

.full-width {
  width: 100%;
}

.case-option {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.case-question {
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.field-hint {
  margin-top: 4px;
  color: var(--el-text-color-placeholder);
  font-size: 11px;
  line-height: 1.45;
}

.switch-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 20px;
  margin: 2px 0 16px;
}

.run-button {
  width: 100%;
}

.estimate {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
  line-height: 1.6;
}

.job-card,
.history-card {
  margin-top: 16px;
}

.job-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-family: var(--el-font-family-mono, ui-monospace, monospace);
  font-size: 11px;
}

.job-current {
  margin-top: 8px;
  color: var(--el-text-color-regular);
  font-size: 12px;
  word-break: break-all;
}

.job-error {
  margin-top: 10px;
}

.stop-button {
  margin-top: 12px;
}

.history-item {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 9px 10px;
  margin-bottom: 6px;
  text-align: left;
  color: var(--el-text-color-regular);
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  cursor: pointer;
  transition: border-color .18s, background .18s;
}

.history-item:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.history-item.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.history-name {
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-family: var(--el-font-family-mono, ui-monospace, monospace);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.result-card {
  min-height: 300px;
}

.empty-card {
  display: flex;
  align-items: center;
  justify-content: center;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.metric {
  padding: 10px 11px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 7px;
  background: #fafafa;
}

.metric-label {
  color: var(--el-text-color-secondary);
  font-size: 10px;
  letter-spacing: .06em;
}

.metric-value {
  margin-top: 2px;
  color: var(--el-text-color-primary);
  font-family: var(--el-font-family-mono, ui-monospace, monospace);
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -.03em;
}

.metric-hint {
  margin-top: 1px;
  color: var(--el-text-color-placeholder);
  font-size: 10px;
  line-height: 1.4;
}

.metric.good .metric-value {
  color: var(--el-color-success);
}

.metric.bad .metric-value {
  color: var(--el-color-danger);
}

.result-table :deep(tbody tr) {
  cursor: pointer;
}

.score-empty {
  color: var(--el-text-color-placeholder);
}

.score-good {
  color: var(--el-color-success);
  font-weight: 600;
}

.score-warn {
  color: var(--el-color-warning);
  font-weight: 600;
}

.score-bad {
  color: var(--el-color-danger);
  font-weight: 600;
}

.detail-section {
  margin-top: 18px;
}

.detail-section h4 {
  margin: 0 0 7px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.text-block {
  max-height: 280px;
  margin: 0;
  padding: 11px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--el-text-color-regular);
  background: #fafafa;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  font-family: inherit;
  font-size: 12px;
  line-height: 1.7;
}

.evidence-text {
  max-height: 220px;
  font-family: var(--el-font-family-mono, ui-monospace, monospace);
  font-size: 11px;
}

.error-list {
  margin: 0;
  padding-left: 18px;
  color: var(--el-color-danger);
  font-size: 12px;
  line-height: 1.7;
}

.report-block {
  max-height: 65vh;
  margin: 0;
  padding: 12px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--el-text-color-regular);
  background: #fafafa;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  font-family: var(--el-font-family-mono, ui-monospace, monospace);
  font-size: 11px;
  line-height: 1.7;
}

@media (max-width: 1400px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .page-head {
    flex-direction: column;
  }

  .head-actions {
    align-self: flex-start;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
