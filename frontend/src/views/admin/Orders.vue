<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrderDetailDrawer from './components/OrderDetailDrawer.vue'
import {
  approveRefund,
  cancelExpiredOrders,
  exportAdminOrders,
  fetchAdminMerchants,
  fetchAdminOrders,
  rejectRefund,
  shipOrder,
} from '@/api/admin'
import type { AdminMerchantView, Order } from '@/api/types'
import { ORDER_STATUS_OPTIONS, orderStatusLabel, orderStatusTag } from '@/utils/orderStatus'

const orders = ref<Order[]>([])
const merchants = ref<AdminMerchantView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref('')
const merchantId = ref<number | null>(null)
const keyword = ref('')
const dateRange = ref<[string, string] | null>(null)
const loading = ref(false)
const exporting = ref(false)

const detailVisible = ref(false)
const detailOrderId = ref<number | null>(null)
const route = useRoute()

/** 取消超时未支付订单的阈值（分钟） */
const minutes = ref(30)

async function load() {
  loading.value = true
  try {
    const result = await fetchAdminOrders({
      page: page.value,
      size: size.value,
      status: status.value || undefined,
      merchantId: merchantId.value ?? undefined,
      keyword: keyword.value.trim() || undefined,
      dateFrom: dateRange.value?.[0],
      dateTo: dateRange.value?.[1],
    })
    orders.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

async function loadMerchants() {
  const result = await fetchAdminMerchants({ page: 1, size: 100 })
  merchants.value = result.records
}

function search() {
  page.value = 1
  load()
}

function reset() {
  status.value = ''
  merchantId.value = null
  keyword.value = ''
  dateRange.value = null
  page.value = 1
  load()
}

async function exportCsv() {
  exporting.value = true
  try {
    await exportAdminOrders({
      status: status.value || undefined,
      merchantId: merchantId.value ?? undefined,
      keyword: keyword.value.trim() || undefined,
      dateFrom: dateRange.value?.[0],
      dateTo: dateRange.value?.[1],
    })
  } finally {
    exporting.value = false
  }
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

function openDetail(row: Order) {
  detailOrderId.value = row.id
  detailVisible.value = true
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

async function ship(row: Order) {
  let trackingNo = ''
  try {
    const result = await ElMessageBox.prompt('请输入物流单号', `发货 · ${row.orderNo}`, {
      inputPattern: /\S+/,
      inputErrorMessage: '物流单号不能为空',
      confirmButtonText: '确认发货',
      cancelButtonText: '取消',
    })
    trackingNo = result.value.trim()
  } catch {
    return
  }

  await shipOrder(row.id, { trackingNo })
  ElMessage.success('已发货，等待买家收货')
  await load()
}

async function approve(row: Order) {
  try {
    await ElMessageBox.confirm('同意退款会回滚对应 SKU 的库存，确定吗？', `同意退款 · ${row.orderNo}`, {
      type: 'warning',
      confirmButtonText: '同意退款',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  await approveRefund(row.id)
  ElMessage.success('已同意退款')
  await load()
}

async function reject(row: Order) {
  try {
    await ElMessageBox.confirm('驳回后订单会回到申请退款前的状态。确定吗？', `驳回退款 · ${row.orderNo}`, {
      type: 'warning',
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  await rejectRefund(row.id)
  ElMessage.success('已驳回退款')
  await load()
}

async function cancelExpired() {
  try {
    await ElMessageBox.confirm(
      `将取消创建超过 ${minutes.value} 分钟仍未支付的订单，确定吗？`,
      '取消超时未支付订单',
      { type: 'warning', confirmButtonText: '执行', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  const count = await cancelExpiredOrders(minutes.value)
  ElMessage.success(count > 0 ? `已取消 ${count} 笔超时订单` : '没有超时未支付的订单')
  await load()
}

onMounted(async () => {
  if (route.query.merchantId) merchantId.value = Number(route.query.merchantId)
  await loadMerchants()
  await load()
})
</script>

<template>
  <div class="admin-page">
    <div class="page-head">
      <h2>订单管理</h2>
      <div class="head-actions">
        <span class="minutes-label">超时阈值</span>
        <el-input-number v-model="minutes" :min="1" size="small" style="width: 110px" />
        <el-button size="small" @click="cancelExpired">取消超时未支付订单</el-button>
      </div>
    </div>

    <div class="filters">
      <el-select
        v-model="merchantId"
        clearable
        filterable
        placeholder="全部店铺"
        style="width: 180px"
        @change="search"
      >
        <el-option
          v-for="merchant in merchants"
          :key="merchant.id"
          :label="merchant.name"
          :value="merchant.id"
        />
      </el-select>
      <el-select
        v-model="status"
        clearable
        placeholder="全部状态"
        style="width: 160px"
        @change="search"
      >
        <el-option
          v-for="option in ORDER_STATUS_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-input
        v-model="keyword"
        placeholder="订单号 / 收货人 / 电话 / 运单号"
        clearable
        style="width: 240px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="下单开始"
        end-placeholder="下单结束"
        style="width: 260px"
        @change="search"
      />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
      <el-button :loading="exporting" @click="exportCsv">导出 CSV</el-button>
    </div>

    <el-table v-loading="loading" :data="orders" border>
      <el-table-column label="订单号" min-width="190">
        <template #default="{ row }">
          <span class="order-no">{{ row.orderNo }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="merchantName" label="店铺" width="130" />
      <el-table-column prop="userId" label="用户 ID" width="90" align="center" />
      <el-table-column label="金额" width="110" align="right">
        <template #default="{ row }">¥{{ row.totalAmount.toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="orderStatusTag(row.status)" size="small">
            {{ orderStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="收货人" width="150">
        <template #default="{ row }">
          <p class="receiver">{{ row.receiver }}</p>
          <p class="phone">{{ row.phone }}</p>
        </template>
      </el-table-column>
      <el-table-column label="物流单号" width="160">
        <template #default="{ row }">{{ row.trackingNo || '—' }}</template>
      </el-table-column>
      <el-table-column label="下单时间" width="150">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <template v-if="row.status === 'PAID'">
            <el-button link type="primary" @click="ship(row)">发货</el-button>
          </template>
          <template v-else-if="row.status === 'REFUNDING'">
            <el-button link type="success" @click="approve(row)">同意退款</el-button>
            <el-button link type="danger" @click="reject(row)">驳回</el-button>
          </template>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="没有符合条件的订单" />
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

    <OrderDetailDrawer v-model="detailVisible" :order-id="detailOrderId" @changed="load" />
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
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.minutes-label {
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

.order-no {
  font-family: ui-monospace, 'SF Mono', Consolas, monospace;
  font-size: 12px;
}

.receiver {
  margin: 0;
  font-size: 13px;
}

.phone {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
