<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Box, Briefcase, Coin, Connection, Cpu, Files, Monitor, Mouse, Printer, Share,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'
import ProductImage from '@/components/ProductImage.vue'
import { fetchCategoryTree, fetchProducts } from '@/api/product'
import type { CategoryNode, Product } from '@/api/types'

const route = useRoute()
const router = useRouter()

const products = ref<Product[]>([])
const categories = ref<CategoryNode[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const categoryId = ref<number | undefined>(undefined)
/** 当前搜索词。品牌专区点进来时会带上它 */
const keyword = ref('')
/** 首屏 / 换筛选条件时的加载（显示遮罩） */
const loading = ref(false)
/** 往下滑时追加下一页（不遮罩，只在底部提示） */
const loadingMore = ref(false)

/** 还有没有下一页 —— 用「已拿到多少 / 总共多少」判断，不靠页码算 */
const hasMore = computed(() => products.value.length < total.value)

/** 分类名 → 图标。匹配不上就用 Box 兜底，新增分类不会把页面搞崩 */
const ICONS: Record<string, Component> = {
  打印机: Printer,
  打印耗材: Files,
  路由器: Share,
  网络设备: Connection,
  笔记本电脑: Monitor,
  显示器: Monitor,
  键鼠外设: Mouse,
  存储设备: Coin,
  办公设备: Briefcase,
  配件线材: Cpu,
}
function iconOf(name: string): Component {
  return ICONS[name] ?? Box
}

/** 轮播位。后端还没有广告位数据，先用文案 + 渐变做，
 *  等真需要运营配置广告了再加一张表和一个接口。 */
const banners = [
  { title: '新用户专享', desc: '注册即领 100 元优惠券', from: '#ef8aab', to: '#f9c8db' },
  { title: '打印机专场', desc: '激光 / 一体机 低至 5 折', from: '#e8638f', to: '#f4a9c3' },
  { title: '办公设备节', desc: '满 2000 减 200', from: '#e8638f', to: '#ef8aab' },
]

/**
 * 品牌专区。**暂时硬编码** —— 后端还没有品牌表，所以这里只放商品名里
 * 真实出现过的词，点进去才搜得到东西。将来要让商户自己维护，
 * 就得建 brands 表 + 商品关联 + 管理端接口。
 */
const BRANDS = ['奔图', '联想', '兄弟', '至像', 'H3C', 'Aolynk', 'HUAWEI']

/** 品牌色块用的渐变，跟无图占位同一套（见 ProductImage.vue） */
const BRAND_GRADIENTS = [
  'linear-gradient(135deg, #FFE4EF, #FFD6E7)',
  'linear-gradient(135deg, #FFE9F0, #FFDCE8)',
  'linear-gradient(135deg, #FFF0F5, #FFE2EC)',
  'linear-gradient(135deg, #FFE6F0, #FFD9E6)',
]

const currentCategoryName = computed(() => {
  if (keyword.value) return `“${keyword.value}” 的搜索结果`
  if (categoryId.value === undefined) return '全部商品'
  return categories.value.find((c) => c.id === categoryId.value)?.name ?? '全部商品'
})

/**
 * 拉数据。`reset` 为真表示换筛选条件了 —— 清空重来；
 * 否则是往下滑，把新一页**追加**到现有列表后面。
 */
async function load(reset: boolean) {
  if (reset) {
    page.value = 1
    loading.value = true
  } else {
    loadingMore.value = true
  }
  try {
    const result = await fetchProducts({
      page: page.value,
      size: size.value,
      categoryId: categoryId.value,
      keyword: keyword.value || undefined,
    })
    products.value = reset ? result.records : [...products.value, ...result.records]
    total.value = result.total
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

/** 滚到底部时调它。加载中或没有下一页时直接忽略，避免重复请求 */
function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value) return
  page.value += 1
  load(false)
}

/**
 * 筛选条件（分类、搜索词）**只存在 URL 里** —— 组件不再各存一份，
 * 免得两处状态打架：刷新能保持、品牌专区点进来天然就是「搜这个品牌」。
 */
function selectCategory(id?: number) {
  router.push({ path: '/', query: id === undefined ? {} : { categoryId: id } })
}

function goDetail(product: Product) {
  router.push(`/product/${product.id}`)
}

/** 把 URL 上的筛选条件翻译成状态，然后从头拉一页 */
function applyQuery() {
  const query = route.query
  categoryId.value = query.categoryId ? Number(query.categoryId) : undefined
  keyword.value = typeof query.keyword === 'string' ? query.keyword : ''
  return load(true)
}

// 同一个路由只改 query 时组件不会重建，所以得盯着 fullPath
watch(() => route.fullPath, applyQuery)

/**
 * 滚到底部自动加载下一页。
 *
 * 用 IntersectionObserver 而不是 Element Plus 的 v-infinite-scroll：
 * 后者要求滚动发生在绑定元素内部，而我们是整页滚动，用它得动布局。
 * rootMargin 提前 200px 触发，滚到底时下一页往往已经到了。
 */
const sentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

onMounted(async () => {
  categories.value = await fetchCategoryTree()
  applyQuery()

  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting) loadMore()
    },
    { rootMargin: '200px' },
  )
  if (sentinel.value) observer.observe(sentinel.value)
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <div class="list-page">
    <!-- ── 首屏：左品类栏 + 右轮播 ── -->
    <section class="hero">
      <aside class="cat-panel">
        <div class="cat-head">
          <span class="cat-head-icon">☰</span>
          <span>品类市场</span>
        </div>
        <ul class="cat-list">
          <li
            class="cat-item"
            :class="{ active: categoryId === undefined }"
            @click="selectCategory(undefined)"
          >
            <el-icon class="cat-icon"><Box /></el-icon>
            <span>全部商品</span>
          </li>
          <li
            v-for="cat in categories"
            :key="cat.id"
            class="cat-item"
            :class="{ active: categoryId === cat.id }"
            @click="selectCategory(cat.id)"
          >
            <el-icon class="cat-icon"><component :is="iconOf(cat.name)" /></el-icon>
            <span>{{ cat.name }}</span>
          </li>
        </ul>
      </aside>

      <div class="banner">
        <el-carousel :interval="4000" arrow="hover">
          <el-carousel-item v-for="banner in banners" :key="banner.title">
            <div
              class="banner-slide"
              :style="{ background: `linear-gradient(120deg, ${banner.from}, ${banner.to})` }"
            >
              <h2>{{ banner.title }}</h2>
              <p>{{ banner.desc }}</p>
            </div>
          </el-carousel-item>
        </el-carousel>
      </div>

      <!-- 品牌专区：点进去就是「搜这个品牌」，复用列表页现成的筛选 -->
      <aside class="brand-panel">
        <div class="brand-head">品牌专区</div>
        <div class="brand-grid">
          <RouterLink
            v-for="(brand, index) in BRANDS"
            :key="brand"
            :to="{ path: '/', query: { keyword: brand } }"
            class="brand-item"
          >
            <span
              class="brand-mark"
              :style="{ background: BRAND_GRADIENTS[index % BRAND_GRADIENTS.length] }"
            >
              {{ brand.charAt(0) }}
            </span>
            <span class="brand-name">{{ brand }}</span>
          </RouterLink>
        </div>
      </aside>
    </section>

    <!-- ── 商品区 ── -->
    <section class="goods">
      <h3 class="goods-title">{{ currentCategoryName }}</h3>

      <div v-loading="loading" class="grid">
        <article
          v-for="product in products"
          :key="product.id"
          class="card"
          @click="goDetail(product)"
        >
          <div class="thumb">
            <ProductImage :src="product.mainImage" :name="product.name" :id="product.id" />
          </div>

          <div class="info">
            <h3 class="name">{{ product.name }}</h3>
            <p v-if="product.subtitle" class="subtitle">{{ product.subtitle }}</p>

            <!-- 价格：主色 + 等宽数字，全卡片最"响"的元素之一。
                 多规格商品这里显示的是**最低价**，加个「起」免得点进去发现不是那个价 -->
            <p class="price">
              <span class="price-symbol">¥</span>
              <span class="price-num">{{ product.price.toFixed(2) }}</span>
              <span v-if="product.hasSpecs === 1" class="price-from">起</span>
            </p>

            <p class="meta">已售 {{ product.sales }}</p>
          </div>
        </article>
      </div>

      <el-empty v-if="!loading && products.length === 0" description="这个分类下还没有商品" />

      <!-- 滚到底部的加载哨兵：IntersectionObserver 盯着它，进视口就去拉下一页 -->
      <div ref="sentinel" class="load-more">
        <span v-if="loadingMore">正在加载…</span>
        <span v-else-if="products.length && !hasMore">
          已经到底了，共 {{ total }} 件商品
        </span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.list-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* ── 首屏 ── */
