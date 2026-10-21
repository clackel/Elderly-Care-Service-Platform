<script setup lang="ts">
import { shallowRef } from 'vue'
import { onShow, onHide, onUnload } from '@dcloudio/uni-app'
import PageFrame from '../../components/PageFrame.vue'
import MobileBookingWorkspace from '../../components/booking/MobileBookingWorkspace.vue'
import { useSessionStore } from '../../stores/session'
const session = useSessionStore(),
  visible = shallowRef(false),
  error = shallowRef('')
let visit = 0
/** 返回服务页先恢复当前身份，再挂载列表或消费消息传来的预约目标。 */
onShow(async () => {
  const current = ++visit
  visible.value = false
  error.value = ''
  try {
    await session.restore()
    if (current === visit) visible.value = true
  } catch (cause) {
    if (current === visit) error.value = cause instanceof Error ? cause.message : '请重新登录'
  }
})
/** 页面隐藏或退出立即卸载预约详情，迟到请求不得回填旧内容。 */
function hide() {
  visit++
  visible.value = false
}
onHide(hide)
onUnload(hide)
/** 引导到真实账号登录入口。 */
function login() {
  uni.switchTab({ url: '/pages/profile/index' })
}
</script>
<template>
  <PageFrame
    :title="session.account?.role === 'STAFF' ? '我的服务任务' : '养老服务预约'"
    subtitle="申请预约，社区确认，安心服务"
  >
    <text v-if="error" class="body-copy">{{ error }}</text>
    <text v-if="!visible && !error" class="body-copy">正在核对账号…</text>
    <MobileBookingWorkspace v-if="visible && session.account" :key="session.account.id" />
    <view v-else-if="visible || error" class="mobile-card"
      ><text class="card-title">请先登录</text
      ><text class="body-copy"
        >登录后查看所属社区服务，为本人或获授权老人预约。服务人员可查看本人任务。</text
      ><button class="feature-button" @click="login">前往我的账号</button></view
    >
  </PageFrame>
</template>
