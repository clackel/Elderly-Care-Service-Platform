<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  statusLabels,
  actionLabels,
  time,
  localTime,
  price,
  specificLines,
  type BookingDetail,
  type BookingEvent,
  type Worker,
  type Field,
  type FormValues,
} from '../../api/bookings'
import BookingFields from './BookingFields.vue'
const props = defineProps<{
  detail: BookingDetail
  history: BookingEvent[]
  historyPage: number
  historyTotal: number
  workers: Worker[]
  busy: boolean
}>()
const emit = defineEmits<{
  close: []
  action: [action: string, note: string]
  arrange: [workerId: string, start: string, note: string]
  history: [page: number]
}>()
const draft = ref<FormValues>({
  start: localTime(props.detail.scheduledStart || props.detail.application?.requestedStart),
  workerId: props.detail.workerId || '',
  coordinationNote: '',
})
const note = ref(''),
  action = ref(
    props.detail.status === 'PENDING'
      ? 'reject'
      : props.detail.status === 'CONFIRMED'
        ? 'cancel'
        : props.detail.hasException
          ? 'resolve'
          : 'terminate',
  )
const canArrange = computed(() => ['PENDING', 'CONFIRMED'].includes(props.detail.status))
const availableWorkers = computed(() =>
  props.workers.filter(
    (w) =>
      w.enabled &&
      w.providerId === props.detail.service.providerId &&
      w.serviceArea === props.detail.service.serviceArea &&
      w.categories.includes(props.detail.service.category),
  ),
)
const arrangeFields = computed<Field[]>(() => [
  {
    key: 'workerId',
    label: '服务人员',
    type: 'select',
    required: true,
    options: availableWorkers.value.map((w) => ({
      value: w.id,
      label: w.name + ' · ' + (w.qualification || '生活服务'),
    })),
  },
  { key: 'start', label: '实际开始时间', type: 'datetime-local', required: true },
  {
    key: 'coordinationNote',
    label: '与申请人的协调结果（改期、改派必填）',
    type: 'textarea',
    max: 500,
  },
])
const actions = computed(() => {
  const result: { value: string; label: string }[] = []
  if (props.detail.status === 'PENDING')
    result.push({ value: 'reject', label: '拒绝申请' }, { value: 'cancel', label: '取消预约' })
  if (props.detail.status === 'CONFIRMED') result.push({ value: 'cancel', label: '取消预约' })
  if (['CONFIRMED', 'IN_PROGRESS'].includes(props.detail.status)) {
    if (props.detail.hasException) result.push({ value: 'resolve', label: '记录异常处理结果' })
    result.push({ value: 'terminate', label: '终止服务' })
  }
  return result
})
const selectedAction = computed(
  () => actions.value.find((a) => a.value === action.value)?.value || actions.value[0]?.value || '',
)
/** 替换草稿字段，安排提交使用详情当前版本。 */
function change(key: string, value: string) {
  draft.value[key] = value
}
/** 提交明确的人员与时间，结束时间由快照时长计算。 */
function arrange() {
  emit(
    'arrange',
    draft.value.workerId || '',
    draft.value.start || '',
    draft.value.coordinationNote || '',
  )
}
/** 拒绝、取消或终止必须明确原因，并由操作者确认最终动作。 */
function submitAction() {
  const label = actions.value.find((a) => a.value === selectedAction.value)?.label
  if (window.confirm('确定' + label + '吗？'))
    emit('action', selectedAction.value, note.value.trim())
}
</script>
<template>
  <section class="booking-panel">
    <div class="booking-toolbar">
      <div>
        <h2>{{ detail.service.name }}</h2>
        <p class="booking-muted">预约编号 {{ detail.id }} · {{ statusLabels[detail.status] }}</p>
      </div>
      <button class="booking-link" :disabled="busy" @click="emit('close')">关闭详情</button>
    </div>
    <dl class="booking-facts">
      <div>
        <dt>服务内容（提交时快照）</dt>
        <dd>{{ detail.service.description }}</dd>
      </div>
      <div>
        <dt>提供方与范围</dt>
        <dd>{{ detail.service.providerName }} · {{ detail.service.serviceArea }}</dd>
      </div>
      <div>
        <dt>时长与参考价</dt>
        <dd>{{ detail.service.durationMinutes }} 分钟 · ¥{{ price(detail.service.priceFen) }}</dd>
      </div>
      <div>
        <dt>老人档案编号</dt>
        <dd>{{ detail.elderId }}</dd>
      </div>
      <div>
        <dt>期望开始</dt>
        <dd>{{ time(detail.application?.requestedStart) }}</dd>
      </div>
      <div>
        <dt>实际服务时段</dt>
        <dd>{{ time(detail.scheduledStart) }} 至 {{ time(detail.scheduledEnd) }}</dd>
      </div>
      <div>
        <dt>安排人员</dt>
        <dd>{{ detail.workerName || '待安排' }}</dd>
      </div>
      <template v-if="detail.application"
        ><div>
          <dt>服务地址</dt>
          <dd>{{ detail.application.address }}</dd>
        </div>
        <div>
          <dt>联系人</dt>
          <dd>{{ detail.application.contactName }} · {{ detail.application.contactPhone }}</dd>
        </div>
        <div>
          <dt>备注</dt>
          <dd>{{ detail.application.remark || '无' }}</dd>
        </div>
        <div
          v-for="line in specificLines(detail.service.category, detail.application.specific)"
          :key="line.label"
        >
          <dt>{{ line.label }}</dt>
          <dd>{{ line.value }}</dd>
        </div></template
      >
      <div v-if="detail.result">
        <dt>服务结果 / 终止原因</dt>
        <dd>{{ detail.result }}</dd>
      </div>
    </dl>
    <p v-if="detail.hasException" class="booking-warning">
      服务人员已报告异常。请协调处理并记录结果；不能继续服务时选择终止。
    </p>
    <form v-if="canArrange" @submit.prevent="arrange">
      <hr class="booking-divider" />
      <h3>{{ detail.status === 'PENDING' ? '确认与安排' : '调整安排' }}</h3>
      <p class="booking-muted">
        结束时间按
        {{ detail.service.durationMinutes }} 分钟计算。更改时间或人员须记录与申请人的协调结果。
      </p>
      <BookingFields :fields="arrangeFields" :values="draft" :disabled="busy" @change="change" />
      <div class="booking-actions">
        <button class="booking-primary" :disabled="busy || !availableWorkers.length" type="submit">
          保存安排
        </button>
      </div>
    </form>
    <form v-if="actions.length" @submit.prevent="submitAction">
      <hr class="booking-divider" />
      <h3>处理预约</h3>
      <label class="booking-field"
        >操作<select v-model="action" :disabled="busy">
          <option v-for="item in actions" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select></label
      ><label class="booking-field"
        >原因或处理结果<textarea
          v-model="note"
          required
          maxlength="1000"
          :disabled="busy"
          rows="3"
        />
      </label>
      <div class="booking-actions">
        <button type="submit" :disabled="busy || !note.trim()">提交处理</button>
      </div>
    </form>
    <hr class="booking-divider" />
    <h3>处理记录</h3>
    <ol class="booking-timeline">
      <li v-for="event in history" :key="event.id">
        <strong>{{ actionLabels[event.action] || event.action }}</strong> ·
        {{ time(event.occurredAt) }}
        <div class="booking-muted">
          操作者 {{ event.actorId }} · {{ statusLabels[event.toStatus] }}
        </div>
        <div>{{ event.note }}</div>
      </li>
    </ol>
    <div class="booking-actions">
      <button :disabled="busy || historyPage <= 1" @click="emit('history', historyPage - 1)">
        较新记录</button
      ><span>第 {{ historyPage }} 页，共 {{ historyTotal }} 条</span
      ><button
        :disabled="busy || historyPage * 10 >= historyTotal"
        @click="emit('history', historyPage + 1)"
      >
        较早记录
      </button>
    </div>
  </section>
</template>
