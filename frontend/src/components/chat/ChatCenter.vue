<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ChatWindow from '@/components/chat/ChatWindow.vue'
import ConversationList from '@/components/chat/ConversationList.vue'
import AgentChatPanel from '@/components/chat/AgentChatPanel.vue'
import {
  fetchAdminConversation,
  fetchAdminConversations,
  fetchAdminMessages,
  fetchConversation,
  fetchConversations,
  fetchMerchantConversation,
  fetchMerchantConversations,
  fetchMerchantMessages,
  fetchMessages,
  markAdminConversationRead,
  markConversationRead,
  markMerchantConversationRead,
  sendAdminMessage,
  sendMerchantMessage,
  sendMessage,
} from '@/api/chat'
import type {
  ChatMessage,
  ChatMessagePage,
  ChatScope,
  ChatSendPayload,
  ConversationView,
  PageResponse,
} from '@/api/types'
import { useChatStore, type ChatSocketEvent } from '@/stores/chat'
import { useUserStore } from '@/stores/user'

const props = defineProps<{ mode: ChatScope; admin?: boolean }>()

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const userStore = useUserStore()

const PAGE_SIZE = 30
const CONVERSATION_PAGE_SIZE = 50

const api = props.admin
  ? {
      list: fetchAdminConversations,
      detail: fetchAdminConversation,
      messages: fetchAdminMessages,
      send: sendAdminMessage,
      read: markAdminConversationRead,
    }
  : props.mode === 'CUSTOMER'
    ? {
        list: fetchConversations,
        detail: fetchConversation,
        messages: fetchMessages,
        send: sendMessage,
        read: markConversationRead,
      }
    : {
        list: fetchMerchantConversations,
        detail: fetchMerchantConversation,
        messages: fetchMerchantMessages,
        send: sendMerchantMessage,
        read: markMerchantConversationRead,
      }

const basePath = props.admin
  ? '/admin/messages'
  : props.mode === 'CUSTOMER' ? '/user/messages' : '/merchant/messages'

const conversations = ref<ConversationView[]>([])
const total = ref(0)
const loadingList = ref(false)
const activeId = ref<number | null>(null)
const agentActive = ref(false)
const active = ref<ConversationView | null>(null)
const messages = ref<ChatMessage[]>([])
const loadingMessages = ref(false)
const hasMore = ref(false)
const peerLastReadMessageId = ref<number | null>(null)
const sending = ref(false)
const chatWindowRef = ref<InstanceType<typeof ChatWindow> | null>(null)

let lastReadSent = 0
let reading = false
let unsubscribe: (() => void) | null = null

const routeId = computed(() => {
  const raw = route.params.conversationId
  const value = Array.isArray(raw) ? raw[0] : raw
  if (!value) return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
})

/** 客服智能体用固定路由 /messages/agent，不是真实会话 */
const routeAgent = computed(() => {
  const raw = route.params.conversationId
  const value = Array.isArray(raw) ? raw[0] : raw
  return value === 'agent'
})

const currentUserId = computed(() => userStore.user?.userId ?? 0)

function sortConversations() {
  conversations.value.sort((a, b) => {
    const left = a.lastMessageAt ?? ''
    const right = b.lastMessageAt ?? ''
    if (left !== right) return right.localeCompare(left)
    return b.id - a.id
  })
}

async function loadConversations(more = false) {
  loadingList.value = true
  try {
    const page = more ? Math.floor(conversations.value.length / CONVERSATION_PAGE_SIZE) + 1 : 1
    const result: PageResponse<ConversationView> = await api.list(page, CONVERSATION_PAGE_SIZE)
    if (more) {
      const known = new Set(conversations.value.map((item) => item.id))
      conversations.value = [
        ...conversations.value,
        ...result.records.filter((item) => !known.has(item.id)),
      ]
    } else {
      conversations.value = result.records
    }
    total.value = result.total
    sortConversations()
  } catch {
    // 拦截器已提示
  } finally {
    loadingList.value = false
  }
}

function loadMore() {
  void loadConversations(true)
}

function selectConversation(id: number) {
  if (id === activeId.value) return
  router.push(`${basePath}/${id}`)
}

function selectAgent() {
  if (agentActive.value) return
  router.push(`${basePath}/agent`)
}

function clearActive() {
  activeId.value = null
  active.value = null
  messages.value = []
  hasMore.value = false
  peerLastReadMessageId.value = null
  lastReadSent = 0
}

function backToList() {
  router.push(basePath)
}

async function openConversation(id: number) {
  if (activeId.value === id && active.value) return
  loadingMessages.value = true
  try {
    const [detail, page] = await Promise.all([
      api.detail(id),
      api.messages(id, { size: PAGE_SIZE }),
    ])
    // 用户可能已快速切到别的会话，旧请求的结果直接丢弃
    if (routeId.value !== id) return
    activeId.value = id
    active.value = detail
    messages.value = page.records
    hasMore.value = page.hasMore
    peerLastReadMessageId.value = page.peerLastReadMessageId
    lastReadSent = 0

    const hit = conversations.value.find((item) => item.id === id)
    if (hit) {
      hit.unread = detail.unread
    } else {
      conversations.value.unshift(detail)
      sortConversations()
    }

    await markReadNow()
    await chatWindowRef.value?.scrollToBottom()
  } catch {
    if (routeId.value === id) {
      ElMessage.error('会话不存在或无权访问')
      await router.replace(basePath)
    }
  } finally {
    loadingMessages.value = false
  }
}

