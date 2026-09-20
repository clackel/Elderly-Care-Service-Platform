<script setup lang="ts">
import { reactive } from 'vue'
import { useMobileBookings } from './useMobileBookings'
import type { Category } from '../../api/bookings'
import MobileServiceList from './MobileServiceList.vue'
import MobileBookingList from './MobileBookingList.vue'
import MobileBookingEditor from './MobileBookingEditor.vue'
import MobileBookingDetail from './MobileBookingDetail.vue'
import './booking.css'
const records = reactive(useMobileBookings())
/** 服务筛选重置分页，不持久化个人资料。 */
function filter(category: Category | '') {
  records.category = category
  records.page = 1
  void records.load()
}
/** 状态筛选始终重新读取后端授权范围。 */
function filterStatus(status: string) {
  records.status = status
  records.page = 1
  void records.load()
}
/** 加载指定分页。 */
function paginate(page: number) {
  records.page = page
  void records.load()
}
</script>
<template>
  <view>
    <view v-if="!records.selected && !records.detail && !records.staff" class="mobile-filter"
      ><button
        :disabled="records.busy"
        :class="{ 'font-button': records.mode === 'catalog' }"
        @click="records.switchMode('catalog')"
      >
        服务目录</button
      ><button
        :disabled="records.busy"
        :class="{ 'font-button': records.mode === 'bookings' }"
        @click="records.switchMode('bookings')"
      >
        我的预约
      </button></view
    >
    <text v-if="records.error" class="mobile-error">{{ records.error }}</text
    ><text v-if="records.success" class="mobile-success">{{ records.success }}</text>
    <text v-if="records.busy" class="body-copy">正在处理，请稍候…</text>
    <MobileBookingEditor
      v-if="records.selected && records.rules"
      :key="records.selected.id"
      :service="records.selected"
      :elders="records.elders"
      :rules="records.rules"
      :busy="records.busy"
      :uncertain="records.uncertain"
      @submit="records.submit"
      @close="records.back"
    />
    <MobileBookingDetail
      v-else-if="records.detail && records.rules"
      :key="records.detail.id + ':' + records.detail.version"
      :detail="records.detail"
      :history="records.events"
      :history-page="records.eventPage"
      :history-total="records.eventTotal"
      :staff="records.staff"
      :can-book="records.elders.some((e) => e.id === records.detail?.elderId && e.canBook)"
      :rules="records.rules"
      :busy="records.busy"
      @action="records.action"
      @back="records.back"
      @refresh="records.open(records.detail!.id)"
      @history="records.open(records.detail!.id, $event)"
    />
    <MobileServiceList
      v-else-if="records.mode === 'catalog'"
      :rows="records.services"
      :page="records.page"
      :total="records.total"
      :category="records.category"
      :busy="records.busy"
      @select="records.select"
      @page="paginate"
      @filter="filter"
    />
    <MobileBookingList
      v-else
      :rows="records.bookings"
      :page="records.page"
      :total="records.total"
      :staff="records.staff"
      :status="records.status"
      :busy="records.busy"
      @select="records.open"
      @page="paginate"
      @filter="filterStatus"
    />
    <view v-if="records.error && !records.selected && !records.detail" class="mobile-buttons"
      ><button :disabled="records.busy" @click="records.load">重新读取列表</button></view
    >
  </view>
</template>
