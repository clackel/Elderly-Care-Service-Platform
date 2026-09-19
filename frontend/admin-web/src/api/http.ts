import type { ApiResponse } from './types'

export class ApiError extends Error {
  /** 保留中文提示、HTTP状态和稳定错误码，供页面区分冲突及可重试故障。 */
  constructor(
    public status: number,
    message: string,
    public traceId?: string,
    public code?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

type CsrfToken = { headerName: string; token: string }

/** 只在内存保存 CSRF 凭证，会话 Cookie 由浏览器管理，禁止写入本地存储。 */
export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  if (options.method && !['GET', 'HEAD', 'OPTIONS'].includes(options.method.toUpperCase())) {
    // 登录和注销会轮换 CSRF 凭证，因此每次写请求获取当前会话对应的凭证。
    const csrf = await request<CsrfToken>('/auth/csrf')
    headers.set(csrf.headerName, csrf.token)
  }
  let response: Response
  try {
    response = await fetch('/api/v1' + path, {
      ...options,
      headers,
      credentials: 'same-origin',
      signal: options.signal
        ? AbortSignal.any([options.signal, AbortSignal.timeout(15000)])
        : AbortSignal.timeout(15000),
    })
  } catch {
    throw new ApiError(0, '无法连接服务器，请检查网络后重试')
  }
  let body: ApiResponse<T>
  try {
    body = (await response.json()) as ApiResponse<T>
  } catch {
    throw new ApiError(response.status, '服务器响应异常，请稍后重试')
  }
  if (!response.ok || body.code !== 'OK') {
    if (response.status === 401 || response.status === 403) {
      window.dispatchEvent(new CustomEvent('session-invalidated', { detail: response.status }))
    }
    throw new ApiError(response.status, body.message || '请求失败', body.traceId, body.code)
  }
  return body.data
}
