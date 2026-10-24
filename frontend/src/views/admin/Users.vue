<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { disableAdminUser, enableAdminUser, fetchAdminUsers } from '@/api/admin'
import type { AdminUserView } from '@/api/types'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const rows = ref<AdminUserView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const role = ref('')
const status = ref<number | null>(null)
const loading = ref(false)
const acting = ref<number | null>(null)

const ROLE_OPTIONS = [
  { value: 'CUSTOMER', label: '顾客' },
  { value: 'MERCHANT', label: '商家' },
  { value: 'ADMIN', label: '管理员' },
]

const STATUS_OPTIONS = [
  { value: 1, label: '正常' },
  { value: 0, label: '已禁用' },
]

function roleLabel(value: string): string {
  return ROLE_OPTIONS.find((item) => item.value === value)?.label ?? value
}

function roleTag(value: string): 'info' | 'warning' | 'danger' {
  return ({ CUSTOMER: 'info', MERCHANT: 'warning', ADMIN: 'danger' } as const)[value] ?? 'info'
}

function isSelf(row: AdminUserView): boolean {
  return userStore.user?.userId === row.id
}

async function load() {
  loading.value = true
  try {
    const result = await fetchAdminUsers({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
      role: role.value || undefined,
      status: status.value ?? undefined,
    })
    rows.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  keyword.value = ''
  role.value = ''
  status.value = null
  page.value = 1
  load()
}

function onPageChange(next: number) {
  page.value = next
  load()
}

function onSizeChange(next: number) {
  size.value = next
  page.value = 1
  load()
}

async function toggle(row: AdminUserView) {
  const disabling = row.status === 1
  try {
    await ElMessageBox.confirm(
      disabling
        ? `禁用「${row.username}」后，该账号的旧登录态会在下一次请求时失效，确定吗？`
        : `恢复「${row.username}」的登录权限？`,
      disabling ? '禁用用户' : '启用用户',
      {
        type: disabling ? 'warning' : 'success',
        confirmButtonText: disabling ? '禁用' : '启用',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  acting.value = row.id
  try {
    if (disabling) await disableAdminUser(row.id)
    else await enableAdminUser(row.id)
    ElMessage.success(disabling ? '已禁用' : '已启用')
    await load()
  } finally {
    acting.value = null
  }
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="page-head">
      <h2>用户管理</h2>
    </div>

    <div class="filters">
      <el-input
        v-model="keyword"
        placeholder="用户名 / 昵称 / 手机号"
        clearable
        style="width: 240px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select v-model="role" clearable placeholder="全部角色" style="width: 140px" @change="search">
        <el-option
          v-for="option in ROLE_OPTIONS"
          :key="option.value"
          :value="option.value"
          :label="option.label"
        />
      </el-select>
      <el-select
        v-model="status"
        clearable
        placeholder="全部状态"
        style="width: 130px"
        @change="search"
      >
        <el-option
          v-for="option in STATUS_OPTIONS"
          :key="option.value"
          :value="option.value"
          :label="option.label"
        />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="id" label="ID" width="80" align="center" />
      <el-table-column prop="username" label="用户名" min-width="130" />
      <el-table-column prop="nickname" label="昵称" min-width="120">
        <template #default="{ row }">{{ row.nickname || '—' }}</template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="140">
        <template #default="{ row }">{{ row.phone || '—' }}</template>
      </el-table-column>
      <el-table-column label="角色" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="roleTag(row.role)" size="small">{{ roleLabel(row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '正常' : '已禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="商户 ID" width="100" align="center">
        <template #default="{ row }">{{ row.merchantId ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="注册时间" width="150">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-tooltip v-if="isSelf(row)" content="不能操作当前登录账号" placement="top">
            <span>
              <el-button link type="info" disabled>当前账号</el-button>
            </span>
          </el-tooltip>
          <el-button
            v-else
            link
            :type="row.status === 1 ? 'danger' : 'success'"
            :loading="acting === row.id"
            @click="toggle(row)"
          >
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="没有符合条件的用户" />
      </template>
    </el-table>

    <div class="pager">
      <el-pagination
        :current-page="page"
        :page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<style scoped>
.admin-page {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
}

.page-head {
  margin-bottom: 16px;
}

.page-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
