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
