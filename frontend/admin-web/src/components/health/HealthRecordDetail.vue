<script setup lang="ts">
import { shallowRef } from 'vue'
import { healthTime, measurementText, type HealthRecord } from '../../api/health'
defineProps<{ record: HealthRecord; disabled: boolean }>()
const emit = defineEmits<{ correct: []; void: [reason: string]; close: []; refresh: [] }>()
const reason = shallowRef(''), voiding = shallowRef(false), confirmed = shallowRef(false)
</script>
<template>
  <section class="health-panel">
    <h2>{{ measurementText(record.measurement) }}</h2>
    <p>测量时间：{{ healthTime(record.measurement.measuredAt) }}</p><p>录入时间：{{ healthTime(record.createdAt) }}</p>
    <p>来源：社区手工代录 · 原录入账号{{ record.originalActorId }} · 当前版本{{ record.version }}</p>
    <p>{{ record.status === 'ACTIVE' ? '有效记录' : '已作废，不进入趋势' }}</p>
    <div class="health-actions"><el-button v-if="record.canWrite" type="primary" :disabled="disabled" @click="emit('correct')">更正</el-button><el-button v-if="record.canWrite" :disabled="disabled" @click="voiding = !voiding">作废</el-button><el-button :disabled="disabled" @click="emit('refresh')">重新读取</el-button><el-button :disabled="disabled" @click="emit('close')">返回列表</el-button></div>
    <div v-if="voiding && record.canWrite" class="health-fields"><label>作废原因<textarea v-model="reason" :disabled="disabled" maxlength="300" rows="3" /></label><label><input v-model="confirmed" type="checkbox" :disabled="disabled" />确认作废后不能恢复，保留历史记录</label><el-button type="danger" :disabled="disabled || !confirmed || !reason.trim()" @click="emit('void', reason.trim())">确认作废</el-button></div>
  </section>
</template>

