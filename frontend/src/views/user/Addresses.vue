<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AddressFormDialog from '@/components/AddressFormDialog.vue'
import { fetchAddresses, removeAddress, setDefaultAddress } from '@/api/address'
import type { Address } from '@/api/types'

const list = ref<Address[]>([])
const loading = ref(true)
const dialogVisible = ref(false)
/** null = 新增；否则是正在编辑的那条 */
const editing = ref<Address | null>(null)

async function load() {
  loading.value = true
  try {
    list.value = await fetchAddresses()
  } finally {
    loading.value = false
  }
}

onMounted(load)

function openCreate() {
  editing.value = null
  dialogVisible.value = true
}

function openEdit(address: Address) {
  editing.value = address
  dialogVisible.value = true
}

async function onSaved() {
  await load()
}

async function makeDefault(address: Address) {
  if (address.isDefault === 1) return
  await setDefaultAddress(address.id)
  ElMessage.success('已设为默认地址')
  await load()
}

async function remove(address: Address) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${address.receiver}」的这条收货地址吗？`,
      '删除地址',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  await removeAddress(address.id)
  ElMessage.success('地址已删除')
  await load()
}

function fullAddress(address: Address): string {
  return `${address.province}${address.city}${address.district}${address.detail}`
}
</script>

<template>
  <div class="addr-page">
    <div class="block">
      <div class="block-head">
        <h2 class="block-title">收货地址</h2>
        <button type="button" class="add-btn" @click="openCreate">+ 新增地址</button>
      </div>

      <p v-if="!loading && list.length === 0" class="empty-tip">
        还没有收货地址，添加一个之后下单会更方便。
      </p>

      <div v-loading="loading" class="addr-list">
        <div v-for="address in list" :key="address.id" class="addr-card">
          <p class="addr-line">
            <span class="addr-name">{{ address.receiver }}</span>
            <span class="addr-phone">{{ address.phone }}</span>
            <span v-if="address.isDefault === 1" class="addr-tag">默认</span>
          </p>
          <p class="addr-detail">{{ fullAddress(address) }}</p>

          <div class="addr-actions">
            <button
              v-if="address.isDefault !== 1"
              type="button"
              class="text-btn"
              @click="makeDefault(address)"
            >
              设为默认
            </button>
            <button type="button" class="text-btn" @click="openEdit(address)">编辑</button>
            <button type="button" class="text-btn danger" @click="remove(address)">删除</button>
          </div>
        </div>
      </div>
    </div>

    <AddressFormDialog v-model="dialogVisible" :address="editing" @saved="onSaved" />
  </div>
</template>

<style scoped>
.block {
  background: var(--c-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 20px 22px;
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.block-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text);
}

.add-btn {
  padding: 7px 16px;
  font-size: 13px;
  font-family: inherit;
  color: #fff;
  background: var(--c-pink);
  border: none;
  border-radius: var(--radius-btn);
  cursor: pointer;
  transition: background 0.15s;
}

.add-btn:hover {
  background: var(--c-pink-hover);
}

.empty-tip {
  margin: 0;
  padding: 12px 0;
  font-size: 13px;
  color: var(--c-text-3);
}

.addr-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
  min-height: 60px;
}

.addr-card {
  padding: 14px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-tag);
  transition: border-color 0.15s;
}

.addr-card:hover {
  border-color: var(--c-pink-soft);
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

.addr-actions {
  display: flex;
  gap: 14px;
  margin-top: 10px;
}

.text-btn {
  padding: 0;
  font-size: 12.5px;
  font-family: inherit;
  color: var(--c-text-3);
  background: none;
  border: none;
  cursor: pointer;
  transition: color 0.15s;
}

.text-btn:hover {
  color: var(--c-pink);
}

.text-btn.danger:hover {
  color: #d9534f;
}
</style>
