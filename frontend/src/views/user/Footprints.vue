<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ProductImage from '@/components/ProductImage.vue'
import { clearFootprints, fetchFootprints } from '@/api/footprint'
import type { FootprintView } from '@/api/types'

const router = useRouter()

const items = ref<FootprintView[]>([])
const total = ref(0)
const page = ref(1)
const size = 12
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await fetchFootprints(page.value, size)
    items.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function open(item: FootprintView) {
  if (item.status !== 1) {
    ElMessage.warning('商品已下架')
    return
  }
  router.push(`/product/${item.productId}`)
}

async function clearAll() {
  try {
    await ElMessageBox.confirm('确定清空全部浏览足迹吗？', '清空足迹', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await clearFootprints()
  ElMessage.success('足迹已清空')
  page.value = 1
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
      <h2 class="block-title">浏览足迹</h2>
      <div class="head-right">
        <span class="count">共 {{ total }} 件</span>
        <el-button v-if="total > 0" link type="danger" @click="clearAll">清空全部</el-button>
      </div>
    </div>

    <div v-loading="loading" class="grid">
      <el-empty v-if="!loading && items.length === 0" description="还没有浏览记录" class="empty">
        <el-button type="primary" @click="router.push('/')">去逛逛</el-button>
      </el-empty>

      <article v-for="item in items" :key="item.id" class="fp-card">
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
          <div class="bottom">
            <span class="price">
              <span class="price-symbol">¥</span>
              <span class="price-num">{{ item.price.toFixed(2) }}</span>
            </span>
            <el-tag v-if="item.status !== 1" type="info" size="small">已下架</el-tag>
          </div>
          <p class="time">{{ formatTime(item.viewedAt) }}</p>
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

.head-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.count {
  font-size: 12px;
  color: var(--c-text-3);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 14px;
  min-height: 120px;
}

.empty {
  grid-column: 1 / -1;
}

.fp-card {
  border: 1px solid var(--c-border);
  border-radius: var(--radius-card);
  overflow: hidden;
  transition: box-shadow 0.18s;
}

.fp-card:hover {
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
  padding: 10px 12px;
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

.bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.time {
  margin: 6px 0 0;
  font-size: 11px;
  color: var(--c-text-3);
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}
</style>
