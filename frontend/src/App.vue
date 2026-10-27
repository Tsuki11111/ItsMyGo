<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Location, Lock, Search, Setting, Shop, ShoppingCart, Star, SwitchButton, Ticket, Tickets, User } from '@element-plus/icons-vue'
import ProductImage from '@/components/ProductImage.vue'
import { useBadgeStore } from '@/stores/badge'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()
const badgeStore = useBadgeStore()

/** 管理端 / 商家端有自己的外壳，不共用顾客顶栏和内容宽度 */
const isBareRoute = computed(() => {
  const path = route.path
  return path === '/admin' || path.startsWith('/admin/')
    || path === '/merchant' || path.startsWith('/merchant/')
})

/** 鼠标移到用户区时展开的抽屉面板 */
const panelOpen = ref(false)

const searchKeyword = ref('')

/**
 * 搜索框跟 URL 走：从品牌专区点进来时，框里也要显示正在搜什么。
 * 搜索词本身存在 URL 的 query 里（和分类一样），组件不另存一份状态。
 */
watch(
  () => route.query.keyword,
  (value) => {
    searchKeyword.value = typeof value === 'string' ? value : ''
  },
  { immediate: true },
)

function submitSearch() {
  const keyword = searchKeyword.value.trim()
  router.push({ path: '/', query: keyword ? { keyword } : {} })
}

function onLogout() {
  userStore.logout()
  ElMessage.success('已退出登录')
}

// 登录后建立 SSE；登出时断开。管理员也要连：平台自营店的消息由管理员接收。
watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      void chatStore.connect()
      void badgeStore.refreshAll()
    } else {
      chatStore.disconnect()
      badgeStore.resetAuth()
    }
  },
  { immediate: true },
)

onMounted(() => badgeStore.startPolling())
onBeforeUnmount(() => badgeStore.stopPolling())
</script>

