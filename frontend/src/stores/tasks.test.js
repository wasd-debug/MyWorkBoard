import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { useTasksStore } from './tasks.js'

function offline() {
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { onLine: false } })
}

test('offline task lifecycle updates local projection and one compacted operation', async () => {
  offline()
  setActivePinia(createPinia())
  const store = useTasksStore()
  await store.switchUser({ id: 'offline-lifecycle' })
  await store.init()

  const created = await store.create({ title: '离线任务', description: '' })
  assert.equal(store.tasks.length, 1)
  assert.equal(store.pending, 1)
  await store.update(created, { title: '离线任务已编辑' })
  assert.equal(store.tasks[0].title, '离线任务已编辑')
  assert.equal(store.pending, 1)
  await store.complete(store.tasks[0])
  assert.equal(store.completedTasks.length, 1)
  await store.reopen(store.tasks[0])
  assert.equal(store.openTasks.length, 1)
  await store.remove(store.tasks[0])
  assert.equal(store.tasks.length, 0)
  assert.equal(store.pending, 0)
})

test('local hydration restores projection and switching users isolates it', async () => {
  offline()
  setActivePinia(createPinia())
  const store = useTasksStore()
  await store.switchUser({ id: 'task-alice' })
  await store.init()
  await store.create({ title: 'Alice 私有任务' })
  assert.equal(store.tasks.length, 1)
  store.tasks = []
  await store.hydrate()
  assert.equal(store.tasks[0].title, 'Alice 私有任务')

  await store.switchUser({ id: 'task-bob' })
  await store.init()
  assert.equal(store.tasks.length, 0)
  assert.equal(store.pending, 0)
})

test('task getters separate open and completed rows', () => {
  setActivePinia(createPinia())
  const store = useTasksStore()
  store.tasks = [{ publicId: '1', status: 'OPEN' }, { publicId: '2', status: 'COMPLETED' }]
  assert.deepEqual(store.openTasks.map(item => item.publicId), ['1'])
  assert.deepEqual(store.completedTasks.map(item => item.publicId), ['2'])
})

test('task getters retain deleted rows for the trash view', () => {
  setActivePinia(createPinia())
  const store = useTasksStore()
  store.tasks = [{ publicId: 'open', status: 'OPEN', deleted: false },
    { publicId: 'trash', status: 'OPEN', deleted: true }]
  assert.deepEqual(store.openTasks.map(item => item.publicId), ['open'])
  assert.deepEqual(store.trashedTasks.map(item => item.publicId), ['trash'])
})

test('offline lists and tags use distinct projections and oplog entries', async () => {
  offline()
  setActivePinia(createPinia())
  const store = useTasksStore()
  await store.switchUser({ id: 'offline-organization' })
  await store.init()
  await store.createList({ name: '离线清单' })
  await store.createTag({ name: '离线标签' })
  assert.equal(store.lists[0].name, '离线清单')
  assert.equal(store.tags[0].name, '离线标签')
  assert.equal(store.pending, 2)
  const list = store.lists[0]
  await store.updateList(list, { name: '离线清单改名' })
  await store.updateList(store.lists[0], { color: '#123456' })
  await store.updateTag(store.tags[0], { name: '离线标签改名' })
  assert.equal(store.pending, 2)
  assert.equal(store.lists[0].name, '离线清单改名')
  await store.deleteList(list)
  assert.equal(store.lists.length, 0)
  assert.equal(store.pending, 1)
  assert.equal(store.deletedLists.length, 0)
  store.deletedLists = []
  await store.hydrate()
  assert.equal(store.deletedLists.length, 0)
  await store.deleteTag(store.tags[0])
  assert.equal(store.tags.length, 0)
  assert.equal(store.pending, 0)
  await assert.rejects(store.restore({ publicId: 'deleted' }), /需要联网/)
})
