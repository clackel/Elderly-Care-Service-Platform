<script setup lang="ts">
import { computed, nextTick, reactive, shallowRef, useTemplateRef, watch } from 'vue'
import { ElDialog } from 'element-plus'
import 'element-plus/es/components/dialog/style/css'
import type { ElderProfile } from '../../api/elders'
import {
  fieldLabels,
  genderLabels,
  livingLabels,
  normalizeProfile,
  todayInShanghai,
  validateProfile,
  type FormErrors,
} from './elderModel'

const props = defineProps<{
  initial: ElderProfile
  editing: boolean
  saving: boolean
  reloading: boolean
  error: string
  conflict: boolean
  uncertain: boolean
}>()
const emit = defineEmits<{
  submit: [profile: ElderProfile]
  close: []
  reload: []
  dirty: [value: boolean]
}>()
const profile = reactive({ ...props.initial })
const errors = shallowRef<FormErrors>({})
const form = useTemplateRef<HTMLFormElement>('form')
const baseline = JSON.stringify(normalizeProfile(props.initial))
const locked = computed(() => props.saving || props.reloading || props.uncertain)
type TextField =
  | 'name'
  | 'birthDate'
  | 'phone'
  | 'address'
  | 'emergencyContactName'
  | 'emergencyContactPhone'
  | 'emergencyContactRelation'
const groups: {
  title: string
  description: string
  fields: { key: TextField; type?: string; max?: number; optional?: boolean; wide?: boolean }[]
}[] = [
  {
    title: '基础资料',
    description: '填写开展社区服务所需的基本信息。',
    fields: [
      { key: 'name', max: 50 },
      { key: 'birthDate', type: 'date' },
      { key: 'phone', type: 'tel', max: 20, optional: true },
      { key: 'address', max: 200, wide: true },
    ],
  },
  {
    title: '紧急联系人',
    description: '请核对联系电话，确保紧急情况下能够联系。',
    fields: [
      { key: 'emergencyContactName', max: 50 },
      { key: 'emergencyContactRelation', max: 30 },
      { key: 'emergencyContactPhone', type: 'tel', max: 20, wide: true },
    ],
  },
]

// 仅比较规范化草稿；通知父级进行关闭和路由离开保护，不持久化个人信息。
watch(profile, () => emit('dirty', JSON.stringify(normalizeProfile(profile)) !== baseline), {
  deep: true,
})

/** 校验所有字段并聚焦首个错误；有效表单发出独立的规范化快照。 */
async function submit() {
  if (props.saving || props.reloading || props.conflict) return
  errors.value = validateProfile(profile)
  if (Object.keys(errors.value).length) {
    await nextTick()
    form.value?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
    return
  }
  emit('submit', normalizeProfile(profile))
}
</script>

<template>
  <ElDialog
    :model-value="true"
    :title="editing ? '编辑老人档案' : '新建老人档案'"
    width="min(720px, calc(100vw - 24px))"
    top="5vh"
    class="elder-dialog"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving && !reloading"
    :show-close="!saving && !reloading"
    :before-close="() => emit('close')"
  >
    <p class="elder-editor-intro">
      带 <span class="elder-required">*</span> 的项目为必填项。资料仅供授权社区人员用于养老服务。
    </p>
    <form id="elder-editor-form" ref="form" novalidate autocomplete="off" @submit.prevent="submit">
      <fieldset
        v-for="(group, index) in groups"
        :key="group.title"
        class="elder-fieldset"
        :disabled="locked"
      >
        <legend>{{ group.title }}</legend>
        <p class="elder-help">{{ group.description }}</p>
        <div class="elder-form-grid">
          <div
            v-for="field in group.fields"
            :key="field.key"
            class="elder-field"
            :class="{ 'elder-wide': field.wide }"
          >
            <label :for="'elder-' + field.key"
              >{{ fieldLabels[field.key] }}
              <span v-if="!field.optional" class="elder-required" aria-hidden="true">*</span
              ><span v-else class="elder-help">（选填）</span></label
            >
            <input
              :id="'elder-' + field.key"
              v-model="profile[field.key]"
              class="elder-control"
              :type="field.type || 'text'"
              :maxlength="field.max"
              :min="field.type === 'date' ? '1900-01-01' : undefined"
              :max="field.type === 'date' ? todayInShanghai() : undefined"
              :required="!field.optional"
              :aria-invalid="!!errors[field.key]"
              :aria-describedby="errors[field.key] ? 'error-' + field.key : undefined"
            />
            <span v-if="errors[field.key]" :id="'error-' + field.key" class="elder-field-error">{{
              errors[field.key]
            }}</span>
          </div>
          <template v-if="index === 0">
            <div class="elder-field">
              <label for="elder-gender">性别</label
              ><select id="elder-gender" v-model="profile.gender" class="elder-control">
                <option v-for="(label, value) in genderLabels" :key="value" :value="value">
                  {{ label }}
                </option>
              </select>
            </div>
            <div class="elder-field">
              <label for="elder-living">居住情况</label
              ><select id="elder-living" v-model="profile.livingArrangement" class="elder-control">
                <option v-for="(label, value) in livingLabels" :key="value" :value="value">
                  {{ label }}
                </option>
              </select>
            </div>
          </template>
        </div>
      </fieldset>
      <fieldset class="elder-fieldset" :disabled="locked">
        <legend>服务备注</legend>
        <div class="elder-field">
          <label for="elder-remark">备注 <span class="elder-help">（选填）</span></label
          ><textarea
            id="elder-remark"
            v-model="profile.remark"
            class="elder-control"
            rows="3"
            maxlength="500"
            :aria-invalid="!!errors.remark"
            aria-describedby="elder-remark-help"
          />
          <p id="elder-remark-help" class="elder-help">
            仅填写服务注意事项，请勿记录病史等健康详情。{{ profile.remark?.length ?? 0 }} / 500
          </p>
          <span v-if="errors.remark" class="elder-field-error">{{ errors.remark }}</span>
        </div>
      </fieldset>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <p v-if="uncertain" class="elder-warning" role="status">
        尚未确认建档结果，资料已暂时锁定。请点击“重试提交”核实结果，相同请求不会重复建档。
      </p>
      <p v-if="conflict" class="elder-warning">
        档案已被更新或归档。请重新加载最新资料，再确认修改；当前草稿不会自动覆盖其他人的修改。
      </p>
    </form>
    <template #footer
      ><div class="elder-editor-footer">
        <el-button :disabled="saving || reloading" @click="emit('close')">取消</el-button
        ><el-button v-if="conflict" type="primary" :loading="reloading" @click="emit('reload')"
          >重新加载最新档案</el-button
        ><el-button
          v-else
          type="primary"
          native-type="submit"
          form="elder-editor-form"
          :loading="saving"
          :disabled="reloading"
          >{{ uncertain ? '重试提交' : editing ? '保存修改' : '创建档案' }}</el-button
        >
      </div></template
    >
  </ElDialog>
</template>
