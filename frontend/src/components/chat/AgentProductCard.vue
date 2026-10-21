<script setup lang="ts">
import { computed } from 'vue'
import type { AgentProductCard } from '@/api/types'

const props = defineProps<{ product: AgentProductCard }>()
const emit = defineEmits<{ buy: [product: AgentProductCard] }>()

const hasStock = computed(() => Number(props.product.stock || 0) > 0)
</script>

<template>
  <article class="product-card">
    <router-link :to="`/product/${product.productId}`" class="image-link">
      <img
        v-if="product.image"
        :src="product.image"
        :alt="product.name"
        class="product-image"
        loading="lazy"
      />
      <div v-else class="product-image placeholder">暂无图片</div>
    </router-link>

    <div class="product-body">
      <router-link :to="`/product/${product.productId}`" class="product-name">
        {{ product.name }}
      </router-link>

      <div class="price-row">
        <span class="price">¥{{ product.price }}</span>
        <span class="stock" :class="{ none: !hasStock }">
          {{ hasStock ? `库存 ${product.stock}` : '暂时无货' }}
        </span>
      </div>

      <p class="meta">
        <span v-if="product.merchantName">{{ product.merchantName }}</span>
        <span v-if="product.sales && product.sales !== '0'">销量 {{ product.sales }}</span>
        <span v-if="product.rating">评分 {{ product.rating }}</span>
      </p>
      <p v-if="product.specText" class="spec">{{ product.specText }}</p>

      <div class="actions">
        <router-link :to="`/product/${product.productId}`" class="detail-link">
          查看详情
        </router-link>
        <el-button size="small" type="primary" :disabled="!hasStock" @click="emit('buy', product)">
          让买小购下单
        </el-button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.product-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--c-border);
  border-radius: 10px;
}

.image-link {
  display: block;
  flex: none;
}

.product-image {
  display: block;
  width: 100%;
  height: 150px;
  object-fit: cover;
  background: #fafafa;
}

.product-image.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--c-text-3);
}

.product-body {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  padding: 9px 10px 10px;
}

.product-name {
  display: -webkit-box;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.45;
  color: var(--c-text);
  text-decoration: none;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.product-name:hover {
  color: var(--c-pink);
}

.price-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
}

.price {
  font-size: 16px;
  font-weight: 700;
  color: var(--c-pink);
}

.stock {
  flex: none;
  font-size: 11px;
  color: var(--c-text-3);
}

.stock.none {
  color: #d9534f;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  margin: 0;
  font-size: 11px;
  color: var(--c-text-3);
}

.spec {
  margin: 0;
  overflow: hidden;
  font-size: 11px;
  color: var(--c-text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-top: 3px;
}

.detail-link {
  font-size: 12px;
  color: var(--c-pink);
  text-decoration: none;
}

.actions :deep(.el-button span) {
  margin-left: 0;
}
</style>
