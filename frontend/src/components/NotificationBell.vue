<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  fetchNotifications,
  fetchNotificationUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/api/notifications'
import type { NotificationView } from '@/api/types'
import { useUserStore } from '@/stores/user'

/** 商家端 / 管理端顶栏通知铃铛：30s 可见轮询未读数，展开时拉最近 20 条。 */
const router = useRouter()
const userStore = useUserStore()
const unread = ref(0)
const items = ref<NotificationView[]>([])
const open = ref(false)
const loading = ref(false)
let timer: number | undefined

async function refreshUnread() {
  try {
    unread.value = await fetchNotificationUnreadCount()
  } catch {
    // 拦截器已提示；保留旧值
  }
}

async function loadList() {
  loading.value = true
  try {
    const view = await fetchNotifications(20)
    items.value = view.items
    unread.value = view.unreadCount
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function onShow() {
  void loadList()
}

function resolveLink(link: string): string {
  // 兼容历史数据：平台自营店的商家侧通知曾存成 /merchant/**，管理员点进去会被守卫拦截
  if (userStore.isAdmin && link.startsWith('/merchant')) {
    return '/admin' + link.slice('/merchant'.length)
  }
  return link
}

async function onClick(item: NotificationView) {
  if (!item.read) {
    try {
      await markNotificationRead(item.id)
      item.read = true
      unread.value = Math.max(0, unread.value - 1)
    } catch {
      // 已读失败不挡跳转
    }
  }
  open.value = false
  const link = resolveLink(item.link)
  if (link) router.push(link)
}

async function readAll() {
  try {
    await markAllNotificationsRead()
    items.value = items.value.map((item) => ({ ...item, read: true }))
    unread.value = 0
    ElMessage.success('已全部标记为已读')
  } catch {
    // 拦截器已提示
  }
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') void refreshUnread()
}

onMounted(() => {
  void refreshUnread()
  timer = window.setInterval(() => {
    if (document.visibilityState === 'visible') void refreshUnread()
  }, 30_000)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onBeforeUnmount(() => {
  if (timer !== undefined) {
    window.clearInterval(timer)
    timer = undefined
  }
  document.removeEventListener('visibilitychange', onVisibilityChange)
})

function timeText(ts: string) {
  return (ts || '').replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <el-popover
    v-model:visible="open"
    placement="bottom-end"
    :width="360"
    trigger="click"
    @show="onShow"
  >
    <template #reference>
      <button class="bell-btn" type="button" aria-label="通知">
        <el-badge :value="unread" :max="99" :hidden="unread === 0">
          <el-icon :size="20"><Bell /></el-icon>
        </el-badge>
      </button>
    </template>

    <div class="notify-head">
      <strong>通知</strong>
      <el-button v-if="items.length" link type="primary" size="small" @click="readAll">
        全部已读
      </el-button>
    </div>
    <div v-loading="loading" class="notify-body">
      <el-empty v-if="!loading && items.length === 0" description="暂无通知" :image-size="60" />
      <div
        v-for="item in items"
        :key="item.id"
        class="notify-item"
        :class="{ unread: !item.read }"
        @click="onClick(item)"
      >
        <div class="notify-title">{{ item.title }}</div>
        <div class="notify-content">{{ item.content }}</div>
        <div class="notify-time">{{ timeText(item.createdAt) }}</div>
      </div>
    </div>
  </el-popover>
</template>

<style scoped>
.bell-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.bell-btn:hover {
  background: rgba(0, 0, 0, 0.06);
}

.notify-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 8px;
  border-bottom: 1px solid #ebeef5;
}

.notify-body {
  max-height: 360px;
  overflow-y: auto;
  padding-top: 6px;
}

.notify-item {
  padding: 8px 6px;
  border-radius: 6px;
  cursor: pointer;
}

.notify-item:hover {
  background: #f5f7fa;
}

.notify-item.unread {
  background: #f0f9ff;
}

.notify-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.notify-content {
  margin-top: 2px;
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
}

.notify-time {
  margin-top: 4px;
  font-size: 11px;
  color: #909399;
}
</style>
