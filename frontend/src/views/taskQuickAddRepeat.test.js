import assert from 'node:assert/strict'
import test from 'node:test'
import { buildQuickRepeatRule, createQuickRepeatState, quickRepeatValidation } from './taskQuickAddRepeat.js'

test('builds supported preset and custom RRULE values', () => {
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'WORKDAY' }), 'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR')
  assert.equal(buildQuickRepeatRule({ ...createQuickRepeatState(), preset: 'CUSTOM', frequency: 'WEEKLY', interval: 2, weekdays: ['TU', 'TH'], endMode: 'COUNT', count: 4 }), 'FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH;COUNT=4')
})

test('validates custom weekly and end conditions', () => {
  assert.equal(quickRepeatValidation({ ...createQuickRepeatState(), preset: 'CUSTOM', frequency: 'WEEKLY', weekdays: [] }), '每周重复至少选择一天')
  assert.equal(quickRepeatValidation({ ...createQuickRepeatState(), preset: 'CUSTOM', endMode: 'UNTIL', until: '' }), '请选择重复结束日期')
  assert.equal(quickRepeatValidation({ ...createQuickRepeatState(), preset: 'CUSTOM', endMode: 'COUNT', count: 0 }), '重复次数必须大于 0')
})
