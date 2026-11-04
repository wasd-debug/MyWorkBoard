import test from 'node:test'
import assert from 'node:assert/strict'
import { isRangeTask, taskHitsDay, taskOverlapsKeys, taskRange, taskStartMoment } from './taskDates.js'

const day = (month, date, hour = 0) => new Date(2026, month - 1, date, hour)

test('时间段任务在区间内的每一天都算命中，首尾两天也包含', () => {
  const task = {
    startAt: day(11, 2, 9).toISOString(),
    dueAt: day(11, 6, 18).toISOString(),
  }
  for (const date of [2, 3, 4, 5, 6]) {
    assert.equal(taskHitsDay(task, day(11, date), day(11, date + 1)), true, `11/${date} 应命中`)
  }
  assert.equal(taskHitsDay(task, day(11, 1), day(11, 2)), false, '开始前一天不算')
  assert.equal(taskHitsDay(task, day(11, 7), day(11, 8)), false, '结束之后不算')
})

test('时间段任务的最后一天按整天算，结束时间在该天 0 点也算', () => {
  const task = {
    startAt: day(11, 2, 0).toISOString(),
    dueAt: day(11, 4, 0).toISOString(),
  }
  assert.equal(taskHitsDay(task, day(11, 4), day(11, 5)), true)
})

test('单日期任务口径不变：只认到期那天', () => {
  const task = { dueAt: day(11, 4, 20).toISOString() }
  assert.equal(taskHitsDay(task, day(11, 4), day(11, 5)), true)
  assert.equal(taskHitsDay(task, day(11, 3), day(11, 4)), false)
  assert.equal(taskHitsDay(task, day(11, 5), day(11, 6)), false)
})

test('同一天内的起止时间只算这一天，不会溢出到前后天', () => {
  const task = {
    startAt: day(11, 4, 9).toISOString(),
    dueAt: day(11, 4, 11).toISOString(),
  }
  // 同一天的时间窗仍是一个区间，但只落在当天
  assert.equal(isRangeTask(task), true)
  assert.equal(taskHitsDay(task, day(11, 4), day(11, 5)), true)
  assert.equal(taskHitsDay(task, day(11, 3), day(11, 4)), false)
  assert.equal(taskHitsDay(task, day(11, 5), day(11, 6)), false)
})

test('没有日期、日期非法或只有开始时间都不算命中', () => {
  assert.equal(taskHitsDay({}, day(11, 4), day(11, 5)), false)
  assert.equal(taskHitsDay({ dueAt: 'not-a-date' }, day(11, 4), day(11, 5)), false)
  assert.equal(taskHitsDay({ startAt: day(11, 4, 9).toISOString() }, day(11, 4), day(11, 5)), false)
})

test('按日期键判断时区间与筛选窗口有交集即可', () => {
  const parseKey = (key) => {
    const [year, month, date] = key.split('-').map(Number)
    return new Date(year, month - 1, date)
  }
  const task = {
    startAt: day(11, 2, 9).toISOString(),
    dueAt: day(11, 6, 18).toISOString(),
  }
  assert.equal(taskOverlapsKeys(task, '2026-11-04', '2026-11-04', parseKey), true)
  assert.equal(taskOverlapsKeys(task, '2026-10-20', '2026-10-31', parseKey), false)
  assert.equal(taskOverlapsKeys(task, '2026-11-01', '2026-11-02', parseKey), true)
  assert.equal(taskOverlapsKeys(task, '2026-11-06', '2026-11-10', parseKey), true)
  assert.equal(taskOverlapsKeys(task, '2026-11-07', '2026-11-10', parseKey), false)
})

test('时间段任务的起点取开始时间，单日期任务仍取到期时间', () => {
  const range = { startAt: day(11, 2, 9).toISOString(), dueAt: day(11, 6, 18).toISOString() }
  assert.equal(taskStartMoment(range).getTime(), day(11, 2, 9).getTime())
  const single = { dueAt: day(11, 4, 20).toISOString() }
  assert.equal(taskStartMoment(single).getTime(), day(11, 4, 20).getTime())
  assert.equal(taskStartMoment({}), null)
})
