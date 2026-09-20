<script setup lang="ts">
import type { Field, FormValues } from '../../api/bookings'
defineProps<{ fields: Field[]; values: FormValues; disabled?: boolean }>()
const emit = defineEmits<{ change: [key: string, value: string] }>()
/** 将表单输入上送到父组件，子组件不直接修改草稿。 */
function change(key: string, event: Event) {
  emit('change', key, (event.target as HTMLInputElement).value)
}
/** 将多选类别转换为有界枚举集合的草稿表示。 */
function multi(key: string, event: Event) {
  emit(
    'change',
    key,
    Array.from((event.target as HTMLSelectElement).selectedOptions)
      .map((o) => o.value)
      .join(','),
  )
}
</script>
<template>
  <div class="booking-fields">
    <label v-for="field in fields" :key="field.key" class="booking-field">
      <span>{{ field.label }}{{ field.required ? ' *' : '' }}</span>
      <select
        v-if="field.type === 'multi'"
        multiple
        :value="(values[field.key] || '').split(',')"
        :required="field.required"
        :disabled="disabled"
        @change="multi(field.key, $event)"
      >
        <option v-for="option in field.options" :key="option.value" :value="option.value">
          {{ option.label }}
        </option>
      </select>
      <select
        v-else-if="field.type === 'select'"
        :value="values[field.key] || ''"
        :required="field.required"
        :disabled="disabled"
        @change="change(field.key, $event)"
      >
        <option value="">请选择</option>
        <option v-for="option in field.options" :key="option.value" :value="option.value">
          {{ option.label }}
        </option>
      </select>
      <textarea
        v-else-if="field.type === 'textarea'"
        :value="values[field.key] || ''"
        :maxlength="field.max"
        :required="field.required"
        :disabled="disabled"
        rows="3"
        @input="change(field.key, $event)"
      />
      <input
        v-else
        :type="field.type || 'text'"
        :value="values[field.key] || ''"
        :required="field.required"
        :disabled="disabled"
        :min="field.min"
        :max="field.type === 'number' ? field.max : undefined"
        :maxlength="field.type !== 'number' ? field.max : undefined"
        :step="field.type === 'number' ? 1 : undefined"
        @input="change(field.key, $event)"
      />
    </label>
  </div>
</template>
