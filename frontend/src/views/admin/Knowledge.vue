<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile, UploadInstance, UploadRawFile } from 'element-plus'
import { Delete, Refresh, UploadFilled } from '@element-plus/icons-vue'
import {
  fetchKnowledgeDocuments,
  fetchKnowledgeTask,
  fetchPlatformPolicies,
  fetchPlatformPolicy,
  revokeKnowledgeDocument,
  uploadKnowledgeFile,
  uploadPlatformPolicy,
} from '@/api/knowledge'
import type {
  KnowledgeDocument,
  PlatformPolicyDetail,
  PlatformPolicySummary,
} from '@/api/knowledge'

interface ImportTask {
  taskId: string
  filename: string
  status: string
  doneList: string[]
  runningList: string[]
}

const activeTab = ref<'docs' | 'policy'>('docs')
const documents = ref<KnowledgeDocument[]>([])
const loading = ref(false)
const uploading = ref(false)
const selectedFiles = ref<UploadRawFile[]>([])
const force = ref(false)
const tasks = ref<ImportTask[]>([])
const uploadRef = ref<UploadInstance>()
const uploadHint = ref('')

const policies = ref<PlatformPolicySummary[]>([])
const policyLoading = ref(false)
const policyDetail = ref<PlatformPolicyDetail | null>(null)
const policyDetailLoading = ref(false)
const policyUploadRef = ref<UploadInstance>()
const policyTargetKey = ref('refund')
const policyFile = ref<UploadRawFile | null>(null)
const policyUploading = ref(false)

const POLICY_TARGETS = [
  { key: 'terms', label: '用户协议' },
  { key: 'privacy', label: '隐私政策' },
  { key: 'refund', label: '退换货与售后政策' },
  { key: 'merchant', label: '商家入驻与经营规范' },
]

let pollTimer: number | undefined

const STATUS_CN: Record<string, string> = {
  pending: '排队中',
  processing: '处理中',
  completed: '已完成',
  failed: '失败',
}

const STAGES_PDF = [
  '开始上传文件', '检查文件', 'PDF转Markdown', 'Markdown图片处理', '文档切分',
  '主体名称识别', '向量生成', '导入向量库', '导入知识图谱',
]
const STAGES_MD = [
  '开始上传文件', '检查文件', 'Markdown图片处理', '文档切分',
  '主体名称识别', '向量生成', '导入向量库', '导入知识图谱',
]

function stagesOf(task: ImportTask): string[] {
  const isPdf = task.filename.toLowerCase().endsWith('.pdf')
    || task.doneList.includes('PDF转Markdown')
    || task.runningList.includes('PDF转Markdown')
  const base = isPdf ? STAGES_PDF : STAGES_MD
  const extra = [...new Set([...task.doneList, ...task.runningList])]
    .filter((stage) => !base.includes(stage))
  return [...base, ...extra]
}

function stageClass(task: ImportTask, stage: string): string {
  if (task.doneList.includes(stage)) return 'done'
  if (task.runningList.includes(stage)) return 'live'
  return ''
}

function statusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'completed') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'processing') return 'warning'
  return 'info'
}

function onFilesChanged(_file: UploadFile, files: UploadFile[]) {
  selectedFiles.value = files
    .map((item) => item.raw)
    .filter((file): file is UploadRawFile => Boolean(file))
}

