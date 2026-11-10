import { del, get, postForm } from './http'

export interface KnowledgeDocument {
  fileTitle: string
  itemName: string
  chunkCount: number
  importedAt: string
  status: string
  kgEntities: number
}

export interface KnowledgeDuplicate {
  filename: string
  fileTitle: string
  itemName: string
  chunkCount: number
  status: string
  importedAt: string
}

export interface KnowledgeFailed {
  filename: string
  reason: string
}

export interface KnowledgeUploadResult {
  message: string
  taskIds: string[]
  duplicates: KnowledgeDuplicate[]
  failed: KnowledgeFailed[]
}

export interface KnowledgeTaskStatus {
  taskId: string
  status: string
  doneList: string[]
  runningList: string[]
}

export interface KnowledgeDocumentList {
  total: number
  documents: KnowledgeDocument[]
}

export interface PlatformPolicySummary {
  policyKey: string
  title: string
  category: string
  version: string
  sectionCount: number
  updatedAt: string
}

export interface PlatformPolicySection {
  sectionNo: number
  heading: string
  content: string
}

export interface PlatformPolicyDetail {
  policyKey: string
  title: string
  category: string
  version: string
  effectiveAt: string
  updatedAt: string
  sections: PlatformPolicySection[]
}

/** 单文件上传；导入图在 agent 后台跑，请求返回的是 taskId */
export function uploadKnowledgeFile(
  file: File,
  force: boolean,
): Promise<KnowledgeUploadResult> {
  const form = new FormData()
  form.append('files', file)
  form.append('force', String(force))
  return postForm<KnowledgeUploadResult>('/admin/knowledge/upload', form, 300000)
}

/** 平台政策只能上传 Markdown；policyKey 为空时后端按文件名推导 */
export function uploadPlatformPolicy(
  file: File,
  policyKey: string,
): Promise<KnowledgeUploadResult> {
  const form = new FormData()
  form.append('files', file)
  form.append('force', 'true')
  form.append('knowledgeBase', 'policy')
  form.append('policyKey', policyKey)
  return postForm<KnowledgeUploadResult>('/admin/knowledge/upload', form, 300000)
}

export function fetchKnowledgeDocuments(): Promise<KnowledgeDocumentList> {
  return get<KnowledgeDocumentList>('/admin/knowledge/documents', undefined, 60000)
}

export function fetchKnowledgeTask(taskId: string): Promise<KnowledgeTaskStatus> {
  return get<KnowledgeTaskStatus>(
    `/admin/knowledge/tasks/${encodeURIComponent(taskId)}`,
  )
}

export function revokeKnowledgeDocument(fileTitle: string): Promise<void> {
  return del<void>(
    `/admin/knowledge/documents/${encodeURIComponent(fileTitle)}`,
    120000,
  )
}

/** 平台政策：政策原文以 MySQL 为准，管理端只读展示章节。 */
export function fetchPlatformPolicies(): Promise<PlatformPolicySummary[]> {
  return get<PlatformPolicySummary[]>('/policies')
}

export function fetchPlatformPolicy(policyKey: string): Promise<PlatformPolicyDetail> {
  return get<PlatformPolicyDetail>(`/policies/${encodeURIComponent(policyKey)}`)
}
