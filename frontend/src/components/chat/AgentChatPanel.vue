<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  Clock,
  Loading,
  Plus,
  Promotion,
  Service,
  VideoPause,
} from '@element-plus/icons-vue'
import {
  fetchAgentActions,
  fetchAgentHistory,
  fetchAgentSessions,
  stopAgent,
  streamAgentMessage,
} from '@/api/agent'
import AgentActionCard from './AgentActionCard.vue'
import { useUserStore } from '@/stores/user'
import type {
  AgentActionView,
  AgentImage,
  AgentOrderResult,
  AgentSessionView,
  AgentStreamEvent,
  AgentTraceEvent,
  AgentUsageEvent,
  ChatScope,
} from '@/api/types'

interface UiMessage {
  id: string
  role: 'user' | 'assistant'
  text: string
  images: AgentImage[]
  traces: AgentTraceEvent[]
  usage: AgentUsageEvent | null
  webOnly: boolean
  error: string
  stopped: boolean
}

const props = defineProps<{ mode: ChatScope }>()
const emit = defineEmits<{ back: [] }>()

const userStore = useUserStore()

const messages = ref<UiMessage[]>([])
const actions = ref<AgentActionView[]>([])
const sessions = ref<AgentSessionView[]>([])
const input = ref('')
const streaming = ref(false)
const loadingHistory = ref(false)
const sessionId = ref<string | null>(null)
const runId = ref('')
const scrollEl = ref<HTMLElement>()
/** 执行流默认折叠；按消息 id 记录展开状态 */
const expandedTraces = ref<Record<string, boolean>>({})
const activeAssistantId = ref<string | null>(null)

let abortController: AbortController | null = null
let assistantSeq = 0

DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A') {
    node.setAttribute('target', '_blank')
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

/** 模型回复是 Markdown：先渲染，再用 DOMPurify 消毒后交给 v-html */
function renderMarkdown(text: string): string {
  if (!text) return ''
  const html = marked.parse(text, { async: false, breaks: true, gfm: true }) as string
  return DOMPurify.sanitize(html)
}

const sessionKey = computed(() =>
  userStore.user ? `its-my-go:agent-session:${userStore.user.userId}` : '',
)

const intro = computed(() =>
  props.mode === 'CUSTOMER'
    ? '可以帮你查询商品信息、使用方法和售后问题。'
    : '可以帮你查询商品信息、产品参数和售后问题。',
)

const suggestions = computed(() =>
  props.mode === 'CUSTOMER'
    ? ['打印机怎么安装墨盒？', '这款路由器怎么设置？', '售后怎么处理？']
    : ['这款商品的参数在哪里看？', '打印机怎么安装墨盒？', '售后怎么处理？'],
)

const canSend = computed(() => input.value.trim().length > 0 && !streaming.value)

/**
 * 只展示还需要用户关注的卡片：
 * - 待确认 / 执行中 / 失败：保留
 * - 已取消 / 已过期：隐藏
 * - 已完成：默认隐藏；但下单成功且订单还没支付时保留，否则用户没有「去支付」入口
 */
const visibleActions = computed(() =>
  actions.value.filter((action) => {
    if (action.status === 'PENDING_CONFIRM'
        || action.status === 'EXECUTING'
        || action.status === 'FAILED') {
      return true
    }
    if (action.status === 'SUCCEEDED' && action.type === 'CREATE_ORDER') {
      const result = action.result as AgentOrderResult | null
      return !!result?.orders.some((order) => order.status === 'PENDING_PAYMENT')
    }
    return false
  }),
)

const TRACE_LABELS: Record<string, string> = {
  delegate: '派发',
  tool: '工具',
  result: '结果',
  reply: '回复',
  node: '步骤',
}

function traceLine(trace: AgentTraceEvent): string {
  const label = TRACE_LABELS[trace.kind] || trace.kind
  return [label, trace.agent, trace.detail].filter(Boolean).join(' · ')
}

function toggleTraces(messageId: string) {
  expandedTraces.value = {
    ...expandedTraces.value,
    [messageId]: !expandedTraces.value[messageId],
  }
}

function isActiveAssistant(message: UiMessage): boolean {
  return streaming.value && activeAssistantId.value === message.id
}

function traceSummary(message: UiMessage): string {
  if (!message.traces.length) return '正在思考…'
  if (isActiveAssistant(message)) {
    return traceLine(message.traces[message.traces.length - 1])
  }
  return `执行过程 · ${message.traces.length} 步`
}

