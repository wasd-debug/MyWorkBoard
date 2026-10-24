import test from 'node:test'
import assert from 'node:assert/strict'
import { completionDateRange, filterCompletionTasks, normalizeCompletionListSelection, toggleCompletionListSelection } from './taskCompletionFilters.js'

const now = new Date(2026, 9, 24, 15)

test('completion date presets use local Monday weeks and calendar months', () => {
  assert.deepEqual(completionDateRange('THIS_WEEK', now), ['2026-10-19', '2026-10-26'])
  assert.deepEqual(completionDateRange('LAST_WEEK', now), ['2026-10-12', '2026-10-19'])
  assert.deepEqual(completionDateRange('THIS_MONTH', now), ['2026-10-01', '2026-11-01'])
})

test('custom completion date ranges include both endpoints and reject reversed ranges', () => {
  assert.deepEqual(completionDateRange('CUSTOM', now, '2026-10-02', '2026-10-05'), ['2026-10-02', '2026-10-06'])
  assert.equal(completionDateRange('CUSTOM', now, '2026-10-06', '2026-10-05'), null)
})

test('completed and abandoned filters use their own persisted status timestamps', () => {
  const tasks = [
    { status: 'COMPLETED', completedAt: '2026-10-22T10:00:00', abandonedAt: null, listId: 'a' },
    { status: 'ABANDONED', completedAt: null, abandonedAt: '2026-10-22T10:00:00', listId: 'b' },
    { status: 'ABANDONED', completedAt: null, abandonedAt: null, listId: 'a' },
    { status: 'COMPLETED', completedAt: '2026-10-22T10:00:00', deleted: true, listId: 'a' },
  ]
  assert.equal(filterCompletionTasks(tasks, { status: 'COMPLETED', dateRange: 'THIS_WEEK' }, now).length, 1)
  assert.equal(filterCompletionTasks(tasks, { status: 'ABANDONED', dateRange: 'THIS_WEEK' }, now).length, 1)
  assert.equal(filterCompletionTasks(tasks, { status: 'ABANDONED', listIds: ['a'] }, now).length, 1)
  assert.equal(filterCompletionTasks(tasks, { status: 'ABANDONED', listIds: [] }, now).length, 0)
  assert.equal(filterCompletionTasks(tasks, { status: 'COMPLETED', listIds: ['b'] }, now).length, 0)
})

test('completion list selection includes inbox and normalizes all-selected state', () => {
  const allIds = ['inbox', 'list-a', 'list-b']
  assert.deepEqual(toggleCompletionListSelection(null, allIds, 'inbox', false), ['list-a', 'list-b'])
  assert.deepEqual(toggleCompletionListSelection(null, allIds, 'list-a', false), ['inbox', 'list-b'])
  assert.deepEqual(toggleCompletionListSelection([], allIds, 'list-a', true), ['list-a'])
  assert.equal(normalizeCompletionListSelection(['list-a', 'inbox', 'list-b', 'list-a'], allIds), null)
  assert.deepEqual(normalizeCompletionListSelection(['list-a', 'stale'], allIds), ['list-a'])
  assert.deepEqual(normalizeCompletionListSelection([], allIds), [])
})