.hero {
  display: grid;
  /* 品类栏 | 轮播 | 品牌专区 */
  grid-template-columns: 190px 1fr 240px;
  gap: 14px;
  /* 高度要能把全部品类装下，否则品类栏自己出滚动条（内容 375 + 头部 ~41 + 内边距） */
  height: 428px;
}

/* 品类栏 */
.cat-panel {
  background: #fff;
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.cat-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-pink);
  border-bottom: 1px solid var(--c-border);
}

.cat-head-icon {
  font-size: 13px;
}

.cat-list {
  list-style: none;
  margin: 0;
  padding: 4px 0;
  overflow-y: auto;
  flex: 1;
}

.cat-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  font-size: 13px;
  color: var(--c-text-2);
  cursor: pointer;
  transition: all 0.14s;
}

.cat-item:hover {
  background: var(--c-pink-tint);
  color: var(--c-pink);
}

/* 选中态 —— 粉色出现的三个位置之一 */
.cat-item.active {
  background: var(--c-pink-soft);
  color: var(--c-pink);
  font-weight: 600;
}

.cat-icon {
  font-size: 15px;
}

/* 轮播 */
.banner {
  border-radius: var(--radius-card);
  overflow: hidden;
  box-shadow: var(--shadow-card);
  height: 100%;
}

