<script setup lang="ts">
import { reactive } from 'vue'
import type { AccountQuery } from '../../api/adminAccounts'
import { adminRoleLabels } from './accountModel'

const props = defineProps<{ query: AccountQuery; loading: boolean }>()
const emit = defineEmits<{ search: [filters: Pick<AccountQuery, 'keyword' | 'role' | 'status'>] }>()
const filters = reactive({
  keyword: props.query.keyword,
  role: props.query.role,
  status: props.query.status,
})

/** 提交独立筛选快照，账号查询条件只在当前页面保留。 */
function search() {
  emit('search', { ...filters, keyword: filters.keyword.trim() })
}
</script>

<template>
  <form class="account-filters account-panel" @submit.prevent="search">
    <label class="account-field" for="account-keyword"
      >账号或显示名
      <input
        id="account-keyword"
        v-model="filters.keyword"
        class="account-control"
        maxlength="64"
        placeholder="输入账号或显示名"
      />
    </label>
    <label class="account-field" for="account-role-filter"
      >角色
      <select id="account-role-filter" v-model="filters.role" class="account-control">
        <option value="">全部角色</option>
        <option v-for="(label, role) in adminRoleLabels" :key="role" :value="role">
          {{ label }}
        </option>
      </select>
    </label>
    <label class="account-field" for="account-status-filter"
      >状态
      <select id="account-status-filter" v-model="filters.status" class="account-control">
        <option value="">全部状态</option>
        <option value="ACTIVE">启用</option>
        <option value="DISABLED">停用</option>
      </select>
    </label>
    <el-button type="primary" native-type="submit" :loading="loading">查询</el-button>
  </form>
</template>
