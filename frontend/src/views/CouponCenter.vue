<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { claimCoupon, fetchCouponCenter } from '@/api/coupon'
import { useBadgeStore } from '@/stores/badge'
import { useUserStore } from '@/stores/user'
import type { CouponView } from '@/api/types'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const badgeStore = useBadgeStore()

const coupons = ref<CouponView[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    coupons.value = await fetchCouponCenter()
    badgeStore.syncCoupons(coupons.value)
  } finally {
    loading.value = false
  }
}

async function claim(item: CouponView) {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  await claimCoupon(item.id)
  item.claimed = true
  item.remainingCount = Math.max(item.remainingCount - 1, 0)
  badgeStore.syncCoupons(coupons.value)
  ElMessage.success('领取成功')
}

function formatDate(value: string): string {
  return value.replace('T', ' ').slice(0, 10)
}

onMounted(load)
</script>

<template>
  <div class="coupon-page">
    <div class="head">
      <h1 class="title">领券中心</h1>
      <RouterLink to="/user/coupons" class="mine-link">我的优惠券</RouterLink>
    </div>

    <div v-loading="loading" class="list">
      <el-empty v-if="!loading && coupons.length === 0" description="暂时没有可领取的优惠券" />

      <article v-for="item in coupons" :key="item.id" class="coupon">
        <div class="amount">
          <span class="symbol">¥</span>
          <span class="num">{{ item.discountAmount.toFixed(2) }}</span>
          <span class="threshold">
            {{ item.thresholdAmount > 0 ? `满 ¥${item.thresholdAmount.toFixed(2)} 可用` : '无门槛' }}
          </span>
        </div>
        <div class="detail">
          <p class="name">{{ item.name }}</p>
          <p class="scope">{{ item.merchantId ? `${item.merchantName} · 店铺券` : '平台券 · 全场可用' }}</p>
          <p class="period">有效期至 {{ formatDate(item.validTo) }}</p>
          <p class="remain">剩余 {{ item.remainingCount }} 张</p>
        </div>
        <div class="action">
          <el-button v-if="item.claimed" disabled>已领取</el-button>
          <el-button v-else type="primary" @click="claim(item)">立即领取</el-button>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.coupon-page {
  max-width: 900px;
  margin: 0 auto;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--c-text);
}

.mine-link {
  font-size: 13px;
  color: var(--c-pink);
  text-decoration: none;
}

.list {
  min-height: 120px;
}

.coupon {
  display: flex;
  align-items: center;
  margin-bottom: 14px;
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.amount {
  width: 170px;
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 14px;
  background: var(--c-pink-tint);
  color: var(--c-pink);
}

.symbol {
  font-size: 14px;
}

.num {
  font-family: ui-monospace, 'SF Mono', Consolas, monospace;
  font-size: 30px;
  font-weight: 700;
  line-height: 1.1;
}

.threshold {
  margin-top: 4px;
  font-size: 11px;
  color: var(--c-text-2);
}

.detail {
  flex: 1;
  padding: 16px 20px;
}

.name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text);
}

.period,
.remain {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
}

.scope {
  margin: 5px 0 0;
  font-size: 12px;
  color: var(--c-pink);
}

.action {
  padding: 0 22px;
}
</style>
