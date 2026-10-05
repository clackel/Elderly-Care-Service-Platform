import { request } from './request'
import type { Notice, Page, Todo, TodoType } from '../../../shared/notifications'
export * from '../../../shared/notifications'

/** 分页读取当前账号仍有权查看的消息，内容不包含预约个人资料。 */
export function noticePage(page: number, unreadOnly: boolean) {
  return request<Page<Notice>>('/notifications?page=' + page + '&pageSize=10&unreadOnly=' + unreadOnly)
}

/** 未读数量由服务端按当前授权范围计算，不沿用撤销前的计数。 */
export function unreadCount() {
  return request<{ count: number }>('/notifications/unread-count')
}

/** 单条已读为可重复写入，不改变预约状态或完成待办。 */
export function readNotice(id: string) {
  return request<null>('/notifications/' + encodeURIComponent(id) + '/read', { method: 'PUT' })
}

/** 工作人员仅能读取自己的当前待办，不提交社区或工作人员身份。 */
export function todoPage(page: number, type: TodoType | '') {
  return request<Page<Todo>>('/todos?page=' + page + '&pageSize=10' + (type ? '&type=' + type : ''))
}
