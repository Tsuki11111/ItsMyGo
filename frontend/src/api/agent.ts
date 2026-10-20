import { UNAUTHORIZED_EVENT, clearAuth, get, getToken, post } from './http'
import type {
  AgentConversePayload,
  AgentHistoryView,
  AgentSessionView,
  AgentStreamEvent,
} from './types'

/**
 * 发一条消息并消费 SSE 事件流。
 *
 * 用 fetch 而不是 EventSource：这里的事件流只属于本次 POST 调用，
 * 且 POST 可以带 Authorization，不需要把 token 放进 URL。
 */
export async function streamAgentMessage(
  payload: AgentConversePayload,
  onEvent: (event: AgentStreamEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const token = getToken()
  const response = await fetch('/api/agent/converse', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
    signal,
  })

  if (response.status === 401) {
    clearAuth()
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
    throw new Error('登录已过期，请重新登录')
  }

  const contentType = response.headers.get('content-type') || ''
  if (!response.ok || !contentType.includes('text/event-stream')) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || '客服服务暂时不可用')
  }
  if (!response.body) {
    throw new Error('当前浏览器不支持流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        const frame = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        handleFrame(frame, onEvent)
        boundary = buffer.indexOf('\n\n')
      }
    }
    buffer += decoder.decode()
    if (buffer.trim()) {
      handleFrame(buffer, onEvent)
    }
  } finally {
    reader.releaseLock()
  }
}

export function stopAgent(sessionId: string, runId: string): Promise<boolean> {
  return post<boolean>('/agent/stop', { sessionId, runId })
}

export function fetchAgentHistory(
  sessionId: string,
  limit = 20,
): Promise<AgentHistoryView> {
  return get<AgentHistoryView>('/agent/history', { sessionId, limit })
}

/** 当前用户的历史会话（跨设备）；agent 和 Java 都会按用户过滤 */
export function fetchAgentSessions(limit = 20): Promise<AgentSessionView[]> {
  return get<AgentSessionView[]>('/agent/sessions', { limit })
}

function handleFrame(frame: string, onEvent: (event: AgentStreamEvent) => void) {
  let event = ''
  const dataLines: string[] = []

  for (const line of frame.split('\n')) {
    if (!line || line.startsWith(':')) continue
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }

  if (!event || dataLines.length === 0) return
  try {
    const data = JSON.parse(dataLines.join('\n'))
    onEvent({ type: event, ...data } as AgentStreamEvent)
  } catch {
    // 忽略无法解析的帧，不要让整条流崩掉
  }
}
