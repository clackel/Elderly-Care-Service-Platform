<script setup lang="ts">
import type { TodoType } from '../../api/notifications'
defineProps<{ staff: boolean; mode: 'notices' | 'todos'; unreadOnly: boolean; type: TodoType | ''; unread: number | null; disabled: boolean }>()
const emit = defineEmits<{ mode: [value: 'notices' | 'todos']; unread: [value: boolean]; type: [value: TodoType | '']; refresh: [] }>()
const filters: { value: TodoType | ''; label: string }[] = [
  { value: '', label: '全部待办' }, { value: 'WAITING_START', label: '待开始' },
  { value: 'IN_PROGRESS', label: '待完成' },
]
</script>

<template>
  <view class="notice-controls">
    <text class="body-copy">未读消息：{{ unread === null ? '—' : unread }}</text>
    <view v-if="staff" class="notice-filter">
      <button :disabled="disabled" :class="{ 'font-button': mode === 'notices' }" @click="emit('mode', 'notices')">我的消息</button>
      <button :disabled="disabled" :class="{ 'font-button': mode === 'todos' }" @click="emit('mode', 'todos')">我的待办</button>
    </view>
    <view v-if="mode === 'notices'" class="notice-filter">
      <button :disabled="disabled" :class="{ 'font-button': !unreadOnly }" @click="emit('unread', false)">全部消息</button>
      <button :disabled="disabled" :class="{ 'font-button': unreadOnly }" @click="emit('unread', true)">只看未读</button>
    </view>
    <view v-else class="notice-filter"><button v-for="item in filters" :key="item.value" :disabled="disabled" :class="{ 'font-button': type === item.value }" @click="emit('type', item.value)">{{ item.label }}</button></view>
    <button :disabled="disabled" @click="emit('refresh')">刷新消息与待办</button>
  </view>
</template>

<style scoped>
.notice-controls { display: flex; flex-direction: column; gap: 14px; margin-bottom: 20px; }
.notice-filter { display: flex; flex-wrap: wrap; gap: 10px; }
.notice-filter button { flex: 1; min-width: 110px; font-size: 0.9em; }
</style>
