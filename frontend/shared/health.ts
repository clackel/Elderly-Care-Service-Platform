export const healthTypes = { BLOOD_PRESSURE: '血压', HEART_RATE: '心率', WEIGHT: '体重', BLOOD_GLUCOSE: '血糖' } as const
export type HealthType = keyof typeof healthTypes
export const glucoseScenes = { FASTING: '空腹', BEFORE_MEAL: '餐前', AFTER_MEAL: '餐后', RANDOM: '随机', UNSPECIFIED: '未说明' } as const
export type GlucoseScene = keyof typeof glucoseScenes
export type HealthScope = 'FAMILY_READ' | 'COMMUNITY_ASSIST'
export interface HealthPage<T> { items: T[]; page: number; pageSize: number; total: number }
export interface HealthElder { id: string; name: string; archived: boolean; canWrite: boolean; canTrend: boolean; canGrant: boolean }
export interface Measurement {
  type: HealthType; measuredAt: string; systolic?: number | null; diastolic?: number | null
  heartRate?: number | null; weight?: number | null; glucose?: number | null; glucoseScene?: GlucoseScene | null
}
export interface HealthRecord {
  id: string; elderId: string; measurement: Measurement; unit: string; status: 'ACTIVE' | 'VOID'; version: number
  originalActorId: string; entryMode: 'SELF' | 'COMMUNITY_ASSIST'; ownerTakenOver: boolean; corrected: boolean
  createdAt: string; updatedAt: string; canWrite: boolean; canHistory: boolean
}
export interface HealthRevision { version: number; measurement: Measurement; reason: string; actorId: string; action: 'CREATE' | 'CORRECT' | 'VOID'; occurredAt: string }
export interface HealthGrant {
  id: string; elderId: string; recipientId: string; scope: HealthScope; createdAt: string; expiresAt: string
  revoked: boolean; effective: boolean; version: number; consentVersion: string
}
export interface HealthRecipient { id: string; displayName: string; role: string }
export interface HealthGrantInput { elderId: string; recipientId: string; scope: HealthScope; days: number; accepted: boolean; consentVersion: string }
export interface HealthMutation { id: string; version: number }
export interface HealthDraft { type: HealthType; date: string; time: string; systolic: string; diastolic: string; heartRate: string; weight: string; glucose: string; glucoseScene: GlucoseScene }
export const units: Record<HealthType, string> = { BLOOD_PRESSURE: 'mmHg', HEART_RATE: '次/分钟', WEIGHT: 'kg', BLOOD_GLUCOSE: 'mmol/L' }
/** 管理端健康入口仅限有社区的运营角色，实际老人范围仍由服务端授权决定。 */
export function canAssistHealth(role?: string, communityId?: string | null): boolean {
  return role === 'COMMUNITY_OPERATOR' && !!communityId
}
export const consentText: Record<HealthScope, string> = {
  FAMILY_READ: '我同意该家属查看我的全部当前有效健康记录及趋势，包括本次授权之前的历史记录。不包含更正前旧值和作废记录。',
  COMMUNITY_ASSIST: '我同意该社区人员新增、查看、更正及作废其自己原始代录且尚未由我接管的记录。不授予完整健康档案、趋势或历史旧值访问权。我更正或作废后，该人员将不能再读取这条记录。',
}
/** 以固定上海时间显示，不受设备时区影响。 */
export function healthTime(value: string): string {
  return new Date(new Date(value).getTime() + 8 * 3600000).toISOString().slice(0, 19).replace('T', ' ')
}
/** 将服务器测量映射为本地草稿，默认当前上海时间，字段按类型提交。 */
export function healthDraft(measurement?: Measurement): HealthDraft {
  const time = healthTime(measurement?.measuredAt || new Date().toISOString())
  return { type: measurement?.type || 'BLOOD_PRESSURE', date: time.slice(0, 10), time: time.slice(11, 16),
    systolic: String(measurement?.systolic ?? ''), diastolic: String(measurement?.diastolic ?? ''),
    heartRate: String(measurement?.heartRate ?? ''), weight: String(measurement?.weight ?? ''),
    glucose: String(measurement?.glucose ?? ''), glucoseScene: measurement?.glucoseScene || 'UNSPECIFIED' }
}
/** 校验输入组合及精度，上海日期转换成含时区时间，不把存储上限解释为医学范围。 */
export function toMeasurement(draft: HealthDraft): Measurement {
  const value = new Date(draft.date + 'T' + draft.time + ':00+08:00')
  if (!/^\d{4}-\d{2}-\d{2}$/.test(draft.date) || !/^\d{2}:\d{2}$/.test(draft.time) || !Number.isFinite(value.getTime())
      || value.getTime() < 0 || value.getTime() > Date.now() || healthTime(value.toISOString()).slice(0, 16) !== draft.date + ' ' + draft.time)
    throw new Error('请选择有效的测量日期和时间，不能晚于当前时间')
  const result: Measurement = { type: draft.type, measuredAt: value.toISOString() }
  /** 严格转换当前类型字段，不接受空值、科学记数法或多余小数位。 */
  function numeric(text: string, max: number, integer: boolean): number {
    const clean = text.trim(), number = Number(clean)
    if (!(integer ? /^\d+$/.test(clean) : /^\d+(\.\d{1,2})?$/.test(clean)) || number <= 0 || number > max)
      throw new Error(integer ? '请填写1至999的整数' : '请填写正数，最多两位小数且不超过存储上限')
    return number
  }
  if (draft.type === 'BLOOD_PRESSURE') { result.systolic = numeric(draft.systolic, 999, true); result.diastolic = numeric(draft.diastolic, 999, true) }
  if (draft.type === 'HEART_RATE') result.heartRate = numeric(draft.heartRate, 999, true)
  if (draft.type === 'WEIGHT') result.weight = numeric(draft.weight, 999.99, false)
  if (draft.type === 'BLOOD_GLUCOSE') { result.glucose = numeric(draft.glucose, 99.99, false); result.glucoseScene = draft.glucoseScene }
  return result
}
/** 输出带明确指标、数值和单位的可阅读记录。 */
export function measurementText(m: Measurement): string {
  const value = m.type === 'BLOOD_PRESSURE' ? '收缩压 ' + m.systolic + ' / 舒张压 ' + m.diastolic
    : m.type === 'HEART_RATE' ? m.heartRate : m.type === 'WEIGHT' ? m.weight : m.glucose
  return healthTypes[m.type] + '：' + value + ' ' + units[m.type] + (m.glucoseScene ? '（' + glucoseScenes[m.glucoseScene] + '）' : '')
}
/** 查询整段上海日期，结束日期包含当天，接口仍为左闭右开。 */
export function healthDateRange(start: string, end: string): { from: string; to: string } {
  const from = new Date(start + 'T00:00:00+08:00'), last = new Date(end + 'T00:00:00+08:00')
  if (!/^\d{4}-\d{2}-\d{2}$/.test(start) || !/^\d{4}-\d{2}-\d{2}$/.test(end)
      || !Number.isFinite(from.getTime()) || !Number.isFinite(last.getTime()) || from > last
      || healthTime(from.toISOString()).slice(0, 10) !== start || healthTime(last.toISOString()).slice(0, 10) !== end)
    throw new Error('请选择有效的起止日期')
  return { from: from.toISOString(), to: new Date(last.getTime() + 86400000).toISOString() }
}
/** 近7、30、90天按上海自然日计算，包含今天。 */
export function healthPreset(days: number) {
  const end = healthTime(new Date().toISOString()).slice(0, 10)
  const start = healthTime(new Date(new Date(end + 'T00:00:00+08:00').getTime() - (days - 1) * 86400000).toISOString()).slice(0, 10)
  return { start, end, ...healthDateRange(start, end) }
}
/** 仅生成业务去重编号，不用作安全凭据。 */
export function healthRequestId(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.floor(Math.random() * 16)
    return (c === 'x' ? r : (r & 3) | 8).toString(16)
  })
}
/** 请求世代使退出、切换及权限失效后的旧响应不能回填健康正文。 */
export function healthEpoch() {
  let value = 0
  return { next: () => ++value, current: (ticket: number) => ticket === value }
}
/** 创建脱离表单引用的固定写请求快照，用于未知结果时原样重试。 */
export function healthWriteSnapshot(path: string, data: object) {
  return { path, data: JSON.parse(JSON.stringify({ ...data, requestId: healthRequestId() })) as object }
}
