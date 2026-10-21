<script setup lang="ts">
import type { AdminAccount } from '../../api/adminAccounts'
import { adminRoleLabels } from './accountModel'

defineProps<{
  rows: AdminAccount[]
  currentId: string
  total: number
  page: number
  pages: number
  loading: boolean
  error: string
}>()
const emit = defineEmits<{
  edit: [account: AdminAccount]
  reset: [account: AdminAccount]
  history: [account: AdminAccount]
  retry: []
  paginate: [page: number]
}>()
</script>

<template>
  <section class="account-panel" :aria-busy="loading" aria-label="后台账号列表">
    <p v-if="loading" role="status">正在读取账号…</p>
    <div v-else-if="error" role="alert">
      <p>{{ error }}</p>
      <el-button @click="emit('retry')">重新加载</el-button>
    </div>
    <el-empty v-else-if="!rows.length" description="暂无符合条件的后台账号" />
    <div v-else class="account-table-scroll">
      <table class="account-table">
        <thead>
          <tr>
            <th>账号 / 显示名</th>
            <th>角色</th>
            <th>状态</th>
            <th>密码状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="account in rows" :key="account.id">
            <td>
              <strong>{{ account.username }}</strong>
              <div class="account-help">{{ account.displayName }}</div>
            </td>
            <td>{{ adminRoleLabels[account.role] }}</td>
            <td>
              <el-tag :type="account.status === 'ACTIVE' ? 'success' : 'info'">{{
                account.status === 'ACTIVE' ? '启用' : '停用'
              }}</el-tag>
            </td>
            <td>{{ account.passwordChangeRequired ? '待本人修改' : '已设置' }}</td>
            <td class="account-row-actions">
              <el-button @click="emit('edit', account)">编辑</el-button
              ><el-button :disabled="account.id === currentId" @click="emit('reset', account)"
                >重置密码</el-button
              ><el-button @click="emit('history', account)">操作记录</el-button>
              <p v-if="account.id === currentId" class="account-help">
                本人密码请通过 <RouterLink to="/password">修改本人密码</RouterLink> 更新。
              </p>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="!error" class="account-pagination">
      <span class="account-help">共 {{ total }} 个账号 · 第 {{ page }} / {{ pages }} 页</span>
      <div>
        <el-button :disabled="loading || page <= 1" @click="emit('paginate', page - 1)"
          >上一页</el-button
        ><el-button :disabled="loading || page >= pages" @click="emit('paginate', page + 1)"
          >下一页</el-button
        >
      </div>
    </div>
  </section>
</template>
