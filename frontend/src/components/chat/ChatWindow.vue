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

      <div ref="scrollEl" v-loading="loading" class="messages">
        <div v-if="hasMore" class="older">
          <el-button link type="primary" size="small" @click="emit('loadOlder')">
            加载更早消息
          </el-button>
        </div>

        <el-empty
          v-if="!loading && messages.length === 0"
          description="还没有消息，打个招呼吧"
          :image-size="80"
        />

        <MessageBubble
          v-for="message in messages"
          :key="message.id"
          :message="message"
          :mine="mine(message)"
          :read="read(message)"
          :mode="mode"
        />
      </div>

      <ChatComposer
        :sending="sending"
        :peer-online="conversation.peerOnline"
        :mode="mode"
        @send="emit('send', $event)"
      />
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
