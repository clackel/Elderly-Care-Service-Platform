<script setup lang="ts">
import { shallowRef } from 'vue'
import { healthTime, measurementText, type HealthRecord, type HealthRevision } from '../../api/health'
const props = defineProps<{ record: HealthRecord; history: HealthRevision[]; historyPage: number; historyTotal: number; disabled: boolean }>()
const emit = defineEmits<{ correct: []; void: [reason: string]; history: [page: number]; close: []; refresh: [] }>()
const reason = shallowRef(''), voiding = shallowRef(false), error = shallowRef('')
/** 二次确认作废含义后上抛原因，不在详情组件直接请求。 */
function confirmVoid() {
  if (!reason.value.trim()) { error.value = '请填写作废原因'; return }
  uni.showModal({ title: '确认作废这条记录？', content: '作废后不进入趋势，保留历史，不能恢复。', success: (r) => {
    if (r.confirm && !props.disabled) emit('void', reason.value.trim())
  } })
}
</script>
<template>
  <view class="mobile-card">
    <text class="card-title">{{ measurementText(record.measurement) }}</text>
    <text class="body-copy">测量：{{ healthTime(record.measurement.measuredAt) }}</text>
    <text class="body-copy">录入：{{ healthTime(record.createdAt) }} · {{ record.entryMode === 'SELF' ? '本人手工录入' : '社区手工代录' }}</text>
    <text class="body-copy">原录入账号：{{ record.originalActorId }} · 当前版本{{ record.version }}</text>
    <text class="body-copy">{{ record.status === 'VOID' ? '已作废' : '有效记录' }}{{ record.ownerTakenOver ? ' · 已由本人接管维护' : '' }}</text>
    <view v-if="record.canWrite" class="health-actions"><button :disabled="disabled" @click="emit('correct')">更正</button><button :disabled="disabled" @click="voiding = !voiding">作废</button></view>
    <view v-if="voiding && record.canWrite"><textarea v-model="reason" class="health-control health-reason" :disabled="disabled" :maxlength="300" placeholder="作废原因（必填）" /><text class="health-error">{{ error }}</text><button :disabled="disabled" @click="confirmVoid">确认作废</button></view>
    <button v-if="record.canHistory" :disabled="disabled" @click="emit('history', 1)">查看历史旧值与原因（仅本人）</button>
    <view v-for="revision in history" :key="revision.version" class="health-history">
      <text class="body-copy">版本{{ revision.version }} · {{ revision.action === 'CREATE' ? '录入' : revision.action === 'CORRECT' ? '更正' : '作废' }}</text>
      <text class="body-copy">{{ measurementText(revision.measurement) }}</text>
      <text class="body-copy">测量：{{ healthTime(revision.measurement.measuredAt) }}</text>
      <text class="body-copy">原因：{{ revision.reason || '首次录入' }} · 操作人{{ revision.actorId }}</text>
      <text class="body-copy">{{ healthTime(revision.occurredAt) }}</text>
    </view>
    <view v-if="history.length" class="health-actions"><button :disabled="disabled || historyPage <= 1" @click="emit('history', historyPage - 1)">上一页旧值</button><button :disabled="disabled || historyPage * 10 >= historyTotal" @click="emit('history', historyPage + 1)">下一页旧值</button></view>
    <button :disabled="disabled" @click="emit('refresh')">重新读取详情</button><button :disabled="disabled" @click="emit('close')">返回记录</button>
  </view>
</template>

