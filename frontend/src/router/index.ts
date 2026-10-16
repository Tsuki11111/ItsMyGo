import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/ProductList.vue') },
    { path: '/product/:id', name: 'product-detail', component: () => import('@/views/ProductDetail.vue') },
    { path: '/login', name: 'login', component: () => import('@/views/Login.vue') },
    { path: '/checkout', name: 'checkout', component: () => import('@/views/Checkout.vue') },
    { path: '/order/:id', name: 'order-detail', component: () => import('@/views/OrderDetail.vue') },
  ],
})

export default router
