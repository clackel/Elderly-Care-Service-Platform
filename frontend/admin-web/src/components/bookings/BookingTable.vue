<script setup lang="ts">
import {
  categoryLabels,
  statusLabels,
  time,
  type BookingSummary,
  type BookingStatus,
} from '../../api/bookings'
defineProps<{
  rows: BookingSummary[]
  page: number
  total: number
  status: BookingStatus | ''
  loading: boolean
  busy: boolean
}>()
const emit = defineEmits<{
  view: [id: string]
  page: [value: number]
  filter: [value: BookingStatus | '']
  retry: []
}>()
/** 状态筛选上送后由父容器重置分页。 */
function filter(event: Event) {
  emit('filter', (event.target as HTMLSelectElement).value as BookingStatus | '')
}
</script>
<template>
  <section class="booking-panel">
    <div class="booking-toolbar">
      <label
        >预约状态
        <select :value="status" :disabled="busy" @change="filter">
          <option value="">全部状态</option>
          <option v-for="(label, value) in statusLabels" :key="value" :value="value">
            {{ label }}
          </option>
        </select></label
      ><button class="booking-link" :disabled="loading || busy" @click="emit('retry')">
        刷新列表
      </button>
    </div>
    <p v-if="loading" role="status">正在读取预约…</p>
    <div v-else class="booking-table-wrap">
      <table class="booking-table">
        <thead>
          <tr>
            <th>服务项目</th>
            <th>期望时间</th>
            <th>实际安排</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>
              {{ row.serviceName }}
              <div class="booking-muted">{{ categoryLabels[row.category] }} · {{ row.id }}</div>
            </td>
            <td>{{ time(row.requestedStart) }}</td>
            <td>{{ time(row.scheduledStart) }}</td>
            <td>
              <span class="booking-status">{{ statusLabels[row.status] }}</span>
              <div v-if="row.hasException" class="booking-error">有待处理异常</div>
            </td>
            <td>
              <button class="booking-link" :disabled="busy" @click="emit('view', row.id)">
                查看与处理
              </button>
            </td>
          </tr>
          <tr v-if="!rows.length">
            <td colspan="5">当前条件下暂无预约。</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="booking-actions">
      <button :disabled="page <= 1 || loading || busy" @click="emit('page', page - 1)">
        上一页</button
      ><span>第 {{ page }} 页，共 {{ total }} 条</span
      ><button :disabled="page * 10 >= total || loading || busy" @click="emit('page', page + 1)">
        下一页
      </button>
    </div>
  </section>
</template>
