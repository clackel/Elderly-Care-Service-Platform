import assert from 'node:assert/strict'
import test from 'node:test'
import {
  emptyProfile,
  normalizeProfile,
  prepareCreate,
  todayInShanghai,
  validateProfile,
} from '../src/components/elders/elderModel.ts'

/** 每例生成独立的合成档案，防止测试之间共享可变个人资料。 */
function fixture() {
  return {
    ...emptyProfile(),
    name: '合成老人',
    birthDate: '1956-02-29',
    address: '合成社区测试路1号',
    emergencyContactName: '合成联系人',
    emergencyContactRelation: '子女',
    emergencyContactPhone: '13900139000',
  }
}

test('必填项缺失返回各字段的中文提示', () => {
  const errors = validateProfile(emptyProfile(), '2026-09-18')
  assert.deepEqual(
    Object.keys(errors).sort(),
    [
      'name',
      'address',
      'birthDate',
      'emergencyContactName',
      'emergencyContactPhone',
      'emergencyContactRelation',
    ].sort(),
  )
  assert.match(errors.name!, /请填写姓名/)
})

test('接受闰年生日、最早日期、当天及空的可选电话', () => {
  for (const birthDate of ['1956-02-29', '1900-01-01', '2026-09-18']) {
    assert.deepEqual(validateProfile({ ...fixture(), birthDate }, '2026-09-18'), {})
  }
})

test('拒绝不存在的日期、越界日期和日期时间字符串', () => {
  for (const birthDate of [
    '1900-02-29',
    '1955-02-29',
    '2020-04-31',
    '1899-12-31',
    '2026-09-19',
    '2026-09-18T00:00:00Z',
    '',
  ]) {
    assert.ok(validateProfile({ ...fixture(), birthDate }, '2026-09-18').birthDate, birthDate)
  }
})

test('手机号和固定电话与后端格式一致', () => {
  for (const phone of ['13800138000', '021-12345678', '01012345678', '0571-1234567']) {
    assert.deepEqual(validateProfile({ ...fixture(), phone, emergencyContactPhone: phone }), {})
  }
  for (const phone of ['123456', '+8613800138000', '1380013800', '021--12345678', '11000138000']) {
    const errors = validateProfile({ ...fixture(), phone, emergencyContactPhone: phone })
    assert.ok(errors.phone)
    assert.ok(errors.emergencyContactPhone)
  }
})

test('校验文本边界与非法枚举，允许恰好达到长度上限', () => {
  const valid = {
    ...fixture(),
    name: '测'.repeat(50),
    address: '地'.repeat(200),
    emergencyContactName: '联'.repeat(50),
    emergencyContactRelation: '关'.repeat(30),
    remark: '注'.repeat(500),
  }
  assert.deepEqual(validateProfile(valid), {})
  for (const field of [
    'name',
    'address',
    'emergencyContactName',
    'emergencyContactRelation',
    'remark',
  ] as const) {
    assert.ok(validateProfile({ ...valid, [field]: valid[field] + '多' })[field])
  }
  // 用断言模拟外部异常数据，确保枚举对象的原型属性不会被当成合法值。
  assert.ok(validateProfile({ ...fixture(), gender: 'toString' as 'UNKNOWN' }).gender)
  assert.ok(
    validateProfile({ ...fixture(), livingArrangement: 'INVALID' as 'UNKNOWN' }).livingArrangement,
  )
})

test('规范化去除首尾空白，可选字段转为null且不污染原始草稿', () => {
  const input = { ...fixture(), name: ' 合成老人 ', phone: '  ', remark: '  ' }
  const result = normalizeProfile(input)
  assert.equal(result.name, '合成老人')
  assert.equal(result.phone, null)
  assert.equal(result.remark, null)
  assert.equal(input.name, ' 合成老人 ')
})

test('相同建档载荷重试复用编号，修改后生成新编号且快照不被草稿污染', () => {
  const input = fixture()
  const first = prepareCreate(input, null, () => 'first-request')
  const repeated = prepareCreate({ ...input, name: ' 合成老人 ' }, first, () => 'unused-request')
  assert.equal(repeated, first)
  input.address = '另一合成地址'
  assert.equal(first.profile.address, '合成社区测试路1号')
  const changed = prepareCreate(input, first, () => 'second-request')
  assert.equal(changed.requestId, 'second-request')
})

test('日期上限按上海时区跨日计算', () => {
  assert.equal(todayInShanghai(new Date('2026-09-17T15:59:59Z')), '2026-09-17')
  assert.equal(todayInShanghai(new Date('2026-09-17T16:00:00Z')), '2026-09-18')
})
