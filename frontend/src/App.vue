<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

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
  <header class="topbar">
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
          <span class="who">{{ userStore.displayName }}</span>
          <button type="button" class="link link-btn" @click="onLogout">退出</button>
        </template>
        <RouterLink v-else to="/login" class="link">登录 / 注册</RouterLink>
      </nav>
    </div>
  </header>

  <main class="page">
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
  gap: 20px;
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

/* 「退出」是个按钮，但长相要和旁边的链接一致 */
.link-btn {
  padding: 0;
  font-family: inherit;
  background: none;
  border: none;
  cursor: pointer;
}

.who {
  font-size: 14px;
  color: var(--c-text);
}

.page {
  max-width: 1440px;
  margin: 0 auto;
  padding: 24px;
}
</style>
