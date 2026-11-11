import test from 'node:test'
import assert from 'node:assert/strict'
import {
  AGENDA_DAYS, agendaGroups, buildDayIndex, calendarViewStorageKey, dayDetail, dayPreview,
  monthCells, monthProgress, rangeStats, rangeTitle, readCalendarView, timeLabelOf, weekDays,
  writeCalendarView
} from './homeCalendarModel.js'
import { fromDateKey, toDateKey } from '../../views/calendar/calendarRange.js'

function memoryStorage(initial = {}) {
  const map = new Map(Object.entries(initial))
  return {
    getItem: key => (map.has(key) ? map.get(key) : null),
    setItem: (key, value) => { map.set(key, String(value)) }
  }
}

const at = (key, hour = 0, minute = 0) => {
  const date = fromDateKey(key)
  date.setHours(hour, minute, 0, 0)
  return date.toISOString()
}

test('day index spreads range tasks over every day and keeps transactions/worktime on their own day', () => {
  const index = buildDayIndex({
    tasks: [
      { publicId: 'a', title: '写方案', status: 'OPEN', priority: 'HIGH', dueAt: at('2026-11-11', 10, 30) },
      { publicId: 'b', title: '出差', status: 'OPEN', startAt: at('2026-11-13', 9), dueAt: at('2026-11-15', 18) },
      { publicId: 'c', title: '已删', status: 'OPEN', deleted: true, dueAt: at('2026-11-11', 9) },
      { publicId: 'd', title: '已放弃', status: 'ABANDONED', dueAt: at('2026-11-11', 9) }
    ],
    transactions: [
      { id: 'f1', kind: 'EXPENSE', amount: 28, occurredOn: '2026-11-11', note: '午饭' },
      { id: 'f2', kind: 'INCOME', amount: 5000, occurredOn: '2026-11-15', note: '工资' },
      { id: 'f3', kind: 'EXPENSE', amount: 1, occurredOn: '2026-11-11', deleted: true }
    ],
    records: { '2026-11-11': { date: '2026-11-11', start: '08:30', end: '18:00', rest: 0 } },
    settings: { lunchMin: 90 }
  })
  const eleventh = dayDetail(index, '2026-11-11', '2026-11-11')
  assert.deepEqual(eleventh.tasks.map(task => task.id), ['a'])
  assert.equal(eleventh.flows.length, 1)
  assert.equal(eleventh.work.minutes, 480)
  assert.equal(eleventh.count, 3)
  // 跨天任务 13/14/15 每天都有
  for (const key of ['2026-11-13', '2026-11-14', '2026-11-15']) {
    assert.deepEqual(index.get(key)?.tasks.map(task => task.publicId), ['b'])
  }
  assert.equal(index.get('2026-11-12')?.tasks, undefined)
})

test('day detail sorts open tasks first, then priority and clock time', () => {
  const index = buildDayIndex({
    tasks: [
      { publicId: 'late', title: '晚上', status: 'OPEN', priority: 'LOW', dueAt: at('2026-11-11', 20) },
      { publicId: 'done', title: '已完成', status: 'COMPLETED', priority: 'HIGH', dueAt: at('2026-11-11', 8) },
      { publicId: 'urgent', title: '紧急', status: 'OPEN', priority: 'HIGH', dueAt: at('2026-11-11', 15) },
      { publicId: 'allday', title: '全天', status: 'OPEN', priority: 'NONE', dueAt: at('2026-11-11') }
    ]
  })
  const detail = dayDetail(index, '2026-11-11')
  assert.deepEqual(detail.tasks.map(task => task.id), ['urgent', 'late', 'allday', 'done'])
  assert.equal(detail.tasks[2].time, '全天')
  assert.equal(detail.tasks[0].time, '15:00')
  assert.equal(detail.weekday, '周三')
})

test('month cells fill whole weeks and mark today / off days / holidays', () => {
  const index = buildDayIndex({ tasks: [{ publicId: 'a', title: 'a', status: 'OPEN', dueAt: at('2026-11-01') }] })
  const rows = monthCells('2026-11-11', index, {
    todayKey: '2026-11-11',
    holidays: { '2026-11-02': { off: true, name: '调休' }, '2026-11-07': { off: false, name: '补班' } }
  })
  assert.ok(rows.length >= 5 && rows.length <= 6)
  assert.ok(rows.every(week => week.length === 7))
  const cells = rows.flat()
  const todayCell = cells.find(cell => cell.today)
  assert.equal(todayCell.key, '2026-11-11')
  assert.equal(todayCell.off, false)
  const first = cells.find(cell => cell.key === '2026-11-01')
  assert.equal(first.taskCount, 1)
  assert.equal(first.holidayLabel, '')
  // 周一开头：第一格必须是周一
  assert.equal(fromDateKey(rows[0][0].key).getDay(), 1)
  assert.equal(cells.find(cell => cell.key === '2026-11-02').holidayLabel, '休')
  assert.equal(cells.find(cell => cell.key === '2026-11-07').holidayLabel, '班')
  // 补位日期不标成本月
  assert.equal(cells.find(cell => cell.key === '2026-10-26' || cell.key === '2026-10-27')?.inMonth, false)
})

