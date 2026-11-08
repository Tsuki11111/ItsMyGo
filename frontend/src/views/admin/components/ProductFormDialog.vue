<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import ImageUpload from '@/components/ImageUpload.vue'
import SkuEditorPanel from './SkuEditorPanel.vue'
import { createProduct, updateProduct } from '@/api/admin'
import { createMerchantProduct, updateMerchantProduct } from '@/api/merchant'
import type { CategoryNode, Product } from '@/api/types'

const props = defineProps<{
  modelValue: boolean
  product: Product | null
  categories: CategoryNode[]
  /** 商家后台复用：走 /api/merchant/** 而不是 /api/admin/** */
  merchant?: boolean
  /** 打开时默认展示的页签（列表「规格」按钮直接进规格页） */
  initialTab?: 'basic' | 'specs'
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const formRef = ref<FormInstance>()
const saving = ref(false)
const activeTab = ref<'basic' | 'specs'>('basic')
/** 新建商品保存成功后留在弹窗，继续在同一弹窗维护规格 */
const createdProduct = ref<Product | null>(null)
const panelRef = ref<{ save: () => Promise<boolean>; hasSpecs: () => boolean } | null>(null)

const form = reactive({
  categoryId: null as number | null,
  name: '',
  subtitle: '',
  mainImage: '',
  price: 0,
  stock: 0,
  detail: '',
  onSale: true,
})

const effectiveProduct = computed(() => props.product ?? createdProduct.value)
const currentProductId = computed(() => effectiveProduct.value?.id ?? null)
const isEditing = computed(() => Boolean(effectiveProduct.value))
/** 多规格商品的价格/库存挂在 SKU 上，商品级字段改了不生效 */
const isMultiSpec = computed(() => effectiveProduct.value?.hasSpecs === 1)

const rules: FormRules = {
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  name: [
    { required: true, message: '请输入商品名', trigger: 'blur' },
    { max: 128, message: '商品名最长 128 个字符', trigger: 'blur' },
  ],
  subtitle: [{ max: 255, message: '副标题最长 255 个字符', trigger: 'blur' }],
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return

    createdProduct.value = null
    activeTab.value = props.initialTab ?? 'basic'
    const product = props.product
    Object.assign(
      form,
      product
        ? {
            categoryId: product.categoryId,
            name: product.name,
            subtitle: product.subtitle ?? '',
            mainImage: product.mainImage ?? '',
            price: product.price,
            stock: product.stock,
            detail: product.detail ?? '',
            onSale: product.status === 1,
          }
        : {
            categoryId: null,
            name: '',
            subtitle: '',
            mainImage: '',
            price: 0,
            stock: 0,
            detail: '',
            onSale: true,
          },
    )
    formRef.value?.clearValidate()
  },
)

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  const payload = {
    categoryId: form.categoryId!,
    name: form.name.trim(),
    subtitle: form.subtitle.trim(),
    mainImage: form.mainImage,
    price: form.price ?? 0,
    stock: form.stock ?? 0,
    detail: form.detail,
    onSale: form.onSale,
  }

  saving.value = true
  try {
    if (effectiveProduct.value) {
      const updateFn = props.merchant ? updateMerchantProduct : updateProduct
      await updateFn(effectiveProduct.value.id, payload)
      // 规格已存在（多规格）时，同一弹窗内一并保存；单规格价格/库存由商品级字段同步到默认 SKU
      if (panelRef.value?.hasSpecs()) {
        const ok = await panelRef.value.save()
        if (!ok) return
      }
      ElMessage.success('商品与规格已更新')
      emit('update:modelValue', false)
      emit('saved')
    } else {
      const createFn = props.merchant ? createMerchantProduct : createProduct
      createdProduct.value = await createFn(payload)
      activeTab.value = 'specs'
      ElMessage.success('商品已创建，可继续维护规格/SKU 后保存')
      emit('saved')
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEditing ? '编辑商品' : '新增商品'"
    :width="activeTab === 'specs' ? '980px' : '640px'"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-tabs v-model="activeTab">
      <el-tab-pane label="基础信息" name="basic">
        <el-alert
          v-if="isMultiSpec"
          type="info"
          :closable="false"
          show-icon
          title="多规格商品的价格和库存请在「规格与库存」页按 SKU 维护"
          class="multi-spec-tip"
        />

        <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
          <el-form-item label="分类" prop="categoryId">
            <el-tree-select
              v-model="form.categoryId"
              :data="categories"
              :props="{ label: 'name', children: 'children' }"
              node-key="id"
              check-strictly
              placeholder="选择分类"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="商品名" prop="name">
            <el-input v-model="form.name" maxlength="128" />
          </el-form-item>

          <el-form-item label="副标题" prop="subtitle">
            <el-input v-model="form.subtitle" maxlength="255" placeholder="选填，列表卡片上显示" />
          </el-form-item>

          <el-form-item label="主图">
            <ImageUpload v-model="form.mainImage" :merchant="merchant" />
          </el-form-item>

          <el-form-item label="价格">
            <el-input-number
              v-model="form.price"
              :min="0"
              :precision="2"
              :step="1"
              :disabled="isMultiSpec"
            />
            <span class="hint">元；多规格商品这里是最低价汇总</span>
          </el-form-item>

          <el-form-item label="库存">
            <el-input-number v-model="form.stock" :min="0" :step="1" :disabled="isMultiSpec" />
            <span class="hint">多规格商品这里是各 SKU 之和</span>
          </el-form-item>

          <el-form-item label="详情">
            <el-input
              v-model="form.detail"
              type="textarea"
              :rows="5"
              placeholder="选填，商品详情页展示"
            />
          </el-form-item>

          <el-form-item label="上架">
            <el-switch v-model="form.onSale" />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="规格与库存" name="specs">
        <SkuEditorPanel
          v-if="currentProductId"
          ref="panelRef"
          :product-id="currentProductId"
          :product="effectiveProduct"
          :merchant="merchant"
        />
        <el-alert
          v-else
          type="info"
          :closable="false"
          show-icon
          title="先保存基础信息创建商品，创建后即可在同一弹窗内维护规格与 SKU"
        />
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">
        {{ isEditing ? '保存' : '创建商品' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.multi-spec-tip {
  margin-bottom: 16px;
}

.hint {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

:deep(.el-tabs__content) {
  overflow: visible;
}
</style>
