<script setup lang="ts">
import type { ElderQuery, ElderSummary } from '../../api/elders'
import { formatTime, genderLabels, livingLabels } from './elderModel'

defineProps<{
  rows: ElderSummary[]
  total: number
  pages: number
  query: ElderQuery
  loading: boolean
  error: string
}>()
const emit = defineEmits<{
  view: [id: string]
  edit: [id: string]
  retry: []
  paginate: [page: number, pageSize?: number]
}>()

/** 将条数选择转换为整数，交由父级重置页码并重新读取。 */
function changePageSize(event: Event) {
  emit('paginate', 1, Number((event.target as HTMLSelectElement).value))
}
</script>

<template>
  <section class="elder-list" aria-labelledby="elder-list-title" :aria-busy="loading">
    <div class="elder-list-heading">
      <h2 id="elder-list-title">
        档案列表 <span v-if="!loading && !error" class="elder-count">{{ total }} 人</span>
      </h2>
      <span class="elder-help">列表联系电话已脱敏</span>
    </div>
    <div v-if="loading" class="elder-placeholder" role="status">正在读取档案…</div>
    <div v-else-if="error" class="elder-placeholder" role="alert">
      <h3>档案暂时无法加载</h3>
      <p>{{ error }}</p>
      <el-button @click="emit('retry')">重新加载</el-button>
    </div>
    <el-empty
      v-else-if="!rows.length"
      description="没有符合条件的档案，请调整筛选条件或新建档案"
      :image-size="88"
    />
    <div v-else class="elder-table-scroll" tabindex="0" aria-label="老人档案列表，可横向滚动">
      <table class="elder-table">
        <thead>
          <tr>
            <th scope="col">老人姓名</th>
            <th scope="col">性别 / 年龄</th>
            <th scope="col">联系电话</th>
            <th scope="col">居住情况</th>
            <th scope="col">状态</th>
            <th scope="col">最近更新</th>
            <th scope="col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <th scope="row">
              <button
                class="elder-name"
                :aria-label="'查看' + row.name + '的档案'"
                @click="emit('view', row.id)"
              >
                <span class="elder-avatar" aria-hidden="true">{{ row.name.slice(0, 1) }}</span
                >{{ row.name }}
              </button>
            </th>
            <td>
              {{ genderLabels[row.gender] }} <span class="elder-divider">/</span> {{ row.age }} 岁
            </td>
            <td>{{ row.maskedPhone || '未填写' }}</td>
            <td>{{ livingLabels[row.livingArrangement] }}</td>
            <td>
              <span class="elder-badge" :class="{ archived: row.status === 'ARCHIVED' }">{{
                row.status === 'ACTIVE' ? '在册' : '已归档'
              }}</span>
            </td>
            <td class="elder-date">{{ formatTime(row.updatedAt) }}</td>
            <td>
              <div class="elder-row-actions">
                <el-button
                  text
                  type="primary"
                  :aria-label="'查看' + row.name"
                  @click="emit('view', row.id)"
                  >查看</el-button
                ><el-button
                  v-if="row.status === 'ACTIVE'"
                  text
                  type="primary"
                  :aria-label="'编辑' + row.name"
                  @click="emit('edit', row.id)"
                  >编辑</el-button
                >
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="!error" class="elder-pagination" aria-label="档案分页">
      <label
        >每页
        <select
          :value="query.pageSize"
          class="elder-control"
          :disabled="loading"
          @change="changePageSize"
        >
          <option :value="10">10 条</option>
          <option :value="20">20 条</option>
          <option :value="50">50 条</option>
        </select></label
      >
      <div>
        <span aria-live="polite">第 {{ query.page }} / {{ pages }} 页</span
        ><el-button :disabled="loading || query.page <= 1" @click="emit('paginate', query.page - 1)"
          >上一页</el-button
        ><el-button
          :disabled="loading || query.page >= pages"
          @click="emit('paginate', query.page + 1)"
          >下一页</el-button
        >
      </div>
    </div>
  </section>
</template>
