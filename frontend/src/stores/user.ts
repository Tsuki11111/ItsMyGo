import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  UNAUTHORIZED_EVENT,
  clearAuth,
  getStoredUser,
  getToken,
  setStoredUser,
  setToken,
  type StoredUser,
} from '@/api/http'
import type { AuthResult } from '@/api/types'

/**
 * 登录态。
 *
 * 凭据（token + 用户信息）的真身在 localStorage，由 `api/http.ts` 负责存取 ——
 * 这里只是它的响应式包装，顺带把「登录 / 退出」这两个动作收在一处。
 * 刷新页面时直接从 localStorage 恢复，不用再打一次 /auth/me。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(getToken())
  const user = ref<StoredUser | null>(getStoredUser())

  const isLoggedIn = computed(() => Boolean(token.value && user.value))
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  /** 商家后台权限：角色是 MERCHANT 且店铺处于营业状态 */
  const isMerchant = computed(() =>
    user.value?.role === 'MERCHANT' && user.value?.merchantStatus === 'ACTIVE',
  )
  /** 是否已经绑定过店铺（含审核中/被驳回/被禁用） */
  const hasMerchant = computed(() => user.value?.merchantId != null)
  /** 顶栏显示用：优先昵称，没填就退回用户名 */
  const displayName = computed(() => user.value?.nickname || user.value?.username || '')

  function save(result: AuthResult) {
    const identity: StoredUser = {
      userId: result.userId,
      username: result.username,
      nickname: result.nickname,
      avatar: result.avatar,
      role: result.role,
      merchantId: result.merchantId ?? null,
      merchantName: result.merchantName ?? '',
      merchantStatus: result.merchantStatus ?? '',
    }
    token.value = result.token
    user.value = identity
    setToken(result.token)
    setStoredUser(identity)
  }

  /**
   * 资料改动后同步内存与 localStorage。
   * 只动传进来的字段 —— 改昵称不应该顺手把头像清了。
   */
  function patchProfile(patch: Partial<Pick<StoredUser, 'nickname' | 'avatar'>>) {
    if (!user.value) return
    const next: StoredUser = { ...user.value, ...patch }
    user.value = next
    setStoredUser(next)
  }

  function logout() {
    token.value = null
    user.value = null
    clearAuth()
  }

  // token 在后端失效时（http.ts 收到 401）跟着一起登出
  window.addEventListener(UNAUTHORIZED_EVENT, logout)

  return { token, user, isLoggedIn, isAdmin, isMerchant, hasMerchant, displayName, save, patchProfile, logout }
})
