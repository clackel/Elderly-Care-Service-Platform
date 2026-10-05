import { computed, onBeforeUnmount, onMounted, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '../../api/http'
import { currentSessionEpoch } from '../../api/sessionEpoch'
import {
  listDeliveryFailures, listNotifications, listTodos, notificationBookingId,
  readNotification, retryNotificationDelivery,
  type DeliveryFailure, type Notice, type Todo, type TodoType,
} from '../../api/notifications'
import { useSessionStore } from '../../stores/session'
import { useBookingNavigationStore } from '../../stores/bookingNavigation'
import { useNotificationOverviewStore } from '../../stores/notificationOverview'
import { errorText } from '../elders/elderModel'

export type NotificationTab = 'notices' | 'todos' | 'deliveries'

/** 管理消息、状态待办和发送异常；每次只读取当前页签，敏感状态不落本地存储。 */
export function useNotificationCenter() {
  const session = useSessionStore()
  const router = useRouter()
  const navigation = useBookingNavigationStore()
  const overview = useNotificationOverviewStore()
  const tab = shallowRef<NotificationTab>('notices')
  const page = shallowRef(1)
  const total = shallowRef(0)
  const unreadOnly = shallowRef(false)
  const todoType = shallowRef<TodoType | ''>('')
  const notices = shallowRef<Notice[]>([])
  const todos = shallowRef<Todo[]>([])
  const deliveries = shallowRef<DeliveryFailure[]>([])
  const loading = shallowRef(false)
  const writingId = shallowRef('')
  const listError = shallowRef('')
  const writeError = shallowRef('')
  const feedback = shallowRef('')
  const pendingRetry = shallowRef<{ eventId: string; version: number } | null>(null)
  const pages = computed(() => Math.max(1, Math.ceil(total.value / 10)))
  let readController: AbortController | null = null
  let writeController: AbortController | null = null
  let alive = true

  /** 隐藏、离开或失去权限时清空数据和在途请求，前台恢复后重新核验。 */
  function clear() {
    readController?.abort()
    writeController?.abort()
    notices.value = []
    todos.value = []
    deliveries.value = []
    total.value = 0
    loading.value = false
    writingId.value = ''
    listError.value = ''
    writeError.value = ''
    feedback.value = ''
    pendingRetry.value = null
  }

  /** 请求返回后同时检查组件、可见性和登录代次，避免旧身份结果重新出现。 */
  function current(controller: AbortController, epoch: number): boolean {
    return alive && !controller.signal.aborted && currentSessionEpoch() === epoch &&
      session.canViewNotifications && document.visibilityState === 'visible'
  }

  /** 取消旧读取并加载当前页签；结果数量变化导致空页时回到最后有效页。 */
  async function load() {
    readController?.abort()
    if (!alive || !session.canViewNotifications || document.visibilityState !== 'visible') return
    const controller = new AbortController()
    readController = controller
    const epoch = currentSessionEpoch()
    loading.value = true
    listError.value = ''
    notices.value = []
    todos.value = []
    deliveries.value = []
    try {
      if (tab.value === 'notices') {
        const result = await listNotifications(page.value, unreadOnly.value, controller.signal)
        if (!current(controller, epoch)) return
        notices.value = result.items
        total.value = result.total
      } else if (tab.value === 'todos') {
        const result = await listTodos(page.value, todoType.value, controller.signal)
        if (!current(controller, epoch)) return
        todos.value = result.items
        total.value = result.total
      } else {
        const result = await listDeliveryFailures(page.value, controller.signal)
        if (!current(controller, epoch)) return
        deliveries.value = result.items
        total.value = result.total
      }
      if (page.value > pages.value) {
        page.value = pages.value
        void load()
      }
    } catch (cause) {
      if (current(controller, epoch)) listError.value = errorText(cause)
    } finally {
      if (alive && readController === controller && !controller.signal.aborted) loading.value = false
    }
  }

  /** 切换页签回到第一页；未知发送结果仍保留原版本供显式重试。 */
  function changeTab(value: NotificationTab) {
    if (writingId.value || tab.value === value) return
    tab.value = value
    page.value = 1
    void load()
  }

  /** 按当前账号重新读取消息筛选，已读动作不会改动待办。 */
  function filterUnread(value: boolean) {
    unreadOnly.value = value
    page.value = 1
    void load()
  }

  /** 只筛选服务端派生的待办类型，不在前端计算或完成业务待办。 */
  function filterTodos(value: TodoType | '') {
    todoType.value = value
    page.value = 1
    void load()
  }

  /** 在合法范围内读取分页，旧分页会被取消。 */
  function paginate(value: number) {
    if (loading.value || writingId.value || value < 1 || value > pages.value) return
    page.value = value
    void load()
  }

  /** 幂等标记消息已读，服务端确认后重新读取列表及未读数，失败保持可重试。 */
  async function markRead(notice: Notice) {
    if (writingId.value || notice.read || !session.canViewNotifications) return
    const controller = new AbortController()
    writeController = controller
    const epoch = currentSessionEpoch()
    writingId.value = notice.id
    writeError.value = ''
    feedback.value = ''
    try {
      await readNotification(notice.id, controller.signal)
      if (!current(controller, epoch)) return
      feedback.value = '消息已标记为已读，业务待办保持不变'
      void overview.refresh()
      await load()
    } catch (cause) {
      if (current(controller, epoch)) writeError.value = errorText(cause) + '。可重试标记已读。'
    } finally {
      if (alive && writeController === controller && !controller.signal.aborted) writingId.value = ''
    }
  }

  /** 固定事件及版本申请重新发送，未知结果复用原载荷，不宣称已送达。 */
  async function retryDelivery(failure?: DeliveryFailure) {
    if (writingId.value || !session.canViewNotifications) return
    if (pendingRetry.value && failure && pendingRetry.value.eventId !== failure.eventId) return
    const attempt = pendingRetry.value ?? (failure ? { eventId: failure.eventId, version: failure.version } : null)
    if (!attempt) return
    const controller = new AbortController()
    writeController = controller
    const epoch = currentSessionEpoch()
    writingId.value = attempt.eventId
    writeError.value = ''
    feedback.value = ''
    pendingRetry.value = attempt
    try {
      await retryNotificationDelivery(attempt.eventId, attempt.version, controller.signal)
      if (!current(controller, epoch)) return
      pendingRetry.value = null
      feedback.value = '重新发送申请已受理，请稍后刷新查看；当前尚不能确认送达。'
      await load()
    } catch (cause) {
      if (!current(controller, epoch)) return
      const uncertain = !(cause instanceof ApiError) || cause.status === 0 || cause.status >= 500 || (cause.status >= 200 && cause.status < 300)
      if (!uncertain) pendingRetry.value = null
      writeError.value = errorText(cause)
      if (cause instanceof ApiError && cause.status === 409) {
        writeError.value += '。发送状态已变化，请按刷新后的列表操作。'
        await load()
      }
    } finally {
      if (alive && writeController === controller && !controller.signal.aborted) writingId.value = ''
    }
  }

  /** 用一次性内存意图进入现有预约详情；导航失败即丢弃目标，不把编号写入URL。 */
  async function openBooking(bookingId: string) {
    if (!session.canViewNotifications || writingId.value) return
    navigation.prepare(bookingId)
    try {
      const failure = await router.push('/bookings')
      if (failure) navigation.clear()
    } catch (cause) {
      navigation.clear()
      if (alive) writeError.value = errorText(cause)
    }
  }

  /** 移交提示只能阅读固定消息，不允许导航到原预约详情。 */
  function openNotice(notice: Notice) {
    const id = notificationBookingId(notice)
    if (id) void openBooking(id)
  }

  /** 回到前台重新获取当前业务状态，后台隐藏期间清除已有展示内容。 */
  function visibilityChanged() {
    clear()
    if (document.visibilityState === 'visible') void load()
  }

  watch(() => [session.account?.id, session.account?.role, session.account?.communityId, session.account?.passwordChangeRequired], () => { clear(); void load() }, { immediate: true })
  onMounted(() => document.addEventListener('visibilitychange', visibilityChanged))
  onBeforeUnmount(() => {
    alive = false
    document.removeEventListener('visibilitychange', visibilityChanged)
    clear()
  })
  return { tab, page, total, pages, unreadOnly, todoType, notices, todos, deliveries, loading, writingId, listError, writeError, feedback, pendingRetry, load, changeTab, filterUnread, filterTodos, paginate, markRead, retryDelivery, openBooking, openNotice }
}
