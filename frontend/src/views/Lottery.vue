<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Present, Trophy } from '@element-plus/icons-vue'
import {
  drawLottery,
  fetchActiveLottery,
  fetchLotteryPrizes,
  fetchMyLotteryRecord,
} from '@/api/lottery'
import { useUserStore } from '@/stores/user'
import type {
  LotteryActivityPublicView,
  LotteryDrawResponse,
  LotteryMyRecordView,
  LotteryPrizePublicView,
} from '@/api/types'
import { formatCountdown, resolveLotteryState } from '@/utils/lotteryState'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activity = ref<LotteryActivityPublicView | null>(null)
const prizes = ref<LotteryPrizePublicView[]>([])
const record = ref<LotteryMyRecordView | null>(null)
const loading = ref(true)
const drawing = ref(false)
const now = ref(Date.now())
const result = ref<LotteryDrawResponse | null>(null)
const resultVisible = ref(false)
let ticker: number | undefined

const state = computed(() => resolveLotteryState(
  activity.value,
  record.value,
  now.value,
  drawing.value,
))

const statusText = computed(() => {
  if (!activity.value) return ''
  if (state.value === 'NOT_STARTED') return '未开始'
  if (state.value === 'ENDED') return '已结束'
  return activity.value.statusLabel
})

const countdownLabel = computed(() => state.value === 'NOT_STARTED' ? '距离开始' : '距离结束')

const countdownText = computed(() => {
  if (!activity.value) return ''
  const startAt = new Date(activity.value.startAt).getTime()
  const endAt = new Date(activity.value.endAt).getTime()
  if (state.value === 'NOT_STARTED') {
    return formatCountdown((startAt - now.value) / 1000)
  }
  if (state.value === 'CAN_DRAW' || state.value === 'DRAWING') {
    return formatCountdown((endAt - now.value) / 1000)
  }
  return ''
})

const drawDisabled = computed(() =>
  state.value === 'NOT_STARTED'
  || state.value === 'ENDED'
  || state.value === 'DRAWING',
)

const buttonText = computed(() => {
  if (state.value === 'NOT_STARTED') return '活动未开始'
  if (state.value === 'ENDED') return '活动已结束'
  if (state.value === 'DRAWING') return '抽奖中...'
  if (state.value === 'CAN_DRAW' && !userStore.isLoggedIn) return '登录后抽奖'
  return '立即抽奖'
})

function prizeLabel(level: number): string {
  if (level === 1) return '一等奖'
  if (level === 2) return '二等奖'
  if (level === 3) return '三等奖'
  return `${level} 等奖`
}

function isSoldOut(prize: LotteryPrizePublicView): boolean {
  return prize.remainingStock <= 0
}

async function load() {
  loading.value = true
  try {
    activity.value = await fetchActiveLottery()
    if (!activity.value) {
      prizes.value = []
      record.value = null
      return
    }
    prizes.value = await fetchLotteryPrizes(activity.value.id)
    if (userStore.isLoggedIn) {
      try {
        record.value = await fetchMyLotteryRecord(activity.value.id)
      } catch {
        // 记录拉取失败不阻断奖池展示；抽奖时会再次校验
        record.value = null
      }
    } else {
      record.value = null
    }
  } catch {
    // http 拦截器已提示；保持空状态
  } finally {
    loading.value = false
  }
}

async function refreshPrizes() {
  if (!activity.value) return
  try {
    prizes.value = await fetchLotteryPrizes(activity.value.id)
  } catch {
    // 库存刷新失败不影响已出的抽奖结果
  }
}

async function refreshRecord() {
  if (!activity.value || !userStore.isLoggedIn) return
  try {
    record.value = await fetchMyLotteryRecord(activity.value.id)
  } catch {
    // 网络异常时保留原状态
  }
}

