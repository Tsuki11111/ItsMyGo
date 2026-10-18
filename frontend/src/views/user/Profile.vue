<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, UploadRequestOptions } from 'element-plus'
import ProductImage from '@/components/ProductImage.vue'
import { fetchProfile, updateProfile, uploadAvatar } from '@/api/user'
import { useUserStore } from '@/stores/user'
import type { UserProfile } from '@/api/types'

const userStore = useUserStore()

const profile = ref<UserProfile | null>(null)
const loading = ref(true)
const saving = ref(false)
const uploading = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({ nickname: '', phone: '' })

const rules: FormRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 32, message: '昵称最长 32 个字符', trigger: 'blur' },
  ],
  phone: [{ max: 20, message: '手机号最长 20 个字符', trigger: 'blur' }],
}

onMounted(async () => {
  loading.value = true
  try {
    profile.value = await fetchProfile()
    form.nickname = profile.value.nickname
    form.phone = profile.value.phone
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
})

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const updated = await updateProfile({ nickname: form.nickname, phone: form.phone })
    profile.value = updated
    // 顶栏显示的是昵称，改完要立刻同步
    userStore.patchProfile({ nickname: updated.nickname, avatar: updated.avatar })
    ElMessage.success('资料已更新')
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

/**
 * 自定义上传：el-upload 默认的 action 会绕过 axios，带不上 JWT，
 * 所以这里用 http-request 自己发，走统一的 axios 封装。
 */
async function onUpload(options: UploadRequestOptions) {
  uploading.value = true
  try {
    const updated = await uploadAvatar(options.file)
    profile.value = updated
    userStore.patchProfile({ nickname: updated.nickname, avatar: updated.avatar })
    ElMessage.success('头像已更新')
  } catch {
    // 文件类型/大小不对等，拦截器已提示
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="profile-page">
    <div class="block">
      <h2 class="block-title">个人资料</h2>

      <div class="avatar-row">
        <div class="avatar">
          <ProductImage
            :src="profile?.avatar"
            :name="profile?.nickname || userStore.displayName"
            :id="profile?.id ?? 0"
          />
        </div>

        <div class="avatar-side">
          <p class="avatar-tip">支持 jpg / png / gif / webp，不超过 2MB</p>
          <el-upload
            :show-file-list="false"
            :http-request="onUpload"
            accept="image/jpeg,image/png,image/gif,image/webp"
          >
            <button type="button" class="ghost-btn" :disabled="uploading">
              {{ uploading ? '上传中…' : '更换头像' }}
            </button>
          </el-upload>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="84px" class="profile-form">
        <el-form-item label="用户名">
          <el-input :model-value="profile?.username" disabled />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="别人看到的名字" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="选填" />
        </el-form-item>
      </el-form>

      <button type="button" class="save-btn" :disabled="saving" @click="save">
        {{ saving ? '保存中…' : '保存修改' }}
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

.avatar-row {
  display: flex;
  align-items: center;
  gap: 20px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--c-border);
}

.avatar {
  width: 88px;
  height: 88px;
  flex: none;
  border-radius: 50%;
  overflow: hidden;
  background: var(--c-pink-tint);
  font-size: 34px;
}

.avatar-tip {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--c-text-3);
}

.ghost-btn {
  padding: 7px 16px;
  font-size: 13px;
  font-family: inherit;
  color: var(--c-pink);
  background: #fff;
  border: 1px solid var(--c-pink);
  border-radius: var(--radius-btn);
  cursor: pointer;
  transition: background 0.15s;
}

.ghost-btn:hover:not(:disabled) {
  background: var(--c-pink-tint);
}

.ghost-btn:disabled {
  color: var(--c-text-3);
  border-color: var(--c-border);
  cursor: not-allowed;
}

.profile-form {
  max-width: 420px;
  margin-top: 20px;
}

.save-btn {
  margin-top: 8px;
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
