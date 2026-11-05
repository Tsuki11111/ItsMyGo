<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelAfterSale, fetchAfterSale, shipBackAfterSale } from '@/api/afterSale'
import type { AfterSaleView } from '@/api/types'

const route = useRoute()
const router = useRouter()
const detail = ref<AfterSaleView | null>(null)
const loading = ref(true)
const acting = ref(false)

const statusType = computed(() => {
  switch (detail.value?.status) {
    case 'REFUNDED':
      return 'success'
    case 'REJECTED':
    case 'CANCELLED':
      return 'info'
    case 'PENDING_REVIEW':
      return 'warning'
    default:
      return 'primary'
  }
})

async function load() {
  loading.value = true
  try {
    detail.value = await fetchAfterSale(Number(route.params.id))
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function onCancel() {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm('确定撤销这次售后申请吗？', '撤销售后', { type: 'warning' })
  } catch {
    return
  }
  acting.value = true
  try {
    detail.value = await cancelAfterSale(detail.value.id)
    ElMessage.success('已撤销')
  } finally {
    acting.value = false
  }
}

async function onShipBack() {
  if (!detail.value) return
  const { value } = await ElMessageBox.prompt('请填写退货快递单号', '填写退货物流', {
    inputPlaceholder: '例如：SF1234567890',
    inputValidator: (v: string) => (v && v.trim() ? true : '请填写单号'),
  })
  acting.value = true
  try {
    detail.value = await shipBackAfterSale(detail.value.id, { trackingNo: value.trim() })
    ElMessage.success('已提交退货单号，等待商家确认收货')
  } finally {
    acting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="after-sale-page">
    <el-empty v-if="!detail && !loading" description="售后单不存在">
      <el-button type="primary" @click="router.push('/user/after-sales')">返回我的售后</el-button>
    </el-empty>

    <template v-else-if="detail">
      <el-breadcrumb separator="/" class="crumb">
        <el-breadcrumb-item :to="{ path: '/user/after-sales' }">我的售后</el-breadcrumb-item>
        <el-breadcrumb-item>售后详情</el-breadcrumb-item>
      </el-breadcrumb>

      <section class="card">
        <div class="head">
          <div>
            <span class="no">售后单号 {{ detail.afterSaleNo }}</span>
            <span class="sub">订单号 {{ detail.orderNo }}</span>
          </div>
          <el-tag :type="statusType">{{ detail.statusLabel }}</el-tag>
        </div>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="售后类型">{{ detail.typeLabel }}</el-descriptions-item>
          <el-descriptions-item label="店铺">{{ detail.merchantName }}</el-descriptions-item>
          <el-descriptions-item label="申请原因">
            {{ detail.reasonLabel }}<span v-if="detail.reasonText">（{{ detail.reasonText }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="申请金额">¥{{ Number(detail.applyAmount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.refundAmount > 0" label="实际退款">
            ¥{{ Number(detail.refundAmount).toFixed(2) }}
          </el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ detail.createdAt }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.rejectReason" label="驳回理由">
            {{ detail.rejectReason }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.returnTrackingNo" label="退货物流">
            {{ detail.returnCarrier }} {{ detail.returnTrackingNo }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="detail.evidenceImages.length" class="evidence">
          <el-image
            v-for="url in detail.evidenceImages"
            :key="url"
            :src="url"
            fit="cover"
            class="evidence-img"
            :preview-src-list="detail.evidenceImages"
          />
        </div>

        <div class="actions">
          <el-button v-if="detail.canCancel" :loading="acting" @click="onCancel">撤销申请</el-button>
          <el-button v-if="detail.canShipBack" type="primary" :loading="acting" @click="onShipBack">
            填写退货单号
          </el-button>
          <el-button @click="router.push(`/order/${detail.orderId}`)">查看订单</el-button>
        </div>
      </section>

      <section class="card">
        <h3 class="title">退款商品</h3>
        <el-table :data="detail.items" size="small">
          <el-table-column label="商品" min-width="220">
            <template #default="{ row }">
              {{ row.productName }}<span v-if="row.specText" class="sub">（{{ row.specText }}）</span>
            </template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="80" />
          <el-table-column label="单价" width="110">
            <template #default="{ row }">¥{{ Number(row.unitPrice).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="退款金额" width="120">
            <template #default="{ row }">¥{{ Number(row.refundAmount).toFixed(2) }}</template>
          </el-table-column>
        </el-table>
      </section>

      <section class="card">
        <h3 class="title">处理进度</h3>
        <el-timeline>
          <el-timeline-item
            v-for="(item, index) in detail.timeline"
            :key="index"
            :timestamp="item.time"
            :type="index === detail.timeline.length - 1 ? 'primary' : ''"
          >
            <div class="timeline-title">{{ item.title }}</div>
            <div v-if="item.detail" class="sub">{{ item.detail }}</div>
          </el-timeline-item>
        </el-timeline>
      </section>
    </template>
  </div>
</template>

<style scoped>
.after-sale-page {
  max-width: 920px;
  margin: 0 auto;
  padding: 20px 16px 60px;
}
.crumb {
  margin-bottom: 12px;
}
.card {
  background: #fff;
  border-radius: 10px;
  padding: 18px 20px;
  margin-bottom: 14px;
  box-shadow: 0 2px 10px rgba(180, 140, 150, 0.08);
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.no {
  font-weight: 600;
  margin-right: 12px;
}
.sub {
  color: #999;
  font-size: 12px;
}
.title {
  margin: 0 0 10px;
  font-size: 15px;
}
.evidence {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}
.evidence-img {
  width: 84px;
  height: 84px;
  border-radius: 6px;
  border: 1px solid #f0e4e8;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 14px;
}
.timeline-title {
  font-size: 14px;
  font-weight: 500;
}
</style>
