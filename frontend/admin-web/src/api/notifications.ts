import { request } from './http'
import type { DeliveryFailure, Notice, Page, Todo, TodoType } from '../../../shared/notifications'
export * from '../../../shared/notifications'

/** 读取当前账号仍有权限访问的站内消息，旧分页查询可通过signal取消。 */
export function listNotifications(page: number, unreadOnly: boolean, signal?: AbortSignal) {
  return request<Page<Notice>>(
    '/notifications?page=' + page + '&pageSize=10&unreadOnly=' + unreadOnly,
    { signal },
  )
}

/** 读取实时未读数，不把读消息视为完成业务待办。 */
export function notificationUnreadCount(signal?: AbortSignal) {
  return request<{ count: number }>('/notifications/unread-count', { signal })
}

/** 标记当前账号的一条消息为已读；重复请求不会重复产生业务效果。 */
export function readNotification(id: string, signal?: AbortSignal) {
  return request<null>('/notifications/' + encodeURIComponent(id) + '/read', {
    method: 'PUT',
    signal,
  })
}

/** 待办由后端依据实时预约状态派生，界面不提供手工完成操作。 */
export function listTodos(page: number, type: TodoType | '', signal?: AbortSignal, pageSize = 10) {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  if (type) params.set('type', type)
  return request<Page<Todo>>('/todos?' + params, { signal })
}

/** 分页读取本社区消息发送异常，仅接收稳定错误码，不展示底层服务错误。 */
export function listDeliveryFailures(page: number, signal?: AbortSignal) {
  return request<Page<DeliveryFailure>>('/notifications/deliveries?page=' + page + '&pageSize=10', {
    signal,
  })
}

/** 使用已读取版本申请重新发送；提交成功只表示已受理，不表示消息已送达。 */
export function retryNotificationDelivery(eventId: string, version: number, signal?: AbortSignal) {
  return request<null>('/notifications/deliveries/' + encodeURIComponent(eventId) + '/retry', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ version }),
    signal,
  })
}
