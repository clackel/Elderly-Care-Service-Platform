import type { CreateAttempt, ElderProfile, Gender, LivingArrangement } from '../../api/elders'

export const genderLabels: Record<Gender, string> = { MALE: '男', FEMALE: '女', UNKNOWN: '未说明' }
export const livingLabels: Record<LivingArrangement, string> = {
  ALONE: '独居',
  WITH_FAMILY: '与家人同住',
  INSTITUTION: '机构居住',
  OTHER: '其他',
  UNKNOWN: '未说明',
}
export const fieldLabels: Record<keyof ElderProfile | 'status', string> = {
  name: '姓名',
  gender: '性别',
  birthDate: '出生日期',
  phone: '联系电话',
  address: '服务地址',
  livingArrangement: '居住情况',
  emergencyContactName: '联系人姓名',
  emergencyContactPhone: '联系人电话',
  emergencyContactRelation: '与老人关系',
  remark: '备注',
  status: '档案状态',
}
export type FormErrors = Partial<Record<keyof ElderProfile, string>>
const phonePattern = /^(1[3-9][0-9]{9}|0[0-9]{2,3}-?[0-9]{7,8})$/

/** 创建独立空白表单，不默认填写老人身份或联系方式。 */
export function emptyProfile(): ElderProfile {
  return {
    name: '',
    gender: 'UNKNOWN',
    birthDate: '',
    phone: null,
    address: '',
    livingArrangement: 'UNKNOWN',
    emergencyContactName: '',
    emergencyContactPhone: '',
    emergencyContactRelation: '',
    remark: null,
  }
}

/** 规范化表单输入，空的可选项统一为null，不修改原始草稿。 */
export function normalizeProfile(value: ElderProfile): ElderProfile {
  return {
    name: value.name.trim(),
    gender: value.gender,
    birthDate: value.birthDate,
    phone: value.phone?.trim() || null,
    address: value.address.trim(),
    livingArrangement: value.livingArrangement,
    emergencyContactName: value.emergencyContactName.trim(),
    emergencyContactPhone: value.emergencyContactPhone.trim(),
    emergencyContactRelation: value.emergencyContactRelation.trim(),
    remark: value.remark?.trim() || null,
  }
}

/** 返回上海时区的日期，供出生日期上限和表单验证使用。 */
export function todayInShanghai(date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date)
  return ['year', 'month', 'day']
    .map((type) => parts.find((part) => part.type === type)?.value)
    .join('-')
}

/** 校验后端约定的必填项、日期和电话，返回可定位到字段的中文错误。 */
export function validateProfile(raw: ElderProfile, today = todayInShanghai()): FormErrors {
  const profile = normalizeProfile(raw)
  const errors: FormErrors = {}
  for (const [field, limit] of Object.entries({
    name: 50,
    address: 200,
    emergencyContactName: 50,
    emergencyContactRelation: 30,
  }) as [keyof ElderProfile, number][]) {
    const value = profile[field]
    if (!value) errors[field] = '请填写' + fieldLabels[field]
    else if (value.length > limit)
      errors[field] = fieldLabels[field] + '不能超过' + limit + '个字符'
  }
  const date = profile.birthDate
  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
    Number.isNaN(Date.parse(date)) ||
    new Date(date).toISOString().slice(0, 10) !== date ||
    date < '1900-01-01' ||
    date > today
  ) {
    errors.birthDate = '请选择1900年起至今天的有效日期'
  }
  if (!Object.hasOwn(genderLabels, profile.gender)) errors.gender = '请选择有效性别'
  if (!Object.hasOwn(livingLabels, profile.livingArrangement))
    errors.livingArrangement = '请选择有效居住情况'
  if (profile.phone && !phonePattern.test(profile.phone))
    errors.phone = '请输入有效手机号或含区号的固定电话'
  if (!phonePattern.test(profile.emergencyContactPhone))
    errors.emergencyContactPhone = '请输入有效的紧急联系人电话'
  if (profile.remark && profile.remark.length > 500) errors.remark = '备注不能超过500个字符'
  return errors
}

/** 原载荷重试复用UUID，明确修改资料后生成新请求；保留输入快照避免后续草稿污染。 */
export function prepareCreate(
  profile: ElderProfile,
  previous: CreateAttempt | null,
  makeId: () => string = () => crypto.randomUUID(),
): CreateAttempt {
  const normalized = normalizeProfile(profile)
  if (previous && JSON.stringify(previous.profile) === JSON.stringify(normalized)) return previous
  return { requestId: makeId(), profile: { ...normalized } }
}

/** 格式化后端时间戳，缺失或非法值显示占位符。 */
export function formatTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

/** 将请求失败转为中文提示，保留客服排查所需的追踪编号。 */
export function errorText(error: unknown): string {
  if (!(error instanceof Error)) return '操作失败，请稍后重试'
  const trace = 'traceId' in error && typeof error.traceId === 'string' ? error.traceId : ''
  return error.message + (trace ? '（参考编号：' + trace + '）' : '')
}
