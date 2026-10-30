import assert from 'node:assert/strict'
import test from 'node:test'
import {
  currentStreak, dateKey, dayLabel, dayState, findCheckin, frequencyLabel, goalLabel, groupHabits,
  isComplete, longestStreak, monthMatrix, monthLogs, monthStats, positiveInt, recentWeek, remindTimes,
  scheduledOn, shiftDays, targetAmount, todayKey, totalDone
} from './habitUtils.js'

const habit = (extra = {}) => ({
  publicId: 'h1', name: '阅读', startDate: '2026-10-01', frequency: 'DAILY',
  customDays: [], targetCount: 1, targetType: 'CHECK', groupKey: 'other', goalDays: 0,
  dailyTimes: 1, intervalDays: 2, checkMode: 'AUTO', perAmount: 1, ...extra
})

test('日期 key 用本地时区，不会因为 UTC 跨天', () => {
  const date = new Date(2026, 9, 29, 23, 30, 0)
  assert.equal(dateKey(date), '2026-10-29')
  assert.equal(dateKey(new Date(2026, 9, 30, 0, 10, 0)), '2026-10-30')
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

test('按时间间隔：每隔 N 天一次，从开始日期起算', () => {
  const interval = habit({ frequency: 'INTERVAL', intervalDays: 2, startDate: '2026-10-01' })
  assert.equal(scheduledOn(interval, '2026-10-01'), true)
  assert.equal(scheduledOn(interval, '2026-10-02'), false)
  assert.equal(scheduledOn(interval, '2026-10-03'), true)
  assert.equal(scheduledOn(interval, '2026-10-05'), true)
  assert.equal(scheduledOn(interval, '2026-10-06'), false)
  assert.equal(frequencyLabel(interval), '每 2 天')
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

test('未完成（MISS）算中断，取消打卡（CLEAR）等价于没记录', () => {
  const checkins = [
    { habitId: 'h1', date: '2026-10-29', count: 0, status: 'MISS' },
    { habitId: 'h1', date: '2026-10-28', count: 1, status: 'DONE' },
    { habitId: 'h1', date: '2026-10-27', count: 1, status: 'DONE' }
  ]
  assert.equal(dayState(habit(), checkins, '2026-10-29', '2026-10-29'), 'missed')
  // 明确标了未完成，连续打卡就在这里断掉
  assert.equal(currentStreak(habit(), checkins, '2026-10-29'), 0)
  // 去掉未完成那条，今天算「还没打」，从昨天往前数 2 天
  assert.equal(currentStreak(habit(), checkins.slice(1), '2026-10-29'), 2)
  // CLEAR 是取消打卡留下的空记录，今天回到待打卡、过去回到未完成
  const cleared = [{ habitId: 'h1', date: '2026-10-29', status: 'CLEAR' }]
  assert.equal(dayState(habit(), cleared, '2026-10-29', '2026-10-29'), 'todo')
  assert.equal(dayState(habit(), cleared, '2026-10-29', '2026-10-30'), 'missed')
  assert.equal(totalDone(habit(), cleared), 0)
})

test('完成量习惯按累计量判断达标，不够算 partial', () => {
  const water = habit({ targetType: 'QUANTITY', dailyTimes: 8, amountUnit: '杯', perAmount: 2 })
  assert.equal(targetAmount(water), 8)
  const half = [{ habitId: 'h1', date: '2026-10-28', count: 2, amount: 4, status: 'DONE' }]
  const full = [{ habitId: 'h1', date: '2026-10-28', count: 4, amount: 8, status: 'DONE' }]
  assert.equal(dayState(water, half, '2026-10-28', '2026-10-29'), 'partial')
  assert.equal(dayState(water, full, '2026-10-28', '2026-10-29'), 'done')
  assert.equal(isComplete(water, half[0]), false)
  assert.equal(totalDone(water, full), 1)
  assert.equal(totalDone(water, half), 0)
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

test('最高连续天数能找回历史最长的一段', () => {
  const checkins = [
    { habitId: 'h1', date: '2026-10-02', count: 1, status: 'DONE' },
    { habitId: 'h1', date: '2026-10-03', count: 1, status: 'DONE' },
    { habitId: 'h1', date: '2026-10-04', count: 1, status: 'DONE' },
    { habitId: 'h1', date: '2026-10-05', count: 1, status: 'DONE' },
    { habitId: 'h1', date: '2026-10-08', count: 1, status: 'DONE' },
    { habitId: 'h1', date: '2026-10-09', count: 1, status: 'DONE' }
  ]
  assert.equal(longestStreak(habit(), checkins, '2026-10-09'), 4)
  assert.equal(currentStreak(habit(), checkins, '2026-10-09'), 2)
})

test('分组：按时段归组，自定义分组跟在内置后面，空组不返回', () => {
  const groups = groupHabits([
    habit({ publicId: 'a', groupKey: 'morning' }),
    habit({ publicId: 'b', groupKey: 'evening' }),
    habit({ publicId: 'c', groupKey: 'evening' }),
    habit({ publicId: 'd', groupKey: '健身' })
  ], [{ key: '健身', label: '健身' }])
  assert.deepEqual(groups.map(group => group.key), ['morning', 'evening', '健身'])
  assert.equal(groups[1].items.length, 2)
  assert.equal(groups[2].label, '健身')
})

test('月历补成 6 行共 42 格，周一开头', () => {
  const cells = monthMatrix(2026, 10, '2026-10-29')
  assert.equal(cells.length, 42)
  assert.equal(cells[0].key, '2026-09-28')
  assert.equal(cells.filter(cell => cell.inMonth).length, 31)
  assert.equal(cells.find(cell => cell.isToday)?.key, '2026-10-29')
})

test('月统计：完成天数、应完成天数、完成率、完成量合计', () => {
  const water = habit({ targetType: 'QUANTITY', dailyTimes: 2, amountUnit: '升' })
  const checkins = [
    { habitId: 'h1', date: '2026-10-02', count: 1, amount: 2, status: 'DONE' },
    { habitId: 'h1', date: '2026-10-03', count: 1, amount: 1, status: 'DONE' },
    { habitId: 'h1', date: '2026-10-04', count: 0, amount: 0, status: 'SKIP' }
  ]
  const stats = monthStats(water, checkins, 2026, 10, '2026-10-04')
  assert.equal(stats.done, 1)
  assert.equal(stats.partial, 1)
  // 跳过的那天不计入应完成天数，否则完成率会被拉低
  assert.equal(stats.target, 3)
  assert.equal(stats.rate, 33)
  assert.equal(stats.amount, 3)
})

test('文案：频率、坚持天数、日期标签', () => {
  assert.equal(frequencyLabel(habit()), '每天')
  assert.equal(frequencyLabel(habit({ frequency: 'CUSTOM', customDays: [1, 2, 3, 4, 5, 6, 7] })), '每天')
  assert.equal(frequencyLabel(habit({ frequency: 'WEEKLY_N', targetCount: 3 })), '每周 3 天')
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

test('提醒时间支持多个，逗号分隔', () => {
  assert.deepEqual(remindTimes(habit({ remindAt: '07:00,20:30' })), ['07:00', '20:30'])
  assert.deepEqual(remindTimes(habit({ remindAt: '' })), [])
  assert.deepEqual(remindTimes({ remindAt: null }), [])
})

test('正整数输入：非数字返回 null，超上限截断', () => {
  assert.equal(positiveInt('42'), 42)
  assert.equal(positiveInt('0042'), 42)
  assert.equal(positiveInt(''), null)
  assert.equal(positiveInt('0'), null)
  assert.equal(positiveInt('abc'), null)
  assert.equal(positiveInt('1500', 999), 999)
})

test('findCheckin 兼容带时分秒的日期', () => {
  const checkins = [{ habitId: 'h1', date: '2026-10-28T00:00:00Z', count: 1, status: 'DONE' }]
  assert.ok(findCheckin(checkins, 'h1', '2026-10-28'))
  assert.equal(findCheckin(checkins, 'h1', '2026-10-27'), undefined)
})
