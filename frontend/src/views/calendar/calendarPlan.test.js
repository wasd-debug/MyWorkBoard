import test from 'node:test'
import assert from 'node:assert/strict'
import { groupPlanTasks, isOverdue, isUndated, planCandidates } from './calendarPlan.js'

const now = new Date('2026-10-28T12:00:00')
const lists = [
  { publicId: 'l-work', name: '工作', color: '#6bcb77', sortOrder: 1 },
  { publicId: 'l-life', name: '生活', color: '#f0b429', sortOrder: 2 }
]
const tags = [{ publicId: 't-java', name: 'Java', color: '#f5c33b' }]
const tasks = [
  { publicId: 'a', title: '无日期高优', listId: 'l-work', priority: 'HIGH', status: 'OPEN' },
  { publicId: 'b', title: '过期任务', listId: 'l-work', priority: 'NONE', status: 'OPEN', dueAt: '2026-10-20T09:00:00' },
  { publicId: 'c', title: '未来任务', listId: 'l-life', priority: 'LOW', status: 'OPEN', dueAt: '2026-11-02T09:00:00' },
  { publicId: 'd', title: '已完成', listId: 'l-work', priority: 'HIGH', status: 'COMPLETED' },
  { publicId: 'e', title: '笔记', listId: 'l-work', priority: 'NONE', status: 'OPEN', itemKind: 'NOTE' },
  { publicId: 'f', title: '子任务', listId: 'l-work', priority: 'NONE', status: 'OPEN', parentId: 'a' },
  { publicId: 'g', title: '带标签', listId: 'l-life', priority: 'MEDIUM', status: 'OPEN', tagIds: ['t-java'] }
]

test('isUndated / isOverdue 判定', () => {
  assert.equal(isUndated(tasks[0]), true)
  assert.equal(isOverdue(tasks[1], now), true)
  assert.equal(isOverdue(tasks[2], now), false)
})

test('默认只保留无日期的待安排任务（笔记默认仍然显示）', () => {
  const pool = planCandidates(tasks, { lists, now })
  assert.deepEqual(pool.map(item => item.publicId), ['a', 'e', 'f', 'g'])
})

test('打开已过期筛选后并集包含过期任务', () => {
  const pool = planCandidates(tasks, { lists, now, includeOverdue: true })
  assert.deepEqual(pool.map(item => item.publicId), ['a', 'b', 'e', 'f', 'g'])
})

test('隐藏笔记与隐藏任务层级生效', () => {
  const pool = planCandidates(tasks, { lists, now, hideNotes: true, hideHierarchy: true })
  assert.deepEqual(pool.map(item => item.publicId), ['a', 'g'])
})

test('按清单分组并按清单顺序排列', () => {
  const pool = planCandidates(tasks, { lists, now })
  const groups = groupPlanTasks(pool, { groupBy: 'list', lists, tags })
  assert.deepEqual(groups.map(item => item.label), ['工作', '生活'])
  assert.deepEqual(groups[0].items.map(item => item.publicId), ['a', 'e', 'f'])
  assert.deepEqual(groups[1].items.map(item => item.publicId), ['g'])
})

test('按优先级分组顺序为高中低无', () => {
  const pool = planCandidates(tasks, { lists, now })
  const groups = groupPlanTasks(pool, { groupBy: 'priority', lists, tags })
  assert.deepEqual(groups.map(item => item.key), ['HIGH', 'MEDIUM', 'NONE'])
})

test('按标签分组时无标签归入独立分组', () => {
  const pool = planCandidates(tasks, { lists, now })
  const groups = groupPlanTasks(pool, { groupBy: 'tag', lists, tags })
  assert.equal(groups[0].label, 'Java')
  assert.equal(groups[1].label, '无标签')
  assert.deepEqual(groups[1].items.map(item => item.publicId), ['a', 'e', 'f'])
})

test('全天任务今天到期不算过期，昨天到期才算', () => {
  const now = new Date('2026-10-28T12:00:00')
  assert.equal(isOverdue({ dueAt: '2026-10-28T00:00:00' }, now), false)
  assert.equal(isOverdue({ dueAt: '2026-10-27T00:00:00' }, now), true)
  assert.equal(isOverdue({ dueAt: '2026-10-28T09:00:00', allDay: false }, now), true)
})