function usageText(usage: AgentUsageEvent): string {
  return `${usage.calls} 次调用 · ${usage.totalTokens} tokens · ¥${usage.cost.toFixed(4)} · ${(usage.elapsedMs / 1000).toFixed(1)}s`
}

function findMessage(id: string): UiMessage | undefined {
  return messages.value.find((item) => item.id === id)
}

function onActionUpdated(updated: AgentActionView) {
  const index = actions.value.findIndex((item) => item.actionNo === updated.actionNo)
  if (index >= 0) actions.value[index] = updated
  else actions.value.push(updated)
}

/** 用户去订单页支付 / 商家处理退款后回到聊天：重新拉一次动作，卡片状态才会更新 */
function onVisibilityChange() {
  if (document.visibilityState === 'visible' && sessionId.value && !streaming.value) {
    void loadActions(sessionId.value)
  }
}

async function scrollToBottom() {
  await nextTick()
  const el = scrollEl.value
  if (el) el.scrollTop = el.scrollHeight
}

async function loadActions(id: string | null) {
  if (!id) {
    actions.value = []
    return
  }
  try {
    actions.value = await fetchAgentActions(id)
  } catch {
    actions.value = []
  }
}

async function loadHistory(savedSessionId: string) {
  loadingHistory.value = true
  try {
    const view = await fetchAgentHistory(savedSessionId, 30)
    messages.value = view.messages.map((item, index) => ({
      id: `h-${index}`,
      role: item.role === 'user' ? 'user' : 'assistant',
      text: item.text,
      images: item.images || [],
      traces: item.traces || [],
      usage: null,
      webOnly: false,
      error: '',
      stopped: false,
    }))
    await loadActions(savedSessionId)
    await scrollToBottom()
  } catch {
    // 历史加载失败不销毁会话，用户仍可继续发送
  } finally {
    loadingHistory.value = false
  }
}

function persistSession(id: string | null) {
  if (!sessionKey.value) return
  if (id) localStorage.setItem(sessionKey.value, id)
  else localStorage.removeItem(sessionKey.value)
}

async function refreshSessions() {
  try {
    sessions.value = await fetchAgentSessions(20)
  } catch {
    // 会话列表失败不影响当前对话
  }
}

async function selectSession(command: unknown) {
  const id = String(command || '')
  if (streaming.value || !id) return
  sessionId.value = id
  persistSession(id)
  messages.value = []
  actions.value = []
  await loadHistory(id)
}

async function sendMessage(preset?: string) {
  const content = (preset ?? input.value).trim()
  if (!content || streaming.value) return

  input.value = ''
  const userId = `u-${Date.now()}`
  const assistantId = `a-${++assistantSeq}`
  messages.value.push({
    id: userId,
    role: 'user',
    text: content,
    images: [],
    traces: [],
    usage: null,
    webOnly: false,
    error: '',
    stopped: false,
  })
  messages.value.push({
    id: assistantId,
    role: 'assistant',
    text: '',
    images: [],
    traces: [],
    usage: null,
    webOnly: false,
    error: '',
    stopped: false,
  })

  streaming.value = true
  activeAssistantId.value = assistantId
  runId.value = ''
  abortController = new AbortController()
  await scrollToBottom()

  const handleEvent = (event: AgentStreamEvent) => {
    const assistant = findMessage(assistantId)
    if (!assistant) return
    switch (event.type) {
      case 'start':
        sessionId.value = event.sessionId
        runId.value = event.runId
        persistSession(event.sessionId)
        break
      case 'trace':
        assistant.traces.push({
          kind: event.kind,
          agent: event.agent,
          detail: event.detail,
        })
        break
      case 'delta':
        assistant.text += event.delta
        break
      case 'final':
        assistant.text = event.answer || assistant.text
        assistant.images = event.images || []
        assistant.webOnly = event.webOnly
        break
      case 'usage':
        assistant.usage = {
          calls: event.calls,
          failed: event.failed,
          totalTokens: event.totalTokens,
          cost: event.cost,
          elapsedMs: event.elapsedMs,
          unpricedCalls: event.unpricedCalls,
        }
        break
      case 'error':
        assistant.error = event.message
        break
      case 'done':
        if (event.reason === 'stopped') assistant.stopped = true
        streaming.value = false
        activeAssistantId.value = null
        void refreshSessions()
        void loadActions(sessionId.value)
        break
    }
    void scrollToBottom()
  }

  try {
    await streamAgentMessage(
      { sessionId: sessionId.value, message: content },
      handleEvent,
      abortController.signal,
    )
    const assistant = findMessage(assistantId)
    if (assistant && !assistant.text && !assistant.error
        && !assistant.stopped && assistant.images.length === 0) {
      assistant.error = '客服没有返回内容，请重试'
    }
  } catch (error) {
    if ((error as Error).name !== 'AbortError') {
      const assistant = findMessage(assistantId)
      if (assistant && !assistant.stopped) {
        assistant.error = (error as Error).message || '客服服务暂时不可用'
      }
    }
  } finally {
    streaming.value = false
    activeAssistantId.value = null
    abortController = null
    runId.value = ''
    await scrollToBottom()
  }
}

