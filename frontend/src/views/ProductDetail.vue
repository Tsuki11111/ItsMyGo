<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ProductImage from '@/components/ProductImage.vue'
import { fetchProduct, fetchProducts, fetchStock } from '@/api/product'
import { addToCart } from '@/api/cart'
import { useUserStore } from '@/stores/user'
import type { Product } from '@/api/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const product = ref<Product | null>(null)
/** 实时库存 —— 来自独立接口，不是详情里的 stock 字段 */
const stock = ref(0)
const quantity = ref(1)
const related = ref<Product[]>([])
const loading = ref(true)
const adding = ref(false)
const notFound = ref(false)

/** 库存为 0 时输入框仍要有个合法的 max，否则 el-input-number 会报警告 */
const maxQuantity = computed(() => Math.max(stock.value, 1))
const soldOut = computed(() => stock.value === 0)

function currentId(): number {
  return Number(route.params.id)
}

async function load() {
  const id = currentId()
  loading.value = true
  notFound.value = false
  quantity.value = 1
  try {
    product.value = await fetchProduct(id)
    stock.value = (await fetchStock(id)).stock
  } catch {
    // 404（不存在 / 已下架）已经在拦截器里提示过一次，这里只负责渲染空状态
    product.value = null
    notFound.value = true
    return
  } finally {
    loading.value = false
  }

  loadRelated(id)
}

/** 同类商品：同分类里取几个，排除自己。后端只有分页接口，就在内存里筛 */
async function loadRelated(id: number) {
  if (!product.value) return
  try {
    const result = await fetchProducts({ categoryId: product.value.categoryId, size: 8 })
    related.value = result.records.filter((p) => p.id !== id).slice(0, 4)
  } catch {
    related.value = []
  }
}

async function onAdd() {
  if (!product.value) return

  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    // 带上回跳地址，登录完能回到这件商品
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  adding.value = true
  try {
    await addToCart(product.value.id, quantity.value)
    ElMessage.success(`已加入购物车 ${quantity.value} 件`)
  } catch {
    // 库存不足 / 未登录过期等，拦截器已提示；重新拉一次库存，让页面上的数字回到真相
    await refreshStock()
  } finally {
    adding.value = false
  }
}

/** 立即购买：跳过购物车，直接带着这件商品去结算 —— 和主流电商语义一致 */
function onBuyNow() {
  if (!product.value) return

  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  router.push({
    path: '/checkout',
    query: { productId: product.value.id, quantity: quantity.value },
  })
}

async function refreshStock() {
  if (!product.value) return
  try {
    stock.value = (await fetchStock(product.value.id)).stock
  } catch {
    // 拿不到就保持原值，下一次操作还会再拉
  }
}

watch(() => route.params.id, load)
onMounted(load)
</script>

<template>
  <div v-loading="loading" class="detail-page">
    <el-empty v-if="notFound" description="商品不存在或已下架">
      <el-button type="primary" @click="router.push('/')">回到首页</el-button>
    </el-empty>

    <template v-else-if="product">
      <el-breadcrumb separator="/" class="crumb">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>{{ product.name }}</el-breadcrumb-item>
      </el-breadcrumb>

      <!-- ── 主区：左图 右信息 ── -->
      <section class="main">
        <div class="gallery">
          <ProductImage :src="product.mainImage" :name="product.name" :id="product.id" />
        </div>

        <div class="info">
          <h1 class="name">{{ product.name }}</h1>
          <p v-if="product.subtitle" class="subtitle">{{ product.subtitle }}</p>

          <!-- 价格区：粉色允许出现的三个位置之一 -->
          <div class="price-box">
            <span class="price">
              <span class="price-symbol">¥</span>
              <span class="price-num">{{ product.price.toFixed(2) }}</span>
            </span>
            <span class="sales">已售 {{ product.sales }} 件</span>
          </div>

          <div class="row">
            <span class="row-label">库存</span>
            <span class="row-value" :class="{ 'is-empty': soldOut }">
              {{ soldOut ? '暂时缺货' : `${stock} 件` }}
            </span>
          </div>

          <div class="row">
            <span class="row-label">数量</span>
            <el-input-number
              v-model="quantity"
              :min="1"
              :max="maxQuantity"
              :disabled="soldOut"
              size="default"
            />
          </div>

          <div class="buy-row">
            <button class="buy-btn ghost" :disabled="soldOut || adding" @click="onAdd">
              加入购物车
            </button>
            <button class="buy-btn" :disabled="soldOut || adding" @click="onBuyNow">
              {{ soldOut ? '已售罄' : '立即购买' }}
            </button>
          </div>

          <ul class="promise">
            <li>正品保障</li>
            <li>七天无理由退换</li>
            <li>付款后 24 小时内发货</li>
          </ul>
        </div>
      </section>

      <!-- ── 商品详情 ── -->
      <section class="block">
        <h2 class="block-title">商品详情</h2>
        <p v-if="product.detail" class="detail-text">{{ product.detail }}</p>
        <p v-else class="detail-blank">这件商品还没有填写详细介绍。</p>
      </section>

      <!-- ── 同类商品 ── -->
      <section v-if="related.length" class="block">
        <h2 class="block-title">同类商品</h2>
        <div class="rel-grid">
          <RouterLink
            v-for="item in related"
            :key="item.id"
            :to="`/product/${item.id}`"
            class="rel-card"
          >
            <div class="rel-thumb">
              <ProductImage :src="item.mainImage" :name="item.name" :id="item.id" />
            </div>
            <div class="rel-info">
              <p class="rel-name">{{ item.name }}</p>
              <p class="price">
                <span class="price-symbol">¥</span>
                <span class="price-num">{{ item.price.toFixed(2) }}</span>
              </p>
            </div>
          </RouterLink>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.crumb {
  margin-bottom: 16px;
}

