import assert from 'node:assert/strict'
import test from 'node:test'
import { getListDropPlacement, moveItem, reorderListDrop } from './taskSidebar.js'

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

test('list drop allocates a sparse sort order without changing destination siblings', () => {
  const target = [{ publicId: 'a', sortOrder: 1024 }, { publicId: 'b', sortOrder: 2048 }, { publicId: 'c', sortOrder: 3072 }]
  const source = { publicId: 'source', groupId: 'folder-a' }

  assert.deepEqual(reorderListDrop(target, source, 'b', 'after').map(item => [item.publicId, item.sortOrder]), [
    ['a', 1024], ['b', 2048], ['source', 2560], ['c', 3072]
  ])
  assert.deepEqual(target.map(item => item.publicId), ['a', 'b', 'c'])
})

test('list drop chooses a midpoint between existing ranks and rebalances only when the gap is exhausted', () => {
  const spaced = [{ publicId: 'a', sortOrder: 1024 }, { publicId: 'b', sortOrder: 2048 }]
  const tight = [{ publicId: 'a', sortOrder: 1024 }, { publicId: 'b', sortOrder: 1025 }]

  assert.deepEqual(reorderListDrop(spaced, { publicId: 'source' }, 'b').map(item => item.sortOrder), [1024, 1536, 2048])
  assert.deepEqual(reorderListDrop(tight, { publicId: 'source' }, 'b').map(item => item.sortOrder), [1024, 2048, 3072])
})

test('list drop uses wider row edges for ordering and the center for folder merge', () => {
  const rect = { top: 100, height: 40 }

  assert.equal(getListDropPlacement(113, rect), 'before')
  assert.equal(getListDropPlacement(114, rect), 'merge')
  assert.equal(getListDropPlacement(126, rect), 'merge')
  assert.equal(getListDropPlacement(127, rect), 'after')
})
