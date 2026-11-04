import test from 'node:test'
import assert from 'node:assert/strict'
import { focusTargetId, focusTargetLabel, isFocusLinked } from './focusTarget.js'

test('关联任务的记录展示任务标题', () => {
  const session = { taskId: 't1', taskTitle: '写周报', habitId: null, habitTitle: null }
  assert.equal(focusTargetLabel(session), '写周报')
  assert.equal(focusTargetId(session), 't1')
  assert.equal(isFocusLinked(session), true)
})

test('关联习惯的记录展示习惯名，不与任务标题冲突', () => {
  const session = { taskId: null, taskTitle: null, habitId: 'h1', habitTitle: '运动' }
  assert.equal(focusTargetLabel(session), '运动')
  assert.equal(focusTargetId(session), 'h1')
  assert.equal(isFocusLinked(session), true)
})

test('没关联的记录统一显示未关联，且不算已关联', () => {
  const session = { taskId: null, taskTitle: null, habitId: null, habitTitle: null }
  assert.equal(focusTargetLabel(session), '未关联任务')
  assert.equal(focusTargetId(session), '')
  assert.equal(isFocusLinked(session), false)
  assert.equal(focusTargetLabel(null), '未关联任务')
})

test('进行中的专注没选目标时，空标签交给调用方兜底', () => {
  assert.equal(focusTargetLabel(null, ''), '')
  assert.equal(focusTargetLabel({ taskTitle: '' }, ''), '')
})
