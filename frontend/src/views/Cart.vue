<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ProductImage from '@/components/ProductImage.vue'
import { fetchCart, removeCartItem, updateCartItem } from '@/api/cart'
import { useUserStore } from '@/stores/user'
import type { CartItemView, CartView } from '@/api/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const cart = ref<CartView | null>(null)
const loading = ref(true)
/** 正在请求的那一行 —— 用来禁用它的控件，防连点 */
const pendingId = ref<number | null>(null)

const items = computed(() => cart.value?.items ?? [])

interface CartGroup {
  key: string
  merchantId: number | null
  merchantName: string
  items: CartItemView[]
  selectable: CartItemView[]
  selectedCount: number
  allSelected: boolean
  partiallySelected: boolean
  subtotal: number
}

/** 按店铺分组：跨店购物车在结算时由后端按店拆单 */
const groups = computed<CartGroup[]>(() => {
  const map = new Map<string, CartItemView[]>()
  for (const item of items.value) {
    const key = item.merchantId == null ? 'none' : String(item.merchantId)
    const list = map.get(key) ?? []
    list.push(item)
    map.set(key, list)
  }
  return [...map.entries()].map(([key, list]) => {
    const selectable = list.filter((item) => item.available)
    const selected = selectable.filter((item) => item.selected)
    return {
      key,
      merchantId: list[0].merchantId,
      merchantName: list[0].merchantName || '店铺不存在',
      items: list,
      selectable,
      selectedCount: selected.length,
      allSelected: selectable.length > 0 && selected.length === selectable.length,
      partiallySelected: selected.length > 0 && selected.length < selectable.length,
      subtotal: selected.reduce((sum, item) => sum + item.subtotal, 0),
    }
  })
})

/** 可勾选的只有「在售且库存够」的行 —— 失效行让用户看到但选不了 */
const selectable = computed(() => items.value.filter((item) => item.available))
const selectedCount = computed(() => selectable.value.filter((item) => item.selected).length)
const allSelected = computed(() => selectable.value.length > 0 && selectedCount.value === selectable.value.length)
const partiallySelected = computed(() => selectedCount.value > 0 && !allSelected.value)
const total = computed(() => cart.value?.selectedTotal ?? 0)

