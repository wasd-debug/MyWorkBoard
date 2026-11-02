import assert from 'node:assert/strict'
import test from 'node:test'

import {
  countdownDays,
  countdownVisible,
  dateRangeFor,
  dateRangeLabel,
  emptySearchCriteria,
  filterSearchResults,
  highlightParts,
  matchCountdown,
  matchHabit,
  matchOrganizations,
  matchTask,
  searchCriteriaFilterCount,
  searchCriteriaFromQuery,
  searchCriteriaToQuery,
  searchResultTotal,
  searchTokens
} from './taskSearch.js'

const TODAY = '2026-11-02' // 周一

const lists = [
  { publicId: 'list-work', name: '工作', color: '#2563eb', objectType: 'TASK' },
  { publicId: 'list-note', name: '笔记', objectType: 'NOTE' },
  { publicId: 'list-fun', name: '娱乐', objectType: 'TASK' }
]

const tags = [
  { publicId: 'tag-a', name: '重要' },
  { publicId: 'tag-b', name: '阅读' }
]

const tasks = [
  { publicId: 't1', title: '写周报', description: '总结本周进展', listId: 'list-work', status: 'OPEN', priority: 'HIGH', dueAt: '2026-11-02T10:00:00.000+08:00', tagIds: ['tag-a'], checklist: [{ title: '整理数据', completed: false }] },
  { publicId: 't2', title: '读书笔记', description: '', listId: 'list-note', itemKind: 'NOTE', status: 'OPEN', priority: 'NONE', dueAt: '', tagIds: [] },
  { publicId: 't3', title: '看电影', description: '周末放松', listId: 'list-fun', status: 'COMPLETED', priority: 'LOW', dueAt: '2026-10-30T20:00:00.000+08:00', tagIds: ['tag-b'] },
  { publicId: 't4', title: '写月报', description: '月末总结', listId: 'list-work', status: 'OPEN', priority: 'MEDIUM', dueAt: '2026-11-20T10:00:00.000+08:00', tagIds: [] }
]

const habits = [
  { publicId: 'h1', name: '晨跑', icon: '🏃', color: '#7fc3a8' },
  { publicId: 'h2', name: '读书 30 分钟', icon: '📖', color: '#6fb3e0' }
]

const countdowns = [
  { publicId: 'c1', name: '距离元旦', targetDate: '2027-01-01', displayRule: 'ALWAYS' },
  { publicId: 'c2', name: '距离本月结束', targetDate: '2026-11-30', displayRule: 'SMART' },
  { publicId: 'c3', name: '距离周末', targetDate: '2026-11-06', displayRule: 'D3' },
  { publicId: 'c4', name: '隐藏的纪念日', targetDate: '2026-11-02', displayRule: 'NEVER' }
]

function context() {
  return {
    today: TODAY,
    listsById: new Map(lists.map(item => [item.publicId, item])),
    tagsById: new Map(tags.map(item => [item.publicId, item]))
  }
}

test('日期下拉：本周/下周/上周/本月/上月/自定义 都按周一开始算', () => {
  assert.deepEqual(dateRangeFor('THIS_WEEK', TODAY), { start: '2026-11-02', end: '2026-11-08' })
  assert.deepEqual(dateRangeFor('NEXT_WEEK', TODAY), { start: '2026-11-09', end: '2026-11-15' })
  assert.deepEqual(dateRangeFor('LAST_WEEK', TODAY), { start: '2026-10-26', end: '2026-11-01' })
  assert.deepEqual(dateRangeFor('THIS_MONTH', TODAY), { start: '2026-11-01', end: '2026-11-30' })
  assert.deepEqual(dateRangeFor('LAST_MONTH', TODAY), { start: '2026-10-01', end: '2026-10-31' })
  assert.deepEqual(dateRangeFor('CUSTOM', TODAY, '2026-11-03', ''), { start: '2026-11-03', end: '2026-11-03' })
  assert.equal(dateRangeFor('ALL', TODAY), null)
  assert.equal(dateRangeFor('CUSTOM', TODAY, '', ''), null)
})

test('日期区间文案带具体日期，和参考图一致', () => {
  assert.equal(dateRangeLabel('THIS_WEEK', TODAY), '本周 (11月2日-11月8日)')
  assert.equal(dateRangeLabel('ALL', TODAY), '所有日期')
})

