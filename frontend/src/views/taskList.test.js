import assert from 'node:assert/strict'
import test from 'node:test'
import { groupTaskItems, sortTaskItems, taskGroupDropPatch } from './taskList.js'

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

test('sortTaskItems supports creation, modification and tag ordering', () => {
  const tasks = [
    { publicId: 'new', createdAt: '2026-10-20T10:00:00Z', updatedAt: '2026-10-22T10:00:00Z', tagIds: ['z'] },
    { publicId: 'old', createdAt: '2026-10-19T10:00:00Z', updatedAt: '2026-10-21T10:00:00Z', tagIds: ['a'] },
  ]
  assert.deepEqual(sortTaskItems(tasks, { sortBy: 'created' }).map((task) => task.publicId), ['old', 'new'])
  assert.deepEqual(sortTaskItems(tasks, { sortBy: 'updated', direction: 'desc' }).map((task) => task.publicId), ['new', 'old'])
  assert.deepEqual(sortTaskItems(tasks, { sortBy: 'tag' }).map((task) => task.publicId), ['old', 'new'])
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

test('groupTaskItems can preserve empty active task lists for the all-tasks board', () => {
  const lists = [
    { publicId: 'inbox', name: '收集箱' },
    { publicId: 'work', name: '工作' },
    { publicId: 'notes', name: '笔记', objectType: 'NOTE' },
    { publicId: 'archived', name: '已归档', archived: true },
  ]
  const groups = groupTaskItems([{ publicId: 'one', listId: 'inbox' }], 'list', lists, new Date(), [], { includeEmptyLists: true })
  assert.deepEqual(groups.map((group) => [group.key, group.label, group.tasks.length]), [
    ['list:inbox', '收集箱', 1],
    ['list:work', '工作', 0],
  ])
})

test('groupTaskItems supports tag, priority and creation-date groups', () => {
  const tasks = [
    { publicId: 'a', tagIds: ['focus'], priority: 'LOW', createdAt: '2026-10-20T09:00:00Z' },
    { publicId: 'b', tagIds: ['home'], priority: 'HIGH', createdAt: '2026-10-19T09:00:00Z' },
    { publicId: 'c', priority: 'NONE' },
  ]
  const tags = [{ publicId: 'focus', name: '专注' }, { publicId: 'home', name: '生活' }]
  assert.deepEqual(groupTaskItems(tasks, 'tag', [], today, tags).map((group) => group.label), ['专注', '生活', '无标签'])
  assert.deepEqual(groupTaskItems(tasks, 'priority', [], today).map((group) => group.label), ['高优先级', '低优先级', '无优先级'])
  assert.deepEqual(groupTaskItems(tasks, 'created', [], today).map((group) => group.label), ['2026-10-20', '2026-10-19', '创建时间未知'])
})

test('taskGroupDropPatch changes the property represented by the target group', () => {
  const task = { listId: 'inbox', tagIds: ['old', 'other'], priority: 'LOW', dueAt: null }
  assert.deepEqual(taskGroupDropPatch(task, 'list', 'list:work'), { listId: 'work' })
  assert.deepEqual(taskGroupDropPatch(task, 'tag', 'tag:new'), { tagIds: ['new', 'old', 'other'] })
  assert.deepEqual(taskGroupDropPatch(task, 'priority', 'priority:HIGH'), { priority: 'HIGH' })
  assert.deepEqual(taskGroupDropPatch(task, 'time', 'none'), null)
  assert.equal(taskGroupDropPatch(task, 'created', 'created:2026-10-20'), null)
})

test('taskGroupDropPatch assigns stable local dates for time groups', () => {
  const now = new Date(2026, 9, 24, 13, 30)
  const task = { dueAt: null }
  assert.equal(new Date(taskGroupDropPatch(task, 'time', 'today', now).dueAt).getDate(), 24)
  assert.equal(new Date(taskGroupDropPatch(task, 'time', 'later', now).dueAt).getDate(), 25)
  assert.equal(new Date(taskGroupDropPatch(task, 'time', 'overdue', now).dueAt).getDate(), 23)
})