onMounted(async () => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.replace({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  await load()
})

async function load() {
  loading.value = true
  try {
    cart.value = await fetchCart()
  } finally {
    loading.value = false
  }
}

/** 所有写操作都返回整份购物车，直接换掉本地状态即可，不用再查一遍 */
async function mutate(item: CartItemView, action: () => Promise<CartView>) {
  if (pendingId.value !== null) return
  pendingId.value = item.id
  try {
    cart.value = await action()
  } catch {
    // 库存不足 / 商品下架等，拦截器已提示；重新拉一次让页面回到真相
    await load()
  } finally {
    pendingId.value = null
  }
}

function toggleItem(item: CartItemView) {
  if (!item.available) return
  mutate(item, () => updateCartItem(item.id, item.quantity, !item.selected))
}

function changeQuantity(item: CartItemView, quantity: number) {
  if (quantity === item.quantity) return
  mutate(item, () => updateCartItem(item.id, quantity, item.selected))
}

/** 全选 / 取消全选：只动「能买」的那些行，失效行本来就选不了 */
async function toggleAll(selected: boolean) {
  await toggleRows(selectable.value, selected)
}

/** 组内全选 / 取消全选 */
async function toggleGroup(group: CartGroup, selected: boolean) {
  await toggleRows(group.selectable, selected)
}

async function toggleRows(candidates: CartItemView[], selected: boolean) {
  const targets = candidates.filter((item) => item.selected !== selected)
  if (!targets.length) return

  pendingId.value = -1
  try {
    let latest: CartView | null = null
    for (const item of targets) {
      latest = await updateCartItem(item.id, item.quantity, selected)
    }
    if (latest) cart.value = latest
  } catch {
    await load()
  } finally {
    pendingId.value = null
  }
}

function invalidReason(item: CartItemView): string {
  return item.onSale ? '库存不足' : '已下架或店铺已停用'
}

async function remove(item: CartItemView) {
  try {
    await ElMessageBox.confirm(`确定要从购物车里移除「${item.productName}」吗？`, '移除商品', {
      confirmButtonText: '移除',
      cancelButtonText: '再想想',
      type: 'warning',
    })
  } catch {
    return   // 点了取消
  }
  mutate(item, () => removeCartItem(item.id))
}

function goCheckout() {
  if (!selectedCount.value) {
    ElMessage.warning('请先勾选要结算的商品')
    return
  }
  router.push('/checkout')
}
</script>

<template>
  <div v-loading="loading" class="cart-page">
    <h1 class="page-title">购物车</h1>

    <el-empty v-if="!loading && items.length === 0" description="购物车还是空的">
      <el-button type="primary" @click="router.push('/')">去逛逛</el-button>
    </el-empty>

    <template v-else-if="items.length">
      <section class="block cart-toolbar">
        <div class="cart-head">
          <el-checkbox
            :model-value="allSelected"
            :indeterminate="partiallySelected"
            @change="(checked: boolean) => toggleAll(checked)"
          >
            全选
          </el-checkbox>
          <span class="head-note">价格以当前为准，下单时才会锁定</span>
        </div>
      </section>

      <section v-for="group in groups" :key="group.key" class="block shop-block">
        <div class="shop-head">
          <el-checkbox
            :model-value="group.allSelected"
            :indeterminate="group.partiallySelected"
            :disabled="group.selectable.length === 0 || pendingId !== null"
            @change="(checked: boolean) => toggleGroup(group, checked)"
          >
            <span class="shop-name">{{ group.merchantName }}</span>
          </el-checkbox>
          <div class="shop-right">
            <span class="shop-subtotal">小计 ¥{{ group.subtotal.toFixed(2) }}</span>
            <RouterLink
              v-if="group.merchantId && group.items.some((item) => item.merchantActive)"
              :to="`/shop/${group.merchantId}`"
              class="shop-link"
            >
              进店
            </RouterLink>
          </div>
        </div>

        <div v-for="item in group.items" :key="item.id" class="cart-row" :class="{ invalid: !item.available }">
          <el-checkbox
            class="row-check"
            :model-value="item.selected"
            :disabled="!item.available || pendingId !== null"
            @change="toggleItem(item)"
          />

          <div class="row-thumb">
            <ProductImage :src="item.productImage" :name="item.productName" :id="item.productId" />
          </div>

          <div class="row-info">
            <p class="row-name">{{ item.productName }}</p>
            <p v-if="item.specText" class="row-spec">{{ item.specText }}</p>
            <p v-if="!item.available" class="row-invalid">
              {{ invalidReason(item) }}，无法结算
            </p>
          </div>

          <div class="price row-price">
            <span class="price-symbol">¥</span>
            <span class="price-num">{{ item.price.toFixed(2) }}</span>
          </div>

          <el-input-number
            class="row-qty"
            :model-value="item.quantity"
            :min="1"
            :max="Math.max(item.stock, 1)"
            :disabled="!item.available || pendingId !== null"
            size="small"
            @change="(value: number | undefined) => changeQuantity(item, value ?? item.quantity)"
          />

          <div class="row-subtotal">
            <span class="price">
              <span class="price-symbol">¥</span>
              <span class="price-num">{{ item.subtotal.toFixed(2) }}</span>
            </span>
          </div>

          <button type="button" class="row-remove" :disabled="pendingId !== null" @click="remove(item)">
            移除
          </button>
        </div>
      </section>

      <!-- ── 结算栏 ── -->
      <section class="block settle">
        <el-checkbox
          :model-value="allSelected"
          :indeterminate="partiallySelected"
          @change="(checked: boolean) => toggleAll(checked)"
        >
          全选
        </el-checkbox>

        <span class="settle-count">已选 {{ selectedCount }} 件</span>

        <div class="settle-total">
          <span class="total-label">合计</span>
          <span class="price">
            <span class="price-symbol">¥</span>
            <span class="price-num total-num">{{ total.toFixed(2) }}</span>
          </span>
        </div>

        <button type="button" class="settle-btn" :disabled="!selectedCount" @click="goCheckout">
          去结算
        </button>
      </section>
    </template>
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
  padding: 6px 22px;
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

/* ── 表头 ── */
.cart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 0;
  border-bottom: 1px solid var(--c-border);
}

