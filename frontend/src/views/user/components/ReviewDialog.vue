<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createReview } from '@/api/review'
import type { OrderItem } from '@/api/types'

const props = defineProps<{
  modelValue: boolean
  orderItem: OrderItem | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submitted: []
}>()

const rating = ref(5)
const content = ref('')
const submitting = ref(false)

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      rating.value = 5
      content.value = ''
    }
  },
)

async function submit() {
  if (!props.orderItem) return
  if (rating.value < 1) {
    ElMessage.warning('请先选择评分')
    return
  }

  submitting.value = true
  try {
    await createReview({
      orderItemId: props.orderItem.id,
      rating: rating.value,
      content: content.value.trim(),
    })
    ElMessage.success('评价已提交')
    emit('submitted')
    emit('update:modelValue', false)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="发表评价"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="orderItem" class="product">
      <p class="name">{{ orderItem.productName }}</p>
      <p v-if="orderItem.specText" class="spec">{{ orderItem.specText }}</p>
    </div>

    <el-form label-width="70px">
      <el-form-item label="评分">
        <el-rate v-model="rating" />
      </el-form-item>
      <el-form-item label="评价">
        <el-input
          v-model="content"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="说说这件商品怎么样（选填）"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交评价</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.product {
  margin-bottom: 14px;
  padding: 10px 12px;
  background: var(--c-pink-tint);
  border-radius: 8px;
}

.name {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
}

.spec {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--c-text-2);
}
</style>
