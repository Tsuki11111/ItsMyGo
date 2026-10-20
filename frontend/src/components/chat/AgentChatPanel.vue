<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Promotion, Service } from '@element-plus/icons-vue'
import type { ChatScope } from '@/api/types'

const props = defineProps<{ mode: ChatScope }>()
const emit = defineEmits<{ back: [] }>()

const intro = computed(() => {
  if (props.mode === 'CUSTOMER') {
    return '可以帮你查询商品信息、订单进度和售后问题。'
  }
  return '可以帮你处理店铺经营、订单履约和平台规则相关的问题。'
})

const suggestions = computed(() =>
  props.mode === 'CUSTOMER'
    ? ['这款商品怎么安装？', '我的订单到哪了？', '怎么申请退款？']
    : ['今天的订单怎么处理？', '退款审核规则是什么？', '店铺数据在哪里看？'],
)

function onSuggest() {
  ElMessage.info('智能客服正在接入中，敬请期待')
}
</script>

<template>
  <section class="agent-panel">
    <header class="window-head">
      <button type="button" class="back-btn" title="返回会话列表" @click="emit('back')">
        <el-icon><ArrowLeft /></el-icon>
      </button>

      <span class="avatar">
        <el-icon><Service /></el-icon>
      </span>
      <div class="who">
        <p class="name">买小购</p>
        <p class="status">智能客服 · 接入中</p>
      </div>
      <el-tag size="small" type="info" effect="plain">AI</el-tag>
    </header>

    <div class="messages">
      <div class="welcome">
        <p class="greeting">你好，我是买小购。</p>
        <p>{{ intro }}</p>
        <p class="pending">智能客服正在接入中，很快就能和我对话了。</p>
      </div>

      <div class="suggestions">
        <p class="tip">你可以问：</p>
        <div class="chips">
          <button
            v-for="item in suggestions"
            :key="item"
            type="button"
            class="chip"
            @click="onSuggest"
          >
            {{ item }}
          </button>
        </div>
      </div>
    </div>

    <div class="composer">
      <el-input
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 4 }"
        resize="none"
        disabled
        placeholder="智能客服接入中，暂时无法发送消息"
      />
      <el-button type="primary" disabled>
        <el-icon><Promotion /></el-icon>
        <span>发送</span>
      </el-button>
    </div>
  </section>
</template>

<style scoped>
.agent-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #fafafa;
}

.window-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
  padding: 10px 14px;
  background: #fff;
  border-bottom: 1px solid var(--c-border);
}

.back-btn {
  display: none;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--c-text-2);
  background: none;
  border: none;
  border-radius: 50%;
  cursor: pointer;
}

.back-btn:hover {
  background: var(--c-pink-tint);
  color: var(--c-pink);
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex: none;
  font-size: 18px;
  color: #fff;
  background: linear-gradient(135deg, var(--c-pink), #f39ab9);
  border-radius: 9px;
}

.who {
  flex: 1;
  min-width: 0;
}

.name {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
}

.status {
  margin: 2px 0 0;
  font-size: 11px;
  color: var(--c-text-3);
}

.messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px 16px;
}

.welcome {
  max-width: 520px;
  padding: 14px 16px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--c-text);
  background: #fff;
  border: 1px solid var(--c-border);
  border-radius: 12px;
}

.welcome p {
  margin: 0;
}

.greeting {
  margin-bottom: 6px !important;
  font-weight: 600;
}

.pending {
  margin-top: 8px !important;
  font-size: 12px;
  color: var(--c-text-3);
}

.suggestions {
  margin-top: 18px;
}

.tip {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--c-text-3);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  padding: 7px 12px;
  font-size: 12px;
  font-family: inherit;
  color: var(--c-pink);
  background: var(--c-pink-tint);
  border: none;
  border-radius: var(--radius-btn);
  cursor: pointer;
}

.chip:hover {
  background: var(--c-pink-soft);
}

.composer {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  flex: none;
  padding: 10px 14px 12px;
  background: #fff;
  border-top: 1px solid var(--c-border);
}

.composer .el-button span {
  margin-left: 4px;
}

@media (max-width: 820px) {
  .back-btn {
    display: inline-flex;
  }
}
</style>
