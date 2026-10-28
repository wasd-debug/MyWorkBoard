import test from 'node:test'
import assert from 'node:assert/strict'
import {
  addDays, addMonths, chunkWeeks, clampSpan, fromDateKey, isSameDay, isoWeekNumber, monthWeeks,
  resolveRange, shiftAnchor, startOfWeek, toDateKey, viewLabel, yearMonths, formatRangeText
} from './calendarRange.js'

test('toDateKey 使用本地日期而不是 UTC 偏移', () => {
  assert.equal(toDateKey(new Date(2026, 9, 1)), '2026-10-01')
  assert.equal(toDateKey(new Date(2026, 0, 31, 23, 59)), '2026-01-31')
})

test('fromDateKey 与 toDateKey 互逆', () => {
  const date = fromDateKey('2026-03-08')
  assert.equal(date.getFullYear(), 2026)
  assert.equal(date.getMonth(), 2)
  assert.equal(date.getDate(), 8)
  assert.equal(toDateKey(date), '2026-03-08')
})

test('startOfWeek 以周一为首日', () => {
  assert.equal(toDateKey(startOfWeek(new Date(2026, 9, 19))), '2026-10-19')
  assert.equal(toDateKey(startOfWeek(new Date(2026, 9, 25))), '2026-10-19')
  assert.equal(toDateKey(startOfWeek(new Date(2026, 9, 26))), '2026-10-26')
  assert.equal(toDateKey(startOfWeek(new Date(2026, 9, 1))), '2026-09-28')
})

test('isoWeekNumber 与滴答截图周序号一致', () => {
  assert.equal(isoWeekNumber(new Date(2026, 9, 19)), 43)
  assert.equal(isoWeekNumber(new Date(2026, 9, 12)), 42)
  assert.equal(isoWeekNumber(new Date(2026, 9, 1)), 40)
  assert.equal(isoWeekNumber(new Date(2026, 11, 31)), 53)
  assert.equal(isoWeekNumber(new Date(2027, 0, 4)), 1)
})

test('monthWeeks 覆盖整月并补足首尾周', () => {
  const weeks = monthWeeks(new Date(2026, 9, 19))
  assert.equal(weeks.length, 5)
  assert.equal(toDateKey(weeks[0][0]), '2026-09-28')
  assert.equal(toDateKey(weeks[4][6]), '2026-11-01')
  for (const week of weeks) assert.equal(week.length, 7)
})

test('monthWeeks 在需要时返回 6 行', () => {
  const weeks = monthWeeks(new Date(2026, 7, 1))
  assert.equal(weeks.length, 6)
  assert.equal(toDateKey(weeks[0][0]), '2026-07-27')
  assert.equal(toDateKey(weeks[5][6]), '2026-09-06')
})

test('monthWeeks 在短月份返回 4 行', () => {
  const weeks = monthWeeks(new Date(2027, 1, 1))
  assert.equal(weeks.length, 4)
})

test('resolveRange 月视图返回对齐的完整周', () => {
  const range = resolveRange({ view: 'month', anchor: new Date(2026, 9, 19) })
  assert.equal(toDateKey(range.start), '2026-09-28')
  assert.equal(toDateKey(range.end), '2026-11-02')
  assert.equal(range.days.length, 35)
  assert.equal(range.weeks.length, 5)
  assert.equal(range.title, '2026年10月')
})

test('resolveRange 多周 6 周从锚点所在周开始', () => {
  const range = resolveRange({ view: 'multiWeek', span: 6, anchor: new Date(2026, 9, 19) })
  assert.equal(toDateKey(range.start), '2026-10-19')
  assert.equal(toDateKey(addDays(range.end, -1)), '2026-11-29')
  assert.equal(range.days.length, 42)
})

test('resolveRange 多日按天展开', () => {
  const range = resolveRange({ view: 'multiDay', span: 3, anchor: new Date(2026, 9, 19) })
  assert.equal(range.days.length, 3)
  assert.equal(toDateKey(range.days[2]), '2026-10-21')
})

