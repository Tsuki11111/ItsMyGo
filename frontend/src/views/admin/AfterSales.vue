<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  approveAdminAfterSale,
  confirmAdminAfterSale,
  createAdminAfterSaleReason,
  fetchAdminAfterSale,
  fetchAdminAfterSaleReasons,
  fetchAdminAfterSales,
  fetchAdminAfterSaleStats,
  rejectAdminAfterSale,
  updateAdminAfterSaleReason,
} from '@/api/afterSale'
import type { AfterSaleReasonView, AfterSaleStatsView, AfterSaleView } from '@/api/types'

const tab = ref<'list' | 'reasons'>('list')

/* ---------- 售后单 ---------- */
const records = ref<AfterSaleView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref('')
const keyword = ref('')
const loading = ref(false)
const stats = ref<AfterSaleStatsView | null>(null)

const drawerVisible = ref(false)
const detail = ref<AfterSaleView | null>(null)
const acting = ref(false)
const route = useRoute()

const STATUS_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'WAITING_RETURN', label: '待买家退货' },
  { value: 'RETURN_SHIPPED', label: '待收货' },
  { value: 'REFUNDED', label: '已退款' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CANCELLED', label: '已撤销' },
]
const TAG_TYPES: Record<string, string> = {
  REFUNDED: 'success',
  REJECTED: 'info',
  CANCELLED: 'info',
  PENDING_REVIEW: 'warning',
  WAITING_RETURN: 'warning',
  RETURN_SHIPPED: 'primary',
}

async function load() {
  loading.value = true
  try {
    const [result, stat] = await Promise.all([
      fetchAdminAfterSales({
        page: page.value,
        size: size.value,
        status: status.value || undefined,
        keyword: keyword.value.trim() || undefined,
      }),
      fetchAdminAfterSaleStats(),
    ])
    records.value = result.records
    total.value = result.total
    stats.value = stat
  } finally {
    loading.value = false
  }
}

async function openDetail(row: AfterSaleView) {
  detail.value = await fetchAdminAfterSale(row.id)
  drawerVisible.value = true
}

async function approve() {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm('平台介入：确认同意该售后吗？', '同意售后', { type: 'warning' })
  } catch {
    return
  }
  acting.value = true
  try {
    detail.value = await approveAdminAfterSale(detail.value.id)
    ElMessage.success('已同意')
    await load()
  } finally {
    acting.value = false
  }
}

async function reject() {
  if (!detail.value) return
  const { value } = await ElMessageBox.prompt('请填写驳回理由（买家可见）', '驳回售后', {
    inputValidator: (v: string) => (v && v.trim() ? true : '请填写驳回理由'),
  })
  acting.value = true
  try {
    detail.value = await rejectAdminAfterSale(detail.value.id, value.trim())
    ElMessage.success('已驳回')
    await load()
  } finally {
    acting.value = false
  }
}

async function confirmReceived() {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm('确认已收到买家退货并退款？', '确认收货', { type: 'warning' })
  } catch {
    return
  }
  acting.value = true
  try {
    detail.value = await confirmAdminAfterSale(detail.value.id)
    ElMessage.success('已确认收货并退款')
    await load()
  } finally {
    acting.value = false
  }
}

/* ---------- 原因字典 ---------- */
const reasons = ref<AfterSaleReasonView[]>([])
const reasonDialog = ref(false)
const reasonEditing = ref<AfterSaleReasonView | null>(null)
const reasonForm = reactive({ code: '', label: '', sort: 0, status: 1 })
const reasonSaving = ref(false)

async function loadReasons() {
  reasons.value = await fetchAdminAfterSaleReasons()
}

function openReason(reason?: AfterSaleReasonView) {
  reasonEditing.value = reason ?? null
  reasonForm.code = reason?.code ?? ''
  reasonForm.label = reason?.label ?? ''
  reasonForm.sort = reason?.sort ?? 0
  reasonForm.status = reason?.status ?? 1
  reasonDialog.value = true
}

async function saveReason() {
  if (!reasonForm.code.trim() || !reasonForm.label.trim()) {
    ElMessage.warning('编码与名称都要填')
    return
  }
  reasonSaving.value = true
  try {
    const payload = {
      code: reasonForm.code.trim().toUpperCase(),
      label: reasonForm.label.trim(),
      sort: reasonForm.sort,
      status: reasonForm.status,
    }
    if (reasonEditing.value) {
      await updateAdminAfterSaleReason(reasonEditing.value.id, payload)
    } else {
      await createAdminAfterSaleReason(payload)
    }
    ElMessage.success('已保存')
    reasonDialog.value = false
    await loadReasons()
  } finally {
    reasonSaving.value = false
  }
}

onMounted(async () => {
  await load()
  await loadReasons()
  // 站内通知直达：带 afterSaleId 时自动打开售后详情
  if (route.query.afterSaleId) {
    try {
      detail.value = await fetchAdminAfterSale(Number(route.query.afterSaleId))
      drawerVisible.value = true
    } catch {
      // 详情加载失败时停留在列表，拦截器已提示
    }
  }
})
</script>

