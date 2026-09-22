import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '../api/auth'
import { ApiError } from '../api/http'
import type { CurrentAccount } from '../api/types'
import { canAssistHealth as healthEntry } from '../../../shared/health'

const managementRoles = ['COMMUNITY_OPERATOR', 'DUTY_OFFICER', 'PLATFORM_ADMIN', 'AUDITOR']

export const useSessionStore = defineStore('session', () => {
  const account = ref<CurrentAccount | null>(null)
  const initialized = ref(false)
  const canAccessAdmin = computed(
    () => account.value !== null && managementRoles.includes(account.value.role),
  )
  const canManageElders = computed(
    () =>
      !!account.value?.communityId &&
      ['COMMUNITY_OPERATOR', 'PLATFORM_ADMIN'].includes(account.value.role),
  )
  const canAssistHealth = computed(() => healthEntry(account.value?.role, account.value?.communityId))

  /** 清除内存会话，权限失效时同步驱动路由和敏感页面卸载。 */
  function clear() {
    account.value = null
    initialized.value = true
  }

  async function restore() {
    try {
      account.value = await authApi.me()
    } catch (error) {
      clear()
      if (!(error instanceof ApiError && error.status === 401)) throw error
    } finally {
      initialized.value = true
    }
  }

  async function login(username: string, password: string) {
    account.value = await authApi.login(username, password)
    initialized.value = true
  }

  async function logout() {
    // 服务端确认注销后才清理界面；网络失败不能误报注销成功。
    await authApi.logout()
    clear()
  }

  return { account, initialized, canAccessAdmin, canManageElders, canAssistHealth, clear, restore, login, logout }
})
