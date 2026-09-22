import { computed, onBeforeUnmount, onMounted, shallowRef } from 'vue'
import { ApiError } from '../../api/http'
import { healthApi, healthDateRange, healthEpoch, healthWriteSnapshot, type HealthElder, type HealthRecord, type HealthGrant, type HealthType, type Measurement } from '../../api/health'

/** 社区页面仅编排获授权老人及本人代录记录，不提供全档案、趋势或历史入口。 */
export function useHealthRecords() {
  const elders = shallowRef<HealthElder[]>([]), selected = shallowRef<HealthElder | null>(null)
  const rows = shallowRef<HealthRecord[]>([]), detail = shallowRef<HealthRecord | null>(null), grants = shallowRef<HealthGrant[]>([])
  const page = shallowRef(1), total = shallowRef(0), elderPage = shallowRef(1), elderTotal = shallowRef(0), grantPage = shallowRef(1), grantTotal = shallowRef(0)
  const type = shallowRef<HealthType | ''>(''), status = shallowRef('ACTIVE'), start = shallowRef(''), end = shallowRef('')
  const busy = shallowRef(false), error = shallowRef(''), notice = shallowRef(''), conflict = shallowRef(false)
  const editor = shallowRef<'create' | 'correct' | null>(null), pending = shallowRef<ReturnType<typeof healthWriteSnapshot> | null>(null)
  const locked = computed(() => busy.value || !!pending.value), epoch = healthEpoch()
  let alive = true
  /** 页面清理使迟到响应失效，并释放所有健康内容及写请求快照。 */
  function clear() {
    epoch.next(); elders.value = []; selected.value = null; rows.value = []; detail.value = null; grants.value = []
    editor.value = null; pending.value = null; busy.value = false
  }
  /** 请求状态统一管理，任何对象权限失效都立即清理健康数据。 */
  async function run(task: (valid: () => boolean) => Promise<void>) {
    if (busy.value || !alive) return
    const ticket = epoch.next(), valid = () => alive && epoch.current(ticket)
    busy.value = true; error.value = ''
    try { await task(valid) }
    catch (e) {
      if (!valid()) return
      if (e instanceof ApiError && [401,403,404].includes(e.status)) clear()
      error.value = e instanceof Error ? e.message + (e instanceof ApiError && e.traceId ? '（追踪编号：' + e.traceId + '）' : '') : '请求失败，请重试'
    } finally { if (valid()) busy.value = false }
  }
  /** 刷新服务端授权老人和收到的授权事实，名单不复用档案管理目录。 */
  async function initialize() {
    if (pending.value) return
    clear()
    await run(async (valid) => {
      const [e, g] = await Promise.all([healthApi.elders(elderPage.value), healthApi.grants(grantPage.value)])
      if (valid()) { elders.value = e.items; elderTotal.value = e.total; grants.value = g.items; grantTotal.value = g.total }
    })
  }
  /** 切换老人清空上一个对象的全部正文和编辑状态。 */
  async function select(elder: HealthElder) {
    if (locked.value) return
    selected.value = elder; detail.value = null; editor.value = null; rows.value = []; page.value = 1
    type.value = ''; status.value = 'ACTIVE'; start.value = ''; end.value = ''; conflict.value = false
    await load(1)
  }
  /** 筛选只作用于服务端允许的原始代录记录。 */
  async function load(next = 1) {
    const elder = selected.value; if (!elder || locked.value) return
    page.value = next; rows.value = []; detail.value = null; editor.value = null
    await run(async (valid) => {
      if (!!start.value !== !!end.value) throw new Error('请同时填写开始与结束日期，或同时留空')
      const range = start.value && end.value ? healthDateRange(start.value, end.value) : undefined
      const result = await healthApi.records(elder.id, next, type.value, status.value, range)
      if (valid()) { rows.value = result.items; total.value = result.total }
    })
  }
  /** 详情实时核验授权与接管标志；读取失败不保留旧正文。 */
  async function open(id: string) {
    if (locked.value) return
    detail.value = null; editor.value = null; conflict.value = false
    await run(async (valid) => { const result = await healthApi.detail(id); if (valid()) detail.value = result })
  }
  /** 编辑入口受服务端动作标志约束，不能修改其他人的记录。 */
  function edit(mode: 'create' | 'correct') {
    if (locked.value || !selected.value?.canWrite || (mode === 'correct' && !detail.value?.canWrite)) return
    editor.value = mode; conflict.value = false; error.value = ''; notice.value = ''
  }
  /** 明确放弃未保存草稿；不丢弃未知提交结果。 */
  function close() { if (!locked.value) { editor.value = null; detail.value = null; conflict.value = false } }
  /** 事务响应仅返回标识和版本，成功后重新读取当前可见内容。 */
  async function submitWrite(snapshot: ReturnType<typeof healthWriteSnapshot>) {
    await run(async (valid) => {
      pending.value = snapshot
      let result
      try { result = await healthApi.write(snapshot.path, snapshot.data) }
      catch (e) {
        if (valid() && e instanceof ApiError && e.status >= 400 && e.status < 500) { pending.value = null; conflict.value = e.status === 409 }
        throw e
      }
      if (!valid()) return
      pending.value = null; editor.value = null; detail.value = null; notice.value = '操作已保存。'
      if (snapshot.path.includes('/records') && selected.value) {
        const [d, list] = await Promise.all([healthApi.detail(result.id), healthApi.records(selected.value.id, page.value, type.value, status.value,
          start.value && end.value ? healthDateRange(start.value, end.value) : undefined)])
        if (valid()) { detail.value = d; rows.value = list.items; total.value = list.total }
      }
    })
  }
  /** 固定当前对象、版本和规范化测量，未知结果重试不重新生成编号。 */
  async function save(measurement: Measurement, reason: string) {
    if (locked.value || conflict.value || !selected.value) return
    const d = detail.value
    if (editor.value === 'correct' && d) await submitWrite(healthWriteSnapshot('/health/records/' + d.id + '/corrections', { version: d.version, measurement, reason }))
    else if (editor.value === 'create') await submitWrite(healthWriteSnapshot('/health/elders/' + selected.value.id + '/records', { measurement }))
  }
  /** 原代录人员仅可作废仍由自己维护的有效记录。 */
  async function voidRecord(reason: string) {
    const d = detail.value; if (locked.value || !d?.canWrite || !reason.trim()) return
    await submitWrite(healthWriteSnapshot('/health/records/' + d.id + '/void', { version: d.version, reason: reason.trim() }))
  }
  /** 显式重试保留的同编号原载荷。 */
  async function retry() { if (pending.value && !busy.value) await submitWrite(pending.value) }
  /** 当前受权人可以放弃单条授权，不能为老人创建或管理其他人授权。 */
  async function revoke(grant: HealthGrant) {
    if (locked.value) return
    await submitWrite(healthWriteSnapshot('/health/grants/' + grant.id + '/revoke', { version: grant.version }))
    if (!pending.value && !error.value) await initialize()
  }
  /** 老人选项分页，每次翻页重新读取授权。 */
  async function moreElders(next: number) { if (!locked.value) { elderPage.value = next; await initialize() } }
  /** 授权事实独立分页，失效记录仅展示事实，不打开健康正文。 */
  async function moreGrants(next: number) {
    if (locked.value) return
    grants.value = []
    await run(async (valid) => {
      const result = await healthApi.grants(next)
      if (valid()) { grants.value = result.items; grantPage.value = next; grantTotal.value = result.total }
    })
  }
  onMounted(initialize)
  onBeforeUnmount(() => { alive = false; clear() })
  return { elders, selected, rows, detail, grants, page, total, elderPage, elderTotal, grantPage, grantTotal, type, status, start, end,
    busy, error, notice, conflict, editor, pending, locked, initialize, select, load, open, edit, close, save, voidRecord, retry, revoke, moreElders, moreGrants }
}