/* el-carousel 的高度参数只认具体像素，传 100% 不生效 —— 用 CSS 强制撑满父容器 */
.banner :deep(.el-carousel),
.banner :deep(.el-carousel__container) {
  height: 100%;
}

.banner-slide {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 56px;
  color: #fff;
}

.banner-slide h2 {
  margin: 0;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.banner-slide p {
  margin: 10px 0 0;
  font-size: 14px;
  opacity: 0.9;
}

/* ── 品牌专区 ── */
.brand-panel {
  background: #fff;
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 和品类栏的头部样式保持一致 */
.brand-head {
  padding: 12px 16px 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-pink);
  border-bottom: 1px solid var(--c-border);
}

.brand-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  /* 行高均分：七个品牌排不满时，别让下半截空着 */
  grid-auto-rows: 1fr;
  gap: 6px;
  padding: 10px;
  overflow-y: auto;
}

.brand-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 8px 4px;
  border-radius: var(--radius-tag);
  text-decoration: none;
  transition: background 0.14s;
}

.brand-item:hover {
  background: var(--c-pink-tint);
}

.brand-mark {
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  font-weight: 600;
  color: rgba(232, 99, 143, 0.55);
  border-radius: var(--radius-tag);
}

.brand-name {
  font-size: 12px;
  line-height: 1;
  color: var(--c-text-2);
}

/* ── 商品区 ── */
.goods-title {
  margin: 0 0 14px;
  font-size: 16px;
  font-weight: 600;
  color: var(--c-text);
}

.grid {
  display: grid;
  /* 大屏固定一行 6 个；间距比原来的 18px 收紧 */
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  min-height: 120px;
}

/* 中等屏幕退到 5 个一行：卡片反而比 6 列时更大 */
@media (max-width: 1099px) {
  .grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}

@media (max-width: 899px) {
  .grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 699px) {
  .grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 519px) {
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.card {
  background: var(--c-card);
  border-radius: var(--radius-card);
  overflow: hidden;
  box-shadow: var(--shadow-card);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

/* 记忆点：轻微上浮 + 阴影变粉变明显 */
.card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-card-hover);
}

.thumb {
  /* 正方形：用 aspect-ratio 跟着卡片宽度走。
     写死 height 的话，卡片一宽就变成横长方形、一窄就变竖的 —— 而卡片宽度本身
     是 minmax(200px, 1fr)，随屏幕伸缩的 */
  aspect-ratio: 1 / 1;
  background: var(--c-pink-tint);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  /* 无图占位的首字字号，见 ProductImage.vue */
  font-size: 40px;
}

.info {
  padding: 12px 14px 14px;
}

/* 层级：图 > 价格 > 名称 > 其余。名称不加粗 */
.name {
  margin: 0;
  font-size: 13.5px;
  font-weight: 500;
  line-height: 1.45;
  color: var(--c-text);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.subtitle {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price {
  margin: 10px 0 0;
  color: var(--c-pink);
  line-height: 1;
}

.price-symbol {
  font-size: 13px;
  font-weight: 600;
  margin-right: 1px;
}

.price-num {
  font-size: 19px;
}

/* 「起」比价格小一号、也不加粗 —— 它只是注解，不能抢价格的戏 */
.price-from {
  margin-left: 2px;
  font-size: 12px;
  font-weight: 400;
}

.meta {
  margin: 6px 0 0;
  font-size: 11.5px;
  color: var(--c-text-3);
}

/* 无限滚动的哨兵：它本身只是个占位，文字是给用户看的加载状态 */
.load-more {
  display: flex;
  justify-content: center;
  padding: 22px 0 8px;
  font-size: 12.5px;
  color: var(--c-text-3);
}

/* 窄屏：品类栏收成横向滚动条，品牌专区直接藏起来（移动端首屏不该塞品牌墙） */
@media (max-width: 820px) {
  .hero {
    grid-template-columns: 1fr;
    height: auto;
  }
  .cat-panel {
    height: auto;
  }
  .brand-panel {
    display: none;
  }
  .cat-list {
    display: flex;
    overflow-x: auto;
    padding: 6px 8px;
  }
  .cat-item {
    white-space: nowrap;
  }
  .banner {
    height: 180px;
  }
}
</style>
