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
import { fetchShop } from '@/api/shop'
import { useBadgeStore } from '@/stores/badge'
import { useUserStore } from '@/stores/user'
import type { Address, CartItemView, Product, ProductSku, UserCouponView } from '@/api/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const badgeStore = useBadgeStore()

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

interface CheckoutLine {
  key: string
  productId: number
  skuId: number
  productName: string
  productImage: string
  specText: string
  price: number
  quantity: number
  subtotal: number
}

/** 结算页按店铺分组：每个店一个券选择器，提交后后端按店拆单 */
interface CheckoutGroup {
  key: string
  merchantId: number
  merchantName: string
  lines: CheckoutLine[]
  goodsTotal: number
  coupons: UserCouponView[]
  couponId: number | null
}

const groups = ref<CheckoutGroup[]>([])

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

const goodsTotal = computed(() =>
  groups.value.reduce((sum, group) => sum + group.goodsTotal, 0),
)

function groupDiscount(group: CheckoutGroup): number {
  const coupon = group.coupons.find((item) => item.id === group.couponId)
  return coupon ? Math.min(coupon.discountAmount, group.goodsTotal) : 0
}

/** 应付金额 = 商品总额 - 各店券减免 */
const discount = computed(() =>
  groups.value.reduce((sum, group) => sum + groupDiscount(group), 0),
)
const total = computed(() => Math.max(goodsTotal.value - discount.value, 0))

function fullAddress(addr: Address): string {
  return `${addr.province}${addr.city}${addr.district}${addr.detail}`
}

function toBuyNowLine(): CheckoutLine | null {
  if (!product.value || !sku.value) return null
  return {
    key: `p${product.value.id}`,
    productId: product.value.id,
    skuId: sku.value.id,
    productName: product.value.name,
    productImage: sku.value.image || product.value.mainImage,
    specText: sku.value.specText,
    price: sku.value.price,
    quantity: quantity.value,
    subtotal: sku.value.price * quantity.value,
  }
}

async function buildGroups() {
  const byMerchant = new Map<number, CheckoutLine[]>()
  let merchantNames = new Map<number, string>()

  if (fromCart.value) {
    for (const item of cartItems.value) {
      if (item.merchantId == null) continue
      const line: CheckoutLine = {
        key: `c${item.id}`,
        productId: item.productId,
        skuId: item.skuId,
        productName: item.productName,
        productImage: item.productImage,
        specText: item.specText,
        price: item.price,
        quantity: item.quantity,
        subtotal: item.subtotal,
      }
      const list = byMerchant.get(item.merchantId) ?? []
      list.push(line)
      byMerchant.set(item.merchantId, list)
      merchantNames.set(item.merchantId, item.merchantName)
    }
  } else {
    const line = toBuyNowLine()
    if (line && product.value) {
      byMerchant.set(product.value.merchantId, [line])
      try {
        const shop = await fetchShop(product.value.merchantId)
        merchantNames.set(product.value.merchantId, shop.name)
      } catch {
        merchantNames.set(product.value.merchantId, `店铺 #${product.value.merchantId}`)
      }
    }
  }

  groups.value = [...byMerchant.entries()].map(([merchantId, lines]) => ({
    key: String(merchantId),
    merchantId,
    merchantName: merchantNames.get(merchantId) ?? `店铺 #${merchantId}`,
    lines,
    goodsTotal: lines.reduce((sum, line) => sum + line.subtotal, 0),
    coupons: [],
    couponId: null,
  }))
  await loadCouponsForGroups()
}

/** 每个店分别取可用券：平台券 + 该店铺券 */
async function loadCouponsForGroups() {
  if (!userStore.isLoggedIn) return
  await Promise.all(groups.value.map(async (group) => {
    try {
      group.coupons = await fetchAvailableCoupons(group.goodsTotal, group.merchantId)
    } catch {
      group.coupons = []
    }
    if (group.couponId !== null
        && !group.coupons.some((coupon) => coupon.id === group.couponId)) {
      group.couponId = null
    }
  }))
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
    await buildGroups()
  } catch {
    // 商品不存在之类，拦截器已经提示过
  } finally {
    loading.value = false
  }
})

