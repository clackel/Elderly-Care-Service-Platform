import { defineStore } from 'pinia'
import { ref } from 'vue'
import { request, clearSession, invalidatePendingRequests, ApiError } from '../api/request'
import { useBookingNavigationStore } from './bookingNavigation'
export interface MobileAccount {
  id: string
  displayName: string
  role: 'ELDER' | 'FAMILY' | 'STAFF'
  communityId: string
}
export const useSessionStore = defineStore('session', () => {
  const account = ref<MobileAccount | null>(null)
  let generation = 0
  let authenticationChange: Promise<void> | null = null

  /** 显式登录和退出串行执行，前台身份恢复只能等待，不能使其请求失效。 */
  function changeAuthentication(work: () => Promise<void>): Promise<void> {
    if (authenticationChange) return Promise.reject(new Error('账号操作正在处理中，请稍后重试'))
    const pending = work()
    authenticationChange = pending
    /** 只释放本次认证操作，迟到结束不能影响后来的操作。 */
    function release() {
      if (authenticationChange === pending) authenticationChange = null
    }
    void pending.then(release, release)
    return pending
  }
  /** 清除会话及业务页面内存，角色只信任服务器身份结果。 */
  function clear() {
    generation++
    account.value = null
    useBookingNavigationStore().clear()
    clearSession()
  }
  /** 恢复服务器会话；只允许最近一次认证操作更新或清除身份。 */
  async function restore() {
    while (authenticationChange) await authenticationChange
    const attempt = ++generation
    invalidatePendingRequests()
    try {
      const value = await request<MobileAccount>('/auth/me')
      if (attempt !== generation) return
      if (!['ELDER', 'FAMILY', 'STAFF'].includes(value.role)) {
        throw new Error('请使用社区核验开通的老人、家属或服务人员账号')
      }
      account.value = value
    } catch (e) {
      if (attempt !== generation) return
      clear()
      if (!(e instanceof ApiError && e.status === 401)) throw e
    }
  }
  /** 微信临时凭证交由服务端校验，过期认证操作不得覆盖后来的会话。 */
  function login(enrollmentToken: string) {
    return changeAuthentication(async () => {
      const attempt = ++generation
      invalidatePendingRequests()
      useBookingNavigationStore().clear()
      // #ifdef MP-WEIXIN
      const code = await new Promise<string>((resolve, reject) =>
        uni.login({
          provider: 'weixin',
          success: (result) =>
            result.code ? resolve(result.code) : reject(new Error('未获取微信登录凭证')),
          fail: () => reject(new Error('微信登录失败，请重试')),
        }),
      )
      if (attempt !== generation) return
      const value = await request<MobileAccount>('/auth/wechat/login', {
        method: 'POST',
        data: { code, enrollmentToken: enrollmentToken.trim() || null },
      })
      if (attempt === generation) account.value = value
      // #endif
      // #ifndef MP-WEIXIN
      void enrollmentToken
      throw new Error('请在微信小程序中使用微信登录；H5可恢复已有服务器会话')
      // #endif
    })
  }
  /** 仅当前注销操作成功后清理身份，迟到的注销结果不清除后续登录。 */
  function logout() {
    return changeAuthentication(async () => {
      const attempt = ++generation
      invalidatePendingRequests()
      await request('/auth/logout', { method: 'POST' })
      if (attempt === generation) clear()
    })
  }
  return { account, clear, restore, login, logout }
})
