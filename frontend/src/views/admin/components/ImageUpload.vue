<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { uploadImage } from '@/api/admin'
import { uploadMerchantImage } from '@/api/merchant'

const props = defineProps<{ modelValue: string; compact?: boolean; merchant?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const uploading = ref(false)

/**
 * 自定义上传：el-upload 默认的 action 会绕过 axios，带不上 JWT，
 * 所以走统一的 axios 封装（和头像上传同一套做法）。
 */
async function onUpload(options: UploadRequestOptions) {
  uploading.value = true
  try {
    const url = props.merchant
      ? await uploadMerchantImage(options.file)
      : await uploadImage(options.file)
    emit('update:modelValue', url)
    ElMessage.success('图片已上传')
  } catch {
    // 类型/大小不对等，拦截器已提示
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="image-upload" :class="{ compact }">
    <div v-if="modelValue" class="preview">
      <img :src="modelValue" alt="已上传图片" />
      <div class="preview-actions">
        <el-upload
          :show-file-list="false"
          :http-request="onUpload"
          accept="image/jpeg,image/png,image/gif,image/webp"
        >
          <el-button size="small" :loading="uploading">更换</el-button>
        </el-upload>
        <el-button size="small" @click="emit('update:modelValue', '')">移除</el-button>
      </div>
    </div>

    <el-upload
      v-else
      :show-file-list="false"
      :http-request="onUpload"
      accept="image/jpeg,image/png,image/gif,image/webp"
    >
      <el-button :loading="uploading">{{ uploading ? '上传中…' : '上传图片' }}</el-button>
    </el-upload>
  </div>
</template>

<style scoped>
.preview {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.preview img {
  width: 120px;
  height: 120px;
  object-fit: cover;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.preview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.compact .preview img {
  width: 56px;
  height: 56px;
}

.compact .preview {
  gap: 8px;
}

.compact .preview-actions {
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}
</style>
