<script setup lang="ts">
import { reactive } from 'vue'
import type { ElderQuery } from '../../api/elders'

const props = defineProps<{ query: ElderQuery }>()
const emit = defineEmits<{ search: [filters: Pick<ElderQuery, 'keyword' | 'status'>] }>()
const filters = reactive({ keyword: props.query.keyword, status: props.query.status })

/** 以完整姓名或老人电话提交过滤条件，不将个人信息写入地址栏。 */
function submit() {
  emit('search', { ...filters, keyword: filters.keyword.trim() })
}

/** 清空检索词并恢复默认的在册状态筛选。 */
function reset() {
  filters.keyword = ''
  filters.status = 'ACTIVE'
  submit()
}
</script>

<template>
  <form class="elder-filters" aria-label="档案筛选" @submit.prevent="submit">
    <div class="elder-search">
      <label for="elder-keyword">姓名 / 联系电话</label>
      <input
        id="elder-keyword"
        v-model="filters.keyword"
        class="elder-control"
        type="search"
        maxlength="50"
        placeholder="输入完整姓名或老人联系电话"
        autocomplete="off"
        aria-describedby="elder-search-help"
      />
    </div>
    <div>
      <label for="elder-status">档案状态</label>
      <select id="elder-status" v-model="filters.status" class="elder-control">
        <option value="ACTIVE">在册</option>
        <option value="ARCHIVED">已归档</option>
        <option value="">全部状态</option>
      </select>
    </div>
    <div class="elder-filter-actions">
      <el-button type="primary" native-type="submit">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>
    <p id="elder-search-help" class="elder-help">
      支持精确匹配；不支持部分姓名或紧急联系人电话检索。
    </p>
  </form>
</template>
