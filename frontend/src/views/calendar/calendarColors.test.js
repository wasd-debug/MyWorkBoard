import test from 'node:test'
import assert from 'node:assert/strict'
import {
  DEFAULT_TASK_COLOR, THEME_COLOR, normalizeColor, resolveCountdownColor, resolveHabitColor, resolveTaskColor, mergedColorSettings
} from './calendarColors.js'

const lists = [{ publicId: 'l1', color: '#6bcb77' }, { publicId: 'l2', color: '' }]
const tags = [{ publicId: 't1', color: '#f5c33b' }, { publicId: 't2', color: '不是颜色' }]

test('normalizeColor 只接受合法颜色值', () => {
  assert.equal(normalizeColor('#fff'), '#fff')
  assert.equal(normalizeColor('rgb(1,2,3)'), 'rgb(1,2,3)')
  assert.equal(normalizeColor('var(--accent)'), 'var(--accent)')
  assert.equal(normalizeColor('red'), DEFAULT_TASK_COLOR)
  assert.equal(normalizeColor('', '#123456'), '#123456')
  assert.equal(normalizeColor(null), DEFAULT_TASK_COLOR)
})

test('按清单颜色取色并回退默认色', () => {
  assert.equal(resolveTaskColor({ listId: 'l1' }, { mode: 'list', lists, tags }), '#6bcb77')
  assert.equal(resolveTaskColor({ listId: 'l2' }, { mode: 'list', lists, tags }), DEFAULT_TASK_COLOR)
  assert.equal(resolveTaskColor({ listId: 'unknown' }, { mode: 'list', lists, tags }), DEFAULT_TASK_COLOR)
})

test('按标签颜色取第一个合法标签色', () => {
  assert.equal(resolveTaskColor({ tagIds: ['t2', 't1'] }, { mode: 'tag', lists, tags }), '#f5c33b')
  assert.equal(resolveTaskColor({ tagIds: [] }, { mode: 'tag', lists, tags }), DEFAULT_TASK_COLOR)
})

test('按优先级颜色映射四档', () => {
  assert.equal(resolveTaskColor({ priority: 'HIGH' }, { mode: 'priority' }), '#e34d59')
  assert.equal(resolveTaskColor({ priority: 'MEDIUM' }, { mode: 'priority' }), '#e69b2d')
  assert.equal(resolveTaskColor({ priority: 'LOW' }, { mode: 'priority' }), '#4d78db')
  assert.equal(resolveTaskColor({ priority: 'NONE' }, { mode: 'priority' }), '#8b92a3')
  assert.equal(resolveTaskColor({}, { mode: 'priority' }), '#8b92a3')
})

test('习惯颜色三种模式', () => {
  assert.equal(resolveHabitColor({ color: '#123456' }, { habitColorMode: 'theme' }), THEME_COLOR)
  assert.equal(resolveHabitColor({ color: '#123456' }, { habitColorMode: 'habit' }), '#123456')
  assert.equal(resolveHabitColor({}, { habitColorMode: 'fixed', habitFixedColor: '#abcdef' }), '#abcdef')
})

test('倒数日颜色两种模式', () => {
  assert.equal(resolveCountdownColor({ color: '#123456' }, { countdownColorMode: 'countdown' }), '#123456')
  assert.equal(resolveCountdownColor({ color: '#123456' }, { countdownColorMode: 'fixed', countdownFixedColor: '#fedcba' }), '#fedcba')
})

test('mergedColorSettings 补齐缺省项', () => {
  const merged = mergedColorSettings({ taskColorMode: 'tag' })
  assert.equal(merged.taskColorMode, 'tag')
  assert.equal(merged.habitColorMode, 'theme')
  assert.equal(merged.countdownColorMode, 'fixed')
})
