<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, ChatLineRound, Discount, Goods, Shop, Tickets } from '@element-plus/icons-vue'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()

const MENU = [
  { path: '/merchant/messages', label: '消息', icon: ChatLineRound },
  { path: '/merchant/products', label: '商品管理', icon: Goods },
  { path: '/merchant/orders', label: '订单管理', icon: Tickets },
  { path: '/merchant/coupons', label: '优惠券', icon: Discount },
  { path: '/merchant/reviews', label: '评价管理', icon: ChatDotRound },
  { path: '/merchant/profile', label: '店铺资料', icon: Shop },
]

const activeMenu = computed(() => {
  const hit = MENU.find((item) => route.path.startsWith(item.path))
  return hit?.path ?? '/merchant/profile'
})

function logout() {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <el-container class="merchant-shell">
    <el-aside width="220px" class="merchant-aside">
      <div class="brand">一次买够 · 商家后台</div>
      <el-menu :default-active="activeMenu" router class="merchant-menu">
        <el-menu-item v-for="item in MENU" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
          <span
            v-if="item.path === '/merchant/messages' && chatStore.merchantUnread > 0"
            class="menu-unread"
          >
            {{ chatStore.merchantUnread > 99 ? '99+' : chatStore.merchantUnread }}
          </span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="merchant-header">
        <span class="title">{{ userStore.user?.merchantName || '商家后台' }}</span>
        <div class="right">
          <RouterLink to="/" class="store-link">返回商城</RouterLink>
          <span class="who">{{ userStore.displayName }}</span>
          <el-button link type="primary" @click="logout">退出登录</el-button>
        </div>
      </el-header>

      <el-main class="merchant-content">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.merchant-shell {
  min-height: 100vh;
  background: #f5f7fa;
}

.merchant-aside {
  background: #fff;
  border-right: 1px solid var(--el-border-color-lighter);
}

.brand {
  height: 60px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  font-size: 16px;
  font-weight: 700;
  color: var(--c-pink);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.merchant-menu {
  border-right: none;
}

.menu-unread {
  margin-left: auto;
  min-width: 17px;
  height: 17px;
  padding: 0 5px;
  font-size: 10px;
  line-height: 17px;
  text-align: center;
  color: #fff;
  background: var(--c-pink);
  border-radius: 9px;
}

.merchant-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.store-link {
  font-size: 13px;
  color: var(--el-color-primary);
  text-decoration: none;
}

.who {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.merchant-content {
  padding: 20px;
}
</style>
