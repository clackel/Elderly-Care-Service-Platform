<script setup lang="ts">
import { healthTime, measurementText, type HealthRecord } from '../../api/health'
defineProps<{ rows: HealthRecord[]; page: number; total: number; disabled: boolean }>()
const emit = defineEmits<{ open: [id: string]; page: [page: number] }>()
</script>
<template>
  <view>
    <text v-if="!rows.length" class="body-copy">暂无记录。未测量或缺失数据不代表健康正常。</text>
    <view v-for="row in rows" :key="row.id" class="mobile-card">
      <text class="card-title">{{ measurementText(row.measurement) }}</text>
      <text class="body-copy">{{ healthTime(row.measurement.measuredAt) }} · {{ row.entryMode === 'SELF' ? '本人手工录入' : '社区手工代录' }}</text>
      <text class="body-copy">{{ row.status === 'VOID' ? '已作废' : '有效记录' }}{{ row.corrected ? ' · 已有后续版本' : '' }}</text>
      <button :disabled="disabled" @click="emit('open', row.id)">查看详情</button>
    </view>
    <view class="health-actions"><button :disabled="disabled || page <= 1" @click="emit('page', page - 1)">上一页</button><text>第{{ page }}页，共{{ total }}条</text><button :disabled="disabled || page * 10 >= total" @click="emit('page', page + 1)">下一页</button></view>
  </view>
</template>

