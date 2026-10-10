import { defineStore } from 'pinia'
import { createTaskSyncEngine } from '../../packages/sync-engine/src/index.js'
import { apiListDeletedTaskLists, apiListTaskLists, apiListTaskTags, apiListTasks, apiPullTaskSync,
  apiGetHabitStats, apiListCountdowns, apiListHabits, apiPurgeTask, apiPushTaskSync, apiRestoreTask,
  apiRestoreTaskList } from '../../packages/api-client/src/index.js'
import { accountScopeFor, taskDatabaseName } from '../utils/accountScope.js'
import { createClientId } from '../utils/clientId.js'

const SCOPE = 'tasks'
const transport = {
  push: (_scope, operations) => apiPushTaskSync([...operations].sort((a, b) => Number(a.operation === 'DELETE') - Number(b.operation === 'DELETE')).map(({ bookId, payload, ...operation }) => {
    const clean = payload ? Object.fromEntries(Object.entries(payload).filter(([key]) => !['bookId', 'updatedAt'].includes(key))) : payload
    if (clean && ['habit', 'habit-checkin', 'countdown'].includes(operation.entityType)) {
      return { ...operation, payload: { id: clean.id, revision: clean.revision, extra: Object.fromEntries(Object.entries(clean).filter(([key]) => !['id', 'revision', 'deleted', 'deletedAt', 'extra'].includes(key))) } }
    }
    return { ...operation, payload: clean }
  })),
  pull: (_scope, cursor) => apiPullTaskSync(cursor)
}

let engine = createTaskSyncEngine({ dbName: taskDatabaseName(''), transport })
let sessionEpoch = 0
let listenersInstalled = false
let syncTimer
let activeSyncPromise

function clearState(store) {
  Object.assign(store, { ready: false, loading: false, syncing: false, lists: [], deletedLists: [], tags: [], tasks: [], total: 0,
    habits: [], habitCheckins: [], countdowns: [], conflicts: [], rejected: [], pending: 0, error: '' })
}

function localTask(value) {
  const { id, ...rest } = value
  return { ...rest, id, publicId: id }
}

async function replaceRemoteCollection(scoped, type, records) {
  const pending = new Set((await scoped.pendingOperations(SCOPE)).filter(item => item.entityType === type).map(item => item.entityId))
  const local = (await scoped.list(type, { bookId: SCOPE, includeDeleted: true })).filter(item => pending.has(item.id))
  await scoped.replace(type, [...records.filter(item => !pending.has(item.publicId)).map(item => ({ ...item, id: item.publicId })), ...local], { bookId: SCOPE })
}

async function organizationOptions(type, item) {
  const pending = (await engine.pendingOperations(SCOPE)).find(value => value.entityType === type && value.entityId === item.publicId)
  return { bookId: SCOPE, opId: pending?.opId, baseRevision: pending?.baseRevision ?? item.revision ?? 0 }
}

