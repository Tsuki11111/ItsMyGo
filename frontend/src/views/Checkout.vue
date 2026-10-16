<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import ProductImage from '@/components/ProductImage.vue'
import { fetchProduct, fetchStock } from '@/api/product'
import { createAddress, fetchAddresses } from '@/api/address'
import { createOrder, fetchOrderToken } from '@/api/order'
import { useUserStore } from '@/stores/user'
import type { Address, Product } from '@/api/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const product = ref<Product | null>(null)
const quantity = ref(1)
const addresses = ref<Address[]>([])
const addressId = ref<number | null>(null)
/**
 * 提交令牌，**进页面就领好、提交时复用同一个**。
 * 提交时才领的话，连点两次会各领一个新令牌，防重复提交就形同虚设（见 implementation §3.7）。
 */
const token = ref('')

const loading = ref(true)
const submitting = ref(false)

/** 「立即购买」的商品来自 URL —— 刷新页面也不丢 */
const buyNowId = computed(() => Number(route.query.productId) || 0)
const total = computed(() => (product.value ? product.value.price * quantity.value : 0))

function fullAddress(addr: Address): string {
  return `${addr.province}${addr.city}${addr.district}${addr.detail}`
}

onMounted(async () => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.replace({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  loading.value = true
  try {
    await Promise.all([loadProduct(), loadAddresses()])
    token.value = await fetchOrderToken()
  } catch {
    // 商品不存在之类，拦截器已经提示过
  } finally {
    loading.value = false
  }
})

async function loadProduct() {
  if (!buyNowId.value) return
  product.value = await fetchProduct(buyNowId.value)

  // 数量按实时库存夹一下：详情页到结算页之间可能被人买走
  const stock = (await fetchStock(buyNowId.value)).stock
  const asked = Number(route.query.quantity) || 1
  quantity.value = Math.min(Math.max(asked, 1), Math.max(stock, 1))
}

async function loadAddresses() {
  addresses.value = await fetchAddresses()
  const preferred = addresses.value.find((a) => a.isDefault === 1) ?? addresses.value[0]
  addressId.value = preferred?.id ?? null
}

async function submitOrder() {
  if (!addressId.value || !product.value) {
    ElMessage.warning('请先选择收货地址')
    return
  }

  submitting.value = true
  try {
    const view = await createOrder({
      addressId: addressId.value,
      token: token.value,
      // 有 items 就是「立即购买」——后端不会碰购物车
      items: [{ productId: product.value.id, quantity: quantity.value }],
    })
    ElMessage.success('下单成功')
    router.replace(`/order/${view.order.id}`)
  } catch {
    // 库存不足 / 令牌失效等，拦截器已提示。
    // 业务失败时后端会把令牌还回来，所以用户改完还能直接重试。
  } finally {
    submitting.value = false
  }
}

// ── 新增地址 ──

const dialogVisible = ref(false)
const savingAddress = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  receiver: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: false,
})

const addrRules: FormRules = {
  receiver: [{ required: true, message: '请输入收货人', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }],
  province: [{ required: true, message: '请输入省份', trigger: 'blur' }],
  city: [{ required: true, message: '请输入城市', trigger: 'blur' }],
  district: [{ required: true, message: '请输入区/县', trigger: 'blur' }],
  detail: [{ required: true, message: '请输入详细地址', trigger: 'blur' }],
}

function openAddressDialog() {
  form.receiver = ''
  form.phone = ''
  form.province = ''
  form.city = ''
  form.district = ''
  form.detail = ''
  // 第一个地址默认设成默认地址，省得用户还要多点一下
  form.isDefault = addresses.value.length === 0
  dialogVisible.value = true
}

