<script setup lang="ts">
import { computed } from 'vue'
import { Present, Ticket } from '@element-plus/icons-vue'

interface LotteryWheelItem {
  key: string
  name: string
  image: string
  soldOut: boolean
  thanks: boolean
}

const props = defineProps<{
  items: LotteryWheelItem[]
  rotation: number
  spinning: boolean
  hubLabel: string
  hubDisabled: boolean
}>()

const emit = defineEmits<{
  (e: 'draw'): void
  (e: 'spin-end', event: TransitionEvent): void
}>()

/** 扇区底色：浅粉和白色交替，避免整圈一个颜色 */
const PALETTE = ['#fff5f9', '#ffffff', '#ffe9f2', '#fff0f6']

const step = computed(() => 360 / Math.max(props.items.length, 1))

const wheelStyle = computed(() => {
  const stops: string[] = []
  for (let i = 0; i < props.items.length; i++) {
    const color = PALETTE[i % PALETTE.length]
    const start = i * step.value
    const end = (i + 1) * step.value
    stops.push(`${color} ${start}deg ${end}deg`)
  }
  return {
    // from -step/2：让第 0 个扇区的中心正对顶部指针
    background: `conic-gradient(from ${-step.value / 2}deg, ${stops.join(', ')})`,
    transform: `rotate(${props.rotation}deg)`,
  }
})

function itemStyle(index: number) {
  return {
    transform: `rotate(${index * step.value}deg) translateY(calc(var(--wheel-size) * -0.34))`,
  }
}

function onTransitionEnd(event: Event) {
  emit('spin-end', event as TransitionEvent)
}
</script>

<template>
  <div class="wheel-wrap">
    <div
      class="wheel"
      :class="{ spinning }"
      :style="wheelStyle"
      @transitionend="onTransitionEnd"
    >
      <div
        v-for="(item, index) in items"
        :key="item.key"
        class="wheel-item"
        :style="itemStyle(index)"
      >
        <div class="wheel-thumb" :class="{ soldOut: item.soldOut }">
          <img v-if="item.image && !item.thanks" :src="item.image" :alt="item.name" />
          <el-icon v-else-if="item.thanks" class="thumb-icon"><Present /></el-icon>
          <el-icon v-else class="thumb-icon"><Ticket /></el-icon>
        </div>
      </div>
    </div>

    <div class="pointer" />

    <button
      type="button"
      class="hub"
      :disabled="hubDisabled"
      @click="emit('draw')"
    >
      {{ hubLabel }}
    </button>
  </div>
</template>

<style scoped>
.wheel-wrap {
  --wheel-size: min(78vw, 420px);
  position: relative;
  width: var(--wheel-size);
  height: var(--wheel-size);
  margin: 0 auto;
}

.wheel {
  width: 100%;
  height: 100%;
  border: 8px solid #fff;
  border-radius: 50%;
  box-shadow: 0 12px 32px rgba(232, 99, 143, 0.2);
  /* 转盘本体旋转；中心按钮和指针在 wrap 里，不跟着转 */
  transition: transform 4.8s cubic-bezier(0.15, 0.85, 0.15, 1);
  will-change: transform;
}

.wheel-item {
  position: absolute;
  top: 50%;
  left: 50%;
  width: calc(var(--wheel-size) * 0.15);
  height: calc(var(--wheel-size) * 0.15);
  margin: calc(var(--wheel-size) * -0.075);
  transform-origin: center;
  pointer-events: none;
}

.wheel-thumb {
  width: 100%;
  height: 100%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 3px solid #fff;
  border-radius: 50%;
  box-shadow: 0 4px 12px rgba(232, 99, 143, 0.18);
}

.wheel-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.wheel-thumb.soldOut {
  filter: grayscale(1);
  opacity: 0.45;
}

.thumb-icon {
  font-size: calc(var(--wheel-size) * 0.07);
  color: var(--c-pink);
}

.pointer {
  position: absolute;
  top: -10px;
  left: 50%;
  z-index: 3;
  width: 0;
  height: 0;
  transform: translateX(-50%);
  border-left: 13px solid transparent;
  border-right: 13px solid transparent;
  border-top: 26px solid var(--c-pink);
  filter: drop-shadow(0 3px 4px rgba(232, 99, 143, 0.35));
}

.hub {
  position: absolute;
  top: 50%;
  left: 50%;
  z-index: 2;
  width: 25%;
  height: 25%;
  transform: translate(-50%, -50%);
  padding: 0;
  font-family: inherit;
  font-size: clamp(12px, 3.4vw, 16px);
  font-weight: 700;
  color: #fff;
  background: var(--c-pink);
  border: 6px solid #fff;
  border-radius: 50%;
  box-shadow: 0 6px 16px rgba(232, 99, 143, 0.35);
  cursor: pointer;
  transition: background 0.15s;
}

.hub:hover:not(:disabled) {
  background: var(--c-pink-hover);
}

.hub:disabled {
  background: var(--c-text-3);
  cursor: not-allowed;
}

@media (max-width: 720px) {
  .wheel-wrap {
    --wheel-size: min(86vw, 380px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .wheel {
    transition-duration: 0.4s;
  }
}
</style>
