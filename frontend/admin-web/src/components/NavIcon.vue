<script setup lang="ts">
import { computed } from 'vue'

/**
 * 导航与功能入口使用的线性图标，避免为少量图标引入额外依赖。
 * name 为功能模块路径，未知名称回退为通用图标；图标仅作装饰，对读屏隐藏。
 */
const props = defineProps<{ name: string; size?: number }>()

const paths: Record<string, string[]> = {
  home: ['M3 10.5 12 3l9 7.5', 'M5 9.5V21h5v-6h4v6h5V9.5'],
  notifications: ['M6 9a6 6 0 1 1 12 0c0 6 2.5 7.5 2.5 7.5h-17S6 15 6 9Z', 'M10 20a2 2 0 0 0 4 0'],
  accounts: [
    'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z',
    'M2 21v-1a6 6 0 0 1 6-6h2a6 6 0 0 1 6 6v1',
    'M16 3.5a4 4 0 0 1 0 7.5',
    'M22 21v-1a6 6 0 0 0-4-5.6',
  ],
  elders: [
    'M5 3h11l3 3v15H5Z',
    'M9 10a2.5 2.5 0 1 0 5 0 2.5 2.5 0 0 0-5 0',
    'M8 17.5a3.5 3.5 0 0 1 7 0',
  ],
  health: [
    'M20.4 5.6a5 5 0 0 0-7.1 0L12 6.9l-1.3-1.3a5 5 0 1 0-7.1 7.1L12 21l8.4-8.3a5 5 0 0 0 0-7.1Z',
    'M3.5 12H8l1.5-3 3 6 1.5-3H20',
  ],
  consent: ['M12 3 4 6v6c0 4.5 3.4 8.3 8 9 4.6-.7 8-4.5 8-9V6Z', 'm8.5 12 2.5 2.5 4.5-5'],
  services: ['M4 7h16v13H4Z', 'M9 7V4h6v3', 'M4 12h16', 'M10 12v2h4v-2'],
  bookings: ['M4 5h16v16H4Z', 'M8 3v4', 'M16 3v4', 'M4 10h16', 'm9 15 2 2 4-4'],
  fulfillment: ['M9 4h6v3H9Z', 'M7 5H5v16h14V5h-2', 'm9 13 2 2 4-4'],
  emergencies: ['M12 3 2 20h20Z', 'M12 10v4', 'M12 17h.01'],
  password: ['M5 11h14v10H5Z', 'M8 11V7a4 4 0 0 1 8 0v4', 'M12 15v2'],
  logout: ['M15 4h4v16h-4', 'M10 8l-4 4 4 4', 'M6 12h10'],
  community: ['M3 21h18', 'M5 21V10l7-5 7 5v11', 'M10 21v-5h4v5'],
  arrow: ['M5 12h14', 'm13 6 6 6-6 6'],
  default: ['M4 4h7v7H4Z', 'M13 4h7v7h-7Z', 'M4 13h7v7H4Z', 'M13 13h7v7h-7Z'],
}

const lines = computed(() => paths[props.name] ?? paths.default)
</script>

<template>
  <svg
    class="nav-icon"
    :width="size ?? 20"
    :height="size ?? 20"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path v-for="line in lines" :key="line" :d="line" />
  </svg>
</template>
