<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Star, StarFilled } from '@element-plus/icons-vue'
import ProductImage from '@/components/ProductImage.vue'
import ProductCommunity from '@/components/ProductCommunity.vue'
import { fetchProduct, fetchProducts, fetchStock } from '@/api/product'
import { fetchShop } from '@/api/shop'
import { startConversation } from '@/api/chat'
import { addToCart } from '@/api/cart'
import { addFavorite, fetchFavoriteStatus, removeFavorite } from '@/api/favorite'
import { recordFootprint } from '@/api/footprint'
import { useBadgeStore } from '@/stores/badge'
import { useUserStore } from '@/stores/user'
import type { MerchantView, Product, ProductSku } from '@/api/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const badgeStore = useBadgeStore()

const product = ref<Product | null>(null)
/** 规格定义。无规格商品是空数组 */
const specs = computed(() => product.value?.specs ?? [])
/** 选中的规格值：specId → valueId */
const selectedValues = ref<Record<number, number>>({})
const quantity = ref(1)
const related = ref<Product[]>([])
const loading = ref(true)
const adding = ref(false)
const notFound = ref(false)

/** 收藏状态；未登录时恒为 false */
const favorited = ref(false)
const favoriteBusy = ref(false)
/** 所属店铺 */
const shop = ref<MerchantView | null>(null)
const startingChat = ref(false)

/**
 * 当前选中的 SKU。
 *
 * 无规格商品只有一条默认 SKU（`specValueIds` 是空串），直接用它 ——
 * 交互跟没有这套东西的时候一模一样。有规格时每组都得选满才定位得到 SKU。
 */
const selectedSku = computed<ProductSku | null>(() => {
  const skus = product.value?.skus ?? []
  if (!skus.length) return null
  if (!specs.value.length) return skus[0]

  const chosen = specs.value.map((spec) => selectedValues.value[spec.id])
  if (chosen.some((v) => v === undefined)) return null

  // 按**数值**升序拼成「11,12」再匹配 —— 规则必须和后端存 spec_value_ids 时一致，
  // 差一点就匹配不上（按字符串排的话 "10" 会排在 "2" 前面）
  const key = [...chosen].sort((a, b) => a - b).join(',')
  return skus.find((sku) => sku.specValueIds === key) ?? null
})

/** 规格还没选全 */
const needPickSpec = computed(() => specs.value.length > 0 && !selectedSku.value)
/** 选全了就是那一款的价格；没选全先用商品的最低价兜着 */
const displayPrice = computed(() => selectedSku.value?.price ?? product.value?.price ?? 0)
/** 同上：选全了看那一款的余量，没选全看总库存 */
const displayStock = computed(() => selectedSku.value?.stock ?? product.value?.stock ?? 0)
const displayImage = computed(() => selectedSku.value?.image || product.value?.mainImage || '')

/** 库存为 0 时输入框仍要有个合法的 max，否则 el-input-number 会报警告 */
const maxQuantity = computed(() => Math.max(displayStock.value, 1))
const soldOut = computed(() => displayStock.value === 0)

function pickSpecValue(specId: number, valueId: number) {
  selectedValues.value = { ...selectedValues.value, [specId]: valueId }
}

/** 从商品页联系卖家：新会话会自动附一条商品卡片 */
async function contactSeller() {
  if (!shop.value || !product.value) return
  if (!userStore.isLoggedIn) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  startingChat.value = true
  try {
    const conversation = await startConversation({
      merchantId: shop.value.id,
      productId: product.value.id,
    })
    await router.push(`/user/messages/${conversation.id}`)
  } catch {
    // 平台自营店/店铺停用等，拦截器已提示
  } finally {
    startingChat.value = false
  }
}

function currentId(): number {
  return Number(route.params.id)
}

