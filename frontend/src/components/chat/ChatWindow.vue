<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import ProductImage from '@/components/ProductImage.vue'
import ChatComposer from '@/components/chat/ChatComposer.vue'
import MessageBubble from '@/components/chat/MessageBubble.vue'
import type {
  ChatMessage,
  ChatScope,
  ChatSendPayload,
  ConversationView,
} from '@/api/types'

const props = defineProps<{
  conversation: ConversationView | null
  messages: ChatMessage[]
  mode: ChatScope
  loading: boolean
  hasMore: boolean
  peerLastReadMessageId: number | null
  sending: boolean
  currentUserId: number
}>()

const emit = defineEmits<{
  send: [payload: ChatSendPayload]
  loadOlder: []
  back: []
}>()

const scrollEl = ref<HTMLElement>()

const peerName = computed(() => {
  if (!props.conversation) return ''
  return props.mode === 'CUSTOMER'
    ? props.conversation.merchantName
    : props.conversation.customerName
})

const peerAvatar = computed(() => {
  if (!props.conversation) return ''
  return props.mode === 'CUSTOMER'
    ? props.conversation.merchantLogo
    : props.conversation.customerAvatar
})

const peerId = computed(() => {
  if (!props.conversation) return 0
  return props.mode === 'CUSTOMER'
    ? props.conversation.merchantId
    : props.conversation.customerId
})

function mine(message: ChatMessage): boolean {
  return message.senderType !== 'AGENT'
    && message.senderType !== 'SYSTEM'
    && message.senderUserId === props.currentUserId
}

function read(message: ChatMessage): boolean {
  return mine(message) && message.id <= (props.peerLastReadMessageId ?? 0)
}

async function scrollToBottom() {
  await nextTick()
  const el = scrollEl.value
  if (el) el.scrollTop = el.scrollHeight
}

defineExpose({ scrollToBottom })
</script>

<template>
  <section class="window-pane">
    <template v-if="conversation">
      <header class="window-head">
        <button type="button" class="back-btn" title="返回会话列表" @click="emit('back')">
          <el-icon><ArrowLeft /></el-icon>
        </button>

        <span class="avatar">
          <ProductImage :src="peerAvatar" :name="peerName" :id="peerId" />
        </span>
        <div class="who">
          <p class="name">{{ peerName }}</p>
          <p v-if="mode === 'CUSTOMER'" class="status" :class="{ online: conversation.peerOnline }">
            {{ conversation.peerOnline ? '在线' : '离线' }}
          </p>
          <p v-else class="status">顾客留言</p>
        </div>

        <RouterLink
          v-if="mode === 'CUSTOMER' && conversation.merchantId !== 1"
          :to="`/shop/${conversation.merchantId}`"
          class="shop-link"
        >
          进店逛逛
        </RouterLink>
      </header>

      <div ref="scrollEl" class="messages" :aria-busy="loading">
        <!-- 切换/首次进入会话时用消息骨架占位，避免右侧整块空白 -->
        <template v-if="loading && messages.length === 0">
          <div class="skeleton-day shimmer" />
          <div
            v-for="n in 5"
            :key="n"
            class="skeleton-bubble-row"
            :class="n % 2 ? 'left' : 'right'"
          >
            <span
              class="skeleton-bubble shimmer"
              :style="{ width: `${n % 2 ? 44 : 34 + (n % 3) * 8}%` }"
            />
          </div>
        </template>

        <template v-else>
          <div v-if="hasMore" class="older">
            <el-button link type="primary" size="small" @click="emit('loadOlder')">
              加载更早消息
            </el-button>
          </div>

          <el-empty
            v-if="messages.length === 0"
            description="还没有消息，打个招呼吧"
            :image-size="80"
          />

          <TransitionGroup name="bubble" tag="div" class="bubble-list">
            <MessageBubble
              v-for="message in messages"
              :key="message.id"
              :message="message"
              :mine="mine(message)"
              :read="read(message)"
              :mode="mode"
            />
          </TransitionGroup>
        </template>
      </div>

      <ChatComposer
        :sending="sending"
        :peer-online="conversation.peerOnline"
        :mode="mode"
        @send="emit('send', $event)"
      />
    </template>

    <!-- 深链/首次打开会话时先渲染窗口骨架，再淡入真实内容 -->
    <template v-else-if="loading">
      <header class="window-head">
        <button type="button" class="back-btn" title="返回会话列表" @click="emit('back')">
          <el-icon><ArrowLeft /></el-icon>
        </button>
        <span class="avatar skeleton-avatar shimmer" />
        <div class="who">
          <span class="skeleton-line shimmer skeleton-name" />
          <span class="skeleton-line shimmer skeleton-status" />
        </div>
      </header>
      <div class="messages skeleton-messages" aria-hidden="true">
        <div class="skeleton-day shimmer" />
        <div
          v-for="n in 5"
          :key="n"
          class="skeleton-bubble-row"
          :class="n % 2 ? 'left' : 'right'"
        >
          <span
            class="skeleton-bubble shimmer"
            :style="{ width: `${n % 2 ? 44 : 34 + (n % 3) * 8}%` }"
          />
        </div>
      </div>
      <div class="composer-skeleton" aria-hidden="true">
        <span class="skeleton-line shimmer" />
      </div>
    </template>

    <el-empty v-else description="选择一个会话开始聊天" class="blank" />
  </section>
