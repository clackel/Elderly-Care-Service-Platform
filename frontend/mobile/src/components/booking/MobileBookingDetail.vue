<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  statusLabels,
  actionLabels,
  time,
  price,
  specificLines,
  type BookingDetail,
  type BookingEvent,
  type BookingRules,
} from '../../api/bookings'
const props = defineProps<{
  detail: BookingDetail
  history: BookingEvent[]
  historyPage: number
  historyTotal: number
  staff: boolean
  canBook: boolean
  rules: BookingRules
  busy: boolean
}>()
const emit = defineEmits<{
  back: []
  action: [name: string, note: string]
  history: [page: number]
  refresh: []
}>()
const note = ref('')
const canCancel = computed(
  () =>
    !props.staff &&
    props.canBook &&
    (props.detail.status === 'PENDING' ||
      (props.detail.status === 'CONFIRMED' &&
        !!props.detail.scheduledStart &&
        Date.now() <
          new Date(props.detail.scheduledStart).getTime() -
            props.rules.cancelBeforeMinutes * 60000)),
)
const actions = computed(() => {
  const items: { key: string; label: string }[] = []
  if (canCancel.value) items.push({ key: 'cancel', label: '取消预约' })
  if (props.staff && props.detail.status === 'CONFIRMED')
    items.push({ key: 'start', label: '开始服务' })
  if (props.staff && props.detail.status === 'IN_PROGRESS' && !props.detail.hasException)
    items.push({ key: 'complete', label: '提交结果并完成服务' })
  if (props.staff && ['CONFIRMED', 'IN_PROGRESS'].includes(props.detail.status))
    items.push({ key: 'exception', label: '报告服务异常' })
  return items
})
/** 取消、完成及异常必须填写原因或结果，服务端按角色和版本再次校验。 */
function action(name: string, label: string) {
  if (name !== 'start' && !note.value.trim()) {
    uni.showToast({ title: '请先填写原因或服务结果', icon: 'none' })
    return
  }
  uni.showModal({
    title: label,
    content: '请核对当前预约。提交后会保留处理记录。',
    success: (result) => {
      if (result.confirm) emit('action', name, note.value.trim() || '已到达并开始服务')
    },
  })
}
</script>
<template>
  <view>
    <view class="mobile-card"
      ><text class="mobile-chip">{{ statusLabels[detail.status] }}</text
      ><text class="card-title">{{ detail.service.name }}</text
      ><text class="body-copy">{{ detail.service.description }}</text>
      <text class="mobile-details"
        >参考价：¥{{ price(detail.service.priceFen) }} ·
        {{ detail.service.durationMinutes }} 分钟\n服务范围：{{
          detail.service.serviceArea
        }}\n期望时间：{{ time(detail.application?.requestedStart) }}\n实际安排：{{
          time(detail.scheduledStart)
        }}\n结束时间：{{ time(detail.scheduledEnd) }}\n服务人员：{{
          detail.workerName || '待安排'
        }}</text
      >
      <template v-if="detail.application"
        ><text class="mobile-details"
          >地址：{{ detail.application.address }}\n联系人：{{
            detail.application.contactName
          }}\n电话：{{ detail.application.contactPhone }}</text
        ><text
          v-for="line in specificLines(detail.service.category, detail.application.specific)"
          :key="line.label"
          class="mobile-details"
          >{{ line.label }}：{{ line.value }}</text
        ><text v-if="detail.application.remark" class="mobile-details"
          >备注：{{ detail.application.remark }}</text
        ></template
      >
      <text v-if="detail.result" class="mobile-details">服务结果：{{ detail.result }}</text>
      <view v-if="detail.hasException" class="notice-card"
        >服务有异常，正在等待社区处理，尚未完成。</view
      >
      <view v-if="!staff && detail.status === 'CONFIRMED'" class="notice-card"
        >用户取消截止时间：{{
          time(
            detail.scheduledStart
              ? new Date(
                  new Date(detail.scheduledStart).getTime() - rules.cancelBeforeMinutes * 60000,
                ).toISOString()
              : null,
          )
        }}。超时请联系社区协调。</view
      >
      <view v-if="actions.length" class="mobile-form-field"
        ><text>原因、必要结果或异常说明</text
        ><textarea
          v-model="note"
          class="mobile-control mobile-textarea"
          :disabled="busy"
          maxlength="1000"
          placeholder="只填写本次服务所需的信息"
        />
      </view>
      <view class="mobile-buttons"
        ><button
          v-for="item in actions"
          :key="item.key"
          :disabled="busy"
          @click="action(item.key, item.label)"
        >
          {{ item.label }}</button
        ><button :disabled="busy" @click="emit('refresh')">刷新进度</button
        ><button :disabled="busy" @click="emit('back')">
          {{ staff ? '返回我的任务' : '返回我的预约' }}
        </button></view
      >
    </view>
    <view class="mobile-card"
      ><text class="card-title">处理进度</text
      ><view v-for="event in history" :key="event.id" class="mobile-record"
        ><text class="card-title">{{ actionLabels[event.action] || event.action }}</text
        ><text class="body-copy"
          >{{ time(event.occurredAt) }} · {{ statusLabels[event.toStatus] }}</text
        ><text class="mobile-details">{{ event.note }}</text></view
      ><view class="mobile-buttons"
        ><button :disabled="busy || historyPage <= 1" @click="emit('history', historyPage - 1)">
          较新记录</button
        ><text>第 {{ historyPage }} 页，共 {{ historyTotal }} 条</text
        ><button
          :disabled="busy || historyPage * 10 >= historyTotal"
          @click="emit('history', historyPage + 1)"
        >
          较早记录
        </button></view
      ></view
    >
  </view>
</template>
