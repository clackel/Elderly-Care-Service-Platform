import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '../api/auth'
import { ApiError } from '../api/http'
import { advanceSessionEpoch } from '../api/sessionEpoch'
import type { CurrentAccount } from '../api/types'
import { canAssistHealth as healthEntry } from '../../../shared/health'

const managementRoles = ['COMMUNITY_OPERATOR', 'DUTY_OFFICER', 'PLATFORM_ADMIN', 'AUDITOR']

export const useSessionStore = defineStore('session', () => {
  const account = ref<CurrentAccount | null>(null)
  const initialized = ref(false)
  let generation = 0
  let sessionChangeGeneration: number | null = null
  const canAccessAdmin = computed(
    () => account.value !== null && managementRoles.includes(account.value.role),
  )
  const canManageElders = computed(
    () =>
      !!account.value?.communityId &&
      ['COMMUNITY_OPERATOR', 'PLATFORM_ADMIN'].includes(account.value.role),
  )
  const canAssistHealth = computed(() => healthEntry(account.value?.role, account.value?.communityId))
  const canManageAccounts = computed(
    () => !!account.value?.communityId && account.value.role === 'PLATFORM_ADMIN',
  )

  /** 清除身份并淘汰在途读取，避免注销后的迟到响应重新写回旧账号。 */
  function clear() {
    generation++
    advanceSessionEpoch()
    account.value = null
    initialized.value = true
  }

  /** 仅应用最新身份读取；登录或注销进行中不发起可覆盖其结果的恢复请求。 */
  async function restore() {
    if (sessionChangeGeneration !== null) return
    const requestGeneration = ++generation
    try {
      const restored = await authApi.me()
      if (requestGeneration !== generation) return
      account.value = restored
    } catch (error) {
      if (requestGeneration !== generation) return
      clear()
      if (!(error instanceof ApiError && error.status === 401)) throw error
    } finally {
      if (requestGeneration === generation) initialized.value = true
    }
  }

  /** 登录开始即淘汰旧身份读取，仅由当前登录请求更新角色和改密要求。 */
  async function login(username: string, password: string) {
    const requestGeneration = ++generation
    advanceSessionEpoch()
    sessionChangeGeneration = requestGeneration
    try {
      const authenticated = await authApi.login(username, password)
      if (requestGeneration !== generation) return
      account.value = authenticated
      initialized.value = true
    } catch (error) {
      if (requestGeneration === generation) clear()
      throw error
    } finally {
      if (sessionChangeGeneration === requestGeneration) sessionChangeGeneration = null
    }
  }

  /** 注销开始即淘汰旧读取；成功后清除当前身份，网络异常时保留重试机会。 */
  async function logout() {
    // 服务端确认注销后才清理界面；网络失败不能误报注销成功。
    const requestGeneration = ++generation
    advanceSessionEpoch()
    sessionChangeGeneration = requestGeneration
    try {
      await authApi.logout()
      if (requestGeneration === generation) clear()
    } finally {
      if (sessionChangeGeneration === requestGeneration) sessionChangeGeneration = null
    }
  }

  return { account, initialized, canAccessAdmin, canManageElders, canAssistHealth, canManageAccounts, clear, restore, login, logout }
})
