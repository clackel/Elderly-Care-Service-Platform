<script setup lang="ts">
import { healthTime, measurementText, type HealthRecord } from '../../api/health'
defineProps<{ rows: HealthRecord[]; page: number; total: number; disabled: boolean }>()
const emit = defineEmits<{ open: [id: string]; page: [page: number] }>()
</script>
<template>
  <section class="health-panel">
    <h2>本人代录记录</h2>
    <div class="health-scroll"><table class="health-table"><thead><tr><th>测量时间（北京时间）</th><th>测量内容</th><th>状态</th><th>版本</th><th>操作</th></tr></thead><tbody>
      <tr v-for="row in rows" :key="row.id"><td>{{ healthTime(row.measurement.measuredAt) }}</td><td>{{ measurementText(row.measurement) }}</td><td>{{ row.status === 'ACTIVE' ? '有效' : '已作废' }}</td><td>{{ row.version }}{{ row.corrected ? '（已有后续版本）' : '' }}</td><td><el-button :disabled="disabled" @click="emit('open',row.id)">查看详情</el-button></td></tr>
    </tbody></table></div>
    <el-empty v-if="!rows.length" description="当前范围暂无记录" />
    <div class="health-actions"><el-button :disabled="disabled || page <= 1" @click="emit('page',page - 1)">上一页</el-button><span>第{{ page }}页，共{{ total }}条</span><el-button :disabled="disabled || page * 10 >= total" @click="emit('page',page + 1)">下一页</el-button></div>
  </section>
</template>

