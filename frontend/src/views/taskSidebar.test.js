import assert from 'node:assert/strict'
import test from 'node:test'
import { insertListDropPlaceholder, moveItem, reorderListDrop } from './taskSidebar.js'

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

test('list drop preview inserts a stable placeholder before or after the target', () => {
  const items = [{ publicId: 'a' }, { publicId: 'b' }, { publicId: 'c' }]

  assert.deepEqual(insertListDropPlaceholder(items, { publicId: 'dragged', name: 'Dragged' }, 'b').map(item => item.publicId), [
    'a', '__drop-preview__:dragged', 'b', 'c'
  ])
  assert.deepEqual(insertListDropPlaceholder(items, { publicId: 'dragged', name: 'Dragged' }, 'b', 'after').map(item => item.publicId), [
    'a', 'b', '__drop-preview__:dragged', 'c'
  ])
  assert.deepEqual(items.map(item => item.publicId), ['a', 'b', 'c'])
})

test('list drop preview and persisted order keep destination siblings stable', () => {
  const target = [{ publicId: 'a' }, { publicId: 'b' }, { publicId: 'c' }]
  const source = { publicId: 'source', groupId: 'folder-a' }

  assert.deepEqual(reorderListDrop(target, source, 'b', 'after').map(item => [item.publicId, item.sortOrder]), [
    ['a', 0], ['b', 1], ['source', 2], ['c', 3]
  ])
  assert.deepEqual(target.map(item => item.publicId), ['a', 'b', 'c'])
})
