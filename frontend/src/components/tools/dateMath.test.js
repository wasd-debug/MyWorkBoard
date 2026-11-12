import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  addDate, businessDaysBetween, diffDays, formatDate, inclusiveDays, isWorkday, parseDate, shiftBusinessDays, todayKey
} from './dateMath.js'

test('解析与格式化保持本地日期不变', () => {
  assert.equal(formatDate(parseDate('2026-11-05')), '2026-11-05')
  assert.equal(formatDate(parseDate('2026-1-5')), '2026-01-05')
  assert.equal(todayKey(new Date(2026, 10, 5, 23, 30)), '2026-11-05')
  assert.throws(() => parseDate('2026/11/05'), RangeError)
})

test('日期差为正负与含端点两种口径', () => {
  assert.equal(diffDays('2026-01-01', '2026-03-01'), 59)
  assert.equal(inclusiveDays('2026-01-01', '2026-03-01'), 60)
  assert.equal(diffDays('2026-03-01', '2026-01-01'), -59)
  assert.equal(inclusiveDays('2026-03-01', '2026-01-01'), -60)
  assert.equal(diffDays('2026-11-05', '2026-11-05'), 0)
})

test('跨年与闰年日期差', () => {
  assert.equal(diffDays('2024-02-28', '2024-03-01'), 2)
  assert.equal(diffDays('2025-02-28', '2025-03-01'), 1)
  assert.equal(diffDays('2026-12-31', '2027-01-01'), 1)
})

test('日期加减在月末做夹取', () => {
  assert.equal(addDate('2026-01-31', { months: 1 }), '2026-02-28')
  assert.equal(addDate('2024-01-31', { months: 1 }), '2024-02-29')
  assert.equal(addDate('2024-02-29', { years: 1 }), '2025-02-28')
  assert.equal(addDate('2026-12-31', { days: 1 }), '2027-01-01')
  assert.equal(addDate('2026-03-01', { days: -1 }), '2026-02-28')
})

test('工作日判断排除周末但尊重调休补班', () => {
  assert.equal(isWorkday('2026-01-05'), true)  // 周一
  assert.equal(isWorkday('2026-01-10'), false) // 周六
  assert.equal(isWorkday('2026-01-10', { '2026-01-10': { name: '调休', off: false } }), true)
  assert.equal(isWorkday('2026-01-05', { '2026-01-05': { name: '元旦', off: true } }), false)
})

test('区间工作日数量包含两端并支持调休', () => {
  assert.equal(businessDaysBetween('2026-01-05', '2026-01-09'), 5)
  assert.equal(businessDaysBetween('2026-01-05', '2026-01-11'), 5)
  assert.equal(businessDaysBetween('2026-01-05', '2026-01-11', { '2026-01-10': { name: '补班', off: false } }), 6)
  assert.equal(businessDaysBetween('2026-01-09', '2026-01-05'), -5)
})

test('工作日推算跳过周末与节假日', () => {
  assert.equal(shiftBusinessDays('2026-01-09', 1), '2026-01-12')
  assert.equal(shiftBusinessDays('2026-01-12', -1), '2026-01-09')
  assert.equal(shiftBusinessDays('2026-01-09', 0), '2026-01-09')
  assert.equal(
    shiftBusinessDays('2026-01-09', 1, { '2026-01-12': { name: '春节', off: true } }),
    '2026-01-13'
  )
})
