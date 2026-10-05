import type { BookingStatus } from './booking'
export type { Page } from './booking'

export const notificationActionLabels = {
  CREATE: '收到新的预约申请',
  CONFIRM: '预约已确认',
  RESCHEDULE: '预约安排已调整',
  REJECT: '预约申请未通过',
  CANCEL: '预约已取消',
  START: '服务已开始',
  COMPLETE: '服务已完成',
  EXCEPTION: '服务出现异常，待协调处理',
  RESOLVE: '已记录异常处理结果',
  TERMINATE: '服务已终止',
  TASK_TRANSFERRED: '任务安排已调整，请以当前任务列表为准',
} as const
export type NotificationAction = keyof typeof notificationActionLabels

export interface Notice {
  id: string
  action: string
  bookingId: string | null
  occurredAt: string
  read: boolean
}

export const todoTypeLabels = {
  PENDING_CONFIRMATION: '待确认预约',
  EXCEPTION: '待处理异常',
  OVERDUE: '服务已超时',
  WAITING_START: '待开始服务',
  IN_PROGRESS: '服务进行中',
} as const
export type TodoType = keyof typeof todoTypeLabels
export interface Todo {
  bookingId: string
  type: TodoType
  status: BookingStatus
  scheduledStart: string | null
  scheduledEnd: string | null
  hasException: boolean
  overdue: boolean
}

export interface DeliveryFailure {
  eventId: string
  action: string
  occurredAt: string
  attempts: number
  errorCode: string | null
  version: number
}

/** 消息标题只使用固定中文模板，未知动作也不展示服务器原始字符串。 */
export function notificationTitle(action: string): string {
  return Object.prototype.hasOwnProperty.call(notificationActionLabels, action)
    ? notificationActionLabels[action as NotificationAction]
    : '预约状态有更新，请查看当前预约'
}

/** 改派移交提示永不返回预约入口，即使旧响应仍携带预约编号也不允许跳转。 */
export function notificationBookingId(notice: Pick<Notice, 'action' | 'bookingId'>): string | null {
  if (notice.action === 'TASK_TRANSFERRED') return null
  return notice.bookingId?.trim() || null
}

/** 将待办类型转换为固定标题，具体处理结果仍以预约当前状态为准。 */
export function todoTitle(type: string): string {
  return Object.prototype.hasOwnProperty.call(todoTypeLabels, type) ? todoTypeLabels[type as TodoType] : '待处理预约'
}

/** 按固定北京时间展示消息和预约时段，兼容小程序且不依赖设备时区或Intl。 */
export function notificationTime(value: string | null): string {
  if (!value) return '尚未安排'
  const date = new Date(new Date(value).getTime() + 8 * 3600000)
  if (Number.isNaN(date.getTime())) return '时间待确认'
  return date.toISOString().slice(0, 16).replace('T', ' ')
}

/** 后台消息入口限本社区运营及平台管理员，强制改密期间保持关闭。 */
export function canViewAdminNotifications(role?: string, communityId?: string | null, passwordChangeRequired = false): boolean {
  return !!communityId && !passwordChangeRequired && ['COMMUNITY_OPERATOR', 'PLATFORM_ADMIN'].includes(role ?? '')
}
