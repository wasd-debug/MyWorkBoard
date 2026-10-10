import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { useTasksStore } from './tasks.js'
import { useAppStore } from './app.js'

test('offline task store refuses writes instead of claiming local persistence', async () => {
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { onLine: false } })
  setActivePinia(createPinia())
  const store = useTasksStore()
  await assert.rejects(() => store.create({ title: '离线任务' }), error => error.code === 'ONLINE_REQUIRED')
})

test('task getters separate open and completed rows', () => {
  setActivePinia(createPinia())
  const store = useTasksStore()
  store.tasks = [{ publicId: '1', status: 'OPEN' }, { publicId: '2', status: 'COMPLETED' }]
  assert.deepEqual(store.openTasks.map(item => item.publicId), ['1'])
  assert.deepEqual(store.completedTasks.map(item => item.publicId), ['2'])
})

test('cached offline session refuses task writes even when the browser reports online', async () => {
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { onLine: true } })
  setActivePinia(createPinia())
  useAppStore().offlineSession = true
  await assert.rejects(() => useTasksStore().create({ title: '缓存会话任务' }), error => error.code === 'ONLINE_REQUIRED')
})
