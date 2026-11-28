<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Present } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { fetchMyLotteryPrizes, saveLotteryClaimAddress } from '@/api/lottery'
import type { LotteryPrizeRecordView } from '@/api/types'

const router = useRouter()
const items = ref<LotteryPrizeRecordView[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const editing = ref<LotteryPrizeRecordView | null>(null)
const form = reactive({
  receiverName: '',
  receiverPhone: '',
  receiverAddress: '',
})

async function load() {
  loading.value = true
  try {
    const result = await fetchMyLotteryPrizes(page.value, size)
    items.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function onPageChange(next: number) {
  page.value = next
  load()
}

function openAddress(item: LotteryPrizeRecordView) {
  if (!item.claim) return
  editing.value = item
  form.receiverName = item.claim.receiverName
  form.receiverPhone = item.claim.receiverPhone
  form.receiverAddress = item.claim.receiverAddress
  dialogVisible.value = true
}

async function submitAddress() {
  if (!editing.value?.claim) return
  if (!form.receiverName.trim() || !form.receiverPhone.trim() || !form.receiverAddress.trim()) {
    ElMessage.warning('请填写完整的收货信息')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(form.receiverPhone.trim())) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  saving.value = true
  try {
    await saveLotteryClaimAddress(editing.value.claim.id, {
      receiverName: form.receiverName.trim(),
      receiverPhone: form.receiverPhone.trim(),
      receiverAddress: form.receiverAddress.trim(),
    })
    ElMessage.success('收货信息已保存')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function couponStatusText(status: string | null): string {
  if (status === 'UNUSED') return '未使用'
  if (status === 'USED') return '已使用'
  if (status === 'EXPIRED') return '已过期'
  return '已发放'
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(load)
</script>

<template>
  <div class="prizes-page">
    <h2 class="page-title">我的奖品</h2>

    <div v-loading="loading" class="prize-list">
      <el-empty v-if="!loading && items.length === 0" description="还没有中奖记录">
        <el-button type="primary" @click="router.push('/activities')">去活动中心</el-button>
      </el-empty>

      <article v-for="item in items" :key="item.id" class="prize-card">
        <div class="prize-thumb">
          <img v-if="item.prizeImage" :src="item.prizeImage" :alt="item.prizeName" />
          <el-icon v-else><Present /></el-icon>
        </div>
        <div class="prize-main">
          <div class="prize-head">
            <span class="prize-name">{{ item.prizeName }}</span>
            <el-tag size="small" :type="item.prizeType === 'COUPON' ? 'success' : 'warning'">
              {{ item.prizeType === 'COUPON' ? '优惠券' : '实物奖品' }}
            </el-tag>
          </div>
          <p class="prize-meta">
            来自「{{ item.activityName }}」 · {{ formatTime(item.drawnAt) }}
            <template v-if="item.prizeCode"> · 中奖码 <code>{{ item.prizeCode }}</code></template>
          </p>

          <div v-if="item.prizeType === 'COUPON'" class="prize-status">
            <span>优惠券状态：{{ couponStatusText(item.couponStatus) }}</span>
            <el-button link type="primary" @click="router.push('/user/coupons')">
              去我的优惠券
            </el-button>
          </div>

          <div v-else class="prize-status">
            <span v-if="item.claim">
              {{ item.claim.statusLabel }}
              <template v-if="item.claim.trackingNo"> · 运单号 {{ item.claim.trackingNo }}</template>
            </span>
            <span v-if="item.claim?.receiverAddress" class="prize-address">
              收货：{{ item.claim.receiverName }} {{ item.claim.receiverPhone }} ·
              {{ item.claim.receiverAddress }}
            </span>
            <el-button
              v-if="item.claim && item.claim.status !== 'SHIPPED'"
              link
              type="primary"
              @click="openAddress(item)"
            >
              {{ item.claim.status === 'PENDING_ADDRESS' ? '填写收货地址' : '修改收货地址' }}
            </el-button>
          </div>
        </div>
      </article>
    </div>

    <div v-if="total > size" class="pager">
      <el-pagination
        layout="prev, pager, next"
        :current-page="page"
        :page-size="size"
        :total="total"
        @current-change="onPageChange"
      />
    </div>

    <el-dialog v-model="dialogVisible" title="收货信息" width="460px">
      <el-form label-width="80px">
        <el-form-item label="收货人">
          <el-input v-model="form.receiverName" maxlength="32" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.receiverPhone" maxlength="11" />
        </el-form-item>
        <el-form-item label="收货地址">
          <el-input
            v-model="form.receiverAddress"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAddress">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.prizes-page {
  max-width: 820px;
}

.page-title {
  margin: 0 0 16px;
  font-size: 18px;
  color: var(--c-text);
}

.prize-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}

.prize-card {
  display: flex;
  gap: 14px;
  padding: 16px;
  background: #fff;
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.prize-thumb {
  flex: none;
  width: 72px;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #f7f7fa;
  border-radius: 10px;
  color: var(--c-pink);
  font-size: 28px;
}

.prize-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.prize-main {
  flex: 1;
  min-width: 0;
}

.prize-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.prize-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text);
}

.prize-meta {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
}

.prize-meta code {
  font-family: ui-monospace, 'SF Mono', Consolas, monospace;
  color: var(--c-pink);
}

.prize-status {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--c-text-2);
}

.prize-address {
  font-size: 12px;
  color: var(--c-text-3);
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
