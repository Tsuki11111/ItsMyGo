import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getStoredUser, getToken } from '@/api/http'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/ProductList.vue') },
    { path: '/product/:id', name: 'product-detail', component: () => import('@/views/ProductDetail.vue') },
    { path: '/shop/:id', name: 'shop', component: () => import('@/views/Shop.vue') },
    { path: '/login', name: 'login', component: () => import('@/views/Login.vue') },
    { path: '/cart', name: 'cart', component: () => import('@/views/Cart.vue') },
    { path: '/checkout', name: 'checkout', component: () => import('@/views/Checkout.vue') },
    {
      path: '/checkout/result',
      name: 'checkout-result',
      component: () => import('@/views/CheckoutResult.vue'),
      meta: { requiresAuth: true },
    },
    { path: '/order/:id', name: 'order-detail', component: () => import('@/views/OrderDetail.vue') },
    { path: '/coupons', name: 'coupon-center', component: () => import('@/views/CouponCenter.vue') },
    {
      path: '/merchant/apply',
      name: 'merchant-apply',
      component: () => import('@/views/merchant/Apply.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/merchant',
      component: () => import('@/views/merchant/MerchantLayout.vue'),
      redirect: '/merchant/profile',
      meta: { requiresMerchant: true },
      children: [
        { path: 'products', name: 'merchant-products', component: () => import('@/views/merchant/Products.vue') },
        { path: 'orders', name: 'merchant-orders', component: () => import('@/views/merchant/Orders.vue') },
        { path: 'coupons', name: 'merchant-coupons', component: () => import('@/views/merchant/Coupons.vue') },
        { path: 'reviews', name: 'merchant-reviews', component: () => import('@/views/merchant/Reviews.vue') },
        { path: 'messages/:conversationId?', name: 'merchant-messages', component: () => import('@/views/merchant/Messages.vue') },
        { path: 'profile', name: 'merchant-profile', component: () => import('@/views/merchant/Profile.vue') },
      ],
    },
    {
      path: '/user',
      component: () => import('@/views/user/UserCenter.vue'),
      redirect: '/user/orders',
      meta: { requiresAuth: true },
      children: [
        { path: 'orders', name: 'user-orders', component: () => import('@/views/user/Orders.vue') },
        { path: 'favorites', name: 'user-favorites', component: () => import('@/views/user/Favorites.vue') },
        { path: 'footprints', name: 'user-footprints', component: () => import('@/views/user/Footprints.vue') },
        { path: 'coupons', name: 'user-coupons', component: () => import('@/views/user/Coupons.vue') },
        { path: 'reviews', name: 'user-reviews', component: () => import('@/views/user/Reviews.vue') },
        { path: 'messages/:conversationId?', name: 'user-messages', component: () => import('@/views/user/Messages.vue') },
        { path: 'addresses', name: 'user-addresses', component: () => import('@/views/user/Addresses.vue') },
        { path: 'profile', name: 'user-profile', component: () => import('@/views/user/Profile.vue') },
        { path: 'password', name: 'user-password', component: () => import('@/views/user/Password.vue') },
      ],
    },
    {
      path: '/admin',
      component: () => import('@/views/admin/AdminLayout.vue'),
      redirect: '/admin/dashboard',
      meta: { requiresAdmin: true },
      children: [
        { path: 'dashboard', name: 'admin-dashboard', component: () => import('@/views/admin/Dashboard.vue') },
        { path: 'categories', name: 'admin-categories', component: () => import('@/views/admin/Categories.vue') },
        { path: 'messages/:conversationId?', name: 'admin-messages', component: () => import('@/views/admin/Messages.vue') },
        { path: 'products', name: 'admin-products', component: () => import('@/views/admin/Products.vue') },
        { path: 'orders', name: 'admin-orders', component: () => import('@/views/admin/Orders.vue') },
        { path: 'coupons', name: 'admin-coupons', component: () => import('@/views/admin/Coupons.vue') },
        { path: 'reviews', name: 'admin-reviews', component: () => import('@/views/admin/Reviews.vue') },
        { path: 'merchants', name: 'admin-merchants', component: () => import('@/views/admin/Merchants.vue') },
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
  const requiresAdmin = to.matched.some((record) => record.meta.requiresAdmin)
  const requiresMerchant = to.matched.some((record) => record.meta.requiresMerchant)
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)

  if ((requiresAuth || requiresAdmin || requiresMerchant) && !getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  const stored = getStoredUser()
  if (requiresAdmin && stored?.role !== 'ADMIN') {
    ElMessage.error('需要管理员权限')
    return { path: '/' }
  }

  if (requiresMerchant
      && (stored?.role !== 'MERCHANT' || stored.merchantStatus !== 'ACTIVE')) {
    ElMessage.error('需要商家权限')
    return { path: '/' }
  }

  return true
})

export default router
