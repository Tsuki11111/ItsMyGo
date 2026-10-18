<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ImageUpload from './ImageUpload.vue'
import StockAdjustDialog from './StockAdjustDialog.vue'
import { fetchProductSkus, saveProductSkus } from '@/api/admin'
import type { Product, ProductSku, SkuSavePayload } from '@/api/types'

const props = defineProps<{
  modelValue: boolean
  product: Product | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

interface LocalValue {
  /** 已有值是真 id（正数）；新建值是负数临时 id */
  id: number
  value: string
}

interface LocalSpec {
  /** 已有规格传真实 id；新建传 null */
  id: number | null
  name: string
  values: LocalValue[]
}

interface SkuRow {
  /** 规范化组合键：规格值 id 数值升序、逗号拼接 */
  key: string
  /** 与 specs 同序，用于展示与请求 */
  valueIds: number[]
  price: number | null
  image: string
  /** 仅新 SKU 可编辑；已有 SKU 的库存必须走调整接口 */
  stock: number | null
  existing?: ProductSku
}

const loading = ref(false)
const saving = ref(false)
const specs = ref<LocalSpec[]>([])
const rows = ref<SkuRow[]>([])
const originalSkus = ref<ProductSku[]>([])
let originalByKey = new Map<string, ProductSku>()
/** 负数临时 id 全局递减，删除后也不复用 —— 避免「删了再加」意外命中旧组合 */
let nextTempId = -1

const adjustVisible = ref(false)
const adjustTarget = ref<SkuRow | null>(null)

function newTempValue(): LocalValue {
  return { id: nextTempId--, value: '' }
}

/** 数值排序，不能直接 sort() —— "10" < "2" 是字符串比较的经典坑 */
function keyOf(ids: number[]): string {
  return [...ids].sort((a, b) => a - b).join(',')
}

function buildCombos(localSpecs: LocalSpec[]): number[][] {
  if (localSpecs.length === 0) return [[]]
  return localSpecs.reduce<number[][]>(
    (acc, spec) => acc.flatMap((ids) => spec.values.map((value) => [...ids, value.id])),
    [[]],
  )
}

/** 按组合重建表格：已有组合保留用户已填的价格/图片，新组合默认用商品当前价 */
function rebuild() {
  const prevByKey = new Map(rows.value.map((row) => [row.key, row]))

  rows.value = buildCombos(specs.value).map((valueIds) => {
    const key = keyOf(valueIds)
    const prev = prevByKey.get(key)
    if (prev) return { ...prev, key, valueIds }

    const existing = originalByKey.get(key)
    return {
      key,
      valueIds,
      price: existing?.price ?? props.product?.price ?? 0,
      image: existing?.image ?? '',
      stock: existing ? null : 0,
      existing,
    }
  })
}

async function load() {
  if (!props.product) return

  loading.value = true
  try {
    const view = await fetchProductSkus(props.product.id)
    nextTempId = -1
    originalSkus.value = view.skus
    originalByKey = new Map(view.skus.map((sku) => [sku.specValueIds, sku]))

    specs.value = [...view.specs]
      .sort((a, b) => a.sort - b.sort)
      .map((spec) => ({
        id: spec.id,
        name: spec.name,
        values: [...spec.values]
          .sort((a, b) => a.sort - b.sort)
          .map((value) => ({ id: value.id, value: value.value })),
      }))

    rows.value = []
    rebuild()
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) load()
  },
)

function valueOf(row: SkuRow, specIndex: number): string {
  const valueId = row.valueIds[specIndex]
  return specs.value[specIndex]?.values.find((value) => value.id === valueId)?.value ?? ''
}

function addSpec() {
  if (specs.value.length >= 3) {
    ElMessage.warning('最多 3 组规格')
    return
  }
  specs.value.push({ id: null, name: `规格${specs.value.length + 1}`, values: [newTempValue()] })
  rebuild()
}

function removeSpec(index: number) {
  specs.value.splice(index, 1)
  rebuild()
}

