import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { request, ApiError } from '../../api/http'
import {
  write,
  options,
  elderOptions,
  type Page,
  type CatalogResource,
  type Provider,
  type Member,
  type ElderOption,
  type FormValues,
} from '../../api/bookings'
import { resourcePaths, resourceFields, resourceBody, type ResourceKind } from './resourceModel'
/** 隔离目录、人员、绑定和授权的列表与编辑状态，保留创建请求编号。 */
export function useResources(initial: ResourceKind) {
  const kind = ref(initial),
    rows = ref<CatalogResource[]>([]),
    page = ref(1),
    total = ref(0),
    busy = ref(false),
    error = ref('')
  const editor = ref<{ key: string; row: CatalogResource | null } | null>(null),
    uncertain = ref(false)
  const providers = ref<Provider[]>([]),
    members = ref<Member[]>([]),
    elders = ref<ElderOption[]>([])
  const enrollment = ref<{ accountId: string; token: string; expiresAt: string } | null>(null)
  let read: AbortController | null = null,
    alive = true,
    pending: { path: string; method: string; body: unknown } | null = null
  const fields = computed(() =>
    resourceFields(
      kind.value,
      { providers: providers.value, members: members.value, elders: elders.value },
      !!editor.value?.row,
    ),
  )
  /** 显示可定位的中文失败信息。 */
  function message(e: unknown) {
    return e instanceof ApiError
      ? e.message + (e.traceId ? '（追踪编号：' + e.traceId + '）' : '')
      : e instanceof Error
        ? e.message
        : '操作失败，请重试'
  }
  /** 查询当前资源和当前社区选项，旧读取不得覆盖新标签。 */
  async function load() {
    read?.abort()
    const controller = new AbortController()
    read = controller
    error.value = ''
    try {
      const data = await request<Page<CatalogResource>>(
        resourcePaths[kind.value] + '?page=' + page.value + '&pageSize=10',
        { signal: controller.signal },
      )
      if (!controller.signal.aborted && alive) {
        rows.value = data.items
        total.value = data.total
      }
    } catch (e) {
      if (!controller.signal.aborted && alive) error.value = message(e)
    }
  }
  /** 编辑前只读取该表单需要的社区对象，避免目录维护读取无关老人资料。 */
  async function edit(row: CatalogResource | null) {
    if (busy.value) return
    busy.value = true
    error.value = ''
    enrollment.value = null
    try {
      const [p, m, e] = await Promise.all([
        ['catalog', 'workers'].includes(kind.value)
          ? options<Provider>('/service-providers')
          : Promise.resolve([] as Provider[]),
        ['workers', 'bindings', 'grants'].includes(kind.value)
          ? options<Member>('/booking-access/members')
          : Promise.resolve([] as Member[]),
        ['bindings', 'grants'].includes(kind.value)
          ? elderOptions()
          : Promise.resolve([] as ElderOption[]),
      ])
      if (alive) {
        providers.value = p
        members.value = m
        elders.value = e
        editor.value = { key: crypto.randomUUID(), row }
        pending = null
        uncertain.value = false
      }
    } catch (e) {
      if (alive) error.value = message(e)
    } finally {
      if (alive) busy.value = false
    }
  }
  /** 标签切换前确认丢弃草稿，不在标签之间复用敏感表单。 */
  function changeKind(value: ResourceKind) {
    if (busy.value || (editor.value && !window.confirm('切换将清除当前草稿，确定继续吗？'))) return
    kind.value = value
    page.value = 1
    editor.value = null
    enrollment.value = null
    rows.value = []
    void load()
  }
  /** 创建保持固定编号，编辑携带版本；冲突后要求重新打开最新记录。 */
  async function save(values: FormValues) {
    if (busy.value || !editor.value) return
    busy.value = true
    error.value = ''
    try {
      if (!uncertain.value || !pending) {
        const row = editor.value.row,
          id = row?.id || (kind.value === 'workers' ? values.id : '')
        const update = ['catalog', 'providers', 'workers'].includes(kind.value) && !!id
        pending = {
          path: resourcePaths[kind.value] + (update ? '/' + id : ''),
          method: update ? 'PUT' : 'POST',
          body: resourceBody(kind.value, values, editor.value.key),
        }
      }
      await write(pending.path, pending.body, pending.method)
      if (alive) {
        editor.value = null
        pending = null
        uncertain.value = false
        await load()
      }
    } catch (e) {
      if (alive) {
        error.value = message(e)
        uncertain.value = !(e instanceof ApiError) || e.status === 0 || e.status >= 500
        if (e instanceof ApiError && e.status === 409) {
          editor.value = null
          const text = error.value
          await load()
          error.value = text + '。请从最新列表重新打开编辑。'
        }
      }
    } finally {
      if (alive) busy.value = false
    }
  }
  /** 签发新码会使旧码失效；原码只在当前页面显示。 */
  async function issue(id: string) {
    if (
      busy.value ||
      !window.confirm('签发新的30分钟开通码将废止旧码。请仅交给已核验的本人。继续吗？')
    )
      return
    busy.value = true
    error.value = ''
    enrollment.value = null
    try {
      const value = await write<{ accountId: string; token: string; expiresAt: string }>(
        '/booking-access/members/' + id + '/enrollment',
        {},
      )
      if (alive) enrollment.value = value
    } catch (e) {
      if (alive) error.value = message(e)
    } finally {
      if (alive) busy.value = false
    }
  }
  /** 按最新版本撤销授权，失败不会显示撤销成功。 */
  async function revoke(row: CatalogResource) {
    if (
      busy.value ||
      !('revoked' in row) ||
      !window.confirm('撤销后该家属将立即失去预约查看及代办权限，确定撤销吗？')
    )
      return
    busy.value = true
    error.value = ''
    try {
      await write('/booking-access/grants/' + row.id + '/revoke', { version: row.version })
      if (alive) await load()
    } catch (e) {
      if (alive) {
        const text = message(e)
        await load()
        error.value = text
      }
    } finally {
      if (alive) busy.value = false
    }
  }
  onMounted(load)
  onBeforeUnmount(() => {
    alive = false
    read?.abort()
    rows.value = []
    elders.value = []
    members.value = []
    editor.value = null
    enrollment.value = null
  })
  return {
    kind,
    rows,
    page,
    total,
    busy,
    error,
    editor,
    uncertain,
    enrollment,
    fields,
    load,
    edit,
    changeKind,
    save,
    issue,
    revoke,
  }
}
