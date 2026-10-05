import { watch } from 'vue'
import { defineStore } from 'pinia'
import { currentSessionEpoch } from '../api/sessionEpoch'
import { useSessionStore } from './session'

/** 保存一次性的预约详情导航意图；不写URL或浏览器存储，按账号和会话限定消费。 */
export const useBookingNavigationStore = defineStore('bookingNavigation', () => {
  const session = useSessionStore()
  let pending: { bookingId: string; owner: string; epoch: number } | null = null

  /** 当前身份及社区构成意图的访问范围，角色或改密要求变化也使意图失效。 */
  function owner(): string {
    const account = session.account
    return account ? [account.id, account.communityId, account.role, account.passwordChangeRequired].join(':') : ''
  }

  /** 清除尚未消费的跳转目标，退出登录或导航取消后不得再次使用。 */
  function clear() { pending = null }

  /** 只为具有预约管理权限的当前账号登记目标，真正详情仍由服务端重新授权。 */
  function prepare(bookingId: string) {
    clear()
    if (!session.canViewNotifications || !bookingId.trim()) return
    pending = { bookingId, owner: owner(), epoch: currentSessionEpoch() }
  }

  /** 预约页只消费一次；账号、社区或会话变化时丢弃旧目标。 */
  function consume(): string | null {
    const target = pending
    clear()
    return target && target.owner === owner() && target.epoch === currentSessionEpoch() && session.canViewNotifications
      ? target.bookingId : null
  }

  watch(owner, clear, { flush: 'sync' })
  return { prepare, consume, clear }
})
