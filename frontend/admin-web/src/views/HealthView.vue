<script setup lang="ts">
import { onBeforeUnmount, onMounted, shallowRef } from 'vue'
import { useSessionStore } from '../stores/session'
import HealthWorkspace from '../components/health/HealthWorkspace.vue'
const session = useSessionStore(), visible = shallowRef(false), error = shallowRef('')
let epoch = 0
/** 前台重新核验账号，再装配健康页面；隐藏时卸载内容，旧请求不得回填。 */
async function refresh() {
  const current = ++epoch
  visible.value = false
  if (document.hidden) return
  error.value = ''
  try { await session.restore(); if (current === epoch) visible.value = true }
  catch (e) { if (current === epoch) error.value = e instanceof Error ? e.message : '身份读取失败，请重新登录' }
}
/** 注册可见性与窗口返回前台事件，离开页面清除监听。 */
onMounted(() => { document.addEventListener('visibilitychange', refresh); window.addEventListener('focus', refresh); void refresh() })
onBeforeUnmount(() => { epoch++; visible.value = false; document.removeEventListener('visibilitychange', refresh); window.removeEventListener('focus', refresh) })
</script>
<template>
  <HealthWorkspace v-if="visible && session.canAssistHealth" :key="session.account?.id" />
  <el-alert v-else-if="error" :title="error" type="error" :closable="false"><el-button @click="refresh">重新核验</el-button></el-alert>
  <p v-else>正在核验健康协助权限。仅指定的社区运营账号可进入。</p>
</template>

