<script setup lang="ts">
import { shallowRef } from 'vue'
import { onShow, onHide, onUnload } from '@dcloudio/uni-app'
import PageFrame from '../../components/PageFrame.vue'
import NotificationWorkspace from '../../components/notifications/NotificationWorkspace.vue'
import { useSessionStore } from '../../stores/session'
const session = useSessionStore(),
  visible = shallowRef(false),
  error = shallowRef('')
let visit = 0

/** 每次返回先核验身份，再挂载消息内容；旧会话不复用消息或未读计数。 */
onShow(async () => {
  const sequence = ++visit
  visible.value = false
  error.value = ''
  try {
    await session.restore()
    if (sequence === visit) visible.value = true
  } catch (cause) {
    if (sequence === visit) error.value = cause instanceof Error ? cause.message : '请重新登录'
  }
})

/** 页面隐藏或卸载时立即清除内容，迟到读取不再进入当前界面。 */
function hide() {
  visit++
  visible.value = false
}
onHide(hide)
onUnload(hide)

/** 使用既有微信账号入口，保留四个底部导航。 */
function login() {
  uni.switchTab({ url: '/pages/profile/index' })
}
</script>

<template>
  <PageFrame title="消息与待办" subtitle="预约进度与当前需要处理的事项">
    <text v-if="error" class="body-copy">{{ error }}</text>
    <text v-if="!visible && !error" class="body-copy">正在核对账号…</text>
    <NotificationWorkspace v-if="visible && session.account" :key="session.account.id" />
    <view v-else-if="visible || error" class="mobile-card"
      ><text class="body-copy">请先登录，查看自己的消息和任务。</text
      ><button class="feature-button" @click="login">前往我的账号</button></view
    >
  </PageFrame>
</template>