async function onDraw() {
  if (!activity.value) return
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (state.value !== 'CAN_DRAW') return

  drawing.value = true
  try {
    const response = await drawLottery(activity.value.id)
    record.value = {
      drawn: true,
      result: response.result,
      prizeId: response.prizeId,
      prizeName: response.prizeName,
      prizeCode: response.prizeCode,
      drawnAt: new Date().toISOString(),
    }
    result.value = response
    resultVisible.value = true
    await refreshPrizes()
  } catch {
    // 响应可能在中途丢失：后端是幂等的，重新拉一次记录确认真实结果
    await refreshRecord()
    await refreshPrizes()
  } finally {
    drawing.value = false
  }
}

onMounted(async () => {
  await load()
  ticker = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onBeforeUnmount(() => {
  if (ticker !== undefined) {
    window.clearInterval(ticker)
  }
})
</script>

<template>
  <div v-loading="loading" class="lottery-page">
    <template v-if="activity">
      <section class="hero">
        <div class="hero-main">
          <p class="eyebrow">LUCKY DRAW</p>
          <h1 class="hero-title">{{ activity.name }}</h1>
          <div class="hero-meta">
            <span class="status-tag">{{ statusText }}</span>
            <span v-if="countdownText" class="countdown">
              {{ countdownLabel }} {{ countdownText }}
            </span>
          </div>
          <p class="rules">每人限抽 1 次 · 多奖项等概率 · 奖品抽完即止</p>
        </div>
        <el-icon class="hero-icon"><Present /></el-icon>
      </section>

      <section class="prize-section">
        <h2 class="section-title">奖池</h2>
        <div class="prize-grid">
          <el-empty v-if="!loading && prizes.length === 0" description="奖池准备中" />
          <article
            v-for="prize in prizes"
            :key="prize.id"
            class="prize-card"
            :class="{ soldOut: isSoldOut(prize) }"
          >
            <span class="prize-level">{{ prizeLabel(prize.level) }}</span>
            <el-icon class="prize-icon"><Trophy /></el-icon>
            <p class="prize-name">{{ prize.name }}</p>
            <p class="prize-stock">
              {{ isSoldOut(prize) ? '已抽完' : `剩余 ${prize.remainingStock} 份` }}
            </p>
          </article>
        </div>
      </section>

      <section class="draw-section">
        <template v-if="state === 'DRAWN'">
          <div class="record-card" :class="{ win: record?.result === 'WIN' }">
            <el-icon class="record-icon">
              <Trophy v-if="record?.result === 'WIN'" />
              <Present v-else />
            </el-icon>
            <div class="record-main">
              <p class="record-title">
                {{ record?.result === 'WIN' ? '恭喜中奖' : '谢谢参与' }}
              </p>
              <p v-if="record?.result === 'WIN'" class="record-detail">
                {{ record?.prizeName }} · <code>{{ record?.prizeCode }}</code>
              </p>
              <p v-else class="record-detail">这次没有抽中，机会已使用</p>
            </div>
          </div>
        </template>
        <template v-else>
          <button
            type="button"
            class="draw-btn"
            :disabled="drawDisabled"
            @click="onDraw"
          >
            {{ buttonText }}
          </button>
          <p class="draw-hint">抽奖结果会写入记录，同一活动每人只可抽一次</p>
        </template>
      </section>
    </template>

    <el-empty v-if="!loading && !activity" description="暂无进行中的抽奖活动">
      <el-button type="primary" @click="router.push('/')">去逛逛</el-button>
    </el-empty>

    <el-dialog
      v-model="resultVisible"
      width="380px"
      align-center
      :show-close="false"
      :close-on-click-modal="false"
    >
      <div class="result">
        <el-icon class="result-icon">
          <Trophy v-if="result?.result === 'WIN'" />
          <Present v-else />
        </el-icon>
        <h2 class="result-title">
          {{ result?.result === 'WIN' ? '恭喜中奖' : '谢谢参与' }}
        </h2>
        <template v-if="result?.result === 'WIN'">
          <p class="result-prize">{{ result.prizeName }}</p>
          <p class="result-code">{{ result.prizeCode }}</p>
        </template>
        <p class="result-message">{{ result?.message }}</p>
        <button type="button" class="result-btn" @click="resultVisible = false">知道了</button>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.lottery-page {
  max-width: 960px;
  margin: 0 auto;
  padding-bottom: 20px;
  min-height: 240px;
}

.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 28px 30px;
  background: linear-gradient(135deg, var(--c-pink-tint), #fff);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.eyebrow {
  margin: 0;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: var(--c-pink);
}

.hero-title {
  margin: 8px 0 12px;
  font-size: 26px;
  font-weight: 700;
  color: var(--c-text);
}

.hero-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.status-tag {
  padding: 3px 10px;
  font-size: 12px;
  color: var(--c-pink);
  background: var(--c-pink-soft);
  border-radius: 999px;
}

.countdown {
  font-size: 12px;
  color: var(--c-text-2);
  font-variant-numeric: tabular-nums;
}

.rules {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
}

.hero-icon {
  flex: none;
  font-size: 64px;
  color: var(--c-pink);
  opacity: 0.8;
}

.prize-section {
  margin-top: 20px;
}

.section-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text);
}