/* ── 店铺分组 ── */
.shop-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid var(--c-border);
}

.shop-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text);
}

.shop-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.shop-subtotal {
  font-size: 12px;
  color: var(--c-text-3);
}

.shop-link {
  font-size: 12px;
  color: var(--c-pink);
  text-decoration: none;
}

.head-note {
  font-size: 12px;
  color: var(--c-text-3);
}

/* ── 行 ── */
.cart-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 0;
  border-bottom: 1px solid var(--c-border);
}

.cart-row:last-child {
  border-bottom: none;
}

.cart-row.invalid .row-name,
.cart-row.invalid .row-price {
  color: var(--c-text-3);
}

.row-check {
  flex: none;
}

.row-thumb {
  width: 76px;
  height: 76px;
  flex: none;
  border-radius: var(--radius-tag);
  overflow: hidden;
  background: var(--c-pink-tint);
  font-size: 26px;
}

.row-info {
  flex: 1;
  min-width: 0;
}

.row-name {
  margin: 0;
  font-size: 14px;
  line-height: 1.45;
  color: var(--c-text);
  /* 长名字最多两行，别把行撑太高 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.row-spec {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--c-text-3);
}

.row-invalid {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--c-pink);
}

.row-price {
  width: 88px;
  flex: none;
  text-align: right;
}

.row-price .price-num {
  font-size: 14px;
}

.row-qty {
  width: 104px;
  flex: none;
}

.row-subtotal {
  width: 100px;
  flex: none;
  text-align: right;
}

.row-subtotal .price-num {
  font-size: 15px;
}

.row-remove {
  flex: none;
  padding: 0;
  font-size: 13px;
  font-family: inherit;
  color: var(--c-text-3);
  background: none;
  border: none;
  cursor: pointer;
  transition: color 0.15s;
}

.row-remove:hover:not(:disabled) {
  color: var(--c-pink);
}

.row-remove:disabled {
  cursor: not-allowed;
}

/* ── 结算栏 ── */
.settle {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px 22px;
  position: sticky;
  bottom: 16px;
}

.settle-count {
  font-size: 13px;
  color: var(--c-text-2);
}

.settle-total {
  margin-left: auto;
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.total-label {
  font-size: 13px;
  color: var(--c-text-2);
}

.total-num {
  font-size: 24px;
}

.settle-btn {
  flex: none;
  width: 150px;
  padding: 12px 0;
  font-size: 15px;
  font-family: inherit;
  color: #fff;
  background: var(--c-pink);
  border: none;
  border-radius: var(--radius-btn);
  cursor: pointer;
  transition: background 0.15s;
}

.settle-btn:hover:not(:disabled) {
  background: var(--c-pink-hover);
}

.settle-btn:disabled {
  background: var(--c-text-3);
  cursor: not-allowed;
}

@media (max-width: 720px) {
  .cart-row {
    flex-wrap: wrap;
    gap: 10px;
  }
  .row-info {
    flex-basis: calc(100% - 150px);
  }
  .row-qty {
    margin-left: auto;
  }
  .settle {
    flex-wrap: wrap;
  }
  .settle-total {
    margin-left: 0;
  }
  .settle-btn {
    width: 100%;
  }
}
</style>
