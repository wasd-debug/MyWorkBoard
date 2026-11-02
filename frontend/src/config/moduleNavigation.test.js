import assert from 'node:assert/strict'
import test from 'node:test'

import { navigationItemIsActive, taskNavigation } from './moduleNavigation.js'

test('task top navigation exposes all planned sections in product order', () => {
  // 左侧图标栏保持这个相对顺序；看板仍是任务视图入口，排在倒数纪念日之后。
  // AI、搜索、设置三个入口都已下线：AI 走悬浮助手，搜索是弹窗，设置页整页删除
  assert.deepEqual(taskNavigation.map(item => item.label), ['任务', '日历', '四象限', '专注', '习惯', '倒数纪念日', '看板', '统计与回顾'])
  assert.deepEqual(taskNavigation.map(item => item.to), ['/tasks/today', '/tasks/calendar', '/tasks/quadrant', '/tasks/focus', '/tasks/habits', '/tasks/countdowns', '/tasks/today', '/tasks/stats'])
  assert.equal(taskNavigation.find(item => item.key === 'board').view, 'KANBAN')
  assert.equal(taskNavigation.some(item => ['agent', 'search', 'settings'].includes(item.key)), false)
})

test('task primary section stays active for nested task views', () => {
  const task = taskNavigation[0]
  for (const path of ['/tasks/inbox', '/tasks/today', '/tasks/tomorrow', '/tasks/next7', '/tasks/all', '/tasks/completed', '/tasks/inbox-notify', '/tasks/trash', '/tasks/list/list-1', '/tasks/tag/tag-1']) {
    assert.equal(navigationItemIsActive({ path, query: {} }, task), true, path)
  }
  for (const path of ['/tasks/habits', '/tasks/calendar', '/tasks/focus', '/tasks/countdowns', '/tasks/board', '/tasks/quadrant', '/tasks/search', '/tasks/stats']) {
    assert.equal(navigationItemIsActive({ path, query: {} }, task), false, path)
  }
})
