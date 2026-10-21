<script setup lang="ts">
import { useNotificationOverviewStore } from '../../stores/notificationOverview'
const overview = useNotificationOverviewStore()
</script>

<template>
  <section class="notification-summary" :aria-busy="overview.loading" aria-label="消息与待办摘要">
    <div>
      <h2>今日关注</h2>
      <p class="muted">待办随预约处理结果更新，标记消息已读不会完成待办。</p>
    </div>
    <RouterLink to="/notifications" class="notification-summary-link"
      ><strong>{{ overview.unread ?? '—' }}</strong
      ><span>未读消息</span></RouterLink
    >
    <RouterLink to="/notifications" class="notification-summary-link"
      ><strong>{{ overview.todos ?? '—' }}</strong
      ><span>当前待办</span></RouterLink
    >
    <el-button :loading="overview.loading" @click="overview.refresh">刷新数量</el-button>
    <div v-if="overview.error" class="notification-summary-error" role="alert">
      {{ overview.error }}
    </div>
  </section>
</template>

<style scoped>
.notification-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 24px;
  background: #fff;
  border: 1px solid #dde7df;
  border-radius: 12px;
  padding: 22px;
  margin-bottom: 28px;
}
.notification-summary h2 {
  margin-bottom: 8px;
  font-size: 18px;
}
.notification-summary p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
}
.notification-summary-link {
  display: grid;
  gap: 5px;
  text-align: center;
  color: #246c5d;
  font-size: 13px;
}
.notification-summary-link strong {
  font-size: 28px;
}
.notification-summary-error {
  width: 100%;
  color: #a84034;
  font-size: 13px;
}
</style>
