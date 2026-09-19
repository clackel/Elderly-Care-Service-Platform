<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { getElderHistory, type ElderHistory } from '../../api/elders'
import { errorText, fieldLabels, formatTime } from './elderModel'

const props = defineProps<{ id: string; version: number }>()
const page = shallowRef(1)
const retry = shallowRef(0)
const rows = shallowRef<ElderHistory[]>([])
const total = shallowRef(0)
const loading = shallowRef(true)
const error = shallowRef('')
const pages = computed(() => Math.max(1, Math.ceil(total.value / 5)))
const actions: Record<ElderHistory['action'], string> = {
  CREATE: '建立档案',
  UPDATE: '更新资料',
  ARCHIVE: '归档',
  RESTORE: '恢复在册',
}

// 切换档案或完成写入后回到最新记录，不保留上个对象的历史分页。
watch(
  () => [props.id, props.version],
  () => {
    page.value = 1
  },
  { flush: 'sync' },
)
// 每次读取注册取消函数，切换分页或卸载时使旧响应失效。
watch(
  () => [props.id, props.version, page.value, retry.value],
  async (_, __, onCleanup) => {
    const controller = new AbortController()
    onCleanup(() => controller.abort())
    rows.value = []
    loading.value = true
    error.value = ''
    try {
      const result = await getElderHistory(props.id, page.value, controller.signal)
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

/** 将后端审计字段转成中文标签，未知字段仅显示通用名称。 */
function describeFields(fields: string[]): string {
  return fields
    .map((field) =>
      Object.hasOwn(fieldLabels, field)
        ? fieldLabels[field as keyof typeof fieldLabels]
        : '其他资料',
    )
    .join('、')
}
</script>

<template>
  <section class="elder-history" aria-labelledby="elder-history-title" :aria-busy="loading">
    <h3 id="elder-history-title">变更记录</h3>
    <p class="elder-help">记录操作和变更字段，不展示历史个人资料正文。</p>
    <p v-if="loading" role="status">正在读取变更记录…</p>
    <div v-else-if="error" role="alert">
      <p>{{ error }}</p>
      <el-button @click="retry++">重试变更记录</el-button>
    </div>
    <p v-else-if="!rows.length" class="elder-help">暂无变更记录</p>
    <ol v-else class="elder-timeline">
      <li v-for="entry in rows" :key="entry.id">
        <div class="elder-history-heading">
          <strong>{{ actions[entry.action] }}</strong
          ><time :datetime="entry.occurredAt">{{ formatTime(entry.occurredAt) }}</time>
        </div>
        <p v-if="entry.changedFields.length">{{ describeFields(entry.changedFields) }}</p>
        <small class="elder-help">操作账号 {{ entry.actorId }} · 版本 {{ entry.version }}</small>
      </li>
    </ol>
    <div v-if="!error && total > 5" class="elder-history-pages">
      <el-button :disabled="loading || page === 1" @click="page--">较新记录</el-button
      ><span>{{ page }} / {{ pages }}</span
      ><el-button :disabled="loading || page >= pages" @click="page++">较早记录</el-button>
    </div>
  </section>
</template>
