export const categoryLabels = {
  MEAL: '助餐',
  CLEANING: '助洁',
  ESCORT: '陪诊',
  CARE: '护理',
  REHABILITATION: '康复',
} as const
export type Category = keyof typeof categoryLabels
export const statusLabels = {
  PENDING: '待社区确认',
  CONFIRMED: '已确认',
  IN_PROGRESS: '服务中',
  COMPLETED: '已完成',
  REJECTED: '已拒绝',
  CANCELLED: '已取消',
  TERMINATED: '已终止',
} as const
export type BookingStatus = keyof typeof statusLabels
export const actionLabels: Record<string, string> = {
  CREATE: '提交申请',
  CONFIRM: '社区确认',
  RESCHEDULE: '调整安排',
  REJECT: '拒绝申请',
  CANCEL: '取消预约',
  START: '开始服务',
  COMPLETE: '完成服务',
  EXCEPTION: '上报异常',
  RESOLVE: '异常处理',
  TERMINATE: '终止服务',
}
export interface Page<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}
export interface Catalog {
  id: string
  category: Category
  name: string
  description: string
  durationMinutes: number
  priceFen: number
  serviceArea: string
  providerId: string
  providerName: string
  professional: boolean
  qualification: string
  enabled: boolean
  version: number
}
export interface Provider {
  id: string
  name: string
  enabled: boolean
  version: number
}
export interface Worker {
  id: string
  name: string
  providerId: string
  categories: Category[]
  serviceArea: string
  qualification: string
  qualificationExpiresAt: string | null
  enabled: boolean
  version: number
}
export interface Specific {
  mealDate?: string
  meal?: string
  portions?: number
  dietaryRequirements?: string
  cleaningScope?: string
  hospital?: string
  appointmentTime?: string
  meetingPoint?: string
  assistance?: string
  careContent?: string
  precautions?: string
  location?: string
  assessmentRequired?: boolean
}
export interface Application {
  elderId: string
  serviceId: string
  requestedStart: string
  address: string
  contactName: string
  contactPhone: string
  remark: string
  specific: Specific
}
export interface CreateBooking {
  requestId: string
  application: Application
}
export interface BookingSummary {
  id: string
  elderId: string
  serviceName: string
  category: Category
  status: BookingStatus
  requestedStart: string
  scheduledStart: string | null
  scheduledEnd: string | null
  hasException: boolean
  version: number
}
export interface BookingDetail {
  id: string
  elderId: string
  applicantId: string
  service: Catalog
  application: Application | null
  status: BookingStatus
  workerId: string | null
  workerName: string | null
  scheduledStart: string | null
  scheduledEnd: string | null
  startedAt: string | null
  completedAt: string | null
  hasException: boolean
  result: string | null
  version: number
  createdAt: string
  updatedAt: string
}
export interface BookingEvent {
  id: string
  actorId: string
  action: string
  fromStatus: BookingStatus
  toStatus: BookingStatus
  version: number
  note: string
  occurredAt: string
}
export interface ElderOption {
  id: string
  name: string
  canBook: boolean
  address: string | null
  contactName: string | null
  contactPhone: string | null
}
export interface BookingRules {
  advanceMinutes: number
  horizonDays: number
  cancelBeforeMinutes: number
}
export interface Member {
  id: string
  displayName: string
  role: 'ELDER' | 'FAMILY' | 'STAFF'
  status: string
  wechatLinked: boolean
}
export interface Binding {
  id: string
  accountId: string
  elderId: string
  active: boolean
  version: number
}
export interface Grant {
  id: string
  elderId: string
  familyId: string
  canBook: boolean
  expiresAt: string
  revoked: boolean
  version: number
}
export interface Field {
  key: string
  label: string
  type?: 'text' | 'textarea' | 'number' | 'date' | 'datetime-local' | 'select' | 'multi'
  required?: boolean
  max?: number
  min?: number
  options?: { value: string; label: string }[]
}
export type FormValues = Record<string, string>
/** 为管理端和移动端提供统一中文选项。 */
export function choices(labels: Record<string, string>) {
  return Object.entries(labels).map(([value, label]) => ({ value, label }))
}
export const specificFields: Record<Category, Field[]> = {
  MEAL: [
    { key: 'mealDate', label: '用餐日期', type: 'date', required: true },
    {
      key: 'meal',
      label: '餐次',
      type: 'select',
      required: true,
      options: choices({ BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐' }),
    },
    { key: 'portions', label: '份数', type: 'number', required: true, min: 1, max: 20 },
    { key: 'dietaryRequirements', label: '必要饮食要求', max: 200 },
  ],
  CLEANING: [
    {
      key: 'cleaningScope',
      label: '清洁范围',
      type: 'select',
      required: true,
      options: choices({
        DAILY: '日常保洁',
        DEEP: '深度清洁',
        KITCHEN: '厨房',
        BATHROOM: '卫生间',
      }),
    },
  ],
  ESCORT: [
    { key: 'hospital', label: '医院', required: true, max: 100 },
    { key: 'appointmentTime', label: '就诊时间', type: 'datetime-local', required: true },
    { key: 'meetingPoint', label: '集合地点', required: true, max: 200 },
    {
      key: 'assistance',
      label: '行动协助',
      type: 'select',
      required: true,
      options: choices({ NONE: '无需协助', WALKING: '步行协助', WHEELCHAIR: '轮椅协助' }),
    },
  ],
  CARE: [
    {
      key: 'careContent',
      label: '照护内容',
      type: 'select',
      required: true,
      options: choices({
        DAILY_LIVING: '日常生活照护',
        PERSONAL_CARE: '个人照护',
        PROFESSIONAL: '专业护理',
      }),
    },
    { key: 'precautions', label: '必要注意事项', type: 'textarea', max: 300 },
  ],
  REHABILITATION: [
    {
      key: 'location',
      label: '服务地点',
      type: 'select',
      required: true,
      options: choices({ HOME: '家中', COMMUNITY_CENTER: '社区中心', PROVIDER_SITE: '服务机构' }),
    },
    {
      key: 'assessmentRequired',
      label: '是否需要先评估',
      type: 'select',
      required: true,
      options: choices({ true: '需要', false: '不需要' }),
    },
    { key: 'precautions', label: '必要注意事项', type: 'textarea', max: 300 },
  ],
}
/** 只提交当前分类字段，并转换明确的数值、布尔和时区时间。 */
export function makeSpecific(category: Category, values: FormValues): Specific {
  const result: Record<string, string | number | boolean> = {}
  for (const field of specificFields[category]) {
    const value = values[field.key]
    if (!value) continue
    result[field.key] =
      field.type === 'number'
        ? Number(value)
        : field.type === 'datetime-local'
          ? new Date(value).toISOString()
          : field.key === 'assessmentRequired'
            ? value === 'true'
            : value.trim()
  }
  return result as Specific
}
/** 以用户本地时区显示带时区时间，空值明确表示尚未安排。 */
export function time(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '尚未安排'
}
/** 将服务器时间转换成本地日期时间表单值。 */
export function localTime(value?: string | null): string {
  if (!value) return ''
  const d = new Date(value)
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}
/** 金额只做展示，不建立支付状态。 */
export function price(fen: number): string {
  return (fen / 100).toFixed(2)
}
/** 根据分类将必要资料转换为中文详情，避免展示内部枚举。 */
export function specificLines(
  category: Category,
  data: Specific,
): { label: string; value: string }[] {
  return specificFields[category].flatMap((field) => {
    const value = data[field.key as keyof Specific]
    if (value === undefined || value === null || value === '') return []
    const text =
      field.type === 'datetime-local'
        ? time(String(value))
        : (field.options?.find((option) => option.value === String(value))?.label ?? String(value))
    return [{ label: field.label, value: text }]
  })
}
