import test from 'node:test'
import assert from 'node:assert/strict'
import { buildCalendarEvents, daySummary, habitScheduledOn, taskWindow } from './calendarEvents.js'
import { eachDay, toDateKey } from './calendarRange.js'
import { layoutColumns } from './calendarTimeGrid.js'

const days = eachDay(new Date(2026, 9, 19), new Date(2026, 9, 26))
const lists = [{ publicId: 'list-work', color: '#6bcb77' }, { publicId: 'list-study', color: '#4d96ff' }]
const tags = [{ publicId: 'tag-java', color: '#f5c33b' }]

function dayEvents(result, key) {
  return result.byDay.get(key)
}

test('taskWindow 全天多日任务按起止日期展开', () => {
  const window = taskWindow({ startAt: '2026-10-16T00:00:00.000Z', dueAt: '2026-10-18T00:00:00.000Z', allDay: true })
  assert.equal(toDateKey(window.start), '2026-10-16')
  assert.equal(toDateKey(window.end), '2026-10-18')
  assert.equal(window.allDay, true)
})

test('taskWindow 无结束时间的有刻任务补默认 30 分钟', () => {
  const window = taskWindow({ startAt: '2026-10-19T01:30:00.000Z', allDay: false })
  assert.equal(window.end.getTime() - window.start.getTime(), 30 * 60000)
})

test('全天任务在多日范围内逐日铺开', () => {
  const result = buildCalendarEvents({
    days, lists, tags,
    tasks: [{ publicId: 't1', title: '工作', startAt: '2026-10-20T00:00:00.000Z', dueAt: '2026-10-21T00:00:00.000Z', allDay: true, listId: 'list-work', status: 'OPEN' }]
  })
  assert.equal(dayEvents(result, '2026-10-20').allDay.length, 1)
  assert.equal(dayEvents(result, '2026-10-21').allDay.length, 1)
  assert.equal(dayEvents(result, '2026-10-22').allDay.length, 0)
})

test('有刻任务按分钟落到时间轴', () => {
  const result = buildCalendarEvents({
    days, lists, tags,
    tasks: [{ publicId: 't2', title: '单词', startAt: '2026-10-19T09:00:00', dueAt: '2026-10-19T10:30:00', allDay: false, listId: 'list-study', status: 'OPEN' }]
  })
  const [event] = dayEvents(result, '2026-10-19').timed
  assert.equal(event.startMinutes, 9 * 60)
  assert.equal(event.endMinutes, 10 * 60 + 30)
  assert.equal(event.startLabel, '09:00')
  assert.equal(event.allDay, false)
})

test('跨夜有刻任务在两天分别裁切', () => {
  const result = buildCalendarEvents({
    days, lists, tags,
    tasks: [{ publicId: 't3', title: '加班', startAt: '2026-10-19T22:00:00', dueAt: '2026-10-20T02:00:00', allDay: false, listId: 'list-work', status: 'OPEN' }]
  })
  const first = dayEvents(result, '2026-10-19').timed[0]
  const second = dayEvents(result, '2026-10-20').timed[0]
  assert.equal(first.startMinutes, 22 * 60)
  assert.equal(first.continuesAfter, true)
  assert.equal(second.startMinutes, 0)
  assert.equal(second.endMinutes, 120)
  assert.equal(second.continuesBefore, true)
})

test('笔记与已放弃任务不进入日历', () => {
  const result = buildCalendarEvents({
    days, lists, tags,
    tasks: [
      { publicId: 'note', title: '笔记', startAt: '2026-10-19T00:00:00', allDay: true, itemKind: 'NOTE', status: 'OPEN' },
      { publicId: 'gone', title: '放弃', startAt: '2026-10-19T00:00:00', allDay: true, status: 'ABANDONED' }
    ]
  })
  assert.equal(dayEvents(result, '2026-10-19').allDay.length, 0)
})

