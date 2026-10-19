<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import ImageUpload from './ImageUpload.vue'
import { createProduct, updateProduct } from '@/api/admin'
import { createMerchantProduct, updateMerchantProduct } from '@/api/merchant'
import type { CategoryNode, Product } from '@/api/types'

const props = defineProps<{
  modelValue: boolean
  product: Product | null
  categories: CategoryNode[]
  /** 商家后台复用：走 /api/merchant/** 而不是 /api/admin/** */
  merchant?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const formRef = ref<FormInstance>()
const saving = ref(false)

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

/** 多规格商品的价格/库存挂在 SKU 上，商品级字段改了不生效 */
const isMultiSpec = computed(() => props.product?.hasSpecs === 1)

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
    if (props.product) {
      const updateFn = props.merchant ? updateMerchantProduct : updateProduct
      await updateFn(props.product.id, payload)
      ElMessage.success('商品已更新')
    } else {
      const createFn = props.merchant ? createMerchantProduct : createProduct
      await createFn(payload)
      ElMessage.success('商品已创建（已自动生成默认 SKU）')
    }
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="product ? '编辑商品' : '新增商品'"
    width="640px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert
      v-if="isMultiSpec"
      type="info"
      :closable="false"
      show-icon
      title="多规格商品的价格和库存请在「规格」编辑器中按 SKU 维护"
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

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
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
</style>
