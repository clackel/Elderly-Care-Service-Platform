<script setup lang="ts">
import { notificationTime, todoTitle, type Todo, type TodoType } from '../../api/notifications'
import { statusLabels } from '../../api/bookings'

defineProps<{ rows: Todo[]; type: TodoType | ''; loading: boolean; disabled: boolean }>()
const emit = defineEmits<{ filter: [type: TodoType | '']; open: [bookingId: string] }>()
const types: TodoType[] = ['PENDING_CONFIRMATION', 'EXCEPTION', 'OVERDUE']
</script>

<template>
  <section aria-labelledby="notification-todos-title">
    <div class="notification-toolbar">
      <div>
        <h2 id="notification-todos-title">本社区待办</h2>
        <p class="muted">在预约中完成处理后，待办自动更新；读消息不会移除待办。</p>
      </div>
      <label class="notification-filter"
        >待办类型<select
          :value="type"
          :disabled="loading || disabled"
          @change="emit('filter', ($event.target as HTMLSelectElement).value as TodoType | '')"
        >
          <option value="">全部类型</option>
          <option v-for="item in types" :key="item" :value="item">{{ todoTitle(item) }}</option>
        </select></label
      >
    </div>
    <el-empty v-if="!loading && !rows.length" description="暂无符合条件的待办" />
    <ol v-else class="notification-list">
      <li v-for="todo in rows" :key="todo.bookingId + ':' + todo.type" class="notification-row">
        <div>
          <div class="notification-title">
            <strong>{{ todoTitle(todo.type) }}</strong
            ><el-tag v-if="todo.overdue" type="warning" size="small">已超时</el-tag
            ><el-tag v-if="todo.hasException" type="danger" size="small">待处理异常</el-tag>
          </div>
          <p class="notification-small">
            预约编号 {{ todo.bookingId }} · {{ statusLabels[todo.status] ?? '状态待确认' }}
          </p>
          <p class="muted notification-small">
            安排时段：{{ notificationTime(todo.scheduledStart) }} 至
            {{ notificationTime(todo.scheduledEnd) }}
          </p>
        </div>
        <el-button type="primary" plain :disabled="disabled" @click="emit('open', todo.bookingId)"
          >打开预约处理</el-button
        >
      </li>
    </ol>
  </section>
</template>
