<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  categoryLabels,
  specificFields,
  makeSpecific,
  choices,
  price,
  type Catalog,
  type ElderOption,
  type BookingRules,
  type CreateBooking,
  type FormValues,
  type Field,
} from '../../api/bookings'
import BookingFields from './BookingFields.vue'
const props = defineProps<{
  services: Catalog[]
  elders: ElderOption[]
  rules: BookingRules | null
  busy: boolean
  uncertain: boolean
}>()
const emit = defineEmits<{ submit: [input: CreateBooking]; close: [] }>()
const draft = ref<FormValues>({}),
  requestId = crypto.randomUUID()
let attempt: CreateBooking | null = null
const selected = computed(() => props.services.find((s) => s.id === draft.value.serviceId))
const fields = computed<Field[]>(() => [
  {
    key: 'elderId',
    label: '预约老人',
    type: 'select',
    required: true,
    options: props.elders
      .filter((e) => e.canBook)
      .map((e) => ({ value: e.id, label: e.name + ' · ' + e.id })),
  },
  {
    key: 'serviceId',
    label: '服务项目',
    type: 'select',
    required: true,
    options: props.services.map((s) => ({
      value: s.id,
      label: categoryLabels[s.category] + ' · ' + s.name,
    })),
  },
  { key: 'requestedStart', label: '期望开始时间', type: 'datetime-local', required: true },
  { key: 'address', label: '详细服务地址', required: true, max: 200 },
  { key: 'contactName', label: '联系人', required: true, max: 50 },
  { key: 'contactPhone', label: '联系电话', required: true, max: 15 },
  { key: 'remark', label: '必要备注（请勿填写病史）', type: 'textarea', max: 300 },
])
const categoryFields = computed(() => {
  if (!selected.value) return []
  return specificFields[selected.value.category].map((f) =>
    f.key === 'careContent'
      ? {
          ...f,
          options: choices(
            selected.value?.professional
              ? { PROFESSIONAL: '专业护理' }
              : { DAILY_LIVING: '日常生活照护', PERSONAL_CARE: '个人照护' },
          ),
        }
      : f,
  )
})
/** 更新草稿并在更换服务时清除不相干的类别资料。 */
function change(key: string, value: string) {
  if (key === 'serviceId') {
    const keep: FormValues = {}
    for (const f of fields.value) keep[f.key] = draft.value[f.key] || ''
    draft.value = keep
  }
  draft.value[key] = value
}
/** 将当前表单快照冻结为幂等请求；不确定结果时只重试原请求。 */
function submit() {
  if (!selected.value) return
  if (!props.uncertain)
    attempt = {
      requestId,
      application: {
        elderId: draft.value.elderId || '',
        serviceId: selected.value.id,
        requestedStart: new Date(draft.value.requestedStart || '').toISOString(),
        address: (draft.value.address || '').trim(),
        contactName: (draft.value.contactName || '').trim(),
        contactPhone: (draft.value.contactPhone || '').trim(),
        remark: (draft.value.remark || '').trim(),
        specific: makeSpecific(selected.value.category, draft.value),
      },
    }
  if (attempt) emit('submit', attempt)
}
/** 关闭有内容或提交结果待确认的申请前提醒，避免误建重复预约。 */
function close() {
  if (props.busy) return
  if (
    window.confirm(
      props.uncertain
        ? '提交结果尚未确认，建议先重试原请求。仍要关闭并到预约列表核实吗？'
        : '关闭后将清除未提交的申请，确定关闭吗？',
    )
  )
    emit('close')
}
</script>
<template>
  <section class="booking-panel">
    <div class="booking-toolbar">
      <h2>代老人预约</h2>
      <button class="booking-link" :disabled="busy" @click="close">关闭</button>
    </div>
    <p class="booking-warning">提交后为“待社区确认”，社区明确时间和人员后才完成安排。</p>
    <p v-if="rules" class="booking-muted">
      至少提前 {{ rules.advanceMinutes }} 分钟，可预约未来
      {{ rules.horizonDays }} 天。已确认预约的用户取消须提前 {{ rules.cancelBeforeMinutes }} 分钟。
    </p>
    <p v-if="!services.length || !elders.some((e) => e.canBook)" class="booking-warning">
      请先准备已上架服务及在管老人档案，再提交申请。
    </p>
    <form @submit.prevent="submit">
      <BookingFields
        :fields="fields"
        :values="draft"
        :disabled="busy || uncertain"
        @change="change"
      />
      <div v-if="selected" class="booking-warning">
        {{ selected.description }}<br />服务范围：{{ selected.serviceArea }} ·
        {{ selected.durationMinutes }} 分钟 · 参考价 ¥{{ price(selected.priceFen) }}（不产生支付）
      </div>
      <BookingFields
        :fields="categoryFields"
        :values="draft"
        :disabled="busy || uncertain"
        @change="change"
      />
      <p v-if="uncertain" class="booking-warning">
        尚未确认提交结果，资料已锁定。点击重试会沿用原请求编号，不会重复创建。
      </p>
      <div class="booking-actions">
        <button class="booking-primary" :disabled="busy || !selected" type="submit">
          {{ busy ? '正在提交…' : uncertain ? '重试并确认结果' : '提交预约申请' }}
        </button>
      </div>
    </form>
  </section>
</template>
