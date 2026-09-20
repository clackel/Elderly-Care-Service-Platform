<script setup lang="ts">
import { reactive } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useSessionStore } from '../../stores/session'
import { useBookings } from './useBookings'
import BookingEditor from './BookingEditor.vue'
import BookingTable from './BookingTable.vue'
import BookingDetails from './BookingDetails.vue'
import type { BookingStatus } from '../../api/bookings'
import './bookings.css'
const records = reactive(useBookings())
const session = useSessionStore()
/** 切换状态时从第一页重新加载。 */
function filter(value: BookingStatus | '') {
  records.status = value
  records.page = 1
  void records.load()
}
/** 明确加载选定分页。 */
function paginate(value: number) {
  records.page = value
  void records.load()
}
onBeforeRouteLeave(
  () =>
    !session.account ||
    (!records.busy &&
      (!records.creating ||
        window.confirm('离开将清除预约草稿；提交结果不确定时请先到列表核实。确定离开吗？'))),
)
</script>
<template>
  <div class="booking-workspace">
    <header class="page-heading">
      <div>
        <span class="eyebrow">COMMUNITY CARE</span>
        <h1>养老服务预约</h1>
        <p class="muted">从收到申请到服务结束，每一步都有记录。</p>
      </div>
      <el-button
        type="primary"
        size="large"
        :disabled="records.busy || records.creating"
        @click="records.startCreate"
        >＋ 代老人预约</el-button
      >
    </header>
    <div v-if="records.error" class="booking-error" role="alert">{{ records.error }}</div>
    <BookingEditor
      v-if="records.creating"
      :services="records.services"
      :elders="records.elders"
      :rules="records.rules"
      :busy="records.busy"
      :uncertain="records.uncertain"
      @submit="records.create"
      @close="records.creating = false"
    />
    <BookingDetails
      v-if="records.detail"
      :key="records.detail.id + ':' + records.detail.version"
      :detail="records.detail"
      :history="records.history"
      :history-page="records.historyPage"
      :history-total="records.historyTotal"
      :workers="records.workers"
      :busy="records.busy"
      @close="records.closeDetail"
      @action="records.action"
      @arrange="records.arrange"
      @history="records.open(records.detail!.id, $event)"
    />
    <BookingTable
      :rows="records.rows"
      :page="records.page"
      :total="records.total"
      :status="records.status"
      :loading="records.loading"
      :busy="records.busy"
      @view="records.open"
      @page="paginate"
      @filter="filter"
      @retry="records.load"
    />
  </div>
</template>
