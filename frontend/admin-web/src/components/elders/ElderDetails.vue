<script setup lang="ts">
import { ElDrawer } from 'element-plus'
import 'element-plus/es/components/drawer/style/css'
import type { ElderDetail } from '../../api/elders'
import { formatTime, genderLabels, livingLabels } from './elderModel'
import ElderHistory from './ElderHistory.vue'

defineProps<{ detail: ElderDetail | null; loading: boolean; error: string; busy: boolean }>()
const emit = defineEmits<{ close: []; retry: []; edit: [detail: ElderDetail]; transition: [] }>()
</script>

<template>
  <ElDrawer
    :model-value="true"
    title="老人档案详情"
    size="min(640px, 100vw)"
    class="elder-drawer"
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    :show-close="!busy"
    :before-close="() => emit('close')"
  >
    <div v-if="loading" class="elder-placeholder" role="status">正在读取档案详情…</div>
    <div v-if="error" class="elder-detail-error" role="alert">
      <el-alert :title="error" type="error" :closable="false" /><el-button
        :disabled="busy || loading"
        @click="emit('retry')"
        >重新加载档案</el-button
      >
    </div>
    <template v-if="detail && !loading">
      <div class="elder-profile-heading">
        <div class="elder-avatar large" aria-hidden="true">
          {{ detail.profile.name.slice(0, 1) }}
        </div>
        <div>
          <h2>{{ detail.profile.name }}</h2>
          <p>{{ genderLabels[detail.profile.gender] }} · {{ detail.profile.birthDate }} 出生</p>
        </div>
        <span class="elder-badge" :class="{ archived: detail.status === 'ARCHIVED' }">{{
          detail.status === 'ACTIVE' ? '在册' : '已归档'
        }}</span>
      </div>
      <p v-if="detail.status === 'ARCHIVED'" class="elder-warning">
        档案已归档，资料与变更记录仍保留。恢复后可继续编辑。
      </p>
      <section class="elder-detail-section">
        <h3>基础资料</h3>
        <dl class="elder-description">
          <div>
            <dt>联系电话</dt>
            <dd>{{ detail.profile.phone || '未填写' }}</dd>
          </div>
          <div>
            <dt>居住情况</dt>
            <dd>{{ livingLabels[detail.profile.livingArrangement] }}</dd>
          </div>
          <div class="elder-wide">
            <dt>服务地址</dt>
            <dd>{{ detail.profile.address }}</dd>
          </div>
        </dl>
      </section>
      <section class="elder-detail-section">
        <h3>紧急联系人</h3>
        <dl class="elder-description">
          <div>
            <dt>联系人姓名</dt>
            <dd>{{ detail.profile.emergencyContactName }}</dd>
          </div>
          <div>
            <dt>与老人关系</dt>
            <dd>{{ detail.profile.emergencyContactRelation }}</dd>
          </div>
          <div class="elder-wide">
            <dt>联系人电话</dt>
            <dd>{{ detail.profile.emergencyContactPhone }}</dd>
          </div>
        </dl>
      </section>
      <section class="elder-detail-section">
        <h3>服务备注</h3>
        <p class="elder-remark">{{ detail.profile.remark || '暂无备注' }}</p>
      </section>
      <div class="elder-record-meta">
        <span>建立时间 {{ formatTime(detail.createdAt) }}</span
        ><span>最近更新 {{ formatTime(detail.updatedAt) }}</span>
      </div>
      <ElderHistory :id="detail.id" :key="detail.id" :version="detail.version" />
    </template>
    <template #footer
      ><div class="elder-detail-footer">
        <el-button :disabled="busy" @click="emit('close')">关闭</el-button
        ><template v-if="detail && !loading"
          ><el-button :disabled="busy || !!error" :loading="busy" @click="emit('transition')">{{
            detail.status === 'ACTIVE' ? '归档档案' : '恢复档案'
          }}</el-button
          ><el-button
            v-if="detail.status === 'ACTIVE'"
            type="primary"
            :disabled="busy || !!error"
            @click="emit('edit', detail)"
            >编辑资料</el-button
          ></template
        >
      </div></template
    >
  </ElDrawer>
</template>
