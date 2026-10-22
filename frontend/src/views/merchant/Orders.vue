<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrderDetailDrawer from '@/views/admin/components/OrderDetailDrawer.vue'
import {
  approveMerchantRefund,
  fetchMerchantOrders,
  rejectMerchantRefund,
  shipMerchantOrder,
} from '@/api/merchant'
import type { Order } from '@/api/types'
import { ORDER_STATUS_OPTIONS, orderStatusLabel, orderStatusTag } from '@/utils/orderStatus'

const orders = ref<Order[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref('')
const loading = ref(false)

const detailVisible = ref(false)
const detailOrderId = ref<number | null>(null)

async function load() {
  loading.value = true
  try {
    const result = await fetchMerchantOrders({
      page: page.value,
      size: size.value,
      status: status.value || undefined,
    })
    orders.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function search() {
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

  await shipMerchantOrder(row.id, trackingNo)
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
  await approveMerchantRefund(row.id)
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
  await rejectMerchantRefund(row.id)
  ElMessage.success('已驳回退款')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="page-head">
      <h2>订单管理</h2>
    </div>

    <div class="filters">
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
    </div>

    <el-table v-loading="loading" :data="orders" border>
      <el-table-column label="订单号" min-width="190">
        <template #default="{ row }">
          <span class="order-no">{{ row.orderNo }}</span>
        </template>
      </el-table-column>
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
          <el-button
            v-if="row.status === 'PAID'"
            link
            type="primary"
            @click="ship(row)"
          >
            发货
          </el-button>
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

    <OrderDetailDrawer
      v-model="detailVisible"
      :order-id="detailOrderId"
      merchant
      @changed="load"
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
