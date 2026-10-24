<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ProductImage from '@/components/ProductImage.vue'
import TrendLineChart from './components/TrendLineChart.vue'
import { fetchAdminDashboard } from '@/api/admin'
import type { AdminDashboardView } from '@/api/types'

const router = useRouter()
const data = ref<AdminDashboardView | null>(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    data.value = await fetchAdminDashboard()
  } catch {
    ElMessage.error('数据概览加载失败')
  } finally {
    loading.value = false
  }
}

function go(path: string) {
  router.push(path)
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="admin-page">
    <div class="page-head">
      <div>
        <h2>数据概览</h2>
        <p class="page-desc">今天该处理什么，一眼看完</p>
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
          <span class="stat-hint">其中已支付 {{ data.todayPaidOrders }} 单</span>
        </div>
        <div class="stat-card clickable" @click="go('/admin/orders')">
          <span class="stat-label">待发货</span>
          <strong class="stat-value">{{ data.pendingShipments }}</strong>
          <span class="stat-hint">去订单管理处理 →</span>
        </div>
        <div class="stat-card clickable" @click="go('/admin/orders')">
          <span class="stat-label">待退款</span>
          <strong class="stat-value">{{ data.pendingRefunds }}</strong>
          <span class="stat-hint">去订单管理审核 →</span>
        </div>
        <div class="stat-card clickable" @click="go('/admin/merchants')">
          <span class="stat-label">待审核商户</span>
          <strong class="stat-value">{{ data.pendingMerchants }}</strong>
          <span class="stat-hint">去商户管理审核 →</span>
        </div>
        <div class="stat-card clickable" @click="go('/admin/products?lowStock=1')">
          <span class="stat-label">低库存 SKU</span>
          <strong class="stat-value">{{ data.lowStockSkus }}</strong>
          <span class="stat-hint">库存 ≤ 10 的在售规格 →</span>
        </div>
      </div>

      <div class="mini-strip">
        <span>用户总数 <strong>{{ data.totalUsers }}</strong></span>
        <span>营业店铺 <strong>{{ data.activeMerchants }}</strong></span>
        <span>在售商品 <strong>{{ data.onSaleProducts }}</strong></span>
      </div>

      <div class="panel wide">
        <div class="panel-head">
          <h3>近 7 天成交</h3>
          <span class="muted">按支付时间</span>
        </div>
        <TrendLineChart :points="data.trend" />
      </div>

      <div class="todo-grid">
        <div class="panel">
          <div class="panel-head">
            <h3>待发货</h3>
            <el-button link type="primary" @click="go('/admin/orders')">全部</el-button>
          </div>
          <div v-if="data.pendingShipmentOrders.length" class="todo-list">
            <div
              v-for="order in data.pendingShipmentOrders"
              :key="order.id"
              class="todo"
              @click="go('/admin/orders')"
            >
              <div class="todo-line">
                <span class="mono">{{ order.orderNo }}</span>
                <span class="amount">¥{{ order.totalAmount }}</span>
              </div>
              <div class="todo-sub">{{ order.merchantName }} · {{ order.createdAt }}</div>
            </div>
          </div>
          <el-empty v-else description="没有待发货订单" :image-size="60" />
        </div>

        <div class="panel">
          <div class="panel-head">
            <h3>待退款</h3>
            <el-button link type="primary" @click="go('/admin/orders')">全部</el-button>
          </div>
          <div v-if="data.pendingRefundOrders.length" class="todo-list">
            <div
              v-for="order in data.pendingRefundOrders"
              :key="order.id"
              class="todo"
              @click="go('/admin/orders')"
            >
              <div class="todo-line">
                <span class="mono">{{ order.orderNo }}</span>
                <span class="amount">¥{{ order.totalAmount }}</span>
              </div>
              <div class="todo-sub">{{ order.merchantName }} · {{ order.createdAt }}</div>
            </div>
          </div>
          <el-empty v-else description="没有待退款订单" :image-size="60" />
        </div>

        <div class="panel">
          <div class="panel-head">
            <h3>待审核商户</h3>
            <el-button link type="primary" @click="go('/admin/merchants')">全部</el-button>
          </div>
          <div v-if="data.pendingMerchantList.length" class="todo-list">
            <div
              v-for="merchant in data.pendingMerchantList"
              :key="merchant.id"
              class="todo"
              @click="go('/admin/merchants')"
            >
              <div class="todo-line">
                <span>{{ merchant.name }}</span>
                <span class="muted">#{{ merchant.id }}</span>
              </div>
              <div class="todo-sub">{{ merchant.contactName }} · {{ merchant.createdAt }}</div>
            </div>
          </div>
          <el-empty v-else description="没有待审核商户" :image-size="60" />
        </div>

        <div class="panel">
          <div class="panel-head">
            <h3>低库存</h3>
            <el-button link type="primary" @click="go('/admin/products?lowStock=1')">全部</el-button>
          </div>
          <div v-if="data.lowStockList.length" class="todo-list">
            <div
              v-for="sku in data.lowStockList"
              :key="sku.skuId"
              class="low-stock"
              @click="go('/admin/products?lowStock=1')"
            >
              <div class="thumb">
                <ProductImage :src="sku.image" :name="sku.productName" :id="sku.productId" />
              </div>
              <div class="low-stock-info">
                <div class="todo-line">
                  <span>{{ sku.productName }}</span>
                  <el-tag :type="sku.stock <= 0 ? 'danger' : 'warning'" size="small">
                    {{ sku.stock <= 0 ? '缺货' : `剩 ${sku.stock}` }}
                  </el-tag>
                </div>
                <div class="todo-sub">{{ sku.specText || '默认规格' }}</div>
              </div>
            </div>
          </div>
          <el-empty v-else description="库存充足" :image-size="60" />
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.admin-page {
  min-height: 320px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.page-desc {
  margin: 4px 0 0;
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

.amount {
  color: var(--c-pink);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.todo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 14px;
}

.todo-list {
  display: flex;
  flex-direction: column;
}

.todo {
  padding: 8px 6px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
  border-radius: 4px;
  cursor: pointer;
}

.todo:last-child {
  border-bottom: none;
}

.todo:hover,
.low-stock:hover {
  background: var(--el-fill-color-lighter);
}

.todo-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 13px;
}

.todo-sub {
  margin-top: 2px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.mono {
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 12px;
}

.muted {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.low-stock {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 6px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
  border-radius: 4px;
  cursor: pointer;
}

.low-stock:last-child {
  border-bottom: none;
}

.thumb {
  width: 38px;
  height: 38px;
  flex: none;
  overflow: hidden;
  border-radius: 6px;
}

.low-stock-info {
  flex: 1;
  min-width: 0;
}

@media (max-width: 720px) {
  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
