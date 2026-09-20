<script setup lang="ts">
import { reactive } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useSessionStore } from '../../stores/session'
import { time } from '../../api/bookings'
import { useResources } from './useResources'
import { resourceLabels, type ResourceKind } from './resourceModel'
import ResourceEditor from './ResourceEditor.vue'
import ResourceTable from './ResourceTable.vue'
import './bookings.css'
const props = defineProps<{ mode: 'catalog' | 'access' }>()
const tabs: ResourceKind[] =
  props.mode === 'catalog' ? ['catalog', 'providers', 'workers'] : ['members', 'bindings', 'grants']
const state = reactive(useResources(tabs[0]!)),
  session = useSessionStore()
/** 切换资源列表页，不改变当前筛选范围。 */
function paginate(page: number) {
  state.page = page
  void state.load()
}
onBeforeRouteLeave(
  () =>
    !session.account ||
    (!state.busy && (!state.editor || window.confirm('离开将清除尚未保存的资料，确定离开吗？'))),
)
</script>
<template>
  <div class="booking-workspace">
    <header class="page-heading">
      <div>
        <span class="eyebrow">COMMUNITY CARE</span>
        <h1>{{ mode === 'catalog' ? '服务目录与人员' : '账号与预约授权' }}</h1>
        <p class="muted">
          {{
            mode === 'catalog'
              ? '维护服务内容、提供方与可安排人员。'
              : '先核验身份，再建立本人绑定或记录明确授权。'
          }}
        </p>
      </div>
      <el-button type="primary" :disabled="state.busy || !!state.editor" @click="state.edit(null)"
        >＋ 新建{{ resourceLabels[state.kind] }}</el-button
      >
    </header>
    <div class="booking-tabs" role="tablist">
      <button
        v-for="tab in tabs"
        :key="tab"
        role="tab"
        :aria-selected="state.kind === tab"
        :disabled="state.busy"
        @click="state.changeKind(tab)"
      >
        {{ resourceLabels[tab] }}
      </button>
    </div>
    <p v-if="mode === 'access'" class="booking-warning">
      核验依据填写内部记录编号；家属关系本身不代表老人同意。这里的授权仅用于预约，不包含健康档案。
    </p>
    <p v-if="state.kind === 'workers'" class="booking-muted">
      请先在“账号与预约授权”中核验开通服务人员账号。已有未结束安排的人员，须先改派或终止后再调整能力。
    </p>
    <div v-if="state.error" class="booking-error" role="alert">{{ state.error }}</div>
    <section v-if="state.enrollment" class="booking-panel">
      <h2>一次性开通码</h2>
      <p>账号：{{ state.enrollment.accountId }} · 有效至 {{ time(state.enrollment.expiresAt) }}</p>
      <p class="booking-warning">{{ state.enrollment.token }}</p>
      <p class="booking-muted">请交给已核验的本人，在微信小程序“我的”输入。重新签发后旧码失效。</p>
      <button class="booking-link" @click="state.enrollment = null">隐藏开通码</button>
    </section>
    <ResourceEditor
      v-if="state.editor"
      :key="state.editor.key"
      :title="resourceLabels[state.kind]"
      :initial="state.editor.row"
      :fields="state.fields"
      :busy="state.busy"
      :uncertain="state.uncertain"
      @submit="state.save"
      @close="state.editor = null"
    />
    <div class="booking-actions">
      <button :disabled="state.busy" @click="state.load">刷新列表</button>
    </div>
    <ResourceTable
      :kind="state.kind"
      :rows="state.rows"
      :page="state.page"
      :total="state.total"
      :busy="state.busy || !!state.editor"
      @edit="state.edit"
      @issue="state.issue"
      @revoke="state.revoke"
      @page="paginate"
    />
  </div>
</template>
