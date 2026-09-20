<script setup lang="ts">
import { statusLabels, time, type BookingSummary } from '../../api/bookings'
defineProps<{
  rows: BookingSummary[]
  page: number
  total: number
  busy: boolean
  staff: boolean
  status: string
}>()
const emit = defineEmits<{ select: [id: string]; page: [page: number]; filter: [status: string] }>()
const filters = [
  { value: '', label: '全部' },
  { value: 'PENDING', label: '待确认' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'IN_PROGRESS', label: '服务中' },
  { value: 'COMPLETED', label: '已完成' },
]
</script>
<template>
  <view>
    <view class="mobile-filter"
      ><button
        v-for="item in filters.filter((f) => !staff || f.value !== 'PENDING')"
        :key="item.value"
        :disabled="busy"
        :class="{ 'font-button': status === item.value }"
        @click="emit('filter', item.value)"
      >
        {{ item.label }}
      </button></view
    >
    <view v-for="row in rows" :key="row.id" class="mobile-card"
      ><text class="mobile-chip">{{ statusLabels[row.status] }}</text
      ><text class="card-title">{{ row.serviceName }}</text
      ><text class="body-copy">期望时间：{{ time(row.requestedStart) }}</text
      ><text class="body-copy">实际安排：{{ time(row.scheduledStart) }}</text
      ><view v-if="row.hasException" class="notice-card">服务异常待处理</view
      ><view class="mobile-buttons"
        ><button class="feature-button" :disabled="busy" @click="emit('select', row.id)">
          {{ staff ? '查看任务与记录执行' : '查看预约进度' }}
        </button></view
      ></view
    >
    <view v-if="!rows.length && !busy" class="mobile-card">{{
      staff ? '暂无分配给您的任务。' : '暂无符合条件的预约。'
    }}</view>
    <view class="mobile-buttons"
      ><button :disabled="busy || page <= 1" @click="emit('page', page - 1)">上一页</button
      ><text>第 {{ page }} 页，共 {{ total }} 条</text
      ><button :disabled="busy || page * 10 >= total" @click="emit('page', page + 1)">
        下一页
      </button></view
    >
  </view>
</template>
