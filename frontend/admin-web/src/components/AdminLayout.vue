<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { featureModules } from '../router/modules'
import { useSessionStore } from '../stores/session'
import { useNotificationOverview } from './notifications/useNotificationOverview'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const notifications = useNotificationOverview()
const loggingOut = ref(false)
const visibleModules = computed(() =>
  featureModules.filter(
    (module) =>
      module.path === 'notifications'
        ? session.canViewNotifications
        : module.path === 'accounts'
        ? session.canManageAccounts
        : module.path === 'health'
        ? session.canAssistHealth
        : !['elders', 'services', 'consent', 'bookings', 'fulfillment'].includes(module.path) ||
          session.canManageElders,
  ),
)

/** 服务端确认注销后跳转登录页，失败时保留会话并提示重试。 */
async function logout() {
  loggingOut.value = true
  try {
    await session.logout()
    await router.replace('/login')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '注销失败，请重试')
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <div class="admin-shell">
    <aside class="sidebar">
      <RouterLink to="/" class="brand"
        ><span class="brand-mark">安</span
        ><span>社区养老<br /><small>运营管理平台</small></span></RouterLink
      >
      <div class="nav-label">社区运营</div>
      <nav aria-label="主导航">
        <RouterLink to="/" class="nav-item" exact-active-class="active">工作台</RouterLink>
        <RouterLink
          v-for="module in visibleModules"
          :key="module.path"
          :to="'/' + module.path"
          class="nav-item"
          active-class="active"
          >{{ module.title }}<span v-if="module.path === 'notifications' && notifications.unread && notifications.unread > 0" class="notification-nav-count" :aria-label="notifications.unread + '条未读消息'">{{ notifications.unread > 99 ? '99+' : notifications.unread }}</span></RouterLink
        >
      </nav>
      <div class="sidebar-foot">基础框架 · v0.1.0</div>
    </aside>
    <div class="main-area">
      <header class="topbar">
        <span
          >运营中心 <span class="muted">/ {{ route.meta.title }}</span></span
        >
        <div class="account-menu">
          <span>{{ session.account?.displayName }}</span>
          <RouterLink to="/password">修改密码</RouterLink>
          <el-button :loading="loggingOut" @click="logout">退出登录</el-button>
        </div>
      </header>
      <main class="page-content"><RouterView :key="route.path" /></main>
    </div>
  </div>
</template>

<style scoped>
.notification-nav-count { display: inline-block; margin-left: 8px; padding: 2px 6px; border-radius: 10px; color: #fff; background: #a84d35; font-size: 11px; line-height: 1.3; }
</style>
