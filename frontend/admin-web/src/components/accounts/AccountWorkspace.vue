<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useSessionStore } from '../../stores/session'
import AccountFilters from './AccountFilters.vue'
import AccountTable from './AccountTable.vue'
import AccountEditor from './AccountEditor.vue'
import AccountHistory from './AccountHistory.vue'
import { useAdminAccounts } from './useAdminAccounts'
import './accounts.css'

const session = useSessionStore()
const accounts = reactive(useAdminAccounts())

/** 在浏览器关闭前提示尚未完成的账号操作，不保存密码草稿。 */
function guardUnload(event: BeforeUnloadEvent) {
  if (accounts.saving || accounts.dirty || accounts.uncertain) {
    event.preventDefault()
    event.returnValue = ''
  }
}

onBeforeRouteLeave(() => !session.account || session.account.passwordChangeRequired || accounts.confirmDiscard())
onMounted(() => window.addEventListener('beforeunload', guardUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', guardUnload))
</script>

<template>
  <div class="account-workspace">
    <section class="page-heading"><div><span class="eyebrow">COMMUNITY ACCOUNTS</span><h1>后台账号管理</h1><p class="muted">维护本社区后台人员的登录账号与职责。</p></div><el-button type="primary" size="large" @click="accounts.openEditor('create')">＋ 创建账号</el-button></section>
    <p class="account-scope">当前社区：{{ session.account?.communityId }}。每个账号使用一个角色，所属社区固定。</p>
    <AccountFilters :query="accounts.query" :loading="accounts.loading" @search="accounts.search" />
    <AccountTable :rows="accounts.rows" :current-id="session.account?.id ?? ''" :total="accounts.total" :page="accounts.query.page" :pages="accounts.pages" :loading="accounts.loading" :error="accounts.listError" @edit="accounts.openEditor('update', $event)" @reset="accounts.openEditor('reset', $event)" @history="accounts.history = $event" @retry="accounts.loadList" @paginate="accounts.paginate" />
    <AccountEditor v-if="accounts.editor" :key="accounts.editor.key" :mode="accounts.editor.mode" :account="accounts.editor.account" :current-id="session.account?.id ?? ''" :saving="accounts.saving" :uncertain="accounts.uncertain" :conflict="accounts.conflict" :error="accounts.formError" @submit="accounts.save" @close="accounts.closeEditor" @dirty="accounts.dirty = $event" />
    <AccountHistory v-if="accounts.history" :key="accounts.history.id" :account="accounts.history" @close="accounts.history = null" />
  </div>
</template>
