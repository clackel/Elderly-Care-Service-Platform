import { request } from './http'

export type ElderStatus = 'ACTIVE' | 'ARCHIVED'
export type Gender = 'MALE' | 'FEMALE' | 'UNKNOWN'
export type LivingArrangement = 'ALONE' | 'WITH_FAMILY' | 'INSTITUTION' | 'OTHER' | 'UNKNOWN'
export interface ElderProfile {
  name: string
  gender: Gender
  birthDate: string
  phone: string | null
  address: string
  livingArrangement: LivingArrangement
  emergencyContactName: string
  emergencyContactPhone: string
  emergencyContactRelation: string
  remark: string | null
}
export interface ElderSummary {
  id: string
  name: string
  gender: Gender
  age: number
  maskedPhone: string | null
  livingArrangement: LivingArrangement
  status: ElderStatus
  version: number
  updatedAt: string
}
export interface ElderDetail {
  id: string
  communityId: string
  profile: ElderProfile
  status: ElderStatus
  version: number
  createdAt: string
  updatedAt: string
}
export interface ElderHistory {
  id: string
  actorId: string
  action: 'CREATE' | 'UPDATE' | 'ARCHIVE' | 'RESTORE'
  changedFields: string[]
  version: number
  occurredAt: string
}
export interface Page<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}
export interface ElderQuery {
  page: number
  pageSize: number
  keyword: string
  status: ElderStatus | ''
}
export interface CreateAttempt {
  requestId: string
  profile: ElderProfile
}

/** 查询本社区档案，检索范围由后端校验；signal用于淘汰过期读取。 */
export function listElders(query: ElderQuery, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(query.page), pageSize: String(query.pageSize) })
  if (query.keyword.trim()) params.set('keyword', query.keyword.trim())
  if (query.status) params.set('status', query.status)
  return request<Page<ElderSummary>>('/elders?' + params, { signal })
}

/** 按档案编号读取最新资料与版本。 */
export function getElder(id: string, signal?: AbortSignal) {
  return request<ElderDetail>('/elders/' + encodeURIComponent(id), { signal })
}

/** 提交带稳定请求编号的建档资料；由公共请求层附加CSRF凭证。 */
export function createElder(input: CreateAttempt) {
  return request<ElderDetail>('/elders', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

/** 使用详情版本完整替换资料，不自动重试冲突写入。 */
export function updateElder(id: string, version: number, profile: ElderProfile) {
  return request<ElderDetail>('/elders/' + encodeURIComponent(id), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ version, profile }),
  })
}

/** 按当前版本归档或恢复档案，保持状态变化可追踪。 */
export function transitionElder(id: string, version: number, action: 'archive' | 'restore') {
  return request<ElderDetail>('/elders/' + encodeURIComponent(id) + '/' + action, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ version }),
  })
}

/** 分页读取变更历史，不包含历史个人资料正文。 */
export function getElderHistory(id: string, page: number, signal?: AbortSignal) {
  return request<Page<ElderHistory>>(
    '/elders/' + encodeURIComponent(id) + '/history?page=' + page + '&pageSize=5',
    { signal },
  )
}