test('week view starts on Monday and agenda skips empty days inside the horizon', () => {
  const index = buildDayIndex({
    tasks: [
      { publicId: 'a', title: '今天的事', status: 'OPEN', dueAt: at('2026-11-11', 9) },
      { publicId: 'b', title: '远处的事', status: 'OPEN', dueAt: at('2026-11-25', 9) },
      { publicId: 'c', title: '超出范围', status: 'OPEN', dueAt: at('2026-12-20', 9) }
    ]
  })
  const week = weekDays('2026-11-11', index, { todayKey: '2026-11-11' })
  assert.equal(week.length, 7)
  assert.equal(week[0].key, '2026-11-09')
  assert.equal(week[6].key, '2026-11-15')
  assert.equal(week[2].today, true)
  assert.equal(week[2].taskCount, 1)

  const agenda = agendaGroups(index, '2026-11-11', AGENDA_DAYS, { todayKey: '2026-11-11' })
  // 只有 11/11 与 11/25（后者已超出 14 天窗口）
  assert.deepEqual(agenda.map(day => day.key), ['2026-11-11'])
  const wide = agendaGroups(index, '2026-11-11', 30, { todayKey: '2026-11-11' })
  assert.deepEqual(wide.map(day => day.key), ['2026-11-11', '2026-11-25'])
})

test('preview lists tasks then flows then worktime and reports the remainder', () => {
  const index = buildDayIndex({
    tasks: [{ publicId: 'a', title: '写方案', status: 'OPEN', dueAt: at('2026-11-11', 10) }],
    transactions: [{ id: 'f', kind: 'EXPENSE', amount: 1280, occurredOn: '2026-11-11', payee: '房租' }],
    records: { '2026-11-11': { date: '2026-11-11', start: '09:00', end: '18:00', rest: 0 } },
    settings: { lunchMin: 90 }
  })
  const detail = dayDetail(index, '2026-11-11')
  const preview = dayPreview(detail, 2)
  assert.equal(preview.rows.length, 2)
  assert.equal(preview.rows[0].label, '写方案')
  assert.equal(preview.rows[1].label, '−¥1,280 房租')
  assert.equal(preview.more, 1)
  const all = dayPreview(detail, 5)
  assert.equal(all.rows[2].label, '工时 09:00–18:00 · 7.5h')
  assert.equal(all.more, 0)
})

test('range titles follow the active view and month progress tracks today', () => {
  assert.equal(rangeTitle('month', '2026-11-11'), '2026 年 11 月')
  assert.equal(rangeTitle('week', '2026-11-11'), '11/9 – 11/15')
  assert.equal(rangeTitle('week', '2026-11-01'), '10/26 – 11/1')
  assert.equal(rangeTitle('agenda', '2026-11-11', 14), '11/11 起 14 天')
  assert.equal(monthProgress('2026-11-11', '2026-11-11'), Math.round((11 / 30) * 100))
  assert.equal(monthProgress('2026-10-11', '2026-11-11'), 100)
  assert.equal(monthProgress('2026-12-11', '2026-11-11'), 0)
})

test('rangeStats counts busy days and total items', () => {
  const rows = [{ count: 2 }, { count: 0 }, { count: 1 }]
  assert.deepEqual(rangeStats(rows), { busy: 2, total: 3, days: 3 })
  assert.deepEqual(rangeStats([rows]), { busy: 2, total: 3, days: 3 })
})

test('view choice persists per account and falls back to month', () => {
  const storage = memoryStorage()
  const key = calendarViewStorageKey('u1')
  assert.equal(readCalendarView(key, storage), 'month')
  writeCalendarView(key, 'week', storage)
  assert.equal(readCalendarView(key, storage), 'week')
  writeCalendarView(key, 'day', storage)
  assert.equal(readCalendarView(key, storage), 'month')
  assert.equal(readCalendarView(calendarViewStorageKey('u2'), storage), 'month')
})

test('time label uses the clock when present and falls back to all-day', () => {
  assert.equal(timeLabelOf({ dueAt: at('2026-11-11', 9, 5) }), '09:05')
  assert.equal(timeLabelOf({ dueAt: at('2026-11-11'), startAt: at('2026-11-11', 8) }), '08:00')
  assert.equal(timeLabelOf({ dueAt: at('2026-11-11') }), '全天')
  assert.equal(timeLabelOf({}), '全天')
})
