<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useSessionStore } from '../../stores/session'
import { request } from '../../api/request'
import './booking.css'
const session = useSessionStore(),
  token = ref(''),
  error = ref(''),
  busy = ref(false),
  enabled = ref(false)
let alive = true
/** 只根据后端真实配置显示微信登录能力。 */
async function capabilities() {
  try {
    const data = await request<{ wechatLogin: boolean }>('/auth/capabilities')
    if (alive) enabled.value = data.wechatLogin
  } catch (e) {
    if (alive) error.value = e instanceof Error ? e.message : '无法读取登录方式'
  }
}
/** 开通码只在当前输入框内保存，成功登录即清除。 */
async function login() {
  busy.value = true
  error.value = ''
  try {
    await session.login(token.value)
    token.value = ''
  } catch (e) {
    if (alive) error.value = e instanceof Error ? e.message : '登录失败，请重试'
  } finally {
    if (alive) busy.value = false
  }
}
/** 恢复已有真实会话，不创建开发身份。 */
async function restore() {
  busy.value = true
  error.value = ''
  try {
    await session.restore()
    if (alive && !session.account) error.value = '没有可恢复的会话，请使用微信登录'
  } catch (e) {
    if (alive) error.value = e instanceof Error ? e.message : '会话恢复失败'
  } finally {
    if (alive) busy.value = false
  }
}
/** 服务端确认退出后才清除已展示的账号信息。 */
async function logout() {
  busy.value = true
  error.value = ''
  try {
    await session.logout()
  } catch (e) {
    if (alive) error.value = e instanceof Error ? e.message : '退出失败，请重试'
  } finally {
    if (alive) busy.value = false
  }
}
onMounted(capabilities)
onBeforeUnmount(() => {
  alive = false
  token.value = ''
})
</script>
<template>
  <view class="mobile-card">
    <template v-if="session.account"
      ><text class="card-title">{{ session.account.displayName }}</text
      ><text class="body-copy">{{
        { ELDER: '老人账号', FAMILY: '家属账号', STAFF: '服务人员账号' }[session.account.role]
      }}</text
      ><text class="mobile-details">账号编号：{{ session.account.id }}</text
      ><text v-if="session.account.role === 'FAMILY'" class="body-copy"
        >可将账号编号交给老人或社区人员，用于建立明确的预约授权。</text
      ><view class="mobile-buttons"
        ><button :disabled="busy" @click="logout">退出登录</button></view
      ></template
    >
    <template v-else
      ><text class="card-title">登录社区服务</text
      ><text class="body-copy"
        >首次使用请联系社区核验身份，领取一次性开通码。已绑定微信的账号无需再次填写。</text
      ><input
        v-model="token"
        class="mobile-control"
        :disabled="busy"
        maxlength="100"
        placeholder="首次使用时输入开通码"
      />
      <!-- #ifdef MP-WEIXIN -->
      <view class="mobile-buttons"
        ><button class="feature-button" :disabled="busy || !enabled" @click="login">
          {{ busy ? '正在登录…' : '微信登录' }}
        </button></view
      >
      <!-- #endif -->
      <!-- #ifdef H5 -->
      <view class="notice-card">微信登录请在小程序中操作。本页可恢复已有的真实服务器会话。</view>
      <!-- #endif -->
      <view v-if="!enabled" class="notice-card">微信登录尚未配置，请联系社区。</view
      ><view class="mobile-buttons"
        ><button :disabled="busy" @click="restore">恢复已有会话</button
        ><button :disabled="busy" @click="capabilities">刷新登录方式</button></view
      >
    </template>
    <text v-if="error" class="mobile-error">{{ error }}</text>
  </view>
</template>
