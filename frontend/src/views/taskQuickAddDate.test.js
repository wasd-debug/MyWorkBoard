import test from 'node:test'
import assert from 'node:assert/strict'
import { calendarMonthDays, localDateKey, nextWholeHour, quickReminderOptions, quickReminderPayloads } from './taskQuickAddDate.js'

test('quick date calendar always returns six Monday-start weeks with adjacent dates', () => {
  const days = calendarMonthDays(new Date(2026, 9, 1))
  assert.equal(days.length, 42)
  assert.equal(days[0].date.getDay(), 1)
  assert.equal(days.find(day => day.inMonth).key, '2026-10-01')
  assert.ok(days.some(day => day.key === '2026-09-28'))
  assert.ok(days.some(day => day.key === '2026-11-01'))
})

test('quick date defaults to the next local whole hour', () => {
  assert.equal(nextWholeHour(new Date(2026, 9, 22, 16, 42)), '2026-10-22T17:00')
  assert.equal(nextWholeHour(new Date(2026, 9, 22, 23, 42)), '2026-10-23T00:00')
})

test('quick reminder options follow whether a task has a time', () => {
  assert.deepEqual(quickReminderOptions(true).map(option => option.label), ['当天', '提前 1 天', '提前 2 天', '提前 3 天', '提前 1 周', '自定义'])
  assert.deepEqual(quickReminderOptions(false).map(option => option.label), ['准时', '提前 5 分钟', '提前 30 分钟', '提前 1 小时', '提前 1 天', '自定义'])
})

test('timed reminders support multiple relative offsets and retain repeat-until-done', () => {
  assert.deepEqual(quickReminderPayloads({ dueAt: '2026-10-22T17:00', allDay: false, config: { enabled: true, presets: ['5_MIN', '30_MIN'], dailyUntilDone: true } }), [
    { kind: 'RELATIVE', offsetMinutes: 5, remindAt: null, channel: 'IN_APP', dailyUntilDone: true },
    { kind: 'RELATIVE', offsetMinutes: 30, remindAt: null, channel: 'IN_APP', dailyUntilDone: true },
  ])
})

test('all-day reminders use an absolute local reminder time before the due date', () => {
  const [payload] = quickReminderPayloads({ dueAt: '2026-10-22T00:00', allDay: true, config: { enabled: true, presets: ['1_DAY'], time: '09:00' } })
  assert.equal(payload.kind, 'ABSOLUTE')
  assert.equal(localDateKey(new Date(payload.remindAt)), '2026-10-21')
  assert.equal(new Date(payload.remindAt).getHours(), 9)
})