test('条件与地址栏互转：默认值不落地址，非法值回落', () => {
  const criteria = emptySearchCriteria({ q: '周报', date: 'THIS_WEEK', listIds: ['list-work'], status: 'OPEN' })
  assert.deepEqual(searchCriteriaToQuery(criteria), { q: '周报', date: 'THIS_WEEK', lists: 'list-work', status: 'OPEN' })
  assert.deepEqual(searchCriteriaFromQuery({ q: '周报', date: 'THIS_WEEK', lists: 'list-work', status: 'OPEN' }), criteria)
  const fallback = searchCriteriaFromQuery({ date: '乱写', status: '??', priority: 'xx', type: 'yy' })
  assert.equal(fallback.date, 'ALL')
  assert.equal(fallback.status, 'ALL')
  assert.equal(fallback.priority, 'ALL')
  assert.equal(fallback.type, 'ALL')
  // 自定义区间只在 CUSTOM 时写地址，避免无意义参数
  assert.deepEqual(searchCriteriaToQuery({ date: 'ALL', start: '2026-11-01', end: '2026-11-03' }), {})
})

test('条件计数不含关键词', () => {
  assert.equal(searchCriteriaFilterCount({ q: '周报' }), 0)
  assert.equal(searchCriteriaFilterCount({ q: '周报', date: 'THIS_WEEK', status: 'OPEN' }), 2)
})

test('关键词按空格拆词，且必须全部命中', () => {
  assert.deepEqual(searchTokens('  写  周报 '), ['写', '周报'])
  assert.equal(matchTask(tasks[0], { q: '周报' }, context()), true)
  assert.equal(matchTask(tasks[0], { q: '写 周报' }, context()), true)
  assert.equal(matchTask(tasks[0], { q: '写 月报' }, context()), false)
  // 描述、检查项、标签名、清单名都能被搜到
  assert.equal(matchTask(tasks[0], { q: '本周进展' }, context()), true)
  assert.equal(matchTask(tasks[0], { q: '整理数据' }, context()), true)
  assert.equal(matchTask(tasks[0], { q: '重要' }, context()), true)
  assert.equal(matchTask(tasks[0], { q: '工作' }, context()), true)
})

test('任务筛选：状态/优先级/清单/标签/日期/类型', () => {
  assert.equal(matchTask(tasks[2], { status: 'COMPLETED' }, context()), true)
  assert.equal(matchTask(tasks[2], { status: 'OPEN' }, context()), false)
  assert.equal(matchTask(tasks[0], { priority: 'HIGH' }, context()), true)
  assert.equal(matchTask(tasks[0], { priority: 'LOW' }, context()), false)
  assert.equal(matchTask(tasks[0], { listIds: ['list-work'] }, context()), true)
  assert.equal(matchTask(tasks[0], { listIds: ['list-fun'] }, context()), false)
  assert.equal(matchTask(tasks[0], { tagIds: ['tag-b'] }, context()), false)
  assert.equal(matchTask(tasks[0], { tagIds: ['tag-a'] }, context()), true)
  assert.equal(matchTask(tasks[0], { date: 'THIS_WEEK' }, context()), true)
  assert.equal(matchTask(tasks[0], { date: 'LAST_WEEK' }, context()), false)
  assert.equal(matchTask(tasks[1], { type: 'NOTE' }, context()), true)
  assert.equal(matchTask(tasks[1], { type: 'TASK' }, context()), false)
  assert.equal(matchTask(tasks[0], { type: 'HABIT' }, context()), false)
  // 无截止日的任务不落进任何日期区间
  assert.equal(matchTask(tasks[1], { date: 'THIS_MONTH' }, context()), false)
})

test('习惯只在不设标签/清单/日期/优先级条件时出现，且类型要匹配', () => {
  assert.equal(matchHabit(habits[0], { q: '晨跑' }, context()), true)
  assert.equal(matchHabit(habits[0], { q: '跑' }, context()), true)
  assert.equal(matchHabit(habits[0], { q: '读书' }, context()), false)
  assert.equal(matchHabit(habits[0], { q: '晨跑', type: 'HABIT' }, context()), true)
  assert.equal(matchHabit(habits[0], { q: '晨跑', type: 'TASK' }, context()), false)
  assert.equal(matchHabit(habits[0], { q: '晨跑', tagIds: ['tag-a'] }, context()), false)
  assert.equal(matchHabit(habits[0], { q: '晨跑', date: 'THIS_WEEK' }, context()), false)
  assert.equal(matchHabit(habits[0], { q: '晨跑', status: 'COMPLETED' }, context()), false)
})

