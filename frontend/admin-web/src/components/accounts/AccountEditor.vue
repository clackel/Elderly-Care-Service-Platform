<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  reactive,
  shallowRef,
  useTemplateRef,
  watch,
} from 'vue'
import { ElDialog } from 'element-plus'
import 'element-plus/es/components/dialog/style/css'
import type { AdminAccount } from '../../api/adminAccounts'
import {
  adminRoleLabels,
  newAccountDraft,
  validateAccountDraft,
  type AccountDraft,
  type AccountErrors,
  type EditorMode,
} from './accountModel'

const props = defineProps<{
  mode: EditorMode
  account: AdminAccount | null
  currentId: string
  saving: boolean
  uncertain: boolean
  conflict: boolean
  error: string
}>()
const emit = defineEmits<{ submit: [draft: AccountDraft]; close: []; dirty: [value: boolean] }>()
const draft = reactive(newAccountDraft(props.account))
const errors = shallowRef<AccountErrors>({})
const form = useTemplateRef<HTMLFormElement>('accountForm')
const locked = computed(() => props.saving || props.uncertain || props.conflict)
const ownAccount = computed(() => props.account?.id === props.currentId)
const title = computed(() =>
  props.mode === 'create'
    ? '创建后台账号'
    : props.mode === 'reset'
      ? '重置账号密码'
      : '编辑后台账号',
)
const baseline = JSON.stringify(draft)

// 向容器报告草稿变化；不把密码或显示名持久化到浏览器存储。
watch(draft, () => emit('dirty', JSON.stringify(draft) !== baseline))

/** 校验当前操作字段并发出独立草稿；未知结果仅复用容器中的原写入请求。 */
async function submit() {
  if (props.saving || props.conflict) return
  if (!props.uncertain) {
    errors.value = validateAccountDraft(draft, props.mode)
    if (Object.keys(errors.value).length) {
      await nextTick()
      form.value?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
      return
    }
  }
  emit('submit', { ...draft })
}

onBeforeUnmount(() => {
  draft.password = ''
  draft.confirmation = ''
})
</script>

<template>
  <ElDialog
    :model-value="true"
    :title="title"
    width="min(600px, calc(100vw - 24px))"
    top="5vh"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    :before-close="() => emit('close')"
  >
    <p class="account-help">
      {{
        mode === 'create'
          ? '账号固定属于当前社区，首次登录必须修改初始密码。'
          : '账号：' + account?.username + ' · 所属社区不可修改'
      }}
    </p>
    <form
      id="account-editor-form"
      ref="accountForm"
      novalidate
      autocomplete="off"
      @submit.prevent="submit"
    >
      <fieldset class="account-fieldset" :disabled="locked">
        <template v-if="mode !== 'reset'">
          <label v-if="mode === 'create'" class="account-field" for="account-username"
            >登录账号
            <input
              id="account-username"
              v-model="draft.username"
              class="account-control"
              maxlength="64"
              autocomplete="off"
              :aria-invalid="!!errors.username"
              aria-describedby="username-help"
            />
            <small id="username-help" class="account-help"
              >3至64位，支持字母、数字、点、下划线和连字符。保存时统一为小写。</small
            >
            <span v-if="errors.username" class="account-field-error">{{ errors.username }}</span>
          </label>
          <label class="account-field" for="account-display-name"
            >显示名
            <input
              id="account-display-name"
              v-model="draft.displayName"
              class="account-control"
              maxlength="100"
              :aria-invalid="!!errors.displayName"
            />
            <span v-if="errors.displayName" class="account-field-error">{{
              errors.displayName
            }}</span>
          </label>
          <label class="account-field" for="account-role"
            >角色
            <select
              id="account-role"
              v-model="draft.role"
              class="account-control"
              :disabled="ownAccount"
              :aria-invalid="!!errors.role"
            >
              <option v-for="(label, role) in adminRoleLabels" :key="role" :value="role">
                {{ label }}
              </option>
            </select>
            <span v-if="errors.role" class="account-field-error">{{ errors.role }}</span>
          </label>
          <label v-if="mode === 'update'" class="account-field" for="account-status"
            >账号状态
            <select
              id="account-status"
              v-model="draft.status"
              class="account-control"
              :disabled="ownAccount"
              :aria-invalid="!!errors.status"
            >
              <option value="ACTIVE">启用</option>
              <option value="DISABLED">停用</option>
            </select>
            <span v-if="errors.status" class="account-field-error">{{ errors.status }}</span>
          </label>
          <p v-if="ownAccount" class="account-help">不能停用自己或调整自己的管理员角色。</p>
        </template>
        <template v-if="mode !== 'update'">
          <label class="account-field" for="account-password"
            >{{ mode === 'create' ? '初始密码' : '重置密码' }}
            <input
              id="account-password"
              v-model="draft.password"
              class="account-control"
              type="password"
              maxlength="64"
              autocomplete="new-password"
              :aria-invalid="!!errors.password"
              aria-describedby="account-password-help"
            />
            <span v-if="errors.password" class="account-field-error">{{ errors.password }}</span>
          </label>
          <label class="account-field" for="account-password-confirm"
            >确认密码
            <input
              id="account-password-confirm"
              v-model="draft.confirmation"
              class="account-control"
              type="password"
              maxlength="64"
              autocomplete="new-password"
              :aria-invalid="!!errors.confirmation"
            />
            <span v-if="errors.confirmation" class="account-field-error">{{
              errors.confirmation
            }}</span>
          </label>
          <p id="account-password-help" class="account-help">
            密码为12至64个字符，较多中文或特殊字符可能需要缩短。请通过可信方式交给账号本人，登录后由本人修改。
          </p>
        </template>
      </fieldset>
      <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
      <p v-if="uncertain" class="account-warning" role="status">
        操作结果尚未确认，表单已锁定。请重试原请求，相同请求不会产生重复结果。
      </p>
      <p v-if="conflict" class="account-warning">
        账号版本已发生变化。请关闭表单并从刷新后的列表重新选择，避免覆盖他人的修改。
      </p>
    </form>
    <template #footer
      ><el-button :disabled="saving" @click="emit('close')">{{
        conflict ? '关闭并刷新' : '取消'
      }}</el-button
      ><el-button
        v-if="!conflict"
        type="primary"
        native-type="submit"
        form="account-editor-form"
        :loading="saving"
        >{{
          uncertain
            ? '重试原请求'
            : mode === 'create'
              ? '创建账号'
              : mode === 'reset'
                ? '确认重置'
                : '保存修改'
        }}</el-button
      ></template
    >
  </ElDialog>
</template>