async function markReadNow() {
  if (!activeId.value || messages.value.length === 0 || reading) return
  const last = messages.value[messages.value.length - 1]
  if (last.id <= lastReadSent) return

  const previous = lastReadSent
  lastReadSent = last.id
  reading = true
  try {
    const unread = await api.read(activeId.value, last.id)
    chatStore.setUnreadFor(props.mode, unread)
    if (active.value) active.value.unread = 0
    const hit = conversations.value.find((item) => item.id === activeId.value)
    if (hit) hit.unread = 0
  } catch {
    lastReadSent = previous
  } finally {
    reading = false
    const latest = messages.value[messages.value.length - 1]
    if (latest && latest.id > lastReadSent) {
      void markReadNow()
    }
  }
}

async function loadOlder() {
  if (!activeId.value || messages.value.length === 0 || !hasMore.value) return
  const id = activeId.value
  const beforeId = messages.value[0].id
  try {
    const page: ChatMessagePage = await api.messages(id, {
      beforeId,
      size: PAGE_SIZE,
    })
    if (activeId.value !== id) return
    messages.value = [...page.records, ...messages.value]
    hasMore.value = page.hasMore
  } catch {
    // 拦截器已提示
  }
}

async function onSend(payload: ChatSendPayload) {
  if (!activeId.value || sending.value) return
  const id = activeId.value
  sending.value = true
  try {
    const message = await api.send(id, payload)
    if (activeId.value !== id) return
    if (!messages.value.some((item) => item.id === message.id)) {
      messages.value.push(message)
    }
    if (active.value) {
      active.value.lastMessageId = message.id
      active.value.lastMessageAt = message.createdAt
      active.value.lastMessagePreview = previewOf(payload, message)
    }
    await chatWindowRef.value?.scrollToBottom()
  } catch {
    // 拦截器已提示
  } finally {
    sending.value = false
  }
}

function previewOf(payload: ChatSendPayload, message: ChatMessage): string {
  if (payload.type === 'IMAGE') return '[图片]'
  if (payload.type === 'PRODUCT') return `[商品] ${message.content}`
  if (payload.type === 'ORDER') return `[订单] ${message.content}`
  return message.content.length > 60 ? `${message.content.slice(0, 60)}…` : message.content
}

function handleEvent(event: ChatSocketEvent) {
  if (event.type === 'message') {
    if (event.scope !== props.mode) return

    const known = conversations.value.find((item) => item.id === event.conversation.id)
    if (known) {
      Object.assign(known, event.conversation)
    } else {
      conversations.value.unshift(event.conversation)
    }
    sortConversations()

    if (event.message.conversationId !== activeId.value) return

    active.value = event.conversation
    if (!messages.value.some((item) => item.id === event.message.id)) {
      messages.value.push(event.message)
      const isPeer = event.message.senderType !== props.mode
      if (isPeer) {
        void markReadNow()
      }
      void chatWindowRef.value?.scrollToBottom()
    }
  } else if (event.type === 'read') {
    if (event.conversationId !== activeId.value || event.readerType === props.mode) return
    peerLastReadMessageId.value = Math.max(
      peerLastReadMessageId.value ?? 0,
      event.lastReadMessageId,
    )
  } else if (event.type === 'presence') {
    for (const item of conversations.value) {
      if (item.merchantId === event.merchantId) item.peerOnline = event.online
    }
    if (active.value && active.value.merchantId === event.merchantId) {
      active.value.peerOnline = event.online
    }
  }
}

watch([routeId, routeAgent], ([id, isAgent]) => {
  if (isAgent) {
    agentActive.value = true
    clearActive()
    return
  }
  agentActive.value = false
  if (id == null) {
    clearActive()
    return
  }
  void openConversation(id)
}, { immediate: true })

onMounted(() => {
  void loadConversations()
  void chatStore.refreshUnread()
  unsubscribe = chatStore.onEvent(handleEvent)
})

onBeforeUnmount(() => {
  unsubscribe?.()
})
</script>

<template>
  <div class="chat-center" :class="{ 'has-active': activeId !== null || agentActive }">
    <ConversationList
      :items="conversations"
      :active-id="activeId"
      :mode="mode"
      :loading="loadingList"
      :total="total"
      :agent-active="agentActive"
      @select="selectConversation"
      @select-agent="selectAgent"
      @load-more="loadMore"
    />

    <AgentChatPanel
      v-if="agentActive"
      :mode="mode"
      @back="backToList"
    />

    <ChatWindow
      v-else
      ref="chatWindowRef"
      :conversation="active"
      :messages="messages"
      :mode="mode"
      :loading="loadingMessages"
      :has-more="hasMore"
      :peer-last-read-message-id="peerLastReadMessageId"
      :sending="sending"
      :current-user-id="currentUserId"
      @send="onSend"
      @load-older="loadOlder"
      @back="backToList"
    />
  </div>
</template>

<style scoped>
.chat-center {
  display: grid;
  /* minmax(0, 1fr)：避免长执行流按 min-content 把 1fr 轨道撑宽，进而顶出窗口 */
  grid-template-columns: 300px minmax(0, 1fr);
  height: min(860px, calc(100vh - 130px));
  min-height: 600px;
  overflow: hidden;
  background: #fff;
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

@media (max-width: 820px) {
  .chat-center {
    grid-template-columns: minmax(0, 1fr);
    height: calc(100vh - 140px);
    min-height: 480px;
  }

  .chat-center.has-active :deep(.list-pane) {
    display: none;
  }

  .chat-center:not(.has-active) :deep(.window-pane) {
    display: none;
  }
}
</style>
