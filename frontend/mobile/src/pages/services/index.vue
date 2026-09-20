<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PageFrame from '../../components/PageFrame.vue'
import MobileBookingWorkspace from '../../components/booking/MobileBookingWorkspace.vue'
import { useSessionStore } from '../../stores/session'
const session = useSessionStore(),
  visit = ref(0)
/** 每次返回服务页重新读取权限及列表，不沿用后台缓存的任务资料。 */
onShow(() => {
  visit.value++
})
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
    <MobileBookingWorkspace v-if="session.account" :key="session.account.id + ':' + visit" />
    <view v-else class="mobile-card"
      ><text class="card-title">请先登录</text
      ><text class="body-copy"
        >登录后查看所属社区服务，为本人或获授权老人预约。服务人员可查看本人任务。</text
      ><button class="feature-button" @click="login">前往我的账号</button></view
    >
  </PageFrame>
</template>
