<script setup lang="ts">
import { reactive } from 'vue'
import { useHealthRecords } from './useHealthRecords'
import { healthTypes, type HealthType } from '../../api/health'
import HealthRecordForm from './HealthRecordForm.vue'
import HealthRecordList from './HealthRecordList.vue'
import HealthRecordDetail from './HealthRecordDetail.vue'
import HealthTrend from './HealthTrend.vue'
import HealthGrantPanel from './HealthGrantPanel.vue'
import './health.css'
const r = reactive(useHealthRecords()), types = ['', ...Object.keys(healthTypes)] as (HealthType | '')[]
</script>
<template>
  <view>
    <text v-if="r.busy" class="body-copy">正在处理，请稍候…</text><text v-if="r.error" class="health-error">{{ r.error }}</text><text v-if="r.notice" class="body-copy">{{ r.notice }}</text>
    <view v-if="r.pending" class="notice-card"><text>尚未确认提交结果，内容已保留。请使用同一请求重试；离开页面会清空内存，请先核对记录后再新增。</text><button :disabled="r.busy" @click="r.retry">重试原请求</button></view>
    <template v-if="!r.editor && !r.detail">
      <text class="card-title">选择老人</text>
      <button v-for="elder in r.elders" :key="elder.id" :disabled="r.locked" :class="{ 'font-button': r.selected?.id === elder.id }" @click="r.select(elder)">{{ elder.name }}{{ elder.archived ? '（已归档）' : '' }}</button>
      <text v-if="!r.elders.length" class="body-copy">暂无可访问老人。本人需有效核验绑定，家属需独立健康授权。</text>
      <view class="health-actions"><button :disabled="r.locked || r.elderPage <= 1" @click="r.moreElders(r.elderPage - 1)">上一页老人</button><button :disabled="r.locked || r.elderPage * 10 >= r.elderTotal" @click="r.moreElders(r.elderPage + 1)">下一页老人</button><button :disabled="r.locked" @click="r.initialize">刷新权限</button></view>
    </template>
    <template v-if="r.selected">
      <text class="card-title">{{ r.selected.name }}的健康记录</text>
      <HealthRecordForm v-if="r.editor" :key="r.editor + ':' + (r.detail?.id || '')" :initial="r.editor === 'correct' ? r.detail?.measurement : undefined" :correcting="r.editor === 'correct'" :disabled="r.locked" :conflict="r.conflict" @save="r.save" @close="r.close" />
      <HealthRecordDetail v-else-if="r.detail" :key="r.detail.id + ':' + r.detail.version" :record="r.detail" :history="r.history" :history-page="r.historyPage" :history-total="r.historyTotal" :disabled="r.locked" @correct="r.edit('correct')" @void="r.voidRecord" @history="r.revisions" @refresh="r.open(r.detail!.id)" @close="r.close" />
      <template v-else>
        <button v-if="r.selected.canWrite" class="feature-button" :disabled="r.locked" @click="r.edit('create')">新增手工测量</button>
        <view class="mobile-card">
          <text class="card-title">筛选记录</text>
          <picker :disabled="r.locked" :range="types.map(t => t ? healthTypes[t] : '全部指标')" :value="types.indexOf(r.type)" @change="r.type = types[Number($event.detail.value)]!"><view class="health-control">{{ r.type ? healthTypes[r.type] : '全部指标' }}</view></picker>
          <picker v-if="r.selected.canGrant || r.selected.archived" :disabled="r.locked" :range="['有效记录','作废记录']" :value="r.status === 'ACTIVE' ? 0 : 1" @change="r.status = Number($event.detail.value) === 0 ? 'ACTIVE' : 'VOID'"><view class="health-control">{{ r.status === 'ACTIVE' ? '有效记录' : '作废记录' }}</view></picker>
          <text class="body-copy">测量日期（北京时间，可选）</text>
          <picker mode="date" :value="r.start" :disabled="r.locked" @change="r.start = $event.detail.value"><view class="health-control">{{ r.start || '开始日期' }}</view></picker>
          <picker mode="date" :value="r.end" :disabled="r.locked" @change="r.end = $event.detail.value"><view class="health-control">{{ r.end || '结束日期' }}</view></picker>
          <button :disabled="r.locked" @click="r.start = ''; r.end = ''">清除日期</button><button :disabled="r.locked" @click="r.load(1)">查询记录</button>
        </view>
        <HealthRecordList :rows="r.rows" :page="r.page" :total="r.total" :disabled="r.locked" @open="r.open" @page="r.load" />
        <HealthTrend v-if="r.selected.canTrend" :key="r.selected.id" :points="r.points" :disabled="r.locked" @query="r.trend" @reset="r.points = []" />
      </template>
    </template>
    <HealthGrantPanel v-if="!r.editor && !r.detail" :key="r.selected?.id || 'none'" :elder="r.selected" :grants="r.grants" :recipient="r.recipient" :page="r.grantPage" :total="r.grantTotal" :disabled="r.locked" @resolve="r.resolve" @reset="r.clearRecipient" @grant="r.grant" @revoke="r.revoke" @page="r.moreGrants" />
  </view>
</template>

