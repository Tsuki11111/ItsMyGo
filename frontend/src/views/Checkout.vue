<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ProductImage from '@/components/ProductImage.vue'
import AddressFormDialog from '@/components/AddressFormDialog.vue'
import { fetchProduct } from '@/api/product'
import { fetchCart } from '@/api/cart'
import { fetchAddresses } from '@/api/address'
import { createOrder, fetchOrderToken } from '@/api/order'
import { fetchAvailableCoupons } from '@/api/coupon'
import { useUserStore } from '@/stores/user'
import type { Address, CartItemView, Product, ProductSku, UserCouponView } from '@/api/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const product = ref<Product | null>(null)
/** 购物车结算时，要下单的那几行（只取**勾选且可买**的） */
const cartItems = ref<CartItemView[]>([])
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
const buyNowSkuId = computed(() => Number(route.query.skuId) || 0)

/** 没带 productId 就是从购物车进来的。两种来源共用这一个页面 */
const fromCart = computed(() => !buyNowId.value)
const hasGoods = computed(() => (fromCart.value ? cartItems.value.length > 0 : Boolean(product.value)))

/**
 * 要买的那一款。正常是详情页带上来的那个 skuId；
 * 兜底取默认 SKU（无规格商品），免得老链接或者手敲 URL 时整页报错。
 */
const sku = computed<ProductSku | null>(() => {
  const skus = product.value?.skus ?? []
  if (!skus.length) return null
  return skus.find((s) => s.id === buyNowSkuId.value)
      ?? skus.find((s) => s.specValueIds === '')
      ?? null
})

/** 单价取**那一款**的，不是商品的最低价 */
const unitPrice = computed(() => sku.value?.price ?? product.value?.price ?? 0)
/** 购物车结算时，金额同样只算「勾选且可买」的行 */
const goodsTotal = computed(() =>
  fromCart.value
    ? cartItems.value.reduce((sum, item) => sum + item.subtotal, 0)
    : unitPrice.value * quantity.value,
)

/** 可用券与选中的券 */
const availableCoupons = ref<UserCouponView[]>([])
const couponId = ref<number | null>(null)
const selectedCoupon = computed(() =>
  availableCoupons.value.find((coupon) => coupon.id === couponId.value) ?? null,
)
const discount = computed(() =>
  selectedCoupon.value
    ? Math.min(selectedCoupon.value.discountAmount, goodsTotal.value)
    : 0,
)
/** 应付金额 = 商品总额 - 券减免 */
const total = computed(() => Math.max(goodsTotal.value - discount.value, 0))

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
    await Promise.all([fromCart.value ? loadCart() : loadProduct(), loadAddresses()])
    token.value = await fetchOrderToken()
    await loadCoupons()
  } catch {
    // 商品不存在之类，拦截器已经提示过
  } finally {
    loading.value = false
  }
})

/** 按当前商品总额筛可用券；切换数量时重新拉，避免展示不可用的券 */
async function loadCoupons() {
  if (!userStore.isLoggedIn) return
  try {
    availableCoupons.value = await fetchAvailableCoupons(goodsTotal.value)
    if (couponId.value !== null
        && !availableCoupons.value.some((coupon) => coupon.id === couponId.value)) {
      couponId.value = null
    }
  } catch {
    availableCoupons.value = []
  }
}

// 立即购买改数量会改变金额：可用券集合可能变化
watch(quantity, () => {
  if (!loading.value) loadCoupons()
})

async function loadProduct() {
  if (!buyNowId.value) return
  product.value = await fetchProduct(buyNowId.value)

  // 数量按**这一款**的实时库存夹一下：从详情页走到结算页之间可能被人买走
  const available = sku.value?.stock ?? 0
  const asked = Number(route.query.quantity) || 1
  quantity.value = Math.min(Math.max(asked, 1), Math.max(available, 1))
}

/**
 * 购物车结算：只把**勾选且可买**的行带进来。
 * 下单时请求体里不传 `items`，后端就会按购物车的勾选项走（见 implementation §3.7）——
 * 所以这里只负责展示，不用把商品再传一遍。
 */
async function loadCart() {
  const view = await fetchCart()
  cartItems.value = view.items.filter((item) => item.selected && item.available)
}

async function loadAddresses() {
  addresses.value = await fetchAddresses()
  const preferred = addresses.value.find((a) => a.isDefault === 1) ?? addresses.value[0]
  addressId.value = preferred?.id ?? null
}

