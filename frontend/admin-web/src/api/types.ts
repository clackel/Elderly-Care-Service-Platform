export type Role =
  | 'ELDER'
  | 'FAMILY'
  | 'STAFF'
  | 'COMMUNITY_OPERATOR'
  | 'DUTY_OFFICER'
  | 'PLATFORM_ADMIN'
  | 'AUDITOR'

export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId: string
}

export interface CurrentAccount {
  id: string
  displayName: string
  role: Role
  communityId: string | null
  passwordChangeRequired: boolean
}

export interface Community {
  id: string
  name: string
}

export interface AuthCapabilities {
  adminLogin: boolean
  developmentLogin: boolean
  wechatLogin: boolean
}
