import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { api } from '../../packages/api-client/src/index.js'
import { useTasksStore } from './tasks.js'

function offline() {
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { onLine: false } })
}

test('task initialization renders the local projection without waiting for remote refresh', async () => {
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { onLine: true } })
  setActivePinia(createPinia())
  const store = useTasksStore()
  await store.switchUser({ id: 'cached-first' })
  let finishRefresh
  let refreshStarted = false
  store.hydrate = async () => { store.tasks = [{ publicId: 'cached', title: '缓存任务' }] }
  store.refreshServer = () => {
    refreshStarted = true
    return new Promise(resolve => { finishRefresh = resolve })
  }

  await store.init({ waitForRemote: false })

  assert.equal(refreshStarted, true)
  assert.equal(store.ready, true)
  assert.equal(store.loading, false)
  assert.equal(store.tasks[0].title, '缓存任务')
  finishRefresh()
})

test('task changes queued during an active sync are sent in a follow-up batch', async () => {
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { onLine: true } })
  setActivePinia(createPinia())
  const store = useTasksStore()
  await store.switchUser({ id: 'sync-race' })
  const previousAdapter = api.defaults.adapter
  let releaseFirstPull
  let markPullStarted
  const firstPullStarted = new Promise(resolve => { markPullStarted = resolve })
  let pullCount = 0
  const pushedBatches = []
  api.defaults.adapter = async config => {
    if (config.url.includes('/api/v1/tasks/sync/pull')) {
      pullCount += 1
      if (pullCount === 1) {
        markPullStarted()
        await new Promise(resolve => { releaseFirstPull = resolve })
      }
      return { data: { data: { cursor: 0, operations: [], hasMore: false } }, status: 200, statusText: 'OK', headers: {}, config }
    }
    if (config.url.includes('/api/v1/tasks/sync/push')) {
      const operations = JSON.parse(config.data)
      pushedBatches.push(operations)
      return { data: { data: { results: operations.map(operation => ({ opId: operation.opId, status: 'APPLIED', entity: { ...operation.payload, revision: 1 } })) } }, status: 200, statusText: 'OK', headers: {}, config }
    }
    throw new Error(`Unexpected API request ${config.method} ${config.url}`)
  }

  try {
    const firstSync = store.syncNow()
    await firstPullStarted
    await store.createList({ name: '同步竞态清单' })
    await new Promise(resolve => setTimeout(resolve, 300))
    releaseFirstPull()
    await firstSync

    assert.equal(pushedBatches.length, 1)
    assert.equal(pushedBatches[0].length, 1)
    assert.equal(store.pending, 0)
    assert.equal(pullCount, 2)
  } finally {
    releaseFirstPull?.()
    api.defaults.adapter = previousAdapter
    await store.clearSession()
  }
})

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

test('task edit can be undone through the local-first projection', async () => {
  offline()
  setActivePinia(createPinia())
  const store = useTasksStore()
  await store.switchUser({ id: 'task-undo' })
  await store.init()
  const created = await store.create({ title: '撤销前' })
  await store.update(created, { title: '撤销后' })
  assert.equal(store.tasks[0].title, '撤销后')
  assert.equal(await store.undoLast(), true)
  assert.equal(store.tasks[0].title, '撤销前')
})

