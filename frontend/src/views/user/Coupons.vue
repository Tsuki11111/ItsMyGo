<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fetchMyCoupons } from '@/api/coupon'
import type { UserCouponView } from '@/api/types'

const router = useRouter()

const TABS = [
  { key: 'UNUSED', label: '未使用' },
  { key: 'USED', label: '已使用' },
  { key: 'EXPIRED', label: '已过期' },
]

const activeTab = ref('UNUSED')
const items = ref<UserCouponView[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await fetchMyCoupons(page.value, size, activeTab.value)
    items.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

watch(activeTab, () => {
  page.value = 1
  load()
}, { immediate: true })

function onPageChange(next: number) {
  page.value = next
  load()
}

function formatDate(value: string): string {
  return value.replace('T', ' ').slice(0, 10)
}

function thresholdText(item: UserCouponView): string {
  return item.thresholdAmount > 0 ? `满 ¥${item.thresholdAmount.toFixed(2)} 可用` : '无门槛'
}
</script>

<template>
  <div class="block">
    <div class="head">
      <h2 class="block-title">我的优惠券</h2>
      <el-button type="primary" plain size="small" @click="router.push('/coupons')">
        去领券中心
      </el-button>
    </div>

    <div class="tabs">
      <button
        v-for="tab in TABS"
        :key="tab.key"
        type="button"
        class="tab"
        :class="{ active: tab.key === activeTab }"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </div>

    <div v-loading="loading" class="list">
      <el-empty v-if="!loading && items.length === 0" description="这里还没有优惠券">
        <el-button type="primary" @click="router.push('/coupons')">去领券</el-button>
      </el-empty>

      <article
        v-for="item in items"
        :key="item.id"
        class="coupon"
        :class="{ disabled: item.status !== 'UNUSED' }"
      >
        <div class="amount">
          <span class="symbol">¥</span>
          <span class="num">{{ item.discountAmount.toFixed(2) }}</span>
          <span class="threshold">{{ thresholdText(item) }}</span>
        </div>
        <div class="detail">
          <p class="name">{{ item.couponName }}</p>
          <p class="scope">{{ item.merchantId ? `${item.merchantName} · 店铺券` : '平台券 · 全场可用' }}</p>
          <p class="period">有效期至 {{ formatDate(item.validTo) }}</p>
          <p v-if="item.status === 'USED'" class="state">已用于订单 #{{ item.usedOrderId }}</p>
          <p v-else-if="item.status === 'EXPIRED'" class="state">已过期</p>
        </div>
      </article>
    </div>

    <div v-if="total > size" class="pager">
      <el-pagination
        layout="prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        background
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.block {
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 20px 22px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.block-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--c-text);
}

.tabs {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid var(--c-border);
}

.tab {
  padding: 10px 0;
  font-size: 14px;
  font-family: inherit;
  color: var(--c-text-2);
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
}

.tab.active {
  color: var(--c-pink);
  border-bottom-color: var(--c-pink);
  font-weight: 600;
}

.list {
  min-height: 120px;
  padding-top: 16px;
}

.coupon {
  display: flex;
  align-items: stretch;
  margin-bottom: 12px;
  border: 1px solid var(--c-border);
  border-radius: 12px;
  overflow: hidden;
}

.coupon.disabled {
  opacity: 0.6;
}

.amount {
  width: 150px;
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 14px;
  background: var(--c-pink-tint);
  color: var(--c-pink);
}

.symbol {
  font-size: 14px;
}

.num {
  font-family: ui-monospace, 'SF Mono', Consolas, monospace;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
}

.threshold {
  margin-top: 4px;
  font-size: 11px;
  color: var(--c-text-2);
}

.detail {
  padding: 14px 18px;
}

.name {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
}

.period,
.state {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
}

.scope {
  margin: 5px 0 0;
  font-size: 12px;
  color: var(--c-pink);
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}
</style>
