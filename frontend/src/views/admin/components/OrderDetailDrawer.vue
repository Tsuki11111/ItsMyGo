<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ProductImage from '@/components/ProductImage.vue'
import {
  approveRefund,
  fetchAdminOrder,
  rejectRefund,
  shipOrder,
} from '@/api/admin'
import {
  approveMerchantRefund,
  fetchMerchantOrder,
  rejectMerchantRefund,
  shipMerchantOrder,
} from '@/api/merchant'
import type { OrderView } from '@/api/types'
import { orderStatusTag } from '@/utils/orderStatus'

const props = defineProps<{
  modelValue: boolean
  orderId: number | null
  /** 商家后台复用：走 /api/merchant/orders/** */
  merchant?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
}>()

const view = ref<OrderView | null>(null)
const loading = ref(false)
const acting = ref(false)

async function load() {
  if (props.orderId === null) return
  loading.value = true
  try {
    const fetchFn = props.merchant ? fetchMerchantOrder : fetchAdminOrder
    view.value = await fetchFn(props.orderId)
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      view.value = null
      load()
    }
  },
)

function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

async function ship() {
  if (!view.value) return

  let trackingNo = ''
  try {
    const result = await ElMessageBox.prompt('请输入物流单号', '发货', {
      inputPattern: /\S+/,
      inputErrorMessage: '物流单号不能为空',
      confirmButtonText: '确认发货',
      cancelButtonText: '取消',
    })
    trackingNo = result.value.trim()
  } catch {
    return
  }

  acting.value = true
  try {
    view.value = props.merchant
      ? await shipMerchantOrder(view.value.order.id, trackingNo)
      : await shipOrder(view.value.order.id, { trackingNo })
    ElMessage.success('已发货')
    emit('changed')
  } finally {
    acting.value = false
  }
}

async function approve() {
  if (!view.value) return
  try {
    await ElMessageBox.confirm('同意退款会回滚对应 SKU 的库存，确定吗？', '同意退款', {
      type: 'warning',
      confirmButtonText: '同意退款',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  acting.value = true
  try {
    const approveFn = props.merchant ? approveMerchantRefund : approveRefund
    view.value = await approveFn(view.value.order.id)
    ElMessage.success('已同意退款')
    emit('changed')
  } finally {
    acting.value = false
  }
}

async function reject() {
  if (!view.value) return
  try {
    await ElMessageBox.confirm('驳回后订单会回到申请退款前的状态。确定吗？', '驳回退款', {
      type: 'warning',
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  acting.value = true
  try {
    const rejectFn = props.merchant ? rejectMerchantRefund : rejectRefund
    view.value = await rejectFn(view.value.order.id)
    ElMessage.success('已驳回退款')
    emit('changed')
  } finally {
    acting.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="订单详情"
    size="600px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading">
      <template v-if="view">
        <div class="drawer-head">
          <div>
            <p class="order-no">{{ view.order.orderNo }}</p>
            <p class="item-count">共 {{ view.itemCount }} 件商品</p>
          </div>
          <el-tag :type="orderStatusTag(view.order.status)" size="large">
            {{ view.statusLabel }}
          </el-tag>
        </div>

        <el-descriptions title="订单信息" :column="2" border size="small">
          <el-descriptions-item label="用户 ID">{{ view.order.userId }}</el-descriptions-item>
          <el-descriptions-item label="订单金额">
            ¥{{ view.order.totalAmount.toFixed(2) }}
          </el-descriptions-item>
          <el-descriptions-item label="下单时间">
            {{ formatTime(view.order.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="支付时间">
            {{ formatTime(view.order.paidAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="发货时间">
            {{ formatTime(view.order.shippedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="完成时间">
            {{ formatTime(view.order.completedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="物流单号" :span="2">
            {{ view.order.trackingNo || '—' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-descriptions title="收货信息" :column="1" border size="small" class="block">
          <el-descriptions-item label="收货人">{{ view.order.receiver }}</el-descriptions-item>
          <el-descriptions-item label="电话">{{ view.order.phone }}</el-descriptions-item>
          <el-descriptions-item label="地址">{{ view.order.address }}</el-descriptions-item>
        </el-descriptions>

        <p class="section-title">商品明细</p>
        <el-table :data="view.items" border size="small">
          <el-table-column label="商品" min-width="200">
            <template #default="{ row }">
              <div class="item">
                <div class="thumb">
                  <ProductImage :src="row.productImage" :name="row.productName" :id="row.productId" />
                </div>
                <div>
                  <p class="item-name">{{ row.productName }}</p>
                  <p v-if="row.specText" class="item-spec">{{ row.specText }}</p>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="单价" width="100" align="right">
            <template #default="{ row }">¥{{ row.unitPrice.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="70" align="center" />
          <el-table-column label="小计" width="110" align="right">
            <template #default="{ row }">¥{{ row.subtotal.toFixed(2) }}</template>
          </el-table-column>
        </el-table>

        <div v-if="view.order.status === 'PAID' || view.order.status === 'REFUNDING'" class="actions">
          <template v-if="view.order.status === 'PAID'">
            <el-button type="primary" :loading="acting" @click="ship">发货</el-button>
          </template>
          <template v-else>
            <el-button type="success" :loading="acting" @click="approve">同意退款</el-button>
            <el-button type="danger" plain :loading="acting" @click="reject">驳回退款</el-button>
          </template>
        </div>
      </template>

      <el-empty v-else-if="!loading" description="订单不存在" />
    </div>
  </el-drawer>
</template>

<style scoped>
.drawer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.order-no {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  font-family: ui-monospace, 'SF Mono', Consolas, monospace;
}

.item-count {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.block {
  margin-top: 16px;
}

.section-title {
  margin: 20px 0 10px;
  font-size: 14px;
  font-weight: 600;
}

.item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.thumb {
  width: 40px;
  height: 40px;
  flex: none;
  border-radius: 4px;
  overflow: hidden;
}

.item-name {
  margin: 0;
  font-size: 13px;
}

.item-spec {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
}
</style>