function addValue(spec: LocalSpec) {
  if (spec.values.length >= 20) {
    ElMessage.warning('每组规格最多 20 个值')
    return
  }
  spec.values.push(newTempValue())
  rebuild()
}

function removeValue(spec: LocalSpec, index: number) {
  spec.values.splice(index, 1)
  rebuild()
}

function openAdjust(row: SkuRow) {
  if (!row.existing) return
  adjustTarget.value = row
  adjustVisible.value = true
}

function onStockUpdated(updated: ProductSku) {
  originalByKey.set(updated.specValueIds, updated)
  const index = originalSkus.value.findIndex((sku) => sku.id === updated.id)
  if (index >= 0) originalSkus.value[index] = updated
  if (adjustTarget.value) adjustTarget.value.existing = updated
}

/** 只刷新已有 SKU 的库存，不覆盖表单里还没保存的价格/图片 */
async function refreshStock() {
  if (!props.product) return
  const view = await fetchProductSkus(props.product.id)
  originalByKey = new Map(view.skus.map((sku) => [sku.specValueIds, sku]))
  originalSkus.value = view.skus
  for (const row of rows.value) {
    const existing = originalByKey.get(row.key)
    if (existing) row.existing = existing
  }
  ElMessage.success('库存已刷新')
}

function validate(): string | null {
  const specNames = new Set<string>()
  for (const spec of specs.value) {
    const name = spec.name.trim()
    if (!name) return '规格名不能为空'
    if (specNames.has(name)) return '规格名不能重复'
    specNames.add(name)

    if (spec.values.length === 0) return `规格「${name}」至少要有一个值`
    if (spec.values.length > 20) return `规格「${name}」最多 20 个值`

    const values = new Set<string>()
    for (const value of spec.values) {
      const text = value.value.trim()
      if (!text) return `规格「${name}」有空的规格值`
      if (values.has(text)) return `规格「${name}」的规格值不能重复`
      values.add(text)
    }
  }

  if (rows.value.length === 0) return '当前没有可保存的 SKU 组合'
  if (rows.value.length > 100) return 'SKU 组合最多 100 个'

  for (const row of rows.value) {
    if (row.price === null || row.price < 0) return '每个 SKU 都要填价格'
    if (!row.existing && (row.stock === null || row.stock < 0)) return '新 SKU 要填库存'
  }
  return null
}

