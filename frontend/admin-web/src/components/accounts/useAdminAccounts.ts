import { computed, onBeforeUnmount, onMounted, reactive, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import { listAdminAccounts, mutateAdminAccount, type AccountMutation, type AccountQuery, type AdminAccount } from '../../api/adminAccounts'
import { ApiError } from '../../api/http'
import { useSessionStore } from '../../stores/session'
import { errorText } from '../elders/elderModel'
import { accountMutation, isUncertainAccountWrite, revokesHealthGrants, type AccountDraft, type EditorMode } from './accountModel'

/** 管理本社区账号读取、短期写入快照及版本冲突，离开页面时清除所有内存资料。 */
export function useAdminAccounts() {
  const session = useSessionStore()
  const query = reactive<AccountQuery>({ page: 1, pageSize: 20, keyword: '', role: '', status: '' })
  const rows = shallowRef<AdminAccount[]>([])
  const total = shallowRef(0)
  const loading = shallowRef(false)
  const listError = shallowRef('')
  const editor = shallowRef<{ key: string; mode: EditorMode; account: AdminAccount | null } | null>(null)
  const history = shallowRef<AdminAccount | null>(null)
  const saving = shallowRef(false)
  const dirty = shallowRef(false)
  const formError = shallowRef('')
  const uncertain = shallowRef(false)
  const conflict = shallowRef(false)
  const pages = computed(() => Math.max(1, Math.ceil(total.value / query.pageSize)))
  let pending: AccountMutation | null = null
  let listController: AbortController | null = null
  let disposed = false

  /** 取消旧分页请求，只有最近一次成功响应可更新当前列表。 */
  async function loadList() {
    listController?.abort()
    const controller = new AbortController()
    listController = controller
    loading.value = true
    listError.value = ''
    rows.value = []
    try {
      const result = await listAdminAccounts({ ...query }, controller.signal)
      if (disposed || controller.signal.aborted) return
      rows.value = result.items
      total.value = result.total
    } catch (cause) {
      if (!disposed && !controller.signal.aborted) listError.value = errorText(cause)
    } finally {
      if (!disposed && !controller.signal.aborted) loading.value = false
    }
  }

  /** 应用筛选条件并回到第一页，不把账号搜索内容写入地址或本地存储。 */
  function search(filters: Pick<AccountQuery, 'keyword' | 'role' | 'status'>) {
    Object.assign(query, filters, { page: 1 })
    void loadList()
  }

  /** 在合法页码范围内读取下一页账号。 */
  function paginate(page: number) {
    if (loading.value || page < 1 || page > pages.value) return
    query.page = page
    void loadList()
  }

  /** 创建固定版本的编辑会话；本人的密码只通过当前密码验证流程修改。 */
  function openEditor(mode: EditorMode, account: AdminAccount | null = null) {
    if (saving.value) return
    if (mode === 'reset' && account?.id === session.account?.id) {
      ElMessage.info('请使用“修改本人密码”入口修改自己的密码')
      return
    }
    editor.value = { key: crypto.randomUUID(), mode, account: account ? { ...account } : null }
    pending = null
    dirty.value = false
    formError.value = ''
    uncertain.value = false
    conflict.value = false
  }

  /** 写入进行中禁止离开；未知结果需提示先核实，明确放弃时不保留密码。 */
  function confirmDiscard(): boolean {
    if (saving.value) return false
    if (uncertain.value) return window.confirm('操作结果尚未确认。关闭会清除密码和本次重试信息，请重新查询账号状态后再操作。仍要关闭吗？')
    return !dirty.value || window.confirm('尚有未保存内容，关闭后会清除。确定关闭吗？')
  }

  /** 明确关闭表单后清除密码载荷；版本冲突要求从列表重新选取最新账号。 */
  function closeEditor() {
    if (!confirmDiscard()) return
    const refresh = uncertain.value || conflict.value
    editor.value = null
    pending = null
    dirty.value = false
    uncertain.value = false
    conflict.value = false
    if (refresh) void loadList()
  }

  /** 提交或重试固定载荷；运营授权撤销需明确确认，版本冲突不自动覆盖。 */
  async function save(draft: AccountDraft) {
    if (saving.value || !editor.value || conflict.value) return
    const current = editor.value
    if (!pending) {
      let prompt = ''
      if (current.mode === 'reset') prompt = '重置后该账号的所有已登录会话失效，下次登录必须修改密码。确定重置吗？'
      else if (current.mode === 'update' && revokesHealthGrants(current.account, draft)) {
        prompt = '本次修改将撤销该运营账号的健康协助授权并使已登录会话失效。以后恢复该角色也需要老人重新授权。确定保存吗？'
      } else if (current.mode === 'update' && current.account &&
        (draft.role !== current.account.role || draft.status !== current.account.status)) {
        prompt = '修改角色或状态后，该账号的所有已登录会话将失效。确定保存吗？'
      }
      if (prompt && !window.confirm(prompt)) return
      pending = accountMutation(draft, current.mode, current.account, crypto.randomUUID())
    }
    saving.value = true
    formError.value = ''
    try {
      await mutateAdminAccount(pending)
    } catch (cause) {
      if (disposed) return
      formError.value = errorText(cause)
      uncertain.value = !(cause instanceof ApiError) || isUncertainAccountWrite(cause.status)
      conflict.value = cause instanceof ApiError && cause.code === 'VERSION_CONFLICT'
      if (!uncertain.value) pending = null
      return
    } finally {
      if (!disposed) saving.value = false
    }
    if (disposed) return
    pending = null
    editor.value = null
    dirty.value = false
    uncertain.value = false
    ElMessage.success(current.mode === 'create' ? '账号已创建，首次登录须修改密码' : current.mode === 'reset' ? '密码已重置，原登录会话已失效' : '账号已更新')
    if (current.account?.id === session.account?.id) {
      try {
        await session.restore()
      } catch (cause) {
        // 写入已经确认成功，后续身份刷新失败不能误标为未知写入结果。
        if (!disposed) listError.value = errorText(cause)
        return
      }
    }
    if (!disposed) await loadList()
  }

  onMounted(loadList)
  onBeforeUnmount(() => {
    disposed = true
    listController?.abort()
    pending = null
    rows.value = []
    editor.value = null
    history.value = null
  })
  return { query, rows, total, loading, listError, editor, history, saving, dirty, formError, uncertain, conflict, pages, loadList, search, paginate, openEditor, closeEditor, confirmDiscard, save }
}
