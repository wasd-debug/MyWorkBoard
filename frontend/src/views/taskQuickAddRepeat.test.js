import assert from 'node:assert/strict'
import test from 'node:test'
import { buildQuickRepeatRule, createQuickRepeatState, parseLunarLabel, quickRepeatMatchesDate, quickRepeatPresets, quickRepeatValidation } from './taskQuickAddRepeat.js'

test('builds supported preset and custom RRULE values', () => {
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'WORKDAY' }), 'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'WEEKLY' }), 'FREQ=WEEKLY')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'CUSTOM', frequency: 'WEEKLY', interval: 2, weekdays: ['TU', 'TH'], endMode: 'COUNT', count: 4 }), 'FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH;COUNT=4')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'CUSTOM', frequency: 'MONTHLY', monthMode: 'WEEKDAY', ordinal: -1, weekday: 'FR' }), 'FREQ=MONTHLY;BYDAY=-1FR')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'CUSTOM', frequency: 'MONTHLY', monthMode: 'WORKDAY', workdayPosition: -1 }), 'FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'CUSTOM', frequency: 'YEARLY', monthMode: 'DATE', month: 8, monthDay: 15 }), 'FREQ=YEARLY;BYMONTH=8;BYMONTHDAY=15')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'CUSTOM', frequency: 'DAILY', skipWeekends: true }), 'FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'CUSTOM', mode: 'COMPLETION_DATE', frequency: 'WEEKLY', weekdays: ['MO'], endMode: 'UNTIL', until: '2026-12-31' }), 'FREQ=WEEKLY;BYDAY=MO;UNTIL=20261231T235959Z')
})

test('validates custom weekly and end conditions', () => {
  assert.equal(quickRepeatValidation({ ...createQuickRepeatState(), preset: 'CUSTOM', frequency: 'WEEKLY', weekdays: [] }), '每周重复至少选择一个有效日期')
  assert.equal(quickRepeatValidation({ ...createQuickRepeatState(), preset: 'CUSTOM', endMode: 'UNTIL', until: '' }), '请选择重复结束日期')
  assert.equal(quickRepeatValidation({ ...createQuickRepeatState(), preset: 'CUSTOM', endMode: 'COUNT', count: 0 }), '重复次数必须大于 0')
  assert.equal(quickRepeatValidation({ ...createQuickRepeatState(), preset: 'CUSTOM', frequency: 'WEEKLY', weekdays: ['SA'], skipWeekends: true }), '每周重复至少选择一个有效日期')
  assert.equal(quickRepeatValidation({ ...createQuickRepeatState(), preset: 'CUSTOM', frequency: 'MONTHLY', monthMode: 'WEEKDAY', weekday: 'SU', skipWeekends: true }), '跳过双休日时不能选择周六或周日')
  assert.equal(quickRepeatValidation({ ...createQuickRepeatState(), preset: 'CUSTOM', frequency: 'YEARLY', monthMode: 'WORKDAY' }), '年度重复仅支持按日期或按星期')
})

test('does not expose recurrence presets that have no persisted server semantics', () => {
  assert.deepEqual(quickRepeatPresets.map(option => option.value), ['', 'DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY', 'WORKDAY', 'LEGAL_WORKDAY', 'WEEKEND', 'LEGAL_HOLIDAY', 'LUNAR_MONTHLY', 'LUNAR_YEARLY', 'CUSTOM'])
})

test('serializes legal holiday and lunar recurrence semantics', () => {
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'LEGAL_WORKDAY' }), 'FREQ=DAILY;X-TASK-KIND=LEGAL_WORKDAY')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'WEEKEND' }), 'FREQ=WEEKLY;BYDAY=SA,SU')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'LEGAL_HOLIDAY' }), 'FREQ=DAILY;X-TASK-KIND=LEGAL_HOLIDAY')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'LUNAR_MONTHLY', lunarDay: 18 }), 'FREQ=MONTHLY;X-TASK-KIND=LUNAR_MONTHLY;X-LUNAR-DAY=18')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'LUNAR_YEARLY', lunarMonth: 8, lunarDay: 15 }), 'FREQ=YEARLY;X-TASK-KIND=LUNAR_YEARLY;X-LUNAR-MONTH=8;X-LUNAR-DAY=15')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'CUSTOM', frequency: 'MONTHLY', monthMode: 'DATE', lastDay: true, skipLegalHolidays: true }), 'FREQ=MONTHLY;BYMONTHDAY=-1;X-SKIP-HOLIDAYS=TRUE')
})

test('parses backend lunar labels into lunar month and day', () => {
  assert.deepEqual(parseLunarLabel('九月初九'), { month: 9, day: 9 })
  assert.deepEqual(parseLunarLabel('闰二月初一'), { month: 2, day: 1 })
  assert.deepEqual(parseLunarLabel('十一月十四'), { month: 11, day: 14 })
  assert.deepEqual(parseLunarLabel('正月初十'), { month: 1, day: 10 })
  assert.deepEqual(parseLunarLabel('八月二十'), { month: 8, day: 20 })
  // 「廿/卅」是后端农历日期的常见写法（如八月廿一），必须能解析
  assert.deepEqual(parseLunarLabel('七月廿一'), { month: 7, day: 21 })
  assert.deepEqual(parseLunarLabel('九月廿九'), { month: 9, day: 29 })
})

