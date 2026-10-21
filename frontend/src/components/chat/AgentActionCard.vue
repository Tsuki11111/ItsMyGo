<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshLeft, ShoppingCart } from '@element-plus/icons-vue'
import { cancelAgentAction, confirmAgentAction } from '@/api/agent'
import type {
  AgentActionView,
  AgentOrderResult,
  AgentOrderSummary,
  AgentRefundResult,
  AgentRefundSummary,
} from '@/api/types'

type TagType = 'success' | 'info' | 'warning' | 'danger'

const props = defineProps<{ action: AgentActionView }>()
const emit = defineEmits<{ updated: [action: AgentActionView] }>()

const busy = ref(false)
const isOrder = computed(() => props.action.type === 'CREATE_ORDER')
const pending = computed(() => props.action.status === 'PENDING_CONFIRM')
const canAct = computed(() => pending.value && !busy.value)
const orderSummary = computed(() => props.action.summary as AgentOrderSummary)
const refundSummary = computed(() => props.action.summary as AgentRefundSummary)
const orderResult = computed(() => props.action.result as AgentOrderResult | null)
const refundResult = computed(() => props.action.result as AgentRefundResult | null)
const hasPendingPayment = computed(() =>
  !!orderResult.value?.orders.some((order) => order.status === 'PENDING_PAYMENT'),
)

const title = computed(() => {
  const order = isOrder.value
  switch (props.action.status) {
    case 'PENDING_CONFIRM':
      return order ? '订单待确认' : '退款待确认'
    case 'EXECUTING':
      return order ? '正在下单' : '正在提交退款'
    case 'SUCCEEDED':
      return order ? '订单已创建' : '退款申请已提交'
    case 'FAILED':
      return order ? '下单失败' : '退款提交失败'
    case 'CANCELLED':
      return order ? '订单草稿已取消' : '退款草稿已取消'
    case 'EXPIRED':
      return order ? '订单草稿已过期' : '退款草稿已过期'
    default:
      return order ? '订单' : '退款'
  }
})

const statusText = computed(() => {
  switch (props.action.status) {
    case 'PENDING_CONFIRM':
      return '待确认'
    case 'EXECUTING':
      return '执行中'
    case 'SUCCEEDED':
      return '已完成'
    case 'FAILED':
      return '执行失败'
    case 'CANCELLED':
      return '已取消'
    case 'EXPIRED':
      return '已过期'
    default:
      return props.action.status
  }
})

const statusType = computed<TagType>(() => {
  switch (props.action.status) {
    case 'PENDING_CONFIRM':
      return 'warning'
    case 'SUCCEEDED':
      return 'success'
    case 'FAILED':
      return 'danger'
    default:
      return 'info'
  }
})

const expireText = computed(() => {
  const raw = props.action.expiresAt
  if (!raw) return ''
  return `请在 ${raw.replace('T', ' ').slice(0, 16)} 前确认`
})

async function confirm() {
  if (!canAct.value) return
  busy.value = true
  try {
    const updated = await confirmAgentAction(props.action.actionNo)
    emit('updated', updated)
    if (updated.status === 'SUCCEEDED') {
      ElMessage.success(isOrder.value ? '下单成功，请尽快支付' : '退款申请已提交')
    } else if (updated.status === 'EXPIRED') {
      ElMessage.warning('确认已过期，请重新发起')
    } else if (updated.status === 'FAILED') {
      ElMessage.error(updated.errorMessage || '执行失败，请稍后重试')
    }
  } catch {
    // http.ts 已经弹过业务错误，这里不重复提示
  } finally {
    busy.value = false
  }
}

