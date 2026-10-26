import { get, post } from './http'

/** 与 rag_helper 评测接口对齐；rows / summary 保留 agent 的 snake_case 字段。 */
export interface AgentEvalCase {
  id: string
  question: string
  category: string
  doc: string
  source: string
  expectedOutcome: string
  hasGold: boolean
}

export interface AgentEvalCases {
  items: AgentEvalCase[]
  judgeModel: string
  configErrors: string[]
  warnings: string[]
}

export interface AgentEvalResultFile {
  name: string
  report: string
  rows: number
  size: number
  /** epoch 秒 */
  mtime: number
}

export interface AgentEvalResultList {
  items: AgentEvalResultFile[]
}

export interface AgentEvalClaim {
  claim: string
  supported: boolean
  evidence_ids: number[]
  reason: string
}

export interface AgentEvalJudge {
  model?: string
  groundedness?: number | null
  answer_relevance?: number | null
  total_claims?: number
  claims?: AgentEvalClaim[]
  notes?: string
  parse_ok?: boolean
}

export interface AgentEvalRecall {
  eligible?: boolean
  recall?: number
  rr?: number
}

export interface AgentEvalEvidence {
  source?: string
  chunk_id?: number | string
  title?: string
  url?: string
  score?: number
  text?: string
}

export interface AgentEvalRow {
  case_id: string
  question: string
  category: string
  doc?: string
  expected_outcome?: string
  rag_outcome?: string
  reply?: string
  judge?: AgentEvalJudge
  recall?: AgentEvalRecall
  refusal_correct?: boolean | null
  outcome_correct?: boolean | null
  evidence?: AgentEvalEvidence[]
  errors?: string[]
  judge_error?: string
  elapsed_ms?: number
}

export interface AgentEvalSummary {
  runs?: number
  judge_ok?: number
  judge_errors?: number
  judge_calls?: number
  judge_unpriced?: number
  groundedness_mean?: number | null
  groundedness_pass_rate?: number | null
  answer_relevance_mean?: number | null
  refusal_accuracy?: number | null
  outcome_accuracy?: number | null
  recall_mean?: number | null
  mrr_mean?: number | null
  recall_cases?: number
  empty_claims_rate?: number | null
  total_cost?: number
  judge_cost?: number
  total_elapsed_ms?: number
  by_category?: Record<string, { runs: number; groundedness_mean: number }>
}

export interface AgentEvalResult {
  name: string
  kind: string
  rows: AgentEvalRow[]
  summary: AgentEvalSummary | null
  report: string
  reportContent: string
}

export interface AgentEvalJobParams {
  caseIds: string[]
  limit: number
  runs: number
  web: boolean
  keep: boolean
  caseCount: number
  judgeModel: string
}

export interface AgentEvalJobResult {
  jsonl: string
  report: string
  summary: AgentEvalSummary | null
}

export interface AgentEvalJob {
  id: string
  status: string
  createdAt: number
  startedAt: number | null
  finishedAt: number | null
  elapsedMs: number | null
  params: AgentEvalJobParams
  total: number
  done: number
  currentCase: string
  percent: number
  result: AgentEvalJobResult | null
  error: string
  warnings: string[]
}

export interface AgentEvalJobList {
  items: AgentEvalJob[]
}

export interface AgentEvalRunPayload {
  cases: string[]
  limit: number
  runs: number
  web: boolean
  keep: boolean
}

export function fetchAgentEvalCases(): Promise<AgentEvalCases> {
  return get<AgentEvalCases>('/admin/agent-eval/cases', undefined, 30000)
}

export function fetchAgentEvalResults(limit = 50): Promise<AgentEvalResultList> {
  return get<AgentEvalResultList>('/admin/agent-eval/results', { limit }, 60000)
}

export function fetchAgentEvalResult(name: string): Promise<AgentEvalResult> {
  return get<AgentEvalResult>(
    `/admin/agent-eval/results/${encodeURIComponent(name)}`,
    undefined,
    60000,
  )
}

export function startAgentEval(payload: AgentEvalRunPayload): Promise<AgentEvalJob> {
  return post<AgentEvalJob>('/admin/agent-eval/runs', payload)
}

export function fetchAgentEvalJobs(limit = 10): Promise<AgentEvalJobList> {
  return get<AgentEvalJobList>('/admin/agent-eval/jobs', { limit }, 30000)
}

export function fetchAgentEvalJob(id: string): Promise<AgentEvalJob> {
  return get<AgentEvalJob>(
    `/admin/agent-eval/jobs/${encodeURIComponent(id)}`,
    undefined,
    30000,
  )
}

export function stopAgentEvalJob(id: string): Promise<AgentEvalJob> {
  return post<AgentEvalJob>(
    `/admin/agent-eval/jobs/${encodeURIComponent(id)}/stop`,
  )
}
