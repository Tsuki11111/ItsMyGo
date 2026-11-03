<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import termsMd from '../../../../docs/policies/terms.md?raw'
import privacyMd from '../../../../docs/policies/privacy.md?raw'
import refundMd from '../../../../docs/policies/refund.md?raw'
import merchantMd from '../../../../docs/policies/merchant.md?raw'

/**
 * 四份平台政策共用一个页面组件。
 *
 * 正文直接引用仓库里的 `docs/policies/*.md`（`?raw`），与仓库正式文本是同一份，
 * 避免「页面一套、文档一套」两处漂移。改政策只改 md。
 */
const DOCS: Record<string, { title: string; body: string }> = {
  terms: { title: '用户协议', body: termsMd },
  privacy: { title: '隐私政策', body: privacyMd },
  refund: { title: '退换货与售后政策', body: refundMd },
  merchant: { title: '商家入驻与经营规范', body: merchantMd },
}

const route = useRoute()
const policy = computed(() => DOCS[String(route.meta.policy ?? 'terms')] ?? DOCS.terms)
const html = computed(() =>
  DOMPurify.sanitize(marked.parse(policy.value.body, {
    async: false,
    gfm: true,
    breaks: true,
  }) as string),
)
</script>

<template>
  <div class="policy-page">
    <el-breadcrumb separator="/" class="crumb">
      <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>{{ policy.title }}</el-breadcrumb-item>
    </el-breadcrumb>

    <article class="policy-card">
      <div class="policy-body" v-html="html"></div>
    </article>
  </div>
</template>

<style scoped>
.policy-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 8px 0 32px;
}

.crumb {
  margin-bottom: 14px;
}

.policy-card {
  padding: 28px 32px;
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.policy-body {
  color: var(--c-text-2);
  font-size: 14px;
  line-height: 1.9;
}

.policy-body :deep(h1) {
  margin: 0 0 18px;
  color: var(--c-text);
  font-size: 22px;
  line-height: 1.4;
}

.policy-body :deep(h2) {
  margin: 28px 0 10px;
  padding-bottom: 6px;
  color: var(--c-text);
  font-size: 16px;
  border-bottom: 1px solid var(--c-border);
}

.policy-body :deep(h3) {
  margin: 18px 0 6px;
  color: var(--c-text);
  font-size: 14px;
}

.policy-body :deep(p) {
  margin: 8px 0;
}

.policy-body :deep(ol),
.policy-body :deep(ul) {
  margin: 8px 0;
  padding-left: 22px;
}

.policy-body :deep(li) {
  margin: 4px 0;
}

.policy-body :deep(blockquote) {
  margin: 10px 0;
  padding: 10px 14px;
  color: var(--c-text-3);
  background: var(--c-pink-tint);
  border-left: 3px solid var(--c-pink);
  border-radius: 0 6px 6px 0;
}

.policy-body :deep(table) {
  width: 100%;
  margin: 12px 0;
  border-collapse: collapse;
  font-size: 13px;
}

.policy-body :deep(th),
.policy-body :deep(td) {
  padding: 8px 10px;
  text-align: left;
  border: 1px solid var(--c-border);
}

.policy-body :deep(th) {
  color: var(--c-text);
  background: #fafafa;
  font-weight: 600;
}

.policy-body :deep(code) {
  padding: 1px 5px;
  background: var(--c-pink-tint);
  border-radius: 4px;
  font-family: var(--font-mono, ui-monospace, monospace);
  font-size: 12px;
}

.policy-body :deep(hr) {
  margin: 24px 0;
  border: 0;
  border-top: 1px solid var(--c-border);
}

@media (max-width: 640px) {
  .policy-card {
    padding: 20px 16px;
  }

  .policy-body :deep(table) {
    display: block;
    overflow-x: auto;
  }
}
</style>