<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import ImageUpload from '@/components/ImageUpload.vue'
import { applyMerchant, fetchMerchantApplication } from '@/api/merchant'
import { useUserStore } from '@/stores/user'
import type { MerchantView } from '@/api/types'

const router = useRouter()
const userStore = useUserStore()

const application = ref<MerchantView | null>(null)
const loading = ref(true)
const submitting = ref(false)
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

const status = computed(() => application.value?.status ?? '')
/** 没有申请、或被驳回后可以填表 */
const showForm = computed(() => !application.value || status.value === 'REJECTED')

async function load() {
  loading.value = true
  try {
    application.value = await fetchMerchantApplication()
    if (application.value) {
      Object.assign(form, {
        name: application.value.name,
        logo: application.value.logo,
        description: application.value.description,
        contactName: application.value.contactName,
        contactPhone: application.value.contactPhone,
      })
    }
  } catch {
    application.value = null
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    application.value = await applyMerchant({
      name: form.name.trim(),
      logo: form.logo.trim(),
      description: form.description.trim(),
      contactName: form.contactName.trim(),
      contactPhone: form.contactPhone.trim(),
    })
    ElMessage.success(application.value.status === 'PENDING' ? '申请已提交，等待平台审核' : '已重新提交')
    // 提交完成后回主页；审核进度可再从用户面板的「商家入驻」进入查看
    router.replace('/')
  } finally {
    submitting.value = false
  }
}

function reLogin() {
  userStore.logout()
  router.push({ path: '/login', query: { redirect: '/merchant/profile' } })
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="apply-page">
    <section class="block">
      <h1 class="title">商家入驻</h1>
      <p class="subtitle">提交店铺资料，平台审核通过后即可进入商家后台管理商品与订单。</p>

      <el-alert
        v-if="status === 'PENDING'"
        type="warning"
        :closable="false"
        show-icon
        title="入驻申请审核中，请等待平台处理"
        class="tip"
      />

      <el-alert
        v-if="status === 'REJECTED'"
        type="error"
        :closable="false"
        show-icon
        :title="`申请被驳回：${application?.rejectReason || '资料不符合要求'}`"
        class="tip"
      />

      <el-alert
        v-if="status === 'DISABLED'"
        type="error"
        :closable="false"
        show-icon
        title="店铺已被平台禁用，请联系平台管理员"
        class="tip"
      />

      <template v-if="status === 'ACTIVE'">
        <el-alert
          type="success"
          :closable="false"
          show-icon
          title="店铺已通过审核"
          class="tip"
        />
        <div class="active-actions">
          <el-button
            v-if="userStore.isMerchant"
            type="primary"
            @click="router.push('/merchant/profile')"
          >
            进入商家后台
          </el-button>
          <template v-else>
            <p class="relogin-tip">审核通过后需要重新登录，才能拿到商家身份。</p>
            <el-button type="primary" @click="reLogin">重新登录</el-button>
          </template>
        </div>
      </template>

      <el-form v-if="showForm" ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="店铺名" prop="name">
          <el-input v-model="form.name" maxlength="64" placeholder="如：小张数码店" />
        </el-form-item>
        <el-form-item label="Logo">
          <ImageUpload v-model="form.logo" apply />
        </el-form-item>
        <el-form-item label="店铺简介">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="选填，介绍你的店铺"
          />
        </el-form-item>
        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="form.contactName" maxlength="32" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" maxlength="11" placeholder="11 位手机号" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">
            {{ status === 'REJECTED' ? '重新提交申请' : '提交入驻申请' }}
          </el-button>
        </el-form-item>
      </el-form>
    </section>
  </div>
</template>

<style scoped>
.apply-page {
  max-width: 760px;
  margin: 0 auto;
  padding: 32px 24px;
}

.block {
  padding: 28px 30px;
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--c-text);
}

.subtitle {
  margin: 8px 0 20px;
  font-size: 13px;
  color: var(--c-text-2);
}

.tip {
  margin-bottom: 20px;
}

.active-actions {
  margin-top: 8px;
}

.relogin-tip {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--c-text-2);
}
</style>