async function submitAddress() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  savingAddress.value = true
  try {
    const created = await createAddress({ ...form })
    dialogVisible.value = false
    ElMessage.success('地址已保存')
    await loadAddresses()
    addressId.value = created.id
  } catch {
    // 参数不合法等，拦截器已提示
  } finally {
    savingAddress.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="checkout-page">
    <h1 class="page-title">确认订单</h1>

    <el-empty v-if="!loading && !product" description="没有要结算的商品">
      <el-button type="primary" @click="router.push('/')">去逛逛</el-button>
    </el-empty>

    <template v-else-if="product">
      <!-- ── 收货地址 ── -->
      <section class="block">
        <div class="block-head">
          <h2 class="block-title">收货地址</h2>
          <button type="button" class="text-btn" @click="openAddressDialog">+ 新增地址</button>
        </div>

        <p v-if="!addresses.length" class="empty-tip">
          还没有收货地址，先添加一个才能下单。
        </p>

        <div v-else class="addr-list">
          <div
            v-for="addr in addresses"
            :key="addr.id"
            class="addr-card"
            :class="{ active: addr.id === addressId }"
            @click="addressId = addr.id"
          >
            <p class="addr-line">
              <span class="addr-name">{{ addr.receiver }}</span>
              <span class="addr-phone">{{ addr.phone }}</span>
              <span v-if="addr.isDefault === 1" class="addr-tag">默认</span>
            </p>
            <p class="addr-detail">{{ fullAddress(addr) }}</p>
          </div>
        </div>
      </section>

      <!-- ── 商品清单 ── -->
      <section class="block">
        <h2 class="block-title">商品清单</h2>
        <div class="goods-row">
          <div class="goods-thumb">
            <ProductImage :src="product.mainImage" :name="product.name" :id="product.id" />
          </div>
          <div class="goods-info">
            <p class="goods-name">{{ product.name }}</p>
            <p v-if="product.subtitle" class="goods-sub">{{ product.subtitle }}</p>
          </div>
          <div class="price goods-price">
            <span class="price-symbol">¥</span>
            <span class="price-num">{{ product.price.toFixed(2) }}</span>
          </div>
          <div class="goods-qty">× {{ quantity }}</div>
        </div>
      </section>

      <!-- ── 提交 ── -->
      <section class="block submit-block">
        <div class="total-row">
          <span class="total-label">应付金额</span>
          <span class="price">
            <span class="price-symbol">¥</span>
            <span class="price-num total-num">{{ total.toFixed(2) }}</span>
          </span>
        </div>
        <button
          type="button"
          class="submit-btn"
          :disabled="submitting || !addressId"
          @click="submitOrder"
        >
          {{ submitting ? '提交中…' : '提交订单' }}
        </button>
      </section>
    </template>

    <!-- ── 新增地址 ── -->
    <el-dialog v-model="dialogVisible" title="新增收货地址" width="460px">
      <el-form ref="formRef" :model="form" :rules="addrRules" label-width="84px">
        <el-form-item label="收货人" prop="receiver">
          <el-input v-model="form.receiver" placeholder="不超过 32 个字符" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" placeholder="手机号或座机" />
        </el-form-item>
        <el-form-item label="省份" prop="province">
          <el-input v-model="form.province" placeholder="如：广东省" />
        </el-form-item>
        <el-form-item label="城市" prop="city">
          <el-input v-model="form.city" placeholder="如：深圳市" />
        </el-form-item>
        <el-form-item label="区/县" prop="district">
          <el-input v-model="form.district" placeholder="如：南山区" />
        </el-form-item>
        <el-form-item label="详细地址" prop="detail">
          <el-input v-model="form.detail" type="textarea" :rows="2" placeholder="街道、门牌号等" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingAddress" @click="submitAddress">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 600;
  color: var(--c-text);
}

.block {
  margin-bottom: 18px;
  padding: 20px 22px;
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.block-title {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text);
}

.text-btn {
  margin-bottom: 14px;
  padding: 0;
  font-size: 13px;
  font-family: inherit;
  color: var(--c-pink);
  background: none;
  border: none;
  cursor: pointer;
}

.text-btn:hover {
  color: var(--c-pink-hover);
}

.empty-tip {
  margin: 0;
  font-size: 13px;
  color: var(--c-text-3);
}

/* ── 地址卡片 ── */
.addr-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.addr-card {
  padding: 12px 14px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-tag);
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.addr-card:hover {
  border-color: var(--c-pink-soft);
}

/* 选中态是粉色允许出现的位置之一 */
.addr-card.active {
  border-color: var(--c-pink);
  background: var(--c-pink-tint);
}

.addr-line {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
}

.addr-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
}

.addr-phone {
  font-size: 13px;
  color: var(--c-text-2);
}

.addr-tag {
  padding: 1px 6px;
  font-size: 11px;
  color: var(--c-pink);
  background: var(--c-pink-soft);
  border-radius: 4px;
}

.addr-detail {
  margin: 6px 0 0;
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--c-text-2);
}

/* ── 商品行 ── */
.goods-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.goods-thumb {
  width: 64px;
  height: 64px;
  flex: none;
  border-radius: var(--radius-tag);
  overflow: hidden;
  background: var(--c-pink-tint);
  font-size: 22px;
}

.goods-info {
  flex: 1;
  min-width: 0;
}

.goods-name {
  margin: 0;
  font-size: 14px;
  color: var(--c-text);
}

.goods-sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
}

.goods-price .price-num {
  font-size: 15px;
}

.goods-qty {
  width: 56px;
  text-align: right;
  font-size: 13px;
  color: var(--c-text-2);
}

/* ── 提交区 ── */
.submit-block {
  display: flex;
  align-items: center;
  gap: 28px;
}

.total-row {
  margin-left: auto;
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.total-label {
  font-size: 13px;
  color: var(--c-text-2);
}

.total-num {
  font-size: 26px;
}

.submit-btn {
  flex: none;
  width: 180px;
  padding: 13px 0;
  font-size: 15px;
  font-family: inherit;
  color: #fff;
  background: var(--c-pink);
  border: none;
  border-radius: var(--radius-btn);
  cursor: pointer;
  transition: background 0.15s;
}

.submit-btn:hover:not(:disabled) {
  background: var(--c-pink-hover);
}

.submit-btn:disabled {
  background: var(--c-text-3);
  cursor: not-allowed;
}

@media (max-width: 720px) {
  .submit-block {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
  }
  .total-row {
    margin-left: 0;
    justify-content: space-between;
  }
  .submit-btn {
    width: 100%;
  }
}
</style>
