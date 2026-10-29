import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildQuadrantGroups, dateBucket, defaultQuadrants, describeRule, groupTasks, normalizeState,
  ruleMatches, sortTasks, splitByQuadrant, summarizeRule
} from './taskQuadrant.js'

const NOW = new Date(2026, 9, 29, 10, 0, 0) // 2026-10-29 周四
const lists = [
  { publicId: 'l-work', name: '工作', icon: 'briefcase', color: '#4d96ff' },
  { publicId: 'l-life', name: '生活', icon: 'house', color: '#6bcb77' }
]
const tags = [
  { publicId: 't-study', name: '学习', color: '#4d96ff' },
  { publicId: 't-urgent', name: '紧急', color: '#e34d59' }
]

function makeTask(overrides = {}) {
  return {
    publicId: overrides.publicId || 'task-1',
    title: '任务',
    status: 'OPEN',
    priority: 'NONE',
    listId: 'l-work',
    tagIds: [],
    dueAt: null,
    createdAt: '2026-10-20T02:00:00Z',
    itemKind: 'TASK',
    ...overrides
  }
}

test('dateBucket 按本地日期切分已过期/今天/明天/本周/以后/无日期', () => {
  assert.equal(dateBucket({ dueAt: '2026-10-28T10:00:00' }, NOW), 'OVERDUE')
  assert.equal(dateBucket({ dueAt: '2026-10-29T23:30:00' }, NOW), 'TODAY')
  assert.equal(dateBucket({ dueAt: '2026-10-30T09:00:00' }, NOW), 'TOMORROW')
  // 2026-11-01 是本周日
  assert.equal(dateBucket({ dueAt: '2026-11-01T09:00:00' }, NOW), 'THIS_WEEK')
  assert.equal(dateBucket({ dueAt: '2026-11-10T09:00:00' }, NOW), 'LATER')
  assert.equal(dateBucket({}, NOW), 'NONE')
})

test('ruleMatches：空条件视为全部，条件之间取交集', () => {
  const task = makeTask({ priority: 'HIGH', listId: 'l-work', tagIds: ['t-study'], dueAt: '2026-10-29T18:00:00' })
  assert.equal(ruleMatches(task, { listIds: [], tagIds: [], dates: [], priorities: [], itemKind: 'ALL' }, NOW), true)
  assert.equal(ruleMatches(task, { listIds: ['l-work'], priorities: ['HIGH'] }, NOW), true)
  assert.equal(ruleMatches(task, { listIds: ['l-life'] }, NOW), false)
  assert.equal(ruleMatches(task, { tagIds: ['t-urgent'] }, NOW), false)
  assert.equal(ruleMatches(task, { dates: ['TODAY'] }, NOW), true)
  assert.equal(ruleMatches(task, { dates: ['OVERDUE'] }, NOW), false)
  assert.equal(ruleMatches(task, { priorities: ['LOW'] }, NOW), false)
})

test('ruleMatches 区分任务与笔记', () => {
  const note = makeTask({ itemKind: 'NOTE' })
  assert.equal(ruleMatches(note, { itemKind: 'TASK' }, NOW), false)
  assert.equal(ruleMatches(note, { itemKind: 'NOTE' }, NOW), true)
  assert.equal(ruleMatches(note, { itemKind: 'ALL' }, NOW), true)
})

test('splitByQuadrant 默认不收已完成与已放弃，且只落进第一个命中的象限', () => {
  const quadrants = defaultQuadrants()
  const tasks = [
    makeTask({ publicId: 'a', priority: 'HIGH', dueAt: '2026-10-29T18:00:00' }),
    makeTask({ publicId: 'b', priority: 'HIGH', dueAt: '2026-11-20T09:00:00' }),
    makeTask({ publicId: 'c', priority: 'LOW', dueAt: '2026-10-28T09:00:00' }),
    makeTask({ publicId: 'd', priority: 'LOW' }),
    makeTask({ publicId: 'e', priority: 'HIGH', dueAt: '2026-10-29T18:00:00', status: 'COMPLETED' }),
    makeTask({ publicId: 'f', priority: 'HIGH', status: 'ABANDONED' })
  ]
  const split = splitByQuadrant(tasks, quadrants, { now: NOW })
  assert.deepEqual(split.IMPORTANT_URGENT.map(item => item.publicId), ['a'])
  assert.deepEqual(split.IMPORTANT_NOT_URGENT.map(item => item.publicId), ['b'])
  assert.deepEqual(split.NOT_IMPORTANT_URGENT.map(item => item.publicId), ['c'])
  assert.deepEqual(split.NOT_IMPORTANT_NOT_URGENT.map(item => item.publicId), ['d'])
})

test('splitByQuadrant 打开显示已完成后纳入已完成任务', () => {
  const quadrants = defaultQuadrants()
  const tasks = [makeTask({ publicId: 'e', priority: 'HIGH', dueAt: '2026-10-29T18:00:00', status: 'COMPLETED' })]
  const split = splitByQuadrant(tasks, quadrants, { now: NOW, showCompleted: true })
  assert.deepEqual(split.IMPORTANT_URGENT.map(item => item.publicId), ['e'])
})

test('sortTasks 按截止时间排序时无日期条目始终排最后', () => {
  const tasks = [
    makeTask({ publicId: 'none' }),
    makeTask({ publicId: 'late', dueAt: '2026-11-02T09:00:00' }),
    makeTask({ publicId: 'early', dueAt: '2026-10-27T09:00:00' })
  ]
  assert.deepEqual(sortTasks(tasks, 'dueAt', 'asc').map(item => item.publicId), ['early', 'late', 'none'])
  assert.deepEqual(sortTasks(tasks, 'dueAt', 'desc').map(item => item.publicId), ['late', 'early', 'none'])
})

