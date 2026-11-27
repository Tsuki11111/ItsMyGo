<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Present, Ticket } from '@element-plus/icons-vue'
import { fetchActivities } from '@/api/activity'
import type { ActivityView } from '@/api/types'
import { activityPhase, activityPhaseLabel, formatActivityDate } from '@/utils/activity'

const router = useRouter()
const activities = ref<ActivityView[]>([])
const loading = ref(true)
const now = ref(Date.now())
let ticker: number | undefined

const cards = computed(() => activities.value.map((activity) => {
  const phase = activityPhase(activity, now.value)
  return {
    ...activity,
    phase,
    phaseLabel: activityPhaseLabel(phase),
    phaseClass: phase === 'ONGOING' ? 'ongoing' : phase === 'NOT_STARTED' ? 'upcoming' : 'ended',
  }
}))

async function load() {
  loading.value = true
  try {
    activities.value = await fetchActivities()
  } catch {
    // http 拦截器已提示
  } finally {
    loading.value = false
  }
}

function open(activity: ActivityView) {
  if (activity.type !== 'LOTTERY') {
    ElMessage.info('该活动即将开放')
    return
  }
  router.push(`/lottery/${activity.id}`)
}

function actionText(activity: { type: string; phase: string }): string {
  if (activity.type !== 'LOTTERY') return '敬请期待'
  if (activity.phase === 'ONGOING') return '去抽奖'
  if (activity.phase === 'NOT_STARTED') return '查看活动'
  return '回看活动'
}

onMounted(async () => {
  await load()
  ticker = window.setInterval(() => {
    now.value = Date.now()
  }, 30_000)
})

onBeforeUnmount(() => {
  if (ticker !== undefined) {
    window.clearInterval(ticker)
  }
})
</script>

<template>
  <div class="activity-page">
    <header class="page-head">
      <div>
        <h1>活动中心</h1>
        <p>抽奖、福利和更多玩法都会在这里出现</p>
      </div>
    </header>

    <div v-loading="loading" class="activity-grid">
      <el-empty v-if="!loading && activities.length === 0" description="暂无活动" />

      <article
        v-for="item in cards"
        :key="item.id"
        class="activity-card"
        :style="{ '--activity-color': item.primaryColor }"
        @click="open(item)"
      >
        <div class="cover">
          <img v-if="item.coverImage" :src="item.coverImage" :alt="item.name" />
          <div v-else class="cover-fallback">
            <el-icon>
              <Present v-if="item.type === 'LOTTERY'" />
              <Ticket v-else />
            </el-icon>
          </div>
          <span class="type-badge">{{ item.typeLabel }}</span>
          <span class="phase-badge" :class="item.phaseClass">{{ item.phaseLabel }}</span>
        </div>

        <div class="body">
          <h2 class="name">{{ item.name }}</h2>
          <p class="desc">{{ item.description || '更多惊喜，点进来看看' }}</p>
          <p class="period">
            {{ formatActivityDate(item.startAt) }} ~ {{ formatActivityDate(item.endAt) }}
          </p>
          <div class="footer">
            <span class="action">{{ actionText(item) }}</span>
            <span class="arrow">→</span>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.activity-page {
  max-width: 1000px;
  margin: 0 auto;
  padding-bottom: 24px;
}

.page-head {
  margin-bottom: 18px;
}

.page-head h1 {
  margin: 0;
  font-size: 24px;
  color: var(--c-text);
}

.page-head p {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--c-text-3);
}

.activity-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  min-height: 180px;
}

.activity-grid :deep(.el-empty) {
  grid-column: 1 / -1;
}

.activity-card {
  overflow: hidden;
  background: var(--c-card);
  border-top: 4px solid var(--activity-color);
  border-radius: 0;
  box-shadow: var(--shadow-card);
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.activity-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-card-hover);
}

.cover {
  position: relative;
  height: 142px;
  overflow: hidden;
  background: var(--c-pink-tint);
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 52px;
  color: var(--activity-color);
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--activity-color) 30%, #fff),
    #fff
  );
}

.type-badge,
.phase-badge {
  position: absolute;
  top: 10px;
  padding: 2px 9px;
  font-size: 11px;
  border-radius: 999px;
}

.type-badge {
  left: 10px;
  color: var(--activity-color);
  background: color-mix(in srgb, var(--activity-color) 16%, #fff);
}

.phase-badge {
  right: 10px;
  color: #fff;
}

.phase-badge.ongoing {
  background: #52c41a;
}

.phase-badge.upcoming {
  background: #faad14;
}

.phase-badge.ended {
  background: rgba(0, 0, 0, 0.42);
}

.body {
  padding: 14px 16px 16px;
}

.name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--c-text);
}

.desc {
  min-height: 34px;
  margin: 6px 0 0;
  display: -webkit-box;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.45;
  color: var(--c-text-2);
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
}

.period {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
}

.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

.action {
  font-size: 13px;
  font-weight: 600;
  color: var(--activity-color);
}

.arrow {
  font-size: 15px;
  color: var(--activity-color);
}

@media (max-width: 900px) {
  .activity-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 600px) {
  .activity-grid {
    grid-template-columns: 1fr;
  }
}
</style>
