import { request } from './http'
import type { Page } from './elders'

export type AdminRole = 'COMMUNITY_OPERATOR' | 'DUTY_OFFICER' | 'PLATFORM_ADMIN' | 'AUDITOR'
export type AccountStatus = 'ACTIVE' | 'DISABLED'
export interface AdminAccount {
  id: string
  username: string
  displayName: string
  role: AdminRole
  communityId: string
  status: AccountStatus
  version: number
  passwordChangeRequired: boolean
}
export interface AccountQuery {
  page: number
  pageSize: number
  keyword: string
  role: AdminRole | ''
  status: AccountStatus | ''
}
export interface AccountEvent {
  id: string
  actorId: string
  action: string
  occurredAt: string
}
export type AccountMutation =
  | { kind: 'create'; requestId: string; username: string; displayName: string; role: AdminRole; password: string }
  | { kind: 'update'; requestId: string; id: string; version: number; displayName: string; role: AdminRole; status: AccountStatus }
  | { kind: 'reset'; requestId: string; id: string; version: number; password: string }

/** 分页读取本社区后台账号；signal用于取消已过期的查询。 */
export function listAdminAccounts(query: AccountQuery, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(query.page), pageSize: String(query.pageSize) })
  if (query.keyword.trim()) params.set('keyword', query.keyword.trim())
  if (query.role) params.set('role', query.role)
  if (query.status) params.set('status', query.status)
  return request<Page<AdminAccount>>('/admin/accounts?' + params, { signal })
}

/** 提交固定请求编号及载荷的账号写入，不自动覆盖版本冲突。 */
export function mutateAdminAccount(attempt: AccountMutation) {
  const { kind, ...input } = attempt
  let path = '/admin/accounts'
  let body: object = input
  if ('id' in input) {
    const { id, ...payload } = input
    path += '/' + encodeURIComponent(id) + (kind === 'reset' ? '/reset-password' : '')
    body = payload
  }
  return request<{ id: string; version: number }>(path, {
    method: kind === 'update' ? 'PUT' : 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

/** 读取账号操作记录，不接收或返回密码正文。 */
export function listAccountEvents(id: string, page: number, signal?: AbortSignal) {
  return request<Page<AccountEvent>>(
    '/admin/accounts/' + encodeURIComponent(id) + '/events?page=' + page + '&pageSize=10',
    { signal },
  )
}
