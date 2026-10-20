import assert from 'node:assert/strict'
import test from 'node:test'
import { groupTaskItems, sortTaskItems } from './taskList.js'

const today = new Date(2026, 9, 20, 12)

test('sortTaskItems orders by date with undated tasks last and stable ties', () => {
  const tasks = [
    { publicId: 'later', dueAt: '2026-10-22T09:00:00', sortOrder: 1 },
    { publicId: 'none', sortOrder: 0 },
    { publicId: 'today', dueAt: '2026-10-20T09:00:00', sortOrder: 2 },
  ]

  assert.deepEqual(sortTaskItems(tasks).map((task) => task.publicId), ['today', 'later', 'none'])
  assert.deepEqual(sortTaskItems(tasks, { direction: 'desc' }).map((task) => task.publicId), ['later', 'today', 'none'])
})

test('sortTaskItems supports priority, title and manual ordering', () => {
  const tasks = [
    { publicId: 'b', title: '乙', priority: 'LOW', sortOrder: 0 },
    { publicId: 'a', title: '甲', priority: 'HIGH', sortOrder: 1 },
  ]

  assert.deepEqual(sortTaskItems(tasks, { sortBy: 'priority' }).map((task) => task.publicId), ['a', 'b'])
  assert.deepEqual(sortTaskItems(tasks, { sortBy: 'title' }).map((task) => task.publicId), ['a', 'b'])
  assert.deepEqual(sortTaskItems(tasks, { sortBy: 'manual' }).map((task) => task.publicId), ['b', 'a'])
})

test('groupTaskItems groups by local day and keeps undated work distinct', () => {
  const tasks = [
    { publicId: 'overdue', dueAt: '2026-10-19T23:00:00' },
    { publicId: 'today', dueAt: '2026-10-20T23:00:00' },
    { publicId: 'later', dueAt: '2026-10-21T00:00:00' },
    { publicId: 'none' },
  ]

  assert.deepEqual(groupTaskItems(tasks, 'time', [], today).map((group) => [group.key, group.tasks.length]), [
    ['overdue', 1], ['today', 1], ['later', 1], ['none', 1],
  ])
})

test('groupTaskItems groups by list and preserves task order in each group', () => {
  const tasks = [{ publicId: 'a', listId: 'work' }, { publicId: 'b', listId: 'home' }, { publicId: 'c', listId: 'work' }]
  const lists = [{ publicId: 'work', name: '工作' }, { publicId: 'home', name: '生活' }]

  assert.deepEqual(groupTaskItems(tasks, 'list', lists).map((group) => [group.label, group.tasks.map((task) => task.publicId)]), [
    ['工作', ['a', 'c']], ['生活', ['b']],
  ])
})
