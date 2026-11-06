import { get, post } from './http'

/** 一个知识缺口（rag_helper 按归一化问题聚合后的结果） */
export interface KnowledgeGap {
  gapId: string
  question: string
  count: number
  firstAt: string
  lastAt: string
  products: string[]
  local: number
  web: number
  status: string
  statusNote: string
}

export interface KnowledgeGapList {
  days: number
  total: number
  gaps: KnowledgeGap[]
}

export interface KnowledgeGapReport {
  days: number
  markdown: string
}

export interface KnowledgeGapDraft {
  title: string
  markdown: string
  gapIds: string[]
  note: string
}

export interface KnowledgeGapStatusResult {
  updated: number
  status: string
}

export interface KnowledgeGapPublish {
  taskId: string
  fileTitle: string
  statusUpdated: boolean
  note: string
}

/** 缺口列表：不调模型，很快；仍给 60s 余量 */
export function fetchKnowledgeGaps(days: number, minCount: number): Promise<KnowledgeGapList> {
  return get<KnowledgeGapList>('/admin/knowledge-gaps', { days, minCount }, 60000)
}

/** 周报要算 embedding（失败会自动降级），给长超时 */
export function fetchKnowledgeGapReport(days: number): Promise<KnowledgeGapReport> {
  return get<KnowledgeGapReport>('/admin/knowledge-gaps/report', { days }, 180000)
}

/** 生成草稿：一次模型调用，几十秒；**返回的是草稿，不入库** */
export function generateKnowledgeGapDraft(payload: {
  gapIds: string[]
  days: number
  title?: string
}): Promise<KnowledgeGapDraft> {
  return post<KnowledgeGapDraft>('/admin/knowledge-gaps/draft', payload, 180000)
}

export function updateKnowledgeGapStatus(payload: {
  gapIds: string[]
  status: string
  note?: string
}): Promise<KnowledgeGapStatusResult> {
  return post<KnowledgeGapStatusResult>('/admin/knowledge-gaps/status', payload)
}

/** 发布草稿：走 rag_helper 导入链路，返回 taskId 供知识库页轮询进度 */
export function publishKnowledgeGapDraft(payload: {
  title: string
  markdown: string
  gapIds: string[]
}): Promise<KnowledgeGapPublish> {
  return post<KnowledgeGapPublish>('/admin/knowledge-gaps/publish', payload, 120000)
}
