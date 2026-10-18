<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import areaData from '@/data/china-area.json'
import { createAddress, updateAddress } from '@/api/address'
import type { Address } from '@/api/types'

/**
 * 省 → 市 → 区县名。
 * 数据来自 modood/Administrative-divisions-of-China（民政部行政区划，31 个省级），
 * 已压成 { 省: { 市: [区县…] } } 放在 src/data/china-area.json。
 */
type AreaTree = Record<string, Record<string, string[]>>
const AREA = areaData as AreaTree

/**
 * 收货地址表单弹窗 —— 结算页和用户中心的地址管理共用同一份。
 * 各写一份的话，校验规则迟早会漂移。
 */
const props = defineProps<{
  modelValue: boolean
  /** 传了就是编辑；不传是新增 */
  address?: Address | null
  /** 新增时是否默认勾上「设为默认」（结算页里第一个地址会这样） */
  defaultWhenNew?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [address: Address]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive({
  receiver: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: false,
})

const rules: FormRules = {
  receiver: [{ required: true, message: '请输入收货人', trigger: 'blur' }],
  // 中国大陆手机号：1 开头、第二位 3-9、共 11 位（和淘宝/京东同口径，只收手机号）
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的 11 位手机号', trigger: 'blur' },
  ],
  province: [{ required: true, message: '请选择省份', trigger: 'change' }],
  city: [{ required: true, message: '请选择城市', trigger: 'change' }],
  district: [{ required: true, message: '请选择区/县', trigger: 'change' }],
  detail: [{ required: true, message: '请输入详细地址', trigger: 'blur' }],
}

const provinces = computed(() => Object.keys(AREA))

const cities = computed(() => {
  if (!form.province) return []
  const options = Object.keys(AREA[form.province] ?? {})
  // 老数据里的城市名可能不在数据集里（比如直辖市的「北京市」在数据里是「市辖区」），
  // 把原值保留成一个选项，编辑时才不会被悄悄清空
  return form.city && !options.includes(form.city) ? [form.city, ...options] : options
})

const districts = computed(() => {
  if (!form.province || !form.city) return []
  const options = AREA[form.province]?.[form.city] ?? []
  return form.district && !options.includes(form.district)
      ? [form.district, ...options]
      : options
})

function onProvinceChange() {
  const options = Object.keys(AREA[form.province] ?? {})
  if (!options.includes(form.city)) {
    form.city = ''
    form.district = ''
  }
}

function onCityChange() {
  const options = AREA[form.province]?.[form.city] ?? []
  if (!options.includes(form.district)) {
    form.district = ''
  }
}

watch(visible, (open) => {
  if (!open) return

  const source = props.address
  Object.assign(form, {
    receiver: source?.receiver ?? '',
    phone: source?.phone ?? '',
    province: source?.province ?? '',
    city: source?.city ?? '',
    district: source?.district ?? '',
    detail: source?.detail ?? '',
    isDefault: source ? source.isDefault === 1 : Boolean(props.defaultWhenNew),
  })
  formRef.value?.clearValidate()
})

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const saved = props.address
        ? await updateAddress(props.address.id, { ...form })
        : await createAddress({ ...form })
    ElMessage.success(props.address ? '地址已更新' : '地址已保存')
    visible.value = false
    emit('saved', saved)
  } catch {
    // 参数不合法等，拦截器已提示
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="address ? '编辑收货地址' : '新增收货地址'" width="460px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="84px">
      <el-form-item label="收货人" prop="receiver">
        <el-input v-model="form.receiver" placeholder="不超过 32 个字符" />
      </el-form-item>
      <el-form-item label="联系电话" prop="phone">
        <el-input v-model="form.phone" placeholder="11 位手机号" maxlength="11" />
      </el-form-item>
      <el-form-item label="省份" prop="province">
        <el-select
          v-model="form.province"
          filterable
          placeholder="选择或输入省份"
          class="area-select"
          @change="onProvinceChange"
        >
          <el-option v-for="item in provinces" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="城市" prop="city">
        <el-select
          v-model="form.city"
          filterable
          placeholder="选择或输入城市"
          class="area-select"
          :disabled="!form.province"
          @change="onCityChange"
        >
          <el-option v-for="item in cities" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="区/县" prop="district">
        <el-select
          v-model="form.district"
          filterable
          placeholder="选择或输入区/县"
          class="area-select"
          :disabled="!form.city"
        >
          <el-option v-for="item in districts" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="详细地址" prop="detail">
        <el-input v-model="form.detail" type="textarea" :rows="2" placeholder="街道、门牌号等" />
      </el-form-item>
      <el-form-item label="设为默认">
        <el-switch v-model="form.isDefault" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.area-select {
  width: 100%;
}
</style>
