import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/api/http'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/ProductList.vue') },
    { path: '/product/:id', name: 'product-detail', component: () => import('@/views/ProductDetail.vue') },
    { path: '/login', name: 'login', component: () => import('@/views/Login.vue') },
    { path: '/cart', name: 'cart', component: () => import('@/views/Cart.vue') },
    { path: '/checkout', name: 'checkout', component: () => import('@/views/Checkout.vue') },
    { path: '/order/:id', name: 'order-detail', component: () => import('@/views/OrderDetail.vue') },
    {
      path: '/user',
      component: () => import('@/views/user/UserCenter.vue'),
      redirect: '/user/orders',
      meta: { requiresAuth: true },
      children: [
        { path: 'orders', name: 'user-orders', component: () => import('@/views/user/Orders.vue') },
        { path: 'addresses', name: 'user-addresses', component: () => import('@/views/user/Addresses.vue') },
        { path: 'profile', name: 'user-profile', component: () => import('@/views/user/Profile.vue') },
        { path: 'password', name: 'user-password', component: () => import('@/views/user/Password.vue') },
      ],
    },
  ],
})

/**
 * 需要登录的页面统一在这里拦。
 * 直接读 localStorage 而不是 Pinia —— 守卫可能在应用挂载前触发，
 * 那时 store 还没装好。
 */
router.beforeEach((to) => {
  if (to.matched.some((record) => record.meta.requiresAuth) && !getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
