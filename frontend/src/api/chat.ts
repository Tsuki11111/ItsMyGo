import { get, post, postForm } from './http'
import type {
  ChatMessage,
  ChatMessagePage,
  ChatSendPayload,
  ChatStreamTicket,
  ChatUnreadView,
  ConversationView,
  PageResponse,
  StartConversationPayload,
} from './types'

// ── 顾客侧 ──

export function startConversation(payload: StartConversationPayload): Promise<ConversationView> {
  return post<ConversationView>('/chat/conversations', payload)
}

export function fetchConversations(page = 1, size = 30): Promise<PageResponse<ConversationView>> {
  return get<PageResponse<ConversationView>>('/chat/conversations', { page, size })
}

export function fetchConversation(id: number): Promise<ConversationView> {
  return get<ConversationView>(`/chat/conversations/${id}`)
}

export function fetchMessages(
  id: number,
  params: { beforeId?: number; afterId?: number; size?: number } = {},
): Promise<ChatMessagePage> {
  return get<ChatMessagePage>(`/chat/conversations/${id}/messages`, { ...params })
}

export function sendMessage(id: number, payload: ChatSendPayload): Promise<ChatMessage> {
  return post<ChatMessage>(`/chat/conversations/${id}/messages`, payload)
}

export function markConversationRead(id: number, lastMessageId: number): Promise<number> {
  return post<number>(`/chat/conversations/${id}/read`, { lastMessageId })
}

export function fetchChatUnread(): Promise<ChatUnreadView> {
  return get<ChatUnreadView>('/chat/unread-count')
}

/** EventSource 不能带 Authorization，先换一个 60 秒一次性票据 */
export function createChatStreamTicket(): Promise<ChatStreamTicket> {
  return post<ChatStreamTicket>('/chat/stream-ticket')
}

export function uploadChatImage(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  return postForm<string>('/chat/files/upload', form)
}

// ── 商家侧 ──

export function fetchMerchantConversations(
  page = 1,
  size = 30,
): Promise<PageResponse<ConversationView>> {
  return get<PageResponse<ConversationView>>('/merchant/chat/conversations', { page, size })
}

export function fetchMerchantConversation(id: number): Promise<ConversationView> {
  return get<ConversationView>(`/merchant/chat/conversations/${id}`)
}

export function fetchMerchantMessages(
  id: number,
  params: { beforeId?: number; afterId?: number; size?: number } = {},
): Promise<ChatMessagePage> {
  return get<ChatMessagePage>(`/merchant/chat/conversations/${id}/messages`, { ...params })
}

export function sendMerchantMessage(id: number, payload: ChatSendPayload): Promise<ChatMessage> {
  return post<ChatMessage>(`/merchant/chat/conversations/${id}/messages`, payload)
}

export function markMerchantConversationRead(id: number, lastMessageId: number): Promise<number> {
  return post<number>(`/merchant/chat/conversations/${id}/read`, { lastMessageId })
}

// ── 平台自营店：管理员以商家身份处理 ──

export function fetchAdminConversations(
  page = 1,
  size = 30,
): Promise<PageResponse<ConversationView>> {
  return get<PageResponse<ConversationView>>('/admin/chat/conversations', { page, size })
}

export function fetchAdminConversation(id: number): Promise<ConversationView> {
  return get<ConversationView>(`/admin/chat/conversations/${id}`)
}

export function fetchAdminMessages(
  id: number,
  params: { beforeId?: number; afterId?: number; size?: number } = {},
): Promise<ChatMessagePage> {
  return get<ChatMessagePage>(`/admin/chat/conversations/${id}/messages`, { ...params })
}

export function sendAdminMessage(id: number, payload: ChatSendPayload): Promise<ChatMessage> {
  return post<ChatMessage>(`/admin/chat/conversations/${id}/messages`, payload)
}

export function markAdminConversationRead(id: number, lastMessageId: number): Promise<number> {
  return post<number>(`/admin/chat/conversations/${id}/read`, { lastMessageId })
}
