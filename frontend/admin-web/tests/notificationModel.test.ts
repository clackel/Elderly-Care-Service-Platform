import assert from 'node:assert/strict'
import test from 'node:test'
import { canViewAdminNotifications, notificationBookingId, notificationTitle } from '../../shared/notifications.ts'

test('移交提示始终隐藏预约入口，缺失目标也不得生成详情导航', () => {
  assert.equal(notificationBookingId({ action: 'TASK_TRANSFERRED', bookingId: '7001' }), null)
  assert.equal(notificationBookingId({ action: 'CONFIRM', bookingId: null }), null)
  assert.equal(notificationBookingId({ action: 'CONFIRM', bookingId: '  ' }), null)
  assert.equal(notificationBookingId({ action: 'CONFIRM', bookingId: '7001' }), '7001')
})

test('消息使用固定模板，未知事件和对象原型属性不会泄露原始消息', () => {
  assert.equal(notificationTitle('EXCEPTION'), '服务出现异常，待协调处理')
  for (const unknown of ['未公开的合成异常正文', '__proto__', 'toString']) {
    const text = notificationTitle(unknown)
    assert.equal(text, '预约状态有更新，请查看当前预约')
    assert.equal(text.includes(unknown), false)
  }
})

test('后台消息仅对本社区运营和管理员开放，强制改密期间关闭入口', () => {
  for (const role of ['COMMUNITY_OPERATOR', 'PLATFORM_ADMIN']) {
    assert.equal(canViewAdminNotifications(role, '7000', false), true)
    assert.equal(canViewAdminNotifications(role, null, false), false)
    assert.equal(canViewAdminNotifications(role, '7000', true), false)
  }
  for (const role of ['DUTY_OFFICER', 'AUDITOR', 'ELDER', 'FAMILY', 'STAFF', undefined]) {
    assert.equal(canViewAdminNotifications(role, '7000', false), false)
  }
})