async function cancel() {
  if (!canAct.value) return
  busy.value = true
  try {
    const updated = await cancelAgentAction(props.action.actionNo)
    emit('updated', updated)
    ElMessage.info('已取消')
  } catch {
    // 同上
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="action-card" :class="{ done: !pending }">
    <header class="action-head">
      <span class="action-icon">
        <el-icon><ShoppingCart v-if="isOrder" /><RefreshLeft v-else /></el-icon>
      </span>
      <div class="action-title">
        <p class="action-name">{{ title }}</p>
        <p class="action-hint">
          {{ pending ? expireText : statusText }}
        </p>
      </div>
      <el-tag size="small" :type="statusType">{{ statusText }}</el-tag>
    </header>

    <template v-if="isOrder">
      <div v-if="orderSummary.address" class="address">
        <div class="address-line">
          <strong>{{ orderSummary.address.receiver }}</strong>
          <span>{{ orderSummary.address.phone }}</span>
        </div>
        <p>{{ orderSummary.address.address }}</p>
      </div>

      <div class="groups">
        <div
          v-for="group in orderSummary.groups"
          :key="group.merchantId"
          class="group"
        >
          <p class="merchant">{{ group.merchantName || '店铺' }}</p>
          <div
            v-for="(item, index) in group.items"
            :key="index"
            class="line"
          >
            <span class="line-name">
              {{ item.productName }}
              <small v-if="item.specText">（{{ item.specText }}）</small>
            </span>
            <span class="line-qty">×{{ item.quantity }}</span>
            <span class="line-price">¥{{ item.subtotal || item.unitPrice }}</span>
          </div>
          <div class="group-total">小计 ¥{{ group.total }}</div>
        </div>
      </div>

      <div class="total-row">
        <span>共 {{ orderSummary.itemCount }} 件</span>
        <span>应付 <strong class="total">¥{{ orderSummary.totalAmount }}</strong></span>
      </div>
      <p v-if="pending" class="note">{{ orderSummary.note }}</p>
    </template>

    <template v-else>
      <div class="refund-head">
        <span>订单 {{ refundSummary.orderNo }}</span>
        <el-tag size="small" type="info">{{ refundSummary.statusLabel }}</el-tag>
      </div>
      <div class="groups">
        <div
          v-for="(item, index) in refundSummary.items"
          :key="index"
          class="line"
        >
          <span class="line-name">
            {{ item.productName }}
            <small v-if="item.specText">（{{ item.specText }}）</small>
          </span>
          <span class="line-qty">×{{ item.quantity }}</span>
          <span class="line-price">¥{{ item.subtotal || item.unitPrice }}</span>
        </div>
      </div>
      <div class="total-row">
        <span>退款金额</span>
        <strong class="total">¥{{ refundSummary.totalAmount }}</strong>
      </div>
      <p v-if="pending" class="note">{{ refundSummary.note }}</p>
    </template>

    <div v-if="action.status === 'SUCCEEDED'" class="result">
      <template v-if="isOrder && orderResult">
        <p v-for="order in orderResult.orders" :key="order.id" class="result-line">
          <span>订单 {{ order.orderNo }} · {{ order.statusLabel }} · ¥{{ order.totalAmount }}</span>
          <router-link
            v-if="order.status === 'PENDING_PAYMENT'"
            :to="`/order/${order.id}`"
            class="pay-link"
          >
            去支付
          </router-link>
        </p>
        <p v-if="hasPendingPayment" class="note">{{ orderResult.note }}</p>
      </template>
      <template v-else-if="!isOrder && refundResult">
        <p class="result-line">
          <template v-if="refundResult.status === 'REFUNDING'">
            {{ refundResult.note }}（当前状态：{{ refundResult.statusLabel }}）
          </template>
          <template v-else>
            当前状态：{{ refundResult.statusLabel }}
          </template>
        </p>
      </template>
    </div>

    <p v-if="action.status === 'FAILED' && action.errorMessage" class="error">
      {{ action.errorMessage }}
    </p>

    <div v-if="pending" class="actions">
      <el-button
        type="primary"
        size="small"
        :loading="busy"
        :disabled="!canAct"
        @click="confirm"
      >
        {{ isOrder ? '确认下单' : '确认提交退款申请' }}
      </el-button>
      <el-button size="small" :disabled="!canAct" @click="cancel">取消</el-button>
    </div>
  </section>
</template>

<style scoped>
.action-card {
  margin-bottom: 12px;
  padding: 14px;
  background: #fff;
  border: 1px solid #f2cede;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(214, 51, 108, 0.06);
}

.action-card.done {
  background: #fcfcfd;
}

.action-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.action-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex: none;
  font-size: 16px;
  color: #fff;
  background: linear-gradient(135deg, var(--c-pink), #f39ab9);
  border-radius: 9px;
}

.action-title {
  flex: 1;
  min-width: 0;
}

.action-name {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
}

.action-hint {
  margin: 2px 0 0;
  font-size: 11px;
  color: var(--c-text-3);
}

.address {
  margin-top: 12px;
  padding: 9px 11px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--c-text-2);
  background: var(--c-pink-tint);
  border-radius: 8px;
}

.address-line {
  display: flex;
  gap: 10px;
  color: var(--c-text);
}

.address p {
  margin: 2px 0 0;
}

.groups {
  margin-top: 10px;
}

.group {
  padding-top: 8px;
  border-top: 1px dashed var(--c-border);
}

.group + .group {
  margin-top: 8px;
}

.merchant {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--c-text-2);
}

.line {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 10px;
  align-items: baseline;
  padding: 3px 0;
  font-size: 13px;
  color: var(--c-text);
}

.line-name {
  min-width: 0;
  overflow-wrap: anywhere;
}

.line-name small {
  color: var(--c-text-3);
}

.line-qty,
.line-price {
  flex: none;
  color: var(--c-text-2);
}

.group-total {
  margin-top: 2px;
  font-size: 11px;
  color: var(--c-text-3);
  text-align: right;
}

.total-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 10px;
  padding-top: 8px;
  font-size: 13px;
  color: var(--c-text-2);
  border-top: 1px solid var(--c-border);
}

.total {
  font-size: 16px;
  color: var(--c-pink);
}

.note {
  margin: 8px 0 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--c-text-3);
}

.refund-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  font-size: 13px;
  color: var(--c-text);
}

.result {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--c-border);
}

.result-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 0 0 6px;
  font-size: 13px;
  color: var(--c-text);
}

.pay-link {
  color: var(--c-pink);
  font-weight: 600;
  text-decoration: none;
}

.error {
  margin: 8px 0 0;
  font-size: 12px;
  color: #d9534f;
}

.actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
</style>
