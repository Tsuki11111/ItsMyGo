<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Aim, MagicStick, Refresh, TrendCharts } from '@element-plus/icons-vue'
import {
  fetchKnowledgeGapReport,
  fetchKnowledgeGaps,
  generateKnowledgeGapDraft,
  publishKnowledgeGapDraft,
  updateKnowledgeGapStatus,
} from '@/api/knowledgeGap'
import type { KnowledgeGap, KnowledgeGapDraft } from '@/api/knowledgeGap'

const days = ref(7)
const minCount = ref(1)
const gaps = ref<KnowledgeGap[]>([])
const loading = ref(false)
const selected = ref<KnowledgeGap[]>([])

const reportVisible = ref(false)
const reportLoading = ref(false)
const reportMarkdown = ref('')

const draftVisible = ref(false)
const draftLoading = ref(false)
const publishLoading = ref(false)
const draft = ref<KnowledgeGapDraft | null>(null)
const draftTitle = ref('')
const draftMarkdown = ref('')

const STATUS_CN: Record<string, string> = {
  pending: '待处理',
  published: '已提交导入',
  resolved: '已补',
  ignored: '不处理',
}

const STATUS_TYPE: Record<string, 'success' | 'warning' | 'info' | 'danger'> = {
  pending: 'warning',
  published: 'info',
  resolved: 'success',
  ignored: 'info',
}

const totalCount = computed(() => gaps.value.reduce((sum, gap) => sum + gap.count, 0))

