<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ProductImage from '@/components/ProductImage.vue'
import { fetchFavorites, removeFavorite } from '@/api/favorite'
import type { FavoriteView } from '@/api/types'

const router = useRouter()

const items = ref<FavoriteView[]>([])
const total = ref(0)
const page = ref(1)
const size = 12
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await fetchFavorites(page.value, size)
    items.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function open(item: FavoriteView) {
  if (item.status !== 1) {
    ElMessage.warning('商品已下架')
    return
  }
  router.push(`/product/${item.productId}`)
}

async function cancel(item: FavoriteView) {
  await removeFavorite(item.productId)
  ElMessage.success('已取消收藏')
  // 删掉最后一条时回退一页，避免停在空页
  if (items.value.length === 1 && page.value > 1) page.value -= 1
  await load()
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
    <div class="head">
      <h2 class="block-title">我的收藏</h2>
      <span class="count">共 {{ total }} 件</span>
    </div>

    <div v-loading="loading" class="grid">
      <el-empty v-if="!loading && items.length === 0" description="还没有收藏商品" class="empty">
        <el-button type="primary" @click="router.push('/')">去逛逛</el-button>
      </el-empty>

      <article v-for="item in items" :key="item.id" class="fav-card">
        <button type="button" class="thumb" @click="open(item)">
          <ProductImage :src="item.mainImage" :name="item.name" :id="item.productId" />
        </button>
        <div class="info">
          <RouterLink
            v-if="item.merchantId"
            :to="`/shop/${item.merchantId}`"
            class="shop-link"
            @click.stop
          >
            {{ item.merchantName }}
          </RouterLink>
          <button type="button" class="name" @click="open(item)">{{ item.name }}</button>
          <p v-if="item.subtitle" class="sub">{{ item.subtitle }}</p>
          <div class="bottom">
            <span class="price">
              <span class="price-symbol">¥</span>
              <span class="price-num">{{ item.price.toFixed(2) }}</span>
            </span>
            <el-tag v-if="item.status !== 1" type="info" size="small">已下架</el-tag>
          </div>
        </div>
        <div class="actions">
          <span class="time">{{ formatTime(item.createdAt) }}</span>
          <el-button link type="danger" @click="cancel(item)">取消收藏</el-button>
        </div>
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

.head {
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
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 16px;
  min-height: 120px;
}

.empty {
  grid-column: 1 / -1;
}

.fav-card {
  border: 1px solid var(--c-border);
  border-radius: var(--radius-card);
  overflow: hidden;
  transition: box-shadow 0.18s;
}

.fav-card:hover {
  box-shadow: var(--shadow-card-hover);
}

.thumb {
  display: block;
  width: 100%;
  aspect-ratio: 1 / 1;
  padding: 0;
  border: none;
  background: var(--c-pink-tint);
  cursor: pointer;
}

.info {
  padding: 10px 12px 6px;
}

.name {
  display: block;
  width: 100%;
  padding: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
  text-align: left;
  background: none;
  border: none;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shop-link {
  display: block;
  margin-bottom: 4px;
  font-size: 11px;
  color: var(--c-pink);
  text-decoration: none;
}

.sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 12px 10px;
}

.time {
  font-size: 11px;
  color: var(--c-text-3);
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}
</style>