async function stop() {
  if (!streaming.value) return
  const id = sessionId.value
  const currentRunId = runId.value
  if (id && currentRunId) {
    try {
      await stopAgent(id, currentRunId)
    } catch {
      // 停止失败也要断开本地流
    }
  }
  abortController?.abort()
  const last = messages.value[messages.value.length - 1]
  if (last && last.role === 'assistant' && !last.text) {
    last.stopped = true
  }
  streaming.value = false
  activeAssistantId.value = null
}

function newSession() {
  if (streaming.value) {
    void stop()
  }
  sessionId.value = null
  runId.value = ''
  persistSession(null)
  messages.value = []
  actions.value = []
}

function onEnter(event: KeyboardEvent) {
  if (event.isComposing) return
  event.preventDefault()
  void sendMessage()
}

onMounted(async () => {
  document.addEventListener('visibilitychange', onVisibilityChange)
  await refreshSessions()
  const saved = sessionKey.value ? localStorage.getItem(sessionKey.value) : null
  const target = saved || sessions.value[0]?.sessionId || null
  if (target) {
    sessionId.value = target
    persistSession(target)
    await loadHistory(target)
    // 老会话第一次加载时 agent 才认领归属，认领后再刷一次列表
    await refreshSessions()
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
  abortController?.abort()
  if (streaming.value && sessionId.value && runId.value) {
    void stopAgent(sessionId.value, runId.value).catch(() => undefined)
  }
})
</script>

<template>
  <section class="agent-panel">
    <header class="window-head">
      <button type="button" class="back-btn" title="返回会话列表" @click="emit('back')">
        <el-icon><ArrowLeft /></el-icon>
      </button>

      <span class="avatar">
        <el-icon><Service /></el-icon>
      </span>
      <div class="who">
        <p class="name">买小购</p>
        <p class="status">{{ streaming ? '正在回复…' : '智能客服' }}</p>
      </div>

      <div class="head-actions">
        <el-dropdown v-if="sessions.length" trigger="click" @command="selectSession">
          <el-button link size="small">
            <el-icon><Clock /></el-icon>
            <span>历史</span>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="item in sessions"
                :key="item.sessionId"
                :command="item.sessionId"
              >
                <span class="session-item">
                  <span class="session-title">{{ item.title || item.sessionId }}</span>
                  <span class="session-count">{{ item.count }} 条</span>
                </span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button v-if="streaming" link type="primary" size="small" @click="stop">
          <el-icon><VideoPause /></el-icon>
          <span>停止</span>
        </el-button>
        <el-button link size="small" @click="newSession">
          <el-icon><Plus /></el-icon>
          <span>新会话</span>
        </el-button>
      </div>
    </header>

    <div ref="scrollEl" v-loading="loadingHistory" class="messages">
      <template v-if="messages.length">
        <div
          v-for="message in messages"
          :key="message.id"
          class="msg-row"
          :class="{ mine: message.role === 'user' }"
        >
          <span v-if="message.role === 'assistant'" class="bot-avatar">
            <el-icon><Service /></el-icon>
          </span>

          <div class="bubble" :class="{ 'agent-bubble': message.role === 'assistant' }">
            <div
              v-if="message.traces.length || (isActiveAssistant(message) && !message.error)"
              class="trace-box"
            >
              <button type="button" class="trace-summary" @click="toggleTraces(message.id)">
                <el-icon
                  class="trace-icon"
                  :class="{ 'is-loading': isActiveAssistant(message) }"
                >
                  <Loading v-if="isActiveAssistant(message)" />
                  <ArrowDown v-else-if="expandedTraces[message.id]" />
                  <ArrowRight v-else />
                </el-icon>
                <span class="trace-text">{{ traceSummary(message) }}</span>
                <span v-if="message.traces.length" class="trace-action">
                  {{ expandedTraces[message.id] ? '收起' : '展开' }}
                </span>
              </button>

              <div v-if="expandedTraces[message.id] && message.traces.length" class="trace-list">
                <p
                  v-for="(trace, index) in message.traces"
                  :key="index"
                  class="trace-line"
                  :class="{
                    latest: isActiveAssistant(message) && index === message.traces.length - 1,
                  }"
                >
                  <span class="trace-index">{{ index + 1 }}</span>
                  <span class="trace-detail">{{ traceLine(trace) }}</span>
                </p>
              </div>
            </div>

            <p v-if="message.role === 'user' && message.text" class="answer plain">
              {{ message.text }}
            </p>
            <div
              v-else-if="message.role === 'assistant' && message.text"
              class="answer markdown-body"
              v-html="renderMarkdown(message.text)"
            ></div>

            <div v-if="message.images.length" class="images">
              <figure v-for="(image, index) in message.images" :key="index" class="image-item">
                <div class="image-wrap">
                  <el-image
                    :src="image.url"
                    :preview-src-list="message.images.map((item) => item.url)"
                    :initial-index="index"
                    preview-teleported
                    fit="contain"
                    class="answer-image"
                  />
                  <span class="image-index">图 {{ index + 1 }}</span>
                </div>
                <figcaption v-if="image.caption">{{ image.caption }}</figcaption>
              </figure>
            </div>

            <p v-if="message.webOnly" class="web-only">本轮仅联网结果，知识库未命中</p>
            <p v-if="message.usage" class="usage">{{ usageText(message.usage) }}</p>
            <p v-if="message.error" class="error">{{ message.error }}</p>
            <p v-if="message.stopped" class="stopped">已停止生成</p>
          </div>
        </div>
      </template>

      <div v-else-if="!visibleActions.length" class="welcome">
        <p class="greeting">你好，我是买小购。</p>
        <p>{{ intro }}</p>
        <p class="pending">可以问我产品使用、参数或售后问题。</p>

        <div class="suggestions">
          <p class="tip">你可以问：</p>
          <div class="chips">
            <button
              v-for="item in suggestions"
              :key="item"
              type="button"
              class="chip"
              :disabled="streaming"
              @click="sendMessage(item)"
            >
              {{ item }}
            </button>
          </div>
        </div>
      </div>

      <div v-if="visibleActions.length" class="action-list">
        <AgentActionCard
          v-for="action in visibleActions"
          :key="action.actionNo"
          :action="action"
          @updated="onActionUpdated"
        />
      </div>
    </div>

    <div class="composer">
      <el-input
        v-model="input"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 5 }"
        resize="none"
        :disabled="streaming"
        placeholder="问问买小购，比如：打印机怎么装墨盒？"
        @keydown.enter.exact="onEnter"
      />
      <el-button type="primary" :loading="streaming" :disabled="!canSend" @click="sendMessage()">
        <el-icon><Promotion /></el-icon>
        <span>发送</span>
      </el-button>
    </div>
  </section>
