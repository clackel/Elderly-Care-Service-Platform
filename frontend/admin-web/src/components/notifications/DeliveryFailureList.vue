<script setup lang="ts">
import { notificationTime, notificationTitle, type DeliveryFailure } from '../../api/notifications'

defineProps<{ rows: DeliveryFailure[]; loading: boolean; writingId: string; pendingId: string }>()
const emit = defineEmits<{ retry: [failure: DeliveryFailure] }>()
</script>

<template>
  <section aria-labelledby="notification-delivery-title">
    <div class="notification-toolbar"><div><h2 id="notification-delivery-title">发送异常</h2><p class="muted">本社区暂未完成发送的消息。重新发送只处理消息，不会重复执行预约操作。</p></div></div>
    <el-empty v-if="!loading && !rows.length" description="暂无待处理的发送异常" />
    <ol v-else class="notification-list">
      <li v-for="failure in rows" :key="failure.eventId" class="notification-row">
        <div><div class="notification-title"><strong>{{ notificationTitle(failure.action) }}</strong><el-tag type="warning">{{ failure.attempts ? '发送失败' : '等待重新发送' }}</el-tag></div><time :datetime="failure.occurredAt" class="muted notification-small">{{ notificationTime(failure.occurredAt) }}</time><p class="muted notification-small">已尝试 {{ failure.attempts }} 次，请重新发送后稍后刷新查看结果。</p></div>
        <el-button :loading="writingId === failure.eventId" :disabled="!!writingId || (!!pendingId && pendingId !== failure.eventId)" @click="emit('retry', failure)">{{ pendingId === failure.eventId ? '重试原申请' : '申请重新发送' }}</el-button>
      </li>
    </ol>
  </section>
</template>
