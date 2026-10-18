<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import ProductImage from '@/components/ProductImage.vue'
import { fetchProfile } from '@/api/user'
import { useUserStore } from '@/stores/user'
import type { UserProfile } from '@/api/types'

const route = useRoute()
const userStore = useUserStore()

const profile = ref<UserProfile | null>(null)

const MENU = [
  { path: '/user/orders', label: '我的订单' },
  { path: '/user/favorites', label: '我的收藏' },
  { path: '/user/footprints', label: '浏览足迹' },
  { path: '/user/coupons', label: '我的优惠券' },
  { path: '/user/reviews', label: '我的评价' },
  { path: '/user/addresses', label: '收货地址' },
  { path: '/user/profile', label: '个人资料' },
  { path: '/user/password', label: '修改密码' },
]

onMounted(async () => {
  try {
    profile.value = await fetchProfile()
  } catch {
    // 登录失效等情况拦截器已提示，页面用 store 里的本地信息兜底
  }
})
</script>

<template>
  <div class="user-page">
    <aside class="side">
      <div class="me">
        <div class="me-avatar">
          <ProductImage
            :src="profile?.avatar"
            :name="profile?.nickname || userStore.displayName"
            :id="profile?.id ?? userStore.user?.userId ?? 0"
          />
        </div>
        <p class="me-name">{{ profile?.nickname || userStore.displayName }}</p>
        <p class="me-account">@{{ profile?.username }}</p>
      </div>

      <el-menu :default-active="route.path" router class="side-menu">
        <el-menu-item v-for="item in MENU" :key="item.path" :index="item.path">
          {{ item.label }}
        </el-menu-item>
      </el-menu>
    </aside>

    <section class="content">
      <RouterView />
    </section>
  </div>
</template>

<style scoped>
.user-page {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 18px;
  align-items: start;
}

/* ── 左侧 ── */
.side {
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.me {
  padding: 24px 16px 20px;
  text-align: center;
  border-bottom: 1px solid var(--c-border);
}

.me-avatar {
  width: 72px;
  height: 72px;
  margin: 0 auto 12px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--c-pink-tint);
  font-size: 28px;
}

.me-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text);
}

.me-account {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
}

/* el-menu 默认是直角 + 蓝灰色，这里压成和其余页面一致的风格 */
.side-menu {
  border-right: none;
  padding: 6px 0;
}

.side-menu :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  font-size: 14px;
  color: var(--c-text-2);
}

.side-menu :deep(.el-menu-item:hover) {
  background: var(--c-pink-tint);
  color: var(--c-pink);
}

/* 选中态是粉色允许出现的位置之一 */
.side-menu :deep(.el-menu-item.is-active) {
  background: var(--c-pink-soft);
  color: var(--c-pink);
  font-weight: 600;
}

/* ── 右侧 ── */
.content {
  min-width: 0;
}

@media (max-width: 820px) {
  .user-page {
    grid-template-columns: 1fr;
  }

  .side-menu {
    display: flex;
    overflow-x: auto;
    padding: 0;
  }

  .side-menu :deep(.el-menu-item) {
    flex: none;
    padding: 0 16px;
  }
}
</style>
