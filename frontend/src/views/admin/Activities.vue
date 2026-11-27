<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Calendar, Picture } from '@element-plus/icons-vue'
import ImageUpload from '@/components/ImageUpload.vue'
import {
  createAdminActivity,
  createAdminLotteryPrize,
  fetchAdminActivities,
  fetchAdminLotteryPrizes,
  updateAdminActivity,
  updateAdminActivityStatus,
} from '@/api/admin'
import type {
  ActivityPayload,
  ActivityUpdatePayload,
  ActivityView,
  LotteryPrizePayload,
  LotteryPrizeView,
} from '@/api/types'

const rows = ref<ActivityView[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const editing = ref<ActivityView | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  type: 'LOTTERY',
  name: '',
  description: '',
  primaryColor: '#e8638f',
  coverImage: '',
  startAt: '',
  endAt: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入活动名称', trigger: 'blur' }],
  startAt: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endAt: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
}

const prizeVisible = ref(false)
const prizeActivity = ref<ActivityView | null>(null)
const prizes = ref<LotteryPrizeView[]>([])
const prizeLoading = ref(false)
const prizeSaving = ref(false)
const prizeForm = reactive<LotteryPrizePayload>({
  name: '',
  image: '',
  level: 1,
  weight: 1,
  stock: 10,
})

async function load() {
  loading.value = true
  try {
    rows.value = await fetchAdminActivities()
  } finally {
    loading.value = false
  }
}

