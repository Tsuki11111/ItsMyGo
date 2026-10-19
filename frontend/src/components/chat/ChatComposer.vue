<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { Picture, Promotion } from '@element-plus/icons-vue'
import { uploadChatImage } from '@/api/chat'
import type { ChatScope, ChatSendPayload } from '@/api/types'

const props = defineProps<{
  sending: boolean
  peerOnline: boolean
  mode: ChatScope
}>()

const emit = defineEmits<{ send: [payload: ChatSendPayload] }>()

const text = ref('')
const uploading = ref(false)

const placeholder = computed(() => {
  if (props.mode === 'CUSTOMER' && !props.peerOnline) {
    return '商家离线，可先留言，上线后会回复'
  }
  return '输入消息，Enter 发送，Shift + Enter 换行'
})

const canSend = computed(() => text.value.trim().length > 0 && !props.sending)

function submit() {
  if (!canSend.value) return
  emit('send', { type: 'TEXT', text: text.value.trim() })
  text.value = ''
}

async function onUpload(options: UploadRequestOptions) {
  uploading.value = true
  try {
    const url = await uploadChatImage(options.file)
    emit('send', { type: 'IMAGE', imageUrl: url })
    ElMessage.success('图片已发送')
  } catch {
    // 类型/大小不对等，拦截器已提示
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="composer">
    <p v-if="mode === 'CUSTOMER' && !peerOnline" class="offline-tip">
      商家当前离线，你的消息会保存为留言
    </p>

    <div class="input-row">
      <el-upload
        :show-file-list="false"
        :http-request="onUpload"
        accept="image/jpeg,image/png,image/gif,image/webp"
        class="image-btn"
      >
        <el-button circle :loading="uploading" :disabled="sending" title="发送图片">
          <el-icon><Picture /></el-icon>
        </el-button>
      </el-upload>

      <el-input
        v-model="text"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 5 }"
        :placeholder="placeholder"
        :disabled="sending"
        resize="none"
        @keydown.enter.exact.prevent="submit"
      />

      <el-button type="primary" :loading="sending" :disabled="!canSend" @click="submit">
        <el-icon><Promotion /></el-icon>
        <span>发送</span>
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.composer {
  padding: 10px 14px 12px;
  border-top: 1px solid var(--c-border);
  background: #fff;
}

.offline-tip {
  margin: 0 0 8px;
  font-size: 12px;
  color: #b88230;
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.image-btn {
  flex: none;
  line-height: 1;
}

:deep(.el-textarea__inner) {
  font-family: inherit;
  font-size: 14px;
}

.el-button span {
  margin-left: 4px;
}
</style>
