<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Location, Lock, Setting, Star, SwitchButton, Ticket, Tickets, User } from '@element-plus/icons-vue'
import ProductImage from '@/components/ProductImage.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 管理端有自己的外壳（AdminLayout），不共用顾客顶栏和内容宽度 */
const isAdminRoute = computed(() => route.path === '/admin' || route.path.startsWith('/admin/'))

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
</script>

<template>
  <header v-if="!isAdminRoute" class="topbar">
    <div class="topbar-inner">
      <RouterLink to="/" class="logo">一次买够</RouterLink>
      <div class="search">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索打印机、路由器、笔记本…"
          clearable
          @keyup.enter="submitSearch"
          @clear="submitSearch"
        />
      </div>
      <nav class="actions">
        <RouterLink to="/cart" class="link">购物车</RouterLink>

        <template v-if="userStore.isLoggedIn">
          <!-- 用户区：鼠标移上去向下滑出功能面板 -->
          <div class="user-menu" @mouseenter="panelOpen = true" @mouseleave="panelOpen = false">
            <RouterLink to="/user/orders" class="user-chip">
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
                <RouterLink to="/user/orders" class="panel-item" @click="panelOpen = false">
                  <el-icon><Tickets /></el-icon>我的订单
                </RouterLink>
                <RouterLink to="/user/favorites" class="panel-item" @click="panelOpen = false">
                  <el-icon><Star /></el-icon>我的收藏
                </RouterLink>
                <RouterLink to="/user/coupons" class="panel-item" @click="panelOpen = false">
                  <el-icon><Ticket /></el-icon>我的优惠券
                </RouterLink>
                <RouterLink to="/coupons" class="panel-item" @click="panelOpen = false">
                  <el-icon><Ticket /></el-icon>领券中心
                </RouterLink>
                <RouterLink to="/user/addresses" class="panel-item" @click="panelOpen = false">
                  <el-icon><Location /></el-icon>收货地址
                </RouterLink>
                <RouterLink to="/user/profile" class="panel-item" @click="panelOpen = false">
                  <el-icon><User /></el-icon>个人资料
                </RouterLink>
                <RouterLink to="/user/password" class="panel-item" @click="panelOpen = false">
                  <el-icon><Lock /></el-icon>修改密码
                </RouterLink>

                <template v-if="userStore.isAdmin">
                  <RouterLink to="/admin" class="panel-item" @click="panelOpen = false">
                    <el-icon><Setting /></el-icon>管理后台
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

        <RouterLink v-else to="/login" class="link">登录 / 注册</RouterLink>
      </nav>
    </div>
  </header>

  <main :class="isAdminRoute ? 'admin-main' : 'page'">
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
  max-width: 1440px;
  margin: 0 auto;
  padding: 14px 24px;
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
  max-width: 460px;
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
@media (max-width: 720px) {
  .who {
    display: none;
  }
}

.page {
  max-width: 1440px;
  margin: 0 auto;
  padding: 24px;
}

/* 管理端自带侧边栏和页头，不再套顾客端的宽度与留白 */
.admin-main {
  min-height: 100vh;
}
</style>