async function load() {
  const id = currentId()
  loading.value = true
  notFound.value = false
  quantity.value = 1
  selectedValues.value = {}
  favorited.value = false
  shop.value = null
  try {
    // 详情接口已经带上每个 SKU 的**实时**库存了，不用再单独拉一次
    product.value = await fetchProduct(id)
  } catch {
    // 404（不存在 / 已下架）已经在拦截器里提示过一次，这里只负责渲染空状态
    product.value = null
    notFound.value = true
    return
  } finally {
    loading.value = false
  }

  loadRelated(id)
  loadShop(product.value.merchantId)
  if (userStore.isLoggedIn) {
    loadFavorite(id)
    // 足迹上报失败不影响页面；同一商品只更新时间
    recordFootprint(id).catch(() => {})
  }
}

async function loadShop(merchantId: number) {
  try {
    shop.value = await fetchShop(merchantId)
  } catch {
    shop.value = null
  }
}

async function loadFavorite(id: number) {
  try {
    favorited.value = await fetchFavoriteStatus(id)
  } catch {
    favorited.value = false
  }
}

async function toggleFavorite() {
  if (!product.value) return
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  favoriteBusy.value = true
  try {
    if (favorited.value) {
      await removeFavorite(product.value.id)
      favorited.value = false
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(product.value.id)
      favorited.value = true
      ElMessage.success('已收藏')
    }
  } finally {
    favoriteBusy.value = false
  }
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

  const sku = selectedSku.value
  if (!sku) {
    ElMessage.warning('请先选择规格')
    return
  }

  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    // 带上回跳地址，登录完能回到这件商品
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  adding.value = true
  try {
    const view = await addToCart(product.value.id, sku.id, quantity.value)
    badgeStore.syncCart(view)
    ElMessage.success(`已加入购物车 ${quantity.value} 件`)
  } catch {
    // 库存不足 / 未登录过期等，拦截器已提示；重新拉一次库存，让页面上的数字回到真相
    await refreshStock()
  } finally {
    adding.value = false
  }
}

/** 立即购买：跳过购物车，直接带着这一款去结算 —— 和主流电商语义一致 */
function onBuyNow() {
  if (!product.value) return

  const sku = selectedSku.value
  if (!sku) {
    ElMessage.warning('请先选择规格')
    return
  }

  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  router.push({
    path: '/checkout',
    query: { productId: product.value.id, skuId: sku.id, quantity: quantity.value },
  })
}

