import test from 'node:test'
import assert from 'node:assert/strict'
import { matchesFilterRules, normalizeFilterRules, serializeFilterRules } from './taskFilterRules.js'

test('normalizes and preserves nested AND/OR rule groups without editor ids in API payload', () => {
  const rules = normalizeFilterRules({ op: 'AND', children: [
    { field: 'status', operator: 'EQ', value: 'OPEN' },
    { op: 'OR', children: [{ field: 'priority', operator: 'EQ', value: 'HIGH' }, { field: 'title', operator: 'CONTAINS', value: 'urgent' }] },
  ] })
  const serialized = serializeFilterRules(rules)
  assert.deepEqual(serialized, { op: 'AND', children: [
    { field: 'status', operator: 'EQ', value: 'OPEN' },
    { op: 'OR', children: [{ field: 'priority', operator: 'EQ', value: 'HIGH' }, { field: 'title', operator: 'CONTAINS', value: 'urgent' }] },
  ] })
})

test('rejects unsupported field/operator pairs and over-depth rules', () => {
  assert.throws(() => serializeFilterRules(normalizeFilterRules({ op: 'AND', children: [{ field: 'status', operator: 'CONTAINS', value: 'OPEN' }] })), /操作符/)
  let tree = { field: 'title', operator: 'EQ', value: 'x' }
  for (let index = 0; index < 6; index += 1) tree = { op: 'AND', children: [tree] }
  assert.throws(() => serializeFilterRules(normalizeFilterRules(tree)), /5 层/)
})

test('evaluates nested rules using AND/OR semantics and task fields', () => {
  const task = { title: 'Quarterly review', description: 'draft', status: 'OPEN', priority: 'HIGH', listId: 'l1', tagIds: ['t1'], dueAt: '2026-10-21T09:00:00+08:00', parentId: null }
  const rules = normalizeFilterRules({ op: 'AND', children: [
    { field: 'status', operator: 'EQ', value: 'OPEN' },
    { op: 'OR', children: [{ field: 'priority', operator: 'EQ', value: 'LOW' }, { field: 'tagId', operator: 'EQ', value: 't1' }] },
  ] })
  assert.equal(matchesFilterRules(task, rules), true)
  assert.equal(matchesFilterRules({ ...task, tagIds: [] }, rules), false)
})

test('supports task type as an advanced rule and rejects unsupported operators', () => {
  const rule = normalizeFilterRules({ op: 'AND', children: [{ field: 'taskType', operator: 'EQ', value: 'NOTE' }] })
  assert.equal(matchesFilterRules({ title: 'Memo', objectType: 'NOTE' }, rule), true)
  assert.equal(matchesFilterRules({ title: 'Task', objectType: 'TASK' }, rule), false)
  assert.throws(() => serializeFilterRules(normalizeFilterRules({ op: 'AND', children: [{ field: 'taskType', operator: 'CONTAINS', value: 'NOTE' }] })), /操作符/)
})