.prize-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  min-height: 100px;
}

.prize-grid :deep(.el-empty) {
  grid-column: 1 / -1;
}

.prize-card {
  position: relative;
  padding: 22px 16px 18px;
  text-align: center;
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.prize-card.soldOut {
  opacity: 0.55;
}

.prize-level {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 2px 8px;
  font-size: 11px;
  color: var(--c-pink);
  background: var(--c-pink-tint);
  border-radius: 999px;
}

.prize-icon {
  margin-bottom: 6px;
  font-size: 28px;
  color: var(--c-pink);
}

.prize-name {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
}

.prize-stock {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
}

.draw-section {
  margin-top: 22px;
  text-align: center;
}

.draw-btn {
  width: 260px;
  padding: 14px 0;
  font-family: inherit;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  background: var(--c-pink);
  border: none;
  border-radius: var(--radius-btn);
  cursor: pointer;
  transition: background 0.15s;
}

.draw-btn:hover:not(:disabled) {
  background: var(--c-pink-hover);
}

.draw-btn:disabled {
  background: var(--c-text-3);
  cursor: not-allowed;
}

.draw-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
}

.record-card {
  display: flex;
  align-items: center;
  gap: 14px;
  max-width: 520px;
  margin: 0 auto;
  padding: 18px 22px;
  text-align: left;
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.record-card.win {
  background: var(--c-pink-tint);
}

.record-icon {
  flex: none;
  font-size: 32px;
  color: var(--c-pink);
}

.record-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text);
}

.record-detail {
  margin: 5px 0 0;
  font-size: 13px;
  color: var(--c-text-2);
}

.record-detail code {
  font-family: ui-monospace, 'SF Mono', Consolas, monospace;
  color: var(--c-pink);
}

.result {
  padding: 4px 6px 2px;
  text-align: center;
}

.result-icon {
  font-size: 56px;
  color: var(--c-pink);
}

.result-title {
  margin: 8px 0;
  font-size: 20px;
  color: var(--c-text);
}

.result-prize {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text);
}

.result-code {
  margin: 8px 0 0;
  font-family: ui-monospace, 'SF Mono', Consolas, monospace;
  font-size: 13px;
  color: var(--c-pink);
}

.result-message {
  margin: 12px 0 18px;
  font-size: 13px;
  color: var(--c-text-2);
}

.result-btn {
  width: 100%;
  padding: 11px 0;
  font-family: inherit;
  font-size: 14px;
  color: #fff;
  background: var(--c-pink);
  border: none;
  border-radius: var(--radius-btn);
  cursor: pointer;
}

@media (max-width: 720px) {
  .hero {
    padding: 22px 20px;
  }

  .hero-title {
    font-size: 22px;
  }

  .hero-icon {
    display: none;
  }

  .prize-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .draw-btn {
    width: 100%;
  }
}

@media (max-width: 420px) {
  .prize-grid {
    grid-template-columns: 1fr;
  }
}
</style>