<template>
  <header v-if="!isBareRoute" class="topbar">
    <div class="topbar-inner">
      <RouterLink to="/" class="logo">一次买够</RouterLink>
      <div class="search">
        <div class="search-shell">
          <el-input
            v-model="searchKeyword"
            class="search-input"
            placeholder="搜索打印机、路由器、笔记本…"
            clearable
            @keyup.enter="submitSearch"
            @clear="submitSearch"
          />
          <button type="button" class="search-btn" @click="submitSearch">
            <el-icon><Search /></el-icon>
            <span>搜索</span>
          </button>
        </div>
      </div>
      <nav class="actions">
        <RouterLink
          v-if="userStore.isLoggedIn"
          :to="userStore.isAdmin ? '/admin/messages' : '/user/messages'"
          class="link badge-link"
          target="_blank"
          rel="noopener"
        >
          <el-icon><ChatDotRound /></el-icon>
          <el-badge
            :value="chatStore.unreadTotal"
            :max="99"
            :hidden="chatStore.unreadTotal === 0"
          >
            <span>消息</span>
          </el-badge>
        </RouterLink>
        <RouterLink
          to="/coupons"
          class="link badge-link"
          target="_blank"
          rel="noopener"
        >
          <el-icon><Ticket /></el-icon>
          <el-badge
            :value="badgeStore.claimableCoupons"
            :max="99"
            :hidden="badgeStore.claimableCoupons === 0"
          >
            <span>领券中心</span>
          </el-badge>
        </RouterLink>
        <RouterLink
          to="/cart"
          class="link badge-link"
          target="_blank"
          rel="noopener"
        >
          <el-icon><ShoppingCart /></el-icon>
          <el-badge
            :value="badgeStore.cartCount"
            :max="99"
            :hidden="badgeStore.cartCount === 0"
          >
            <span>购物车</span>
          </el-badge>
        </RouterLink>

        <template v-if="userStore.isLoggedIn">
          <!-- 用户区：鼠标移上去向下滑出功能面板 -->
          <div class="user-menu" @mouseenter="panelOpen = true" @mouseleave="panelOpen = false">
            <RouterLink
              to="/user/orders"
              class="user-chip"
              target="_blank"
              rel="noopener"
            >
              <span class="chip-avatar">
                <ProductImage
                  :src="userStore.user?.avatar"
                  :name="userStore.displayName"
                  :id="userStore.user?.userId ?? 0"
                />
              </span>
              <span class="who">{{ userStore.displayName }}</span>
            </RouterLink>

            <div class="user-panel" :class="{ open: panelOpen }">
              <div class="panel-card">
                <RouterLink
                  to="/user/orders"
                  class="panel-item"
                  target="_blank"
                  rel="noopener"
                  @click="panelOpen = false"
                >
                  <el-icon><Tickets /></el-icon>我的订单
                </RouterLink>
                <RouterLink
                  :to="userStore.isAdmin ? '/admin/messages' : '/user/messages'"
                  class="panel-item"
                  target="_blank"
                  rel="noopener"
                  @click="panelOpen = false"
                >
                  <el-icon><ChatDotRound /></el-icon>
                  {{ userStore.isAdmin ? '平台消息' : '我的消息' }}
                </RouterLink>
                <RouterLink
                  to="/user/favorites"
                  class="panel-item"
                  target="_blank"
                  rel="noopener"
                  @click="panelOpen = false"
                >
                  <el-icon><Star /></el-icon>我的收藏
                </RouterLink>
                <RouterLink
                  to="/user/coupons"
                  class="panel-item"
                  target="_blank"
                  rel="noopener"
                  @click="panelOpen = false"
                >
                  <el-icon><Ticket /></el-icon>我的优惠券
                </RouterLink>
                <RouterLink
                  to="/user/addresses"
                  class="panel-item"
                  target="_blank"
                  rel="noopener"
                  @click="panelOpen = false"
                >
                  <el-icon><Location /></el-icon>收货地址
                </RouterLink>
                <RouterLink
                  to="/user/profile"
                  class="panel-item"
                  target="_blank"
                  rel="noopener"
                  @click="panelOpen = false"
                >
                  <el-icon><User /></el-icon>个人资料
                </RouterLink>
                <RouterLink
                  to="/user/password"
                  class="panel-item"
                  target="_blank"
                  rel="noopener"
                  @click="panelOpen = false"
                >
                  <el-icon><Lock /></el-icon>修改密码
                </RouterLink>

                <template v-if="userStore.isAdmin">
                  <RouterLink
                    to="/admin"
                    class="panel-item"
                    target="_blank"
                    rel="noopener"
                    @click="panelOpen = false"
                  >
                    <el-icon><Setting /></el-icon>管理后台
                  </RouterLink>
                </template>
                <template v-else-if="userStore.isMerchant">
                  <RouterLink
                    to="/merchant/profile"
                    class="panel-item"
                    target="_blank"
                    rel="noopener"
                    @click="panelOpen = false"
                  >
                    <el-icon><Shop /></el-icon>商家后台
                  </RouterLink>
                </template>
                <template v-else>
                  <RouterLink
                    to="/merchant/apply"
                    class="panel-item"
                    target="_blank"
                    rel="noopener"
                    @click="panelOpen = false"
                  >
                    <el-icon><Shop /></el-icon>商家入驻
                  </RouterLink>
                </template>

                <div class="panel-divider"></div>

                <button type="button" class="panel-item" @click="onLogout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </button>
              </div>
            </div>
          </div>
        </template>

        <RouterLink v-else to="/login" class="link" target="_blank" rel="noopener">
          登录 / 注册
        </RouterLink>
      </nav>
    </div>
  </header>

  <main :class="isBareRoute ? 'admin-main' : 'page'">
    <RouterView />
  </main>
</template>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #fff;
  border-bottom: 1px solid var(--c-border);
}

.topbar-inner {
  /* 和 .page 保持同一个宽度，否则顶栏和内容会错开 */
  max-width: 1680px;
  margin: 0 auto;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 32px;
}