export const useTasksStore = defineStore('tasks', {
  state: () => ({
    ready: false, loading: false, saving: false, syncing: false,
    online: typeof navigator === 'undefined' ? true : navigator.onLine,
    accountScope: '', lists: [], deletedLists: [], tags: [], tasks: [], habits: [], habitCheckins: [], countdowns: [],
    total: 0, conflicts: [], rejected: [], pending: 0, error: ''
  }),
  getters: {
    inbox: state => state.lists.find(item => item.systemKey === 'INBOX') || state.lists[0],
    openTasks: state => state.tasks.filter(item => !item.deleted && item.status !== 'COMPLETED'),
    completedTasks: state => state.tasks.filter(item => !item.deleted && item.status === 'COMPLETED'),
    trashedTasks: state => state.tasks.filter(item => item.deleted)
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
      const [lists, tags, tasks, habits, habitCheckins, countdowns, conflicts, rejected, pending] = await Promise.all([
        scoped.list('task-list', { bookId: SCOPE, includeDeleted: true }), scoped.list('task-tag', { bookId: SCOPE }),
        scoped.list('task', { bookId: SCOPE, includeDeleted: true }),
        scoped.list('habit', { bookId: SCOPE, includeDeleted: true }),
        scoped.list('habit-checkin', { bookId: SCOPE, includeDeleted: true }),
        scoped.list('countdown', { bookId: SCOPE, includeDeleted: true }),
        scoped.conflicts(SCOPE), scoped.rejected(SCOPE), scoped.pendingOperations(SCOPE)
      ])
      if (epoch !== sessionEpoch || scoped !== engine) return
      this.lists = lists.filter(item => !item.deleted).map(item => ({ ...item, publicId: item.id })).sort((a, b) => a.sortOrder - b.sortOrder)
      this.deletedLists = lists.filter(item => item.deleted).map(item => ({ ...item, publicId: item.id }))
      this.tags = tags.map(item => ({ ...item, publicId: item.id })).sort((a, b) => a.sortOrder - b.sortOrder)
      this.tasks = tasks.map(localTask)
      this.habits = habits.filter(item => !item.deleted).map(localTask).sort((a, b) => a.sortOrder - b.sortOrder)
      this.habitCheckins = habitCheckins.filter(item => !item.deleted).map(localTask)
      this.countdowns = countdowns.filter(item => !item.deleted).map(localTask)
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
        await this.syncNow()
        const [lists, deletedLists, tags, habits, countdowns] = await Promise.all([
          apiListTaskLists(), apiListDeletedTaskLists(), apiListTaskTags(), apiListHabits(), apiListCountdowns()
        ])
        if (epoch !== sessionEpoch || scoped !== engine) return
        await replaceRemoteCollection(scoped, 'task-list', [...(lists || []), ...(deletedLists || []).map(item => ({ ...item, deleted: true }))])
        await replaceRemoteCollection(scoped, 'task-tag', tags || [])
        await replaceRemoteCollection(scoped, 'habit', habits || [])
        await replaceRemoteCollection(scoped, 'countdown', countdowns || [])
        const checkins = []
        for (const habit of habits || []) checkins.push(...((await apiGetHabitStats(habit.publicId))?.checkins || []))
        await replaceRemoteCollection(scoped, 'habit-checkin', checkins || [])
        const remoteTasks = []
        for (const view of ['ALL', 'TRASH']) {
          let pageNumber = 0
          let fetched = 0
          let total = 0
          do {
            const page = await apiListTasks({ view, page: pageNumber, size: 200 })
            if (epoch !== sessionEpoch || scoped !== engine) return
            const items = page?.items || []
            remoteTasks.push(...items)
            fetched += items.length
            total = Number(page?.total ?? fetched)
            pageNumber += 1
            if (!items.length) break
          } while (fetched < total)
        }
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
    async saveEfficiency(type, value, operation = 'UPSERT') {
      const id = value.publicId || value.id || createClientId()
      const pending = (await engine.pendingOperations(SCOPE)).find(item => item.entityType === type && item.entityId === id)
      if (operation === 'DELETE' && pending && Number(pending.baseRevision || 0) === 0) {
        await engine.removeStore(engine.storeName(type), `${SCOPE}:${id}`)
        await engine.removeStore('oplog', pending.opId)
      } else if (operation === 'DELETE') {
        await engine.remove(type, id, { bookId: SCOPE, opId: pending?.opId, baseRevision: value.revision || 0 })
      } else {
        await engine.put(type, { ...value, id, publicId: undefined }, { bookId: SCOPE, opId: pending?.opId, baseRevision: value.revision || 0 })
      }
      await this.hydrate(); this.scheduleSync()
      return id
    },
    createHabit(payload) { return this.saveEfficiency('habit', { ...payload, publicId: createClientId(), revision: 0 }) },
    updateHabit(item, payload) { return this.saveEfficiency('habit', { ...item, ...payload }) },
    deleteHabit(item) { return this.saveEfficiency('habit', item, 'DELETE') },
    checkinHabit(habit, payload) {
      const existing = this.habitCheckins.find(item => item.habitId === habit.publicId && item.date === payload.date)
      const count = payload.status === 'DONE' ? Number(existing?.count || 0) + Number(payload.count || 1) : 0
      return this.saveEfficiency('habit-checkin', { ...(existing || {}), ...payload, count, habitId: habit.publicId,
        publicId: existing?.publicId || createClientId(), revision: existing?.revision || 0 })
    },
    createCountdown(payload) { return this.saveEfficiency('countdown', { ...payload, publicId: createClientId(), revision: 0 }) },
    updateCountdown(item, payload) { return this.saveEfficiency('countdown', { ...item, ...payload }) },
    deleteCountdown(item) { return this.saveEfficiency('countdown', item, 'DELETE') },
    async create(payload) {
      this.saving = true
      try {
        return await this.saveLocal({ ...payload, publicId: createClientId(), listId: payload.listId || this.inbox?.publicId || '',
          status: 'OPEN', source: 'WEB', revision: 0, completedAt: null })
      } finally { this.saving = false }
    },
    async update(item, payload) {
      this.saving = true
      try {
        if (payload.listId && payload.listId !== item.listId) {
          const queue = [item.publicId]
          for (let index = 0; index < queue.length; index += 1) {
            for (const child of this.tasks.filter(value => value.parentId === queue[index])) {
              if (queue.includes(child.publicId)) continue
              queue.push(child.publicId)
              await engine.put('task', { ...child, id: child.publicId, listId: payload.listId }, { bookId: SCOPE, recordOp: false })
            }
          }
        }
        return await this.saveLocal({ ...item, ...payload, publicId: item.publicId })
      }
      finally { this.saving = false }
    },
    async complete(item) { return this.saveLocal({ ...item, status: 'COMPLETED', completedAt: new Date().toISOString() }) },
    async reopen(item) { return this.saveLocal({ ...item, status: 'OPEN', completedAt: null }) },
    async remove(item) {
      const descendants = []
      const queue = [item.publicId]
      for (let index = 0; index < queue.length; index += 1) {
        for (const child of this.tasks.filter(value => value.parentId === queue[index] && !value.deleted)) {
          if (!queue.includes(child.publicId)) { queue.push(child.publicId); descendants.push(child) }
        }
      }
      for (const child of descendants) {
        const pending = (await engine.pendingOperations(SCOPE)).find(value => value.entityId === child.publicId)
        if (pending && Number(pending.baseRevision || 0) === 0) {
          await engine.removeStore('oplog', pending.opId)
          await engine.removeStore('tasks', `${SCOPE}:${child.publicId}`)
        } else {
          await engine.put('task', { ...child, id: child.publicId, deleted: true }, { bookId: SCOPE, recordOp: false })
        }
      }
      return this.saveLocal(item, 'DELETE')
    },
    async restore(item) {
      if (!this.online) throw new Error('恢复任务需要联网执行')
      const epoch = sessionEpoch
      const scoped = engine
      const restored = await apiRestoreTask(item.publicId, item.revision)
      if (epoch !== sessionEpoch || scoped !== engine) return restored
      await scoped.put('task', { ...restored, id: restored.publicId }, { bookId: SCOPE, recordOp: false })
      await this.hydrate()
      await this.refreshServer()
      return restored
    },
    async restoreList(item) {
      if (!this.online) throw new Error('恢复清单需要联网执行')
      const epoch = sessionEpoch
      await apiRestoreTaskList(item.publicId, item.revision)
      if (epoch === sessionEpoch) await this.refreshServer()
    },
    async purge(item) {
      if (!this.online) throw new Error('永久清除需要联网执行')
      const epoch = sessionEpoch
      const scoped = engine
      await apiPurgeTask(item.publicId)
      if (epoch !== sessionEpoch || scoped !== engine) return
      await scoped.removeStore('tasks', `${SCOPE}:${item.publicId}`)
      await this.hydrate()
    },
    async createList(payload) {
      const created = await engine.put('task-list', { ...payload, id: createClientId(), revision: 0,
        archived: Boolean(payload.archived), sortOrder: payload.sortOrder ?? this.lists.length }, { bookId: SCOPE })
      await this.hydrate()
      this.scheduleSync()
      return created
    },
    async updateList(item, payload) {
      const changed = await engine.put('task-list', { ...item, ...payload, id: item.publicId, publicId: undefined },
        await organizationOptions('task-list', item))
      await this.hydrate()
      this.scheduleSync()
      return changed
    },
    async deleteList(item) {
      const options = await organizationOptions('task-list', item)
      if (options.opId && Number(options.baseRevision) === 0) {
        await engine.removeStore('oplog', options.opId)
        await engine.removeStore('task-lists', `${SCOPE}:${item.publicId}`)
        for (const task of this.tasks.filter(value => value.listId === item.publicId && !value.deleted)) await this.remove(task)
      } else {
        await engine.remove('task-list', item.publicId, options)
      }
      await this.hydrate()
      this.scheduleSync()
    },
    async createTag(payload) {
      const created = await engine.put('task-tag', { ...payload, id: createClientId(), revision: 0,
        sortOrder: payload.sortOrder ?? this.tags.length }, { bookId: SCOPE })
      await this.hydrate()
      this.scheduleSync()
      return created
    },
    async updateTag(item, payload) {
      const changed = await engine.put('task-tag', { ...item, ...payload, id: item.publicId, publicId: undefined },
        await organizationOptions('task-tag', item))
      await this.hydrate()
      this.scheduleSync()
      return changed
    },
    async deleteTag(item) {
      const options = await organizationOptions('task-tag', item)
      if (options.opId && Number(options.baseRevision) === 0) {
        await engine.removeStore('oplog', options.opId)
        await engine.removeStore('task-tags', `${SCOPE}:${item.publicId}`)
      } else {
        await engine.remove('task-tag', item.publicId, options)
      }
      for (const task of this.tasks.filter(value => value.tagIds?.includes(item.publicId))) {
        await engine.put('task', { ...task, id: task.publicId, tagIds: task.tagIds.filter(id => id !== item.publicId) },
          { bookId: SCOPE, recordOp: false })
      }
      await this.hydrate()
      this.scheduleSync()
    },
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