test('sortTasks 支持标题、优先级与修改时间', () => {
  const tasks = [
    makeTask({ publicId: 'c', title: '丙', priority: 'NONE', updatedAt: '2026-10-25T09:00:00Z' }),
    makeTask({ publicId: 'a', title: '甲', priority: 'HIGH', updatedAt: '2026-10-27T09:00:00Z' }),
    makeTask({ publicId: 'b', title: '乙', priority: 'MEDIUM', updatedAt: '2026-10-26T09:00:00Z' })
  ]
  // 按拼音排序：丙(bing) < 甲(jia) < 乙(yi)
  assert.deepEqual(sortTasks(tasks, 'title', 'asc').map(item => item.publicId), ['c', 'a', 'b'])
  assert.deepEqual(sortTasks(tasks, 'priority', 'asc').map(item => item.publicId), ['a', 'b', 'c'])
  assert.deepEqual(sortTasks(tasks, 'updatedAt', 'desc').map(item => item.publicId), ['a', 'b', 'c'])
})

test('groupTasks 按清单分组并保持清单顺序与图标', () => {
  const tasks = [
    makeTask({ publicId: 'a', listId: 'l-life' }),
    makeTask({ publicId: 'b', listId: 'l-work' })
  ]
  const groups = groupTasks(tasks, 'list', { lists, tags, now: NOW })
  assert.deepEqual(groups.map(item => item.label), ['工作', '生活'])
  assert.equal(groups[0].icon, 'briefcase')
  assert.deepEqual(groups[1].tasks.map(item => item.publicId), ['a'])
})

test('groupTasks 按时间分组时顺序固定为 已过期→今天→明天→本周→以后→无日期', () => {
  const tasks = [
    makeTask({ publicId: 'none' }),
    makeTask({ publicId: 'later', dueAt: '2026-12-01T09:00:00' }),
    makeTask({ publicId: 'today', dueAt: '2026-10-29T18:00:00' }),
    makeTask({ publicId: 'overdue', dueAt: '2026-10-20T18:00:00' })
  ]
  const groups = groupTasks(tasks, 'dueAt', { lists, tags, now: NOW })
  assert.deepEqual(groups.map(item => item.label), ['已过期', '今天', '以后', '无日期'])
})

test('groupTasks 按标签分组时无标签单独成组', () => {
  const tasks = [
    makeTask({ publicId: 'a', tagIds: ['t-study'] }),
    makeTask({ publicId: 'b' })
  ]
  const groups = groupTasks(tasks, 'tag', { lists, tags, now: NOW })
  assert.deepEqual(groups.map(item => item.label).sort(), ['学习', '无标签'].sort())
})

test('groupTasks 的 none 模式只返回一组', () => {
  const groups = groupTasks([makeTask({}), makeTask({ publicId: 'b' })], 'none', { lists, tags, now: NOW })
  assert.equal(groups.length, 1)
  assert.equal(groups[0].tasks.length, 2)
})

test('buildQuadrantGroups 先排序再分组', () => {
  const quadrant = { key: 'IMPORTANT_URGENT', groupBy: 'list', sortBy: 'priority', order: 'asc' }
  const groups = buildQuadrantGroups([
    makeTask({ publicId: 'low', priority: 'LOW' }),
    makeTask({ publicId: 'high', priority: 'HIGH' })
  ], quadrant, { lists, tags, now: NOW })
  assert.deepEqual(groups[0].tasks.map(item => item.publicId), ['high', 'low'])
})

test('summarizeRule / describeRule 生成弹窗摘要与规则示例', () => {
  const rule = { listIds: ['l-work', 'l-life'], tagIds: [], dates: ['OVERDUE', 'TODAY'], priorities: ['HIGH'], itemKind: 'ALL' }
  const summary = summarizeRule(rule, { lists, tags })
  assert.equal(summary.list, '工作 和其他1个清单')
  assert.equal(summary.tag, '所有')
  assert.equal(summary.date, '已过期,今天')
  assert.equal(summary.priority, '高')
  assert.equal(describeRule(rule, { lists, tags }), '清单为 工作 和其他1个清单，日期为 已过期,今天，优先级为 高')
  assert.equal(describeRule({ listIds: [], tagIds: [], dates: [], priorities: [], itemKind: 'ALL' }, { lists, tags }), '全部未完成任务')
})

test('normalizeState 丢弃脏数据并补回默认值', () => {
  const state = normalizeState({
    quadrants: [
      { key: 'IMPORTANT_URGENT', name: '我的第一格', groupBy: 'tag', sortBy: 'bogus', order: 'desc', rule: { dates: ['NOPE'], priorities: ['HIGH'] } }
    ],
    showCompleted: 1,
    collapsed: { 'IMPORTANT_URGENT:l-work': true }
  })
  assert.equal(state.quadrants.length, 4)
  assert.equal(state.quadrants[0].name, '我的第一格')
  assert.equal(state.quadrants[0].groupBy, 'tag')
  assert.equal(state.quadrants[0].sortBy, 'dueAt', '非法排序回退默认')
  assert.equal(state.quadrants[0].order, 'desc')
  assert.deepEqual(state.quadrants[0].rule.dates, [], '非法日期被过滤')
  assert.deepEqual(state.quadrants[0].rule.priorities, ['HIGH'])
  assert.equal(state.showCompleted, true)
  assert.equal(state.collapsed['IMPORTANT_URGENT:l-work'], true)
  assert.equal(normalizeState(null).quadrants.length, 4)
})
