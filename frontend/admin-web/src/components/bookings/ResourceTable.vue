<script setup lang="ts">
import { computed } from 'vue'
import type { CatalogResource } from '../../api/bookings'
import { resourceSummary, type ResourceKind } from './resourceModel'
const props = defineProps<{
  kind: ResourceKind
  rows: CatalogResource[]
  page: number
  total: number
  busy: boolean
}>()
const emit = defineEmits<{
  edit: [row: CatalogResource]
  issue: [id: string]
  revoke: [row: CatalogResource]
  page: [page: number]
}>()
const items = computed(() =>
  props.rows.map((row) => ({ row, ...resourceSummary(props.kind, row) })),
)
</script>
<template>
  <section class="booking-panel">
    <div class="booking-table-wrap">
      <table class="booking-table">
        <thead>
          <tr>
            <th>名称 / 对象</th>
            <th>信息</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.row.id">
            <td>
              {{ item.title }}
              <div class="booking-muted">{{ item.row.id }}</div>
            </td>
            <td>{{ item.detail }}</td>
            <td>{{ item.status }}</td>
            <td>
              <div class="booking-actions">
                <button v-if="kind !== 'members'" :disabled="busy" @click="emit('edit', item.row)">
                  编辑</button
                ><button
                  v-if="kind === 'members' && 'wechatLinked' in item.row && !item.row.wechatLinked"
                  :disabled="busy"
                  @click="emit('issue', item.row.id)"
                >
                  签发开通码</button
                ><button
                  v-if="'revoked' in item.row && !item.row.revoked"
                  :disabled="busy"
                  @click="emit('revoke', item.row)"
                >
                  撤销授权
                </button>
              </div>
            </td>
          </tr>
          <tr v-if="!items.length">
            <td colspan="4">暂无记录，请先新建。</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="booking-actions">
      <button :disabled="busy || page <= 1" @click="emit('page', page - 1)">上一页</button
      ><span>第 {{ page }} 页，共 {{ total }} 条</span
      ><button :disabled="busy || page * 10 >= total" @click="emit('page', page + 1)">
        下一页
      </button>
    </div>
  </section>
</template>
