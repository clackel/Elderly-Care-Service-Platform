<script setup lang="ts">
import { reactive } from 'vue'
import NotificationControls from './NotificationControls.vue'
import NoticeList from './NoticeList.vue'
import TodoList from './TodoList.vue'
import { useMobileNotifications } from './useMobileNotifications'
const messages = reactive(useMobileNotifications())
</script>

<template>
  <view>
    <NotificationControls
      :staff="messages.staff"
      :mode="messages.mode"
      :unread-only="messages.unreadOnly"
      :type="messages.type"
      :unread="messages.unread"
      :disabled="messages.busy || messages.navigating"
      @mode="messages.switchMode"
      @unread="messages.filterUnread"
      @type="messages.filterType"
      @refresh="messages.load"
    />
    <text v-if="messages.error" class="notice-error">{{ messages.error }}</text>
    <text v-if="messages.busy" class="body-copy">正在读取，请稍候…</text>
    <template v-else-if="!messages.error">
      <NoticeList
        v-if="messages.mode === 'notices'"
        :rows="messages.notices"
        :disabled="messages.navigating"
        @read="messages.markRead"
        @open="messages.openNotice"
      />
      <TodoList
        v-else
        :rows="messages.todos"
        :disabled="messages.navigating"
        @open="messages.openBooking"
      />
      <text v-if="messages.total === 0" class="body-copy">{{
        messages.mode === 'notices' ? '暂无符合条件的消息。' : '暂无符合条件的待办。'
      }}</text>
      <view v-if="messages.total > 0" class="notice-pagination">
        <button
          :disabled="messages.page <= 1 || messages.navigating"
          @click="messages.paginate(messages.page - 1)"
        >
          上一页
        </button>
        <text class="body-copy">{{ messages.page }} / {{ messages.pages }}</text>
        <button
          :disabled="messages.page >= messages.pages || messages.navigating"
          @click="messages.paginate(messages.page + 1)"
        >
          下一页
        </button>
      </view>
    </template>
  </view>
</template>

<style scoped>
.notice-error {
  display: block;
  color: #902f28;
  background: #fff0ed;
  padding: 16px;
  border-radius: 10px;
  margin: 16px 0;
  overflow-wrap: anywhere;
}
.notice-pagination {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin: 20px 0;
}
.notice-pagination button {
  flex: 1;
  font-size: 0.9em;
  min-width: 100px;
}
</style>
