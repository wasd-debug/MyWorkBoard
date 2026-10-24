import assert from 'node:assert/strict'
import test from 'node:test'
import { buildSummaryMarkdown, filterSummaryItems, groupSummaryItems, summaryDateRange } from './taskSummary.js'

test('summary date presets use local full-day intervals and support a custom inclusive range', () => {
  const now = new Date(2026, 9, 21, 15, 30)
  const today = summaryDateRange('TODAY', now)
  assert.equal(today.from.getDate(), 21)
  assert.equal(today.to.getDate(), 22)
  const custom = summaryDateRange('CUSTOM', now, { from: '2026-10-18', to: '2026-10-20' })
  assert.equal(custom.from.getDate(), 18)
  assert.equal(custom.to.getDate(), 21)
  const invalid = summaryDateRange('CUSTOM', now, { from: '2026-10-20', to: '2026-10-18' })
  assert.ok(invalid.from > invalid.to)
})

test('summary filters combine date, status, list, tag, and check-in scope', () => {
  const tasks = [
    { title: 'done', status: 'COMPLETED', dueAt: '2026-10-21T10:00:00', listId: 'l1', tagIds: ['t1'] },
    { title: 'open', status: 'OPEN', dueAt: '2026-10-21T12:00:00', listId: 'l1', tagIds: ['t2'] },
    { title: 'checkin', kind: 'checkin', status: 'DONE', date: '2026-10-21', tagIds: [] },
  ]
  const result = filterSummaryItems(tasks, {
    range: summaryDateRange('TODAY', new Date(2026, 9, 21)),
    listMode: 'SELECTED', listIds: ['l1'], tagMode: 'SELECTED', tagIds: ['t2'],
    status: 'INCOMPLETE', priorities: [],
  }, new Date(2026, 9, 21, 13))
  assert.deepEqual(result.map(item => item.title), ['open'])
})

test('in progress and not started are distinct open-task states', () => {
  const tasks = [
    { title: 'started', status: 'OPEN', startAt: '2026-10-21T09:00:00' },
    { title: 'not started', status: 'OPEN' },
  ]
  const base = { range: null, listMode: 'ALL', listIds: [], tagMode: 'ALL', tagIds: [], priorities: [] }
  assert.deepEqual(filterSummaryItems(tasks, { ...base, status: 'IN_PROGRESS' }, new Date(2026, 9, 21, 12)).map(item => item.title), ['started'])
  assert.deepEqual(filterSummaryItems(tasks, { ...base, status: 'INCOMPLETE' }, new Date(2026, 9, 21, 12)).map(item => item.title), ['not started'])
})

test('summary groups and renders only the selected report fields', () => {
  const groups = groupSummaryItems([
    { title: 'A', status: 'COMPLETED', listId: 'l1', completedAt: '2026-10-20T09:00:00', focusMinutes: 25 },
  ], 'status', { l1: '工作' })
  const markdown = buildSummaryMarkdown(groups, ['title', 'focus'])
  assert.match(markdown, /已完成（1）/)
  assert.match(markdown, /任务名称 \| 专注数据/)
  assert.match(markdown, /A \| 25 分钟/)
  assert.doesNotMatch(markdown, /完成时间/)
})
