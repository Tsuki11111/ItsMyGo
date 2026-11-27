<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Present, Ticket, Trophy } from '@element-plus/icons-vue'
import LotteryWheel from '@/components/lottery/LotteryWheel.vue'
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
import { computeSpinRotation, formatCountdown, resolveLotteryState } from '@/utils/lotteryState'

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

const rotation = ref(0)
const spinning = ref(false)
const pendingResult = ref<LotteryDrawResponse | null>(null)
let ticker: number | undefined
let spinTimer: number | undefined

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

const hubLabel = computed(() => {
  if (state.value === 'DRAWN') return '已抽奖'
  if (state.value === 'NOT_STARTED') return '未开始'
  if (state.value === 'ENDED') return '已结束'
  if (state.value === 'DRAWING') return '抽奖中'
  return userStore.isLoggedIn ? '抽奖' : '登录抽奖'
})

const hubDisabled = computed(() => state.value !== 'CAN_DRAW')

/** 奖盘最后固定有一个「谢谢参与」扇区；后端返回 THANKS 时转到这里 */
const wheelItems = computed(() => {
  const items = prizes.value.map((prize) => ({
    key: String(prize.id),
    name: prize.name,
    image: prize.image || '',
    soldOut: prize.remainingStock <= 0,
    thanks: false,
    remainingStock: prize.remainingStock,
  }))
  items.push({
    key: 'thanks',
    name: '谢谢参与',
    image: '',
    soldOut: false,
    thanks: true,
    remainingStock: 0,
  })
  return items
})

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

function spinTo(response: LotteryDrawResponse) {
  const items = wheelItems.value
  const index = response.prizeId === null
    ? items.length - 1
    : items.findIndex((item) => item.key === String(response.prizeId))
  const targetIndex = index >= 0 ? index : items.length - 1

  rotation.value = computeSpinRotation(rotation.value, targetIndex, items.length)
  spinning.value = true
  if (spinTimer !== undefined) {
    window.clearTimeout(spinTimer)
  }
  // transitionend 在正常情况下触发；这里兜底避免极少数环境不触发导致按钮永久禁用
  spinTimer = window.setTimeout(finishSpin, 5200)
}

function finishSpin() {
  if (!spinning.value) return
  spinning.value = false
  drawing.value = false
  if (spinTimer !== undefined) {
    window.clearTimeout(spinTimer)
    spinTimer = undefined
  }

  const response = pendingResult.value
  if (!response) return
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
  pendingResult.value = null
  void refreshPrizes()
}

function onSpinEnd(event: TransitionEvent) {
  if (event.propertyName !== 'transform') return
  finishSpin()
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
    pendingResult.value = response
    spinTo(response)
  } catch {
    // 响应可能在中途丢失：后端是幂等的，重新拉一次记录确认真实结果
    drawing.value = false
    await refreshRecord()
    await refreshPrizes()
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
  if (spinTimer !== undefined) {
    window.clearTimeout(spinTimer)
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
          <p class="rules">每人限抽 1 次 · 小额奖品权重更高 · 奖品抽完即止</p>
        </div>
        <el-icon class="hero-icon"><Present /></el-icon>
      </section>

      <section class="wheel-section">
        <LotteryWheel
          :items="wheelItems"
          :rotation="rotation"
          :spinning="spinning"
          :hub-label="hubLabel"
          :hub-disabled="hubDisabled"
          @draw="onDraw"
          @spin-end="onSpinEnd"
        />

        <div class="wheel-legend">
          <div
            v-for="item in wheelItems"
            :key="item.key"
            class="legend-item"
            :class="{ soldOut: item.soldOut, thanks: item.thanks }"
          >
            <span class="legend-thumb">
              <img v-if="item.image && !item.thanks" :src="item.image" :alt="item.name" />
              <el-icon v-else-if="item.thanks"><Present /></el-icon>
              <el-icon v-else><Ticket /></el-icon>
            </span>
            <span class="legend-name">{{ item.name }}</span>
            <span v-if="!item.thanks" class="legend-stock">
              {{ item.soldOut ? '已抽完' : `剩 ${item.remainingStock}` }}
            </span>
          </div>
        </div>
      </section>

      <section v-if="state === 'DRAWN'" class="record-section">
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

.wheel-section {
  margin-top: 20px;
  padding: 28px 20px 22px;
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.wheel-legend {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 14px;
  margin-top: 26px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-size: 12px;
  color: var(--c-text-2);
}

.legend-item.soldOut {
  opacity: 0.5;
}

.legend-thumb {
  width: 28px;
  height: 28px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  color: var(--c-pink);
  background: var(--c-pink-tint);
  border-radius: 50%;
}

.legend-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.legend-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--c-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.legend-item.thanks .legend-name {
  color: var(--c-text-3);
}

.legend-stock {
  flex: none;
  color: var(--c-text-3);
}

.record-section {
  margin-top: 20px;
  text-align: center;
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

  .wheel-legend {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 420px) {
  .wheel-legend {
    grid-template-columns: 1fr;
  }
}
</style>
