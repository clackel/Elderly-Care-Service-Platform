import { request } from './http'
import type {
  Page,
  BookingSummary,
  BookingDetail,
  BookingEvent,
  BookingStatus,
  CreateBooking,
  Catalog,
  Provider,
  Worker,
  ElderOption,
  BookingRules,
  Member,
  Binding,
  Grant,
} from '../../../shared/booking'
export * from '../../../shared/booking'
/** 统一发送预约写请求，保留公共会话与CSRF处理。 */
export function write<T>(path: string, body: unknown, method = 'POST'): Promise<T> {
  return request<T>(path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}
/** 分页加载资源，避免只显示第一页选项；调用方负责取消过期请求。 */
export async function options<T>(path: string, signal?: AbortSignal): Promise<T[]> {
  const rows: T[] = []
  for (let page = 1; ; page++) {
    const data = await request<Page<T>>(
      path + (path.includes('?') ? '&' : '?') + 'pageSize=100&page=' + page,
      { signal },
    )
    rows.push(...data.items)
    if (!data.items.length || rows.length >= data.total) return rows
  }
}
/** 读取指定分页与状态的社区预约。 */
export function listBookings(page: number, status: BookingStatus | '', signal?: AbortSignal) {
  return request<Page<BookingSummary>>(
    '/bookings?page=' + page + '&pageSize=10' + (status ? '&status=' + status : ''),
    { signal },
  )
}
/** 读取最新详情及版本，取消信号用于防止旧请求覆盖新选择。 */
export function getBooking(id: string, signal?: AbortSignal) {
  return request<BookingDetail>('/bookings/' + id, { signal })
}
/** 分页读取当前预约的处理记录。 */
export function bookingHistory(id: string, page: number, signal?: AbortSignal) {
  return request<Page<BookingEvent>>('/bookings/' + id + '/history?page=' + page + '&pageSize=10', {
    signal,
  })
}
/** 使用固定请求编号提交代预约。 */
export function createBooking(input: CreateBooking) {
  return write<BookingDetail>('/bookings', input)
}
/** 按版本处理社区允许的状态动作。 */
export function bookingAction(id: string, action: string, version: number, note: string) {
  return write<BookingDetail>('/bookings/' + id + '/' + action, { version, note })
}
/** 提交确认或重新安排，服务端校验人员资格和时间重叠。 */
export function arrangeBooking(
  id: string,
  version: number,
  workerId: string,
  start: string,
  end: string,
  coordinationNote: string,
) {
  return write<BookingDetail>('/bookings/' + id + '/arrange', {
    version,
    workerId,
    start,
    end,
    coordinationNote,
  })
}
/** 读取运行中的预约时间规则。 */
export function bookingRules(signal?: AbortSignal) {
  return request<BookingRules>('/bookings/rules', { signal })
}
/** 分页读取可预约老人最小资料，不通过完整档案接口绕过权限。 */
export async function elderOptions(signal?: AbortSignal) {
  const rows: ElderOption[] = []
  for (let page = 1; ; page++) {
    const batch = await request<ElderOption[]>('/bookings/elders?pageSize=100&page=' + page, {
      signal,
    })
    rows.push(...batch)
    if (batch.length < 100) return rows
  }
}
export type CatalogResource = Catalog | Provider | Worker | Member | Binding | Grant
