import { defineStore } from 'pinia'
import { createTaskSyncEngine } from '../../packages/sync-engine/src/index.js'
import { apiListTaskLists, apiListTasks, apiPullTaskSync, apiPushTaskSync } from '../../packages/api-client/src/index.js'
import { accountScopeFor, taskDatabaseName } from '../utils/accountScope.js'
import { createClientId } from '../utils/clientId.js'

const SCOPE = 'tasks'
const transport = {
  push: (_scope, operations) => apiPushTaskSync(operations.map(({ bookId, payload, ...operation }) => ({
    ...operation,
    payload: payload ? Object.fromEntries(Object.entries(payload).filter(([key]) => key !== 'bookId')) : payload
  }))),
  pull: (_scope, cursor) => apiPullTaskSync(cursor)
}

let engine = createTaskSyncEngine({ dbName: taskDatabaseName(''), transport })
let sessionEpoch = 0
let listenersInstalled = false
let syncTimer
let activeSyncPromise

function clearState(store) {
  Object.assign(store, { ready: false, loading: false, syncing: false, lists: [], tasks: [], total: 0,
    conflicts: [], rejected: [], pending: 0, error: '' })
}

function localTask(value) {
  const { id, ...rest } = value
  return { ...rest, id, publicId: id }
}

export const useTasksStore = defineStore('tasks', {
  state: () => ({
    ready: false, loading: false, saving: false, syncing: false,
    online: typeof navigator === 'undefined' ? true : navigator.onLine,
    accountScope: '', lists: [], tasks: [], total: 0, conflicts: [], rejected: [], pending: 0, error: ''
  }),
  getters: {
    inbox: state => state.lists.find(item => item.systemKey === 'INBOX') || state.lists[0],
    openTasks: state => state.tasks.filter(item => item.status !== 'COMPLETED'),
    completedTasks: state => state.tasks.filter(item => item.status === 'COMPLETED')
  },
  actions: {
    async switchUser(user) {
      const nextScope = accountScopeFor(user)
      if (nextScope && nextScope === this.accountScope) return
      const previous = engine
      sessionEpoch += 1
      clearTimeout(syncTimer)
      syncTimer = undefined
      activeSyncPromise = null
      clearState(this)
      this.accountScope = nextScope
      engine = createTaskSyncEngine({ dbName: taskDatabaseName(nextScope), transport })
      await previous.close()
    },
    async clearSession() { await this.switchUser(null) },
    installListeners() {
      if (listenersInstalled || typeof window === 'undefined') return
      listenersInstalled = true
      window.addEventListener('online', () => { this.online = true; this.refreshServer() })
      window.addEventListener('offline', () => { this.online = false })
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible' && navigator.onLine) this.refreshServer()
      })
    },
    async init({ waitForRemote = true } = {}) {
      this.installListeners()
      if (this.ready) return
      this.loading = true
      try {
        await this.hydrate()
        if (this.online && this.accountScope) {
          const refresh = this.refreshServer()
          if (waitForRemote) await refresh
        }
      } finally {
        this.loading = false
        this.ready = true
      }
    },
    async fetch() { return this.init() },
    async hydrate() {
      const epoch = sessionEpoch
      const scoped = engine
      const [lists, tasks, conflicts, rejected, pending] = await Promise.all([
        scoped.list('task-list', { bookId: SCOPE }), scoped.list('task', { bookId: SCOPE }),
        scoped.conflicts(SCOPE), scoped.rejected(SCOPE), scoped.pendingOperations(SCOPE)
      ])
      if (epoch !== sessionEpoch || scoped !== engine) return
      this.lists = lists.map(item => ({ ...item, publicId: item.id }))
      this.tasks = tasks.map(localTask)
      this.total = this.tasks.length
      this.conflicts = conflicts
      this.rejected = rejected
      this.pending = pending.length
    },
    async refreshServer() {
      if (!this.online || !this.accountScope) return
      const epoch = sessionEpoch
      const scoped = engine
      this.error = ''
      try {
        const lists = await apiListTaskLists()
        if (epoch !== sessionEpoch || scoped !== engine) return
        await this.syncNow()
        await scoped.replace('task-list', (lists || []).map(item => ({ ...item, id: item.publicId })), { bookId: SCOPE })
        const remoteTasks = []
        let pageNumber = 0
        let total = 0
        do {
          const page = await apiListTasks({ page: pageNumber, size: 200 })
          remoteTasks.push(...(page?.items || []))
          total = Number(page?.total || remoteTasks.length)
          pageNumber += 1
        } while (remoteTasks.length < total)
        const pendingIds = new Set((await scoped.pendingOperations(SCOPE)).map(item => item.entityId))
        for (const item of remoteTasks) {
          if (!pendingIds.has(item.publicId)) {
            await scoped.put('task', { ...item, id: item.publicId }, { bookId: SCOPE, recordOp: false })
          }
        }
        await this.hydrate()
      } catch (error) {
        if (epoch === sessionEpoch) this.error = error?.response?.data?.detail || error?.message || '任务同步失败'
      }
    },
    async saveLocal(value, operation = 'UPSERT') {
      const id = value.publicId || value.id || createClientId()
      const pending = (await engine.pendingOperations(SCOPE)).find(item => item.entityId === id)
      if (operation === 'DELETE' && pending && Number(pending.baseRevision || 0) === 0) {
        await engine.removeStore('oplog', pending.opId)
        await engine.removeStore('tasks', `${SCOPE}:${id}`)
        await this.hydrate()
        return { ...value, deleted: true }
      }
      const options = { bookId: SCOPE, opId: pending?.opId, baseRevision: value.revision || 0 }
      const record = operation === 'DELETE'
        ? await engine.remove('task', id, options)
        : await engine.put('task', { ...value, id, publicId: undefined }, options)
      await this.hydrate()
      this.scheduleSync()
      return localTask(record)
    },
    async create(payload) {
      this.saving = true
      try {
        return await this.saveLocal({ ...payload, publicId: createClientId(), listId: payload.listId || this.inbox?.publicId || '',
          status: 'OPEN', source: 'WEB', revision: 0, completedAt: null })
      } finally { this.saving = false }
    },
    async update(item, payload) {
      this.saving = true
      try { return await this.saveLocal({ ...item, ...payload, publicId: item.publicId }) }
      finally { this.saving = false }
    },
    async complete(item) { return this.saveLocal({ ...item, status: 'COMPLETED', completedAt: new Date().toISOString() }) },
    async reopen(item) { return this.saveLocal({ ...item, status: 'OPEN', completedAt: null }) },
    async remove(item) { return this.saveLocal(item, 'DELETE') },
    scheduleSync() {
      clearTimeout(syncTimer)
      const epoch = sessionEpoch
      syncTimer = setTimeout(() => { if (epoch === sessionEpoch) this.syncNow() }, 250)
    },
    async syncNow() {
      if (!this.online || !this.accountScope) return
      const epoch = sessionEpoch
      const scoped = engine
      if (activeSyncPromise?.epoch === epoch) return activeSyncPromise.promise
      this.syncing = true
      const promise = (async () => {
        try {
          const result = await scoped.sync(SCOPE)
          if (result?.resetRequired) await scoped.sync(SCOPE)
          if (epoch === sessionEpoch && scoped === engine) await this.hydrate()
          return result
        } catch (error) {
          if (epoch === sessionEpoch) this.error = error?.response?.data?.detail || error?.message || '任务同步失败'
        } finally {
          if (activeSyncPromise?.promise === promise) { this.syncing = false; activeSyncPromise = null }
        }
      })()
      activeSyncPromise = { epoch, promise }
      return promise
    },
    async resolveConflict(opId, strategy) {
      await engine.resolveConflict(opId, strategy)
      await this.hydrate()
      this.scheduleSync()
    }
  }
})
