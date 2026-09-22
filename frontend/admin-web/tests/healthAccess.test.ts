import test from 'node:test'
import assert from 'node:assert/strict'
import { canAssistHealth } from '../../shared/health.ts'

/** 平台管理、工作人员、审计及移动角色没有社区健康代录入口；运营账号也必须有社区。 */
test('健康协助入口不继承管理角色权限', () => {
  assert.equal(canAssistHealth('COMMUNITY_OPERATOR', '1'), true)
  assert.equal(canAssistHealth('COMMUNITY_OPERATOR', null), false)
  for (const role of ['PLATFORM_ADMIN', 'STAFF', 'AUDITOR', 'DUTY_OFFICER', 'FAMILY', 'ELDER', undefined]) {
    assert.equal(canAssistHealth(role, '1'), false)
  }
})
