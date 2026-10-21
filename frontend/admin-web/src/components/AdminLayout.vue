<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { featureModules } from '../router/modules'
import { roleLabels } from '../api/roles'
import NavIcon from './NavIcon.vue'
import { useSessionStore } from '../stores/session'
import { useNotificationOverview } from './notifications/useNotificationOverview'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const notifications = useNotificationOverview()
const loggingOut = ref(false)
const visibleModules = computed(() =>
  featureModules.filter((module) =>
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

/** 取显示名称首字作为头像，缺少名称时显示占位符。 */
const avatarText = computed(() => session.account?.displayName?.trim().charAt(0) || '·')

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
        ><span class="brand-mark" aria-hidden="true">安</span
        ><span class="brand-text">社区养老<small>运营管理平台</small></span></RouterLink
      >
      <div class="nav-label">社区运营</div>
      <nav aria-label="主导航">
        <RouterLink to="/" class="nav-item" exact-active-class="active"
          ><NavIcon name="home" /><span class="nav-text">工作台</span></RouterLink
        >
        <RouterLink
          v-for="module in visibleModules"
          :key="module.path"
          :to="'/' + module.path"
          class="nav-item"
          active-class="active"
          ><NavIcon :name="module.path" /><span class="nav-text">{{ module.title }}</span
          ><span
            v-if="
              module.path === 'notifications' && notifications.unread && notifications.unread > 0
            "
            class="notification-nav-count"
            :aria-label="notifications.unread + '条未读消息'"
            >{{ notifications.unread > 99 ? '99+' : notifications.unread }}</span
          ></RouterLink
        >
      </nav>
      <div class="sidebar-foot">
        <span class="sidebar-foot-dot" aria-hidden="true"></span>权限由服务端核验 · v0.1.0
      </div>
    </aside>
    <div class="main-area">
      <header class="topbar">
        <nav class="breadcrumb" aria-label="当前位置">
          <span class="muted">运营中心</span><span class="breadcrumb-sep" aria-hidden="true">/</span
          ><strong>{{ route.meta.title }}</strong>
        </nav>
        <div class="account-menu">
          <div class="account-chip">
            <span class="avatar" aria-hidden="true">{{ avatarText }}</span>
            <span class="account-text"
              ><strong>{{ session.account?.displayName }}</strong
              ><small>{{ session.account ? roleLabels[session.account.role] : '' }}</small></span
            >
          </div>
          <RouterLink to="/password" class="topbar-link"
            ><NavIcon name="password" :size="16" />修改密码</RouterLink
          >
          <el-button :loading="loggingOut" @click="logout">退出登录</el-button>
        </div>
      </header>
      <main class="page-content"><RouterView :key="route.path" /></main>
    </div>
  </div>
</template>

<style scoped>
.notification-nav-count {
  margin-left: auto;
  min-width: 20px;
  padding: 2px 7px;
  border-radius: 999px;
  color: #fff;
  background: var(--ec-danger);
  font-size: 11px;
  font-weight: 700;
  line-height: 1.4;
  text-align: center;
}
</style>
