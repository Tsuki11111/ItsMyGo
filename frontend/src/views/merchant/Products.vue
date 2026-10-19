<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import ProductFormDialog from '@/views/admin/components/ProductFormDialog.vue'
import SkuEditorDialog from '@/views/admin/components/SkuEditorDialog.vue'
import ProductImage from '@/components/ProductImage.vue'
import { fetchMerchantProducts, updateMerchantProduct } from '@/api/merchant'
import { fetchCategoryTree } from '@/api/product'
import type { CategoryNode, Product } from '@/api/types'

const categories = ref<CategoryNode[]>([])
const products = ref<Product[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const categoryId = ref<number | null>(null)
const loading = ref(false)

const formVisible = ref(false)
const editingProduct = ref<Product | null>(null)
const skuVisible = ref(false)
const skuProduct = ref<Product | null>(null)

const categoryNameById = computed(() => {
  const map = new Map<number, string>()
  const walk = (nodes: CategoryNode[]) => {
    for (const node of nodes) {
      map.set(node.id, node.name)
      walk(node.children ?? [])
    }
  }
  walk(categories.value)
  return map
})

async function loadCategories() {
  // 商家只读平台分类，公开树里只有启用的分类
  categories.value = await fetchCategoryTree()
}

async function load() {
  loading.value = true
  try {
    const result = await fetchMerchantProducts({
      page: page.value,
      size: size.value,
      categoryId: categoryId.value ?? undefined,
      keyword: keyword.value.trim() || undefined,
    })
    products.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  keyword.value = ''
  categoryId.value = null
  page.value = 1
  load()
}

function onPageChange(next: number) {
  page.value = next
  load()
}

function onSizeChange(next: number) {
  size.value = next
  page.value = 1
  load()
}

function openCreate() {
  editingProduct.value = null
  formVisible.value = true
}

function openEdit(row: Product) {
  editingProduct.value = row
  formVisible.value = true
}

function openSku(row: Product) {
  skuProduct.value = row
  skuVisible.value = true
}

async function toggleOnSale(row: Product, onSale: boolean) {
  await updateMerchantProduct(row.id, {
    categoryId: row.categoryId,
    name: row.name,
    subtitle: row.subtitle ?? '',
    mainImage: row.mainImage ?? '',
    price: row.price,
    stock: row.stock,
    detail: row.detail ?? '',
    onSale,
  })
  ElMessage.success(onSale ? '商品已上架' : '商品已下架')
  await load()
}

function onSaleChange(row: Product, value: string | number | boolean) {
  toggleOnSale(row, Boolean(value))
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(async () => {
  await loadCategories()
  await load()
})
</script>

<template>
  <div class="admin-page">
    <div class="page-head">
      <h2>商品管理</h2>
      <el-button type="primary" @click="openCreate">新增商品</el-button>
    </div>

    <div class="filters">
      <el-tree-select
        v-model="categoryId"
        :data="categories"
        :props="{ label: 'name', children: 'children' }"
        node-key="id"
        check-strictly
        clearable
        placeholder="全部分类"
        style="width: 200px"
      />
      <el-input
        v-model="keyword"
        placeholder="搜索商品名 / 副标题"
        clearable
        style="width: 240px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="products" border>
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column label="主图" width="80" align="center">
        <template #default="{ row }">
          <div class="thumb">
            <ProductImage :src="row.mainImage" :name="row.name" :id="row.id" />
          </div>
        </template>
      </el-table-column>
      <el-table-column label="商品" min-width="220">
        <template #default="{ row }">
          <p class="p-name">{{ row.name }}</p>
          <p v-if="row.subtitle" class="p-sub">{{ row.subtitle }}</p>
        </template>
      </el-table-column>
      <el-table-column label="分类" width="120">
        <template #default="{ row }">
          {{ categoryNameById.get(row.categoryId) ?? `#${row.categoryId}` }}
        </template>
      </el-table-column>
      <el-table-column label="价格" width="110" align="right">
        <template #default="{ row }">¥{{ row.price.toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="80" align="right" />
      <el-table-column prop="sales" label="销量" width="80" align="right" />
      <el-table-column label="规格" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.hasSpecs === 1 ? 'warning' : 'info'" size="small">
            {{ row.hasSpecs === 1 ? '多规格' : '单规格' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上架" width="90" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 1"
            @change="onSaleChange(row, $event)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="150">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openSku(row)">规格</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="还没有商品" />
      </template>
    </el-table>

    <div class="pager">
      <el-pagination
        :current-page="page"
        :page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <ProductFormDialog
      v-model="formVisible"
      :product="editingProduct"
      :categories="categories"
      merchant
      @saved="load"
    />

    <SkuEditorDialog v-model="skuVisible" :product="skuProduct" merchant @saved="load" />
  </div>
</template>

<style scoped>
.admin-page {
  background: #fff;
  padding: 20px;
  border-radius: 8px;
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.filters {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.thumb {
  width: 48px;
  height: 48px;
  border-radius: 4px;
  overflow: hidden;
}

.p-name {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.p-sub {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