test('completed recurring task cannot reopen after its next instance exists', async () => {
  setActivePinia(createPinia())
  const store = useTasksStore()
  store.tasks = [
    { publicId: 'current', status: 'COMPLETED', rrule: 'FREQ=DAILY', seriesId: 'series', seriesSequence: 1 },
    { publicId: 'next', status: 'OPEN', rrule: 'FREQ=DAILY', seriesId: 'series', seriesSequence: 2 }
  ]
  await assert.rejects(store.reopen(store.tasks[0]), /下一实例已生成/)
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

test('safe task-list conflicts are cleared and inbox name conflicts preserve the local color', async t => {
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { onLine: false } })
  setActivePinia(createPinia())
  const store = useTasksStore()
  await store.switchUser({ id: 'task-safe-conflicts' })
  await store.init()
  const stale = await store.createList({ name: '已一致清单', color: '#123456', icon: 'list-check' })
  const inbox = await store.createList({ name: '收件箱', systemKey: 'INBOX', color: '#ff8b3d' })
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { onLine: true } })
  store.online = true
  const previousAdapter = api.defaults.adapter
  const pushed = []
  api.defaults.adapter = async config => {
    if (config.url.includes('/api/v1/tasks/sync/push')) {
      const operations = JSON.parse(config.data)
      pushed.push(...operations)
      const results = operations.map(operation => {
        if (operation.entityId === stale.id) return { opId: operation.opId, status: 'CONFLICT', serverRevision: 3, serverEntity: { ...operation.payload, revision: 3 } }
        if (operation.entityId === inbox.id && operation.payload.name === '收件箱') {
          return { opId: operation.opId, status: 'CONFLICT', message: '收集箱不可重命名', serverRevision: 5,
            serverEntity: { ...operation.payload, name: '收集箱', color: null, systemKey: 'INBOX', revision: 5 } }
        }
        return { opId: operation.opId, status: 'APPLIED', entity: { ...operation.payload, revision: 6 } }
      })
      return { data: { data: { results } }, status: 200, statusText: 'OK', headers: {}, config }
    }
    if (config.url.includes('/api/v1/tasks/sync/pull')) {
      return { data: { data: { cursor: 0, operations: [], hasMore: false } }, status: 200, statusText: 'OK', headers: {}, config }
    }
    throw new Error(`Unexpected API request ${config.method} ${config.url}`)
  }
  try {
    await store.syncNow()
    assert.equal(store.conflicts.length, 0)
    assert.equal(store.pending, 0)
    assert.equal(store.lists.find(item => item.publicId === inbox.id).name, '收集箱')
    assert.equal(store.lists.find(item => item.publicId === inbox.id).color, '#ff8b3d')
    assert.ok(pushed.some(operation => operation.entityId === inbox.id && operation.payload.name === '收集箱' && operation.payload.color === '#ff8b3d'))
  } finally {
    api.defaults.adapter = previousAdapter
    await store.clearSession()
  }
})

test('list folder assignment updates the cached view before its background request', async t => {
  offline()
  setActivePinia(createPinia())
  const store = useTasksStore()
  await store.switchUser({ id: 'task-folder-local-first' })
  await store.init()
  const list = await store.createList({ name: '待归组清单' })
  await store.assignListGroup(store.lists[0], 'group-local')
  assert.equal(store.lists[0].publicId, list.id)
  assert.equal(store.lists[0].groupId, 'group-local')
  assert.equal(store.pending, 2)
  assert.equal(store.syncing, false)
  await store.clearSession()
})

test('list reordering and folder assignment hydrate the local projection once', async () => {
  offline()
  setActivePinia(createPinia())
  const store = useTasksStore()
  await store.switchUser({ id: 'task-list-reorder-batch' })
  await store.init()
  const first = await store.createList({ name: '第一张清单' })
  const second = await store.createList({ name: '第二张清单' })
  const third = await store.createList({ name: '第三张清单' })
  let hydrationCount = 0
  const hydrate = store.hydrate.bind(store)
  store.hydrate = async () => { hydrationCount += 1; await hydrate() }

  await store.reorderLists([
    { ...store.lists.find(item => item.publicId === third.id), sortOrder: 0, groupId: 'folder-a' },
    { ...store.lists.find(item => item.publicId === first.id), sortOrder: 1 },
    { ...store.lists.find(item => item.publicId === second.id), sortOrder: 2 }
  ], { listId: third.id, groupId: 'folder-a' })

  assert.equal(hydrationCount, 1)
  assert.equal(store.lists.find(item => item.publicId === third.id).groupId, 'folder-a')
  assert.equal(store.lists.find(item => item.publicId === third.id).sortOrder, 0)
  assert.equal(store.pending, 4)
  await store.clearSession()
})

