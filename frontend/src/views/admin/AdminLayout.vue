<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Goods, Menu as MenuIcon, Tickets } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const MENU = [
  { path: '/admin/categories', label: '分类管理', icon: MenuIcon },
  { path: '/admin/products', label: '商品管理', icon: Goods },
  { path: '/admin/orders', label: '订单管理', icon: Tickets },
]

/** 子路由激活时高亮对应菜单；/admin 本身由路由 redirect 兜住 */
const activeMenu = computed(() => {
  const hit = MENU.find((item) => route.path.startsWith(item.path))
  return hit?.path ?? '/admin/categories'
})

function logout() {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <el-container class="admin-shell">
    <el-aside width="220px" class="admin-aside">
      <div class="brand">一次买够 · 管理后台</div>
      <el-menu :default-active="activeMenu" router class="admin-menu">
        <el-menu-item v-for="item in MENU" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <span class="title">管理后台</span>
        <div class="right">
          <RouterLink to="/" class="store-link">返回商城</RouterLink>
          <span class="who">{{ userStore.displayName }}</span>
          <el-button link type="primary" @click="logout">退出登录</el-button>
        </div>
      </el-header>

      <el-main class="admin-content">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-shell {
  min-height: 100vh;
  background: #f5f7fa;
}

.admin-aside {
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

.admin-menu {
  border-right: none;
}

.admin-header {
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

.admin-content {
  padding: 20px;
}
</style>
