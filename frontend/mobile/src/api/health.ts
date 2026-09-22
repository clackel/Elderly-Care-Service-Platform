import { request } from './request'
import { createHealthApi } from '../../../shared/healthApi'
export * from '../../../shared/health'
export const healthApi = createHealthApi(request)

