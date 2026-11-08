import { ref } from 'vue'
import { defineStore } from 'pinia'
import { fetchPermissions } from '@/api/auth'
import { useUserStore } from '@/stores/user'

/** 管理端权限码：只用于菜单/按钮显隐，后端仍会校验（前端隐藏不等于授权）。 */
export const usePermissionStore = defineStore('permission', () => {
  const roles = ref<string[]>([])
  const codes = ref<Set<string>>(new Set())
  const loaded = ref(false)

  async function load() {
    const userStore = useUserStore()
    if (!userStore.isLoggedIn || !userStore.isAdmin) {
      reset()
      return
    }
    try {
      const view = await fetchPermissions()
      roles.value = view.roles
      codes.value = new Set(view.permissions)
      loaded.value = true
    } catch {
      // 拦截器已提示；保持空权限（前端只影响显隐）
      loaded.value = false
    }
  }

  function reset() {
    roles.value = []
    codes.value = new Set()
    loaded.value = false
  }

  function has(code: string): boolean {
    return codes.value.has(code)
  }

  return { roles, loaded, load, reset, has }
})
