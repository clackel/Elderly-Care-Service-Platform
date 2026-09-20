<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useSessionStore } from '../../stores/session'
import {
  grants,
  grant,
  revokeGrant,
  eligibleElders,
  time,
  localTime,
  type Grant,
  type ElderOption,
  type Field,
  type FormValues,
} from '../../api/bookings'
import MobileFields from './MobileFields.vue'
const session = useSessionStore(),
  rows = ref<Grant[]>([]),
  elders = ref<ElderOption[]>([]),
  page = ref(1),
  total = ref(0),
  busy = ref(false),
  error = ref(''),
  editing = ref(false)
const draft = ref<FormValues>({}),
  version = ref(0)
let alive = true
const fields = computed<Field[]>(() => [
  {
    key: 'elderId',
    label: '我的档案',
    type: 'select',
    required: true,
    options: elders.value.map((e) => ({ value: e.id, label: e.name })),
  },
  { key: 'familyId', label: '家属账号编号（请向家属获取）', required: true, max: 19 },
  {
    key: 'canBook',
    label: '授权范围',
    type: 'select',
    required: true,
    options: [
      { value: 'false', label: '仅查看预约进度' },
      { value: 'true', label: '查看、代预约和取消预约' },
    ],
  },
  { key: 'expiresAt', label: '授权截止时间（一年以内）', type: 'datetime-local', required: true },
])
/** 读取当前账号可管理的授权，失败时清除旧结果。 */
async function load() {
  busy.value = true
  error.value = ''
  rows.value = []
  try {
    const [data, options] = await Promise.all([
      grants(page.value),
      session.account?.role === 'ELDER' ? eligibleElders() : Promise.resolve([]),
    ])
    if (alive) {
      rows.value = data.items
      total.value = data.total
      elders.value = options
    }
  } catch (e) {
    if (alive) error.value = e instanceof Error ? e.message : '授权读取失败'
  } finally {
    if (alive) busy.value = false
  }
}
/** 创建或续期时使用独立草稿，并携带已知版本。 */
function edit(row?: Grant) {
  version.value = row?.version || 0
  draft.value = {
    elderId: row?.elderId || elders.value[0]?.id || '',
    familyId: row?.familyId || '',
    canBook: String(row?.canBook || false),
    expiresAt: localTime(row?.expiresAt),
  }
  editing.value = true
}
/** 更新本人授权草稿。 */
function change(key: string, value: string) {
  draft.value[key] = value
}
/** 授权前明确告知范围，仅老人本人可提交，家属不能自行授予权限。 */
function save() {
  const v = draft.value
  if (!v.elderId || !v.familyId || !v.expiresAt || !v.canBook) {
    error.value = '请填写档案、家属账号、范围和截止时间'
    return
  }
  uni.showModal({
    title: '确认预约授权',
    content:
      '我同意该家属在有效期内' +
      (v.canBook === 'true' ? '查看、代我预约及取消预约。' : '查看我的预约进度。') +
      '此授权不包含健康档案，我可随时撤销。',
    success: (result) => {
      if (result.confirm) void submit()
    },
  })
}
/** 保存明确同意后的授权，冲突时保留错误并刷新记录。 */
async function submit() {
  busy.value = true
  error.value = ''
  try {
    await grant({
      elderId: draft.value.elderId || '',
      familyId: draft.value.familyId || '',
      canBook: draft.value.canBook === 'true',
      expiresAt: new Date(draft.value.expiresAt || '').toISOString(),
      version: version.value,
    })
    if (alive) {
      editing.value = false
      await load()
    }
  } catch (e) {
    if (alive)
      error.value = e instanceof Error ? e.message + '；请刷新授权记录后重试' : '授权保存失败'
  } finally {
    if (alive) busy.value = false
  }
}
/** 撤销前确认结果，由后端按版本即时收回权限。 */
function revoke(row: Grant) {
  uni.showModal({
    title: '撤销预约授权',
    content: '撤销后家属将不能查看或代办该老人的预约，已有预约仍由社区处理。',
    success: (result) => {
      if (result.confirm) void remove(row)
    },
  })
}
/** 服务端确认撤销后刷新列表，不将失败视为成功。 */
async function remove(row: Grant) {
  busy.value = true
  error.value = ''
  try {
    await revokeGrant(row.id, row.version)
    if (alive) await load()
  } catch (e) {
    if (alive) error.value = e instanceof Error ? e.message : '撤销失败'
  } finally {
    if (alive) busy.value = false
  }
}
/** 切换授权列表页。 */
function paginate(value: number) {
  page.value = value
  void load()
}
onMounted(load)
onBeforeUnmount(() => {
  alive = false
  draft.value = {}
  rows.value = []
  elders.value = []
})
</script>
<template>
  <view class="mobile-card"
    ><text class="card-title">{{
      session.account?.role === 'ELDER' ? '家属预约授权' : '收到的预约授权'
    }}</text
    ><text class="body-copy">预约授权与健康数据授权相互独立。</text
    ><text v-if="error" class="mobile-error">{{ error }}</text>
    <view v-if="editing"
      ><MobileFields :fields="fields" :values="draft" :disabled="busy" @change="change" /><view
        class="mobile-buttons"
        ><button class="feature-button" :disabled="busy" @click="save">核对并同意授权</button
        ><button :disabled="busy" @click="editing = false">关闭</button></view
      ></view
    >
    <view v-else class="mobile-buttons"
      ><button
        v-if="session.account?.role === 'ELDER'"
        :disabled="busy || !elders.length"
        @click="edit()"
      >
        新增预约授权</button
      ><button :disabled="busy" @click="load">刷新授权</button></view
    >
    <view v-for="row in rows" :key="row.id" class="mobile-record"
      ><text class="body-copy">老人档案：{{ row.elderId }}\n家属账号：{{ row.familyId }}</text
      ><text class="mobile-details"
        >{{ row.canBook ? '查看及代预约' : '仅查看预约' }}\n截止：{{
          time(row.expiresAt)
        }}\n状态：{{
          row.revoked
            ? '已撤销'
            : new Date(row.expiresAt).getTime() <= Date.now()
              ? '已过期'
              : '有效'
        }}</text
      ><view class="mobile-buttons"
        ><button v-if="session.account?.role === 'ELDER'" :disabled="busy" @click="edit(row)">
          调整 / 续期</button
        ><button v-if="!row.revoked" :disabled="busy" @click="revoke(row)">撤销授权</button></view
      ></view
    >
    <text v-if="!rows.length && !busy" class="body-copy">暂无授权记录。</text
    ><view class="mobile-buttons"
      ><button :disabled="busy || page <= 1" @click="paginate(page - 1)">上一页</button
      ><text>第 {{ page }} 页，共 {{ total }} 条</text
      ><button :disabled="busy || page * 10 >= total" @click="paginate(page + 1)">
        下一页
      </button></view
    >
  </view>
</template>