<template>
  <div class="panel">
    <div class="head">
      <h2 class="title">售后管理</h2>
      <el-radio-group v-model="tab">
        <el-radio-button value="list">售后单</el-radio-button>
        <el-radio-button value="reasons">原因字典</el-radio-button>
      </el-radio-group>
    </div>

    <template v-if="tab === 'list'">
      <div class="filters">
        <span v-if="stats" class="stat">
          待审核 <b>{{ stats.counts.PENDING_REVIEW ?? 0 }}</b> ·
          待收货 <b>{{ stats.counts.RETURN_SHIPPED ?? 0 }}</b> ·
          总计 <b>{{ stats.total }}</b>
        </span>
        <el-input
          v-model="keyword"
          placeholder="售后单号 / 订单号"
          style="width: 200px"
          clearable
          @keyup.enter="load"
        />
        <el-select v-model="status" style="width: 150px" @change="load">
          <el-option
            v-for="option in STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="records" size="small">
        <el-table-column prop="afterSaleNo" label="售后单号" width="190" />
        <el-table-column prop="orderNo" label="订单号" width="190" />
        <el-table-column prop="merchantName" label="店铺" width="140" />
        <el-table-column label="商品" min-width="200">
          <template #default="{ row }">
            {{ row.items.map((item: { productName: string }) => item.productName).join('、') }}
          </template>
        </el-table-column>
        <el-table-column prop="typeLabel" label="类型" width="100" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="TAG_TYPES[row.status] || 'primary'">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请金额" width="110">
          <template #default="{ row }">¥{{ Number(row.applyAmount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="申请时间" width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          layout="prev, pager, next, total"
          @current-change="load"
        />
      </div>
    </template>

    <template v-else>
      <div class="filters">
        <el-button type="primary" @click="openReason()">新增原因</el-button>
      </div>
      <el-table :data="reasons" size="small">
        <el-table-column prop="code" label="编码" width="200" />
        <el-table-column prop="label" label="名称" min-width="180" />
        <el-table-column prop="sort" label="排序" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="openReason(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <el-drawer v-model="drawerVisible" title="售后详情" size="520px">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="售后单号">{{ detail.afterSaleNo }}</el-descriptions-item>
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="店铺">{{ detail.merchantName }}</el-descriptions-item>
          <el-descriptions-item label="类型 / 状态">
            {{ detail.typeLabel }} / {{ detail.statusLabel }}
          </el-descriptions-item>
          <el-descriptions-item label="原因">
            {{ detail.reasonLabel }}<span v-if="detail.reasonText">（{{ detail.reasonText }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="申请金额">¥{{ Number(detail.applyAmount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.rejectReason" label="驳回理由">
            {{ detail.rejectReason }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="detail.evidenceImages.length" class="evidence">
          <el-image
            v-for="url in detail.evidenceImages"
            :key="url"
            :src="url"
            fit="cover"
            class="evidence-img"
            :preview-src-list="detail.evidenceImages"
          />
        </div>

        <h4 class="sub-title">退款商品</h4>
        <el-table :data="detail.items" size="small">
          <el-table-column prop="productName" label="商品" min-width="180" />
          <el-table-column prop="quantity" label="数量" width="70" />
          <el-table-column label="退款金额" width="110">
            <template #default="{ row }">¥{{ Number(row.refundAmount).toFixed(2) }}</template>
          </el-table-column>
        </el-table>

        <h4 class="sub-title">处理进度</h4>
        <el-timeline>
          <el-timeline-item
            v-for="(item, index) in detail.timeline"
            :key="index"
            :timestamp="item.time"
          >
            <div>{{ item.title }}</div>
            <div v-if="item.detail" class="sub">{{ item.detail }}</div>
          </el-timeline-item>
        </el-timeline>

        <div class="actions">
          <el-button v-if="detail.canApprove" type="primary" :loading="acting" @click="approve">
            同意售后
          </el-button>
          <el-button v-if="detail.canReject" :loading="acting" @click="reject">驳回</el-button>
          <el-button
            v-if="detail.canConfirmReceived"
            type="primary"
            :loading="acting"
            @click="confirmReceived"
          >
            确认收货并退款
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="reasonDialog" :title="reasonEditing ? '编辑原因' : '新增原因'" width="420px">
      <el-form label-width="70px">
        <el-form-item label="编码">
          <el-input v-model="reasonForm.code" placeholder="大写字母 / 数字 / 下划线" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="reasonForm.label" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="reasonForm.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="reasonForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reasonDialog = false">取消</el-button>
        <el-button type="primary" :loading="reasonSaving" @click="saveReason">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel {
  background: #fff;
  border-radius: 10px;
  padding: 18px 20px;
  box-shadow: 0 2px 10px rgba(180, 140, 150, 0.08);
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.title {
  margin: 0;
  font-size: 16px;
}
.filters {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.stat {
  font-size: 13px;
  color: #666;
  margin-right: auto;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.evidence {
  display: flex;
  gap: 8px;
  margin: 12px 0;
}
.evidence-img {
  width: 76px;
  height: 76px;
  border-radius: 6px;
  border: 1px solid #f0e4e8;
}
.sub-title {
  margin: 16px 0 8px;
  font-size: 14px;
}
.sub {
  color: #999;
  font-size: 12px;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin: 16px 0 30px;
}
</style>
