import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useSessionStore } from '../../stores/session'
import { useNotificationOverviewStore } from '../../stores/notificationOverview'

/** 布局可见时随入口或身份变化刷新摘要，隐藏及卸载后立即停止读取。 */
export function useNotificationOverview() {
  const route = useRoute()
  const session = useSessionStore()
  const overview = useNotificationOverviewStore()

  /** 返回前台重新核验数量，离开前台不保留上一时刻的数量或发起后台轮询。 */
  function visibilityChanged() {
    if (document.visibilityState === 'visible') void overview.refresh()
    else overview.clear()
  }

  watch(() => [route.path, session.account?.id, session.account?.role, session.account?.communityId, session.account?.passwordChangeRequired], () => void overview.refresh(), { immediate: true })
  onMounted(() => document.addEventListener('visibilitychange', visibilityChanged))
  onBeforeUnmount(() => {
    document.removeEventListener('visibilitychange', visibilityChanged)
    overview.clear()
  })
  return overview
}
