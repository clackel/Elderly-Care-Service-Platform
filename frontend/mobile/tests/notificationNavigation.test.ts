import test from 'node:test'
import assert from 'node:assert/strict'
import { createBookingNavigation } from '../src/stores/bookingNavigationState.ts'

/** 消息导航只能由同一账号消费一次，不携带详情或长期保存历史目标。 */
test('预约导航意图绑定账号且只能消费一次', () => {
  const navigation = createBookingNavigation()
  navigation.queue('10001', '20001')
  assert.equal(navigation.consume('10001'), '20001')
  assert.equal(navigation.consume('10001'), null)
  navigation.queue('10001', '20002')
  assert.equal(navigation.consume('10002'), null)
  assert.equal(navigation.consume('10001'), null)
})

/** 注销或失败清理阻止同账号再次登录后打开旧会话留下的意图。 */
test('会话清理后不会恢复旧预约导航', () => {
  const navigation = createBookingNavigation()
  navigation.queue('10001', '20001')
  navigation.clear()
  assert.equal(navigation.consume('10001'), null)
})
