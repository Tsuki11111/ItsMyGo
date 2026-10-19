<script setup lang="ts">
import { onMounted, ref } from 'vue'
import ProductImage from '@/components/ProductImage.vue'
import { fetchMyReviews } from '@/api/review'
import type { MyReviewView } from '@/api/types'

const items = ref<MyReviewView[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await fetchMyReviews(page.value, size)
    items.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function onPageChange(next: number) {
  page.value = next
  load()
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>

<template>
  <div class="block">
    <h2 class="block-title">我的评价</h2>

    <div v-loading="loading" class="list">
      <el-empty v-if="!loading && items.length === 0" description="还没有发表过评价" />

      <article v-for="item in items" :key="item.id" class="review-card">
        <div class="product">
          <div class="thumb">
            <ProductImage :src="item.productImage" :name="item.productName" :id="item.productId" />
          </div>
          <div class="product-info">
            <RouterLink :to="`/product/${item.productId}`" class="product-name">
              {{ item.productName }}
            </RouterLink>
            <RouterLink
              v-if="item.merchantId"
              :to="`/shop/${item.merchantId}`"
              class="shop-link"
            >
              {{ item.merchantName }}
            </RouterLink>
            <el-rate :model-value="item.rating" disabled size="small" />
          </div>
          <el-tag :type="item.status === 1 ? 'success' : 'info'" size="small">
            {{ item.status === 1 ? '展示中' : '已隐藏' }}
          </el-tag>
        </div>
        <p v-if="item.content" class="content">{{ item.content }}</p>
        <p v-else class="content blank">（未填写文字评价）</p>
        <p class="time">{{ formatTime(item.createdAt) }}</p>
      </article>
    </div>

    <div v-if="total > size" class="pager">
      <el-pagination
        layout="prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        background
        @current-change="onPageChange"
      />
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
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 600;
  color: var(--c-text);
}

.list {
  min-height: 120px;
}

.review-card {
  padding: 14px 0;
  border-bottom: 1px solid var(--c-border);
}

.review-card:last-child {
  border-bottom: none;
}

.product {
  display: flex;
  align-items: center;
  gap: 12px;
}

.thumb {
  width: 48px;
  height: 48px;
  flex: none;
  border-radius: 6px;
  overflow: hidden;
}

.product-info {
  flex: 1;
  min-width: 0;
}

.product-name {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shop-link {
  display: inline-block;
  margin-bottom: 4px;
  font-size: 11px;
  color: var(--c-pink);
  text-decoration: none;
}

.content {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--c-text-2);
  white-space: pre-wrap;
}

.content.blank {
  color: var(--c-text-3);
}

.time {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--c-text-3);
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}
</style>
