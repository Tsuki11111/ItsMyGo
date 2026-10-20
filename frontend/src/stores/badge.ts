import { ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchCouponCenter } from '@/api/coupon'
import { fetchCart } from '@/api/cart'
import { getToken } from '@/api/http'
import { useUserStore } from '@/stores/user'
import type { CartView, CouponView } from '@/api/types'

/**
 * 顶栏角标：可领券数量 + 购物车商品总数。
 *
 * 消息角标由 SSE 实时维护（stores/chat.ts），这里只负责另外两个；
 * 每 30 秒轮询一次，并在加购/改购物车/领券后立即刷新。
 */
export const useBadgeStore = defineStore('badge', () => {
  const claimableCoupons = ref(0)
  const cartCount = ref(0)

  let timer: number | undefined

  async function refreshCoupons() {
    try {
      syncCoupons(await fetchCouponCenter())
    } catch {
      // 拦截器已提示；保留上一次的值
    }
  }

  /** 领券中心拿到的列表直接复用，省一次请求 */
  function syncCoupons(coupons: CouponView[]) {
    claimableCoupons.value = coupons.filter((item) => !item.claimed).length
  }

  async function refreshCart() {
    const user = useUserStore()
    if (!getToken() || !user.isLoggedIn) {
      cartCount.value = 0
      return
    }
    try {
      syncCart(await fetchCart())
    } catch {
      cartCount.value = 0
    }
  }

  /** 购物车接口本来就返回整份购物车，直接同步，省一次请求 */
  function syncCart(view: CartView) {
    cartCount.value = view.items.reduce((sum, item) => sum + item.quantity, 0)
  }

  function resetAuth() {
    cartCount.value = 0
    // 登出后 claimed 标记消失，可领数量会变回「全部」
    void refreshCoupons()
  }

  async function refreshAll() {
    await Promise.all([refreshCoupons(), refreshCart()])
  }

  function startPolling() {
    stopPolling()
    timer = window.setInterval(() => {
      // 后台标签页不轮询，回到前台后下一轮自然刷新
      if (document.visibilityState === 'visible') {
        void refreshAll()
      }
    }, 30_000)
  }

  function stopPolling() {
    if (timer !== undefined) {
      window.clearInterval(timer)
      timer = undefined
    }
  }

  return {
    claimableCoupons,
    cartCount,
    refreshCoupons,
    refreshCart,
    syncCoupons,
    syncCart,
    resetAuth,
    refreshAll,
    startPolling,
    stopPolling,
  }
})
