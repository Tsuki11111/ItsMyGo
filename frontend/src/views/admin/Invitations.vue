<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CopyDocument, Refresh, UserFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  approveAdminInvitation,
  createAdminInvitation,
  fetchAdminInvitations,
  revokeAdminInvitation,
} from '@/api/adminInvitation'
import type { AdminInvitationView } from '@/api/types'

const userStore = useUserStore()
const rows = ref<AdminInvitationView[]>([])
const loading = ref(false)
const creating = ref(false)
const username = ref('')
const statusFilter = ref('')
const created = ref<AdminInvitationView | null>(null)

const STATUS_META: Record<string, { label: string; type: 'warning' | 'success' | 'info' }> = {
  PENDING_APPROVAL: { label: '待批准', type: 'warning' },
  APPROVED: { label: '待接受', type: 'success' },
  ACCEPTED: { label: '已接受', type: 'success' },
  REVOKED: { label: '已撤回', type: 'info' },
  EXPIRED: { label: '已过期', type: 'info' },
}

const inviteLink = computed(() =>
  created.value?.token
    ? `${window.location.origin}/admin-invite?token=${encodeURIComponent(created.value.token)}`
    : '',
)

function statusLabel(status: string): string {
  return STATUS_META[status]?.label ?? status
}

function statusType(status: string): 'warning' | 'success' | 'info' {
  return STATUS_META[status]?.type ?? 'info'
}

function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function canApprove(row: AdminInvitationView): boolean {
  return row.status === 'PENDING_APPROVAL' && row.invitedBy !== userStore.user?.userId
}

async function load() {
  loading.value = true
  try {
    rows.value = await fetchAdminInvitations(statusFilter.value || undefined)
  } finally {
    loading.value = false
  }
}

async function create() {
  const name = username.value.trim()
  if (!name) {
    ElMessage.warning('请输入要邀请的用户名')
    return
  }
  creating.value = true
  try {
    created.value = await createAdminInvitation(name)
    username.value = ''
    ElMessage.success('邀请已创建，请等待另一位管理员批准')
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    creating.value = false
  }
}

async function approve(row: AdminInvitationView) {
  try {
    await ElMessageBox.confirm(
      `批准「${row.targetUsername}」的管理员邀请？批准后需要被邀请人打开邀请链接接受。`,
      '批准管理员邀请',
      { type: 'warning', confirmButtonText: '批准', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await approveAdminInvitation(row.id)
    ElMessage.success('已批准')
    await load()
  } catch {
    // 拦截器已提示
  }
}

async function revoke(row: AdminInvitationView) {
  try {
    await ElMessageBox.confirm(
      `撤回对「${row.targetUsername}」的邀请？`,
      '撤回管理员邀请',
      { type: 'warning', confirmButtonText: '撤回', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await revokeAdminInvitation(row.id)
    ElMessage.success('已撤回')
    await load()
  } catch {
    // 拦截器已提示
  }
}

async function copy(text: string, label: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label}已复制`)
  } catch {
    ElMessage.warning('浏览器不允许自动复制，请手动选中复制')
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="page-head">
      <div>
        <h2>管理员邀请</h2>
        <p class="page-desc">
          管理员不能自助注册。邀请需要另一位管理员批准，被邀请人登录后打开链接接受。
        </p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-alert type="info" :closable="false" class="bootstrap-tip">
      <template #title>
        首个管理员由部署时的 <code>BOOTSTRAP_ADMIN_USERNAME</code> /
        <code>BOOTSTRAP_ADMIN_PASSWORD</code> 创建；库中已有管理员时不会覆盖。
      </template>
    </el-alert>

    <section class="invite-card">
      <div class="invite-row">
        <el-input
          v-model="username"
          placeholder="输入普通顾客的用户名"
          clearable
          style="width: 280px"
          @keyup.enter="create"
        />
        <el-button type="primary" :icon="UserFilled" :loading="creating" @click="create">
          创建邀请
        </el-button>
        <span class="muted">只能邀请角色为 CUSTOMER 的账号</span>
      </div>
    </section>

    <section v-if="created?.token" class="link-card">
      <div class="link-title">「{{ created.targetUsername }}」的邀请链接（只显示这一次）</div>
      <el-input :model-value="inviteLink" readonly />
      <div class="link-actions">
        <el-button :icon="CopyDocument" @click="copy(inviteLink, '邀请链接')">复制链接</el-button>
        <el-button :icon="CopyDocument" @click="copy(created.token!, '邀请令牌')">复制令牌</el-button>
      </div>
      <p class="muted">
        把链接发给被邀请人；TA 需要先登录自己的账号，再打开链接接受。
        链接在另一位管理员批准后才会生效。
      </p>
    </section>

    <div class="filters">
      <el-select v-model="statusFilter" clearable placeholder="全部状态" style="width: 150px" @change="load">
        <el-option
          v-for="(meta, status) in STATUS_META"
          :key="status"
          :label="meta.label"
          :value="status"
        />
      </el-select>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column label="被邀请人" min-width="160">
        <template #default="{ row }">
          <p class="name">{{ row.targetUsername }}</p>
          <p class="muted">{{ row.targetNickname || '—' }}</p>
        </template>
      </el-table-column>
      <el-table-column prop="inviterUsername" label="发起人" width="130" />
      <el-table-column prop="approverUsername" label="批准人" width="130">
        <template #default="{ row }">{{ row.approverUsername || '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="150">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="过期时间" width="150">
        <template #default="{ row }">{{ formatTime(row.expiresAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'PENDING_APPROVAL'"
            link
            type="success"
            :disabled="!canApprove(row)"
            :title="!canApprove(row) ? '需要另一位管理员批准' : ''"
            @click="approve(row)"
          >
            批准
          </el-button>
          <el-button
            v-if="row.status === 'PENDING_APPROVAL' || row.status === 'APPROVED'"
            link
            type="danger"
            @click="revoke(row)"
          >
            撤回
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="还没有管理员邀请" />
      </template>
    </el-table>
  </div>
</template>

<style scoped>
.admin-page {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.page-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.page-desc {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.bootstrap-tip {
  margin-bottom: 14px;
}

.bootstrap-tip code {
  padding: 0 3px;
  background: var(--el-fill-color);
  border-radius: 3px;
}

.invite-card,
.link-card {
  padding: 14px;
  margin-bottom: 14px;
  background: #fafafa;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.invite-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.link-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
}

.link-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.filters {
  display: flex;
  margin-bottom: 12px;
}

.name {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}

.muted {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