test('folder assignment retry recognizes a server-side success after a lost response', async () => {
  offline()
  setActivePinia(createPinia())
  const store = useTasksStore()
  await store.switchUser({ id: 'task-folder-retry' })
  await store.init()
  const list = await store.createList({ name: '归属重试清单' })
  await store.assignListGroup(store.lists[0], 'group-already-assigned')
  const previousAdapter = api.defaults.adapter
  api.defaults.adapter = async config => {
    if (config.url.includes('/api/v1/tasks/sync/push')) {
      const operations = JSON.parse(config.data)
      return { data: { data: { results: operations.map(operation => ({ opId: operation.opId, status: 'APPLIED', entity: { ...operation.payload, revision: 1 } })) } }, status: 200, statusText: 'OK', headers: {}, config }
    }
    if (config.url.includes('/api/v1/tasks/sync/pull')) {
      return { data: { data: { cursor: 0, operations: [], hasMore: false } }, status: 200, statusText: 'OK', headers: {}, config }
    }
    if (config.url.endsWith(`/api/v1/tasks/lists/${list.id}/group`)) {
      const error = new Error('Revision conflict after server accepted the assignment')
      error.config = config
      error.response = { status: 409, config, data: { serverRevision: 2 } }
      throw error
    }
    if (config.url.endsWith('/api/v1/tasks/lists')) {
      return { data: { data: [{ publicId: list.id, name: '归属重试清单', groupId: 'group-already-assigned', revision: 2 }] }, status: 200, statusText: 'OK', headers: {}, config }
    }
    throw new Error(`Unexpected API request ${config.method} ${config.url}`)
  }
  try {
    store.online = true
    await store.syncNow()
    assert.equal(store.pending, 0)
    assert.equal(store.lists[0].groupId, 'group-already-assigned')
    assert.equal(store.syncError, '')
  } finally {
    api.defaults.adapter = previousAdapter
    await store.clearSession()
  }
})

test('batch task actions and manual sorting update the local projection without waiting for network', async () => {
  offline()
  setActivePinia(createPinia())
  const store = useTasksStore()
  await store.switchUser({ id: 'task-fast-batch-sort' })
  await store.init()
  const first = await store.create({ title: '第一项' })
  const second = await store.create({ title: '第二项' })
  const third = await store.create({ title: '第三项' })

  await store.sortTask(store.tasks.find(item => item.publicId === third.id), 0)
  assert.deepEqual(store.tasks.slice().sort((a, b) => a.sortOrder - b.sortOrder).map(item => item.title), ['第三项', '第一项', '第二项'])
  const selected = [first, second].map(item => store.tasks.find(value => value.publicId === item.id))
  const result = await store.batchTasks(selected, 'COMPLETE')
  assert.equal(result.affected, 2)
  assert.equal(store.completedTasks.length, 2)
  assert.equal(store.pending, 3)
  assert.equal(store.syncing, false)
  await store.clearSession()
})

test('authentication failures retain queued task changes and expose a non-blocking status', async () => {
  offline()
  setActivePinia(createPinia())
  const store = useTasksStore()
  await store.switchUser({ id: 'task-sync-auth-failure' })
  await store.init()
  await store.create({ title: '保留在本机' })
  const previousAdapter = api.defaults.adapter
  api.defaults.adapter = async config => {
    if (config.url.includes('/api/v1/tasks/sync/push') || config.url.includes('/api/v1/auth/refresh')) {
      const error = new Error('Unauthorized')
      error.config = config
      error.response = { status: 401, config }
      throw error
    }
    throw new Error(`Unexpected API request ${config.method} ${config.url}`)
  }
  try {
    store.online = true
    await store.syncNow()
    assert.equal(store.pending, 1)
    assert.match(store.syncError, /登录已过期/)
    assert.equal(store.tasks[0].title, '保留在本机')
  } finally {
    api.defaults.adapter = previousAdapter
    await store.clearSession()
  }
})
