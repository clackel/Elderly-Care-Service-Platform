<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PageFrame from '../../components/PageFrame.vue'
import MobileAccountPanel from '../../components/booking/MobileAccountPanel.vue'
import MobileGrants from '../../components/booking/MobileGrants.vue'
import { usePreferencesStore } from '../../stores/preferences'
import { useSessionStore } from '../../stores/session'
const preferences = usePreferencesStore(),
  session = useSessionStore(),
  visit = ref(0)
/** 返回账号页时重新核对当前授权，避免继续展示撤销前的数据。 */
onShow(() => {
  visit.value++
})
</script>
<template>
  <PageFrame title="我的" subtitle="账号、预约授权与使用设置">
    <MobileAccountPanel />
    <MobileGrants
      v-if="session.account && session.account.role !== 'STAFF'"
      :key="session.account.id + ':' + visit"
    />
    <button class="font-button" @click="preferences.toggleTextSize">
      {{ preferences.largeText ? '恢复默认字号' : '切换特大字号' }}
    </button>
  </PageFrame>
</template>
