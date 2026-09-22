<script setup lang="ts">
import { shallowRef } from 'vue'
import { healthTime, type HealthGrant } from '../../api/health'
defineProps<{ grants: HealthGrant[]; page: number; total: number; disabled: boolean }>()
const emit = defineEmits<{ revoke: [grant: HealthGrant]; page: [page: number] }>()
const confirmId = shallowRef('')
</script>
<template>
  <section class="health-panel">
    <h2>收到的健康协助授权</h2>
    <p class="muted">仅老人本人可以在线授权。这里可以查看授权期限或主动放弃，不能替老人授予权限。</p>
    <article v-for="grant in grants" :key="grant.id" class="health-grant">
      <p>老人编号{{ grant.elderId }} · 到期{{ healthTime(grant.expiresAt) }} · {{ grant.revoked ? '已撤销' : grant.effective ? '有效' : '已到期或关系失效' }}</p>
      <el-button v-if="!grant.revoked && confirmId !== grant.id" :disabled="disabled" @click="confirmId = grant.id">放弃此授权</el-button>
      <template v-if="confirmId === grant.id && !grant.revoked"><span>放弃后不能再读取或维护相关健康记录。</span><el-button :disabled="disabled" type="danger" @click="emit('revoke', grant); confirmId = ''">确认放弃</el-button><el-button :disabled="disabled" @click="confirmId = ''">取消</el-button></template>
    </article>
    <p v-if="!grants.length">暂无收到的健康授权。</p>
    <div class="health-actions"><el-button :disabled="disabled || page <= 1" @click="emit('page', page - 1)">上一页授权</el-button><el-button :disabled="disabled || page * 10 >= total" @click="emit('page', page + 1)">下一页授权</el-button></div>
  </section>
</template>