test('完成态任务保留并标记 done', () => {
  const result = buildCalendarEvents({
    days, lists, tags,
    tasks: [{ publicId: 't4', title: '运动', startAt: '2026-10-19T00:00:00', allDay: true, status: 'COMPLETED', listId: 'list-work' }]
  })
  assert.equal(dayEvents(result, '2026-10-19').allDay[0].done, true)
})

test('taskFilter 决定任务是否进入日历', () => {
  const result = buildCalendarEvents({
    days, lists, tags,
    tasks: [
      { publicId: 'a', title: 'A', startAt: '2026-10-19T00:00:00', allDay: true, listId: 'list-work', status: 'OPEN' },
      { publicId: 'b', title: 'B', startAt: '2026-10-19T00:00:00', allDay: true, listId: 'list-study', status: 'OPEN' }
    ],
    taskFilter: task => task.listId === 'list-study'
  })
  assert.deepEqual(dayEvents(result, '2026-10-19').allDay.map(item => item.title), ['B'])
})

test('颜色按设置来源解析', () => {
  const base = { days: days.slice(0, 1), lists, tags, tasks: [{ publicId: 't5', title: '任务', startAt: '2026-10-19T00:00:00', allDay: true, listId: 'list-work', tagIds: ['tag-java'], priority: 'HIGH', status: 'OPEN' }] }
  assert.equal(buildCalendarEvents(base).byDay.get('2026-10-19').allDay[0].color, '#6bcb77')
  assert.equal(buildCalendarEvents({ ...base, colorSettings: { taskColorMode: 'tag' } }).byDay.get('2026-10-19').allDay[0].color, '#f5c33b')
  assert.equal(buildCalendarEvents({ ...base, colorSettings: { taskColorMode: 'priority' } }).byDay.get('2026-10-19').allDay[0].color, '#e34d59')
})

test('习惯按频率排期', () => {
  const habit = { publicId: 'h1', name: '戒', frequency: 'CUSTOM', customDays: [1, 3], startDate: '2026-10-01', targetCount: 1 }
  assert.equal(habitScheduledOn(habit, new Date(2026, 9, 19)), true)
  assert.equal(habitScheduledOn(habit, new Date(2026, 9, 20)), false)
  assert.equal(habitScheduledOn({ ...habit, frequency: 'DAILY' }, new Date(2026, 9, 20)), true)
  assert.equal(habitScheduledOn({ ...habit, startDate: '2026-11-01' }, new Date(2026, 9, 19)), false)
})

test('习惯打卡状态来自 checkin', () => {
  const result = buildCalendarEvents({
    days, lists, tags,
    habits: [{ publicId: 'h1', name: '阅读', frequency: 'DAILY', startDate: '2026-09-01', targetCount: 1, color: '#4d78ff' }],
    habitCheckins: [{ habitId: 'h1', date: '2026-10-19', status: 'DONE', count: 1 }],
    colorSettings: { habitColorMode: 'habit' }
  })
  const [event] = dayEvents(result, '2026-10-19').allDay
  assert.equal(event.kind, 'habit')
  assert.equal(event.done, true)
  assert.equal(event.color, '#4d78ff')
})

test('习惯在未达到目标次数时不算完成', () => {
  const result = buildCalendarEvents({
    days: days.slice(0, 1), lists, tags,
    habits: [{ publicId: 'h2', name: '喝水', frequency: 'DAILY', startDate: '2026-09-01', targetCount: 3 }],
    habitCheckins: [{ habitId: 'h2', date: '2026-10-19', status: 'DONE', count: 2 }]
  })
  assert.equal(dayEvents(result, '2026-10-19').allDay[0].done, false)
})

test('倒数日每年重复时落在每年同一天', () => {
  const result = buildCalendarEvents({
    days, lists, tags,
    countdowns: [{ publicId: 'c1', title: '生日', targetDate: '2000-10-21', repeatYearly: true }]
  })
  assert.equal(dayEvents(result, '2026-10-21').allDay[0].kind, 'countdown')
  assert.equal(dayEvents(result, '2026-10-20').allDay.length, 0)
})