</template>

<style scoped>
.agent-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
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
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex: none;
  font-size: 18px;
  color: #fff;
  background: linear-gradient(135deg, var(--c-pink), #f39ab9);
  border-radius: 9px;
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
}

.status {
  margin: 2px 0 0;
  font-size: 11px;
  color: var(--c-text-3);
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: none;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 320px;
}

.session-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-count {
  flex: none;
  margin-left: auto;
  font-size: 11px;
  color: var(--c-text-3);
}

.messages {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 16px 14px;
}

.welcome {
  max-width: 560px;
  padding: 14px 16px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--c-text);
  background: #fff;
  border: 1px solid var(--c-border);
  border-radius: 12px;
}

.action-list {
  margin-top: 4px;
}

.welcome p {
  margin: 0;
}

.greeting {
  margin-bottom: 6px !important;
  font-weight: 600;
}

.pending {
  margin-top: 8px !important;
  font-size: 12px;
  color: var(--c-text-3);
}

.suggestions {
  margin-top: 16px;
}

.tip {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--c-text-3);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  padding: 7px 12px;
  font-size: 12px;
  font-family: inherit;
  color: var(--c-pink);
  background: var(--c-pink-tint);
  border: none;
  border-radius: var(--radius-btn);
  cursor: pointer;
}

.chip:hover:not(:disabled) {
  background: var(--c-pink-soft);
}

.chip:disabled {
  color: var(--c-text-3);
  cursor: not-allowed;
}

.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
  margin-bottom: 12px;
}

.msg-row.mine {
  justify-content: flex-end;
}

.bot-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex: none;
  font-size: 14px;
  color: #fff;
  background: linear-gradient(135deg, var(--c-pink), #f39ab9);
  border-radius: 8px;
}

