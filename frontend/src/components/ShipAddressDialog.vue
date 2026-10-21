<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import areaData from '@/data/china-area.json'
import { updateMerchantShipAddress } from '@/api/merchant'
import { updateAdminMerchantShipAddress } from '@/api/admin'
import { geocodeAddress } from '@/utils/tencentMap'
import type { MerchantView } from '@/api/types'

/**
 * 发货地址弹窗 —— 商家资料页和平台商户管理共用。
 * 地址坐标决定虚拟物流的起点；不解析坐标时后端回退虚拟转运中心。
 */
type AreaTree = Record<string, Record<string, string[]>>
const AREA = areaData as AreaTree
const areaOptions = Object.entries(AREA).map(([province, cities]) => ({
  value: province,
  label: province,
  children: Object.entries(cities).map(([city, districts]) => ({
    value: city,
    label: city,
    children: districts.map((district) => ({ value: district, label: district })),
  })),
}))

const props = defineProps<{
  modelValue: boolean
  /** 商家自己设置 / 平台代设 */
  mode: 'merchant' | 'admin'
  merchantId: number
  merchantName: string
  initial?: MerchantView | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const form = reactive({
  area: [] as string[],
  detail: '',
  lng: null as number | null,
  lat: null as number | null,
})

/** 当前坐标对应的完整地址；地址改过就当坐标失效 */
const resolvedAddress = ref('')
const resolving = ref(false)
const saving = ref(false)

const title = computed(() =>
  props.merchantId === 1 && props.mode === 'admin' ? '平台自营发货地址' : '发货地址',
)

const fullAddress = computed(() => `${form.area.join('')}${form.detail.trim()}`)

const located = computed(() => form.lng != null && form.lat != null)

function reset() {
  const initial = props.initial
  form.area = initial?.shipProvince && initial?.shipCity && initial?.shipDistrict
    ? [initial.shipProvince, initial.shipCity, initial.shipDistrict]
    : []
  form.detail = initial?.shipAddress || ''
  form.lng = initial?.shipLng ?? null
  form.lat = initial?.shipLat ?? null
  resolvedAddress.value = located.value ? fullAddress.value : ''
}

watch(() => props.modelValue, (open) => {
  if (open) reset()
})

async function resolveCoords() {
  if (form.area.length < 3 || !form.detail.trim()) {
    ElMessage.warning('请先选择省市区并填写详细地址')
    return
  }
  resolving.value = true
  try {
    const point = await geocodeAddress(fullAddress.value)
    if (!point) {
      ElMessage.warning('没有解析到坐标；保存后物流将使用虚拟转运中心兜底')
      return
    }
    form.lng = point.lng
    form.lat = point.lat
    resolvedAddress.value = fullAddress.value
    ElMessage.success('坐标解析成功')
  } catch (e) {
    // 腾讯地图失败时 reject 的是普通对象（status/message），不是 Error
    const message = e instanceof Error
      ? e.message
      : (e as { message?: string } | null)?.message
    ElMessage.error(message || '坐标解析失败')
  } finally {
    resolving.value = false
  }
}

async function save() {
  if (form.area.length < 3) {
    ElMessage.warning('请选择省 / 市 / 区县')
    return
  }
  if (!form.detail.trim()) {
    ElMessage.warning('请输入详细地址')
    return
  }

  // 地址改过但没重新解析：丢掉旧坐标，避免把物流起点画到旧地址
  const coordsStale = located.value && resolvedAddress.value !== fullAddress.value
  const payload = {
    province: form.area[0],
    city: form.area[1],
    district: form.area[2],
    address: form.detail.trim(),
    lng: coordsStale ? null : form.lng,
    lat: coordsStale ? null : form.lat,
  }

  saving.value = true
  try {
    if (props.mode === 'admin') {
      await updateAdminMerchantShipAddress(props.merchantId, payload)
    } else {
      await updateMerchantShipAddress(payload)
    }
    if (payload.lng == null || payload.lat == null) {
      ElMessage.success('发货地址已保存；未定位坐标，物流使用虚拟转运中心兜底')
    } else {
      ElMessage.success('发货地址已保存')
    }
    emit('saved')
    visible.value = false
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="560px" append-to-body>
    <p class="shop">店铺：{{ merchantName || `#${merchantId}` }}</p>
    <el-form label-width="90px">
      <el-form-item label="所在地区" required>
        <el-cascader
          v-model="form.area"
          :options="areaOptions"
          placeholder="省 / 市 / 区县"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="详细地址" required>
        <el-input
          v-model="form.detail"
          maxlength="128"
          show-word-limit
          placeholder="街道、门牌号、园区等"
        />
      </el-form-item>
      <el-form-item label="发货坐标">
        <div class="coords">
          <el-button :loading="resolving" @click="resolveCoords">解析坐标</el-button>
          <span v-if="located" class="coord-text">
            已定位 {{ form.lng?.toFixed(6) }}, {{ form.lat?.toFixed(6) }}
          </span>
          <span v-else class="coord-text muted">未定位</span>
        </div>
      </el-form-item>
    </el-form>

    <p class="hint">
      发货后物流地图会从这里出发。点击「解析坐标」可精确定位；未定位时使用虚拟转运中心兜底。
      修改地址后需要重新解析，否则保存时坐标会被清空。
    </p>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.shop {
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.coords {
  display: flex;
  align-items: center;
  gap: 10px;
}

.coord-text {
  font-size: 12px;
  color: var(--el-color-success);
}

.coord-text.muted {
  color: var(--el-text-color-secondary);
}

.hint {
  margin: 0;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}
</style>
