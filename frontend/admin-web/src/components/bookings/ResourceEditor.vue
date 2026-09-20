<script setup lang="ts">
import { ref } from 'vue'
import type { CatalogResource, Field, FormValues } from '../../api/bookings'
import { resourceDraft } from './resourceModel'
import BookingFields from './BookingFields.vue'
const props = defineProps<{
  title: string
  fields: Field[]
  initial: CatalogResource | null
  busy: boolean
  uncertain: boolean
}>()
const emit = defineEmits<{ submit: [values: FormValues]; close: [] }>()
const values = ref(resourceDraft(props.initial))
/** 只修改本次编辑草稿，由父容器负责持久化。 */
function change(key: string, value: string) {
  values.value[key] = value
}
/** 关闭前确认丢弃内存草稿，提交中不能关闭。 */
function close() {
  if (!props.busy && window.confirm('确定关闭并清除草稿吗？结果不确定时，请先重试或刷新列表核实。'))
    emit('close')
}
</script>
<template>
  <section class="booking-panel">
    <div class="booking-toolbar">
      <h2>{{ title }}</h2>
      <button class="booking-link" :disabled="busy" @click="close">关闭</button>
    </div>
    <p v-if="uncertain" class="booking-warning">
      请求结果尚未确认，重试将使用原内容。请勿重复新建。
    </p>
    <form @submit.prevent="emit('submit', { ...values })">
      <BookingFields
        :fields="fields"
        :values="values"
        :disabled="busy || uncertain"
        @change="change"
      />
      <div class="booking-actions">
        <button class="booking-primary" type="submit" :disabled="busy">
          {{ busy ? '正在保存…' : uncertain ? '重试原请求' : '保存' }}
        </button>
      </div>
    </form>
  </section>
</template>
