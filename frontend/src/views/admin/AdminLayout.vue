<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Aim, ChatDotRound, ChatLineRound, Collection, DataAnalysis, DataBoard, Discount, Document, Goods, Key, Menu as MenuIcon, RefreshLeft, Shop, Tickets, User } from '@element-plus/icons-vue'
import NotificationBell from '@/components/NotificationBell.vue'
import { useChatStore } from '@/stores/chat'
import { usePermissionStore } from '@/stores/permission'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()
const permissionStore = usePermissionStore()

const MENU = [
  { path: '/admin/dashboard', label: '数据概览', icon: DataBoard, perm: 'admin:dashboard:view' },
  { path: '/admin/users', label: '用户管理', icon: User, perm: 'admin:user:view' },
  { path: '/admin/messages', label: '平台消息', icon: ChatLineRound, perm: 'admin:chat:view' },
  { path: '/admin/categories', label: '分类管理', icon: MenuIcon, perm: 'admin:product:view' },
  { path: '/admin/merchants', label: '商户管理', icon: Shop, perm: 'admin:merchant:view' },
  { path: '/admin/products', label: '商品管理', icon: Goods, perm: 'admin:product:view' },
  { path: '/admin/orders', label: '订单管理', icon: Tickets, perm: 'admin:order:view' },
  { path: '/admin/after-sales', label: '售后管理', icon: RefreshLeft, perm: 'admin:aftersale:view' },
  { path: '/admin/coupons', label: '优惠券管理', icon: Discount, perm: 'admin:coupon:manage' },
  { path: '/admin/reviews', label: '评价管理', icon: ChatDotRound, perm: 'admin:review:manage' },
  { path: '/admin/knowledge', label: '知识库', icon: Collection, perm: 'admin:knowledge:import' },
  { path: '/admin/knowledge-gaps', label: '知识缺口', icon: Aim, perm: 'admin:knowledge-gap:manage' },
  { path: '/admin/agent-eval', label: '智能体评测', icon: DataAnalysis, perm: 'admin:agent-eval:view' },
  { path: '/admin/invitations', label: '管理员邀请', icon: Key, perm: 'admin:admin:view' },
  { path: '/admin/audit-logs', label: '操作审计', icon: Document, perm: 'admin:audit:view' },
]

/** 按权限码过滤菜单；权限码只影响显隐，后端仍会 403 兜底 */
const visibleMenu = computed(() =>
  MENU.filter((item) => !item.perm || permissionStore.has(item.perm)),
)

/** 子路由激活时高亮对应菜单；/admin 本身由路由 redirect 兜住。
 *  取「匹配到的最长前缀」—— /admin/knowledge-gaps 也以 /admin/knowledge 开头，
 *  不按长度取就会把知识缺口高亮成知识库。 */
const activeMenu = computed(() => {
  const hits = MENU.filter((item) => route.path.startsWith(item.path))
  hits.sort((a, b) => b.path.length - a.path.length)
  return hits[0]?.path ?? '/admin/dashboard'
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
        <el-menu-item v-for="item in visibleMenu" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
          <span
            v-if="item.path === '/admin/messages' && chatStore.merchantUnread > 0"
            class="menu-unread"
          >
            {{ chatStore.merchantUnread > 99 ? '99+' : chatStore.merchantUnread }}
          </span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <span class="title">管理后台</span>
        <div class="right">
          <NotificationBell />
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
