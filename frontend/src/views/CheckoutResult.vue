<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchOrder, payOrder } from '@/api/order'
import type { OrderView } from '@/api/types'

const route = useRoute()
const router = useRouter()

const orders = ref<OrderView[]>([])
const loading = ref(true)
const payingId = ref<number | null>(null)

function ids(): number[] {
  const raw = route.query.ids
  if (typeof raw !== 'string' || !raw) return []
  return raw.split(',').map(Number).filter((id) => Number.isFinite(id) && id > 0)
}

async function load() {
  loading.value = true
  try {
    orders.value = await Promise.all(ids().map((id) => fetchOrder(id)))
  } finally {
    loading.value = false
  }
}

async function pay(order: OrderView) {
  payingId.value = order.order.id
  try {
    const updated = await payOrder(order.order.id)
    const index = orders.value.findIndex((item) => item.order.id === updated.order.id)
    if (index >= 0) orders.value[index] = updated
    ElMessage.success('支付成功')
  } catch {
    await load()
  } finally {
    payingId.value = null
  }
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="result-page">
    <h1 class="page-title">下单成功</h1>
    <p class="subtitle">
      本次结算按店铺拆成了 {{ orders.length }} 个订单，请分别支付。
    </p>

    <el-empty v-if="!loading && orders.length === 0" description="没有找到订单">
      <el-button type="primary" @click="router.push('/user/orders')">去我的订单</el-button>
    </el-empty>

    <section v-for="view in orders" :key="view.order.id" class="block">
      <div class="order-head">
        <div>
          <p class="shop">{{ view.order.merchantName }}</p>
          <p class="order-no">{{ view.order.orderNo }}</p>
        </div>
        <el-tag type="warning" size="small">{{ view.statusLabel }}</el-tag>
      </div>

      <div class="order-body">
        <span class="time">{{ formatTime(view.order.createdAt) }}</span>
        <span class="count">共 {{ view.itemCount }} 件</span>
        <span class="price">
          <span class="price-symbol">¥</span>
          <span class="price-num">{{ view.order.totalAmount.toFixed(2) }}</span>
        </span>
      </div>

      <div class="actions">
        <el-button
          v-if="view.order.status === 'PENDING_PAYMENT'"
          type="primary"
          :loading="payingId === view.order.id"
          @click="pay(view)"
        >
          立即支付
        </el-button>
        <el-button @click="router.push(`/order/${view.order.id}`)">查看详情</el-button>
      </div>
    </section>

    <div class="footer">
      <el-button @click="router.push('/user/orders')">去我的订单</el-button>
      <el-button type="primary" plain @click="router.push('/')">继续逛逛</el-button>
    </div>
  </div>
</template>

<style scoped>
.result-page {
  max-width: 760px;
  margin: 0 auto;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--c-text);
}

.subtitle {
  margin: 8px 0 18px;
  font-size: 13px;
  color: var(--c-text-2);
}

.block {
  margin-bottom: 14px;
  padding: 16px 20px;
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.order-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.shop {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
}

.order-no {
  margin: 4px 0 0;
  font-size: 12px;
  font-family: ui-monospace, 'SF Mono', Consolas, monospace;
  color: var(--c-text-3);
}

.order-body {
  display: flex;
  align-items: baseline;
  gap: 16px;
  margin-top: 10px;
}

.time,
.count {
  font-size: 12px;
  color: var(--c-text-3);
}

.price {
  margin-left: auto;
  color: var(--c-pink);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

.footer {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 20px;
}
</style>
