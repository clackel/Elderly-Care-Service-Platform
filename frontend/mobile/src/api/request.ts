interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId: string
}
export class ApiError extends Error {
  /** 保留HTTP状态、错误码和追踪编号，区分权限、冲突及未知提交结果。 */
  constructor(
    public status: number,
    message: string,
    public code?: string,
    public traceId?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}
let unauthorizedHandler: () => void = () => {}
let cookie = '',
  epoch = 0
/** 注册会话失效时的统一清理入口。 */
export function onUnauthorized(handler: () => void) {
  unauthorizedHandler = handler
}
/** 清除仅驻留内存的会话标识，并使旧请求结果失效。 */
export function clearSession() {
  cookie = ''
  epoch++
}
/** 确定H5同源代理或微信已配置的HTTPS接口地址。 */
function baseUrl(): string {
  let url = import.meta.env.VITE_API_BASE_URL || ''
  // #ifdef H5
  url = '/api/v1'
  // #endif
  if (!url) throw new Error('请先配置小程序接口地址')
  // #ifdef MP-WEIXIN
  if (!url.startsWith('https://')) throw new Error('小程序接口地址必须使用HTTPS')
  // #endif
  return url.replace(/\/$/, '')
}
interface Options {
  method?: 'GET' | 'POST' | 'PUT'
  data?: unknown
  headers?: Record<string, string>
}
/** 发起真实会话请求；小程序Cookie只放内存，H5由浏览器管理HttpOnly Cookie。 */
function send<T>(path: string, options: Options = {}): Promise<T> {
  const generation = epoch
  return new Promise((resolve, reject) => {
    let url: string
    try {
      url = baseUrl() + path
    } catch (e) {
      reject(e)
      return
    }
    const header: Record<string, string> = {
      'Content-Type': 'application/json',
      ...options.headers,
    }
    // #ifdef MP-WEIXIN
    if (cookie) header.Cookie = cookie
    // #endif
    uni.request({
      url,
      method: options.method || 'GET',
      data: options.data as UniApp.RequestOptions['data'],
      header,
      timeout: 15000,
      // #ifdef H5
      withCredentials: true,
      // #endif
      success(response) {
        if (generation !== epoch) {
          reject(new ApiError(401, '会话已变化，请重新读取'))
          return
        }
        // #ifdef MP-WEIXIN
        const cookies = (response as typeof response & { cookies?: string[] }).cookies || []
        const raw = response.header['Set-Cookie'] || response.header['set-cookie']
        const candidates = cookies.length ? cookies : typeof raw === 'string' ? [raw] : []
        for (const value of candidates) {
          const match = value.match(/(?:^|,\s*)(ELDERLY_CARE_SESSION=[^;,\s]*)/)
          if (match) cookie = match[1] || ''
        }
        // #endif
        const body = response.data as ApiResponse<T>
        if (response.statusCode >= 200 && response.statusCode < 300 && body?.code === 'OK')
          resolve(body.data)
        else {
          if (response.statusCode === 401 || response.statusCode === 403) unauthorizedHandler()
          reject(
            new ApiError(
              response.statusCode,
              body?.message || '请求失败，请稍后重试',
              body?.code,
              body?.traceId,
            ),
          )
        }
      },
      fail() {
        reject(new ApiError(0, '网络连接失败，请检查网络后重试'))
      },
    })
  })
}
/** 每次写入读取当前会话CSRF；登录后轮换的Cookie和凭证不会复用旧值。 */
export async function request<T>(path: string, options: Options = {}): Promise<T> {
  if (options.method && options.method !== 'GET') {
    const csrf = await send<{ headerName: string; token: string }>('/auth/csrf')
    options = { ...options, headers: { ...options.headers, [csrf.headerName]: csrf.token } }
  }
  return send<T>(path, options)
}
