<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ProductFormDialog from './components/ProductFormDialog.vue'
import SkuEditorDialog from './components/SkuEditorDialog.vue'
import StockAdjustDialog from './components/StockAdjustDialog.vue'
import ProductImage from '@/components/ProductImage.vue'
import {
  batchUpdateProductStatus,
  fetchAdminCategories,
  fetchAdminMerchants,
  fetchAdminProducts,
  fetchProductSkus,
  rebuildBloomFilter,
  updateProduct,
} from '@/api/admin'
import type { AdminMerchantView, CategoryNode, Product, ProductSku } from '@/api/types'

const route = useRoute()
const categories = ref<CategoryNode[]>([])
const merchants = ref<AdminMerchantView[]>([])
const products = ref<Product[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const categoryId = ref<number | null>(null)
const merchantId = ref<number | null>(null)
const statusFilter = ref<number | null>(null)
const lowStock = ref(false)
const loading = ref(false)
const selected = ref<Product[]>([])

const formVisible = ref(false)
const editingProduct = ref<Product | null>(null)
const skuVisible = ref(false)
const skuProduct = ref<Product | null>(null)
const stockVisible = ref(false)
const stockProductId = ref<number | null>(null)
const stockSku = ref<ProductSku | null>(null)

/** 分类名映射：管理端商品列表只给 categoryId */
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
  categories.value = await fetchAdminCategories()
}

async function loadMerchants() {
  const result = await fetchAdminMerchants({ page: 1, size: 100 })
  merchants.value = result.records
}

const merchantNameById = computed(() => {
  const map = new Map<number, string>()
  for (const merchant of merchants.value) {
    map.set(merchant.id, merchant.name)
  }
  return map
})

async function load() {
  loading.value = true
  try {
    const result = await fetchAdminProducts({
      page: page.value,
      size: size.value,
      categoryId: categoryId.value ?? undefined,
      keyword: keyword.value.trim() || undefined,
      merchantId: merchantId.value ?? undefined,
      status: statusFilter.value ?? undefined,
      lowStock: lowStock.value || undefined,
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
  merchantId.value = null
  statusFilter.value = null
  lowStock.value = false
  page.value = 1
  load()
}

function onSelectionChange(rows: Product[]) {
  selected.value = rows
}

async function batchStatus(onSale: boolean) {
  if (selected.value.length === 0) {
    ElMessage.warning('请先勾选商品')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定${onSale ? '上架' : '下架'}选中的 ${selected.value.length} 个商品吗？`,
      onSale ? '批量上架' : '批量下架',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  const count = await batchUpdateProductStatus(
    selected.value.map((item) => item.id),
    onSale,
  )
  ElMessage.success(`已更新 ${count} 个商品`)
  await load()
}

async function openStock(row: Product) {
  const view = await fetchProductSkus(row.id)
  if (view.skus.length !== 1) {
    // 多规格的库存分散在多个 SKU 上，直接进规格编辑器
    openSku(row)
    return
  }
  stockProductId.value = row.id
  stockSku.value = view.skus[0]
  stockVisible.value = true
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

/**
 * 上下架复用商品更新接口，把整行的字段原样带回去，只翻转 onSale。
 * 不做硬删除 —— 商品删了会留下购物车/订单/统计的孤儿数据。
 */
async function toggleOnSale(row: Product, onSale: boolean) {
  await updateProduct(row.id, {
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

async function rebuildBloom() {
  try {
    await ElMessageBox.confirm(
      '手工重建商品布隆过滤器？用于手工插库、数据导入之后的补救。',
      '重建布隆过滤器',
    )
  } catch {
    return
  }
  const count = await rebuildBloomFilter()
  ElMessage.success(`已重建，共 ${count} 个商品`)
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(async () => {
  if (route.query.lowStock === '1') lowStock.value = true
  if (route.query.merchantId) merchantId.value = Number(route.query.merchantId)
  await Promise.all([loadCategories(), loadMerchants()])
  await load()
})
</script>

<template>
  <div class="admin-page">
    <div class="page-head">
      <h2>商品管理</h2>
      <div class="head-actions">
        <el-button @click="rebuildBloom">重建布隆过滤器</el-button>
        <el-button type="primary" @click="openCreate">新增商品</el-button>
      </div>
    </div>

    <div class="filters">
      <el-select
        v-model="merchantId"
        clearable
        filterable
        placeholder="全部店铺"
        style="width: 180px"
      >
        <el-option
          v-for="merchant in merchants"
          :key="merchant.id"
          :label="merchant.name"
          :value="merchant.id"
        />
      </el-select>
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
      <el-select
        v-model="statusFilter"
        clearable
        placeholder="全部状态"
        style="width: 130px"
        @change="search"
      >
        <el-option :value="1" label="已上架" />
        <el-option :value="0" label="已下架" />
      </el-select>
      <el-checkbox v-model="lowStock" @change="search">只看低库存</el-checkbox>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <div class="batch-bar">
      <span>已选 {{ selected.length }} 项</span>
      <el-button size="small" :disabled="selected.length === 0" @click="batchStatus(true)">
        批量上架
      </el-button>
      <el-button size="small" :disabled="selected.length === 0" @click="batchStatus(false)">
        批量下架
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="products"
      row-key="id"
      border
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="46" />
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column label="主图" width="80" align="center">
        <template #default="{ row }">
          <div class="thumb">
            <ProductImage :src="row.thumbImage || row.mainImage" :name="row.name" :id="row.id" />
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
      <el-table-column label="店铺" width="130">
        <template #default="{ row }">
          {{ merchantNameById.get(row.merchantId) ?? `#${row.merchantId}` }}
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
      <el-table-column label="操作" width="190">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openSku(row)">规格</el-button>
          <el-button link type="primary" @click="openStock(row)">库存</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="没有符合条件的商品" />
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
      @saved="load"
    />

    <SkuEditorDialog v-model="skuVisible" :product="skuProduct" @saved="load" />

    <StockAdjustDialog
      v-model="stockVisible"
      :product-id="stockProductId ?? 0"
      :sku="stockSku"
      @updated="load"
    />
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

.head-actions {
  display: flex;
  gap: 8px;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
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
