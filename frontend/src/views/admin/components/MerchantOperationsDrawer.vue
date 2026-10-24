<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import ProductImage from '@/components/ProductImage.vue'
import TrendLineChart from './TrendLineChart.vue'
import { fetchAdminMerchantOperations } from '@/api/admin'
import type { AdminMerchantOperationsView } from '@/api/types'

const props = defineProps<{
  modelValue: boolean
  merchantId: number | null
}>()

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const router = useRouter()
const view = ref<AdminMerchantOperationsView | null>(null)
const loading = ref(false)

const refundRate = computed(() => {
  if (!view.value || view.value.orders30 <= 0) return '0.0%'
  return `${(view.value.refunding30 / view.value.orders30 * 100).toFixed(1)}%`
})

async function load() {
  if (props.merchantId === null) return
  loading.value = true
  try {
    view.value = await fetchAdminMerchantOperations(props.merchantId)
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      view.value = null
      load()
    }
  },
)

function formatTime(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function statusLabel(value: string): string {
  return ({
    PENDING: '待审核',
    ACTIVE: '营业中',
    REJECTED: '已驳回',
    DISABLED: '已禁用',
  } as const)[value as 'PENDING' | 'ACTIVE' | 'REJECTED' | 'DISABLED'] ?? value
}

function goProducts() {
  if (!view.value) return
  router.push({ path: '/admin/products', query: { merchantId: String(view.value.merchant.id) } })
  emit('update:modelValue', false)
}

function goOrders() {
  if (!view.value) return
  router.push({ path: '/admin/orders', query: { merchantId: String(view.value.merchant.id) } })
  emit('update:modelValue', false)
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="商户经营详情"
    size="760px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading">
      <template v-if="view">
        <div class="head">
          <div>
            <h3>{{ view.merchant.name }}</h3>
            <p class="sub">店主 {{ view.merchant.ownerUsername || '—' }}</p>
          </div>
          <el-tag :type="view.merchant.status === 'ACTIVE' ? 'success' : 'info'">
            {{ statusLabel(view.merchant.status) }}
          </el-tag>
        </div>

        <div class="kpis">
          <div class="kpi"><span>近 30 天成交</span><strong>¥{{ view.gmv30 }}</strong></div>
          <div class="kpi"><span>近 30 天订单</span><strong>{{ view.orders30 }}</strong></div>
          <div class="kpi"><span>退款订单</span><strong>{{ view.refunding30 }}（{{ refundRate }}）</strong></div>
          <div class="kpi"><span>在售商品</span><strong>{{ view.onSaleProducts }}</strong></div>
          <div class="kpi"><span>低库存 SKU</span><strong :class="{ danger: view.lowStockSkus > 0 }">{{ view.lowStockSkus }}</strong></div>
        </div>

        <el-descriptions :column="2" border size="small" class="block">
          <el-descriptions-item label="联系人">{{ view.merchant.contactName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ view.merchant.contactPhone || '—' }}</el-descriptions-item>
          <el-descriptions-item label="发货地址" :span="2">
            {{ [view.merchant.shipProvince, view.merchant.shipCity, view.merchant.shipDistrict, view.merchant.shipAddress].filter(Boolean).join('') || '未设置' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="block">
          <h4>近 7 天成交</h4>
          <TrendLineChart :points="view.trend" />
        </div>

        <div class="block">
          <h4>热销商品 Top 5</h4>
          <el-table :data="view.topProducts" size="small" border>
            <el-table-column label="商品" min-width="220">
              <template #default="{ row }">
                <div class="product">
                  <div class="thumb">
                    <ProductImage :src="row.image" :name="row.name" :id="row.productId" />
                  </div>
                  <span>{{ row.name }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="价格" width="100" align="right">
              <template #default="{ row }">¥{{ row.price }}</template>
            </el-table-column>
            <el-table-column prop="stock" label="库存" width="80" align="right" />
            <el-table-column prop="sales" label="销量" width="80" align="right" />
          </el-table>
        </div>

        <div class="block">
          <h4>最近订单</h4>
          <el-table :data="view.recentOrders" size="small" border>
            <el-table-column prop="orderNo" label="订单号" min-width="180" />
            <el-table-column label="金额" width="100" align="right">
              <template #default="{ row }">¥{{ row.totalAmount }}</template>
            </el-table-column>
            <el-table-column prop="statusLabel" label="状态" width="100" />
            <el-table-column label="下单时间" width="150">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div class="actions">
          <el-button @click="goProducts">查看该店商品</el-button>
          <el-button @click="goOrders">查看该店订单</el-button>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="暂无经营数据" />
    </div>
  </el-drawer>
</template>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.head h3 {
  margin: 0;
  font-size: 18px;
}

.sub {
  margin: 5px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.kpis {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.kpi {
  padding: 12px;
  background: #fafafa;
  border-radius: 6px;
}

.kpi span {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.kpi strong {
  display: block;
  margin-top: 6px;
  font-size: 16px;
}

.kpi strong.danger {
  color: var(--el-color-danger);
}

.block {
  margin-top: 18px;
}

.block h4 {
  margin: 0 0 10px;
  font-size: 14px;
}

.product {
  display: flex;
  align-items: center;
  gap: 8px;
}

.thumb {
  width: 36px;
  height: 36px;
  flex: none;
  border-radius: 4px;
  overflow: hidden;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
}
</style>
