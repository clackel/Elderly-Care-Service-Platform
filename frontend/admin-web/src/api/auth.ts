import { request } from './http'
import type { AuthCapabilities, Community, CurrentAccount } from './types'

export const authApi = {
  capabilities: () => request<AuthCapabilities>('/auth/capabilities'),
  me: () => request<CurrentAccount>('/auth/me'),
  login: (username: string, password: string) =>
    request<CurrentAccount>('/auth/admin/login', {
      method: 'POST',
      body: new URLSearchParams({ username, password }),
    }),
  logout: () => request<null>('/auth/logout', { method: 'POST' }),
  /** 本人修改密码；成功后服务端撤销所有会话，调用方必须重新登录。 */
  changePassword: (input: { requestId: string; currentPassword: string; newPassword: string }) =>
    request<null>('/auth/password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    }),
  community: () => request<Community>('/system/community'),
}
