<script setup lang="ts">
import { todoTitle, notificationTime, type Todo } from '../../api/notifications'
defineProps<{ rows: Todo[]; disabled: boolean }>()
const emit = defineEmits<{ open: [id: string] }>()
</script>

<template>
  <view>
    <text class="body-copy">待办随预约状态更新。消息已读不会完成任务。</text>
    <view v-for="todo in rows" :key="todo.bookingId + ':' + todo.type" class="mobile-card">
      <text class="card-title">{{ todoTitle(todo.type) }}</text>
      <text class="body-copy">{{ todo.status === 'IN_PROGRESS' ? '服务进行中，待记录结果' : '服务已安排，待开始' }}</text>
      <text class="body-copy">开始：{{ notificationTime(todo.scheduledStart) }}</text>
      <text class="body-copy">结束：{{ notificationTime(todo.scheduledEnd) }}</text>
      <text v-if="todo.hasException" class="todo-warning">有未处理异常，请联系社区处理。</text>
      <text v-if="todo.overdue" class="todo-warning">已超过计划时间，请核对实际进度。</text>
      <button :disabled="disabled" class="feature-button" @click="emit('open', todo.bookingId)">打开任务</button>
    </view>
  </view>
</template>

<style scoped>
.todo-warning { display: block; color: #8b3d22; margin: 12px 0; }
</style>