test('倒数日不重复时只落在当年日期', () => {
  const result = buildCalendarEvents({
    days, lists, tags,
    countdowns: [{ publicId: 'c2', title: '考试', targetDate: '2025-10-21', repeatYearly: false }]
  })
  assert.equal(dayEvents(result, '2026-10-21').allDay.length, 0)
})

test('专注会话生成时间块', () => {
  const result = buildCalendarEvents({
    days, lists, tags,
    focusSessions: [{ publicId: 'f1', taskTitle: '工作', startedAt: '2026-10-19T05:00:00', plannedMinutes: 45, actualMinutes: 45, status: 'COMPLETED' }]
  })
  const [event] = dayEvents(result, '2026-10-19').timed
  assert.equal(event.kind, 'focus')
  assert.equal(event.startMinutes, 5 * 60)
  assert.equal(event.endMinutes, 5 * 60 + 45)
})

test('空输入返回空桶而不是抛错', () => {
  const result = buildCalendarEvents({ days: [], tasks: [] })
  assert.equal(result.byDay.size, 0)
  assert.deepEqual(daySummary(undefined), { total: 0, done: 0, timed: 0, allDay: 0 })
})

test('daySummary 统计当天条目与完成数', () => {
  const result = buildCalendarEvents({
    days: days.slice(0, 1), lists, tags,
    tasks: [
      { publicId: 's1', title: 'A', startAt: '2026-10-19T00:00:00', allDay: true, status: 'OPEN' },
      { publicId: 's2', title: 'B', startAt: '2026-10-19T09:00:00', allDay: false, status: 'COMPLETED' }
    ]
  })
  assert.deepEqual(daySummary(dayEvents(result, '2026-10-19')), { total: 2, done: 1, timed: 1, allDay: 1 })
})

test('节假日排在最前并按休班着色', () => {
  const result = buildCalendarEvents({
    days: days.slice(0, 1), lists, tags,
    holidays: [{ date: '2026-10-19', name: '调休', off: false }, { date: '2026-12-25', name: '圣诞节', off: true }],
    tasks: [{ publicId: 'x', title: '工作', startAt: '2026-10-19T00:00:00', allDay: true, status: 'OPEN' }]
  })
  const list = dayEvents(result, '2026-10-19').allDay
  assert.equal(list.length, 2)
  assert.equal(list[0].kind, 'holiday')
  assert.equal(list[0].color, '#8b92a3')
})

test('工时条目生成带时间的块并标注加班', () => {
  const result = buildCalendarEvents({
    days: days.slice(0, 1), lists, tags,
    worktime: [{ date: '2026-10-19', start: '08:30', end: '18:00', overtimeMinutes: 30 }]
  })
  const [event] = dayEvents(result, '2026-10-19').timed
  assert.equal(event.kind, 'worktime')
  assert.equal(event.startMinutes, 8 * 60 + 30)
  assert.equal(event.endMinutes, 18 * 60)
  assert.match(event.title, /加班 0\.5h/)
})

test('流水只在该日有收支时生成全天条目', () => {
  const result = buildCalendarEvents({
    days: days.slice(0, 1), lists, tags,
    ledger: [{ date: '2026-10-19', income: 100, expense: 23.5 }, { date: '2026-10-19', income: 0, expense: 0 }]
  })
  const list = dayEvents(result, '2026-10-19').allDay
  assert.equal(list.length, 1)
  assert.equal(list[0].kind, 'ledger')
  assert.equal(list[0].title, '收 ¥100 / 支 ¥23.5')
})

test('任务日历不传工时与流水时不产生对应条目', () => {
  const result = buildCalendarEvents({ days: days.slice(0, 1), lists, tags, tasks: [] })
  assert.equal(dayEvents(result, '2026-10-19').allDay.length, 0)
  assert.equal(dayEvents(result, '2026-10-19').timed.length, 0)
})

