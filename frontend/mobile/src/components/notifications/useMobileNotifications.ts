import { computed, onBeforeUnmount, onMounted, shallowRef } from 'vue'
import { useSessionStore } from '../../stores/session'
import { useBookingNavigationStore } from '../../stores/bookingNavigation'
import { ApiError } from '../../api/request'
import { noticePage, unreadCount, readNotice, todoPage, notificationBookingId, type Notice, type Todo, type TodoType } from '../../api/notifications'

/** 消息和本人待办只保留当前页面身份的数据，离开页面后拒绝所有旧请求回填。 */
export function useMobileNotifications() {
  const session = useSessionStore(), identity = session.account
  const staff = computed(() => session.account?.role === 'STAFF')
  const mode = shallowRef<'notices' | 'todos'>('notices')
  const notices = shallowRef<Notice[]>([]), todos = shallowRef<Todo[]>([])
  const page = shallowRef(1), total = shallowRef(0), unread = shallowRef<number | null>(null)
  const unreadOnly = shallowRef(false), type = shallowRef<TodoType | ''>('')
  const busy = shallowRef(false), navigating = shallowRef(false), error = shallowRef('')
  const pages = computed(() => Math.max(1, Math.ceil(total.value / 10)))
  let alive = true, generation = 0

  /** 只允许本组件、当前身份对象及最近读取更新界面。 */
  function current(sequence?: number) {
    return alive && identity !== null && session.account === identity && (sequence === undefined || generation === sequence)
  }

  /** 保留可读错误及追踪编号，不在界面输出接口内部对象。 */
  function message(cause: unknown) {
    return cause instanceof ApiError
      ? cause.message + (cause.traceId ? '（追踪编号：' + cause.traceId + '）' : '')
      : cause instanceof Error ? cause.message : '消息读取失败，请重试'
  }

  /** 读取当前分页和有权查看的未读计数；切换时清除上一范围的旧消息。 */
  async function load() {
    if (!current()) return
    const sequence = ++generation
    busy.value = true; error.value = ''; notices.value = []; todos.value = []; unread.value = null
    try {
      const [count, rows] = await Promise.all([
        unreadCount(),
        mode.value === 'todos' && staff.value ? todoPage(page.value, type.value) : noticePage(page.value, unreadOnly.value),
      ])
      if (!current(sequence)) return
      const lastPage = Math.max(1, Math.ceil(rows.total / 10))
      if (page.value > lastPage) {
        page.value = lastPage
        await load()
        return
      }
      unread.value = count.count
      total.value = rows.total
      if (mode.value === 'todos' && staff.value) todos.value = rows.items as Todo[]
      else notices.value = rows.items as Notice[]
    } catch (cause) {
      if (current(sequence)) { error.value = message(cause); total.value = 0 }
    } finally {
      if (current(sequence)) busy.value = false
    }
  }

  /** 切换消息或工作人员待办时从第一页重新读取，不把已读状态当作完成状态。 */
  function switchMode(value: 'notices' | 'todos') {
    if (busy.value || navigating.value || (value === 'todos' && !staff.value)) return
    mode.value = value; page.value = 1; void load()
  }

  /** 未读筛选通过后端授权查询生效，不在前端过滤保留失效消息。 */
  function filterUnread(value: boolean) {
    if (busy.value || navigating.value) return
    unreadOnly.value = value; page.value = 1; void load()
  }

  /** 工作人员待办类型变更后重新读取真实状态。 */
  function filterType(value: TodoType | '') {
    if (busy.value || navigating.value) return
    type.value = value; page.value = 1; void load()
  }

  /** 有界分页不把筛选或目标对象写入URL。 */
  function paginate(value: number) {
    if (busy.value || navigating.value || value < 1 || value > pages.value) return
    page.value = value; void load()
  }

  /** 已读可以重复请求；等待服务端成功后重新读取列表和计数。 */
  async function markRead(notice: Notice) {
    if (!current() || busy.value || navigating.value || notice.read) return
    busy.value = true; error.value = ''
    try {
      await readNotice(notice.id)
      if (!current()) return
      if (unreadOnly.value && notices.value.length === 1 && page.value > 1) page.value--
      await load()
    } catch (cause) {
      if (current()) { notices.value = []; todos.value = []; unread.value = null; error.value = message(cause) }
    } finally {
      if (current()) busy.value = false
    }
  }

  /** 只传递当前账号的预约编号，服务页面重新恢复身份并读取有权访问的详情。 */
  function openBooking(bookingId: string) {
    if (!current() || busy.value || navigating.value || !identity) return
    navigating.value = true
    const navigation = useBookingNavigationStore()
    navigation.queue(identity.id, bookingId)
    uni.switchTab({
      url: '/pages/services/index',
      fail: () => {
        if (!current()) return
        navigation.clear(); navigating.value = false; error.value = '暂时无法打开服务页，请重试'
      },
    })
  }

  /** 任务移交通知永远不进入预约详情，即使返回载荷意外包含编号。 */
  function openNotice(notice: Notice) {
    const id = notificationBookingId(notice)
    if (id) openBooking(id)
  }

  onMounted(load)
  onBeforeUnmount(() => {
    alive = false; generation++
    notices.value = []; todos.value = []; unread.value = null
  })
  return { staff, mode, notices, todos, page, total, pages, unread, unreadOnly, type, busy, navigating, error,
    load, switchMode, filterUnread, filterType, paginate, markRead, openBooking, openNotice }
}
