<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Box, Briefcase, Coin, Connection, Cpu, Files, Monitor, Mouse, Printer, Share,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'
import ProductImage from '@/components/ProductImage.vue'
import { fetchCategoryTree, fetchProducts } from '@/api/product'
import type { CategoryNode, Product } from '@/api/types'

const router = useRouter()

const products = ref<Product[]>([])
const categories = ref<CategoryNode[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const categoryId = ref<number | undefined>(undefined)
const loading = ref(false)

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

const currentCategoryName = computed(() => {
  if (categoryId.value === undefined) return '全部商品'
  return categories.value.find((c) => c.id === categoryId.value)?.name ?? '全部商品'
})

async function load() {
  loading.value = true
  try {
    const result = await fetchProducts({
      page: page.value,
      size: size.value,
      categoryId: categoryId.value,
    })
    products.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function selectCategory(id?: number) {
  categoryId.value = id
  page.value = 1
  load()
}

function onPageChange(next: number) {
  page.value = next
  load()
}

function goDetail(product: Product) {
  router.push(`/product/${product.id}`)
}

onMounted(async () => {
  categories.value = await fetchCategoryTree()
  await load()
})
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
  grid-template-columns: 190px 1fr;
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

/* ── 商品区 ── */
.goods-title {
  margin: 0 0 14px;
  font-size: 16px;
  font-weight: 600;
  color: var(--c-text);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 18px;
  min-height: 120px;
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

.pager {
  display: flex;
  justify-content: center;
  padding: 20px 0 8px;
}

/* 窄屏：品类栏收成横向滚动条，别占满整屏 */
@media (max-width: 820px) {
  .hero {
    grid-template-columns: 1fr;
    height: auto;
  }
  .cat-panel {
    height: auto;
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
