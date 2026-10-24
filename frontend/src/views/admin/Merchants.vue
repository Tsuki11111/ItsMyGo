<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ShipAddressDialog from '@/components/ShipAddressDialog.vue'
import MerchantOperationsDrawer from './components/MerchantOperationsDrawer.vue'
import {
  approveMerchant,
  disableMerchant,
  enableMerchant,
  fetchAdminMerchants,
  rejectMerchant,
} from '@/api/admin'
import type { AdminMerchantView } from '@/api/types'

const rows = ref<AdminMerchantView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref('')
const keyword = ref('')
const loading = ref(false)
const shipDialogVisible = ref(false)
const shipTarget = ref<AdminMerchantView | null>(null)
const operationsVisible = ref(false)
const operationsMerchantId = ref<number | null>(null)

const STATUS_OPTIONS = [
  { value: 'PENDING', label: '待审核' },
  { value: 'ACTIVE', label: '营业中' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'DISABLED', label: '已禁用' },
]

function statusLabel(value: string): string {
  return STATUS_OPTIONS.find((item) => item.value === value)?.label ?? value
}

function statusTag(value: string): 'warning' | 'success' | 'danger' | 'info' {
  return ({
    PENDING: 'warning',
    ACTIVE: 'success',
    REJECTED: 'danger',
    DISABLED: 'info',
  } as const)[value] ?? 'info'
}

async function load() {
  loading.value = true
  try {
    const result = await fetchAdminMerchants({
      page: page.value,
      size: size.value,
      status: status.value || undefined,
      keyword: keyword.value.trim() || undefined,
    })
    rows.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  status.value = ''
  keyword.value = ''
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

function openOperations(row: AdminMerchantView) {
  operationsMerchantId.value = row.id
  operationsVisible.value = true
}

async function approve(row: AdminMerchantView) {
  try {
    await ElMessageBox.confirm(`通过「${row.name}」的入驻申请？`, '审核通过', {
      type: 'success',
      confirmButtonText: '通过',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await approveMerchant(row.id)
  ElMessage.success('已通过审核')
  await load()
}

async function reject(row: AdminMerchantView) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请输入驳回原因', `驳回「${row.name}」`, {
      inputPattern: /\S+/,
      inputErrorMessage: '驳回原因不能为空',
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
    })
    reason = result.value.trim()
  } catch {
    return
  }
  await rejectMerchant(row.id, reason)
  ElMessage.success('已驳回申请')
  await load()
}

async function disable(row: AdminMerchantView) {
  try {
    await ElMessageBox.confirm(
      '禁用后该店铺商品对顾客隐藏，商家后台立即失效。确定吗？',
      `禁用「${row.name}」`,
      { type: 'warning', confirmButtonText: '禁用', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await disableMerchant(row.id)
  ElMessage.success('店铺已禁用')
  await load()
}

async function enable(row: AdminMerchantView) {
  try {
    await ElMessageBox.confirm(`重新启用「${row.name}」？`, '启用店铺', {
      type: 'warning',
      confirmButtonText: '启用',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await enableMerchant(row.id)
  ElMessage.success('店铺已启用')
  await load()
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

function openShipAddress(row: AdminMerchantView) {
  shipTarget.value = row
  shipDialogVisible.value = true
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="page-head">
      <h2>商户管理</h2>
    </div>

    <div class="filters">
      <el-select v-model="status" clearable placeholder="全部状态" style="width: 140px">
        <el-option
          v-for="option in STATUS_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-input
        v-model="keyword"
        placeholder="搜索店铺名"
        clearable
        style="width: 220px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column label="店铺" min-width="180">
        <template #default="{ row }">
          <p class="shop-name">{{ row.name }}</p>
          <p class="shop-desc">{{ row.description || '—' }}</p>
        </template>
      </el-table-column>
      <el-table-column prop="ownerUsername" label="店主账号" width="140" />
      <el-table-column label="联系人" width="150">
        <template #default="{ row }">
          <p>{{ row.contactName }}</p>
          <p class="muted">{{ row.contactPhone }}</p>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="近 30 天成交" width="120" align="right">
        <template #default="{ row }">¥{{ row.gmv30 }}</template>
      </el-table-column>
      <el-table-column prop="orders30" label="订单" width="70" align="right" />
      <el-table-column prop="refunding30" label="退款" width="70" align="right" />
      <el-table-column prop="onSaleProducts" label="在售" width="70" align="right" />
      <el-table-column label="低库存" width="80" align="right">
        <template #default="{ row }">
          <span :class="{ danger: row.lowStockSkus > 0 }">{{ row.lowStockSkus }}</span>
        </template>
      </el-table-column>
      <el-table-column label="发货地" min-width="150">
        <template #default="{ row }">
          <template v-if="row.shipAddress || row.shipCity || row.shipDistrict">
            <p class="ship-place">
              {{ row.shipProvince }}{{ row.shipCity }}{{ row.shipDistrict }}
            </p>
            <p class="muted">
              {{ row.shipAddress || '—' }}
              <span v-if="row.shipLng == null || row.shipLat == null">（未定位）</span>
            </p>
          </template>
          <span v-else class="muted">虚拟转运中心兜底</span>
        </template>
      </el-table-column>
      <el-table-column label="申请时间" width="150">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="340">
        <template #default="{ row }">
          <el-tag v-if="row.id === 1" type="info" size="small">平台自营</el-tag>
          <el-button link type="primary" @click="openOperations(row)">经营详情</el-button>
          <el-button link type="primary" @click="openShipAddress(row)">发货地址</el-button>
          <template v-if="row.id !== 1">
            <template v-if="row.status === 'PENDING'">
              <el-button link type="success" @click="approve(row)">通过</el-button>
              <el-button link type="danger" @click="reject(row)">驳回</el-button>
            </template>
            <el-button
              v-else-if="row.status === 'ACTIVE'"
              link
              type="danger"
              @click="disable(row)"
            >
              禁用
            </el-button>
            <el-button
              v-else-if="row.status === 'DISABLED'"
              link
              type="primary"
              @click="enable(row)"
            >
              启用
            </el-button>
            <span v-else class="muted">等待商家重新提交</span>
          </template>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="没有符合条件的商户" />
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

    <ShipAddressDialog
      v-model="shipDialogVisible"
      mode="admin"
      :merchant-id="shipTarget?.id ?? 0"
      :merchant-name="shipTarget?.name ?? ''"
      :initial="shipTarget"
      @saved="load"
    />

    <MerchantOperationsDrawer
      v-model="operationsVisible"
      :merchant-id="operationsMerchantId"
    />
  </div>
</template>

<style scoped>
.admin-page {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
}

.page-head {
  margin-bottom: 16px;
}

.page-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.filters {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.shop-name {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}

.shop-desc {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.muted {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ship-place {
  margin: 0;
  font-size: 13px;
}

.danger {
  color: var(--el-color-danger);
  font-weight: 600;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
