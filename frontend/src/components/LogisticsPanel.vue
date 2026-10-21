<script setup lang="ts">
import { computed } from 'vue'
import LogisticsMap from '@/components/LogisticsMap.vue'
import type { LogisticsView } from '@/api/types'

const props = defineProps<{ logistics: LogisticsView }>()

const delivered = computed(() => props.logistics.status === 'DELIVERED')
const cancelled = computed(() => props.logistics.status === 'CANCELLED')
</script>

<template>
  <div class="logistics-panel">
    <header class="logistics-head">
      <div class="head-main">
        <p class="logistics-status">{{ logistics.statusLabel }}</p>
        <p class="logistics-eta">
          <template v-if="delivered">
            已于 {{ logistics.deliveredAt || logistics.eta }} 送达（与预计送达时间一致）
          </template>
          <template v-else-if="cancelled">
            物流已停止（退款通过）
          </template>
          <template v-else>
            预计送达 {{ logistics.eta }}
          </template>
        </p>
      </div>
      <span class="carrier-tag">{{ logistics.carrier }}</span>
    </header>

    <div class="courier-row">
      <span><em>快递员</em>{{ logistics.courierName }}</span>
      <span><em>电话</em>{{ logistics.courierPhone }}</span>
      <span><em>运单号</em>{{ logistics.trackingNo }}</span>
    </div>

    <div class="timeline">
      <div
        v-for="(item, index) in logistics.timeline"
        :key="item.status"
        class="timeline-item"
        :class="{ done: item.done, current: item.status === logistics.status }"
      >
        <span class="timeline-dot" />
        <div class="timeline-body">
          <span class="timeline-label">{{ item.label }}</span>
          <span class="timeline-time">{{ item.time }}</span>
        </div>
        <span v-if="index < logistics.timeline.length - 1" class="timeline-line" />
      </div>
    </div>

    <LogisticsMap :logistics="logistics" />
  </div>
</template>

<style scoped>
.logistics-panel {
  min-width: 0;
}

.logistics-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.head-main {
  min-width: 0;
}

.logistics-status {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--c-pink);
}

.logistics-eta {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--c-text-2);
}

.carrier-tag {
  flex: none;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--c-pink);
  background: var(--c-pink-tint);
  border-radius: 999px;
}

.courier-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin-top: 14px;
  padding: 10px 12px;
  font-size: 13px;
  color: var(--c-text);
  background: #fafafb;
  border-radius: 8px;
}

.courier-row em {
  margin-right: 6px;
  font-style: normal;
  color: var(--c-text-3);
}

.timeline {
  position: relative;
  margin-top: 16px;
  padding-left: 4px;
}

.timeline-item {
  position: relative;
  display: flex;
  gap: 10px;
  min-height: 38px;
  padding-bottom: 10px;
  color: var(--c-text-3);
}

.timeline-dot {
  position: relative;
  z-index: 1;
  flex: none;
  width: 10px;
  height: 10px;
  margin-top: 4px;
  background: #d9dce3;
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 1px #d9dce3;
}

.timeline-item.done .timeline-dot {
  background: var(--c-pink);
  box-shadow: 0 0 0 1px var(--c-pink);
}

.timeline-item.current .timeline-dot {
  box-shadow: 0 0 0 3px var(--c-pink-tint), 0 0 0 1px var(--c-pink);
}

.timeline-line {
  position: absolute;
  top: 14px;
  left: 4px;
  width: 1px;
  height: calc(100% - 10px);
  background: #e6e8ee;
}

.timeline-item.done .timeline-line {
  background: var(--c-pink-soft);
}

.timeline-body {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
}

.timeline-label {
  font-size: 13px;
  color: var(--c-text-2);
}

.timeline-item.done .timeline-label {
  color: var(--c-text);
}

.timeline-item.current .timeline-label {
  font-weight: 600;
  color: var(--c-pink);
}

.timeline-time {
  font-size: 11px;
  color: var(--c-text-3);
}
</style>
