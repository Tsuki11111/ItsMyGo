<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelAfterSale, fetchAfterSales } from '@/api/afterSale'
import type { AfterSaleView } from '@/api/types'

const router = useRouter()
const records = ref<AfterSaleView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref('')
const loading = ref(false)

const STATUS_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'WAITING_RETURN', label: '待买家退货' },
  { value: 'RETURN_SHIPPED', label: '待商家收货' },
  { value: 'REFUNDED', label: '已退款' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CANCELLED', label: '已撤销' },
]

const TAG_TYPES: Record<string, string> = {
  REFUNDED: 'success',
  REJECTED: 'info',
  CANCELLED: 'info',
  PENDING_REVIEW: 'warning',
}

async function load() {
  loading.value = true
  try {
    const result = await fetchAfterSales({
      page: page.value,
      size: size.value,
      status: status.value || undefined,
    })
    records.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

async function onCancel(row: AfterSaleView) {
  try {
    await ElMessageBox.confirm('确定撤销这次售后申请吗？', '撤销售后', { type: 'warning' })
  } catch {
    return
  }
  await cancelAfterSale(row.id)
  ElMessage.success('已撤销')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="list-page">
    <div class="head">
      <h2 class="title">我的售后</h2>
      <el-select v-model="status" style="width: 160px" @change="load">
        <el-option
          v-for="option in STATUS_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
    </div>

    <el-table v-loading="loading" :data="records" size="small">
      <el-table-column prop="afterSaleNo" label="售后单号" width="190" />
      <el-table-column label="商品" min-width="220">
        <template #default="{ row }">
          {{ row.items.map((item: { productName: string }) => item.productName).join('、') }}
        </template>
      </el-table-column>
      <el-table-column prop="typeLabel" label="类型" width="100" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="TAG_TYPES[row.status] || 'primary'">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="金额" width="110">
        <template #default="{ row }">¥{{ Number(row.applyAmount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="申请时间" width="180" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/after-sale/${row.id}`)">查看</el-button>
          <el-button v-if="row.canCancel" link type="warning" @click="onCancel(row)">撤销</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="prev, pager, next, total"
        @current-change="load"
      />
    </div>
  </div>
</template>

<style scoped>
.list-page {
  background: #fff;
  border-radius: 10px;
  padding: 18px 20px;
  box-shadow: 0 2px 10px rgba(180, 140, 150, 0.08);
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.title {
  margin: 0;
  font-size: 16px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
