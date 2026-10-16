<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { login, register } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const mode = ref<'login' | 'register'>('login')
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  username: '',
  password: '',
  nickname: '',
})

// 与后端 RegisterRequest 的约束保持一致（登录接口只要求非空，
// 但合法密码必然 ≥6 位，多校验一步不会挡住任何正常用户）
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度需在 3-32 个字符之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度需在 6-64 个字符之间', trigger: 'blur' },
  ],
  nickname: [{ max: 32, message: '昵称最长 32 个字符', trigger: 'blur' }],
}

function switchMode(next: 'login' | 'register') {
  mode.value = next
  formRef.value?.clearValidate()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const result =
      mode.value === 'login'
        ? await login(form.username, form.password)
        : await register(form.username, form.password, form.nickname || undefined)

    userStore.save(result)
    ElMessage.success(mode.value === 'login' ? '登录成功' : '注册成功')

    // 打回原处：从「加购」跳过来的，登录完直接回到那件商品
    const redirect = route.query.redirect
    router.push(typeof redirect === 'string' ? redirect : '/')
  } catch {
    // 用户名已存在 / 密码错误 / 被限流，拦截器已经提示过
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1 class="brand">一次买够</h1>
      <p class="slogan">电子设备 · 一次买够</p>

      <div class="tabs">
        <button
          type="button"
          class="tab"
          :class="{ active: mode === 'login' }"
          @click="switchMode('login')"
        >
          登录
        </button>
        <button
          type="button"
          class="tab"
          :class="{ active: mode === 'register' }"
          @click="switchMode('register')"
        >
          注册
        </button>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="3-32 个字符" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="至少 6 位"
            show-password
            @keyup.enter="submit"
          />
        </el-form-item>

        <el-form-item v-if="mode === 'register'" label="昵称（选填）" prop="nickname">
          <el-input
            v-model="form.nickname"
            placeholder="不填就用用户名"
            @keyup.enter="submit"
          />
        </el-form-item>

        <button type="button" class="submit-btn" :disabled="submitting" @click="submit">
          {{ submitting ? '请稍候…' : mode === 'login' ? '登录' : '注册并登录' }}
        </button>
      </el-form>

      <!-- 练手项目的演示账号，方便直接上手；真上线要拿掉 -->
      <p class="hint">演示账号：testuser / 123456</p>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}

.auth-card {
  width: 380px;
  padding: 32px 32px 24px;
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.brand {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--c-pink);
  text-align: center;
}

.slogan {
  margin: 6px 0 24px;
  font-size: 12px;
  color: var(--c-text-3);
  text-align: center;
}

/* 登录 / 注册切换 —— 选中态是粉色允许出现的位置之一 */
.tabs {
  display: flex;
  gap: 24px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--c-border);
}

.tab {
  padding: 0 0 10px;
  font-size: 15px;
  font-family: inherit;
  color: var(--c-text-2);
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}

.tab.active {
  color: var(--c-pink);
  border-bottom-color: var(--c-pink);
  font-weight: 600;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
  padding: 12px 0;
  font-size: 15px;
  font-family: inherit;
  color: #fff;
  background: var(--c-pink);
  border: none;
  border-radius: var(--radius-btn);
  cursor: pointer;
  transition: background 0.15s;
}

.submit-btn:hover:not(:disabled) {
  background: var(--c-pink-hover);
}

.submit-btn:disabled {
  background: var(--c-text-3);
  cursor: not-allowed;
}

.hint {
  margin: 20px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
  text-align: center;
}
</style>
