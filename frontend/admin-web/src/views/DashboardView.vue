<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { authApi } from '../api/auth'
import { roleLabels } from '../api/roles'
import type { Community } from '../api/types'
import { featureModules } from '../router/modules'
import { useSessionStore } from '../stores/session'
import NotificationSummary from '../components/notifications/NotificationSummary.vue'
import NavIcon from '../components/NavIcon.vue'

const session = useSessionStore()
const community = ref<Community | null>(null)
const loading = ref(false)
const errorMessage = ref('')
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

const readyModules = [
  'notifications',
  'accounts',
  'elders',
  'health',
  'services',
  'consent',
  'bookings',
  'fulfillment',
]

/** 按本地时间段生成问候语，仅用于界面展示。 */
const greeting = computed(() => {
  const hour = new Date().getHours()
  return hour < 6
    ? '夜深了'
    : hour < 12
      ? '早上好'
      : hour < 14
        ? '中午好'
        : hour < 18
          ? '下午好'
          : '晚上好'
})

/** 读取当前账号所属社区，失败时保留错误提示供重试。 */
async function loadCommunity() {
  loading.value = true
  errorMessage.value = ''
  community.value = null
  try {
    community.value = await authApi.community()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '社区信息读取失败'
  } finally {
    loading.value = false
  }
}
onMounted(loadCommunity)
</script>

<template>
  <section class="dashboard-hero">
    <div class="dashboard-hero-text">
      <span class="eyebrow">WORKSPACE</span>
      <h1>{{ greeting }}，{{ session.account?.displayName }}</h1>
      <p>从这里进入社区服务管理，所有数据访问均按当前账号权限由服务端核验。</p>
    </div>
    <div class="dashboard-scope">
      <span class="dashboard-scope-icon"><NavIcon name="community" :size="22" /></span>
      <div>
        <small>当前服务范围</small>
        <strong>{{ loading ? '正在读取社区…' : (community?.name ?? '暂未获取社区') }}</strong>
        <span
          >{{ session.account ? roleLabels[session.account.role] : '—' }} · 社区编号
          {{ community?.id ?? '—' }}</span
        >
      </div>
    </div>
  </section>
  <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false"
    ><el-button @click="loadCommunity">重新加载</el-button></el-alert
  >
  <NotificationSummary v-if="session.canViewNotifications" />
  <section class="section-heading">
    <h2>功能入口</h2>
    <span class="muted">按当前账号权限展示</span>
  </section>
  <div class="module-grid">
    <RouterLink
      v-for="module in visibleModules"
      :key="module.path"
      :to="'/' + module.path"
      class="module-card"
      :class="{ pending: !readyModules.includes(module.path) }"
    >
      <span class="module-icon"><NavIcon :name="module.path" :size="22" /></span>
      <h3>{{ module.title }}</h3>
      <p>{{ module.description }}</p>
      <span class="module-status"
        >{{ readyModules.includes(module.path) ? '进入管理' : '页面已预留' }}
        <NavIcon name="arrow" :size="16"
      /></span>
    </RouterLink>
  </div>
  <div class="framework-note">
    <strong>当前交付范围</strong>
    <p>
      老人档案、养老服务预约与健康协助录入已接入。健康代录需老人本人独立授权；移动账号需先核验身份与绑定。
    </p>
  </div>
</template>