async function submit() {
  if (!props.product) return

  const error = validate()
  if (error) {
    ElMessage.warning(error)
    return
  }

  // 原 SKU 的 key 不在当前组合里 = 保存后会被删掉，库存/销量一起没
  const currentKeys = new Set(rows.value.map((row) => row.key))
  const removed = originalSkus.value.filter((sku) => !currentKeys.has(sku.specValueIds))
  if (removed.length > 0) {
    try {
      await ElMessageBox.confirm(
        `将删除 ${removed.length} 个已有 SKU，其库存和销量会丢失（历史订单有快照，不受影响）。继续保存？`,
        '删除 SKU 确认',
        { type: 'warning', confirmButtonText: '继续保存', cancelButtonText: '返回检查' },
      )
    } catch {
      return
    }
  }

  const payload: SkuSavePayload = {
    specs: specs.value.map((spec) => ({
      id: spec.id,
      name: spec.name.trim(),
      values: spec.values.map((value) => ({ id: value.id, value: value.value.trim() })),
    })),
    skus: rows.value.map((row) => ({
      specValueIds: row.valueIds,
      price: row.price ?? 0,
      // 已有行传当前库存即可，后端会忽略这个字段
      stock: row.existing ? row.existing.stock : (row.stock ?? 0),
      image: row.image,
    })),
  }

  saving.value = true
  try {
    await saveProductSkus(props.product.id, payload)
    ElMessage.success('规格与 SKU 已保存')
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="`规格与 SKU · ${product?.name ?? ''}`"
    width="920px"
    top="5vh"
    class="sku-editor-dialog"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="已有 SKU 的库存不会被整组保存覆盖；补货/盘库请点「调整」。删除规格或值会删掉对应 SKU。"
        class="tip"
      />

      <div class="specs">
        <div class="specs-head">
          <span class="section-title">规格定义</span>
          <div>
            <el-button size="small" :disabled="specs.length >= 3" @click="addSpec">
              添加规格
            </el-button>
            <el-button size="small" @click="refreshStock">刷新库存</el-button>
          </div>
        </div>

        <el-empty
          v-if="specs.length === 0"
          description="当前是单规格商品，只有一条默认 SKU"
          :image-size="60"
        />

        <div v-for="(spec, specIndex) in specs" :key="specIndex" class="spec-card">
          <div class="spec-head">
            <el-input v-model="spec.name" placeholder="规格名，如 颜色" maxlength="32" class="spec-name" />
            <el-button link type="danger" @click="removeSpec(specIndex)">删除规格</el-button>
          </div>

          <div class="values">
            <div v-for="(value, valueIndex) in spec.values" :key="value.id" class="value-row">
              <el-input v-model="value.value" placeholder="规格值，如 黑色" maxlength="64" class="value-input" />
              <el-button link type="danger" @click="removeValue(spec, valueIndex)">删除</el-button>
            </div>
            <el-button size="small" :disabled="spec.values.length >= 20" @click="addValue(spec)">
              添加值
            </el-button>
          </div>
        </div>
      </div>

      <div class="matrix">
        <span class="section-title">SKU 组合</span>
        <el-table :data="rows" border size="small" class="matrix-table">
          <el-table-column
            v-for="(spec, specIndex) in specs"
            :key="specIndex"
            :label="spec.name || `规格${specIndex + 1}`"
            min-width="110"
          >
            <template #default="{ row }">{{ valueOf(row, specIndex) }}</template>
          </el-table-column>

          <el-table-column v-if="specs.length === 0" label="规格" width="110">
            <template #default>默认规格</template>
          </el-table-column>

          <el-table-column label="价格（元）" width="170">
            <template #default="{ row }">
              <el-input-number
                v-model="row.price"
                :min="0"
                :precision="2"
                size="small"
                controls-position="right"
                class="full"
              />
            </template>
          </el-table-column>

          <el-table-column label="库存" width="180">
            <template #default="{ row }">
              <template v-if="row.existing">
                <span class="stock">{{ row.existing.stock }}</span>
                <el-button link type="primary" size="small" @click="openAdjust(row)">调整</el-button>
              </template>
              <el-input-number
                v-else
                v-model="row.stock"
                :min="0"
                size="small"
                controls-position="right"
                class="full"
              />
            </template>
          </el-table-column>

          <el-table-column label="规格图" width="190">
            <template #default="{ row }">
              <ImageUpload v-model="row.image" compact />
            </template>
          </el-table-column>
        </el-table>
      </div>

      <p class="foot-tip">
        新 SKU 的库存在这里填；已有 SKU 的库存只读，点「调整」改 ——
        库存该由下单和取消来动，不能被改价顺手冲掉。
      </p>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="loading" @click="submit">
        保存
      </el-button>
    </template>

    <StockAdjustDialog
      v-model="adjustVisible"
      :product-id="product?.id ?? 0"
      :sku="adjustTarget?.existing ?? null"
      @updated="onStockUpdated"
    />
  </el-dialog>
</template>

<style scoped>
.tip {
  margin-bottom: 16px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
}

.specs-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.spec-card {
  padding: 12px;
  margin-bottom: 10px;
  background: #fafafa;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.spec-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.spec-name {
  width: 240px;
}

.values {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.value-row {
  display: flex;
  align-items: center;
  gap: 4px;
}

.value-input {
  width: 180px;
}

.matrix {
  margin-top: 20px;
}

.matrix-table {
  margin-top: 10px;
}

.stock {
  margin-right: 8px;
  font-family: ui-monospace, 'SF Mono', Consolas, monospace;
}

.full {
  width: 100%;
}

.foot-tip {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
