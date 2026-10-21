<script setup lang="ts">
defineProps<{ page: number; pages: number; total: number; disabled: boolean }>()
const emit = defineEmits<{ change: [page: number] }>()
</script>

<template>
  <div class="notification-pagination">
    <span class="muted">共 {{ total }} 条 · 第 {{ page }} / {{ pages }} 页</span>
    <div>
      <el-button :disabled="disabled || page <= 1" @click="emit('change', page - 1)"
        >上一页</el-button
      ><el-button :disabled="disabled || page >= pages" @click="emit('change', page + 1)"
        >下一页</el-button
      >
    </div>
  </div>
</template>
