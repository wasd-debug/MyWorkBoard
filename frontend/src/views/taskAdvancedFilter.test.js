import test from 'node:test'
import assert from 'node:assert/strict'
import { advancedFilterConditionsFromRules, advancedFilterConditionsValid, buildAdvancedFilterRules, newAdvancedFilterCondition } from './taskAdvancedFilter.js'
import { matchesFilterRules, serializeFilterRules } from './taskFilterRules.js'

test('advanced picker exposes each criterion once and prevents a second list criterion', () => {
  const conditions = [newAdvancedFilterCondition('LIST')]
  const rules = buildAdvancedFilterRules(conditions, [])
  assert.deepEqual(rules, { op: 'AND', children: [] })
  assert.equal(advancedFilterConditionsValid(conditions, []), false)
})

test('advanced conditions compile AND/OR joins and exclude selected lists', () => {
  const conditions = [
    { ...newAdvancedFilterCondition('LIST'), logic: 'NOT', listIds: ['l1', 'l2'] },
    { ...newAdvancedFilterCondition('TAG'), tagIds: ['t1', 't2'] },
    { ...newAdvancedFilterCondition('TASK_TYPE'), taskTypes: ['NOTE'] },
  ]
  const rules = buildAdvancedFilterRules(conditions, ['AND', 'OR'])
  assert.deepEqual(serializeFilterRules(rules), { op: 'OR', children: [
    { op: 'AND', children: [
      { op: 'AND', children: [{ field: 'listId', operator: 'NE', value: 'l1' }, { field: 'listId', operator: 'NE', value: 'l2' }] },
      { op: 'AND', children: [{ field: 'tagId', operator: 'EQ', value: 't1' }, { field: 'tagId', operator: 'EQ', value: 't2' }] },
    ] },
    { field: 'taskType', operator: 'IN', value: ['NOTE'] },
  ] })
  assert.equal(matchesFilterRules({ listId: 'l3', tagIds: ['t1', 't2'], objectType: 'TASK' }, rules), true)
  assert.equal(matchesFilterRules({ listId: 'l1', tagIds: ['t1', 't2'], objectType: 'TASK' }, rules), false)
})

test('advanced field criteria support inclusive and excluded date ranges, priority and content', () => {
  const date = { ...newAdvancedFilterCondition('DATE'), logic: 'NOT', start: '2026-10-20', end: '2026-10-25' }
  const excludedDates = buildAdvancedFilterRules([date], [])
  assert.equal(matchesFilterRules({ dueAt: null }, excludedDates), true)
  assert.equal(matchesFilterRules({ dueAt: '2026-10-22T09:00:00+08:00' }, excludedDates), false)
  const conditions = [
    { ...newAdvancedFilterCondition('PRIORITY'), priorities: ['HIGH', 'MEDIUM'] },
    { ...newAdvancedFilterCondition('CONTENT'), keyword: 'budget' },
  ]
  const rules = buildAdvancedFilterRules(conditions, ['AND'])
  assert.equal(matchesFilterRules({ priority: 'HIGH', title: 'Budget review' }, rules), true)
  assert.equal(matchesFilterRules({ priority: 'LOW', title: 'Budget review' }, rules), false)
  assert.equal(advancedFilterConditionsValid(conditions, ['AND']), true)
})

test('converts recognized legacy advanced rules and preserves unknown subtrees intact', () => {
  const legacy = { op: 'AND', children: [
    { field: 'listId', operator: 'IN', value: ['l1', 'l2'] },
    { field: 'status', operator: 'EQ', value: 'OPEN' },
  ] }
  const converted = advancedFilterConditionsFromRules(legacy)
  const output = buildAdvancedFilterRules(converted.conditions, converted.joins)
  assert.match(JSON.stringify(serializeFilterRules(output)), /"field":"status"/)
  for (const task of [{ listId: 'l1', status: 'OPEN' }, { listId: 'l3', status: 'OPEN' }, { listId: 'l1', status: 'COMPLETED' }]) {
    assert.equal(matchesFilterRules(task, output), matchesFilterRules(task, legacy))
  }
  assert.equal(converted.conditions[1].type, 'LEGACY')
})
