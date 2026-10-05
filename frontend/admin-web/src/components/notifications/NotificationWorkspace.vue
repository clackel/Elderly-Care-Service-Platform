<script setup lang="ts">
import { reactive } from 'vue'
import NotificationList from './NotificationList.vue'
import TodoList from './TodoList.vue'
import DeliveryFailureList from './DeliveryFailureList.vue'
import NotificationPager from './NotificationPager.vue'
import { useNotificationCenter, type NotificationTab } from './useNotificationCenter'
import './notifications.css'

const center = reactive(useNotificationCenter())
const tabs: { value: NotificationTab; label: string }[] = [
  { value: 'notices', label: '我的消息' }, { value: 'todos', label: '业务待办' }, { value: 'deliveries', label: '发送异常' },
]
</script>

<template>
  <div class="notification-workspace">
    <header class="page-heading"><div><span class="eyebrow">COMMUNITY UPDATES</span><h1>消息与待办</h1><p class="muted">查看预约变化，及时处理本社区需要关注的服务。</p></div><el-button :loading="center.loading" :disabled="!!center.writingId" @click="center.load">刷新当前列表</el-button></header>
    <nav class="notification-tabs" aria-label="消息工作区"><button v-for="tab in tabs" :key="tab.value" type="button" :class="{ active: center.tab === tab.value }" :aria-current="center.tab === tab.value ? 'page' : undefined" :disabled="!!center.writingId" @click="center.changeTab(tab.value)">{{ tab.label }}</button></nav>
    <el-alert v-if="center.writeError" :title="center.writeError" type="error" :closable="false" role="alert" class="notification-feedback" />
    <el-alert v-if="center.feedback" :title="center.feedback" type="info" :closable="false" class="notification-feedback" />
    <div v-if="center.pendingRetry && !center.writingId" class="notification-warning" role="status"><p>重新发送的结果尚未确认，保留了原申请。请重试原申请；若状态已变化，刷新后以当前列表为准。</p><el-button @click="center.retryDelivery()">重试原申请</el-button></div>
    <section class="notification-panel" :aria-busy="center.loading">
      <p v-if="center.loading" role="status">正在读取最新消息与待办…</p>
      <div v-if="center.listError" role="alert"><p>{{ center.listError }}</p><el-button @click="center.load">重新加载</el-button></div>
      <template v-else>
        <NotificationList v-if="center.tab === 'notices'" :rows="center.notices" :unread-only="center.unreadOnly" :loading="center.loading" :writing-id="center.writingId" @filter="center.filterUnread" @read="center.markRead" @open="center.openNotice" />
        <TodoList v-else-if="center.tab === 'todos'" :rows="center.todos" :type="center.todoType" :loading="center.loading" :disabled="!!center.writingId" @filter="center.filterTodos" @open="center.openBooking" />
        <DeliveryFailureList v-else :rows="center.deliveries" :loading="center.loading" :writing-id="center.writingId" :pending-id="center.pendingRetry?.eventId ?? ''" @retry="center.retryDelivery" />
        <NotificationPager :page="center.page" :pages="center.pages" :total="center.total" :disabled="center.loading || !!center.writingId" @change="center.paginate" />
      </template>
    </section>
  </div>
</template>
