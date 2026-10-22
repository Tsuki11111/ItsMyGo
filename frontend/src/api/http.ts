import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from './types'

const TOKEN_KEY = 'its-my-go:token'
const USER_KEY = 'its-my-go:user'

/** 登录态失效时广播，store 收到后同步清掉内存里的用户信息 */
export const UNAUTHORIZED_EVENT = 'its-my-go:unauthorized'

/** 存进 localStorage 的身份信息 —— 是后端 AuthResult 去掉 token 的部分 */
export interface StoredUser {
  userId: number
  username: string
  nickname: string
  avatar?: string
  role: string
  merchantId?: number | null
  merchantName?: string
  merchantStatus?: string
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function getStoredUser(): StoredUser | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as StoredUser
  } catch {
    // 存坏了就当没登录，别让整个应用起不来
    localStorage.removeItem(USER_KEY)
    return null
  }
}

export function setStoredUser(user: StoredUser): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

const instance = axios.create({
  // 走 Vite 的 proxy 转发到后端，开发时不需要后端配 CORS
  baseURL: '/api',
  timeout: 15000,
})

instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  (response) => response,
  (error) => {
    // ⚠️ 后端的 HTTP 状态码**不总是语义化的**：
    //    401（未登录）和 403（无权限）是真状态码 —— Spring Security 的 entry point 手动 setStatus 的；
    //    而 400（参数错）、404（不存在）、429（限流）只体现在 body 的 code 里，HTTP 仍然是 200。
    //    所以下面 success 分支按 body.code 判断，这里只兜「HTTP 层就失败」的情况。
    const body = error.response?.data as Result<unknown> | undefined

    if (error.response?.status === 401) {
      // token 过期或无效：直接清掉登录态，别让用户卡在"看起来已登录、但什么都做不了"
      clearAuth()
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
    }

    ElMessage.error(body?.message || '网络异常，请稍后重试')
    return Promise.reject(error)
  },
)

/** 发请求并**剥掉外层包装**，调用方直接拿 data */
async function request<T>(config: Parameters<typeof instance.request>[0]): Promise<T> {
  const response = await instance.request<Result<T>>(config)
  const result = response.data

  if (result.code !== 200) {
    // 业务失败（400 / 404 / 429 都走这里）
    ElMessage.error(result.message || '请求失败')
    throw new Error(result.message)
  }
  return result.data
}

export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return request<T>({ url, method: 'GET', params })
}

export function post<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ url, method: 'POST', data })
}

/**
 * multipart 上传。
 * **不要手写 Content-Type** —— 浏览器要为 FormData 生成带 boundary 的那个，
 * 手动设成 `multipart/form-data` 反而会把 boundary 丢掉，后端就解析不出文件。
 */
export function postForm<T>(url: string, data: FormData): Promise<T> {
  return request<T>({ url, method: 'POST', data })
}

export function put<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ url, method: 'PUT', data })
}

export function del<T>(url: string): Promise<T> {
  return request<T>({ url, method: 'DELETE' })
}

/** 下载文件（导出 CSV 等）：保留鉴权头，用 blob 触发浏览器保存。 */
export async function download(
  url: string,
  params: Record<string, unknown> | undefined,
  filename: string,
): Promise<void> {
  const response = await instance.get<Blob>(url, { params, responseType: 'blob' })
  const contentType = (response.headers['content-type'] as string | undefined)
    || 'application/octet-stream'
  const blob = new Blob([response.data], { type: contentType })
  const href = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = href
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(href), 0)
}
