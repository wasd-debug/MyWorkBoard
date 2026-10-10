import assert from 'node:assert/strict'
import test from 'node:test'

import { navigationItemIsActive, taskNavigation } from './moduleNavigation.js'

test('task top navigation exposes only the four primary sections', () => {
  assert.deepEqual(taskNavigation.map(item => item.label), ['任务', '习惯', '日历', '倒数日'])
  assert.deepEqual(taskNavigation.map(item => item.to), ['/tasks/today', '/tasks/habits', '/tasks/calendar', '/tasks/countdowns'])
})

test('task primary section stays active for nested task views', () => {
  const task = taskNavigation[0]
  for (const path of ['/tasks/inbox', '/tasks/today', '/tasks/next7', '/tasks/focus', '/tasks/all', '/tasks/completed', '/tasks/inbox-notify', '/tasks/trash', '/tasks/list/list-1', '/tasks/tag/tag-1']) {
    assert.equal(navigationItemIsActive({ path, query: {} }, task), true, path)
  }
  for (const path of ['/tasks/habits', '/tasks/calendar', '/tasks/countdowns']) {
    assert.equal(navigationItemIsActive({ path, query: {} }, task), false, path)
  }
})
