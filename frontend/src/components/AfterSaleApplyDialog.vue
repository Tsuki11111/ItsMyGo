<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { applyAfterSale, fetchAfterSaleReasons, uploadAfterSaleEvidence } from '@/api/afterSale'
import type { AfterSaleReasonView, AfterSaleView, OrderItem } from '@/api/types'

const props = defineProps<{
  modelValue: boolean
  orderId: number
  orderStatus: string
  items: OrderItem[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: [afterSale: AfterSaleView]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const reasons = ref<AfterSaleReasonView[]>([])
const type = ref<'REFUND_ONLY' | 'RETURN_REFUND'>('REFUND_ONLY')
const reasonCode = ref('')
const reasonText = ref('')
const evidence = ref<string[]>([])
const submitting = ref(false)
const uploading = ref(false)
/** 选中的订单项 → 退款数量 */
const selected = ref<Record<number, number>>({})

const canReturn = computed(
  () => props.orderStatus === 'SHIPPED' || props.orderStatus === 'COMPLETED',
)

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    selected.value = {}
    evidence.value = []
    reasonText.value = ''
    type.value = 'REFUND_ONLY'
    if (!reasons.value.length) {
      try {
        reasons.value = await fetchAfterSaleReasons()
      } catch {
        reasons.value = []
      }
    }
    reasonCode.value = reasons.value[0]?.code ?? ''
  },
)

function toggleItem(item: OrderItem, checked: boolean) {
  if (checked) {
    selected.value = { ...selected.value, [item.id]: selected.value[item.id] ?? 1 }
  } else {
    const next = { ...selected.value }
    delete next[item.id]
    selected.value = next
  }
}

function setQuantity(item: OrderItem, value: number) {
  const quantity = Math.max(1, Math.min(Math.floor(value) || 1, item.quantity))
  selected.value = { ...selected.value, [item.id]: quantity }
}

async function upload(options: UploadRequestOptions) {
  if (evidence.value.length >= 3) {
    ElMessage.warning('凭证最多 3 张')
    return
  }
  uploading.value = true
  try {
    const url = await uploadAfterSaleEvidence(options.file)
    evidence.value = [...evidence.value, url]
    options.onSuccess?.(url)
  } catch {
    options.onError?.(new Error('上传失败') as never)
  } finally {
    uploading.value = false
  }
}

function removeEvidence(index: number) {
  evidence.value = evidence.value.filter((_, i) => i !== index)
}

async function submit() {
  const chosen = Object.entries(selected.value)
  if (!chosen.length) {
    ElMessage.warning('请选择要退的商品')
    return
  }
  if (!reasonCode.value) {
    ElMessage.warning('请选择售后原因')
    return
  }
  submitting.value = true
  try {
    const afterSale = await applyAfterSale({
      orderId: props.orderId,
      type: type.value,
      reasonCode: reasonCode.value,
      reasonText: reasonText.value.trim() || undefined,
      evidenceImages: evidence.value,
      items: chosen.map(([orderItemId, quantity]) => ({
        orderItemId: Number(orderItemId),
        quantity,
      })),
    })
    ElMessage.success('售后申请已提交，等待商家处理')
    visible.value = false
    emit('created', afterSale)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="申请售后" width="640px" :close-on-click-modal="false">
    <el-form label-width="88px">
      <el-form-item label="售后类型">
        <el-select v-model="type" style="width: 100%">
          <el-option label="仅退款（不退货）" value="REFUND_ONLY" />
          <el-option
            label="退货退款（寄回商品后退款）"
            value="RETURN_REFUND"
            :disabled="!canReturn"
          />
        </el-select>
        <div v-if="!canReturn" class="hint">未发货的订单只能申请仅退款</div>
      </el-form-item>

      <el-form-item label="商品">
        <div class="items">
          <div v-for="item in items" :key="item.id" class="item-row">
            <el-checkbox
              :model-value="item.id in selected"
              :label="item.productName"
              @change="(checked: boolean) => toggleItem(item, checked)"
            />
            <span class="sub">×{{ item.quantity }} · ¥{{ Number(item.unitPrice).toFixed(2) }}</span>
            <el-input-number
              v-if="item.id in selected"
              :model-value="selected[item.id]"
              :min="1"
              :max="item.quantity"
              size="small"
              style="width: 110px"
              @update:model-value="(value: number | undefined) => setQuantity(item, value ?? 1)"
            />
          </div>
        </div>
      </el-form-item>

      <el-form-item label="售后原因">
        <el-select v-model="reasonCode" style="width: 100%" placeholder="请选择原因">
          <el-option
            v-for="reason in reasons"
            :key="reason.code"
            :label="reason.label"
            :value="reason.code"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="补充说明">
        <el-input
          v-model="reasonText"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          placeholder="可写清问题，方便商家判断（最多 200 字）"
        />
      </el-form-item>

      <el-form-item label="凭证">
        <div class="evidence">
          <div v-for="(url, index) in evidence" :key="url" class="evidence-item">
            <el-image :src="url" fit="cover" class="evidence-img" />
            <el-button link type="danger" size="small" @click="removeEvidence(index)">移除</el-button>
          </div>
          <el-upload
            v-if="evidence.length < 3"
            :show-file-list="false"
            accept="image/*"
            :http-request="upload"
          >
            <el-button :loading="uploading" size="small">上传凭证（最多 3 张）</el-button>
          </el-upload>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交申请</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.hint {
  font-size: 12px;
  color: #999;
}
.items {
  width: 100%;
}
.item-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  border-bottom: 1px dashed #f0e4e8;
}
.sub {
  color: #999;
  font-size: 12px;
}
.evidence {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.evidence-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.evidence-img {
  width: 72px;
  height: 72px;
  border-radius: 6px;
  border: 1px solid #f0e4e8;
}
</style>