/* ── 主区 ── */
.main {
  display: grid;
  grid-template-columns: 420px 1fr;
  gap: 32px;
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 24px;
}

.gallery {
  height: 420px;
  border-radius: var(--radius-card);
  overflow: hidden;
  background: var(--c-pink-tint);
  /* 无图占位的首字字号 —— 详情页图大，字也得跟着大 */
  font-size: 96px;
}

.info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.name {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--c-text);
}

.subtitle {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--c-text-2);
}

/* 价格区 —— 浅粉做面，深粉做点 */
.price-box {
  display: flex;
  align-items: baseline;
  gap: 16px;
  margin-top: 20px;
  padding: 16px 20px;
  background: var(--c-pink-tint);
  border-radius: var(--radius-card);
}

.price {
  color: var(--c-pink);
  line-height: 1;
}

.price-symbol {
  font-size: 16px;
  font-weight: 600;
  margin-right: 2px;
}

.price-num {
  font-size: 30px;
}

.sales {
  font-size: 12px;
  color: var(--c-text-3);
}

.row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 18px;
}

.row-label {
  width: 40px;
  font-size: 13px;
  color: var(--c-text-2);
}

.row-value {
  font-size: 14px;
  color: var(--c-text);
}

.row-value.is-empty {
  color: var(--c-text-3);
}

/* 两个都是主操作，但「立即购买」更主动 —— 一实心一描边拉开层级 */
.buy-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 26px;
}

.buy-btn {
  padding: 13px 0;
  font-size: 15px;
  font-family: inherit;
  color: #fff;
  background: var(--c-pink);
  /* 透明边框是为了两个按钮高度完全一致 */
  border: 1px solid transparent;
  border-radius: var(--radius-btn);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.buy-btn:hover:not(:disabled) {
  background: var(--c-pink-hover);
}

.buy-btn.ghost {
  color: var(--c-pink);
  background: #fff;
  border-color: var(--c-pink);
}

.buy-btn.ghost:hover:not(:disabled) {
  background: var(--c-pink-tint);
}

.buy-btn:disabled {
  color: #fff;
  background: var(--c-text-3);
  border-color: transparent;
  cursor: not-allowed;
}

.buy-btn.ghost:disabled {
  color: var(--c-text-3);
  background: #fff;
  border-color: var(--c-border);
}

.promise {
  display: flex;
  gap: 20px;
  list-style: none;
  margin: 18px 0 0;
  padding: 0;
  font-size: 12px;
  color: var(--c-text-3);
}

.promise li::before {
  content: '·';
  margin-right: 6px;
}

/* ── 下方区块 ── */
.block {
  margin-top: 24px;
  padding: 24px;
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.block-title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 600;
  color: var(--c-text);
}

.detail-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.9;
  color: var(--c-text-2);
  /* 详情里换行是有意义的，别让 HTML 折叠掉 */
  white-space: pre-wrap;
}

.detail-blank {
  margin: 0;
  font-size: 13px;
  color: var(--c-text-3);
}

/* ── 同类商品 ── */
.rel-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 18px;
}

.rel-card {
  border: 1px solid var(--c-border);
  border-radius: var(--radius-card);
  overflow: hidden;
  text-decoration: none;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.rel-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-card-hover);
}

.rel-thumb {
  height: 140px;
  background: var(--c-pink-tint);
  font-size: 36px;
}

.rel-info {
  padding: 10px 12px 12px;
}

.rel-name {
  margin: 0;
  font-size: 13px;
  line-height: 1.45;
  color: var(--c-text);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.rel-info .price {
  margin: 8px 0 0;
}

.rel-info .price-num {
  font-size: 17px;
}

/* ── 窄屏 ── */
@media (max-width: 900px) {
  .main {
    grid-template-columns: 1fr;
    gap: 20px;
  }
  .gallery {
    height: 300px;
    font-size: 64px;
  }
  .rel-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
