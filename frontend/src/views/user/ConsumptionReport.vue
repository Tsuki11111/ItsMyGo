<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchConsumptionReport, type ConsumptionReport } from '@/api/report'

const router = useRouter()

const period = ref<'month' | 'year'>('month')

const now = new Date()
const monthValue = ref(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`)
const yearValue = ref(String(now.getFullYear()))

const report = ref<ConsumptionReport | null>(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    report.value = await fetchConsumptionReport({
      period: period.value,
      date: period.value === 'month' ? monthValue.value : yearValue.value,
    })
  } catch {
    // 错误提示由 http 拦截器统一弹出，这里只保证页面不显示旧数据
    report.value = null
  } finally {
    loading.value = false
  }
}

const label = computed(() => report.value?.label ?? '')
const hasOrders = computed(() => (report.value?.orderCount ?? 0) > 0)
const categories = computed(() => report.value?.categories ?? [])
const merchants = computed(() => report.value?.merchants ?? [])
const topProducts = computed(() => report.value?.topProducts ?? [])

const cards = computed(() => {
  const data = report.value
  return [
    { label: '实付金额', value: data ? `¥${data.paidAmount}` : '—', hint: '期间内已支付订单' },
    { label: '净支出', value: data ? `¥${data.netAmount}` : '—', hint: '实付减退款' },
    { label: '订单数', value: data ? `${data.orderCount} 笔` : '—', hint: '已支付订单' },
    { label: '优惠金额', value: data ? `¥${data.discountAmount}` : '—', hint: '累计省下' },
  ]
})

function amountNumber(value: string): number {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

function maxAmount(items: { amount: string }[]): number {
  return Math.max(1, ...items.map((item) => amountNumber(item.amount)))
}

/** 用 CSS 宽度表达占比；最小的条留 2%，避免金额为 0 时完全看不见 */
function barWidth(amount: string, max: number): string {
  return `${Math.max(2, (amountNumber(amount) / max) * 100)}%`
}

/** 年度趋势补满 12 个月，没有订单的月份显示为 0 */
const monthlyBars = computed(() => {
  if (period.value !== 'year' || !report.value) {
    return []
  }
  const year = label.value.replace('年', '')
  const amounts = new Map(report.value.monthly.map((item) => [item.month, item.amount]))
  const list = Array.from({ length: 12 }, (_, index) => {
    const month = `${year}-${String(index + 1).padStart(2, '0')}`
    return {
      month,
      label: `${index + 1}月`,
      amount: amounts.get(month) ?? '0.00',
    }
  })
  const max = Math.max(1, ...list.map((item) => amountNumber(item.amount)))
  return list.map((item) => ({
    ...item,
    height: `${Math.max(amountNumber(item.amount) > 0 ? 6 : 2, (amountNumber(item.amount) / max) * 100)}%`,
  }))
})

onMounted(load)
</script>

<template>
  <div class="block">
    <div class="head">
      <div>
        <h2 class="block-title">我的消费报告</h2>
        <p class="sub">{{ label || '统计期间内已支付订单，金额均来自订单数据' }}</p>
      </div>
      <div class="filters">
        <el-radio-group v-model="period" @change="load">
          <el-radio-button value="month">按月</el-radio-button>
          <el-radio-button value="year">按年</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-if="period === 'month'"
          v-model="monthValue"
          type="month"
          value-format="YYYY-MM"
          format="YYYY年MM月"
          :clearable="false"
          style="width: 150px"
          @change="load"
        />
        <el-date-picker
          v-else
          v-model="yearValue"
          type="year"
          value-format="YYYY"
          format="YYYY年"
          :clearable="false"
          style="width: 120px"
          @change="load"
        />
      </div>
    </div>

    <div v-loading="loading" class="body">
      <div class="stats">
        <div v-for="card in cards" :key="card.label" class="stat">
          <p class="stat-label">{{ card.label }}</p>
          <p class="stat-value">{{ card.value }}</p>
          <p class="stat-hint">{{ card.hint }}</p>
        </div>
      </div>

      <template v-if="hasOrders">
        <section v-if="period === 'year'" class="panel">
          <div class="panel-head">
            <h3 class="panel-title">每月消费</h3>
            <span class="panel-hint">最高月 ¥{{ report?.monthly.reduce(
              (peak, item) => (amountNumber(item.amount) > amountNumber(peak) ? item.amount : peak),
              '0.00',
            ) }}</span>
          </div>
          <div class="trend">
            <div
              v-for="bar in monthlyBars"
              :key="bar.month"
              class="trend-col"
              :title="`${bar.month} ¥${bar.amount}`"
            >
              <span class="trend-amount">{{ bar.amount === '0.00' ? '' : `¥${bar.amount}` }}</span>
              <div class="trend-track">
                <div class="trend-bar" :style="{ height: bar.height }" />
              </div>
              <span class="trend-label">{{ bar.label }}</span>
            </div>
          </div>
        </section>

        <div class="ranks">
          <section class="panel">
            <h3 class="panel-title">品类 Top3</h3>
            <ol v-if="categories.length" class="rank-list">
              <li v-for="(item, index) in categories" :key="item.name" class="rank-item">
                <span class="rank-no">{{ index + 1 }}</span>
                <div class="rank-main">
                  <div class="rank-line">
                    <span class="rank-name">{{ item.name }}</span>
                    <span class="rank-amount">¥{{ item.amount }}<em> · {{ item.count }} 件</em></span>
                  </div>
                  <div class="rank-track">
                    <div
                      class="rank-bar"
                      :style="{ width: barWidth(item.amount, maxAmount(categories)) }"
                    />
                  </div>
                </div>
              </li>
            </ol>
            <p v-else class="panel-empty">暂无品类数据</p>
          </section>

          <section class="panel">
            <h3 class="panel-title">店铺 Top3</h3>
            <ol v-if="merchants.length" class="rank-list">
              <li v-for="(item, index) in merchants" :key="item.name" class="rank-item">
                <span class="rank-no">{{ index + 1 }}</span>
                <div class="rank-main">
                  <div class="rank-line">
                    <span class="rank-name">{{ item.name }}</span>
                    <span class="rank-amount">¥{{ item.amount }}</span>
                  </div>
                  <div class="rank-track">
                    <div
                      class="rank-bar"
                      :style="{ width: barWidth(item.amount, maxAmount(merchants)) }"
                    />
                  </div>
                </div>
              </li>
            </ol>
            <p v-else class="panel-empty">暂无店铺数据</p>
          </section>

          <section class="panel">
            <h3 class="panel-title">商品 Top3</h3>
            <ol v-if="topProducts.length" class="rank-list">
              <li v-for="(item, index) in topProducts" :key="item.name" class="rank-item">
                <span class="rank-no">{{ index + 1 }}</span>
                <div class="rank-main">
                  <div class="rank-line">
                    <span class="rank-name">{{ item.name }}</span>
                    <span class="rank-amount">¥{{ item.amount }}<em> · {{ item.count }} 件</em></span>
                  </div>
                  <div class="rank-track">
                    <div
                      class="rank-bar"
                      :style="{ width: barWidth(item.amount, maxAmount(topProducts)) }"
                    />
                  </div>
                </div>
              </li>
            </ol>
            <p v-else class="panel-empty">暂无商品数据</p>
          </section>
        </div>
      </template>

      <el-empty
        v-else-if="!loading"
        description="本期还没有已支付的订单，暂无消费数据"
      >
        <el-button type="primary" @click="router.push('/')">去逛逛</el-button>
      </el-empty>
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
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.block-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--c-text);
}

.sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
}

.filters {
  display: flex;
  align-items: center;
  gap: 10px;
}

.body {
  min-height: 160px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.stat {
  padding: 14px 16px;
  background: var(--c-pink-tint);
  border-radius: var(--radius-card);
}

.stat-label {
  margin: 0;
  font-size: 12px;
  color: var(--c-text-3);
}

.stat-value {
  margin: 8px 0 4px;
  font-size: 22px;
  font-weight: 700;
  color: var(--c-pink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stat-hint {
  margin: 0;
  font-size: 11px;
  color: var(--c-text-3);
}

.panel {
  margin-top: 16px;
  padding: 14px 16px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-card);
}

.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.panel-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
}

.panel-hint {
  font-size: 12px;
  color: var(--c-text-3);
}

.panel-empty {
  margin: 16px 0;
  font-size: 13px;
  color: var(--c-text-3);
  text-align: center;
}

/* ── 年度月度趋势：纯 CSS 柱状图，不引图表库 ── */
.trend {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  margin-top: 14px;
  padding-top: 8px;
}

.trend-col {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  min-width: 0;
}

.trend-amount {
  height: 16px;
  font-size: 10px;
  color: var(--c-text-3);
  white-space: nowrap;
}

.trend-track {
  display: flex;
  align-items: flex-end;
  width: 100%;
  height: 110px;
}

.trend-bar {
  width: 100%;
  min-height: 2px;
  background: linear-gradient(180deg, var(--c-pink), var(--c-pink-soft));
  border-radius: 3px 3px 0 0;
  transition: height 0.2s;
}

.trend-label {
  margin-top: 6px;
  font-size: 11px;
  color: var(--c-text-3);
}

/* ── 三个排行并排 ── */
.ranks {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.rank-list {
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
}

.rank-item {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.rank-no {
  flex: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  font-size: 11px;
  line-height: 18px;
  color: #fff;
  text-align: center;
  background: var(--c-pink);
}

.rank-main {
  min-width: 0;
  flex: 1;
}

.rank-line {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 5px;
}

.rank-name {
  font-size: 13px;
  color: var(--c-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-amount {
  flex: none;
  font-size: 12px;
  color: var(--c-text-2);
}

.rank-amount em {
  font-style: normal;
  color: var(--c-text-3);
}

.rank-track {
  height: 5px;
  background: var(--c-pink-tint);
  border-radius: 3px;
  overflow: hidden;
}

.rank-bar {
  height: 100%;
  background: var(--c-pink);
  border-radius: 3px;
}

@media (max-width: 820px) {
  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ranks {
    grid-template-columns: 1fr;
  }

  .trend-amount {
    display: none;
  }
}
</style>