/** 加购失败后重新拉一次实时库存，让页面上的数字回到真相 */
async function refreshStock() {
  if (!product.value) return
  try {
    const view = await fetchStock(product.value.id)
    const latest = new Map(view.skus.map((s) => [s.skuId, s.stock]))
    for (const sku of product.value.skus ?? []) {
      const value = latest.get(sku.id)
      if (value !== undefined) sku.stock = value
    }
    product.value.stock = view.stock
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
          <ProductImage :src="displayImage" :name="product.name" :id="product.id" />
        </div>

        <div class="info">
          <div class="name-row">
            <h1 class="name">{{ product.name }}</h1>
            <button
              type="button"
              class="fav-btn"
              :class="{ active: favorited }"
              :disabled="favoriteBusy"
              @click="toggleFavorite"
            >
              <el-icon>
                <StarFilled v-if="favorited" />
                <Star v-else />
              </el-icon>
              {{ favorited ? '已收藏' : '收藏' }}
            </button>
          </div>
          <p v-if="product.subtitle" class="subtitle">{{ product.subtitle }}</p>

          <!-- 价格区：粉色允许出现的三个位置之一 -->
          <div class="price-box">
            <span class="price">
              <span class="price-symbol">¥</span>
              <span class="price-num">{{ displayPrice.toFixed(2) }}</span>
            </span>
            <span class="sales">已售 {{ product.sales }} 件</span>
          </div>

          <!-- 所属店铺 -->
          <div v-if="shop" class="shop-card">
            <div class="shop-avatar">
              <ProductImage :src="shop.logo" :name="shop.name" :id="shop.id" />
            </div>
            <div class="shop-info">
              <p class="shop-name">{{ shop.name }}</p>
              <p class="shop-desc">{{ shop.description || '进店看看更多商品' }}</p>
            </div>
            <div class="shop-actions">
              <button
                v-if="!userStore.isAdmin"
                type="button"
                class="contact-btn"
                :disabled="startingChat"
                @click="contactSeller"
              >
                {{ startingChat ? '打开中…' : '联系卖家' }}
              </button>
              <RouterLink :to="`/shop/${shop.id}`" class="shop-link">进店逛逛</RouterLink>
            </div>
          </div>

          <!-- ── 规格选择栏：选中哪一款，价格 / 库存 / 图片就跟着变 ── -->
          <div v-for="spec in specs" :key="spec.id" class="row spec-row">
            <span class="row-label">{{ spec.name }}</span>
            <div class="spec-values">
              <button
                v-for="value in spec.values"
                :key="value.id"
                type="button"
                class="spec-value"
                :class="{ active: selectedValues[spec.id] === value.id }"
                @click="pickSpecValue(spec.id, value.id)"
              >
                {{ value.value }}
              </button>
            </div>
          </div>

          <p v-if="selectedSku?.specText" class="picked">已选 {{ selectedSku.specText }}</p>

          <div class="row">
            <span class="row-label">库存</span>
            <span class="row-value" :class="{ 'is-empty': soldOut }">
              {{ soldOut ? '暂时缺货' : `${displayStock} 件` }}
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
              {{ needPickSpec ? '请选择规格' : soldOut ? '已售罄' : '立即购买' }}
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

      <!-- ── 商品评价 + 问大家 ── -->
      <ProductCommunity :product-id="product.id" />

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
              <ProductImage :src="item.thumbImage || item.mainImage" :name="item.name" :id="item.id" />
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

.name-row {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.name {
  flex: 1;
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--c-text);
}

.fav-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: none;
  margin-top: 4px;
  padding: 6px 12px;
  font-size: 12px;
  font-family: inherit;
  color: var(--c-text-2);
  background: #fff;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-btn);
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}

.fav-btn:hover,
.fav-btn.active {
  color: var(--c-pink);
  border-color: var(--c-pink);
  background: var(--c-pink-tint);
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

/* ── 所属店铺 ── */
.shop-card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-tag);
}

.shop-avatar {
  width: 42px;
  height: 42px;
  flex: none;
  border-radius: 10px;
  overflow: hidden;
  background: var(--c-pink-tint);
  font-size: 16px;
}

.shop-info {
  flex: 1;
  min-width: 0;
}

.shop-name {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
}

.shop-desc {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shop-link {
  flex: none;
  font-size: 12px;
  color: var(--c-pink);
  text-decoration: none;
}

.shop-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
}

.contact-btn {
  padding: 5px 12px;
  font-size: 12px;
  font-family: inherit;
  color: var(--c-pink);
  background: var(--c-pink-tint);
  border: none;
  border-radius: var(--radius-btn);
  cursor: pointer;
}

.contact-btn:disabled {
  color: var(--c-text-3);
  cursor: not-allowed;
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

/* ── 规格选择栏 ── */
.spec-row {
  align-items: flex-start;
}

.spec-values {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.spec-value {
  padding: 6px 14px;
  font-size: 13px;
  font-family: inherit;
  color: var(--c-text);
  background: #fff;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-tag);
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}

.spec-value:hover {
  color: var(--c-pink);
  border-color: var(--c-pink-soft);
}

/* 选中态 —— 粉色允许出现的三个位置之一 */
.spec-value.active {
  color: var(--c-pink);
  background: var(--c-pink-tint);
  border-color: var(--c-pink);
  font-weight: 600;
}

.picked {
  margin: 14px 0 0;
  font-size: 13px;
  color: var(--c-text-2);
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