function toLocal(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function openCreate() {
  editing.value = null
  const now = new Date()
  const weekLater = new Date(now.getTime() + 7 * 24 * 3600 * 1000)
  Object.assign(form, {
    type: 'LOTTERY',
    name: '',
    description: '',
    primaryColor: '#e8638f',
    coverImage: '',
    startAt: toLocal(now),
    endAt: toLocal(weekLater),
  })
  dialogVisible.value = true
}

function openEdit(row: ActivityView) {
  editing.value = row
  Object.assign(form, {
    type: row.type,
    name: row.name,
    description: row.description,
    primaryColor: row.primaryColor || '#e8638f',
    coverImage: row.coverImage,
    startAt: row.startAt,
    endAt: row.endAt,
  })
  dialogVisible.value = true
}

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  const payload: ActivityPayload = {
    type: form.type,
    name: form.name.trim(),
    description: form.description.trim(),
    primaryColor: form.primaryColor,
    coverImage: form.coverImage,
    startAt: form.startAt,
    endAt: form.endAt,
  }

  saving.value = true
  try {
    if (editing.value) {
      const update: ActivityUpdatePayload = {
        name: payload.name,
        description: payload.description,
        primaryColor: payload.primaryColor,
        coverImage: payload.coverImage,
        startAt: payload.startAt,
        endAt: payload.endAt,
      }
      await updateAdminActivity(editing.value.id, update)
      ElMessage.success('活动已更新')
    } else {
      await createAdminActivity(payload)
      ElMessage.success('活动已创建')
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: ActivityView, value: string | number | boolean) {
  const active = Boolean(value)
  await updateAdminActivityStatus(row.id, active ? 1 : 2)
  ElMessage.success(active ? '活动已恢复' : '活动已结束')
  await load()
}

function openPrizes(row: ActivityView) {
  if (row.type !== 'LOTTERY') {
    ElMessage.info('该活动类型暂无奖项配置')
    return
  }
  prizeActivity.value = row
  Object.assign(prizeForm, { name: '', image: '', level: 1, weight: 1, stock: 10 })
  prizeVisible.value = true
  loadPrizes(row.id)
}

async function loadPrizes(activityId: number) {
  prizeLoading.value = true
  try {
    prizes.value = await fetchAdminLotteryPrizes(activityId)
  } finally {
    prizeLoading.value = false
  }
}

async function addPrize() {
  if (!prizeActivity.value) return
  if (!prizeForm.name.trim()) {
    ElMessage.warning('请填写奖项名称')
    return
  }
  const activityId = prizeActivity.value.id
  prizeSaving.value = true
  try {
    await createAdminLotteryPrize(activityId, {
      name: prizeForm.name.trim(),
      image: prizeForm.image,
      level: prizeForm.level,
      weight: prizeForm.weight,
      stock: prizeForm.stock,
    })
    ElMessage.success('奖项已添加')
    Object.assign(prizeForm, { name: '', image: '', level: 1, weight: 1, stock: 10 })
    await loadPrizes(activityId)
  } finally {
    prizeSaving.value = false
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
      <h2>活动管理</h2>
      <el-button type="primary" @click="openCreate">新建活动</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column label="封面" width="96" align="center">
        <template #default="{ row }">
          <el-image
            v-if="row.coverImage"
            :src="row.coverImage"
            fit="cover"
            class="cover-thumb"
          />
          <div v-else class="cover-thumb cover-empty" :style="{ background: row.primaryColor }">
            <el-icon><Calendar /></el-icon>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column label="类型" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small">{{ row.typeLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="活动名称" min-width="150" />
      <el-table-column prop="description" label="副标题" min-width="160" show-overflow-tooltip />
      <el-table-column label="起止时间" width="230">
        <template #default="{ row }">
          {{ formatTime(row.startAt) }} ~ {{ formatTime(row.endAt) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-switch :model-value="row.status === 1" @change="toggleStatus(row, $event)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="row.type === 'LOTTERY'"
            link
            type="primary"
            @click="openPrizes(row)"
          >
            奖项
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="还没有活动" />
      </template>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑活动' : '新建活动'"
      width="620px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="活动类型">
          <el-select v-model="form.type" :disabled="!!editing" style="width: 180px">
            <el-option label="抽奖" value="LOTTERY" />
          </el-select>
        </el-form-item>
        <el-form-item label="活动名称" prop="name">
          <el-input v-model="form.name" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="form.description" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="主色调">
          <el-color-picker v-model="form.primaryColor" />
          <span class="color-text">{{ form.primaryColor }}</span>
        </el-form-item>
        <el-form-item label="封面图">
          <ImageUpload v-model="form.coverImage" compact />
        </el-form-item>
        <el-form-item label="开始时间" prop="startAt">
          <el-date-picker
            v-model="form.startAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endAt">
          <el-date-picker
            v-model="form.endAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择结束时间"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="prizeVisible"
      :title="prizeActivity ? `奖项配置 · ${prizeActivity.name}` : '奖项配置'"
      width="820px"
    >
      <el-table v-loading="prizeLoading" :data="prizes" border size="small">
        <el-table-column label="图片" width="70" align="center">
          <template #default="{ row }">
            <el-image v-if="row.image" :src="row.image" fit="cover" class="prize-thumb" />
            <div v-else class="prize-thumb prize-empty"><el-icon><Picture /></el-icon></div>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="奖项" min-width="180" />
        <el-table-column prop="level" label="等级" width="70" align="center" />
        <el-table-column prop="weight" label="权重" width="70" align="center" />
        <el-table-column prop="stock" label="初始库存" width="90" align="center" />
        <el-table-column label="实时剩余" width="90" align="center">
          <template #default="{ row }">
            {{ row.remainingStock === null ? '-' : row.remainingStock }}
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="还没有奖项" />
        </template>
      </el-table>

      <el-divider>新增奖项</el-divider>
      <div class="prize-form">
        <el-input v-model="prizeForm.name" placeholder="奖项名称" style="width: 200px" />
        <el-input-number v-model="prizeForm.level" :min="1" :max="99" controls-position="right" />
        <el-input-number v-model="prizeForm.weight" :min="1" :max="9999" controls-position="right" />
        <el-input-number v-model="prizeForm.stock" :min="1" :max="99999" controls-position="right" />
        <ImageUpload v-model="prizeForm.image" compact />
        <el-button type="primary" :loading="prizeSaving" @click="addPrize">添加</el-button>
      </div>
      <p class="prize-hint">
        权重越大越容易中；小额券建议配高权重，实物大奖配低权重。图片留空时前端显示券图标。
      </p>

      <template #footer>
        <el-button @click="prizeVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-head h2 {
  margin: 0;
  font-size: 18px;
}

.cover-thumb {
  width: 64px;
  height: 40px;
  border-radius: 6px;
  display: block;
}

.cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20px;
}

.color-text {
  margin-left: 10px;
  color: var(--el-text-color-regular);
  font-family: ui-monospace, 'SF Mono', Consolas, monospace;
  font-size: 12px;
}

.prize-thumb {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: block;
}

.prize-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-pink);
  background: var(--c-pink-tint);
}

.prize-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.prize-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
