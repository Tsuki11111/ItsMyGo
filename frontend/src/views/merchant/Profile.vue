<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import ImageUpload from '@/components/ImageUpload.vue'
import { fetchMerchantProfile, updateMerchantProfile } from '@/api/merchant'
import type { MerchantView } from '@/api/types'

const profile = ref<MerchantView | null>(null)
const loading = ref(true)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  logo: '',
  description: '',
  contactName: '',
  contactPhone: '',
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入店铺名', trigger: 'blur' },
    { max: 64, message: '店铺名最长 64 个字符', trigger: 'blur' },
  ],
  contactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  contactPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的 11 位手机号', trigger: 'blur' },
  ],
}

async function load() {
  loading.value = true
  try {
    profile.value = await fetchMerchantProfile()
    Object.assign(form, {
      name: profile.value.name,
      logo: profile.value.logo,
      description: profile.value.description,
      contactName: profile.value.contactName,
      contactPhone: profile.value.contactPhone,
    })
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    profile.value = await updateMerchantProfile({
      name: form.name.trim(),
      logo: form.logo.trim(),
      description: form.description.trim(),
      contactName: form.contactName.trim(),
      contactPhone: form.contactPhone.trim(),
    })
    ElMessage.success('店铺资料已保存')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="block">
    <div class="head">
      <h2 class="block-title">店铺资料</h2>
      <el-tag v-if="profile" type="success" size="small">{{ profile.status }}</el-tag>
    </div>

    <el-form v-loading="loading" ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="店铺 ID">
        <span class="readonly">#{{ profile?.id ?? '—' }}</span>
      </el-form-item>
      <el-form-item label="店铺名" prop="name">
        <el-input v-model="form.name" maxlength="64" />
      </el-form-item>
      <el-form-item label="Logo">
        <ImageUpload v-model="form.logo" merchant />
      </el-form-item>
      <el-form-item label="店铺简介">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="联系人" prop="contactName">
        <el-input v-model="form.contactName" maxlength="32" />
      </el-form-item>
      <el-form-item label="联系电话" prop="contactPhone">
        <el-input v-model="form.contactPhone" maxlength="11" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.block {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.block-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.readonly {
  color: var(--el-text-color-secondary);
}
</style>