test('resolveRange 年视图覆盖整年', () => {
  const range = resolveRange({ view: 'year', anchor: new Date(2026, 9, 19) })
  assert.equal(toDateKey(range.start), '2026-01-01')
  assert.equal(toDateKey(range.end), '2027-01-01')
  assert.equal(range.days.length, 365)
  assert.equal(range.title, '2026年')
})

test('resolveRange 日程视图给出连续 30 天', () => {
  const range = resolveRange({ view: 'agenda', anchor: new Date(2026, 9, 19) })
  assert.equal(range.days.length, 30)
  assert.equal(toDateKey(range.days[0]), '2026-10-19')
})

test('shiftAnchor 按视图单位位移', () => {
  const anchor = new Date(2026, 9, 19)
  assert.equal(toDateKey(shiftAnchor(anchor, { view: 'day', direction: 'next' })), '2026-10-20')
  assert.equal(toDateKey(shiftAnchor(anchor, { view: 'week', direction: 'prev' })), '2026-10-12')
  assert.equal(toDateKey(shiftAnchor(anchor, { view: 'month', direction: 'next' })), '2026-11-19')
  assert.equal(toDateKey(shiftAnchor(anchor, { view: 'multiDay', span: 5, direction: 'next' })), '2026-10-24')
  assert.equal(toDateKey(shiftAnchor(anchor, { view: 'multiWeek', span: 2, direction: 'next' })), '2026-11-02')
  assert.equal(shiftAnchor(anchor, { view: 'year', direction: 'next' }).getFullYear(), 2027)
})

test('shiftAnchor 今天忽略方向', () => {
  const shifted = shiftAnchor(new Date(2020, 0, 1), { view: 'month', direction: 'today' })
  assert.ok(isSameDay(shifted, new Date()))
})

test('addMonths 收敛到目标月最后一天', () => {
  assert.equal(toDateKey(addMonths(new Date(2026, 0, 31), 1)), '2026-02-28')
  assert.equal(toDateKey(addMonths(new Date(2026, 2, 31), -1)), '2026-02-28')
})

test('clampSpan 限制在配置区间', () => {
  assert.equal(clampSpan('multiDay', 20), 14)
  assert.equal(clampSpan('multiDay', 0), 1)
  assert.equal(clampSpan('multiWeek', 1), 2)
  assert.equal(clampSpan('multiWeek', 9), 6)
  assert.equal(clampSpan('multiWeek', undefined), 6)
})

test('viewLabel 显示跨度数值', () => {
  assert.equal(viewLabel('month', 6), '月')
  assert.equal(viewLabel('multiDay', 5), '5 天')
  assert.equal(viewLabel('multiWeek', 6), '6 周')
})

test('chunkWeeks 保留不足七天的尾段', () => {
  const days = Array.from({ length: 10 }, (_, index) => addDays(new Date(2026, 9, 1), index))
  const weeks = chunkWeeks(days)
  assert.equal(weeks.length, 2)
  assert.equal(weeks[1].length, 3)
})

test('yearMonths 返回十二个月且首周为周一', () => {
  const months = yearMonths(new Date(2026, 9, 19))
  assert.equal(months.length, 12)
  assert.equal(months[0].label, '1月')
  for (const month of months) {
    for (const week of month.weeks) assert.equal(week[0].getDay(), 1)
  }
})

test('formatRangeText 同日与跨年', () => {
  const single = resolveRange({ view: 'day', anchor: new Date(2026, 9, 19) })
  assert.equal(formatRangeText(single), '10月19日 周一')
  const crossYear = resolveRange({ view: 'multiWeek', span: 2, anchor: new Date(2026, 11, 31) })
  assert.match(formatRangeText(crossYear), /2026年/)
})

test('日程视图支持向前向后扩展窗口', () => {
  const base = resolveRange({ view: 'agenda', anchor: new Date(2026, 9, 19) })
  assert.equal(base.days.length, 30)
  const extended = resolveRange({ view: 'agenda', anchor: new Date(2026, 9, 19), agendaBefore: 30, agendaAfter: 30 })
  assert.equal(extended.days.length, 90)
  assert.equal(toDateKey(extended.days[0]), '2026-09-19')
  assert.equal(toDateKey(extended.days.at(-1)), '2026-12-17')
})
