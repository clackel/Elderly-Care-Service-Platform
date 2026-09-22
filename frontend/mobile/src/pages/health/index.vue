<script setup lang="ts">
import { shallowRef } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import PageFrame from '../../components/PageFrame.vue'
import HealthWorkspace from '../../components/health/HealthWorkspace.vue'
import { useSessionStore } from '../../stores/session'
const session = useSessionStore(), visible = shallowRef(false), error = shallowRef('')
let visit = 0
/** 返回前台时重新核验身份，旧页面先卸载，不沿用上一访问的健康正文。 */
onShow(async () => {
  const current = ++visit
  visible.value = false; error.value = ''
  try { await session.restore(); if (current === visit) visible.value = true }
  catch (e) { if (current === visit) error.value = e instanceof Error ? e.message : '请重新登录' }
})
/** 离开页面或进入后台立即卸载健康组件，阻止迟到请求回填。 */
onHide(() => { visit++; visible.value = false })
/** 引导到现有真实账号入口。 */
function login() { uni.switchTab({ url: '/pages/profile/index' }) }
</script>
<template>
  <PageFrame title="我的健康" subtitle="个人健康信息，仅向获准人员开放">
    <text v-if="error" class="body-copy">{{ error }}</text>
    <HealthWorkspace v-if="visible && session.account && ['ELDER','FAMILY'].includes(session.account.role)" :key="session.account.id" />
    <view v-else class="mobile-card"><text class="body-copy">健康记录仅向有效绑定本人及获独立健康授权的家属开放。</text><button @click="login">前往我的账号</button></view>
  </PageFrame>
</template>
