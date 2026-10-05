import type { AccountMutation, AccountStatus, AdminAccount, AdminRole } from '../../api/adminAccounts'

export const adminRoleLabels: Record<AdminRole, string> = {
  COMMUNITY_OPERATOR: '社区运营',
  DUTY_OFFICER: '值守人员',
  PLATFORM_ADMIN: '平台管理员',
  AUDITOR: '审计人员',
}
export type EditorMode = 'create' | 'update' | 'reset'
export interface AccountDraft {
  username: string
  displayName: string
  role: AdminRole
  status: AccountStatus
  password: string
  confirmation: string
}
export type AccountErrors = Partial<Record<keyof AccountDraft, string>>

/** 按当前账号快照创建内存草稿，密码字段永远为空。 */
export function newAccountDraft(account: AdminAccount | null): AccountDraft {
  return {
    username: account?.username ?? '',
    displayName: account?.displayName ?? '',
    role: account?.role ?? 'COMMUNITY_OPERATOR',
    status: account?.status ?? 'ACTIVE',
    password: '',
    confirmation: '',
  }
}

/** 校验密码字符长度及编码长度，避免中文密码超出服务端支持范围。 */
export function passwordError(password: string): string {
  if (!password.trim()) return '密码不能全部为空白字符'
  if (password.length < 12 || password.length > 64) return '密码需为12至64个字符'
  if (new TextEncoder().encode(password).length > 72) return '密码过长，请减少中文或特殊字符后重试'
  return ''
}

/** 按操作类型校验账号表单；界面约束不代替服务端权限验证。 */
export function validateAccountDraft(draft: AccountDraft, mode: EditorMode): AccountErrors {
  const errors: AccountErrors = {}
  if (mode === 'create' && !/^[a-zA-Z0-9][a-zA-Z0-9._-]{2,63}$/.test(draft.username.trim())) {
    errors.username = '账号需为3至64位字母、数字、点、下划线或连字符，并以字母或数字开头'
  }
  if (mode !== 'reset') {
    if (!draft.displayName.trim() || draft.displayName.trim().length > 100) errors.displayName = '显示名需为1至100个字符'
    if (!Object.hasOwn(adminRoleLabels, draft.role)) errors.role = '请选择有效的后台角色'
    if (!['ACTIVE', 'DISABLED'].includes(draft.status)) errors.status = '请选择有效的账号状态'
  }
  if (mode !== 'update') {
    const message = passwordError(draft.password)
    if (message) errors.password = message
    if (draft.password !== draft.confirmation) errors.confirmation = '两次输入的密码不一致'
  }
  return errors
}

/** 为一次明确的操作生成独立载荷，后续未知结果重试直接复用该快照。 */
export function accountMutation(
  draft: AccountDraft,
  mode: EditorMode,
  account: AdminAccount | null,
  requestId: string,
): AccountMutation {
  if (mode === 'create') return {
    kind: 'create', requestId, username: draft.username.trim().toLowerCase(),
    displayName: draft.displayName.trim(), role: draft.role, password: draft.password,
  }
  if (!account) throw new Error('账号信息已失效，请关闭表单后重新选择')
  if (mode === 'reset') return { kind: 'reset', requestId, id: account.id, version: account.version, password: draft.password }
  return {
    kind: 'update', requestId, id: account.id, version: account.version,
    displayName: draft.displayName.trim(), role: draft.role, status: draft.status,
  }
}

/** 判断本次状态或角色修改是否撤销社区运营人员的健康协助授权。 */
export function revokesHealthGrants(account: AdminAccount | null, draft: AccountDraft): boolean {
  return account?.role === 'COMMUNITY_OPERATOR' &&
    (draft.role !== 'COMMUNITY_OPERATOR' || draft.status !== 'ACTIVE')
}

/** 判断已发出的写入是否可能完成，以决定是否锁定载荷等待原请求重试。 */
export function isUncertainAccountWrite(status: number): boolean {
  return status === 0 || status >= 500 || (status >= 200 && status < 300)
}
