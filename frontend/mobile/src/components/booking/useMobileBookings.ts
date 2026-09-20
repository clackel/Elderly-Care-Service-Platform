import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useSessionStore } from '../../stores/session'
import { ApiError } from '../../api/request'
import {
  catalogPage,
  bookingPage,
  bookingDetail,
  historyPage,
  eligibleElders,
  getRules,
  submitBooking,
  act,
  type Catalog,
  type Category,
  type BookingSummary,
  type BookingDetail,
  type BookingEvent,
  type ElderOption,
  type BookingRules,
  type CreateBooking,
} from '../../api/bookings'
/** 移动预约页面只保存本次会话数据，真实角色决定预约或任务入口。 */
export function useMobileBookings() {
  const session = useSessionStore(),
    owner = session.account?.id
  const staff = computed(() => session.account?.role === 'STAFF')
  const mode = ref<'catalog' | 'bookings'>(staff.value ? 'bookings' : 'catalog')
  const services = ref<Catalog[]>([]),
    bookings = ref<BookingSummary[]>([]),
    detail = ref<BookingDetail | null>(null),
    events = ref<BookingEvent[]>([])
  const selected = ref<Catalog | null>(null),
    elders = ref<ElderOption[]>([]),
    rules = ref<BookingRules | null>(null)
  const page = ref(1),
    total = ref(0),
    eventPage = ref(1),
    eventTotal = ref(0),
    category = ref<Category | ''>(''),
    status = ref('')
  const busy = ref(false),
    error = ref(''),
    success = ref(''),
    uncertain = ref(false)
  let alive = true,
    generation = 0
  /** 不接受已卸载或前一账号会话返回的个人资料。 */
  function current(sequence?: number) {
    return (
      alive && session.account?.id === owner && (sequence === undefined || sequence === generation)
    )
  }
  /** 显示中文失败和必要追踪编号。 */
  function message(e: unknown) {
    return e instanceof ApiError
      ? e.message + (e.traceId ? '（追踪编号：' + e.traceId + '）' : '')
      : e instanceof Error
        ? e.message
        : '请求失败，请重试'
  }
  /** 分页读取当前标签内容，防止过期读取覆盖最新状态。 */
  async function load() {
    const sequence = ++generation
    busy.value = true
    error.value = ''
    try {
      if (mode.value === 'catalog') {
        const data = await catalogPage(page.value, category.value)
        if (current(sequence)) {
          services.value = data.items
          total.value = data.total
        }
      } else {
        const data = await bookingPage(page.value, status.value)
        if (current(sequence)) {
          bookings.value = data.items
          total.value = data.total
        }
      }
    } catch (e) {
      if (current(sequence)) error.value = message(e)
    } finally {
      if (current(sequence)) busy.value = false
    }
  }
  /** 切换到服务目录或本人预约，清除上一页详情。 */
  function switchMode(value: 'catalog' | 'bookings') {
    if (busy.value) return
    mode.value = value
    selected.value = null
    detail.value = null
    events.value = []
    page.value = 1
    success.value = ''
    void load()
  }
  /** 进入申请前更新老人权限及服务端规则，撤销授权不继续使用旧选项。 */
  async function select(service: Catalog) {
    busy.value = true
    error.value = ''
    success.value = ''
    try {
      const [e, r] = await Promise.all([eligibleElders(), getRules()])
      if (current()) {
        elders.value = e
        rules.value = r
        selected.value = service
        uncertain.value = false
      }
    } catch (e) {
      if (current()) error.value = message(e)
    } finally {
      if (current()) busy.value = false
    }
  }
  /** 读取详情与历史时先移除旧内容，权限失败不保留旧资料。 */
  async function open(id: string, nextPage = 1) {
    const sequence = ++generation
    busy.value = true
    error.value = ''
    detail.value = null
    events.value = []
    try {
      const [d, h, e, r] = await Promise.all([
        bookingDetail(id),
        historyPage(id, nextPage),
        staff.value ? Promise.resolve([] as ElderOption[]) : eligibleElders(),
        getRules(),
      ])
      if (current(sequence)) {
        detail.value = d
        events.value = h.items
        eventPage.value = nextPage
        eventTotal.value = h.total
        elders.value = e
        rules.value = r
      }
    } catch (e) {
      if (current(sequence)) error.value = message(e)
    } finally {
      if (current(sequence)) busy.value = false
    }
  }
  /** 冻结请求的幂等提交；超时或服务器失败保留表单供原样重试。 */
  async function submit(input: CreateBooking) {
    if (busy.value) return
    busy.value = true
    error.value = ''
    try {
      const d = await submitBooking(input)
      if (current()) {
        selected.value = null
        uncertain.value = false
        mode.value = 'bookings'
        success.value = '申请已收到，待社区确认。'
        await open(d.id)
      }
    } catch (e) {
      if (current()) {
        error.value = message(e)
        uncertain.value = !(e instanceof ApiError) || e.status === 0 || e.status >= 500
      }
    } finally {
      if (current()) busy.value = false
    }
  }
  /** 提交本人或任务动作，冲突或网络不确定时重新读取，不自动重放状态修改。 */
  async function action(name: string, note: string) {
    const d = detail.value
    if (!d || busy.value) return
    busy.value = true
    error.value = ''
    success.value = ''
    try {
      const result = await act(d.id, name, d.version, note)
      if (current()) {
        await open(result.id)
        success.value = '处理记录已保存。'
      }
    } catch (e) {
      if (current()) {
        const text = message(e)
        await open(d.id)
        error.value = text
      }
    } finally {
      if (current()) busy.value = false
    }
  }
  /** 返回列表并从服务器重新获取状态。 */
  function back() {
    selected.value = null
    detail.value = null
    events.value = []
    void load()
  }
  onMounted(load)
  onBeforeUnmount(() => {
    alive = false
    generation++
    selected.value = null
    detail.value = null
    events.value = []
    elders.value = []
    bookings.value = []
  })
  return {
    staff,
    mode,
    services,
    bookings,
    detail,
    events,
    selected,
    elders,
    rules,
    page,
    total,
    eventPage,
    eventTotal,
    category,
    status,
    busy,
    error,
    success,
    uncertain,
    load,
    switchMode,
    select,
    open,
    submit,
    action,
    back,
  }
}
