import type { Directive } from 'vue'
import { usePermissionStore } from '@/stores/permission'

/**
 * v-permission="'admin:order:ship'" / v-permission="['a','b']"
 * 无权限时隐藏元素（display:none）。仅做体验，后端仍必须校验。
 */
export const permission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    apply(el, binding.value)
  },
  updated(el, binding) {
    apply(el, binding.value)
  },
}

function apply(el: HTMLElement, value: string | string[]) {
  const store = usePermissionStore()
  const list = Array.isArray(value) ? value : [value]
  const allowed = list.some((code) => store.has(code))
  el.style.display = allowed ? '' : 'none'
}
