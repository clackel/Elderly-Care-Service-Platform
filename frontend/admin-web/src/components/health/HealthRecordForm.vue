<script setup lang="ts">
import { reactive, shallowRef } from 'vue'
import { healthDraft, toMeasurement, healthTypes, glucoseScenes, type Measurement } from '../../api/health'
const props = defineProps<{ initial?: Measurement; correcting: boolean; disabled: boolean; conflict: boolean }>()
const emit = defineEmits<{ save: [measurement: Measurement, reason: string]; close: [] }>()
const draft = reactive(healthDraft(props.initial)), reason = shallowRef(''), error = shallowRef('')
/** 纯表单校验后发出测量和原因事件，不直接调用接口。 */
function submit() {
  try {
    if (props.correcting && !reason.value.trim()) throw new Error('请填写更正原因')
    const measurement = toMeasurement(draft)
    error.value = ''; emit('save', measurement, reason.value.trim())
  } catch (e) { error.value = e instanceof Error ? e.message : '请检查输入' }
}
</script>
<template>
  <section class="health-panel">
    <h2>{{ correcting ? '更正本人代录记录' : '新增手工代录' }}</h2>
    <p class="muted">测量时间使用北京时间。以下仅校验输入格式与存储上限，不作医学判断。</p>
    <form @submit.prevent="submit">
      <fieldset :disabled="disabled" class="health-fields">
        <label>指标<select v-model="draft.type" :disabled="correcting"><option v-for="(label,key) in healthTypes" :key="key" :value="key">{{ label }}</option></select></label>
        <label>测量日期<input v-model="draft.date" type="date" required /></label><label>测量时间<input v-model="draft.time" type="time" required /></label>
        <template v-if="draft.type === 'BLOOD_PRESSURE'"><label>收缩压（mmHg）<input v-model="draft.systolic" inputmode="numeric" maxlength="3" required /></label><label>舒张压（mmHg）<input v-model="draft.diastolic" inputmode="numeric" maxlength="3" required /></label></template>
        <label v-else-if="draft.type === 'HEART_RATE'">心率（次/分钟）<input v-model="draft.heartRate" inputmode="numeric" maxlength="3" required /></label>
        <label v-else-if="draft.type === 'WEIGHT'">体重（kg）<input v-model="draft.weight" inputmode="decimal" maxlength="6" required /></label>
        <template v-else><label>血糖（mmol/L）<input v-model="draft.glucose" inputmode="decimal" maxlength="5" required /></label><label>血糖场景<select v-model="draft.glucoseScene"><option v-for="(label,key) in glucoseScenes" :key="key" :value="key">{{ label }}</option></select></label></template>
        <label v-if="correcting">更正原因<textarea v-model="reason" maxlength="300" required rows="3" /></label>
      </fieldset>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-alert v-if="conflict" title="版本或状态已变化，请关闭表单并重新读取详情后核对，不会自动覆盖。" type="warning" :closable="false" />
      <div class="health-actions"><el-button native-type="submit" type="primary" :disabled="disabled || conflict">确认保存</el-button><el-button :disabled="disabled" @click="emit('close')">关闭并放弃未保存内容</el-button></div>
    </form>
  </section>
</template>

