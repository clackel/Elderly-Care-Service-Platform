<script setup lang="ts">
import {
  notificationBookingId,
  notificationTime,
  notificationTitle,
  type Notice,
} from '../../api/notifications'

defineProps<{ rows: Notice[]; unreadOnly: boolean; loading: boolean; writingId: string }>()
const emit = defineEmits<{
  filter: [unreadOnly: boolean]
  read: [notice: Notice]
  open: [notice: Notice]
}>()
</script>

<template>
  <section aria-labelledby="notification-list-title">
    <div class="notification-toolbar">
      <div>
        <h2 id="notification-list-title">我的消息</h2>
        <p class="muted">仅展示当前仍有权限查看的预约变更提醒。</p>
      </div>
      <label class="notification-checkbox"
        ><input
          :checked="unreadOnly"
          type="checkbox"
          :disabled="loading || !!writingId"
          @change="emit('filter', ($event.target as HTMLInputElement).checked)"
        />只看未读</label
      >
    </div>
    <el-empty
      v-if="!loading && !rows.length"
      :description="unreadOnly ? '暂无未读消息' : '暂无消息'"
    />
    <ol v-else class="notification-list">
      <li
        v-for="notice in rows"
        :key="notice.id"
        class="notification-row"
        :class="{ 'notification-unread': !notice.read }"
      >
        <div>
          <div class="notification-title">
            <strong>{{ notificationTitle(notice.action) }}</strong
            ><el-tag v-if="!notice.read" size="small">未读</el-tag
            ><span v-else class="muted notification-small">已读</span>
          </div>
          <time :datetime="notice.occurredAt" class="muted notification-small">{{
            notificationTime(notice.occurredAt)
          }}</time>
          <p v-if="notificationBookingId(notice)" class="muted notification-small">
            预约编号 {{ notificationBookingId(notice) }}
          </p>
        </div>
        <div class="notification-actions">
          <el-button
            v-if="!notice.read"
            :loading="writingId === notice.id"
            :disabled="!!writingId"
            @click="emit('read', notice)"
            >标记已读</el-button
          ><el-button
            v-if="notificationBookingId(notice)"
            type="primary"
            plain
            :disabled="!!writingId"
            @click="emit('open', notice)"
            >查看预约</el-button
          >
        </div>
      </li>
    </ol>
  </section>
</template>