function formatTime(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function renderPolicyContent(content: string): string {
  return DOMPurify.sanitize(marked.parse(content || '', { async: false }))
}

async function loadDocuments() {
  loading.value = true
  try {
    const result = await fetchKnowledgeDocuments()
    documents.value = result.documents
  } finally {
    loading.value = false
  }
}

async function startUpload() {
  if (selectedFiles.value.length === 0) {
    ElMessage.warning('请先选择 PDF / Markdown 文件')
    return
  }
  uploading.value = true
  uploadHint.value = ''
  const accepted: ImportTask[] = []
  const duplicates: string[] = []
  const failures: string[] = []
  try {
    for (const file of selectedFiles.value) {
      try {
        const result = await uploadKnowledgeFile(file, force.value)
        for (const taskId of result.taskIds) {
          accepted.push({
            taskId,
            filename: file.name,
            status: 'pending',
            doneList: [],
            runningList: [],
          })
        }
        for (const item of result.duplicates) {
          duplicates.push(item.filename)
        }
        for (const item of result.failed) {
          failures.push(`${item.filename}：${item.reason}`)
        }
      } catch {
        // 拦截器已提示具体错误，继续处理下一个文件
      }
    }

    tasks.value = [...accepted, ...tasks.value]
    uploadRef.value?.clearFiles()
    selectedFiles.value = []

    if (accepted.length) {
      uploadHint.value = `已受理 ${accepted.length} 个文件，正在后台导入；下方可查看进度。`
      ElMessage.success(`已受理 ${accepted.length} 个文件`)
      ensurePolling()
    }
    if (duplicates.length) {
      ElMessage.warning(`已导入过，已跳过：${duplicates.join('、')}；如需重导请勾选「重复也重新导入」`)
    }
    if (failures.length) {
      ElMessage.error(failures.join('；'))
    }
    if (!accepted.length && !duplicates.length && !failures.length) {
      uploadHint.value = '没有文件被受理'
    }
  } finally {
    uploading.value = false
  }
}

async function pollTasks() {
  const active = tasks.value.filter(
    (task) => task.status !== 'completed' && task.status !== 'failed',
  )
  if (active.length === 0) {
    stopPolling()
    return
  }
  let finished = false
  for (const task of active) {
    try {
      const status = await fetchKnowledgeTask(task.taskId)
      task.status = status.status
      task.doneList = status.doneList
      task.runningList = status.runningList
      if (status.status === 'completed' || status.status === 'failed') {
        finished = true
      }
    } catch {
      // 网络抖动跳过这一轮
    }
  }
  if (finished) void loadDocuments()
}

function ensurePolling() {
  if (pollTimer === undefined) {
    pollTimer = window.setInterval(pollTasks, 2000)
  }
}

function stopPolling() {
  if (pollTimer !== undefined) {
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }
}

async function loadPolicies() {
  policyLoading.value = true
  try {
    policies.value = await fetchPlatformPolicies()
    if (!policyDetail.value && policies.value.length) {
      await selectPolicy(policies.value[0])
    }
  } finally {
    policyLoading.value = false
  }
}

async function selectPolicy(row: PlatformPolicySummary) {
  policyDetailLoading.value = true
  try {
    policyDetail.value = await fetchPlatformPolicy(row.policyKey)
  } finally {
    policyDetailLoading.value = false
  }
}

function onPolicyFileChanged(_file: UploadFile, files: UploadFile[]) {
  policyFile.value = files
    .map((item) => item.raw)
    .filter((file): file is UploadRawFile => Boolean(file))[0] ?? null
}

async function startPolicyUpload() {
  if (!policyFile.value) {
    ElMessage.warning('请先选择一个 Markdown 政策文件')
    return
  }
  policyUploading.value = true
  try {
    const result = await uploadPlatformPolicy(policyFile.value, policyTargetKey.value)
    if (result.failed.length) {
      ElMessage.error(result.failed.map((item) => `${item.filename}：${item.reason}`).join('；'))
    } else {
      ElMessage.success('政策已写入 MySQL，正在同步向量库')
    }
    policyUploadRef.value?.clearFiles()
    policyFile.value = null
    await loadPolicies()
    const target = policies.value.find((item) => item.policyKey === policyTargetKey.value)
    if (target) await selectPolicy(target)
  } catch {
    // 拦截器已提示
  } finally {
    policyUploading.value = false
  }
}

function onTabChange(name: string | number) {
  if (name === 'policy' && policies.value.length === 0) {
    void loadPolicies()
  }
}

function refreshActive() {
  if (activeTab.value === 'policy') {
    void loadPolicies()
  } else {
    void loadDocuments()
  }
}

async function revoke(row: KnowledgeDocument) {
  try {
    await ElMessageBox.confirm(
      `撤回「${row.fileTitle}」会删除 Milvus 切片、知识图谱和 MinIO 文件，且不可恢复。确定撤回吗？`,
      '撤回知识库文档',
      { type: 'warning', confirmButtonText: '撤回', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await revokeKnowledgeDocument(row.fileTitle)
    ElMessage.success('已撤回')
    await loadDocuments()
  } catch {
    // 拦截器已提示
  }
}

onMounted(() => {
  void loadDocuments()
})

onBeforeUnmount(stopPolling)
</script>

<template>
  <div class="admin-page">
    <div class="page-head">
      <div>
        <h2>知识库</h2>
        <p class="page-desc">
          按知识库分别维护：产品文档供买小购检索回答；平台政策以 MySQL 章节为准，供政策问答引用。
        </p>
      </div>
      <el-button :icon="Refresh" @click="refreshActive">刷新</el-button>
    </div>

    <el-tabs v-model="activeTab" class="kb-tabs" @tab-change="onTabChange">
      <el-tab-pane label="产品文档知识库" name="docs">
    <section class="upload-card">
      <el-upload
        ref="uploadRef"
        drag
        multiple
        :auto-upload="false"
        accept=".pdf,.md"
        :on-change="onFilesChanged"
        :on-remove="onFilesChanged"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽 PDF / Markdown 到这里，或点击选择</div>
        <template #tip>
          <div class="el-upload__tip">
            单文件最大 200MB；默认按文件内容查重，重复文档会跳过。
          </div>
        </template>
      </el-upload>

      <div class="upload-actions">
        <el-checkbox v-model="force">重复也重新导入</el-checkbox>
        <el-button type="primary" :loading="uploading" :disabled="selectedFiles.length === 0" @click="startUpload">
          {{ uploading ? '上传中…' : `开始导入（${selectedFiles.length}）` }}
        </el-button>
      </div>
      <p v-if="uploadHint" class="upload-hint">{{ uploadHint }}</p>
    </section>

    <section v-if="tasks.length" class="task-panel">
      <div class="panel-title">导入任务</div>
      <div v-for="task in tasks" :key="task.taskId" class="task">
        <div class="task-head">
          <span class="task-name">{{ task.filename }}</span>
          <el-tag size="small" :type="statusType(task.status)">
            {{ STATUS_CN[task.status] || task.status || '排队中' }}
          </el-tag>
        </div>
        <div class="stages">
          <span
            v-for="stage in stagesOf(task)"
            :key="stage"
            class="stage"
            :class="stageClass(task, stage)"
          >
            <i class="dot"></i>{{ stage }}
          </span>
        </div>
      </div>
    </section>

    <section class="doc-panel">
      <div class="panel-title">已导入文档（{{ documents.length }}）</div>
      <el-table v-loading="loading" :data="documents" border>
        <el-table-column prop="fileTitle" label="文档" min-width="260" show-overflow-tooltip />
        <el-table-column prop="itemName" label="识别产品" min-width="180" show-overflow-tooltip />
        <el-table-column prop="chunkCount" label="切片" width="80" align="right" />
        <el-table-column prop="kgEntities" label="图谱实体" width="90" align="right" />
        <el-table-column label="导入时间" width="150">
          <template #default="{ row }">{{ formatTime(row.importedAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">
              {{ STATUS_CN[row.status] || row.status || '—' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button link type="danger" :icon="Delete" @click="revoke(row)">撤回</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="知识库还没有文档" />
        </template>
      </el-table>
    </section>
      </el-tab-pane>

      <el-tab-pane label="平台政策库" name="policy">
        <section class="policy-upload">
          <div class="policy-upload-hint">
            政策只支持上传 Markdown（.md）。上传后原文先写 MySQL，再由导入服务重建该政策的章节向量。
          </div>
          <div class="policy-upload-actions">
            <el-select v-model="policyTargetKey" class="policy-target" placeholder="选择目标政策">
              <el-option
                v-for="item in POLICY_TARGETS"
                :key="item.key"
                :label="item.label"
                :value="item.key"
              />
            </el-select>
            <el-upload
              ref="policyUploadRef"
              :auto-upload="false"
              accept=".md"
              :limit="1"
              :on-change="onPolicyFileChanged"
              :on-remove="onPolicyFileChanged"
            >
              <el-button>选择 Markdown</el-button>
            </el-upload>
            <el-button
              type="primary"
              :loading="policyUploading"
              :disabled="!policyFile"
              @click="startPolicyUpload"
            >
              上传并同步
            </el-button>
          </div>
        </section>

        <div class="policy-layout">
          <section class="policy-list">
            <div class="panel-title">政策（{{ policies.length }}）</div>
            <el-table
              v-loading="policyLoading"
              :data="policies"
              border
              highlight-current-row
              @row-click="selectPolicy"
            >
              <el-table-column prop="title" label="政策" min-width="180" show-overflow-tooltip />
              <el-table-column prop="category" label="分类" width="110" />
              <el-table-column prop="version" label="版本" width="70" align="center" />
              <el-table-column prop="sectionCount" label="章节" width="70" align="right" />
              <template #empty>
                <el-empty description="还没有平台政策" />
              </template>
            </el-table>
          </section>

          <section v-loading="policyDetailLoading" class="policy-detail">
            <template v-if="policyDetail">
              <div class="policy-detail-head">
                <div>
                  <h3>{{ policyDetail.title }}</h3>
                  <p class="policy-meta">
                    {{ policyDetail.category }} · v{{ policyDetail.version }} ·
                    更新于 {{ formatTime(policyDetail.updatedAt) }}
                  </p>
                </div>
                <el-tag size="small" type="success">MySQL 原文</el-tag>
              </div>
              <div class="policy-sections">
                <article
                  v-for="section in policyDetail.sections"
                  :key="section.sectionNo"
                  class="policy-section"
                >
                  <h4>{{ section.sectionNo }}. {{ section.heading }}</h4>
                  <div
                    v-if="section.content"
                    class="policy-content"
                    v-html="renderPolicyContent(section.content)"
                  ></div>
                  <p v-else class="policy-content">（本节无正文）</p>
                </article>
              </div>
            </template>
            <el-empty v-else description="从左侧选择一份政策查看章节" />
          </section>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.admin-page {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
}

.kb-tabs {
  margin-top: 4px;
}

.policy-layout {
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.policy-upload {
  margin-bottom: 14px;
  padding: 12px 14px;
  background: #fafafa;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.policy-upload-hint {
  margin-bottom: 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.policy-upload-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.policy-target {
  width: 220px;
}

.policy-list {
  min-width: 0;
}

.policy-detail {
  min-height: 320px;
  padding: 16px;
  background: #fafafa;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.policy-detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.policy-detail-head h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.policy-meta {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.policy-sections {
  max-height: 560px;
  overflow-y: auto;
  margin-top: 12px;
}

.policy-section {
  padding: 10px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.policy-section:last-child {
  border-bottom: 0;
}

.policy-section h4 {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 600;
}

.policy-content {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
  word-break: break-word;
}

.policy-content :deep(p) {
  margin: 0 0 6px;
}

.policy-content :deep(ul),
.policy-content :deep(ol) {
  margin: 0 0 6px;
  padding-left: 20px;
}

.policy-content :deep(table) {
  width: 100%;
  margin: 6px 0;
  border-collapse: collapse;
  font-size: 12px;
}

.policy-content :deep(th),
.policy-content :deep(td) {
  padding: 4px 6px;
  border: 1px solid var(--el-border-color-lighter);
  text-align: left;
}

@media (max-width: 1100px) {
  .policy-layout {
    grid-template-columns: 1fr;
  }
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
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.upload-card {
  padding: 16px;
  background: #fafafa;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.upload-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 12px;
}

.upload-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.task-panel,
.doc-panel {
  margin-top: 18px;
}

.panel-title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
}

.task {
  padding: 12px;
  margin-bottom: 10px;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.task-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.task-name {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stages {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-top: 10px;
}

.stage {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.stage .dot {
  width: 6px;
  height: 6px;
  background: var(--el-border-color);
  border-radius: 50%;
}

.stage.done {
  color: var(--el-color-success);
}

.stage.done .dot {
  background: var(--el-color-success);
}

.stage.live {
  color: var(--el-color-primary);
  font-weight: 600;
}

.stage.live .dot {
  background: var(--el-color-primary);
  box-shadow: 0 0 0 3px var(--el-color-primary-light-8);
}
</style>
