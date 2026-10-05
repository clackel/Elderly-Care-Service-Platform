import { shallowRef } from 'vue'
import { defineStore } from 'pinia'
import { listTodos, notificationUnreadCount } from '../api/notifications'
import { currentSessionEpoch } from '../api/sessionEpoch'
import { useSessionStore } from './session'
import { errorText } from '../components/elders/elderModel'

/** 共享侧栏与工作台的最小数量摘要，不缓存消息正文或预约详情。 */
export const useNotificationOverviewStore = defineStore('notificationOverview', () => {
  const session = useSessionStore()
  const unread = shallowRef<number | null>(null)
  const todos = shallowRef<number | null>(null)
  const loading = shallowRef(false)
  const error = shallowRef('')
  let controller: AbortController | null = null

  /** 清除数量并取消在途请求，在隐藏页面、退出登录或切换账号时使用。 */
  function clear() {
    controller?.abort()
    controller = null
    unread.value = null
    todos.value = null
    loading.value = false
    error.value = ''
  }

  /** 仅在可见且具备消息权限时重新核验摘要，迟到响应不得更新新会话。 */
  async function refresh() {
    clear()
    if (!session.canViewNotifications || document.visibilityState !== 'visible') return
    const active = new AbortController()
    controller = active
    const epoch = currentSessionEpoch()
    const ownerId = session.account?.id
    loading.value = true
    try {
      const [count, tasks] = await Promise.all([
        notificationUnreadCount(active.signal), listTodos(1, '', active.signal, 1),
      ])
      if (active.signal.aborted || currentSessionEpoch() !== epoch || session.account?.id !== ownerId) return
      unread.value = count.count
      todos.value = tasks.total
    } catch (cause) {
      if (!active.signal.aborted && currentSessionEpoch() === epoch) error.value = errorText(cause)
    } finally {
      if (controller === active && !active.signal.aborted) loading.value = false
    }
  }

  return { unread, todos, loading, error, clear, refresh }
})
