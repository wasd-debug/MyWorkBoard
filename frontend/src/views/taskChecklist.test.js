import test from 'node:test'
import assert from 'node:assert/strict'
import { moveChecklistItem } from './taskChecklist.js'

test('checklist reordering preserves item data and moves rows to the requested index', () => {
  const items = [
    { id: 'a', title: 'First', completed: true },
    { id: 'b', title: 'Second', completed: false },
    { id: 'c', title: 'Third', completed: true },
  ]

  assert.deepEqual(moveChecklistItem(items, 0, 2), [items[1], items[2], items[0]])
  assert.deepEqual(moveChecklistItem(items, 2, 0), [items[2], items[0], items[1]])
  assert.deepEqual(items.map(item => item.id), ['a', 'b', 'c'])
})

test('checklist reordering leaves invalid and same-position moves unchanged', () => {
  const items = [{ id: 'a' }, { id: 'b' }]
  assert.equal(moveChecklistItem(items, -1, 1), items)
  assert.equal(moveChecklistItem(items, 0, 2), items)
  assert.equal(moveChecklistItem(items, 1, 1), items)
})
