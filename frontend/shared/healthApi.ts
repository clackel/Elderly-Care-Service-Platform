import type { HealthElder, HealthGrant, HealthGrantInput, HealthMutation, HealthPage, HealthRecipient, HealthRecord, HealthRevision, HealthScope, HealthType, GlucoseScene } from './health'
export type HealthTransport = <T>(path: string, options?: { method?: 'GET' | 'POST'; data?: unknown }) => Promise<T>
/** 两端共享健康接口语义，具体会话、CSRF和网络实现由各端现有封装负责。 */
export function createHealthApi(send: HealthTransport) {
  return {
    /** 分页读取实际授权老人。 */
    elders: (page: number) => send<HealthPage<HealthElder>>('/health/elders?pageSize=10&page=' + page),
    /** 按类型、状态和时间查询当前版本。 */
    records: (id: string, page: number, type: HealthType | '', status: string, range?: { from: string; to: string }) =>
      send<HealthPage<HealthRecord>>('/health/elders/' + id + '/records?pageSize=10&page=' + page + '&status=' + status
        + (type ? '&type=' + type : '') + (range ? '&from=' + encodeURIComponent(range.from) + '&to=' + encodeURIComponent(range.to) : '')),
    /** 单独读取最新详情。 */
    detail: (id: string) => send<HealthRecord>('/health/records/' + id),
    /** 本人分页读取历史旧值。 */
    revisions: (id: string, page: number) => send<HealthPage<HealthRevision>>('/health/records/' + id + '/revisions?pageSize=10&page=' + page),
    /** 获取最长90天的有效原始点。 */
    trends: (id: string, type: HealthType, scene: GlucoseScene | '', range: { from: string; to: string }) =>
      send<HealthRecord[]>('/health/elders/' + id + '/trends?type=' + type + (scene ? '&scene=' + scene : '')
        + '&from=' + encodeURIComponent(range.from) + '&to=' + encodeURIComponent(range.to)),
    /** 获取当前账号可管理的独立健康同意事实。 */
    grants: (page: number) => send<HealthPage<HealthGrant>>('/health/grants?pageSize=10&page=' + page),
    /** 本人按受权人主动提供的账号编号精确核对。 */
    resolve: (elderId: string, recipientId: string, scope: HealthScope) =>
      send<HealthRecipient>('/health/grant-recipients/resolve', { method: 'POST', data: { elderId, recipientId, scope } }),
    /** 写入已冻结的同请求编号载荷，不自动重试或生成新编号。 */
    write: (path: string, data: object) => send<HealthMutation>(path, { method: 'POST', data }),
  }
}
export type HealthApi = ReturnType<typeof createHealthApi>
export type { HealthGrantInput }

