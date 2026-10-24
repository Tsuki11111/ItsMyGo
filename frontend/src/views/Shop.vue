<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProductImage from '@/components/ProductImage.vue'
import { fetchShop } from '@/api/shop'
import { fetchProducts } from '@/api/product'
import { startConversation } from '@/api/chat'
import { useUserStore } from '@/stores/user'
import type { MerchantView, Product } from '@/api/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const shop = ref<MerchantView | null>(null)
const products = ref<Product[]>([])
const total = ref(0)
const page = ref(1)
const size = 12
const loading = ref(true)
const notFound = ref(false)
const startingChat = ref(false)

function shopId(): number {
  return Number(route.params.id)
}

async function loadShop() {
  try {
    shop.value = await fetchShop(shopId())
  } catch {
    shop.value = null
    notFound.value = true
  }
}

async function loadProducts() {
  loading.value = true
  try {
    const result = await fetchProducts({ merchantId: shopId(), page: page.value, size })
    products.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

async function load() {
  loading.value = true
  notFound.value = false
  page.value = 1
  await loadShop()
  if (!notFound.value) {
    await loadProducts()
  } else {
    loading.value = false
  }
}

function onPageChange(next: number) {
  page.value = next
  loadProducts()
}

async function contactSeller() {
  if (!shop.value) return
  if (!userStore.isLoggedIn) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  startingChat.value = true
  try {
    const conversation = await startConversation({ merchantId: shop.value.id })
    await router.push(`/user/messages/${conversation.id}`)
  } catch {
    // 拦截器已提示
  } finally {
    startingChat.value = false
  }
}

watch(() => route.params.id, load)
onMounted(load)
</script>

<template>
  <div v-loading="loading" class="shop-page">
    <el-empty v-if="notFound" description="店铺不存在或已停用">
      <el-button type="primary" @click="router.push('/')">回到首页</el-button>
    </el-empty>

    <template v-else-if="shop">
      <section class="shop-head">
        <div class="logo">
          <ProductImage :src="shop.logo" :name="shop.name" :id="shop.id" />
        </div>
        <div class="info">
          <h1 class="name">{{ shop.name }}</h1>
          <p class="desc">{{ shop.description || '这家店还没有填写简介' }}</p>
        </div>
        <el-tag type="success" size="small">营业中</el-tag>
        <el-button
          v-if="!userStore.isAdmin"
          type="primary"
          plain
          size="small"
          :loading="startingChat"
          @click="contactSeller"
        >
          联系商家
        </el-button>
      </section>

      <section class="block">
        <div class="block-head">
          <h2 class="block-title">店铺商品</h2>
          <span class="count">共 {{ total }} 件</span>
        </div>

        <el-empty v-if="!loading && products.length === 0" description="店铺还没有上架商品" />

        <div v-else class="grid">
          <article
            v-for="item in products"
            :key="item.id"
            class="card"
            @click="router.push(`/product/${item.id}`)"
          >
            <div class="thumb">
              <ProductImage :src="item.thumbImage || item.mainImage" :name="item.name" :id="item.id" />
            </div>
            <p class="p-name">{{ item.name }}</p>
            <p v-if="item.subtitle" class="p-sub">{{ item.subtitle }}</p>
            <p class="price">
              <span class="price-symbol">¥</span>
              <span class="price-num">{{ item.price.toFixed(2) }}</span>
              <span v-if="item.hasSpecs === 1" class="from">起</span>
            </p>
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
      </section>
    </template>
  </div>
</template>

<style scoped>
.shop-page {
  max-width: 1200px;
  margin: 0 auto;
}

.shop-head {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 22px 24px;
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.logo {
  width: 72px;
  height: 72px;
  flex: none;
  border-radius: 16px;
  overflow: hidden;
  background: var(--c-pink-tint);
  font-size: 26px;
}

.info {
  flex: 1;
  min-width: 0;
}

.name {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--c-text);
}

.desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--c-text-2);
}

.block {
  margin-top: 18px;
  padding: 20px 22px;
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.block-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 16px;
}

.block-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--c-text);
}

.count {
  font-size: 12px;
  color: var(--c-text-3);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 16px;
}

.card {
  padding: 10px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-card);
  cursor: pointer;
  transition: box-shadow 0.18s;
}

.card:hover {
  box-shadow: var(--shadow-card-hover);
}

.thumb {
  width: 100%;
  aspect-ratio: 1 / 1;
  border-radius: var(--radius-tag);
  overflow: hidden;
  background: var(--c-pink-tint);
  font-size: 30px;
}

.p-name {
  margin: 10px 0 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.p-sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price {
  margin: 8px 0 0;
  color: var(--c-pink);
}

.from {
  margin-left: 2px;
  font-size: 11px;
  color: var(--c-text-2);
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}
</style>