test('matches quick repeat dates for preset rules', () => {
  const state = createQuickRepeatState()
  assert.equal(quickRepeatMatchesDate(state, new Date(2026, 9, 27)), false)
  assert.equal(quickRepeatMatchesDate({ ...state, preset: 'DAILY' }, new Date(2026, 9, 27)), true)
  assert.equal(quickRepeatMatchesDate({ ...state, preset: 'WEEKEND' }, new Date(2026, 9, 31)), true) // 周六
  assert.equal(quickRepeatMatchesDate({ ...state, preset: 'WEEKEND' }, new Date(2026, 9, 27)), false) // 周二
  assert.equal(quickRepeatMatchesDate({ ...state, preset: 'WORKDAY' }, new Date(2026, 9, 30)), true) // 周五
  // 调休上班的周六视为法定工作日，放假的周四不是
  const holidays = { '2026-10-31': { off: false }, '2026-10-29': { off: true } }
  const context = { holidays }
  assert.equal(quickRepeatMatchesDate({ ...state, preset: 'LEGAL_WORKDAY' }, new Date(2026, 9, 31), context), true)
  assert.equal(quickRepeatMatchesDate({ ...state, preset: 'LEGAL_WORKDAY' }, new Date(2026, 9, 29), context), false)
  assert.equal(quickRepeatMatchesDate({ ...state, preset: 'LEGAL_HOLIDAY' }, new Date(2026, 9, 29), context), true)
  assert.equal(quickRepeatMatchesDate({ ...state, preset: 'LEGAL_HOLIDAY' }, new Date(2026, 9, 31), context), false)
})

test('matches custom rules with weekdays, month days, and intervals', () => {
  const base = { ...createQuickRepeatState(), preset: 'CUSTOM' }
  const context = { anchor: new Date(2026, 9, 27) }
  assert.equal(quickRepeatMatchesDate({ ...base, frequency: 'WEEKLY', weekdays: ['TU', 'FR'] }, new Date(2026, 9, 30)), true)
  assert.equal(quickRepeatMatchesDate({ ...base, frequency: 'WEEKLY', weekdays: ['TU', 'FR'] }, new Date(2026, 9, 28)), false)
  assert.equal(quickRepeatMatchesDate({ ...base, frequency: 'MONTHLY', monthMode: 'DATE', monthDays: [1, 15, 27] }, new Date(2026, 9, 27)), true)
  assert.equal(quickRepeatMatchesDate({ ...base, frequency: 'MONTHLY', monthMode: 'DATE', monthDays: [1, 15, 27] }, new Date(2026, 9, 28)), false)
  assert.equal(quickRepeatMatchesDate({ ...base, frequency: 'MONTHLY', monthMode: 'DATE', lastDay: true }, new Date(2026, 9, 31)), true)
  assert.equal(quickRepeatMatchesDate({ ...base, frequency: 'MONTHLY', monthMode: 'WEEKDAY', ordinal: -1, weekday: 'TU' }, new Date(2026, 9, 27)), true)
  // 每 2 周：锚点 10/27（周二），11/10 命中，11/3 不命中
  const biweekly = { ...base, frequency: 'WEEKLY', interval: 2, weekdays: ['TU'] }
  assert.equal(quickRepeatMatchesDate(biweekly, new Date(2026, 10, 10), context), true)
  assert.equal(quickRepeatMatchesDate(biweekly, new Date(2026, 10, 3), context), false)
  // 每月第 1 个工作日 / 最后一个工作日（含调休：10/31 周六上班）
  const firstWorkday = { ...base, frequency: 'MONTHLY', monthMode: 'WORKDAY', workdayPosition: 1 }
  const lastWorkday = { ...base, frequency: 'MONTHLY', monthMode: 'WORKDAY', workdayPosition: -1 }
  const holidayContext = { holidays: { '2026-10-31': { off: false } } }
  assert.equal(quickRepeatMatchesDate(firstWorkday, new Date(2026, 9, 1), holidayContext), true)
  assert.equal(quickRepeatMatchesDate(lastWorkday, new Date(2026, 9, 31), holidayContext), true)
  // 每 2 天：锚点 10/27，10/29 命中，10/28 不命中
  const every2Days = { ...base, frequency: 'DAILY', interval: 2 }
  assert.equal(quickRepeatMatchesDate(every2Days, new Date(2026, 9, 29), context), true)
  assert.equal(quickRepeatMatchesDate(every2Days, new Date(2026, 9, 28), context), false)
})

test('matches lunar repeats using calendar lunar metadata', () => {
  const lunar = new Map([['2026-10-27', { month: 9, day: 17 }]])
  const context = { lunar }
  const state = createQuickRepeatState()
  assert.equal(quickRepeatMatchesDate({ ...state, preset: 'LUNAR_MONTHLY', lunarDay: 17 }, new Date(2026, 9, 27), context), true)
  assert.equal(quickRepeatMatchesDate({ ...state, preset: 'LUNAR_MONTHLY', lunarDay: 18 }, new Date(2026, 9, 27), context), false)
  assert.equal(quickRepeatMatchesDate({ ...state, preset: 'LUNAR_YEARLY', lunarMonth: 9, lunarDay: 17 }, new Date(2026, 9, 27), context), true)
  assert.equal(quickRepeatMatchesDate({ ...state, preset: 'LUNAR_YEARLY', lunarMonth: 8, lunarDay: 17 }, new Date(2026, 9, 27), context), false)
  assert.equal(quickRepeatMatchesDate({ ...state, preset: 'LUNAR_MONTHLY', lunarDay: 17 }, new Date(2026, 9, 28), context), false)
})
