import { computed, onBeforeUnmount, onMounted, shallowRef } from 'vue'
import { ApiError } from '../../api/request'
import { healthApi, healthDateRange, healthEpoch, healthPreset, healthWriteSnapshot, type HealthElder, type HealthRecord,
  type HealthRevision, type HealthGrant, type HealthRecipient, type HealthType, type GlucoseScene, type HealthScope,
  type HealthGrantInput, type Measurement } from '../../api/health'

/** 健康页只持有本次可见期间的内存，读取世代隔离和固定写快照阻止过期响应或重复提交。 */
export function useHealthRecords() {
  const elders = shallowRef<HealthElder[]>([]), selected = shallowRef<HealthElder | null>(null)
  const rows = shallowRef<HealthRecord[]>([]), detail = shallowRef<HealthRecord | null>(null), history = shallowRef<HealthRevision[]>([])
  const points = shallowRef<HealthRecord[]>([]), grants = shallowRef<HealthGrant[]>([]), recipient = shallowRef<HealthRecipient | null>(null)
  const page = shallowRef(1), total = shallowRef(0), elderPage = shallowRef(1), elderTotal = shallowRef(0)
  const historyPage = shallowRef(1), historyTotal = shallowRef(0), grantPage = shallowRef(1), grantTotal = shallowRef(0)
  const type = shallowRef<HealthType | ''>(''), status = shallowRef('ACTIVE'), start = shallowRef(''), end = shallowRef('')
  const busy = shallowRef(false), error = shallowRef(''), notice = shallowRef(''), conflict = shallowRef(false)
  const editor = shallowRef<'create' | 'correct' | null>(null)
  const pending = shallowRef<ReturnType<typeof healthWriteSnapshot> | null>(null)
  const epoch = healthEpoch()
  let alive = true
  const locked = computed(() => busy.value || !!pending.value)
  /** 清空正文、草稿入口和全部身份候选，并使在途读取失效。 */
  function clear() {
    epoch.next(); elders.value = []; selected.value = null; rows.value = []; detail.value = null; history.value = []
    points.value = []; grants.value = []; recipient.value = null; editor.value = null; pending.value = null; busy.value = false
  }
  /** 用单一状态边界执行请求；权限失效立即清空所有健康信息。 */
  async function run(task: (valid: () => boolean) => Promise<void>) {
    if (busy.value || !alive) return
    const ticket = epoch.next()
    const valid = () => alive && epoch.current(ticket)
    busy.value = true; error.value = ''
    try { await task(valid) }
    catch (e) {
      if (!valid()) return
      if (e instanceof ApiError && [401, 403, 404].includes(e.status)) clear()
      error.value = e instanceof Error ? e.message + (e instanceof ApiError && e.traceId ? '（追踪编号：' + e.traceId + '）' : '') : '读取失败，请重试'
    } finally { if (valid()) busy.value = false }
  }
  /** 首次或主动刷新时重新读取实际老人范围和独立健康同意事实。 */
  async function initialize() {
    if (pending.value) return
    clear()
    await run(async (valid) => {
      const [e, g] = await Promise.all([healthApi.elders(elderPage.value), healthApi.grants(grantPage.value)])
      if (valid()) { elders.value = e.items; elderTotal.value = e.total; grants.value = g.items; grantTotal.value = g.total }
    })
  }
  /** 切换老人先销毁上一老人正文、趋势和表单，再重新核验列表。 */
  async function select(elder: HealthElder) {
    if (locked.value) return
    selected.value = elder; page.value = 1; detail.value = null; editor.value = null; history.value = []; points.value = []
    recipient.value = null; rows.value = []; type.value = ''; status.value = 'ACTIVE'; start.value = ''; end.value = ''; conflict.value = false
    await load()
  }
  /** 按当前筛选分页；不保留失败读取前的旧列表。 */
  async function load(next = 1) {
    const elder = selected.value
    if (!elder || locked.value) return
    page.value = next; rows.value = []; points.value = []; detail.value = null; history.value = []; editor.value = null
    await run(async (valid) => {
      if (!!start.value !== !!end.value) throw new Error('请同时填写开始与结束日期，或同时留空')
      const range = start.value && end.value ? healthDateRange(start.value, end.value) : undefined
      const result = await healthApi.records(elder.id, page.value, type.value, status.value, range)
      if (valid()) { rows.value = result.items; total.value = result.total }
    })
  }
  /** 打开最新详情时先清空正文和历史，失败不继续展示旧资料。 */
  async function open(id: string) {
    if (locked.value) return
    detail.value = null; history.value = []; editor.value = null; conflict.value = false
    await run(async (valid) => { const result = await healthApi.detail(id); if (valid()) detail.value = result })
  }
  /** 本人明确打开历史时才读取旧值。 */
  async function revisions(next: number) {
    const record = detail.value; if (!record?.canHistory || locked.value) return
    history.value = []
    await run(async (valid) => {
      const result = await healthApi.revisions(record.id, next)
      if (valid()) { history.value = result.items; historyPage.value = result.page; historyTotal.value = result.total }
    })
  }
  /** 用户选择单一指标、血糖场景与近日期范围后读取原始点。 */
  async function trend(metric: HealthType, scene: GlucoseScene, days: number) {
    const elder = selected.value; if (!elder?.canTrend || locked.value) return
    points.value = []
    await run(async (valid) => {
      const result = await healthApi.trends(elder.id, metric, metric === 'BLOOD_GLUCOSE' ? scene : '', healthPreset(days))
      if (valid()) points.value = result
    })
  }
  /** 新建或更正只打开表单；更正保留读到的版本直到明确刷新。 */
  function edit(mode: 'create' | 'correct') {
    if (locked.value || !selected.value?.canWrite || (mode === 'correct' && !detail.value?.canWrite)) return
    editor.value = mode; conflict.value = false; error.value = ''; notice.value = ''
  }
  /** 关闭表单或详情，丢弃本次草稿；未知提交结果不能静默丢弃。 */
  function close() {
    if (locked.value) return
    editor.value = null; detail.value = null; history.value = []; conflict.value = false
  }
  /** 保存固定快照，首次与重试使用同一编号和正文；失败后不自动覆盖版本冲突。 */
  async function submitWrite(snapshot: ReturnType<typeof healthWriteSnapshot>) {
    await run(async (valid) => {
      pending.value = snapshot
      let result
      try { result = await healthApi.write(snapshot.path, snapshot.data) }
      catch (e) {
        if (valid() && e instanceof ApiError && e.status >= 400 && e.status < 500) {
          pending.value = null; conflict.value = e.status === 409
        }
        throw e
      }
      if (!valid()) return
      pending.value = null; editor.value = null; history.value = []; points.value = []; detail.value = null
      notice.value = '操作已保存。'
      const g = await healthApi.grants(grantPage.value)
      if (!valid()) return
      grants.value = g.items; grantTotal.value = g.total
      if (selected.value && snapshot.path.includes('/records')) {
        const [d, list] = await Promise.all([healthApi.detail(result.id), healthApi.records(selected.value.id, page.value, type.value, status.value,
          start.value && end.value ? healthDateRange(start.value, end.value) : undefined)])
        if (valid()) { detail.value = d; rows.value = list.items; total.value = list.total }
      }
    })
  }
  /** 规范化测量由纯表单传入，业务层添加版本、对象和唯一请求编号。 */
  async function save(measurement: Measurement, reason: string) {
    if (locked.value || conflict.value || !selected.value) return
    const record = detail.value
    if (editor.value === 'correct' && record) await submitWrite(healthWriteSnapshot('/health/records/' + record.id + '/corrections', { version: record.version, measurement, reason }))
    else if (editor.value === 'create') await submitWrite(healthWriteSnapshot('/health/elders/' + selected.value.id + '/records', { measurement }))
  }
  /** 作废必须由用户确认并填写原因，保持原始读取版本。 */
  async function voidRecord(reason: string) {
    const record = detail.value; if (locked.value || !record?.canWrite || !reason.trim()) return
    await submitWrite(healthWriteSnapshot('/health/records/' + record.id + '/void', { version: record.version, reason: reason.trim() }))
  }
  /** 超时后的显式重试不从可变表单重新取值。 */
  async function retry() { if (pending.value && !busy.value) await submitWrite(pending.value) }
  /** 核对候选前清除旧身份，防止把上一账号结果用于新的授权。 */
  async function resolve(recipientId: string, scope: HealthScope) {
    const elder = selected.value; if (!elder?.canGrant || locked.value) return
    recipient.value = null
    await run(async (valid) => { const result = await healthApi.resolve(elder.id, recipientId, scope); if (valid()) recipient.value = result })
  }
  /** 用户修改账号或范围时撤销已核对身份。 */
  function clearRecipient() { recipient.value = null }
  /** 明确同意后创建独立授权，禁止直接依赖预约授权对象。 */
  async function grant(input: HealthGrantInput) {
    if (locked.value || !selected.value?.canGrant || recipient.value?.id !== input.recipientId) return
    await submitWrite(healthWriteSnapshot('/health/grants', input))
    if (!pending.value) recipient.value = null
  }
  /** 本人撤销或受权人主动放弃；成功后重新读取授权与老人入口。 */
  async function revoke(value: HealthGrant) {
    if (locked.value) return
    await submitWrite(healthWriteSnapshot('/health/grants/' + value.id + '/revoke', { version: value.version }))
    if (!pending.value && !error.value) await initialize()
  }
  /** 授权事实分页独立于老人记录页。 */
  async function moreGrants(next: number) {
    if (locked.value) return
    grants.value = []
    await run(async (valid) => {
      const result = await healthApi.grants(next)
      if (valid()) { grants.value = result.items; grantPage.value = next; grantTotal.value = result.total }
    })
  }
  /** 老人选项有界分页，翻页清除旧老人健康内容。 */
  async function moreElders(next: number) { if (!locked.value) { elderPage.value = next; await initialize() } }
  onMounted(initialize)
  onBeforeUnmount(() => { alive = false; clear() })
  return { elders, selected, rows, detail, history, points, grants, recipient, page, total, elderPage, elderTotal,
    historyPage, historyTotal, grantPage, grantTotal, type, status, start, end, busy, error, notice, conflict, editor, pending, locked,
    initialize, select, load, open, revisions, trend, edit, close, save, voidRecord, retry, resolve, clearRecipient, grant, revoke, moreGrants, moreElders }
}

