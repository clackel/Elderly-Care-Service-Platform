import test from 'node:test'
import assert from 'node:assert/strict'
import { healthDraft, toMeasurement, healthDateRange, healthTime, healthEpoch, healthWriteSnapshot } from '../../shared/health.ts'

/** 不同设备时区下日期仍按上海自然日查询，截止边界包含用户选中的整天。 */
test('日期范围固定为北京时间且采用左闭右开', () => {
  assert.deepEqual(healthDateRange('2020-01-02', '2020-01-02'), { from: '2020-01-01T16:00:00.000Z', to: '2020-01-02T16:00:00.000Z' })
  assert.equal(healthTime('2020-01-01T16:00:00Z'), '2020-01-02 00:00:00')
})
/** 当前指标只携带对应数值；禁止通过宽松数字转换吞掉无效精度。 */
test('测量只提交当前类型且拒绝额外精度', () => {
  const draft = healthDraft({ type: 'WEIGHT', measuredAt: '2020-01-01T00:00:00Z', weight: 60.25 })
  draft.heartRate = '70'
  assert.deepEqual(toMeasurement(draft), { type: 'WEIGHT', measuredAt: '2020-01-01T00:00:00.000Z', weight: 60.25 })
  draft.weight = '60.251'
  assert.throws(() => toMeasurement(draft))
  draft.weight = '1e1'
  assert.throws(() => toMeasurement(draft))
})
/** 血压不允许缺失一项，血糖保留明确场景。 */
test('血压成对并保留血糖场景', () => {
  const draft = healthDraft({ type: 'BLOOD_PRESSURE', measuredAt: '2020-01-01T00:00:00Z', systolic: 120 })
  assert.throws(() => toMeasurement(draft))
  draft.type = 'BLOOD_GLUCOSE'; draft.glucose = '5.6'; draft.glucoseScene = 'FASTING'
  assert.equal(toMeasurement(draft).glucoseScene, 'FASTING')
  assert.equal(toMeasurement(draft).systolic, undefined)
})
/** 请求快照不随可变草稿改变，重试复用相同编号和正文。 */
test('未知结果重试使用独立固定快照', () => {
  const input = { measurement: { weight: 60 } }
  const pending = healthWriteSnapshot('/health/elders/1/records', input)
  input.measurement.weight = 99
  const data = pending.data as typeof input & { requestId: string }
  assert.equal(data.measurement.weight, 60)
  assert.match(data.requestId, /^[0-9a-f-]{36}$/)
})
/** 页面退出或切换使所有先前请求失效，阻止旧资料回填。 */
test('旧请求不能越过页面清理世代', () => {
  const epoch = healthEpoch(), old = epoch.next()
  assert.equal(epoch.current(old), true)
  epoch.next()
  assert.equal(epoch.current(old), false)
})

