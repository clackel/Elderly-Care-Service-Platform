<script setup lang="ts">
import { reactive, shallowRef } from 'vue'
import { healthDraft, toMeasurement, healthTypes, glucoseScenes, units, type Measurement, type HealthType, type GlucoseScene } from '../../api/health'
const props = defineProps<{ initial?: Measurement; disabled: boolean; correcting: boolean; conflict: boolean }>()
const emit = defineEmits<{ save: [measurement: Measurement, reason: string]; close: [] }>()
const draft = reactive(healthDraft(props.initial)), reason = shallowRef(''), error = shallowRef('')
const types = Object.keys(healthTypes) as HealthType[], scenes = Object.keys(glucoseScenes) as GlucoseScene[]
/** 校验与规范化后只发出事件，表单不直接调用接口。 */
function submit() {
  try {
    if (props.correcting && (!reason.value.trim() || reason.value.trim().length > 300)) throw new Error('请填写更正原因')
    const measurement = toMeasurement(draft)
    error.value = ''; emit('save', measurement, reason.value.trim())
  } catch (e) { error.value = e instanceof Error ? e.message : '请检查输入' }
}
</script>
<template>
  <view class="mobile-card">
    <text class="card-title">{{ correcting ? '更正测量' : '新增测量' }}</text>
    <text class="body-copy">手工记录 · 时间为北京时间 · 输入限制不是医学正常范围</text>
    <text class="body-copy">指标</text>
    <picker :disabled="disabled || correcting" :range="types.map(t => healthTypes[t])" :value="types.indexOf(draft.type)" @change="draft.type = types[Number($event.detail.value)]!">
      <view class="health-control">{{ healthTypes[draft.type] }} ▾</view>
    </picker>
    <text class="body-copy">测量日期与时间</text>
    <picker mode="date" :value="draft.date" :disabled="disabled" @change="draft.date = $event.detail.value"><view class="health-control">{{ draft.date }}</view></picker>
    <picker mode="time" :value="draft.time" :disabled="disabled" @change="draft.time = $event.detail.value"><view class="health-control">{{ draft.time }}</view></picker>
    <template v-if="draft.type === 'BLOOD_PRESSURE'">
      <text class="body-copy">收缩压（mmHg）</text><input v-model="draft.systolic" class="health-control" type="number" :disabled="disabled" :maxlength="3" placeholder="填写收缩压" />
      <text class="body-copy">舒张压（mmHg）</text><input v-model="draft.diastolic" class="health-control" type="number" :disabled="disabled" :maxlength="3" placeholder="填写舒张压" />
    </template>
    <template v-else-if="draft.type === 'HEART_RATE'"><text class="body-copy">心率（次/分钟）</text><input v-model="draft.heartRate" class="health-control" type="number" :disabled="disabled" :maxlength="3" placeholder="填写心率" /></template>
    <template v-else-if="draft.type === 'WEIGHT'"><text class="body-copy">体重（{{ units.WEIGHT }}）</text><input v-model="draft.weight" class="health-control" type="digit" :disabled="disabled" :maxlength="6" placeholder="填写体重" /></template>
    <template v-else>
      <text class="body-copy">血糖（mmol/L）</text><input v-model="draft.glucose" class="health-control" type="digit" :disabled="disabled" :maxlength="5" placeholder="填写血糖" />
      <text class="body-copy">血糖场景</text><picker :disabled="disabled" :range="scenes.map(s => glucoseScenes[s])" :value="scenes.indexOf(draft.glucoseScene)" @change="draft.glucoseScene = scenes[Number($event.detail.value)]!"><view class="health-control">{{ glucoseScenes[draft.glucoseScene] }} ▾</view></picker>
    </template>
    <template v-if="correcting"><text class="body-copy">更正原因（必填）</text><textarea v-model="reason" class="health-control health-reason" :disabled="disabled" :maxlength="300" placeholder="填写需要更正的原因" /></template>
    <text v-if="error" class="health-error">{{ error }}</text>
    <text v-if="conflict" class="health-error">版本或状态已变化，请关闭表单，重新读取详情后核对。不会自动覆盖。</text>
    <button class="feature-button" :disabled="disabled || conflict" @click="submit">确认保存</button>
    <button :disabled="disabled" @click="emit('close')">关闭并放弃未保存内容</button>
  </view>
</template>

