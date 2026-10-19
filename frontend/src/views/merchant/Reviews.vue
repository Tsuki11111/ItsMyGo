<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchMerchantReviews, setMerchantReviewVisible } from '@/api/merchant'
import type { AdminReviewView } from '@/api/types'

const rows = ref<AdminReviewView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const visible = ref<boolean | ''>('')
const keyword = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await fetchMerchantReviews({
      page: page.value,
      size: size.value,
      visible: visible.value === '' ? undefined : visible.value,
      keyword: keyword.value.trim() || undefined,
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
  visible.value = ''
  keyword.value = ''
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

async function toggleVisible(row: AdminReviewView, value: boolean) {
  await setMerchantReviewVisible(row.id, value)
  ElMessage.success(value ? '评价已展示' : '评价已隐藏')
  await load()
}

function onVisibleChange(row: AdminReviewView, value: string | number | boolean) {
  toggleVisible(row, Boolean(value))
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="page-head">
      <h2>评价管理</h2>
    </div>

    <div class="filters">
      <el-select v-model="visible" clearable placeholder="全部状态" style="width: 140px">
        <el-option label="展示中" :value="true" />
        <el-option label="已隐藏" :value="false" />
      </el-select>
      <el-input
        v-model="keyword"
        placeholder="搜索评价内容"
        clearable
        style="width: 240px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="productName" label="商品" min-width="180" />
      <el-table-column prop="userNickname" label="用户" width="120" />
      <el-table-column label="评分" width="150">
        <template #default="{ row }">
          <el-rate :model-value="row.rating" disabled size="small" />
        </template>
      </el-table-column>
      <el-table-column label="评价内容" min-width="260">
        <template #default="{ row }">
          <span v-if="row.content">{{ row.content }}</span>
          <span v-else class="muted">（未填写文字）</span>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="150">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="展示" width="90" align="center">
        <template #default="{ row }">
          <el-switch :model-value="row.status === 1" @change="onVisibleChange(row, $event)" />
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="还没有评价" />
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
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.muted {
  color: var(--el-text-color-secondary);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
