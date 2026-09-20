import { request } from './request'
import type {
  Page,
  Catalog,
  BookingSummary,
  BookingDetail,
  BookingEvent,
  ElderOption,
  BookingRules,
  CreateBooking,
  Grant,
} from '../../../shared/booking'
export * from '../../../shared/booking'
/** 分页读取本社区上架目录。 */
export function catalogPage(page: number, category = '') {
  return request<Page<Catalog>>(
    '/services?page=' +
      page +
      '&pageSize=10&available=true' +
      (category ? '&category=' + category : ''),
  )
}
/** 分页读取本人、授权老人预约或本人任务。 */
export function bookingPage(page: number, status = '') {
  return request<Page<BookingSummary>>(
    '/bookings?page=' + page + '&pageSize=10' + (status ? '&status=' + status : ''),
  )
}
/** 读取一笔预约的最新详情。 */
export function bookingDetail(id: string) {
  return request<BookingDetail>('/bookings/' + id)
}
/** 按页读取该预约的状态进度。 */
export function historyPage(id: string, page: number) {
  return request<Page<BookingEvent>>('/bookings/' + id + '/history?page=' + page + '&pageSize=10')
}
/** 读取已核验本人或有效授权老人，排除不可预约对象。 */
export async function eligibleElders() {
  const rows: ElderOption[] = []
  for (let page = 1; ; page++) {
    const data = await request<ElderOption[]>('/bookings/elders?pageSize=100&page=' + page)
    rows.push(...data)
    if (data.length < 100) return rows
  }
}
/** 读取实际预约时限，供用户提交前确认。 */
export function getRules() {
  return request<BookingRules>('/bookings/rules')
}
/** 提交固定请求编号的申请，网络重试不会产生重复业务结果。 */
export function submitBooking(data: CreateBooking) {
  return request<BookingDetail>('/bookings', { method: 'POST', data })
}
/** 提交取消或本人任务动作，后端始终重新核验身份和状态。 */
export function act(id: string, action: string, version: number, note: string) {
  return request<BookingDetail>('/bookings/' + id + '/' + action, {
    method: 'POST',
    data: { version, note },
  })
}
/** 分页查看本人授权或家属收到的预约授权。 */
export function grants(page: number) {
  return request<Page<Grant>>('/booking-access/grants?page=' + page + '&pageSize=10')
}
/** 由老人本人明确同意后授予权限，不能用于健康资料授权。 */
export function grant(data: {
  elderId: string
  familyId: string
  canBook: boolean
  expiresAt: string
  version: number
}) {
  return request<Grant>('/booking-access/grants', {
    method: 'POST',
    data: { ...data, consentReference: '老人本人在线明确同意' },
  })
}
/** 按版本撤销预约授权。 */
export function revokeGrant(id: string, version: number) {
  return request<Grant>('/booking-access/grants/' + id + '/revoke', {
    method: 'POST',
    data: { version },
  })
}
/** 生成仅用于业务去重的UUID；它不是认证凭证。 */
export function newRequestId(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const r = Math.floor(Math.random() * 16)
    return (char === 'x' ? r : (r & 3) | 8).toString(16)
  })
}
