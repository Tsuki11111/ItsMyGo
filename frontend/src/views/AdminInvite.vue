<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Key } from '@element-plus/icons-vue'
import { acceptAdminInvitation } from '@/api/adminInvitation'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const token = ref(typeof route.query.token === 'string' ? route.query.token : '')
const submitting = ref(false)
const currentUser = computed(() => userStore.user?.username || '')

async function accept() {
  if (!token.value.trim()) {
    ElMessage.warning('请填写邀请令牌')
    return
  }
  submitting.value = true
  try {
    await acceptAdminInvitation(token.value.trim())
    userStore.logout()
    ElMessage.success('已开通管理员，请用新身份重新登录')
    router.replace('/login')
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="invite-page">
    <div class="invite-card">
      <el-icon class="invite-icon"><Key /></el-icon>
      <h1>接受管理员邀请</h1>
      <p class="desc">
        当前账号：<strong>{{ currentUser || '未登录' }}</strong>。
        邀请只能由被邀请的账号接受；接受后需要重新登录才会生效。
      </p>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="邀请令牌">
          <el-input v-model="token" placeholder="粘贴管理员发给你的邀请链接里的 token" />
        </el-form-item>
      </el-form>

      <el-button type="primary" :loading="submitting" class="accept-btn" @click="accept">
        接受邀请并开通管理员
      </el-button>
      <p class="hint">
        如果你不是被邀请的账号，请退出后登录对应账号再打开邀请链接。
      </p>
    </div>
  </div>
</template>

<style scoped>
.invite-page {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}

.invite-card {
  width: 420px;
  padding: 28px 30px;
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.invite-icon {
  display: block;
  margin: 0 auto 10px;
  font-size: 28px;
  color: var(--c-pink);
}

h1 {
  margin: 0;
  font-size: 19px;
  color: var(--c-text);
  text-align: center;
}

.desc {
  margin: 10px 0 18px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--c-text-2);
  text-align: center;
}

.accept-btn {
  width: 100%;
}

.hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
  line-height: 1.6;
}
</style>
