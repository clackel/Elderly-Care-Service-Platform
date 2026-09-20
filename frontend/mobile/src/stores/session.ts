import { defineStore } from 'pinia'
import { ref } from 'vue'
import { request, clearSession, ApiError } from '../api/request'
export interface MobileAccount {
  id: string
  displayName: string
  role: 'ELDER' | 'FAMILY' | 'STAFF'
  communityId: string
}
export const useSessionStore = defineStore('session', () => {
  const account = ref<MobileAccount | null>(null)
  /** 清除会话及业务页面内存，角色只信任服务器身份结果。 */
  function clear() {
    account.value = null
    clearSession()
  }
  /** 恢复服务器会话；无法确定身份时不保留原资料。 */
  async function restore() {
    try {
      const value = await request<MobileAccount>('/auth/me')
      if (!['ELDER', 'FAMILY', 'STAFF'].includes(value.role)) {
        clear()
        throw new Error('请使用社区核验开通的老人、家属或服务人员账号')
      }
      account.value = value
    } catch (e) {
      clear()
      if (!(e instanceof ApiError && e.status === 401)) throw e
    }
  }
  /** 微信临时凭证交由服务端校验，首次登录使用社区交付的单次开通码。 */
  async function login(enrollmentToken: string) {
    // #ifdef MP-WEIXIN
    const code = await new Promise<string>((resolve, reject) =>
      uni.login({
        provider: 'weixin',
        success: (result) =>
          result.code ? resolve(result.code) : reject(new Error('未获取微信登录凭证')),
        fail: () => reject(new Error('微信登录失败，请重试')),
      }),
    )
    account.value = await request<MobileAccount>('/auth/wechat/login', {
      method: 'POST',
      data: { code, enrollmentToken: enrollmentToken.trim() || null },
    })
    // #endif
    // #ifndef MP-WEIXIN
    void enrollmentToken
    throw new Error('请在微信小程序中使用微信登录；H5可恢复已有服务器会话')
    // #endif
  }
  /** 仅服务端确认注销后清理页面，失败时提示重试。 */
  async function logout() {
    await request('/auth/logout', { method: 'POST' })
    clear()
  }
  return { account, clear, restore, login, logout }
})
