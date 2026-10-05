import assert from 'node:assert/strict'
import test from 'node:test'
import {
  accountMutation,
  isUncertainAccountWrite,
  newAccountDraft,
  passwordError,
  revokesHealthGrants,
  validateAccountDraft,
} from '../src/components/accounts/accountModel.ts'
import type { AdminAccount } from '../src/api/adminAccounts.ts'

/** 创建独立的合成社区运营账号，不使用实际账号资料或凭据。 */
function fixture(): AdminAccount {
  return {
    id: '7001', username: 'synthetic.operator', displayName: '合成运营人员',
    role: 'COMMUNITY_OPERATOR', communityId: '7000', status: 'ACTIVE', version: 4,
    passwordChangeRequired: false,
  }
}

test('密码同时受字符数与UTF-8字节上限约束', () => {
  assert.ok(passwordError(' '.repeat(12)))
  assert.ok(passwordError('a'.repeat(11)))
  assert.equal(passwordError('a'.repeat(12)), '')
  assert.equal(passwordError('a'.repeat(64)), '')
  assert.ok(passwordError('a'.repeat(65)))
  assert.equal(passwordError('合'.repeat(24)), '')
  assert.match(passwordError('合'.repeat(25)), /密码过长/)
})

test('后台账号表单验证合法账号、显示名和密码确认，不接受移动端角色', () => {
  const draft = { ...newAccountDraft(null), username: ' Demo.Operator ', displayName: '合成管理人员', password: 'synthetic-password', confirmation: 'synthetic-password' }
  assert.deepEqual(validateAccountDraft(draft, 'create'), {})
  assert.ok(validateAccountDraft({ ...draft, username: '_invalid' }, 'create').username)
  assert.ok(validateAccountDraft({ ...draft, displayName: '名'.repeat(101) }, 'create').displayName)
  assert.ok(validateAccountDraft({ ...draft, confirmation: 'different-password' }, 'create').confirmation)
  assert.ok(validateAccountDraft({ ...draft, role: 'STAFF' as 'AUDITOR' }, 'create').role)
})

test('写入快照固定原版本和请求编号，后续输入不污染载荷且编辑不携带密码', () => {
  const account = fixture()
  const draft = { ...newAccountDraft(account), password: 'synthetic-password', confirmation: 'synthetic-password' }
  const reset = accountMutation(draft, 'reset', account, 'request-1')
  draft.password = 'another-password'
  account.version = 5
  assert.deepEqual(reset, { kind: 'reset', requestId: 'request-1', id: '7001', version: 4, password: 'synthetic-password' })
  const update = accountMutation(draft, 'update', account, 'request-2')
  assert.equal('password' in update, false)
  const created = accountMutation({ ...draft, username: ' SYNTHETIC.ADMIN ' }, 'create', null, 'request-3')
  assert.equal(created.kind === 'create' && created.username, 'synthetic.admin')
})

test('运营离开角色或停用必须提示撤销授权，明确失败与未知结果分别处理', () => {
  const account = fixture()
  const draft = newAccountDraft(account)
  assert.equal(revokesHealthGrants(account, draft), false)
  assert.equal(revokesHealthGrants(account, { ...draft, role: 'AUDITOR' }), true)
  assert.equal(revokesHealthGrants(account, { ...draft, status: 'DISABLED' }), true)
  assert.equal(revokesHealthGrants({ ...account, role: 'AUDITOR' }, draft), false)
  for (const status of [0, 200, 502, 503]) assert.equal(isUncertainAccountWrite(status), true)
  for (const status of [400, 401, 403, 409, 429]) assert.equal(isUncertainAccountWrite(status), false)
})
