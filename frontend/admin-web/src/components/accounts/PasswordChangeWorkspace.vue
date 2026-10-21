<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import PasswordChangeForm from './PasswordChangeForm.vue'
import { usePasswordChange } from './usePasswordChange'
import './accounts.css'

const password = reactive(usePasswordChange())

/** 刷新或关闭时提示尚未完成的改密操作，不把密码写入浏览器存储。 */
function guardUnload(event: BeforeUnloadEvent) {
  if (password.busy || password.dirty || password.uncertain) {
    event.preventDefault()
    event.returnValue = ''
  }
}

onBeforeRouteLeave(password.confirmLeave)
onMounted(() => window.addEventListener('beforeunload', guardUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', guardUnload))
</script>

<template>
  <main class="password-page">
    <section class="account-panel">
      <span class="eyebrow">ACCOUNT SECURITY</span>
      <h1>{{ password.required ? '请先修改密码' : '修改本人密码' }}</h1>
      <p class="account-help">
        {{
          password.required
            ? '当前使用初始或重置密码，完成修改后才能进入管理后台。'
            : '请使用仅由本人掌握的新密码。'
        }}
      </p>
      <PasswordChangeForm
        :busy="password.busy"
        :uncertain="password.uncertain"
        :error="password.error"
        @submit="password.save"
        @dirty="password.dirty = $event"
      />
      <div class="password-actions">
        <RouterLink v-if="!password.required && !password.busy" to="/">返回工作台</RouterLink
        ><el-button :disabled="password.busy" @click="password.logout">退出登录</el-button>
      </div>
    </section>
  </main>
</template>
