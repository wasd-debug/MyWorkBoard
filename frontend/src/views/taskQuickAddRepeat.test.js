import assert from 'node:assert/strict'
import test from 'node:test'
import { buildQuickRepeatRule, createQuickRepeatState, quickRepeatPresets, quickRepeatValidation } from './taskQuickAddRepeat.js'

test('builds supported preset and custom RRULE values', () => {
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'WORKDAY' }), 'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'WEEKLY' }), 'FREQ=WEEKLY')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'CUSTOM', frequency: 'WEEKLY', interval: 2, weekdays: ['TU', 'TH'], endMode: 'COUNT', count: 4 }), 'FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH;COUNT=4')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'CUSTOM', frequency: 'MONTHLY', monthMode: 'WEEKDAY', ordinal: -1, weekday: 'FR' }), 'FREQ=MONTHLY;BYDAY=-1FR')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'CUSTOM', frequency: 'MONTHLY', monthMode: 'WORKDAY', workdayPosition: -1 }), 'FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'CUSTOM', frequency: 'YEARLY', monthMode: 'DATE', month: 8, monthDay: 15 }), 'FREQ=YEARLY;BYMONTH=8;BYMONTHDAY=15')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'CUSTOM', frequency: 'DAILY', skipWeekends: true }), 'FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR')
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
  assert.deepEqual(quickRepeatPresets.map(option => option.value), ['', 'DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY', 'WORKDAY', 'CUSTOM'])
})