// 立即购买改数量会改变金额：重新分组并刷新可用券
watch(quantity, () => {
  if (!loading.value && !fromCart.value) buildGroups()
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
 * 下单时请求体里不传 `items`，后端就会按购物车的勾选项走并清空它们。
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
  if (!hasGoods.value || groups.value.length === 0) {
    ElMessage.warning(fromCart.value ? '请先在购物车里勾选商品' : '没有要结算的商品')
    return
  }
  if (!fromCart.value && (!product.value || !sku.value)) {
    ElMessage.warning('没有要结算的商品')
    return
  }

  submitting.value = true
  try {
    const coupons = groups.value
      .filter((group) => group.couponId !== null)
      .map((group) => ({ merchantId: group.merchantId, userCouponId: group.couponId! }))

    // 从购物车结算**不传 items**，后端按购物车勾选行分组拆单；
    // 立即购买才传 items，且只属于一个店铺。
    const view = await createOrder({
      addressId: addr,
      token: token.value,
      coupons,
      ...(fromCart.value ? {} : {
        items: [{
          productId: product.value!.id,
          skuId: sku.value!.id,
          quantity: quantity.value,
        }],
      }),
    })
    if (fromCart.value) {
      // 下单只清空勾选行，未勾选的商品还在购物车里 —— 以服务端为准
      void badgeStore.refreshCart()
    }

    ElMessage.success(view.orders.length > 1
      ? `已生成 ${view.orders.length} 个店铺订单，请分别支付`
      : '下单成功')
    const ids = view.orders.map((order) => order.order.id).join(',')
    router.replace({ path: '/checkout/result', query: { ids } })
  } catch {
    // 库存不足 / 令牌失效等，拦截器已提示。
    // 业务失败时后端会把令牌还回来，所以用户改完还能直接重试。
  } finally {
    submitting.value = false
  }
}

// ── 新增地址 ──
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

      <!-- ── 商品清单（按店铺分组，一店一个券选择器） ── -->
      <section v-for="group in groups" :key="group.key" class="block">
        <div class="block-head">
          <h2 class="block-title">{{ group.merchantName }}</h2>
          <span class="shop-total">小计 ¥{{ group.goodsTotal.toFixed(2) }}</span>
        </div>

        <div v-for="line in group.lines" :key="line.key" class="goods-row">
          <div class="goods-thumb">
            <ProductImage :src="line.productImage" :name="line.productName" :id="line.productId" />
          </div>
          <div class="goods-info">
            <p class="goods-name">{{ line.productName }}</p>
            <p v-if="line.specText" class="goods-sub">{{ line.specText }}</p>
          </div>
          <div class="price goods-price">
            <span class="price-symbol">¥</span>
            <span class="price-num">{{ line.price.toFixed(2) }}</span>
          </div>
          <div class="goods-qty">× {{ line.quantity }}</div>
        </div>

        <div class="coupon-row">
          <span class="coupon-label">优惠券</span>
          <el-select
            v-model="group.couponId"
            clearable
            placeholder="不使用优惠券"
            style="width: 380px"
          >
            <el-option
              v-for="coupon in group.coupons"
              :key="coupon.id"
              :value="coupon.id"
              :label="`${coupon.couponName}（满 ${coupon.thresholdAmount.toFixed(2)} 减 ${coupon.discountAmount.toFixed(2)}）`"
            />
          </el-select>
          <span v-if="!group.coupons.length" class="empty-tip">没有可用的优惠券</span>
          <span v-if="groupDiscount(group) > 0" class="discount-amount">
            -¥{{ groupDiscount(group).toFixed(2) }}
          </span>
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

.shop-total {
  font-size: 12px;
  color: var(--c-text-3);
}

.coupon-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--c-border);
}

.coupon-label {
  font-size: 13px;
  color: var(--c-text-2);
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