async function submitOrder() {
  const addr = addressId.value
  if (!addr) {
    ElMessage.warning('请先选择收货地址')
    return
  }
  if (!hasGoods.value) {
    ElMessage.warning(fromCart.value ? '请先在购物车里勾选商品' : '没有要结算的商品')
    return
  }
  if (!fromCart.value && (!product.value || !sku.value)) {
    ElMessage.warning('没有要结算的商品')
    return
  }

  submitting.value = true
  try {
    // 从购物车结算**不传 items** —— 后端按购物车的勾选行下单并清空它们；
    // 立即购买才传 items，那条路完全不碰购物车（见 implementation §3.7）
    const view = fromCart.value
      ? await createOrder({
          addressId: addr,
          token: token.value,
          couponId: couponId.value ?? undefined,
        })
      : await createOrder({
          addressId: addr,
          token: token.value,
          couponId: couponId.value ?? undefined,
          items: [{
            productId: product.value!.id,
            skuId: sku.value!.id,
            quantity: quantity.value,
          }],
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
// 表单本身是公共组件（用户中心的地址管理页也在用同一份）
const dialogVisible = ref(false)

async function onAddressSaved(address: Address) {
  await loadAddresses()
  addressId.value = address.id
}
</script>

<template>
  <div v-loading="loading" class="checkout-page">
    <h1 class="page-title">确认订单</h1>

    <el-empty
      v-if="!loading && !hasGoods"
      :description="fromCart ? '购物车里还没有勾选可结算的商品' : '没有要结算的商品'"
    >
      <el-button type="primary" @click="router.push(fromCart ? '/cart' : '/')">
        {{ fromCart ? '回购物车' : '去逛逛' }}
      </el-button>
    </el-empty>

    <template v-else-if="hasGoods">
      <!-- ── 收货地址 ── -->
      <section class="block">
        <div class="block-head">
          <h2 class="block-title">收货地址</h2>
          <button type="button" class="text-btn" @click="dialogVisible = true">+ 新增地址</button>
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

        <!-- 购物车结算：勾选且可买的那几行 -->
        <template v-if="fromCart">
          <div v-for="item in cartItems" :key="item.id" class="goods-row">
            <div class="goods-thumb">
              <ProductImage :src="item.productImage" :name="item.productName" :id="item.productId" />
            </div>
            <div class="goods-info">
              <p class="goods-name">{{ item.productName }}</p>
              <p v-if="item.specText" class="goods-sub">{{ item.specText }}</p>
            </div>
            <div class="price goods-price">
              <span class="price-symbol">¥</span>
              <span class="price-num">{{ item.price.toFixed(2) }}</span>
            </div>
            <div class="goods-qty">× {{ item.quantity }}</div>
          </div>
        </template>

        <!-- 立即购买：只有一件 -->
        <div v-else-if="product" class="goods-row">
          <div class="goods-thumb">
            <ProductImage :src="sku?.image || product.mainImage" :name="product.name" :id="product.id" />
          </div>
          <div class="goods-info">
            <p class="goods-name">{{ product.name }}</p>
            <!-- 有规格就显示规格（这才是「买的是哪一款」），没有才退回副标题 -->
            <p v-if="sku?.specText" class="goods-sub">{{ sku.specText }}</p>
            <p v-else-if="product.subtitle" class="goods-sub">{{ product.subtitle }}</p>
          </div>
          <div class="price goods-price">
            <span class="price-symbol">¥</span>
            <span class="price-num">{{ unitPrice.toFixed(2) }}</span>
          </div>
          <div class="goods-qty">× {{ quantity }}</div>
        </div>
      </section>

      <!-- ── 优惠券 ── -->
      <section class="block">
        <h2 class="block-title">优惠券</h2>
        <div class="coupon-row">
          <el-select
            v-model="couponId"
            clearable
            placeholder="不使用优惠券"
            style="width: 380px"
          >
            <el-option
              v-for="coupon in availableCoupons"
              :key="coupon.id"
              :value="coupon.id"
              :label="`${coupon.couponName}（满 ${coupon.thresholdAmount.toFixed(2)} 减 ${coupon.discountAmount.toFixed(2)}）`"
            />
          </el-select>
          <span v-if="!availableCoupons.length" class="empty-tip">没有可用的优惠券</span>
        </div>
      </section>

      <!-- ── 提交 ── -->
      <section class="block submit-block">
        <div class="amounts">
          <div v-if="discount > 0" class="total-row sub">
            <span class="total-label">商品总额</span>
            <span class="sub-amount">¥{{ goodsTotal.toFixed(2) }}</span>
          </div>
          <div v-if="discount > 0" class="total-row sub">
            <span class="total-label">优惠券</span>
            <span class="discount-amount">-¥{{ discount.toFixed(2) }}</span>
          </div>
          <div class="total-row">
            <span class="total-label">应付金额</span>
            <span class="price">
              <span class="price-symbol">¥</span>
              <span class="price-num total-num">{{ total.toFixed(2) }}</span>
            </span>
          </div>
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
    <AddressFormDialog
      v-model="dialogVisible"
      :default-when-new="addresses.length === 0"
      @saved="onAddressSaved"
    />
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

.coupon-row {
  display: flex;
  align-items: center;
  gap: 12px;
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

.amounts {
  margin-left: auto;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.total-row {
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

.sub-amount {
  font-size: 13px;
  color: var(--c-text-3);
}

.discount-amount {
  font-size: 13px;
  color: var(--c-pink);
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
