<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { changePassword } from '@/api/user'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const STRONG_PASSWORD = /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!STRONG_PASSWORD.test(value || '')) {
          callback(new Error('新密码至少 8 位，且需同时包含字母和数字'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再输入一次新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await changePassword(form.oldPassword, form.newPassword)
    // JWT 是无状态的，旧 token 在过期前技术上仍有效 —— 所以由前端主动登出，
    // 让用户用新密码重新拿一个 token
    userStore.logout()
    ElMessage.success('密码已修改，请用新密码重新登录')
    router.replace('/login')
  } catch {
    // 旧密码错 / 新旧相同等，拦截器已提示；留在本页让用户重试
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="pwd-page">
    <div class="block">
      <h2 class="block-title">修改密码</h2>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="94px" class="pwd-form">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password placeholder="至少 8 位，含字母和数字" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>

      <p class="hint">修改成功后需要用新密码重新登录。</p>

      <button type="button" class="save-btn" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '修改密码' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.block {
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 20px 22px;
}

.block-title {
  margin: 0 0 18px;
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text);
}

.pwd-form {
  max-width: 380px;
}

.hint {
  margin: 4px 0 16px;
  font-size: 12.5px;
  color: var(--c-text-3);
}

.save-btn {
  padding: 11px 40px;
  font-size: 14px;
  font-family: inherit;
  color: #fff;
  background: var(--c-pink);
  border: none;
  border-radius: var(--radius-btn);
  cursor: pointer;
  transition: background 0.15s;
}

.save-btn:hover:not(:disabled) {
  background: var(--c-pink-hover);
}

.save-btn:disabled {
  background: var(--c-text-3);
  cursor: not-allowed;
}
</style>
