import { computed, onBeforeUnmount, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../../api/auth'
import { ApiError } from '../../api/http'
import { useSessionStore } from '../../stores/session'
import { errorText } from '../elders/elderModel'
import { isUncertainAccountWrite } from './accountModel'

/** 管理本人改密请求，未知结果只保留内存快照，成功后要求重新登录。 */
export function usePasswordChange() {
  const router = useRouter()
  const session = useSessionStore()
  const busy = shallowRef(false)
  const uncertain = shallowRef(false)
  const error = shallowRef('')
  const dirty = shallowRef(false)
  const required = computed(() => !!session.account?.passwordChangeRequired)
  let pending: { requestId: string; currentPassword: string; newPassword: string } | null = null
  let disposed = false

  /** 提交本人当前密码和新密码；重试固定载荷，已失效会话由统一请求层转回登录。 */
  async function save(input: { currentPassword: string; newPassword: string }) {
    if (busy.value) return
    pending ??= { requestId: crypto.randomUUID(), ...input }
    busy.value = true
    error.value = ''
    try {
      await authApi.changePassword(pending)
      if (disposed) return
      pending = null
      dirty.value = false
      uncertain.value = false
      session.clear()
      ElMessage.success('密码已修改，请使用新密码重新登录')
      await router.replace('/login')
    } catch (cause) {
      if (disposed) return
      error.value = errorText(cause)
      uncertain.value = !(cause instanceof ApiError) || isUncertainAccountWrite(cause.status)
      if (!uncertain.value) pending = null
    } finally {
      if (!disposed) busy.value = false
    }
  }

  /** 浏览器导航离开前确认未完成操作，敏感内容不会保留到下次访问。 */
  function confirmLeave(): boolean {
    if (!session.account) return true
    if (busy.value) return false
    if (uncertain.value)
      return window.confirm(
        '改密结果尚未确认，离开会清除本次重试信息。可重新登录核对密码是否生效。确定离开吗？',
      )
    return !dirty.value || window.confirm('尚有未保存的密码输入，离开后将清除。确定离开吗？')
  }

  /** 服务端确认退出后清除身份和密码草稿，失败保留重试机会。 */
  async function logout() {
    if (!confirmLeave()) return
    busy.value = true
    error.value = ''
    try {
      await session.logout()
      if (disposed) return
      pending = null
      await router.replace('/login')
    } catch (cause) {
      if (!disposed) error.value = errorText(cause)
    } finally {
      if (!disposed) busy.value = false
    }
  }

  onBeforeUnmount(() => {
    disposed = true
    pending = null
  })
  return { busy, uncertain, error, dirty, required, save, logout, confirmLeave }
}
