<script setup lang="ts">
import type { Field, FormValues } from '../../api/bookings'
const props = defineProps<{ fields: Field[]; values: FormValues; disabled?: boolean }>()
const emit = defineEmits<{ change: [key: string, value: string] }>()
/** 选择器索引转换为明确业务枚举，不提交显示文本。 */
function select(field: Field, event: { detail: { value: string | number } }) {
  emit('change', field.key, field.options?.[Number(event.detail.value)]?.value || '')
}
/** 日期与时间分别选择后合成本地时间，提交时才转换成带时区时间。 */
function dateTime(field: Field, part: 'date' | 'time', event: { detail: { value: string } }) {
  const old = props.values[field.key] || ''
  const date = part === 'date' ? event.detail.value : old.slice(0, 10)
  const time = part === 'time' ? event.detail.value : old.slice(11) || '09:00'
  emit('change', field.key, date ? date + 'T' + time : '')
}
/** 获取选择项的中文标签，未选择时清晰提示。 */
function label(field: Field) {
  return (
    field.options?.find((option) => option.value === props.values[field.key])?.label || '请选择'
  )
}
/** 兼容uni-app原生输入事件与H5输入事件，不使用未经声明的事件属性。 */
function input(key: string, event: Event | { detail: { value: string } }) {
  const value =
    'detail' in event
      ? (event as { detail: { value: string } }).detail.value
      : (event.target as HTMLInputElement).value
  emit('change', key, value)
}
</script>
<template>
  <view class="mobile-form">
    <view v-for="field in fields" :key="field.key" class="mobile-form-field">
      <text class="mobile-field-label"
        >{{ field.label }}{{ field.required ? '（必填）' : '' }}</text
      >
      <picker
        v-if="field.type === 'select'"
        :disabled="disabled"
        :range="field.options || []"
        range-key="label"
        :value="
          Math.max(
            0,
            (field.options || []).findIndex((o) => o.value === values[field.key]),
          )
        "
        @change="select(field, $event)"
        ><view class="mobile-control">{{ label(field) }} ▾</view></picker
      >
      <view v-else-if="field.type === 'datetime-local'" class="mobile-date-pair"
        ><picker
          mode="date"
          :disabled="disabled"
          :value="(values[field.key] || '').slice(0, 10)"
          @change="dateTime(field, 'date', $event)"
          ><view class="mobile-control">{{
            (values[field.key] || '').slice(0, 10) || '选择日期'
          }}</view></picker
        ><picker
          mode="time"
          :disabled="disabled || !values[field.key]"
          :value="(values[field.key] || '').slice(11) || '09:00'"
          @change="dateTime(field, 'time', $event)"
          ><view class="mobile-control">{{
            (values[field.key] || '').slice(11) || '选择时间'
          }}</view></picker
        ></view
      >
      <picker
        v-else-if="field.type === 'date'"
        mode="date"
        :disabled="disabled"
        :value="values[field.key] || ''"
        @change="emit('change', field.key, $event.detail.value)"
        ><view class="mobile-control">{{ values[field.key] || '选择日期' }}</view></picker
      >
      <textarea
        v-else-if="field.type === 'textarea'"
        class="mobile-control mobile-textarea"
        :value="values[field.key] || ''"
        :disabled="disabled"
        :maxlength="field.max || 300"
        :placeholder="'请输入' + field.label"
        @input="input(field.key, $event)"
      />
      <input
        v-else
        class="mobile-control"
        :type="field.type === 'number' ? 'number' : 'text'"
        :value="values[field.key] || ''"
        :disabled="disabled"
        :maxlength="field.type === 'number' ? 9 : field.max || 200"
        :placeholder="'请输入' + field.label"
        @input="input(field.key, $event)"
      />
    </view>
  </view>
</template>
