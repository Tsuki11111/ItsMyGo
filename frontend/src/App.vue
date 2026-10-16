<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <header class="topbar">
    <div class="topbar-inner">
      <RouterLink to="/" class="logo">一次买够</RouterLink>
      <div class="search">
        <el-input placeholder="搜索打印机、路由器、笔记本…" clearable />
      </div>
      <nav class="actions">
        <RouterLink to="/cart" class="link">购物车</RouterLink>
        <RouterLink to="/login" class="link">登录</RouterLink>
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
  max-width: 1200px;
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

.page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}
</style>
