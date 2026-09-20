<script setup lang="ts">
import PageFrame from '../../components/PageFrame.vue'
import { usePreferencesStore } from '../../stores/preferences'
const preferences = usePreferencesStore()
/** 切换到底部导航中已注册的页面，url 为页面路径。 */
function navigate(url: string) {
  uni.switchTab({ url })
}
</script>
<template>
  <PageFrame title="您好，欢迎来到社区养老" subtitle="服务在身边，生活更安心">
    <button class="font-button" @click="preferences.toggleTextSize">
      {{ preferences.largeText ? '使用默认字号' : '使用特大字号' }}
    </button>
    <view class="home-links">
      <button class="feature-button" @click="navigate('/pages/services/index')">
        查看社区服务
      </button>
      <button class="feature-button" @click="navigate('/pages/health/index')">我的健康</button>
      <button class="feature-button" @click="navigate('/pages/profile/index')">我的账号</button>
    </view>
    <view class="notice-card"
      ><text>服务预约提交后需由社区确认。首次使用请联系社区核验身份并开通账号。</text></view
    >
  </PageFrame>
</template>
