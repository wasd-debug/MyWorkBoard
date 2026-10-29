import assert from 'node:assert/strict'
import test from 'node:test'
import {
  currentStreak, dateKey, dayLabel, dayState, doneTarget, findCheckin, frequencyLabel,
  goalLabel, groupHabits, monthMatrix, monthLogs, recentWeek, scheduledOn, shiftDays, todayKey, totalDone
} from './habitUtils.js'

const habit = (extra = {}) => ({
  publicId: 'h1', name: '阅读', startDate: '2026-10-01', frequency: 'DAILY',
  customDays: [], targetCount: 1, targetType: 'CHECK', groupKey: 'other', goalDays: 0, ...extra
})

test('日期 key 用本地时区，不会因为 UTC 跨天', () => {
  const date = new Date(2026, 9, 29, 23, 30, 0)
  assert.equal(dateKey(date), '2026-10-29')
  assert.equal(shiftDays('2026-10-29', 1), '2026-10-30')
  assert.equal(shiftDays('2026-11-01', -1), '2026-10-31')
})

test('最近 7 天以今天结尾，weekday 用中文短名', () => {
  const week = recentWeek('2026-10-29')
  assert.equal(week.length, 7)
  assert.equal(week[6].key, '2026-10-29')
  assert.equal(week[6].weekday, '四')
  assert.equal(week[6].isToday, true)
  assert.equal(week[0].key, '2026-10-23')
})

test('计划判断：开始日期之前不算，自定义星期按周几命中', () => {
  assert.equal(scheduledOn(habit(), '2026-09-30'), false)
  assert.equal(scheduledOn(habit(), '2026-10-02'), true)
  const custom = habit({ frequency: 'CUSTOM', customDays: [1, 3, 5] })
  assert.equal(scheduledOn(custom, '2026-10-26'), true)   // 周一
  assert.equal(scheduledOn(custom, '2026-10-27'), false)  // 周二
})

test('当天状态：完成 / 过期为 missed / 未来 / 计划外', () => {
  const checkins = [{ habitId: 'h1', date: '2026-10-28', count: 1, status: 'DONE' }]
  assert.equal(dayState(habit(), checkins, '2026-10-28', '2026-10-29'), 'done')
  assert.equal(dayState(habit(), checkins, '2026-10-27', '2026-10-29'), 'missed')
  assert.equal(dayState(habit(), checkins, '2026-10-29', '2026-10-29'), 'todo')
  assert.equal(dayState(habit(), checkins, '2026-10-30', '2026-10-29'), 'future')
  assert.equal(dayState(habit(), checkins, '2026-09-20', '2026-10-29'), 'none')
})

test('跳过（SKIP）不算完成，也不算中断', () => {
  const checkins = [
    { habitId: 'h1', date: '2026-10-29', count: 0, status: 'SKIP' },
    { habitId: 'h1', date: '2026-10-28', count: 1, status: 'DONE' }
  ]
  assert.equal(dayState(habit(), checkins, '2026-10-29', '2026-10-29'), 'skip')
  assert.equal(currentStreak(habit(), checkins, '2026-10-29'), 1)
})

test('完成量习惯要打满每天次数才算 done', () => {
  const quantity = habit({ targetType: 'QUANTITY', targetCount: 3 })
  assert.equal(doneTarget(quantity), 3)
  const checkins = [{ habitId: 'h1', date: '2026-10-28', count: 2, status: 'DONE' }]
  assert.equal(dayState(quantity, checkins, '2026-10-28', '2026-10-29'), 'missed')
  assert.equal(dayState(quantity, [{ ...checkins[0], count: 3 }], '2026-10-28', '2026-10-29'), 'done')
  assert.equal(totalDone(quantity, [{ ...checkins[0], count: 3 }]), 1)
})

test('连续天数从今天往回数，今天还没打就从昨天开始数', () => {
  const checkins = [
    { habitId: 'h1', date: '2026-10-27', count: 1, status: 'DONE' },
    { habitId: 'h1', date: '2026-10-28', count: 1, status: 'DONE' },
    { habitId: 'h1', date: '2026-10-29', count: 1, status: 'DONE' }
  ]
  assert.equal(currentStreak(habit(), checkins, '2026-10-29'), 3)
  assert.equal(currentStreak(habit(), checkins.slice(0, 2), '2026-10-29'), 2)
  assert.equal(currentStreak(habit(), [], '2026-10-29'), 0)
})

test('分组：按时段归组，空组不返回', () => {
  const groups = groupHabits([
    habit({ publicId: 'a', groupKey: 'morning' }),
    habit({ publicId: 'b', groupKey: 'evening' }),
    habit({ publicId: 'c', groupKey: 'evening' })
  ])
  assert.deepEqual(groups.map(group => group.key), ['morning', 'evening'])
  assert.equal(groups[1].items.length, 2)
})

test('月历补成 6 行共 42 格，周一开头', () => {
  const cells = monthMatrix(2026, 10, '2026-10-29')
  assert.equal(cells.length, 42)
  assert.equal(cells[0].key, '2026-09-28')
  assert.equal(cells.filter(cell => cell.inMonth).length, 31)
  assert.equal(cells.find(cell => cell.isToday)?.key, '2026-10-29')
})

test('文案：频率、坚持天数、日期标签', () => {
  assert.equal(frequencyLabel(habit()), '每天')
  assert.equal(frequencyLabel(habit({ frequency: 'WEEKLY_N', targetCount: 3 })), '每周 3 次')
  assert.equal(frequencyLabel(habit({ frequency: 'CUSTOM', customDays: [1, 3] })), '每周一、三')
  assert.equal(goalLabel(habit({ goalDays: 21 }), 8), '8/21 天')
  assert.equal(goalLabel(habit(), 8), '已坚持 8 天')
  assert.equal(dayLabel('2026-10-29', '2026-10-29'), '今天')
  assert.equal(dayLabel('2026-10-03', '2026-10-29'), '10月3日')
})

test('打卡日志按月份取且倒序', () => {
  const checkins = [
    { habitId: 'h1', date: '2026-10-03', count: 1, status: 'DONE' },
    { habitId: 'h1', date: '2026-10-28', count: 1, status: 'DONE' },
    { habitId: 'h1', date: '2026-09-30', count: 1, status: 'DONE' }
  ]
  const logs = monthLogs(habit(), checkins, '2026-10')
  assert.deepEqual(logs.map(log => log.date), ['2026-10-28', '2026-10-03'])
})

test('findCheckin 兼容带时分秒的日期', () => {
  const checkins = [{ habitId: 'h1', date: '2026-10-28T00:00:00Z', count: 1, status: 'DONE' }]
  assert.ok(findCheckin(checkins, 'h1', '2026-10-28'))
  assert.equal(findCheckin(checkins, 'h1', '2026-10-27'), undefined)
})
