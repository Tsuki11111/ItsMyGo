<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ProductImage from '@/components/ProductImage.vue'
import TrendLineChart from '@/views/admin/components/TrendLineChart.vue'
import { fetchMerchantDashboard } from '@/api/merchant'
import type { MerchantDashboardView } from '@/api/types'

const router = useRouter()
const data = ref<MerchantDashboardView | null>(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    data.value = await fetchMerchantDashboard()
  } catch {
    ElMessage.error('经营看板加载失败')
  } finally {
    loading.value = false
  }
}

function go(path: string) {
  router.push(path)
}

function formatTime(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="merchant-page">
    <div class="page-head">
      <div>
        <h2>经营概览</h2>
        <p class="page-desc">{{ data?.merchantName || '我的店铺' }} · 今天该处理什么，一眼看完</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <template v-if="data">
      <div class="stats">
        <div class="stat-card primary">
          <span class="stat-label">今日成交额</span>
          <strong class="stat-value">¥{{ data.todayGmv }}</strong>
          <span class="stat-hint">按支付时间统计，已退款/取消不计</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">今日订单</span>
          <strong class="stat-value">{{ data.todayOrders }}</strong>
          <span class="stat-hint">今天创建的订单</span>
        </div>
        <div class="stat-card clickable" @click="go('/merchant/orders')">
          <span class="stat-label">待发货</span>
          <strong class="stat-value">{{ data.pendingShipment }}</strong>
          <span class="stat-hint">去订单管理发货 →</span>
        </div>
        <div class="stat-card clickable" @click="go('/merchant/orders')">
          <span class="stat-label">待退款</span>
          <strong class="stat-value">{{ data.pendingRefund }}</strong>
          <span class="stat-hint">去订单管理审核 →</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">近 30 天成交</span>
          <strong class="stat-value">¥{{ data.gmv30 }}</strong>
          <span class="stat-hint">已支付订单 {{ data.paidOrders30 }} 单</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">客单价</span>
          <strong class="stat-value">¥{{ data.avgOrderValue30 }}</strong>
          <span class="stat-hint">近 30 天已支付订单</span>
        </div>
      </div>

      <div class="mini-strip">
        <span>在售商品 <strong>{{ data.onSaleProducts }}</strong></span>
        <span>低库存 SKU <strong class="danger">{{ data.lowStockSkus }}</strong></span>
        <span>近 30 天订单 <strong>{{ data.orders30 }}</strong></span>
        <span>退款/已退款 <strong>{{ data.refunding30 }}</strong></span>
      </div>

      <div class="panel wide">
        <div class="panel-head">
          <h3>近 7 天成交</h3>
          <span class="muted">按支付时间</span>
        </div>
        <TrendLineChart :points="data.trend" />
      </div>

      <div class="panel-grid">
        <div class="panel">
          <div class="panel-head">
            <h3>热销商品</h3>
            <el-button link type="primary" @click="go('/merchant/products')">全部商品</el-button>
          </div>
          <div v-if="data.topProducts.length" class="product-list">
            <div v-for="item in data.topProducts" :key="item.productId" class="product-row">
              <div class="thumb">
                <ProductImage :src="item.image" :name="item.name" :id="item.productId" />
              </div>
              <div class="product-main">
                <p class="product-name">{{ item.name }}</p>
                <p class="muted">库存 {{ item.stock }} · 已售 {{ item.sales }}</p>
              </div>
              <span class="amount">¥{{ item.price }}</span>
            </div>
          </div>
          <el-empty v-else description="还没有在售商品" :image-size="60" />
        </div>

        <div class="panel">
          <div class="panel-head">
            <h3>低库存</h3>
            <span class="muted">库存 ≤ 10 的在售规格</span>
          </div>
          <div v-if="data.lowStockList.length" class="product-list">
            <div v-for="item in data.lowStockList" :key="item.skuId" class="product-row">
              <div class="thumb">
                <ProductImage :src="item.image" :name="item.productName" :id="item.productId" />
              </div>
              <div class="product-main">
                <p class="product-name">{{ item.productName }}</p>
                <p class="muted">{{ item.specText || '默认规格' }}</p>
              </div>
              <span class="stock danger">剩 {{ item.stock }}</span>
            </div>
          </div>
          <el-empty v-else description="没有低库存商品" :image-size="60" />
        </div>
      </div>

      <div class="panel wide">
        <div class="panel-head">
          <h3>最近订单</h3>
          <el-button link type="primary" @click="go('/merchant/orders')">全部订单</el-button>
        </div>
        <el-table v-if="data.recentOrders.length" :data="data.recentOrders" size="small">
          <el-table-column prop="orderNo" label="订单号" min-width="190" />
          <el-table-column prop="statusLabel" label="状态" width="90" />
          <el-table-column label="金额" width="120" align="right">
            <template #default="{ row }">¥{{ row.totalAmount }}</template>
          </el-table-column>
          <el-table-column label="下单时间" width="160">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="还没有订单" :image-size="60" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.merchant-page {
  min-height: 300px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.page-desc {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 12px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 16px;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.stat-card.clickable {
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.stat-card.clickable:hover {
  border-color: var(--c-pink);
  box-shadow: 0 2px 10px rgba(214, 51, 108, 0.08);
}

.stat-card.primary {
  background: linear-gradient(135deg, #fff5f8, #fff);
  border-color: #f2cede;
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stat-value {
  margin: 4px 0;
  font-size: 26px;
  line-height: 1.2;
  color: var(--el-text-color-primary);
}

.stat-card.primary .stat-value {
  color: var(--c-pink);
}

.stat-hint {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.mini-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 26px;
  margin: 14px 0;
  padding: 12px 16px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.mini-strip strong {
  margin-left: 4px;
  color: var(--el-text-color-primary);
}

.panel {
  padding: 16px;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.panel.wide {
  margin-bottom: 14px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.panel-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.panel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}

.product-list {
  display: flex;
  flex-direction: column;
}

.product-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 4px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.product-row:last-child {
  border-bottom: none;
}

.thumb {
  width: 42px;
  height: 42px;
  flex: none;
  overflow: hidden;
  border-radius: 6px;
}

.product-main {
  flex: 1;
  min-width: 0;
}

.product-name {
  margin: 0;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.muted {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.amount,
.stock {
  flex: none;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.amount {
  color: var(--c-pink);
}

.danger {
  color: var(--el-color-danger);
}
</style>
