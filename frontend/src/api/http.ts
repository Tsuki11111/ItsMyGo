import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from './types'

const TOKEN_KEY = 'its-my-go:token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
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

export function put<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ url, method: 'PUT', data })
}

export function del<T>(url: string): Promise<T> {
  return request<T>({ url, method: 'DELETE' })
}