test('整日对齐的多日有刻任务进入全天区而不是时间轴', () => {
  const result = buildCalendarEvents({
    days, lists, tags,
    tasks: [{ publicId: 'span', title: '长期项目', allDay: false, startAt: '2026-10-19T00:00:00', dueAt: '2026-10-21T00:00:00', listId: 'list-work', status: 'OPEN' }]
  })
  assert.equal(dayEvents(result, '2026-10-19').allDay.length, 1)
  assert.equal(dayEvents(result, '2026-10-20').allDay.length, 1)
  assert.equal(dayEvents(result, '2026-10-19').timed.length, 0)
  assert.equal(dayEvents(result, '2026-10-19').allDay[0].promoted, true)
})

test('单日 00:00–23:59 的有刻任务按全天处理', () => {
  const result = buildCalendarEvents({
    days, lists, tags,
    tasks: [{ publicId: 'whole', title: '整日', allDay: false, startAt: '2026-10-19T00:00:00', dueAt: '2026-10-19T23:59:00', status: 'OPEN' }]
  })
  assert.equal(dayEvents(result, '2026-10-19').allDay.length, 1)
  assert.equal(dayEvents(result, '2026-10-19').timed.length, 0)
})

test('跨夜但有实际时刻的任务仍留在时间轴并裁切', () => {
  const result = buildCalendarEvents({
    days, lists, tags,
    tasks: [{ publicId: 'night', title: '加班', allDay: false, startAt: '2026-10-19T22:00:00', dueAt: '2026-10-20T02:00:00', status: 'OPEN' }]
  })
  assert.equal(dayEvents(result, '2026-10-19').allDay.length, 0)
  assert.equal(dayEvents(result, '2026-10-19').timed.length, 1)
  assert.equal(dayEvents(result, '2026-10-19').timed[0].promoted, false)
})

test('起止都是 00:00 的任务按当天全天处理而不是 15 分钟时间块', () => {
  const result = buildCalendarEvents({
    days, lists, tags,
    tasks: [{ publicId: 'midnight', title: '个人工作台', allDay: false, startAt: '2026-10-19T00:00:00', dueAt: '2026-10-19T00:00:00', status: 'OPEN' }]
  })
  assert.equal(dayEvents(result, '2026-10-19').allDay.length, 1)
  assert.equal(dayEvents(result, '2026-10-19').timed.length, 0)
  assert.equal(dayEvents(result, '2026-10-19').allDay[0].promoted, true)
})

test('00:00 起但有时长的任务仍留在时间轴', () => {
  const result = buildCalendarEvents({
    days, lists, tags,
    tasks: [{ publicId: 'early', title: '早班', allDay: false, startAt: '2026-10-19T00:00:00', dueAt: '2026-10-19T01:00:00', status: 'OPEN' }]
  })
  assert.equal(dayEvents(result, '2026-10-19').allDay.length, 0)
  assert.equal(dayEvents(result, '2026-10-19').timed.length, 1)
})

test('凌晨开始的三个重叠任务并排分列', () => {
  const result = buildCalendarEvents({
    days, lists, tags,
    tasks: [
      { publicId: 'a', title: 'A', allDay: false, startAt: '2026-10-19T00:30:00', dueAt: '2026-10-19T11:30:00', status: 'OPEN' },
      { publicId: 'b', title: 'B', allDay: false, startAt: '2026-10-19T00:30:00', dueAt: '2026-10-19T09:30:00', status: 'OPEN' },
      { publicId: 'c', title: 'C', allDay: false, startAt: '2026-10-19T05:30:00', dueAt: '2026-10-19T09:30:00', status: 'OPEN' }
    ]
  })
  const layout = layoutColumns(dayEvents(result, '2026-10-19').timed)
  assert.equal(layout.get('a').columns, 3)
  assert.equal(new Set(['a', 'b', 'c'].map(id => layout.get(id).column)).size, 3)
})
