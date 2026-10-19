<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { adjustSkuStock } from '@/api/admin'
import { adjustMerchantSkuStock } from '@/api/merchant'
import type { ProductSku } from '@/api/types'

const props = defineProps<{
  modelValue: boolean
  productId: number
  sku: ProductSku | null
  /** 商家后台复用：走 /api/merchant/** */
  merchant?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  updated: [sku: ProductSku]
}>()

const stock = ref(0)
const saving = ref(false)

watch(
  () => props.modelValue,
  (open) => {
    if (open) stock.value = props.sku?.stock ?? 0
  },
)

async function submit() {
  if (!props.sku) return

  saving.value = true
  try {
    const adjustFn = props.merchant ? adjustMerchantSkuStock : adjustSkuStock
    const updated = await adjustFn(props.productId, props.sku.id, {
      stock: stock.value,
      // 条件更新：期间被下单/取消改过就拒绝，而不是硬覆盖
      expectedStock: props.sku.stock,
    })
    emit('updated', updated)
    emit('update:modelValue', false)
    ElMessage.success('库存已更新')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="调整库存"
    width="380px"
    class="stock-adjust-dialog"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-width="80px">
      <el-form-item label="规格">
        <span>{{ sku?.specText || '默认规格' }}</span>
      </el-form-item>
      <el-form-item label="当前库存">
        <span>{{ sku?.stock ?? 0 }}</span>
      </el-form-item>
      <el-form-item label="调整为">
        <el-input-number v-model="stock" :min="0" :step="1" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>
