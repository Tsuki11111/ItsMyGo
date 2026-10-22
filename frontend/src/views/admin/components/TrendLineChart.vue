<script setup lang="ts">
import { computed, ref } from 'vue'
import type { AdminDashboardTrendPoint } from '@/api/types'

const props = defineProps<{ points: AdminDashboardTrendPoint[] }>()

const W = 720
const H = 240
const PAD = { top: 20, right: 20, bottom: 32, left: 58 }
const PLOT_W = W - PAD.left - PAD.right
const PLOT_H = H - PAD.top - PAD.bottom

const hover = ref<number | null>(null)

const amounts = computed(() => props.points.map((point) => Number(point.amount) || 0))

const maxValue = computed(() => {
  const max = Math.max(...amounts.value, 0)
  return max <= 0 ? 1 : niceCeil(max)
})

interface Coord {
  x: number
  y: number
  amount: number
  point: AdminDashboardTrendPoint
}

const coords = computed<Coord[]>(() => props.points.map((point, index) => {
  const ratio = props.points.length <= 1 ? 0.5 : index / (props.points.length - 1)
  return {
    x: PAD.left + PLOT_W * ratio,
    y: PAD.top + PLOT_H - (amounts.value[index] / maxValue.value) * PLOT_H,
    amount: amounts.value[index],
    point,
  }
}))

const linePath = computed(() => smoothPath(coords.value))
const areaPath = computed(() => {
  const points = coords.value
  if (!points.length) return ''
  const bottom = PAD.top + PLOT_H
  return `${linePath.value} L ${points[points.length - 1].x} ${bottom} L ${points[0].x} ${bottom} Z`
})

const yTicks = computed(() => [0, 0.25, 0.5, 0.75, 1].map((ratio) => ({
  y: PAD.top + PLOT_H - ratio * PLOT_H,
  label: compactMoney(maxValue.value * ratio),
})))

const hoverPoint = computed(() => hover.value == null ? null : coords.value[hover.value])

function smoothPath(points: Coord[]): string {
  if (!points.length) return ''
  if (points.length === 1) return `M ${points[0].x} ${points[0].y}`
  let path = `M ${points[0].x} ${points[0].y}`
  for (let i = 1; i < points.length; i += 1) {
    const prev = points[i - 1]
    const cur = points[i]
    const midX = (prev.x + cur.x) / 2
    path += ` C ${midX} ${prev.y}, ${midX} ${cur.y}, ${cur.x} ${cur.y}`
  }
  return path
}

function niceCeil(value: number): number {
  const magnitude = 10 ** Math.floor(Math.log10(value))
  const normalized = value / magnitude
  const step = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10
  return step * magnitude
}

function compactMoney(value: number): string {
  if (value >= 10000) return `¥${(value / 10000).toFixed(1)}万`
  if (value >= 1000) return `¥${(value / 1000).toFixed(1)}k`
  return `¥${Math.round(value)}`
}

function exactMoney(value: number): string {
  return value.toFixed(2)
}
</script>

<template>
  <div class="trend-chart">
    <svg :viewBox="`0 0 ${W} ${H}`" role="img" aria-label="近 7 天成交折线图">
      <defs>
        <linearGradient id="trend-area" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#d6336c" stop-opacity="0.22" />
          <stop offset="100%" stop-color="#d6336c" stop-opacity="0" />
        </linearGradient>
      </defs>

      <g class="grid">
        <line
          v-for="tick in yTicks"
          :key="tick.y"
          :x1="PAD.left"
          :x2="W - PAD.right"
          :y1="tick.y"
          :y2="tick.y"
        />
        <text
          v-for="tick in yTicks"
          :key="`label-${tick.y}`"
          :x="PAD.left - 10"
          :y="tick.y + 4"
          text-anchor="end"
        >
          {{ tick.label }}
        </text>
      </g>

      <path v-if="areaPath" :d="areaPath" fill="url(#trend-area)" />
      <path v-if="linePath" :d="linePath" class="line" vector-effect="non-scaling-stroke" />

      <g v-for="(coord, index) in coords" :key="coord.point.date">
        <circle
          :cx="coord.x"
          :cy="coord.y"
          r="12"
          class="hit"
          @mouseenter="hover = index"
          @mouseleave="hover = null"
        >
          <title>{{ coord.point.date }}：¥{{ exactMoney(coord.amount) }}，{{ coord.point.orders }} 单</title>
        </circle>
        <circle
          :cx="coord.x"
          :cy="coord.y"
          :r="hover === index ? 6 : 4"
          class="dot"
          vector-effect="non-scaling-stroke"
        />
        <text :x="coord.x" :y="H - 10" text-anchor="middle" class="x-label">
          {{ coord.point.date.slice(5) }}
        </text>
      </g>
    </svg>

    <div
      v-if="hoverPoint"
      class="chart-tooltip"
      :style="{
        left: `${(hoverPoint.x / W) * 100}%`,
        top: `${(hoverPoint.y / H) * 100}%`,
      }"
    >
      <strong>{{ hoverPoint.point.date }}</strong>
      <span>成交 ¥{{ exactMoney(hoverPoint.amount) }}</span>
      <span>{{ hoverPoint.point.orders }} 单</span>
    </div>
  </div>
</template>

<style scoped>
.trend-chart {
  position: relative;
  width: 100%;
}

svg {
  display: block;
  width: 100%;
  height: auto;
  overflow: visible;
}

.grid line {
  stroke: var(--el-border-color-lighter);
  stroke-dasharray: 3 4;
  stroke-width: 1;
}

.grid text,
.x-label {
  font-size: 10px;
  fill: var(--el-text-color-secondary);
}

.line {
  fill: none;
  stroke: var(--c-pink);
  stroke-width: 2.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.dot {
  fill: #fff;
  stroke: var(--c-pink);
  stroke-width: 2;
  transition: r 0.15s;
}

.hit {
  fill: transparent;
  cursor: pointer;
}

.chart-tooltip {
  position: absolute;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 9px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  pointer-events: none;
  background: #fff;
  border: 1px solid #f2cede;
  border-radius: 8px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.12);
  transform: translate(-50%, -120%);
}

.chart-tooltip strong {
  font-size: 12px;
  color: var(--el-text-color-primary);
}
</style>
