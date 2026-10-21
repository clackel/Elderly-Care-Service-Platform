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
import { passwordError } from './accountModel'

const props = defineProps<{ busy: boolean; uncertain: boolean; error: string }>()
const emit = defineEmits<{
  submit: [input: { currentPassword: string; newPassword: string }]
  dirty: [value: boolean]
}>()
const draft = reactive({ currentPassword: '', newPassword: '', confirmation: '' })
const errors = shallowRef<Partial<Record<keyof typeof draft, string>>>({})
const form = useTemplateRef<HTMLFormElement>('passwordForm')
const locked = computed(() => props.busy || props.uncertain)

// 仅报告是否存在密码草稿，不向容器传递未提交的密码内容。
watch(draft, () =>
  emit('dirty', !!(draft.currentPassword || draft.newPassword || draft.confirmation)),
)

/** 校验新密码与确认字段；未知结果时由上层复用第一次请求的原始载荷。 */
async function submit() {
  if (props.busy) return
  if (!props.uncertain) {
    errors.value = {}
    if (!draft.currentPassword.trim()) errors.value.currentPassword = '请输入当前密码'
    const message = passwordError(draft.newPassword)
    if (message) errors.value.newPassword = message
    else if (draft.newPassword === draft.currentPassword)
      errors.value.newPassword = '新密码不能与当前密码相同'
    if (draft.confirmation !== draft.newPassword)
      errors.value.confirmation = '两次输入的新密码不一致'
    if (Object.keys(errors.value).length) {
      await nextTick()
      form.value?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
      return
    }
  }
  emit('submit', { currentPassword: draft.currentPassword, newPassword: draft.newPassword })
}

onBeforeUnmount(() => {
  draft.currentPassword = ''
  draft.newPassword = ''
  draft.confirmation = ''
})
</script>

<template>
  <form ref="passwordForm" novalidate @submit.prevent="submit">
    <fieldset class="account-fieldset" :disabled="locked">
      <label class="account-field" for="current-password"
        >当前密码
        <input
          id="current-password"
          v-model="draft.currentPassword"
          class="account-control"
          type="password"
          autocomplete="current-password"
          maxlength="64"
          :aria-invalid="!!errors.currentPassword"
        />
        <span v-if="errors.currentPassword" class="account-field-error">{{
          errors.currentPassword
        }}</span>
      </label>
      <label class="account-field" for="new-password"
        >新密码
        <input
          id="new-password"
          v-model="draft.newPassword"
          class="account-control"
          type="password"
          autocomplete="new-password"
          maxlength="64"
          :aria-invalid="!!errors.newPassword"
          aria-describedby="new-password-help"
        />
        <span v-if="errors.newPassword" class="account-field-error">{{ errors.newPassword }}</span>
      </label>
      <label class="account-field" for="confirm-new-password"
        >确认新密码
        <input
          id="confirm-new-password"
          v-model="draft.confirmation"
          class="account-control"
          type="password"
          autocomplete="new-password"
          maxlength="64"
          :aria-invalid="!!errors.confirmation"
        />
        <span v-if="errors.confirmation" class="account-field-error">{{
          errors.confirmation
        }}</span>
      </label>
    </fieldset>
    <p id="new-password-help" class="account-help">
      新密码需为12至64个字符；较多中文或特殊字符可能需要缩短。修改成功后，所有已登录会话失效，请使用新密码登录。
    </p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <p v-if="uncertain" class="account-warning" role="status">
      改密结果尚未确认，输入已锁定。可重试原请求；若提示会话已失效，请重新登录核对新密码。
    </p>
    <div class="password-actions">
      <el-button native-type="submit" type="primary" :loading="busy">{{
        uncertain ? '重试原请求' : '修改密码并重新登录'
      }}</el-button>
    </div>
  </form>
</template>
