import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { createChatStreamTicket, fetchChatUnread } from '@/api/chat'
import { getToken } from '@/api/http'
import { useUserStore } from '@/stores/user'
import type { ChatMessage, ChatScope, ConversationView } from '@/api/types'

export type ChatSocketEvent =
  | {
      type: 'message'
      scope: ChatScope
      message: ChatMessage
      conversation: ConversationView
      unread: number
    }
  | { type: 'read'; conversationId: number; readerType: ChatScope; lastReadMessageId: number }
  | { type: 'presence'; merchantId: number; online: boolean }

interface ConnectedEvent {
  userId: number
  customerUnread: number
  merchantUnread: number
}

interface MessageEventPayload {
  scope: ChatScope
  message: ChatMessage
  conversation: ConversationView
  unread: number
}

interface ReadEventPayload {
  conversationId: number
  readerType: ChatScope
  lastReadMessageId: number
}

interface PresenceEventPayload {
  merchantId: number
  online: boolean
}

/**
 * 全局消息状态：未读角标 + SSE 生命周期。
 *
 * App.vue 根据登录态连接/断开；页面通过 `onEvent` 订阅事件，
 * 离开页面时记得取消订阅。
 */
export const useChatStore = defineStore('chat', () => {
  const customerUnread = ref(0)
  const merchantUnread = ref(0)
  const connected = ref(false)

  const unreadTotal = computed(() => customerUnread.value + merchantUnread.value)

  let source: EventSource | null = null
  let reconnectTimer: number | undefined
  let attempts = 0
  let stopped = false
  let connecting = false
  const listeners = new Set<(event: ChatSocketEvent) => void>()

  function canConnect(): boolean {
    const user = useUserStore()
    // 管理员也要连：平台自营店的商家侧消息由管理员接收
    return Boolean(getToken() && user.isLoggedIn)
  }

  async function connect() {
    stopped = false
    if (!canConnect() || source || connecting) return
    await open()
  }

  async function open() {
    if (stopped || !canConnect() || source || connecting) return
    connecting = true
    try {
      const { ticket } = await createChatStreamTicket()
      if (stopped) return
      const es = new EventSource(`/api/chat/stream?ticket=${encodeURIComponent(ticket)}`)
      source = es
      es.addEventListener('connected', (event) => {
        const data = parse<ConnectedEvent>(event)
        if (!data) return
        customerUnread.value = data.customerUnread
        merchantUnread.value = data.merchantUnread
        connected.value = true
      })
      es.addEventListener('message', (event) => {
        const data = parse<MessageEventPayload>(event)
        if (!data) return
        if (data.scope === 'CUSTOMER') customerUnread.value = data.unread
        else merchantUnread.value = data.unread
        connected.value = true
        emit({
          type: 'message',
          scope: data.scope,
          message: data.message,
          conversation: data.conversation,
          unread: data.unread,
        })
      })
      es.addEventListener('read', (event) => {
        const data = parse<ReadEventPayload>(event)
        if (data) emit({ type: 'read', ...data })
      })
      es.addEventListener('presence', (event) => {
        const data = parse<PresenceEventPayload>(event)
        if (data) emit({ type: 'presence', ...data })
      })
      es.onopen = () => {
        connected.value = true
        attempts = 0
      }
      es.onerror = () => {
        es.close()
        if (source === es) source = null
        connected.value = false
        scheduleReconnect()
      }
    } catch {
      scheduleReconnect()
    } finally {
      connecting = false
    }
  }

  function scheduleReconnect() {
    if (stopped || reconnectTimer !== undefined || !canConnect()) return
    const delay = Math.min(1_000 * 2 ** Math.min(attempts, 5), 30_000)
    attempts += 1
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = undefined
      void open()
    }, delay)
  }

  function disconnect() {
    stopped = true
    if (reconnectTimer !== undefined) {
      window.clearTimeout(reconnectTimer)
      reconnectTimer = undefined
    }
    source?.close()
    source = null
    connected.value = false
    customerUnread.value = 0
    merchantUnread.value = 0
  }

  async function refreshUnread() {
    if (!canConnect()) return
    try {
      const unread = await fetchChatUnread()
      customerUnread.value = unread.customerUnread
      merchantUnread.value = unread.merchantUnread
    } catch {
      // 未登录/网络异常时保持旧值，拦截器已提示
    }
  }

  /** 标记已读后由聊天页回写，避免等 SSE 事件才更新角标 */
  function setUnreadFor(scope: ChatScope, value: number) {
    if (scope === 'CUSTOMER') customerUnread.value = Math.max(0, value)
    else merchantUnread.value = Math.max(0, value)
  }

  function onEvent(listener: (event: ChatSocketEvent) => void): () => void {
    listeners.add(listener)
    return () => listeners.delete(listener)
  }

  function emit(event: ChatSocketEvent) {
    listeners.forEach((listener) => listener(event))
  }

  return {
    customerUnread,
    merchantUnread,
    unreadTotal,
    connected,
    connect,
    disconnect,
    refreshUnread,
    setUnreadFor,
    onEvent,
  }
})

function parse<T>(event: Event): T | null {
  try {
    return JSON.parse((event as MessageEvent).data) as T
  } catch {
    return null
  }
}