.bubble {
  max-width: 78%;
  min-width: 0;
  padding: 9px 12px;
  font-size: 14px;
  line-height: 1.65;
  color: var(--c-text);
  background: var(--c-pink-soft);
  border-radius: 12px;
  overflow-wrap: anywhere;
}

.agent-bubble {
  max-width: 88%;
  background: #fff;
  border: 1px solid var(--c-border);
}

.answer {
  margin: 0;
  word-break: break-word;
}

.answer.plain {
  white-space: pre-wrap;
}

.markdown-body {
  line-height: 1.7;
}

.markdown-body :deep(p) {
  margin: 0 0 8px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 4px 0 8px;
  padding-left: 20px;
}

.markdown-body :deep(li) {
  margin: 2px 0;
}

.markdown-body :deep(strong) {
  font-weight: 600;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 10px 0 6px;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
}

.markdown-body :deep(code) {
  padding: 1px 4px;
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 12px;
  background: var(--c-pink-tint);
  border-radius: 4px;
}

.markdown-body :deep(pre) {
  margin: 8px 0;
  padding: 10px;
  overflow-x: auto;
  background: #f6f8fa;
  border-radius: 8px;
}

.markdown-body :deep(pre code) {
  padding: 0;
  background: none;
}

.markdown-body :deep(blockquote) {
  margin: 8px 0;
  padding-left: 10px;
  color: var(--c-text-2);
  border-left: 3px solid var(--c-border);
}

.markdown-body :deep(a) {
  color: var(--c-pink);
}

.markdown-body :deep(hr) {
  margin: 10px 0;
  border: none;
  border-top: 1px solid var(--c-border);
}

.markdown-body :deep(table) {
  width: 100%;
  margin: 8px 0;
  font-size: 13px;
  border-collapse: collapse;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 4px 6px;
  border: 1px solid var(--c-border);
}

.markdown-body :deep(img) {
  max-width: 100%;
  border-radius: 6px;
}

.trace-box {
  min-width: 0;
  max-width: 100%;
  margin-bottom: 8px;
  padding-bottom: 6px;
  overflow: hidden;
  border-bottom: 1px dashed var(--c-border);
}

.trace-summary {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
  padding: 0;
  font-family: inherit;
  font-size: 11px;
  line-height: 1.5;
  color: var(--c-text-3);
  text-align: left;
  background: none;
  border: none;
  cursor: pointer;
}

.trace-icon {
  flex: none;
  font-size: 12px;
}

.trace-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-action {
  flex: none;
  color: var(--c-pink);
}

.trace-list {
  max-height: 200px;
  min-width: 0;
  max-width: 100%;
  margin-top: 6px;
  padding-right: 4px;
  padding-left: 2px;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.trace-line {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
  max-width: 100%;
  margin: 3px 0;
  font-size: 11px;
  line-height: 1.5;
  color: var(--c-text-3);
}

.trace-line.latest {
  color: var(--c-pink);
}

.trace-index {
  flex: none;
  width: 16px;
  color: var(--c-text-3);
}

.trace-detail {
  min-width: 0;
  max-width: 100%;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.images {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
  align-items: start;
  margin-top: 12px;
}

.image-item {
  min-width: 0;
  margin: 0;
}

.image-wrap {
  position: relative;
  width: 100%;
}

.image-index {
  position: absolute;
  top: 6px;
  left: 6px;
  padding: 2px 7px;
  font-size: 11px;
  line-height: 1.4;
  color: #fff;
  background: rgba(0, 0, 0, 0.55);
  border-radius: 10px;
}

.image-item figcaption {
  margin-top: 6px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--c-text-3);
}

.answer-image {
  display: block;
  width: 100%;
  height: 280px;
  background: #fff;
  border: 1px solid var(--c-border);
  border-radius: 8px;
  cursor: zoom-in;
}

.web-only {
  margin: 8px 0 0;
  padding: 5px 8px;
  font-size: 11px;
  color: #b88230;
  background: #fff8e6;
  border-radius: 6px;
}

.usage {
  margin: 8px 0 0;
  font-size: 10px;
  color: var(--c-text-3);
}

.error {
  margin: 8px 0 0;
  font-size: 12px;
  color: #d9534f;
}

.stopped {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--c-text-3);
}

.composer {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  flex: none;
  padding: 10px 14px 12px;
  background: #fff;
  border-top: 1px solid var(--c-border);
}

.composer .el-button span {
  margin-left: 4px;
}

@media (max-width: 820px) {
  .back-btn {
    display: inline-flex;
  }
}
</style>
