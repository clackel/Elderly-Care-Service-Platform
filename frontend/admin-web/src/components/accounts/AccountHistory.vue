<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { ElDialog } from 'element-plus'
import 'element-plus/es/components/dialog/style/css'
import { listAccountEvents, type AccountEvent, type AdminAccount } from '../../api/adminAccounts'
import { errorText, formatTime } from '../elders/elderModel'

const props = defineProps<{ account: AdminAccount }>()
const emit = defineEmits<{ close: [] }>()
const page = shallowRef(1)
const retry = shallowRef(0)
const rows = shallowRef<AccountEvent[]>([])
const total = shallowRef(0)
const loading = shallowRef(false)
const error = shallowRef('')
const pages = computed(() => Math.max(1, Math.ceil(total.value / 10)))
const actions: Record<string, string> = {
  CREATED: '创建账号',
  PROFILE_UPDATED: '修改显示名',
  ROLE_CHANGED: '调整角色',
  DISABLED: '停用账号',
  ENABLED: '启用账号',
  PASSWORD_RESET: '管理员重置密码',
  PASSWORD_CHANGED: '本人修改密码',
  ASSISTANCE_REVOKED: '撤销健康协助授权',
  BOOTSTRAPPED: '初始化管理员',
}

// 每次分页或重试先清除旧记录，关闭面板时取消读取，避免展示过期数据。
watch(
  () => [props.account.id, page.value, retry.value],
  async (_, __, onCleanup) => {
    const controller = new AbortController()
    onCleanup(() => controller.abort())
    rows.value = []
    total.value = 0
    loading.value = true
    error.value = ''
    try {
      const result = await listAccountEvents(props.account.id, page.value, controller.signal)
      if (controller.signal.aborted) return
      rows.value = result.items
      total.value = result.total
    } catch (cause) {
      if (!controller.signal.aborted) error.value = errorText(cause)
    } finally {
      if (!controller.signal.aborted) loading.value = false
    }
  },
  { immediate: true },
)
</script>

<template>
  <ElDialog
    :model-value="true"
    title="账号操作记录"
    width="min(640px, calc(100vw - 24px))"
    :before-close="() => emit('close')"
  >
    <p>{{ account.displayName }} · {{ account.username }}</p>
    <section :aria-busy="loading">
      <p v-if="loading" role="status">正在读取操作记录…</p>
      <div v-else-if="error" role="alert">
        <p>{{ error }}</p>
        <el-button @click="retry++">重新加载</el-button>
      </div>
      <p v-else-if="!rows.length" class="account-help">暂无操作记录</p>
      <ol v-else class="account-history">
        <li v-for="event in rows" :key="event.id">
          <strong>{{ actions[event.action] ?? '账号操作' }}</strong
          ><time :datetime="event.occurredAt">{{ formatTime(event.occurredAt) }}</time>
          <p class="account-help">操作账号：{{ event.actorId }}</p>
        </li>
      </ol>
      <div v-if="!error && total > 10" class="account-pagination">
        <el-button :disabled="loading || page <= 1" @click="page--">较新记录</el-button
        ><span>{{ page }} / {{ pages }}</span
        ><el-button :disabled="loading || page >= pages" @click="page++">较早记录</el-button>
      </div>
    </section>
  </ElDialog>
</template>
