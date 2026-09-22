import { request } from './http'
import { createHealthApi, type HealthTransport } from '../../../shared/healthApi'
export * from '../../../shared/health'
/** 管理端健康请求沿用会话和CSRF封装，正文不写入URL。 */
const transport: HealthTransport = <T>(path: string, options?: { method?: 'GET' | 'POST'; data?: unknown }) =>
  request<T>(path, options?.method === 'POST'
    ? { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(options.data) }
    : {})
export const healthApi = createHealthApi(transport)

