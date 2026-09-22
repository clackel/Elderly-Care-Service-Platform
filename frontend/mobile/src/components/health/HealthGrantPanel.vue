<script setup lang="ts">
import { shallowRef, watch } from 'vue'
import { consentText, healthTime, type HealthElder, type HealthGrant, type HealthRecipient, type HealthScope, type HealthGrantInput } from '../../api/health'
const props = defineProps<{ elder: HealthElder | null; grants: HealthGrant[]; recipient: HealthRecipient | null; page: number; total: number; disabled: boolean }>()
const emit = defineEmits<{ resolve: [id: string, scope: HealthScope]; reset: []; grant: [input: HealthGrantInput]; revoke: [grant: HealthGrant]; page: [page: number] }>()
const accountId = shallowRef(''), scope = shallowRef<HealthScope>('FAMILY_READ'), days = shallowRef(30), accepted = shallowRef(false), error = shallowRef('')
/** 修改目标或范围后必须重新核对身份并再次明确同意。 */
watch([accountId, scope], () => { accepted.value = false; error.value = ''; emit('reset') })
watch(() => props.recipient, () => { accepted.value = false })
/** 仅发起受权人主动提供编号的精确核对，不展示全社区目录。 */
function resolve() {
  if (!/^[1-9]\d{0,18}$/.test(accountId.value.trim())) { error.value = '请填写受权人提供的有效账号编号'; return }
  error.value = ''; emit('resolve', accountId.value.trim(), scope.value)
}
/** 将已核对身份和明确同意一起提交给页面状态层。 */
function grant() {
  if (!props.elder || !props.recipient || !accepted.value) { error.value = '请先核对账号并勾选同意'; return }
  emit('grant', { elderId: props.elder.id, recipientId: props.recipient.id, scope: scope.value, days: days.value, accepted: true, consentVersion: 'health-v1' })
}
/** 撤销或放弃前说明后续请求立即停止，已返回内容无法远程收回。 */
function revoke(grant: HealthGrant) {
  uni.showModal({ title: '撤销或放弃该健康授权？', content: '后续访问将停止，已保存的健康记录继续保留。', success: (r) => {
    if (r.confirm && !props.disabled) emit('revoke', grant)
  } })
}
</script>
<template>
  <view class="mobile-card">
    <text class="card-title">独立健康授权</text>
    <text class="body-copy">预约授权不包含健康权限。撤销、到期或本人绑定失效后停止后续访问；已返回的信息无法远程收回。</text>
    <view v-if="elder?.canGrant">
      <text class="body-copy">为{{ elder.name }}授予健康权限</text>
      <picker :disabled="disabled" :range="['家属查看当前记录及趋势','指定社区人员协助录入']" :value="scope === 'FAMILY_READ' ? 0 : 1" @change="scope = Number($event.detail.value) === 0 ? 'FAMILY_READ' : 'COMMUNITY_ASSIST'"><view class="health-control">{{ scope === 'FAMILY_READ' ? '家属只读' : '社区协助录入' }} ▾</view></picker>
      <text class="body-copy">受权账号编号（请对方主动提供）</text><input v-model="accountId" class="health-control" :disabled="disabled" :maxlength="19" placeholder="填写账号编号" />
      <button :disabled="disabled" @click="resolve">核对受权人</button>
      <view v-if="recipient" class="notice-card">
        <text class="body-copy">已核对：{{ recipient.displayName }} · {{ recipient.role === 'FAMILY' ? '家属' : '社区工作人员' }}</text><text class="body-copy">账号：{{ recipient.id }}</text>
        <text class="body-copy">{{ consentText[scope] }}</text>
        <text class="body-copy">再次授权会撤销同一对象同一范围的旧授权，并建立新期限。</text>
        <picker :disabled="disabled" :range="['7天','30天','90天','365天']" :value="[7,30,90,365].indexOf(days)" @change="days = [7,30,90,365][Number($event.detail.value)]!"><view class="health-control">有效期{{ days }}天 ▾</view></picker>
        <checkbox-group @change="accepted = $event.detail.value.includes('accepted')"><label><checkbox value="accepted" :checked="accepted" :disabled="disabled" />我已核对受权人，并同意上述范围及期限</label></checkbox-group>
        <button :disabled="disabled || !accepted" @click="grant">确认授予健康权限</button>
      </view>
      <text v-if="error" class="health-error">{{ error }}</text>
    </view>
    <text v-else class="body-copy">{{ elder?.archived ? '档案已归档，可查看和撤销已有授权。' : '仅有效绑定的老人本人可以授予权限。' }}</text>
    <view v-for="grant in grants" :key="grant.id" class="health-history">
      <text class="body-copy">老人编号{{ grant.elderId }} · 受权账号{{ grant.recipientId }}</text>
      <text class="body-copy">{{ grant.scope === 'FAMILY_READ' ? '家属查看当前有效记录与趋势' : '指定人员协助自己的代录记录' }}</text>
      <text class="body-copy">至{{ healthTime(grant.expiresAt) }} · {{ grant.revoked ? '已撤销' : grant.effective ? '有效' : '已到期或关系失效' }}</text>
      <button v-if="!grant.revoked" :disabled="disabled" @click="revoke(grant)">撤销 / 放弃此授权</button>
    </view>
    <text v-if="!grants.length" class="body-copy">暂无健康授权。</text>
    <view class="health-actions"><button :disabled="disabled || page <= 1" @click="emit('page', page - 1)">上一页授权</button><button :disabled="disabled || page * 10 >= total" @click="emit('page', page + 1)">下一页授权</button></view>
  </view>
</template>

