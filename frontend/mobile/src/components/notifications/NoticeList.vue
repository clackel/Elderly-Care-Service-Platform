<script setup lang="ts">
import {
  notificationTitle,
  notificationTime,
  notificationBookingId,
  type Notice,
} from '../../api/notifications'
defineProps<{ rows: Notice[]; disabled: boolean }>()
const emit = defineEmits<{ read: [notice: Notice]; open: [notice: Notice] }>()
</script>

<template>
  <view>
    <view v-for="notice in rows" :key="notice.id" class="mobile-card">
      <text class="card-title">{{ notificationTitle(notice.action) }}</text>
      <text class="body-copy"
        >{{ notificationTime(notice.occurredAt) }} · {{ notice.read ? '已读' : '未读' }}</text
      >
      <view class="notice-actions">
        <button v-if="!notice.read" :disabled="disabled" @click="emit('read', notice)">
          标为已读
        </button>
        <button
          v-if="notificationBookingId(notice)"
          :disabled="disabled"
          class="feature-button"
          @click="emit('open', notice)"
        >
          查看预约
        </button>
        <text v-else class="body-copy">{{
          notice.action === 'TASK_TRANSFERRED'
            ? '这项任务已移交，不再提供详情。'
            : '相关预约当前不可打开。'
        }}</text>
      </view>
    </view>
  </view>
</template>

<style scoped>
.notice-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 18px;
}
.notice-actions button {
  width: 100%;
}
</style>
