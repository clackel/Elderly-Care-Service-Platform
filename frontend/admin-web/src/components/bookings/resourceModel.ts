import {
  choices,
  categoryLabels,
  localTime,
  time,
  type CatalogResource,
  type Catalog,
  type Provider,
  type Worker,
  type Member,
  type ElderOption,
  type Field,
  type FormValues,
} from '../../api/bookings'
export type ResourceKind = 'catalog' | 'providers' | 'workers' | 'members' | 'bindings' | 'grants'
export const resourcePaths: Record<ResourceKind, string> = {
  catalog: '/services',
  providers: '/service-providers',
  workers: '/service-workers',
  members: '/booking-access/members',
  bindings: '/booking-access/bindings',
  grants: '/booking-access/grants',
}
export const resourceLabels: Record<ResourceKind, string> = {
  catalog: '服务项目',
  providers: '服务提供方',
  workers: '服务人员',
  members: '移动账号',
  bindings: '本人绑定',
  grants: '家属预约授权',
}
export interface Lookups {
  providers: Provider[]
  members: Member[]
  elders: ElderOption[]
}
const yesNo = choices({ true: '启用', false: '停用' })
/** 按资源职责建立表单，所有可选对象来自本社区接口。 */
export function resourceFields(kind: ResourceKind, lookup: Lookups, editing: boolean): Field[] {
  const providers = lookup.providers.map((p) => ({
    value: p.id,
    label: p.name + (p.enabled ? '' : '（已停用）'),
  }))
  const accounts = (role: string) =>
    lookup.members
      .filter((m) => m.role === role && m.status === 'ACTIVE')
      .map((m) => ({ value: m.id, label: m.displayName + ' · ' + m.id }))
  const elders = lookup.elders.map((e) => ({ value: e.id, label: e.name + ' · ' + e.id }))
  const enabled: Field = {
    key: 'enabled',
    label: '可用状态',
    type: 'select',
    required: true,
    options: yesNo,
  }
  if (kind === 'providers')
    return [{ key: 'name', label: '提供方名称', required: true, max: 100 }, enabled]
  if (kind === 'catalog')
    return [
      {
        key: 'category',
        label: '服务分类',
        type: 'select',
        required: true,
        options: choices(categoryLabels),
      },
      { key: 'name', label: '服务名称', required: true, max: 100 },
      { key: 'description', label: '服务内容说明', type: 'textarea', required: true, max: 1000 },
      {
        key: 'providerId',
        label: '服务提供方',
        type: 'select',
        required: true,
        options: providers,
      },
      { key: 'serviceArea', label: '服务范围（人员须与此完全一致）', required: true, max: 80 },
      {
        key: 'durationMinutes',
        label: '服务时长（分钟）',
        type: 'number',
        required: true,
        min: 15,
        max: 480,
      },
      {
        key: 'priceFen',
        label: '参考价格（分，100分=1元）',
        type: 'number',
        required: true,
        min: 0,
        max: 10000000,
      },
      {
        key: 'professional',
        label: '服务性质',
        type: 'select',
        required: true,
        options: choices({ false: '生活服务', true: '专业护理 / 康复' }),
      },
      { key: 'qualification', label: '专业服务所需资质名称', max: 100 },
      { ...enabled, label: '上架状态', options: choices({ true: '上架', false: '下架' }) },
    ]
  if (kind === 'workers')
    return [
      ...(!editing
        ? [
            {
              key: 'id',
              label: '已核验工作人员账号',
              type: 'select',
              required: true,
              options: accounts('STAFF'),
            } as Field,
          ]
        : []),
      {
        key: 'providerId',
        label: '所属提供方',
        type: 'select',
        required: true,
        options: providers,
      },
      {
        key: 'categories',
        label: '可服务类别（可多选）',
        type: 'multi',
        required: true,
        options: choices(categoryLabels),
      },
      { key: 'serviceArea', label: '服务范围', required: true, max: 80 },
      { key: 'qualification', label: '已核验资质名称', max: 100 },
      {
        key: 'qualificationExpiresAt',
        label: '资质有效期（有资质时必填）',
        type: 'datetime-local',
      },
      enabled,
    ]
  if (kind === 'members')
    return [
      { key: 'displayName', label: '显示名称', required: true, max: 50 },
      {
        key: 'role',
        label: '已核验身份',
        type: 'select',
        required: true,
        options: choices({ ELDER: '老人', FAMILY: '家属', STAFF: '服务人员' }),
      },
      {
        key: 'verificationReference',
        label: '身份核验依据编号（不填身份证号）',
        required: true,
        max: 200,
      },
    ]
  if (kind === 'bindings')
    return [
      ...(!editing
        ? ([
            {
              key: 'accountId',
              label: '老人账号',
              type: 'select',
              required: true,
              options: accounts('ELDER'),
            },
            {
              key: 'elderId',
              label: '已核验对应档案',
              type: 'select',
              required: true,
              options: elders,
            },
          ] as Field[])
        : []),
      { key: 'active', label: '本人绑定状态', type: 'select', required: true, options: yesNo },
      { key: 'verificationReference', label: '核验 / 撤销依据编号', required: true, max: 200 },
    ]
  return [
    ...(!editing
      ? ([
          { key: 'elderId', label: '授权老人', type: 'select', required: true, options: elders },
          {
            key: 'familyId',
            label: '被授权家属',
            type: 'select',
            required: true,
            options: accounts('FAMILY'),
          },
        ] as Field[])
      : []),
    {
      key: 'canBook',
      label: '授权范围',
      type: 'select',
      required: true,
      options: choices({ false: '查看预约进度', true: '查看预约及代预约、取消' }),
    },
    { key: 'expiresAt', label: '授权截止时间（一年以内）', type: 'datetime-local', required: true },
    { key: 'consentReference', label: '老人明确同意的核验依据编号', required: true, max: 200 },
  ]
}
/** 将读取结果复制成独立草稿，不把核验依据写入URL或本地存储。 */
export function resourceDraft(row: CatalogResource | null): FormValues {
  const values: FormValues = {
    enabled: 'true',
    active: 'true',
    professional: 'false',
    version: '0',
    durationMinutes: '60',
    priceFen: '0',
    canBook: 'false',
    qualification: '',
  }
  if (row)
    for (const [key, value] of Object.entries(row)) {
      if (key.endsWith('At') && typeof value === 'string') values[key] = localTime(value)
      else values[key] = Array.isArray(value) ? value.join(',') : value == null ? '' : String(value)
    }
  return values
}
/** 生成与后端DTO一致的结构化写入内容。 */
export function resourceBody(kind: ResourceKind, v: FormValues, requestId: string) {
  const version = Number(v.version || 0)
  if (kind === 'providers')
    return { requestId, version, name: v.name?.trim(), enabled: v.enabled === 'true' }
  if (kind === 'catalog')
    return {
      requestId,
      version,
      category: v.category,
      name: v.name?.trim(),
      description: v.description?.trim(),
      providerId: v.providerId,
      serviceArea: v.serviceArea?.trim(),
      durationMinutes: Number(v.durationMinutes),
      priceFen: Number(v.priceFen),
      professional: v.professional === 'true',
      qualification: v.professional === 'true' ? v.qualification?.trim() || '' : '',
      enabled: v.enabled === 'true',
    }
  if (kind === 'workers')
    return {
      version,
      providerId: v.providerId,
      categories: (v.categories || '').split(',').filter(Boolean),
      serviceArea: v.serviceArea?.trim(),
      qualification: v.qualification?.trim() || '',
      qualificationExpiresAt: v.qualificationExpiresAt
        ? new Date(v.qualificationExpiresAt).toISOString()
        : null,
      enabled: v.enabled === 'true',
    }
  if (kind === 'members')
    return {
      requestId,
      displayName: v.displayName?.trim(),
      role: v.role,
      verificationReference: v.verificationReference?.trim(),
    }
  if (kind === 'bindings')
    return {
      version,
      accountId: v.accountId,
      elderId: v.elderId,
      active: v.active === 'true',
      verificationReference: v.verificationReference?.trim(),
    }
  return {
    version,
    elderId: v.elderId,
    familyId: v.familyId,
    canBook: v.canBook === 'true',
    expiresAt: new Date(v.expiresAt || '').toISOString(),
    consentReference: v.consentReference?.trim(),
  }
}
/** 为各类资源生成简洁列表摘要，显示中文业务状态。 */
export function resourceSummary(kind: ResourceKind, row: CatalogResource) {
  if (kind === 'catalog') {
    const r = row as Catalog
    return {
      title: r.name,
      detail: categoryLabels[r.category] + ' · ' + r.providerName + ' · ' + r.serviceArea,
      status: r.enabled ? '已上架' : '已下架',
    }
  }
  if (kind === 'providers') {
    const r = row as Provider
    return { title: r.name, detail: '服务提供方', status: r.enabled ? '启用' : '停用' }
  }
  if (kind === 'workers') {
    const r = row as Worker
    return {
      title: r.name,
      detail:
        r.categories.map((c) => categoryLabels[c]).join('、') +
        ' · ' +
        r.serviceArea +
        ' · ' +
        (r.qualification || '生活服务'),
      status: r.enabled ? '可安排' : '停用',
    }
  }
  if ('role' in row)
    return {
      title: row.displayName,
      detail: { ELDER: '老人', FAMILY: '家属', STAFF: '服务人员' }[row.role],
      status: row.wechatLinked ? '已绑定微信' : '待开通微信',
    }
  if ('active' in row)
    return {
      title: '老人档案 ' + row.elderId,
      detail: '账号 ' + row.accountId,
      status: row.active ? '绑定有效' : '绑定已停用',
    }
  if ('revoked' in row)
    return {
      title: '老人档案 ' + row.elderId,
      detail:
        '家属 ' +
        row.familyId +
        ' · ' +
        (row.canBook ? '查看及代预约' : '仅查看') +
        ' · 到期 ' +
        time(row.expiresAt),
      status: row.revoked
        ? '已撤销'
        : new Date(row.expiresAt).getTime() <= Date.now()
          ? '已过期'
          : '有效',
    }
  return { title: row.id, detail: '', status: '' }
}
