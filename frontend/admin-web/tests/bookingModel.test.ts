import assert from 'node:assert/strict'
import test from 'node:test'
import { makeSpecific, specificLines } from '../../shared/booking.ts'

test('切换类别只提交相关结构化字段，并转换份数', () => {
  assert.deepEqual(
    makeSpecific('MEAL', {
      mealDate: '2026-10-02',
      meal: 'LUNCH',
      portions: '2',
      hospital: '合成医院',
    }),
    {
      mealDate: '2026-10-02',
      meal: 'LUNCH',
      portions: 2,
    },
  )
})

test('康复否选项保留布尔false，显示对应中文', () => {
  const data = makeSpecific('REHABILITATION', { location: 'HOME', assessmentRequired: 'false' })
  assert.equal(data.assessmentRequired, false)
  assert.deepEqual(specificLines('REHABILITATION', data), [
    { label: '服务地点', value: '家中' },
    { label: '是否需要先评估', value: '不需要' },
  ])
})

test('陪诊时间转换为带时区时间，空的可选资料不扩散', () => {
  const data = makeSpecific('ESCORT', {
    appointmentTime: '2026-10-02T10:30:00+08:00',
    hospital: ' 合成医院 ',
    precautions: '不相关的合成备注',
  })
  assert.deepEqual(data, { appointmentTime: '2026-10-02T02:30:00.000Z', hospital: '合成医院' })
})
