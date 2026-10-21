import assert from 'node:assert/strict'
import test from 'node:test'
import { moveItem } from './taskSidebar.js'

test('moveItem reorders items and normalizes sort order', () => {
  const items = [{ publicId: 'a', sortOrder: 0 }, { publicId: 'b', sortOrder: 1 }, { publicId: 'c', sortOrder: 2 }]

  assert.deepEqual(moveItem(items, 'c', 'a'), [
    { publicId: 'c', sortOrder: 0 },
    { publicId: 'a', sortOrder: 1 },
    { publicId: 'b', sortOrder: 2 },
  ])
  assert.deepEqual(items.map(item => item.publicId), ['a', 'b', 'c'])
})

test('moveItem leaves the list unchanged when either item is missing', () => {
  const items = [{ publicId: 'a', sortOrder: 0 }]
  assert.equal(moveItem(items, 'missing', 'a'), items)
  assert.equal(moveItem(items, 'a', 'missing'), items)
})

test('moveItem supports before and after drop positions', () => {
  const items = [{ publicId: 'a' }, { publicId: 'b' }, { publicId: 'c' }]
  assert.deepEqual(moveItem(items, 'a', 'b', 'after').map(item => item.publicId), ['b', 'a', 'c'])
  assert.deepEqual(moveItem(items, 'c', 'a', 'after').map(item => item.publicId), ['a', 'c', 'b'])
})