.logo {
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--c-pink);
  text-decoration: none;
  white-space: nowrap;
}

.search {
  flex: 1;
  max-width: 520px;
}

.search-shell {
  display: flex;
  align-items: stretch;
  height: 36px;
  overflow: hidden;
  background: var(--c-card);
  border: 1px solid var(--c-border);
  border-radius: 999px;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.search-shell:focus-within {
  border-color: var(--c-pink);
  box-shadow: 0 0 0 2px rgba(232, 99, 143, 0.12);
}

.search-input {
  flex: 1;
  min-width: 0;
}

.search-input :deep(.el-input__wrapper) {
  height: 100%;
  padding-left: 14px;
  background: transparent;
  border-radius: 0;
  box-shadow: none !important;
}

.search-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: none;
  padding: 0 17px;
  color: #fff;
  background: var(--c-pink);
  border: 0;
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
}

.search-btn:hover {
  background: var(--c-pink-hover);
}

.search-btn .el-icon {
  font-size: 15px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-left: auto;
}

.link {
  font-size: 14px;
  color: var(--c-text-2);
  text-decoration: none;
  transition: color 0.15s;
}

.link:hover {
  color: var(--c-pink);
}

.badge-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: none;
  white-space: nowrap;
}

.badge-link :deep(.el-icon) {
  font-size: 15px;
}

.badge-link :deep(.el-badge__content) {
  font-size: 10px;
}

/* ── 用户区：悬停展开的抽屉面板 ── */
.user-menu {
  position: relative;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
}

.chip-avatar {
  width: 28px;
  height: 28px;
  flex: none;
  display: block;
  border-radius: 50%;
  overflow: hidden;
  background: var(--c-pink-tint);
  font-size: 12px;
}

.who {
  font-size: 14px;
  color: var(--c-text);
  text-decoration: none;
  transition: color 0.15s;
}

.user-chip:hover .who {
  color: var(--c-pink);
}

/*
 * padding-top 不是装饰：鼠标从头像移到面板要跨过这 8px，
 * 把它算进面板自己的盒子，移过去时就不会触发 mouseleave 把面板收掉。
 */
.user-panel {
  position: absolute;
  top: 100%;
  right: 0;
  padding-top: 8px;
  z-index: 20;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transform: translateY(-6px);
  transition: opacity 0.16s ease, transform 0.16s ease, visibility 0.16s;
}

.user-panel.open {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
  transform: translateY(0);
}

.panel-card {
  width: 168px;
  padding: 6px;
  background: #fff;
  border: 1px solid var(--c-border);
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(232, 99, 143, 0.18);
}

.panel-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 12px;
  font-size: 13px;
  font-family: inherit;
  text-align: left;
  color: var(--c-text-2);
  background: none;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  text-decoration: none;
  transition: background 0.14s, color 0.14s;
}

.panel-item:hover {
  background: var(--c-pink-tint);
  color: var(--c-pink);
}

.panel-divider {
  height: 1px;
  margin: 6px 4px;
  background: var(--c-border);
}

/* 窄屏只留头像，昵称收起 */
@media (max-width: 820px) {
  /* 窄屏把搜索框放到底下一整行，避免被右侧快捷入口挤成 0 宽 */
  .topbar-inner {
    flex-wrap: wrap;
    gap: 10px 14px;
  }

  .search {
    order: 3;
    flex-basis: 100%;
    max-width: none;
    min-width: 0;
  }
}

@media (max-width: 720px) {
  .who {
    display: none;
  }

  .search-btn {
    padding: 0 13px;
  }

  .search-btn span {
    display: none;
  }
}

.page {
  max-width: 1680px;
  margin: 0 auto;
  padding: 16px;
}

/* 管理端自带侧边栏和页头，不再套顾客端的宽度与留白 */
.admin-main {
  min-height: 100vh;
}
</style>
