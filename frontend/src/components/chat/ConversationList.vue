<script setup lang="ts">
import { Service } from '@element-plus/icons-vue'
import ProductImage from '@/components/ProductImage.vue'
import type { ChatScope, ConversationView } from '@/api/types'

const props = defineProps<{
  items: ConversationView[]
  activeId: number | null
  mode: ChatScope
  loading: boolean
  total: number
  agentActive?: boolean
}>()

const emit = defineEmits<{
  select: [id: number]
  loadMore: []
  selectAgent: []
}>()

function peerName(item: ConversationView): string {
  return props.mode === 'CUSTOMER' ? item.merchantName : item.customerName
}

function peerAvatar(item: ConversationView): string {
  return props.mode === 'CUSTOMER' ? item.merchantLogo : item.customerAvatar
}

function peerId(item: ConversationView): number {
  return props.mode === 'CUSTOMER' ? item.merchantId : item.customerId
}

function formatTime(value: string | null): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  if (date.toDateString() === now.toDateString()) {
    return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  }
  const md = `${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  return date.getFullYear() === now.getFullYear() ? md : `${date.getFullYear()}-${md}`
}
</script>

<template>
  <aside class="list-pane">
    <div class="list-head">
      <h2>消息</h2>
      <span v-if="total > 0" class="count">{{ total }} 个会话</span>
    </div>

    <div v-loading="loading" class="list-body">
      <!-- 永久置顶：客服智能体占位入口，所有用户都能看到 -->
      <button
        type="button"
        class="conversation agent-card"
        :class="{ active: agentActive }"
        @click="emit('selectAgent')"
      >
        <span class="avatar agent-avatar">
          <el-icon><Service /></el-icon>
        </span>
        <span class="main">
          <span class="row">
            <span class="name">买小购</span>
            <span class="agent-tag">AI</span>
          </span>
          <span class="row">
            <span class="preview">智能客服 · 即将接入</span>
          </span>
        </span>
      </button>

      <el-empty
        v-if="!loading && items.length === 0"
        description="还没有与商家的会话"
        :image-size="70"
      />

      <button
        v-for="item in items"
        :key="item.id"
        type="button"
        class="conversation"
        :class="{ active: item.id === activeId }"
        @click="emit('select', item.id)"
      >
        <span class="avatar">
          <ProductImage :src="peerAvatar(item)" :name="peerName(item)" :id="peerId(item)" />
          <span
            v-if="mode === 'CUSTOMER' && item.peerOnline"
            class="online-dot"
            title="在线"
          />
        </span>

        <span class="main">
          <span class="row">
            <span class="name">{{ peerName(item) }}</span>
            <span class="time">{{ formatTime(item.lastMessageAt) }}</span>
          </span>
          <span class="row">
            <span class="preview">{{ item.lastMessagePreview || '暂无消息' }}</span>
            <span v-if="item.unread > 0" class="unread">{{ item.unread > 99 ? '99+' : item.unread }}</span>
          </span>
        </span>
      </button>

      <div v-if="items.length < total" class="load-more">
        <el-button link type="primary" @click="emit('loadMore')">加载更多</el-button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.list-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #fff;
  border-right: 1px solid var(--c-border);
}

.list-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 14px 16px 10px;
  border-bottom: 1px solid var(--c-border);
}

.list-head h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--c-text);
}

.count {
  font-size: 11px;
  color: var(--c-text-3);
}

.list-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 6px;
}

.conversation {
  display: flex;
  gap: 10px;
  width: 100%;
  padding: 10px;
  font-family: inherit;
  text-align: left;
  background: none;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.14s;
}

.conversation:hover {
  background: var(--c-pink-tint);
}

.conversation.active {
  background: var(--c-pink-soft);
}

.avatar {
  position: relative;
  width: 42px;
  height: 42px;
  flex: none;
  border-radius: 10px;
  overflow: hidden;
  background: var(--c-pink-tint);
}

.agent-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: #fff;
  background: linear-gradient(135deg, var(--c-pink), #f39ab9);
}

.agent-tag {
  flex: none;
  padding: 1px 6px;
  font-size: 9px;
  font-weight: 600;
  color: var(--c-pink);
  background: var(--c-pink-tint);
  border-radius: 8px;
}

.online-dot {
  position: absolute;
  right: 1px;
  bottom: 1px;
  width: 9px;
  height: 9px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: #3fbf6f;
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
}

.row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time {
  flex: none;
  font-size: 10px;
  color: var(--c-text-3);
}

.preview {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: var(--c-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unread {
  flex: none;
  min-width: 17px;
  height: 17px;
  padding: 0 5px;
  font-size: 10px;
  line-height: 17px;
  text-align: center;
  color: #fff;
  background: var(--c-pink);
  border-radius: 9px;
}

.load-more {
  padding: 8px 0 4px;
  text-align: center;
}
</style>
