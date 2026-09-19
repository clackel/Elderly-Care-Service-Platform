<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useSessionStore } from '../../stores/session'
import ElderDetails from './ElderDetails.vue'
import ElderEditor from './ElderEditor.vue'
import ElderFilters from './ElderFilters.vue'
import ElderTable from './ElderTable.vue'
import { useElderRecords } from './useElderRecords'
import './elders.css'

const session = useSessionStore()
const records = reactive(useElderRecords())

/** 浏览器刷新或关闭前提示未保存数据；不会将个人资料持久化为草稿。 */
function guardUnload(event: BeforeUnloadEvent) {
  if (records.dirty || records.saving || records.uncertain) {
    event.preventDefault()
    event.returnValue = ''
  }
}

// 会话失效必须立即卸载敏感页面；正常导航则遵循草稿关闭确认。
onBeforeRouteLeave(() => !session.account || records.confirmDiscard())
onMounted(() => window.addEventListener('beforeunload', guardUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', guardUnload))
</script>

<template>
  <div class="elder-workspace">
    <section class="page-heading elder-page-heading">
      <div>
        <span class="eyebrow">COMMUNITY CARE</span>
        <h1>老人档案</h1>
        <p class="muted">一份清晰的档案，让每一次服务更有准备。</p>
      </div>
      <el-button type="primary" size="large" @click="records.startCreate">＋ 新建档案</el-button>
    </section>
    <div class="elder-scope">
      <span class="elder-scope-dot" aria-hidden="true" /><span>当前社区档案</span
      ><span class="elder-help">仅展示您有权访问的老人资料，请按服务需要查阅。</span>
    </div>
    <ElderFilters :query="records.query" @search="records.search" />
    <ElderTable
      :rows="records.rows"
      :total="records.total"
      :pages="records.pages"
      :query="records.query"
      :loading="records.loading"
      :error="records.listError"
      @view="records.openDetail"
      @edit="records.openDetail($event, true)"
      @retry="records.loadList"
      @paginate="records.paginate"
    />
    <ElderDetails
      v-if="records.detailOpen"
      :detail="records.detail"
      :loading="records.detailLoading"
      :error="records.detailError"
      :busy="records.transitioning"
      @close="records.closeDetail"
      @retry="records.openDetail(records.selectedId)"
      @edit="records.editDetail"
      @transition="records.changeStatus"
    />
    <ElderEditor
      v-if="records.editor"
      :key="records.editor.key"
      :initial="records.editor.profile"
      :editing="!!records.editor.detail"
      :saving="records.saving"
      :reloading="records.editorReloading"
      :error="records.formError"
      :conflict="records.conflict"
      :uncertain="records.uncertain"
      @submit="records.save"
      @close="records.closeEditor"
      @reload="records.reloadEditor"
      @dirty="records.dirty = $event"
    />
  </div>
</template>