function formatTime(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

async function load() {
  loading.value = true
  try {
    const result = await fetchKnowledgeGaps(days.value, minCount.value)
    gaps.value = result.gaps
  } catch (error) {
    // 错误提示由 http 拦截器统一弹出；这里只保证表格不被旧数据误导
    gaps.value = []
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows: KnowledgeGap[]) {
  selected.value = rows
}

async function openReport() {
  reportVisible.value = true
  reportLoading.value = true
  reportMarkdown.value = ''
  try {
    const result = await fetchKnowledgeGapReport(days.value)
    reportMarkdown.value = result.markdown
  } finally {
    reportLoading.value = false
  }
}

async function openDraft(rows: KnowledgeGap[]) {
  if (rows.length === 0) {
    ElMessage.warning('请先选择要生成草稿的缺口')
    return
  }
  if (rows.length > 20) {
    ElMessage.warning('单次最多选择 20 个缺口')
    return
  }
  draftVisible.value = true
  draftLoading.value = true
  draft.value = null
  draftMarkdown.value = ''
  draftTitle.value = ''
  try {
    const result = await generateKnowledgeGapDraft({
      gapIds: rows.map((gap) => gap.gapId),
      days: days.value,
    })
    draft.value = result
    draftTitle.value = result.title
    draftMarkdown.value = result.markdown
  } catch (error) {
    draftVisible.value = false
  } finally {
    draftLoading.value = false
  }
}

async function publishDraft() {
  if (!draftTitle.value.trim()) {
    ElMessage.warning('请填写文档标题')
    return
  }
  if (!draftMarkdown.value.trim().startsWith('#')) {
    ElMessage.warning('草稿必须以一级标题（# 开头）')
    return
  }
  publishLoading.value = true
  try {
    const result = await publishKnowledgeGapDraft({
      title: draftTitle.value.trim(),
      markdown: draftMarkdown.value,
      gapIds: draft.value?.gapIds ?? [],
    })
    ElMessage.success(`已提交导入（taskId=${result.taskId}），进度可在「知识库」页查看`)
    if (!result.statusUpdated) {
      ElMessage.warning('导入任务已创建，但缺口状态回写失败，请稍后手动标记')
    }
    draftVisible.value = false
    await load()
  } finally {
    publishLoading.value = false
  }
}

async function markSelected(status: 'resolved' | 'ignored', rows: KnowledgeGap[]) {
  if (rows.length === 0) {
    ElMessage.warning('请先选择缺口')
    return
  }
  const label = STATUS_CN[status] ?? status
  try {
    await ElMessageBox.confirm(
      `把选中的 ${rows.length} 个缺口标记为「${label}」？`,
      '确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  const result = await updateKnowledgeGapStatus({
    gapIds: rows.map((gap) => gap.gapId),
    status,
  })
  ElMessage.success(`已更新 ${result.updated} 条`)
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2><el-icon><Aim /></el-icon> 知识缺口</h2>
        <p class="hint">
          来自线上「答不上来」的问题（无参考内容 / 仅靠联网）。生成的是<b>草稿</b>，
          必须人工核实后再发布入库。
        </p>
      </div>
      <div class="page-actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button :icon="TrendCharts" @click="openReport">生成周报</el-button>
      </div>
    </div>

    <div class="toolbar">
      <span class="field">
        统计区间
        <el-select v-model="days" style="width: 110px" @change="load">
          <el-option :value="7" label="最近 7 天" />
          <el-option :value="14" label="最近 14 天" />
          <el-option :value="30" label="最近 30 天" />
        </el-select>
      </span>
      <span class="field">
        最小频次
        <el-input-number v-model="minCount" :min="1" :max="100" @change="load" />
      </span>
      <span class="spacer" />
      <span class="summary">
        共 <b>{{ gaps.length }}</b> 个缺口 / <b>{{ totalCount }}</b> 轮问答
      </span>
      <el-button
        :icon="MagicStick"
        :disabled="selected.length === 0"
        @click="openDraft(selected)"
      >
        生成草稿（{{ selected.length }}）
      </el-button>
      <el-button :disabled="selected.length === 0" @click="markSelected('resolved', selected)">
        标记已补
      </el-button>
      <el-button :disabled="selected.length === 0" @click="markSelected('ignored', selected)">
        不处理
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="gaps"
      border
      stripe
      empty-text="这段时间没有知识缺口"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="46" />
      <el-table-column prop="question" label="问题" min-width="320" show-overflow-tooltip />
      <el-table-column label="产品" min-width="180">
        <template #default="{ row }">
          <el-tag
            v-for="product in row.products"
            :key="product"
            size="small"
            class="product-tag"
          >
            {{ product }}
          </el-tag>
          <span v-if="row.products.length === 0" class="muted">未识别</span>
        </template>
      </el-table-column>
      <el-table-column prop="count" label="频次" width="80" sortable />
      <el-table-column label="来源" width="110">
        <template #default="{ row }">
          本地 {{ row.local }} / 联网 {{ row.web }}
        </template>
      </el-table-column>
      <el-table-column prop="lastAt" label="最近出现" width="150">
        <template #default="{ row }">{{ formatTime(row.lastAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="STATUS_TYPE[row.status] ?? 'info'" size="small">
            {{ STATUS_CN[row.status] ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDraft([row])">草稿</el-button>
          <el-button link @click="markSelected('resolved', [row])">已补</el-button>
          <el-button link @click="markSelected('ignored', [row])">忽略</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="reportVisible" title="知识缺口周报" width="820px">
      <div v-loading="reportLoading">
        <pre class="report">{{ reportMarkdown || '（生成中…）' }}</pre>
      </div>
    </el-dialog>

    <el-dialog
      v-model="draftVisible"
      title="FAQ 草稿（未入库）"
      width="900px"
      :close-on-click-modal="false"
    >
      <div v-loading="draftLoading">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="草稿由模型起草，参数与步骤必须人工核实后才能发布"
          class="draft-alert"
        />
        <el-form label-width="80px">
          <el-form-item label="文档标题">
            <el-input v-model="draftTitle" maxlength="200" show-word-limit />
          </el-form-item>
          <el-form-item label="草稿内容">
            <el-input
              v-model="draftMarkdown"
              type="textarea"
              :autosize="{ minRows: 16, maxRows: 28 }"
            />
          </el-form-item>
        </el-form>
        <p class="hint">
          发布后走 rag_helper 的导入链路（与手动上传 Markdown 相同），
          导入进度可在「知识库」页按 taskId 查看。
        </p>
      </div>
      <template #footer>
        <el-button @click="draftVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishLoading" @click="publishDraft">
          核实完毕，发布入库
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  padding: 20px 24px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-head h2 {
  margin: 0 0 6px;
  font-size: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.hint {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.field {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.spacer {
  flex: 1;
}

.summary {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.product-tag {
  margin-right: 6px;
}

.muted {
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}

.report {
  margin: 0;
  max-height: 60vh;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
}

.draft-alert {
  margin-bottom: 16px;
}
</style>
