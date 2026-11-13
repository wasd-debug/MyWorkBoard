import test from 'node:test'
import assert from 'node:assert/strict'
import {
  budgetAmountUpdate, punchUpdate, taskDateClear, taskDateUpdate, taskPriorityUpdate,
  taskTitleUpdate, TASK_PRIORITIES
} from './homeWidgetEdits.js'

test('task title update trims input and ignores blank or unchanged titles', () => {
  assert.deepEqual(taskTitleUpdate('旧标题', '  新标题  '), { title: '新标题' })
  assert.deepEqual(taskTitleUpdate('旧标题', '多  个   空格'), { title: '多 个 空格' })
  assert.equal(taskTitleUpdate('旧标题', '   '), null)
  assert.equal(taskTitleUpdate('旧标题', '旧标题'), null)
  assert.equal(taskTitleUpdate('旧标题', 'x'.repeat(320)).title.length, 200)
})

test('date panel payload keeps range fields and only overrides repeat when provided', () => {
  assert.deepEqual(taskDateUpdate({ dueAt: '2026-11-14T09:00', startAt: '', allDay: false }),
    { dueAt: '2026-11-14T09:00', startAt: '', allDay: false })
  assert.deepEqual(taskDateUpdate({ dueAt: '2026-11-16T10:00', startAt: '2026-11-14T09:00', allDay: false }),
    { dueAt: '2026-11-16T10:00', startAt: '2026-11-14T09:00', allDay: false })
  // 没动重复设置就不带 rrule，避免把任务原有重复规则清空
  assert.equal('rrule' in taskDateUpdate({ dueAt: '2026-11-14T09:00' }), false)
  assert.deepEqual(taskDateUpdate({ dueAt: '2026-11-14T09:00', rrule: 'FREQ=DAILY' }),
    { dueAt: '2026-11-14T09:00', startAt: '', allDay: false, rrule: 'FREQ=DAILY', recurrenceAnchor: 'DUE_DATE' })
  assert.deepEqual(taskDateUpdate({ dueAt: '2026-11-14', rrule: '', recurrenceAnchor: 'COMPLETION_DATE' }).recurrenceAnchor,
    'COMPLETION_DATE')
  assert.equal(taskDateUpdate({ dueAt: '' }), null)
  assert.equal(taskDateUpdate(null), null)
  assert.deepEqual(taskDateClear(), { dueAt: '', startAt: '', allDay: false })
})

test('priority update rejects unknown values and no-ops', () => {
  assert.deepEqual(taskPriorityUpdate('NONE', 'HIGH'), { priority: 'HIGH' })
  assert.equal(taskPriorityUpdate('HIGH', 'HIGH'), null)
  assert.equal(taskPriorityUpdate('NONE', 'URGENT'), null)
  assert.deepEqual(TASK_PRIORITIES.includes('MEDIUM'), true)
})

test('budget update carries revision for existing rows and month only for new ones', () => {
  assert.deepEqual(budgetAmountUpdate({ id: 'b1', revision: 3, categoryId: 12 }, '1200.456', '2026-11'), {
    monthKey: '2026-11', budget: 1200.46, categoryId: '12', id: 'b1', revision: 3
  })
  assert.deepEqual(budgetAmountUpdate(null, 3000, '2026-11'), { monthKey: '2026-11', budget: 3000 })
  assert.equal(budgetAmountUpdate(null, 0, '2026-11'), null)
  assert.equal(budgetAmountUpdate(null, 'abc', '2026-11'), null)
  assert.equal(budgetAmountUpdate(null, 100, '2026-13'), null)
})

test('punch update validates date and clock format, clamps break time', () => {
  assert.deepEqual(punchUpdate('2026-11-13', { start: '09:00', end: '18:30', rest: '90' }),
    { date: '2026-11-13', start: '09:00', end: '18:30', rest: 90 })
  // 跨夜班次允许下班早于上班
  assert.deepEqual(punchUpdate('2026-11-13', { start: '18:00', end: '02:00', rest: 0 }),
    { date: '2026-11-13', start: '18:00', end: '02:00', rest: 0 })
  assert.equal(punchUpdate('2026-11-13', { start: '09:00', end: '18:30', rest: 9999 }).rest, 600)
  assert.equal(punchUpdate('2026/11/13', { start: '09:00', end: '18:00' }), null)
  assert.equal(punchUpdate('2026-11-13', { start: '', end: '18:00' }), null)
})
