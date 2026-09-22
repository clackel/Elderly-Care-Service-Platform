<script setup lang="ts">
import { reactive } from 'vue'
import { useSessionStore } from '../../stores/session'
import { healthTypes } from '../../api/health'
import { useHealthRecords } from './useHealthRecords'
import HealthRecordForm from './HealthRecordForm.vue'
import HealthRecordTable from './HealthRecordTable.vue'
import HealthRecordDetail from './HealthRecordDetail.vue'
import HealthGrantList from './HealthGrantList.vue'
import './health.css'
const session = useSessionStore(), r = reactive(useHealthRecords())
</script>
<template>
  <section class="page-heading"><div><span class="eyebrow">HEALTH</span><h1>健康协助录入</h1><p class="muted">仅处理本人获授权的老人及自己原始代录、尚未由老人接管的记录。</p></div></section>
  <section class="health-panel"><p>本账号编号：<strong>{{ session.account?.id }}</strong></p><p class="muted">可将账号编号提供给老人，由本人在“健康”页面核对并授予协助权限。预约授权不能代替健康授权。</p></section>
  <el-alert v-if="r.error" :title="r.error" type="error" :closable="false" /><el-alert v-if="r.notice" :title="r.notice" type="success" :closable="false" />
  <p v-if="r.busy" role="status">正在读取或保存，请稍候…</p>
  <section v-if="r.pending" class="health-panel"><p>提交结果尚未确认，已保留原始请求。请重试原请求；离开页面会清空内存，重新进入后先核对记录再新增。</p><el-button :disabled="r.busy" type="primary" @click="r.retry">重试原请求</el-button></section>
  <template v-if="!r.editor && !r.detail">
    <section class="health-panel"><h2>获授权老人</h2><div class="health-actions"><el-button v-for="elder in r.elders" :key="elder.id" :type="r.selected?.id === elder.id ? 'primary' : 'default'" :disabled="r.locked" @click="r.select(elder)">{{ elder.name }}</el-button></div><p v-if="!r.elders.length">当前没有可协助的老人，需要本人在线独立授权。</p><div class="health-actions"><el-button :disabled="r.locked || r.elderPage <= 1" @click="r.moreElders(r.elderPage - 1)">上一页老人</el-button><el-button :disabled="r.locked || r.elderPage * 10 >= r.elderTotal" @click="r.moreElders(r.elderPage + 1)">下一页老人</el-button><el-button :disabled="r.locked" @click="r.initialize">刷新权限</el-button></div></section>
  </template>
  <template v-if="r.selected">
    <h2>{{ r.selected.name }}</h2>
    <HealthRecordForm v-if="r.editor" :key="r.editor + ':' + (r.detail?.id || '')" :initial="r.editor === 'correct' ? r.detail?.measurement : undefined" :correcting="r.editor === 'correct'" :disabled="r.locked" :conflict="r.conflict" @save="r.save" @close="r.close" />
    <HealthRecordDetail v-else-if="r.detail" :key="r.detail.id + ':' + r.detail.version" :record="r.detail" :disabled="r.locked" @correct="r.edit('correct')" @void="r.voidRecord" @refresh="r.open(r.detail!.id)" @close="r.close" />
    <template v-else>
      <section class="health-panel"><div class="health-actions"><el-button v-if="r.selected.canWrite" type="primary" :disabled="r.locked" @click="r.edit('create')">新增手工代录</el-button></div>
        <form @submit.prevent="r.load(1)"><fieldset class="health-fields" :disabled="r.locked">
          <label>指标<select v-model="r.type"><option value="">全部指标</option><option v-for="(label,key) in healthTypes" :key="key" :value="key">{{ label }}</option></select></label>
          <label>状态<select v-model="r.status"><option value="ACTIVE">有效记录</option><option value="VOID">作废记录</option></select></label>
          <label>开始日期（北京时间）<input v-model="r.start" type="date" /></label><label>结束日期（含当天）<input v-model="r.end" type="date" /></label>
        </fieldset><div class="health-actions"><el-button native-type="submit" :disabled="r.locked">查询</el-button><el-button :disabled="r.locked" @click="r.start = ''; r.end = ''">清除日期</el-button></div></form>
      </section>
      <HealthRecordTable :rows="r.rows" :page="r.page" :total="r.total" :disabled="r.locked" @open="r.open" @page="r.load" />
    </template>
  </template>
  <HealthGrantList v-if="!r.editor && !r.detail" :grants="r.grants" :page="r.grantPage" :total="r.grantTotal" :disabled="r.locked" @revoke="r.revoke" @page="r.moreGrants" />
</template>

