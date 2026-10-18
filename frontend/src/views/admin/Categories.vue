<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { createCategory, deleteCategory, fetchAdminCategories, updateCategory } from '@/api/admin'
import type { CategoryNode } from '@/api/types'

const tree = ref<CategoryNode[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  parentId: null as number | null,
  name: '',
  sort: 0,
  enabled: true,
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入分类名', trigger: 'blur' },
    { max: 32, message: '分类名最长 32 个字符', trigger: 'blur' },
  ],
}

async function load() {
  loading.value = true
  try {
    tree.value = await fetchAdminCategories()
  } finally {
    loading.value = false
  }
}

/**
 * 父分类下拉要剔除「自己及全部后代」。
 * 后端只拦了「把自己设成父分类」，没拦后代成环。
 */
function filterTree(nodes: CategoryNode[], excludeId: number | null): CategoryNode[] {
  return nodes
    .filter((node) => node.id !== excludeId)
    .map((node) => ({ ...node, children: filterTree(node.children ?? [], excludeId) }))
}

const parentOptions = computed(() => filterTree(tree.value, editingId.value))

function openCreate(parentId?: number) {
  editingId.value = null
  Object.assign(form, {
    parentId: parentId ?? null,
    name: '',
    sort: 0,
    enabled: true,
  })
  dialogVisible.value = true
}

function openEdit(row: CategoryNode) {
  editingId.value = row.id
  Object.assign(form, {
    parentId: row.parentId === 0 ? null : row.parentId,
    name: row.name,
    sort: row.sort,
    enabled: row.status === 1,
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

  const payload = {
    parentId: form.parentId ?? 0,
    name: form.name.trim(),
    sort: form.sort ?? 0,
    enabled: form.enabled,
  }

  saving.value = true
  try {
    if (editingId.value === null) {
      await createCategory(payload)
      ElMessage.success('分类已创建')
    } else {
      await updateCategory(editingId.value, payload)
      ElMessage.success('分类已更新')
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: CategoryNode, enabled: boolean) {
  try {
    await updateCategory(row.id, {
      parentId: row.parentId,
      name: row.name,
      sort: row.sort,
      enabled,
    })
    ElMessage.success(enabled ? '分类已启用' : '分类已停用')
  } finally {
    await load()
  }
}

function onStatusChange(row: CategoryNode, value: string | number | boolean) {
  toggleStatus(row, Boolean(value))
}

async function remove(row: CategoryNode) {
  try {
    await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？`, '删除分类', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  await deleteCategory(row.id)
  ElMessage.success('分类已删除')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="page-head">
      <h2>分类管理</h2>
      <el-button type="primary" @click="openCreate()">新增顶级分类</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="tree"
      row-key="id"
      :tree-props="{ children: 'children' }"
      default-expand-all
      border
    >
      <el-table-column prop="name" label="分类名" min-width="220" />
      <el-table-column prop="sort" label="排序" width="90" align="center" />
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 1"
            @change="onStatusChange(row, $event)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button link type="primary" @click="openCreate(row.id)">新增子分类</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="还没有分类" />
      </template>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新增分类' : '编辑分类'"
      width="460px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="父分类">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'name', children: 'children' }"
            node-key="id"
            check-strictly
            clearable
            placeholder="留空表示顶级分类"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="分类名" prop="name">
          <el-input v-model="form.name" maxlength="32" placeholder="如：笔记本" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
          <span class="hint">数字小的排在前面</span>
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

.hint {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
