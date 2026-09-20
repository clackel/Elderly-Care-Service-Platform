<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  choices,
  specificFields,
  makeSpecific,
  newRequestId,
  price,
  type Catalog,
  type ElderOption,
  type BookingRules,
  type CreateBooking,
  type Field,
  type FormValues,
} from '../../api/bookings'
import MobileFields from './MobileFields.vue'
const props = defineProps<{
  service: Catalog
  elders: ElderOption[]
  rules: BookingRules
  busy: boolean
  uncertain: boolean
}>()
const emit = defineEmits<{ submit: [input: CreateBooking]; close: [] }>()
const draft = ref<FormValues>({}),
  error = ref(''),
  requestId = newRequestId()
let attempt: CreateBooking | null = null
const fields = computed<Field[]>(() => [
  {
    key: 'elderId',
    label: '为谁预约',
    type: 'select',
    required: true,
    options: props.elders.filter((e) => e.canBook).map((e) => ({ value: e.id, label: e.name })),
  },
  { key: 'requestedStart', label: '期望服务时间', type: 'datetime-local', required: true },
  { key: 'address', label: '详细服务地址', required: true, max: 200 },
  { key: 'contactName', label: '联系人', required: true, max: 50 },
  { key: 'contactPhone', label: '联系电话', required: true, max: 15 },
  ...specificFields[props.service.category].map((f) =>
    f.key === 'careContent'
      ? {
          ...f,
          options: choices(
            props.service.professional
              ? { PROFESSIONAL: '专业护理' }
              : { DAILY_LIVING: '日常生活照护', PERSONAL_CARE: '个人照护' },
          ),
        }
      : f,
  ),
  { key: 'remark', label: '必要备注（不要填写病史）', type: 'textarea', max: 300 },
])
/** 切换老人时只使用服务器允许预填的本人资料，家属不读取完整档案。 */
function change(key: string, value: string) {
  draft.value[key] = value
  if (key === 'elderId') {
    const elder = props.elders.find((e) => e.id === value)
    draft.value.address = elder?.address || ''
    draft.value.contactName = elder?.contactName || ''
    draft.value.contactPhone = elder?.contactPhone || ''
  }
}
/** 提交前校验必要输入并冻结申请；结果不确定时始终重试原请求。 */
function submit() {
  error.value = ''
  if (!props.uncertain) {
    for (const field of fields.value) {
      const value = (draft.value[field.key] || '').trim()
      if (field.required && !value) {
        error.value = '请填写' + field.label
        return
      }
      if (
        field.type === 'number' &&
        value &&
        (!Number.isInteger(Number(value)) ||
          Number(value) < (field.min ?? 0) ||
          Number(value) > (field.max ?? Infinity))
      ) {
        error.value = field.label + '超出允许范围'
        return
      }
    }
    if (!/^(1[3-9][0-9]{9}|0[0-9]{2,3}-?[0-9]{7,8})$/.test(draft.value.contactPhone || '')) {
      error.value = '请填写正确的手机或固定电话号码'
      return
    }
    attempt = {
      requestId,
      application: {
        elderId: draft.value.elderId || '',
        serviceId: props.service.id,
        requestedStart: new Date(draft.value.requestedStart || '').toISOString(),
        address: (draft.value.address || '').trim(),
        contactName: (draft.value.contactName || '').trim(),
        contactPhone: (draft.value.contactPhone || '').trim(),
        remark: (draft.value.remark || '').trim(),
        specific: makeSpecific(props.service.category, draft.value),
      },
    }
  }
  if (attempt) emit('submit', attempt)
}
/** 关闭草稿前说明未确认请求需要先核实，避免重复创建。 */
function close() {
  uni.showModal({
    title: '关闭申请',
    content: props.uncertain
      ? '提交结果尚未确认，请优先重试。关闭后请在我的预约核实，避免重复申请。'
      : '关闭后将清除未提交的资料。',
    success: (result) => {
      if (result.confirm) emit('close')
    },
  })
}
if (props.elders.filter((e) => e.canBook).length === 1)
  change('elderId', props.elders.find((e) => e.canBook)!.id)
</script>
<template>
  <view class="mobile-card">
    <text class="card-title">预约：{{ service.name }}</text
    ><text class="body-copy">{{ service.description }}</text>
    <text class="mobile-details"
      >提供方：{{ service.providerName }}\n服务范围：{{ service.serviceArea }}\n时长：{{
        service.durationMinutes
      }}
      分钟\n参考价：¥{{ price(service.priceFen) }}，不在线支付</text
    >
    <view class="notice-card"
      >至少提前 {{ rules.advanceMinutes }} 分钟，最长预约未来
      {{ rules.horizonDays }} 天。提交后请等待社区确认。</view
    >
    <MobileFields :fields="fields" :values="draft" :disabled="busy || uncertain" @change="change" />
    <text v-if="error" class="mobile-error">{{ error }}</text>
    <view v-if="uncertain" class="notice-card"
      >提交结果尚未确认，资料已锁定。重试会沿用原编号，不重复创建预约。</view
    >
    <view class="mobile-buttons"
      ><button
        class="feature-button"
        :disabled="busy || !elders.some((e) => e.canBook)"
        @click="submit"
      >
        {{ busy ? '正在提交…' : uncertain ? '重试并确认结果' : '提交申请，等待社区确认' }}</button
      ><button :disabled="busy" @click="close">返回服务列表</button></view
    >
  </view>
</template>