test('倒数日：显式搜索能搜到，NEVER 的类型永远不出现', () => {
  assert.equal(matchCountdown(countdowns[0], { q: '元旦' }, context()), true)
  assert.equal(matchCountdown(countdowns[3], { q: '隐藏' }, context()), false)
  assert.equal(matchCountdown(countdowns[0], { q: '元旦', type: 'COUNTDOWN' }, context()), true)
  assert.equal(matchCountdown(countdowns[0], { q: '元旦', type: 'TASK' }, context()), false)
  assert.equal(matchCountdown(countdowns[0], { q: '元旦', date: 'THIS_WEEK' }, context()), false)
})

test('倒数日显示规则真正生效：SMART 当天 / D3 / D7 / ALWAYS / NEVER', () => {
  assert.equal(countdownDays(countdowns[1], TODAY), 28)
  assert.equal(countdownVisible({ ...countdowns[1], targetDate: TODAY }, TODAY), true)
  assert.equal(countdownVisible(countdowns[1], TODAY), false)
  assert.equal(countdownVisible({ ...countdowns[2], targetDate: '2026-11-04' }, TODAY), true) // 提前 2 天，D3 可见
  assert.equal(countdownVisible({ ...countdowns[2], targetDate: '2026-11-09' }, TODAY), false) // 提前 7 天，D3 不可见
  assert.equal(countdownVisible({ ...countdowns[2], targetDate: '2026-11-09', displayRule: 'D7' }, TODAY), true)
  assert.equal(countdownVisible(countdowns[0], TODAY), true)
  assert.equal(countdownVisible(countdowns[3], TODAY), false)
  assert.equal(countdownVisible({ ...countdowns[0], archived: true }, TODAY), false)
  assert.equal(countdownVisible({ ...countdowns[1], targetDate: '2026-10-30', displayRule: 'SMART' }, TODAY), false)
})

test('结果装配：类型为任务时不带习惯与倒数日；总数三部分相加', () => {
  const all = filterSearchResults({ tasks, habits, countdowns, lists, tags, criteria: { q: '写' }, today: TODAY })
  assert.deepEqual(all.tasks.map(item => item.publicId), ['t1', 't4'])
  assert.deepEqual(all.habits, [])
  assert.deepEqual(all.countdowns, [])
  assert.equal(searchResultTotal(all), 2)

  const habitOnly = filterSearchResults({ tasks, habits, countdowns, lists, tags, criteria: { q: '读书' }, today: TODAY })
  assert.deepEqual(habitOnly.tasks.map(item => item.publicId), ['t2']) // 笔记标题命中
  assert.deepEqual(habitOnly.habits.map(item => item.publicId), ['h2'])

  const countdownOnly = filterSearchResults({ tasks, habits, countdowns, lists, tags, criteria: { type: 'COUNTDOWN' }, today: TODAY })
  assert.deepEqual(countdownOnly.countdowns.map(item => item.publicId), ['c1', 'c2', 'c3'])
})

test('标签/清单/过滤器按名称分词匹配', () => {
  assert.deepEqual(matchOrganizations(tags, '读').map(item => item.publicId), ['tag-b'])
  assert.deepEqual(matchOrganizations(lists, '工').map(item => item.publicId), ['list-work'])
  assert.deepEqual(matchOrganizations(tags, '   '), [])
})

test('高亮只切第一个命中词，大小写不敏感', () => {
  assert.deepEqual(highlightParts('Write 周报 today', '周报'), [{ text: 'Write ', hit: false }, { text: '周报', hit: true }, { text: ' today', hit: false }])
  assert.deepEqual(highlightParts('abc', ''), [{ text: 'abc', hit: false }])
  assert.deepEqual(highlightParts('ABC', 'b'), [{ text: 'A', hit: false }, { text: 'B', hit: true }, { text: 'C', hit: false }])
})
