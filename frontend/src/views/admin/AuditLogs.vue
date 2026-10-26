<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchAdminAuditLogs } from '@/api/admin'
import type { AdminAuditView } from '@/api/types'

const rows = ref<AdminAuditView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const action = ref('')
const result = ref('')
const dateRange = ref<[string, string] | null>(null)
const loading = ref(false)
const detailVisible = ref(false)
const detailRow = ref<AdminAuditView | null>(null)

const ACTION_OPTIONS = [
  { value: 'USER_DISABLE', label: '禁用用户' },
  { value: 'USER_ENABLE', label: '启用用户' },
  { value: 'CATEGORY_CREATE', label: '新增分类' },
  { value: 'CATEGORY_UPDATE', label: '修改分类' },
  { value: 'CATEGORY_DELETE', label: '删除分类' },
  { value: 'PRODUCT_CREATE', label: '新增商品' },
  { value: 'PRODUCT_UPDATE', label: '修改商品' },
  { value: 'PRODUCT_DELETE', label: '删除商品' },
  { value: 'PRODUCT_SKU_SAVE', label: '保存商品规格' },
  { value: 'PRODUCT_BATCH_STATUS', label: '批量上下架商品' },
  { value: 'SKU_STOCK_ADJUST', label: '调整 SKU 库存' },
  { value: 'ORDER_SHIP', label: '订单发货' },
  { value: 'REFUND_APPROVE', label: '同意退款' },
  { value: 'REFUND_REJECT', label: '驳回退款' },
  { value: 'ORDER_CANCEL_EXPIRED', label: '取消超时订单' },
  { value: 'MERCHANT_APPROVE', label: '商户审核通过' },
  { value: 'MERCHANT_REJECT', label: '商户审核拒绝' },
  { value: 'MERCHANT_DISABLE', label: '禁用商户' },
  { value: 'MERCHANT_ENABLE', label: '启用商户' },
  { value: 'COUPON_CREATE', label: '新增优惠券' },
  { value: 'COUPON_UPDATE', label: '修改优惠券' },
  { value: 'REVIEW_VISIBILITY', label: '修改评价展示状态' },
  { value: 'FILE_UPLOAD', label: '上传文件' },
  { value: 'AGENT_EVAL_RUN', label: '启动智能体评测' },
  { value: 'AGENT_EVAL_STOP', label: '停止智能体评测' },
  { value: 'AGENT_EVAL_CASE_CREATE', label: '新建智能体评测用例' },
  { value: 'CHAT_REPLY', label: '平台消息回复' },
]

const RESULT_OPTIONS = [
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
]

function actionLabel(value: string): string {
  return ACTION_OPTIONS.find((item) => item.value === value)?.label ?? value
}

async function load() {
  loading.value = true
  try {
    const response = await fetchAdminAuditLogs({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
      action: action.value || undefined,
      result: result.value || undefined,
      dateFrom: dateRange.value?.[0],
      dateTo: dateRange.value?.[1],
    })
    rows.value = response.records
    total.value = response.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  keyword.value = ''
  action.value = ''
  result.value = ''
  dateRange.value = null
  page.value = 1
  load()
}

function onPageChange(next: number) {
  page.value = next
  load()
}

function onSizeChange(next: number) {
  size.value = next
  page.value = 1
  load()
}

function showDetail(row: AdminAuditView) {
  detailRow.value = row
  detailVisible.value = true
}

async function copyTrace(traceId: string) {
  if (!traceId) return
  try {
    await navigator.clipboard.writeText(traceId)
    ElMessage.success('已复制 traceId')
  } catch {
    ElMessage.warning('复制失败，请手动选择')
  }
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 19)
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="page-head">
      <h2>操作审计</h2>
      <span class="hint">只读记录；与运行日志通过 traceId 关联</span>
    </div>

    <div class="filters">
      <el-input
        v-model="keyword"
        placeholder="操作人 / 摘要 / 目标 / traceId / IP"
        clearable
        style="width: 260px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select
        v-model="action"
        clearable
        filterable
        allow-create
        default-first-option
        placeholder="全部动作"
        style="width: 180px"
        @change="search"
      >
        <el-option
          v-for="option in ACTION_OPTIONS"
          :key="option.value"
          :value="option.value"
          :label="option.label"
        />
      </el-select>
      <el-select
        v-model="result"
        clearable
        placeholder="全部结果"
        style="width: 120px"
        @change="search"
      >
        <el-option
          v-for="option in RESULT_OPTIONS"
          :key="option.value"
          :value="option.value"
          :label="option.label"
        />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width: 250px"
        @change="search"
      />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="actorUsername" label="操作人" width="120" />
      <el-table-column label="动作" min-width="160">
        <template #default="{ row }">
          <p class="action">{{ row.summary || actionLabel(row.action) }}</p>
          <p class="action-code">{{ row.action }}</p>
        </template>
      </el-table-column>
      <el-table-column label="目标" min-width="120">
        <template #default="{ row }">
          {{ row.targetType || '—' }}<span v-if="row.targetId"> #{{ row.targetId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
            {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="130" />
      <el-table-column label="traceId" width="170">
        <template #default="{ row }">
          <el-button v-if="row.traceId" link type="primary" @click="copyTrace(row.traceId)">
            {{ row.traceId.slice(0, 12) }}…
          </el-button>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">详情</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="没有符合条件的审计记录" />
      </template>
    </el-table>

    <div class="pager">
      <el-pagination
        :current-page="page"
        :page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <el-dialog v-model="detailVisible" title="审计详情" width="640px">
      <el-descriptions v-if="detailRow" :column="1" border size="small">
        <el-descriptions-item label="动作">
          {{ detailRow.summary }}（{{ detailRow.action }}）
        </el-descriptions-item>
        <el-descriptions-item label="操作人">
          {{ detailRow.actorUsername }}（ID {{ detailRow.actorUserId }}）
        </el-descriptions-item>
        <el-descriptions-item label="目标">
          {{ detailRow.targetType || '—' }}<span v-if="detailRow.targetId"> #{{ detailRow.targetId }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="结果">
          {{ detailRow.result === 'SUCCESS' ? '成功' : '失败' }}
        </el-descriptions-item>
        <el-descriptions-item v-if="detailRow.errorMessage" label="失败原因">
          {{ detailRow.errorMessage }}
        </el-descriptions-item>
        <el-descriptions-item label="IP">{{ detailRow.ip || '—' }}</el-descriptions-item>
        <el-descriptions-item label="User-Agent">
          {{ detailRow.userAgent || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="traceId">
          {{ detailRow.traceId || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="请求详情">
          {{ detailRow.detail || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatTime(detailRow.createdAt) }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-page {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
}

.page-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}

.page-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.action {
  margin: 0;
  font-size: 13px;
}

.action-code {
  margin: 3px 0 0;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  font-family: ui-monospace, 'SF Mono', Consolas, monospace;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