</template>

<style scoped>
.window-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #fafafa;
}

.window-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
  padding: 10px 14px;
  background: #fff;
  border-bottom: 1px solid var(--c-border);
}

.back-btn {
  display: none;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--c-text-2);
  background: none;
  border: none;
  border-radius: 50%;
  cursor: pointer;
}

.back-btn:hover {
  background: var(--c-pink-tint);
  color: var(--c-pink);
}

.avatar {
  width: 36px;
  height: 36px;
  flex: none;
  border-radius: 9px;
  overflow: hidden;
  background: var(--c-pink-tint);
}

.who {
  flex: 1;
  min-width: 0;
}

.name {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status {
  margin: 2px 0 0;
  font-size: 11px;
  color: var(--c-text-3);
}

.status.online {
  color: #3fbf6f;
}

.shop-link {
  flex: none;
  font-size: 12px;
  color: var(--c-pink);
  text-decoration: none;
}

.messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px;
}

.bubble-list {
  display: flex;
  flex-direction: column;
}

.bubble-enter-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.bubble-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.bubble-leave-active {
  display: none;
}

.skeleton-day {
  width: 64px;
  height: 8px;
  margin: 2px auto 16px;
  border-radius: 4px;
}

.skeleton-bubble-row {
  display: flex;
  margin-bottom: 14px;
}

.skeleton-bubble-row.right {
  justify-content: flex-end;
}

.skeleton-bubble {
  height: 38px;
  border-radius: 12px;
}

.skeleton-messages {
  padding-top: 20px;
}

.skeleton-avatar {
  width: 36px;
  height: 36px;
  flex: none;
  border-radius: 9px;
}

.skeleton-line {
  display: block;
  height: 10px;
  border-radius: 5px;
}

.skeleton-name {
  width: 120px;
  height: 12px;
  margin-bottom: 7px;
}

.skeleton-status {
  width: 60px;
  height: 8px;
}

.composer-skeleton {
  flex: none;
  height: 74px;
  padding: 14px;
  background: #fff;
  border-top: 1px solid var(--c-border);
}

.composer-skeleton .skeleton-line {
  width: 72%;
  height: 34px;
  margin: 5px auto;
  border-radius: 10px;
}

.shimmer {
  background: linear-gradient(90deg, #f2f2f4 25%, #e9e9ee 37%, #f2f2f4 63%);
  background-size: 400% 100%;
  animation: shimmer 1.2s ease-in-out infinite;
}

@keyframes shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .shimmer,
  .bubble-enter-active {
    animation: none;
    transition: none;
  }
}

.older {
  margin-bottom: 8px;
  text-align: center;
}

.blank {
  margin: auto;
}

@media (max-width: 820px) {
  .back-btn {
    display: inline-flex;
  }
}
</style>
