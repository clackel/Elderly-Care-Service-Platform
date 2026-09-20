import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ApiError } from '../../api/http'
import {
  listBookings,
  getBooking,
  bookingHistory,
  createBooking,
  bookingAction,
  arrangeBooking,
  bookingRules,
  elderOptions,
  options,
  type BookingSummary,
  type BookingDetail,
  type BookingEvent,
  type BookingStatus,
  type Catalog,
  type Worker,
  type ElderOption,
  type BookingRules,
  type CreateBooking,
} from '../../api/bookings'
/** 管理预约读取、写入与请求淘汰，敏感草稿只保存在当前页面内存。 */
export function useBookings() {
  const rows = ref<BookingSummary[]>([]),
    detail = ref<BookingDetail | null>(null),
    history = ref<BookingEvent[]>([])
  const services = ref<Catalog[]>([]),
    workers = ref<Worker[]>([]),
    elders = ref<ElderOption[]>([])
  const rules = ref<BookingRules | null>(null),
    page = ref(1),
    total = ref(0),
    status = ref<BookingStatus | ''>('')
  const historyPage = ref(1),
    historyTotal = ref(0),
    busy = ref(false),
    loading = ref(false),
    error = ref(''),
    creating = ref(false),
    uncertain = ref(false)
  let alive = true,
    listRead: AbortController | null = null,
    detailRead: AbortController | null = null
  const initialRead = new AbortController()
  /** 使用中文提示和追踪编号保留失败原因。 */
  function failure(e: unknown) {
    return e instanceof ApiError
      ? e.message + (e.traceId ? '（追踪编号：' + e.traceId + '）' : '')
      : e instanceof Error
        ? e.message
        : '操作失败，请重试'
  }
  /** 查询当前页，旧请求取消后不得覆盖较新的条件。 */
  async function load() {
    listRead?.abort()
    const controller = new AbortController()
    listRead = controller
    loading.value = true
    error.value = ''
    try {
      const result = await listBookings(page.value, status.value, controller.signal)
      if (!controller.signal.aborted && alive) {
        rows.value = result.items
        total.value = result.total
      }
    } catch (e) {
      if (!controller.signal.aborted && alive) error.value = failure(e)
    } finally {
      if (!controller.signal.aborted && alive) loading.value = false
    }
  }
  /** 原子加载当前详情与首屏记录，防止不同预约的资料混在一起。 */
  async function open(id: string, eventPage = 1) {
    detailRead?.abort()
    const controller = new AbortController()
    detailRead = controller
    detail.value = null
    history.value = []
    error.value = ''
    try {
      const [record, events] = await Promise.all([
        getBooking(id, controller.signal),
        bookingHistory(id, eventPage, controller.signal),
      ])
      if (!controller.signal.aborted && alive) {
        detail.value = record
        history.value = events.items
        historyPage.value = eventPage
        historyTotal.value = events.total
      }
    } catch (e) {
      if (!controller.signal.aborted && alive) error.value = failure(e)
    }
  }
  /** 成功写入后刷新状态；版本冲突重新读取，网络不确定时保留原申请供幂等重试。 */
  async function mutate(task: () => Promise<BookingDetail>, isCreate = false) {
    if (busy.value) return
    busy.value = true
    error.value = ''
    try {
      const result = await task()
      if (!alive) return
      creating.value = false
      uncertain.value = false
      await load()
      await open(result.id)
    } catch (e) {
      if (!alive) return
      const message = failure(e)
      if (isCreate) uncertain.value = !(e instanceof ApiError) || e.status === 0 || e.status >= 500
      if (e instanceof ApiError && e.status === 409 && detail.value) await open(detail.value.id)
      error.value = message
    } finally {
      if (alive) busy.value = false
    }
  }
  /** 稳定编号的创建请求仅由提交表单生成。 */
  function create(input: CreateBooking) {
    return mutate(() => createBooking(input), true)
  }
  /** 按已读版本执行社区状态动作。 */
  function action(action: string, note: string) {
    const d = detail.value
    if (d) return mutate(() => bookingAction(d.id, action, d.version, note))
  }
  /** 根据快照时长计算结束时间并提交人员安排。 */
  function arrange(workerId: string, start: string, note: string) {
    const d = detail.value
    if (d)
      return mutate(() =>
        arrangeBooking(
          d.id,
          d.version,
          workerId,
          new Date(start).toISOString(),
          new Date(new Date(start).getTime() + d.service.durationMinutes * 60000).toISOString(),
          note,
        ),
      )
  }
  /** 加载表单选项与时间规则；所有选项都来自当前社区权限范围。 */
  async function initialize() {
    await load()
    try {
      const [s, w, e, r] = await Promise.all([
        options<Catalog>('/services?available=true', initialRead.signal),
        options<Worker>('/service-workers', initialRead.signal),
        elderOptions(initialRead.signal),
        bookingRules(initialRead.signal),
      ])
      if (alive) {
        services.value = s
        workers.value = w
        elders.value = e
        rules.value = r
      }
    } catch (e) {
      if (alive && !initialRead.signal.aborted) error.value = failure(e)
    }
  }
  /** 开启新申请并刷新可能上下架的服务选项。 */
  async function startCreate() {
    creating.value = true
    uncertain.value = false
    error.value = ''
    await initialize()
  }
  /** 安全清理详情面板及未完成读取。 */
  function closeDetail() {
    detailRead?.abort()
    detail.value = null
    history.value = []
  }
  onMounted(initialize)
  onBeforeUnmount(() => {
    alive = false
    listRead?.abort()
    detailRead?.abort()
    initialRead.abort()
    rows.value = []
    detail.value = null
    history.value = []
    elders.value = []
  })
  return {
    rows,
    detail,
    history,
    services,
    workers,
    elders,
    rules,
    page,
    total,
    status,
    historyPage,
    historyTotal,
    busy,
    loading,
    error,
    creating,
    uncertain,
    load,
    open,
    create,
    action,
    arrange,
    initialize,
    startCreate,
    closeDetail,
  }
}
