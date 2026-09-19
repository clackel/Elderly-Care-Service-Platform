import { computed, onBeforeUnmount, onMounted, reactive, shallowRef } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message-box/style/css'
import { ApiError } from '../../api/http'
import {
  createElder,
  getElder,
  listElders,
  transitionElder,
  updateElder,
  type CreateAttempt,
  type ElderDetail,
  type ElderProfile,
  type ElderQuery,
  type ElderSummary,
} from '../../api/elders'
import { emptyProfile, errorText, normalizeProfile, prepareCreate } from './elderModel'

interface EditorState {
  key: number
  detail: ElderDetail | null
  profile: ElderProfile
}

/** 管理档案读取、编辑和状态变更；草稿仅驻留内存，过期请求不更新界面。 */
export function useElderRecords() {
  const query = reactive<ElderQuery>({ page: 1, pageSize: 20, keyword: '', status: 'ACTIVE' })
  const rows = shallowRef<ElderSummary[]>([])
  const total = shallowRef(0)
  const loading = shallowRef(false)
  const listError = shallowRef('')
  const detail = shallowRef<ElderDetail | null>(null)
  const detailOpen = shallowRef(false)
  const detailLoading = shallowRef(false)
  const detailError = shallowRef('')
  const selectedId = shallowRef('')
  const editor = shallowRef<EditorState | null>(null)
  const dirty = shallowRef(false)
  const saving = shallowRef(false)
  const formError = shallowRef('')
  const conflict = shallowRef(false)
  const uncertain = shallowRef(false)
  const transitioning = shallowRef(false)
  const editorReloading = shallowRef(false)
  const pages = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)))
  let attempt: CreateAttempt | null = null
  let alive = true
  let listRead: AbortController | undefined
  let detailRead: AbortController | undefined
  let editorKey = 0

  /** 读取当前筛选页；先清除旧行，归档后自动退回仍存在的最后一页。 */
  async function loadList() {
    listRead?.abort()
    const controller = new AbortController()
    listRead = controller
    rows.value = []
    loading.value = true
    listError.value = ''
    try {
      const result = await listElders({ ...query }, controller.signal)
      if (!alive || controller.signal.aborted) return
      total.value = result.total
      if (query.page > pages.value) {
        query.page = pages.value
        await loadList()
        return
      }
      rows.value = result.items
    } catch (error) {
      if (alive && !controller.signal.aborted) listError.value = errorText(error)
    } finally {
      if (alive && listRead === controller) loading.value = false
    }
  }

  /** 应用完整姓名或电话及状态过滤，每次搜索从第一页开始。 */
  function search(filters: Pick<ElderQuery, 'keyword' | 'status'>) {
    Object.assign(query, filters, { page: 1 })
    void loadList()
  }

  /** 切换合法页码或每页条数，修改条数时重新从第一页读取。 */
  function paginate(page: number, pageSize = query.pageSize) {
    query.page = pageSize === query.pageSize ? Math.max(1, Math.min(page, pages.value)) : 1
    query.pageSize = pageSize
    void loadList()
  }

  /** 确认丢弃草稿；待确认的建档会提示核对列表，避免误认为请求未落库。 */
  async function confirmDiscard(): Promise<boolean> {
    if (saving.value || editorReloading.value) return false
    if (!dirty.value && !uncertain.value) return true
    try {
      await ElMessageBox.confirm(
        uncertain.value
          ? '上次建档结果尚未确认。关闭后请先查询档案，避免重复建档。确定关闭吗？'
          : '尚有未保存的修改，确定放弃这些修改吗？',
        '确认关闭',
        { confirmButtonText: '放弃并关闭', cancelButtonText: '继续编辑', type: 'warning' },
      )
      return alive
    } catch {
      return false
    }
  }

  /** 清理表单及请求快照，在保存成功或用户明确放弃时调用。 */
  function resetEditor() {
    editor.value = null
    dirty.value = false
    formError.value = ''
    conflict.value = false
    uncertain.value = false
    attempt = null
  }

  /** 用户主动关闭编辑器时先检查未保存修改。 */
  async function closeEditor() {
    if (await confirmDiscard()) {
      resetEditor()
      void loadList()
    }
  }

  /** 打开独立建档表单，生成请求编号的时机延后至首次有效提交。 */
  function startCreate() {
    detailRead?.abort()
    detailLoading.value = false
    detailOpen.value = false
    detail.value = null
    resetEditor()
    editor.value = { key: ++editorKey, detail: null, profile: emptyProfile() }
  }

  /** 装载编辑草稿；归档资料只能查看，恢复后方可编辑。 */
  function editDetail(value: ElderDetail) {
    if (value.status !== 'ACTIVE') return
    detailOpen.value = false
    resetEditor()
    editor.value = { key: ++editorKey, detail: value, profile: { ...value.profile } }
  }

  /** 读取对象最新版本后打开详情或编辑；新的选择会取消旧对象的读取。 */
  async function openDetail(id: string, edit = false) {
    detailRead?.abort()
    const controller = new AbortController()
    detailRead = controller
    selectedId.value = id
    detail.value = null
    detailError.value = ''
    detailOpen.value = true
    detailLoading.value = true
    try {
      const value = await getElder(id, controller.signal)
      if (!alive || controller.signal.aborted) return
      detail.value = value
      if (edit && value.status === 'ACTIVE') editDetail(value)
    } catch (error) {
      if (alive && !controller.signal.aborted) detailError.value = errorText(error)
    } finally {
      if (alive && detailRead === controller) detailLoading.value = false
    }
  }

  /** 关闭详情时立即释放明文，并取消尚未完成的详情请求。 */
  function closeDetail() {
    if (transitioning.value) return
    detailRead?.abort()
    detailOpen.value = false
    detailLoading.value = false
    detail.value = null
    detailError.value = ''
    selectedId.value = ''
  }

  /** 保存完整资料；建档结果不确定时仅允许原载荷原编号重试。 */
  async function save(profile: ElderProfile) {
    if (!editor.value || saving.value || conflict.value || editorReloading.value) return
    const state = editor.value
    saving.value = true
    formError.value = ''
    try {
      let saved: ElderDetail
      if (state.detail) {
        saved = await updateElder(state.detail.id, state.detail.version, normalizeProfile(profile))
      } else {
        attempt = uncertain.value && attempt ? attempt : prepareCreate(profile, attempt)
        saved = await createElder(attempt)
      }
      if (!alive) return
      resetEditor()
      detail.value = saved
      selectedId.value = saved.id
      detailError.value = ''
      detailOpen.value = true
      ElMessage.success(state.detail ? '档案已保存' : '建档成功')
      void loadList()
    } catch (error) {
      if (!alive) return
      formError.value = errorText(error)
      conflict.value =
        error instanceof ApiError &&
        ['ELDER_VERSION_CONFLICT', 'ELDER_ARCHIVED', 'INVALID_ELDER_STATE'].includes(
          error.code ?? '',
        )
      // 请求超时、5xx或无法解析成功响应均可能发生在事务提交后，保留建档载荷。
      uncertain.value =
        uncertain.value ||
        (!state.detail &&
          (!(error instanceof ApiError) ||
            error.status === 0 ||
            error.status >= 500 ||
            error.status === 200))
    } finally {
      if (alive) saving.value = false
    }
  }

  /** 冲突后由用户确认丢弃草稿，再读取最新版本；不自动覆盖其他人的更改。 */
  async function reloadEditor() {
    const id = editor.value?.detail?.id
    if (!id || !(await confirmDiscard())) return
    editorReloading.value = true
    try {
      const latest = await getElder(id)
      if (!alive) return
      if (latest.status === 'ACTIVE') editDetail(latest)
      else {
        resetEditor()
        detail.value = latest
        selectedId.value = latest.id
        detailOpen.value = true
      }
      void loadList()
    } catch (error) {
      if (alive) formError.value = errorText(error)
    } finally {
      if (alive) editorReloading.value = false
    }
  }

  /** 确认后按显示版本归档或恢复；发生冲突时保留提示并要求重新读取。 */
  async function changeStatus() {
    if (!detail.value || transitioning.value || detailLoading.value || detailError.value) return
    const current = detail.value
    const archive = current.status === 'ACTIVE'
    transitioning.value = true
    try {
      await ElMessageBox.confirm(
        archive
          ? '归档后保留资料及变更记录，暂停编辑；需要时可以恢复。确定归档吗？'
          : '恢复后，该档案将重新进入在册列表并允许编辑。',
        archive ? '归档确认' : '恢复确认',
        {
          confirmButtonText: archive ? '确认归档' : '确认恢复',
          cancelButtonText: '取消',
          type: 'warning',
        },
      )
      if (!alive) return
      detail.value = await transitionElder(
        current.id,
        current.version,
        archive ? 'archive' : 'restore',
      )
      if (!alive) {
        detail.value = null
        return
      }
      ElMessage.success(archive ? '档案已归档' : '档案已恢复')
      void loadList()
    } catch (error) {
      if (alive && error !== 'cancel' && error !== 'close') detailError.value = errorText(error)
    } finally {
      if (alive) transitioning.value = false
    }
  }

  onMounted(loadList)
  onBeforeUnmount(() => {
    alive = false
    listRead?.abort()
    detailRead?.abort()
    ElMessageBox.close()
    rows.value = []
    detail.value = null
    resetEditor()
  })

  return {
    query,
    rows,
    total,
    pages,
    loading,
    listError,
    detail,
    detailOpen,
    detailLoading,
    detailError,
    selectedId,
    editor,
    dirty,
    saving,
    formError,
    conflict,
    uncertain,
    transitioning,
    editorReloading,
    loadList,
    search,
    paginate,
    startCreate,
    closeEditor,
    confirmDiscard,
    editDetail,
    openDetail,
    closeDetail,
    save,
    reloadEditor,
    changeStatus,
  }
}
