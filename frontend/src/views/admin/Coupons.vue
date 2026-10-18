<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createAdminCoupon,
  fetchAdminCoupons,
  updateAdminCoupon,
} from '@/api/admin'
import type { Coupon } from '@/api/types'

const rows = ref<Coupon[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const enabled = ref<boolean | ''>('')
const keyword = ref('')
const loading = ref(false)

const dialogVisible = ref(false)
const editing = ref<Coupon | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  thresholdAmount: 0,
  discountAmount: 0,
  totalCount: 100,
  validFrom: '',
  validTo: '',
  enabled: true,
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入券名称', trigger: 'blur' }],
  validFrom: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  validTo: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
}

async function load() {
  loading.value = true
  try {
    const result = await fetchAdminCoupons({
      page: page.value,
      size: size.value,
      enabled: enabled.value === '' ? undefined : enabled.value,
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
  enabled.value = ''
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

function openCreate() {
  editing.value = null
  const now = new Date()
  const weekLater = new Date(now.getTime() + 7 * 24 * 3600 * 1000)
  Object.assign(form, {
    name: '',
    thresholdAmount: 0,
    discountAmount: 10,
    totalCount: 100,
    validFrom: toLocal(now),
    validTo: toLocal(weekLater),
    enabled: true,
  })
  dialogVisible.value = true
}

function openEdit(row: Coupon) {
  editing.value = row
  Object.assign(form, {
    name: row.name,
    thresholdAmount: row.thresholdAmount,
    discountAmount: row.discountAmount,
    totalCount: row.totalCount,
    validFrom: row.validFrom,
    validTo: row.validTo,
    enabled: row.status === 1,
  })
  dialogVisible.value = true
}

function toLocal(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  const payload = {
    name: form.name.trim(),
    thresholdAmount: form.thresholdAmount,
    discountAmount: form.discountAmount,
    totalCount: form.totalCount,
    validFrom: form.validFrom,
    validTo: form.validTo,
    enabled: form.enabled,
  }

  saving.value = true
  try {
    if (editing.value) {
      await updateAdminCoupon(editing.value.id, payload)
      ElMessage.success('优惠券已更新')
    } else {
      await createAdminCoupon(payload)
      ElMessage.success('优惠券已创建')
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: Coupon, value: boolean) {
  await updateAdminCoupon(row.id, {
    name: row.name,
    thresholdAmount: row.thresholdAmount,
    discountAmount: row.discountAmount,
    totalCount: row.totalCount,
    validFrom: row.validFrom,
    validTo: row.validTo,
    enabled: value,
  })
  ElMessage.success(value ? '优惠券已启用' : '优惠券已停用')
  await load()
}

function onStatusChange(row: Coupon, value: string | number | boolean) {
  toggleStatus(row, Boolean(value))
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="page-head">
      <h2>优惠券管理</h2>
      <el-button type="primary" @click="openCreate">新增优惠券</el-button>
    </div>

    <div class="filters">
      <el-select v-model="enabled" clearable placeholder="全部状态" style="width: 140px">
        <el-option label="启用" :value="true" />
        <el-option label="停用" :value="false" />
      </el-select>
      <el-input
        v-model="keyword"
        placeholder="搜索券名称"
        clearable
        style="width: 220px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="name" label="券名称" min-width="160" />
      <el-table-column label="满减" width="150">
        <template #default="{ row }">
          满 ¥{{ row.thresholdAmount.toFixed(2) }} 减 ¥{{ row.discountAmount.toFixed(2) }}
        </template>
      </el-table-column>
      <el-table-column label="领取情况" width="120" align="center">
        <template #default="{ row }">{{ row.claimedCount }} / {{ row.totalCount }}</template>
      </el-table-column>
      <el-table-column label="有效期" width="220">
        <template #default="{ row }">
          {{ formatTime(row.validFrom) }} ~ {{ formatTime(row.validTo) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 1"
            @change="onStatusChange(row, $event)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="还没有优惠券" />
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

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑优惠券' : '新增优惠券'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="券名称" prop="name">
          <el-input v-model="form.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="使用门槛">
          <el-input-number v-model="form.thresholdAmount" :min="0" :precision="2" />
          <span class="hint">满多少可用，0 表示无门槛</span>
        </el-form-item>
        <el-form-item label="减免金额">
          <el-input-number v-model="form.discountAmount" :min="0.01" :precision="2" />
        </el-form-item>
        <el-form-item label="发放总量">
          <el-input-number v-model="form.totalCount" :min="editing ? editing.claimedCount : 1" />
          <span v-if="editing" class="hint">已领取 {{ editing.claimedCount }} 张</span>
        </el-form-item>
        <el-form-item label="开始时间" prop="validFrom">
          <el-date-picker
            v-model="form.validFrom"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择开始时间"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="validTo">
          <el-date-picker
            v-model="form.validTo"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择结束时间"
          />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
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
  align-items: center;
  justify-content: space-between;
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

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.hint {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
