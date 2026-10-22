import { defineStore } from 'pinia'
import { createTaskSyncEngine } from '../../packages/sync-engine/src/index.js'
import { apiListDeletedTaskLists, apiListTaskLists, apiListTaskTags, apiListTasks, apiPullTaskSync,
  apiGetHabitStats, apiListCountdowns, apiListHabits, apiPurgeTask, apiPushTaskSync, apiRestoreTask,
  apiRestoreTaskList, apiListTaskListGroups, apiCreateTaskListGroup, apiUpdateTaskListGroup,
  apiDeleteTaskListGroup, apiAssignTaskListGroup, apiBatchTasks } from '../../packages/api-client/src/index.js'
import { accountScopeFor, taskDatabaseName } from '../utils/accountScope.js'
import { createClientId } from '../utils/clientId.js'

const SCOPE = 'tasks'
const GROUP_ASSIGNMENTS_META_KEY = 'pending-list-group-assignments'
const LIST_GROUPS_CACHE_META_KEY = 'task-list-groups-cache'
const transport = {
  push: async (_scope, operations) => {
    const blockedIds = new Set((await engine.conflicts(SCOPE)).map(item => item.operation?.opId || item.id))
    const ready = operations.filter(operation => !blockedIds.has(operation.opId))
    if (!ready.length) return { results: [] }
    return apiPushTaskSync([...ready].sort((a, b) => Number(a.operation === 'DELETE') - Number(b.operation === 'DELETE')).map(({ bookId, payload, ...operation }) => {
    const excluded = ['bookId', 'updatedAt', ...(operation.entityType === 'task-list' ? ['groupId'] : [])]
    const clean = payload ? Object.fromEntries(Object.entries(payload).filter(([key]) => !excluded.includes(key))) : payload
    if (clean && ['habit', 'habit-checkin', 'countdown'].includes(operation.entityType)) {
      return { ...operation, payload: { id: clean.id, revision: clean.revision, extra: Object.fromEntries(Object.entries(clean).filter(([key]) => !['id', 'revision', 'deleted', 'deletedAt', 'extra'].includes(key))) } }
    }
    return { ...operation, payload: clean }
    }))
  },
  pull: (_scope, cursor) => apiPullTaskSync(cursor)
}

let engine = createTaskSyncEngine({ dbName: taskDatabaseName(''), transport })
let sessionEpoch = 0
let listenersInstalled = false
let syncTimer
let syncRetryTimer
let syncRetryAttempt = 0
let activeSyncPromise
let activeRefreshPromise

function clearState(store) {
  Object.assign(store, { ready: false, loading: false, syncing: false, listGroups: [], lists: [], deletedLists: [], tags: [], tasks: [], total: 0, undoStack: [],
    habits: [], habitCheckins: [], countdowns: [], conflicts: [], rejected: [], pending: 0, error: '', syncError: '' })
}

function localTask(value) {
  const { id, ...rest } = value
  return { ...rest, id, publicId: id }
}

async function replaceRemoteCollection(scoped, type, records) {
  const groupAssignments = type === 'task-list' ? await readGroupAssignments(scoped) : []
  const pending = new Set([
    ...(await scoped.pendingOperations(SCOPE)).filter(item => item.entityType === type).map(item => item.entityId),
    ...groupAssignments.map(item => item.listId)
  ])
  const local = (await scoped.list(type, { bookId: SCOPE, includeDeleted: true })).filter(item => pending.has(item.id))
  await scoped.replace(type, [...records.filter(item => !pending.has(item.publicId)).map(item => ({ ...item, id: item.publicId })), ...local], { bookId: SCOPE })
}

async function readGroupAssignments(scoped = engine) {
  return await scoped.meta(GROUP_ASSIGNMENTS_META_KEY) || []
}

async function writeGroupAssignments(assignments, scoped = engine) {
  await scoped.setMeta(GROUP_ASSIGNMENTS_META_KEY, assignments)
}

function conflictListView(entity) {
  return { ...(entity || {}), ...(entity?.extra || {}) }
}

async function reconcileSafeListConflicts(scoped) {
  const conflicts = await scoped.conflicts(SCOPE)
  let requeued = false
  for (const conflict of conflicts) {
    const operation = conflict.operation
    const result = conflict.result
    if (operation?.entityType !== 'task-list' || !result?.serverEntity) continue
    const local = operation.payload || {}
    const server = conflictListView(result.serverEntity)
    const fields = ['name', 'color', 'icon', 'systemKey', 'sortOrder', 'archived', 'pinned', 'viewType', 'objectType', 'showInSmartLists']
    const matchesServer = fields.every(field => local[field] === undefined || local[field] === server[field])
    if (matchesServer) {
      await scoped.resolveConflict(operation.opId, 'server')
      continue
    }
    if (server.systemKey === 'INBOX' && result.message === '收集箱不可重命名') {
      const merged = {
        ...local,
        name: server.name,
        systemKey: server.systemKey,
        revision: Number(result.serverRevision || server.revision || 0)
      }
      await scoped.put('task-list', { ...merged, id: operation.entityId, publicId: undefined }, { bookId: SCOPE, recordOp: false })
      await scoped.resolveConflict(operation.opId, 'merged', merged)
      requeued = true
    }
  }
  return { resolved: conflicts.length > (await scoped.conflicts(SCOPE)).length, requeued }
}

function scheduleSyncRetry(store, epoch) {
  clearTimeout(syncRetryTimer)
  const delay = Math.min(60_000, [1_000, 2_500, 5_000, 10_000, 20_000, 60_000][Math.min(syncRetryAttempt, 5)])
  syncRetryAttempt += 1
  syncRetryTimer = setTimeout(() => {
    if (epoch === sessionEpoch && store.online) void store.syncNow()
  }, delay)
}

function syncFailureMessage(error) {
  if (error?.response?.status === 401) return '登录已过期，变更已保存在本机；重新登录后会继续同步'
  if (error?.response?.status === 403) return '当前账号无权同步部分变更；本机数据仍已保留'
  return error?.response?.data?.detail || error?.message || '后台同步失败，变更已保存在本机并将重试'
}

async function organizationOptions(type, item) {
  const pending = (await engine.pendingOperations(SCOPE)).find(value => value.entityType === type && value.entityId === item.publicId)
  return { bookId: SCOPE, opId: pending?.opId, baseRevision: pending?.baseRevision ?? item.revision ?? 0 }
}

export const useTasksStore = defineStore('tasks', {
  state: () => ({
    ready: false, loading: false, saving: false, syncing: false,
    online: typeof navigator === 'undefined' ? true : navigator.onLine,
    accountScope: '', listGroups: [], lists: [], deletedLists: [], tags: [], tasks: [], habits: [], habitCheckins: [], countdowns: [],
    total: 0, conflicts: [], rejected: [], pending: 0, error: '', syncError: '', undoStack: []
  }),
  getters: {
    inbox: state => state.lists.find(item => item.systemKey === 'INBOX') || state.lists[0],
    openTasks: state => state.tasks.filter(item => !item.deleted && !['COMPLETED', 'ABANDONED'].includes(item.status)),
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
      clearTimeout(syncRetryTimer)
      syncTimer = undefined
      syncRetryTimer = undefined
      syncRetryAttempt = 0
      activeSyncPromise = null
      activeRefreshPromise = null
      clearState(this)
      this.accountScope = nextScope
      engine = createTaskSyncEngine({ dbName: taskDatabaseName(nextScope), transport })
      await previous.close()
    },
    async clearSession() { await this.switchUser(null) },
    installListeners() {
      if (listenersInstalled || typeof window === 'undefined') return
      listenersInstalled = true
      window.addEventListener('online', () => { this.online = true; void this.refreshServer() })
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
      const [lists, tags, tasks, habits, habitCheckins, countdowns, conflicts, rejected, pending, cachedListGroups, groupAssignments] = await Promise.all([
        scoped.list('task-list', { bookId: SCOPE, includeDeleted: true }), scoped.list('task-tag', { bookId: SCOPE }),
        scoped.list('task', { bookId: SCOPE, includeDeleted: true }),
        scoped.list('habit', { bookId: SCOPE, includeDeleted: true }),
        scoped.list('habit-checkin', { bookId: SCOPE, includeDeleted: true }),
        scoped.list('countdown', { bookId: SCOPE, includeDeleted: true }),
        scoped.conflicts(SCOPE), scoped.rejected(SCOPE), scoped.pendingOperations(SCOPE),
        scoped.meta(LIST_GROUPS_CACHE_META_KEY), readGroupAssignments(scoped)
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
      this.listGroups = cachedListGroups || []
      for (const assignment of groupAssignments) {
        const list = this.lists.find(item => item.publicId === assignment.listId)
        if (list) list.groupId = assignment.groupId || null
      }
      this.pending = pending.length + groupAssignments.length
    },
    async refreshServer() {
      if (!this.online || !this.accountScope) return
      const epoch = sessionEpoch
      const scoped = engine
      if (activeRefreshPromise?.epoch === epoch) return activeRefreshPromise.promise
      const promise = (async () => {
        this.error = ''
        try {
          await this.syncNow()
          const [listGroups, lists, deletedLists, tags, habits, countdowns] = await Promise.all([
            apiListTaskListGroups(), apiListTaskLists(), apiListDeletedTaskLists(), apiListTaskTags(), apiListHabits(), apiListCountdowns()
          ])
          if (epoch !== sessionEpoch || scoped !== engine) return
          this.listGroups = listGroups || []
          await scoped.setMeta(LIST_GROUPS_CACHE_META_KEY, this.listGroups)
          await replaceRemoteCollection(scoped, 'task-list', [...(lists || []), ...(deletedLists || []).map(item => ({ ...item, deleted: true }))])
          await replaceRemoteCollection(scoped, 'task-tag', tags || [])
          await replaceRemoteCollection(scoped, 'habit', habits || [])
          await replaceRemoteCollection(scoped, 'countdown', countdowns || [])
          const habitStats = await Promise.all((habits || []).map(habit => apiGetHabitStats(habit.publicId)))
          const checkins = habitStats.flatMap(result => result?.checkins || [])
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
      })()
      activeRefreshPromise = { epoch, promise }
      try {
        return await promise
      } finally {
        if (activeRefreshPromise?.promise === promise) activeRefreshPromise = null
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
          sortOrder: payload.sortOrder ?? this.tasks.filter(item => item.listId === (payload.listId || this.inbox?.publicId || '') && item.status === 'OPEN' && !item.deleted).length,
          status: 'OPEN', source: 'WEB', revision: 0, completedAt: null })
      } finally { this.saving = false }
    },
    async update(item, payload) {
      this.saving = true
      try {
        this.undoStack.push({ item: { ...item }, label: '编辑任务' })
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
    async complete(item) { this.undoStack.push({ item: { ...item }, label: '完成任务' }); return this.saveLocal({ ...item, status: 'COMPLETED', completedAt: new Date().toISOString() }) },
    async abandon(item) {
      if (item.status === 'ABANDONED') return item
      this.undoStack.push({ item: { ...item }, label: '放弃任务' })
      return this.saveLocal({ ...item, status: 'ABANDONED', completedAt: null })
    },
    async reopen(item) {
      this.undoStack.push({ item: { ...item }, label: '重新打开任务' })
      if (item.rrule && item.seriesId && this.tasks.some(value => value.seriesId === item.seriesId && value.seriesSequence > (item.seriesSequence || 0) && value.status === 'OPEN' && !value.deleted)) {
        throw new Error('周期任务的下一实例已生成，无法重开当前实例')
      }
      return this.saveLocal({ ...item, status: 'OPEN', completedAt: null })
    },
    async undoLast() {
      const entry = this.undoStack.pop()
      if (!entry) return false
      await this.saveLocal(entry.item)
      return true
    },
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
    async createListGroup(payload) {
      if (!this.online) throw new Error('清单分组需要联网保存')
      const created = await apiCreateTaskListGroup(payload)
      if (created?.publicId) {
        this.listGroups = [...this.listGroups.filter(item => item.publicId !== created.publicId), created]
        await engine.setMeta(LIST_GROUPS_CACHE_META_KEY, this.listGroups)
      }
      return created
    },
    async updateListGroup(item, payload) {
      if (!this.online) throw new Error('清单分组需要联网保存')
      const updated = await apiUpdateTaskListGroup(item.publicId, payload, item.revision)
      this.listGroups = this.listGroups.map(value => value.publicId === item.publicId ? { ...value, ...payload, ...(updated || {}) } : value)
      await engine.setMeta(LIST_GROUPS_CACHE_META_KEY, this.listGroups)
      return updated
    },
    async deleteListGroup(item) {
      if (!this.online) throw new Error('清单分组需要联网删除')
      await apiDeleteTaskListGroup(item.publicId, item.revision)
      this.listGroups = this.listGroups.filter(value => value.publicId !== item.publicId)
      await engine.setMeta(LIST_GROUPS_CACHE_META_KEY, this.listGroups)
      for (const list of this.lists.filter(value => value.groupId === item.publicId)) {
        await engine.put('task-list', { ...list, id: list.publicId, groupId: null }, { bookId: SCOPE, recordOp: false })
      }
      await this.hydrate()
    },
    async assignListGroup(item, groupId) {
      const id = item.publicId || item.id
      const normalizedGroupId = groupId || null
      const assignments = await readGroupAssignments()
      const next = [...assignments.filter(value => value.listId !== id), { listId: id, groupId: normalizedGroupId }]
      await writeGroupAssignments(next)
      const current = this.lists.find(value => value.publicId === id) || item
      await engine.put('task-list', { ...current, id, groupId: normalizedGroupId }, { bookId: SCOPE, recordOp: false })
      await this.hydrate()
      this.scheduleSync()
    },
    async reorderLists(orderedLists, assignment = null) {
      const epoch = sessionEpoch
      const scoped = engine
      const items = orderedLists.filter(item => this.lists.some(list => list.publicId === item.publicId))
      if (assignment) {
        const assignments = await readGroupAssignments(scoped)
        const next = [...assignments.filter(value => value.listId !== assignment.listId), {
          listId: assignment.listId,
          groupId: assignment.groupId || null
        }]
        await writeGroupAssignments(next, scoped)
        const current = this.lists.find(value => value.publicId === assignment.listId)
        if (current) await scoped.put('task-list', {
          ...current,
          id: assignment.listId,
          groupId: assignment.groupId || null
        }, { bookId: SCOPE, recordOp: false })
      }
      const pendingOperations = await scoped.pendingOperations(SCOPE)
      await Promise.all(items.map(item => {
        const current = this.lists.find(value => value.publicId === item.publicId)
        if (!current) return Promise.resolve()
        if (Number(current.sortOrder || 0) === Number(item.sortOrder || 0)
          && (current.groupId || null) === (item.groupId || null)) return Promise.resolve()
        const pending = pendingOperations.find(value => value.entityType === 'task-list' && value.entityId === item.publicId)
        return scoped.put('task-list', {
          ...current,
          ...item,
          id: item.publicId,
          publicId: undefined
        }, {
          bookId: SCOPE,
          opId: pending?.opId,
          baseRevision: pending?.baseRevision ?? current.revision ?? 0
        })
      }))
      if (epoch !== sessionEpoch || scoped !== engine) return
      await this.hydrate()
      this.scheduleSync()
    },
    async batchTasks(items, action, values = {}) {
      const current = items.map(item => this.tasks.find(value => value.publicId === item.publicId) || item).filter(Boolean)
      if (action === 'REMINDER') {
        if (!this.online) throw new Error('批量提醒需要联网执行')
        await this.syncNow()
        const result = await apiBatchTasks({ targets: current.map(item => ({ publicId: item.publicId, revision: item.revision })), action, ...values })
        void this.refreshServer()
        return result
      }
      if (!current.length) return { affected: 0, items: [] }
      const pendingOperations = await engine.pendingOperations(SCOPE)
      const changed = []
      for (const item of current) {
        const pending = pendingOperations.find(value => value.entityType === 'task' && value.entityId === item.publicId)
        const options = { bookId: SCOPE, opId: pending?.opId, baseRevision: pending?.baseRevision ?? item.revision ?? 0 }
        if (action === 'DELETE') {
          if (pending && Number(pending.baseRevision || 0) === 0) {
            await engine.removeStore('oplog', pending.opId)
            await engine.removeStore('tasks', `${SCOPE}:${item.publicId}`)
          } else await engine.remove('task', item.publicId, options)
          changed.push({ ...item, deleted: true })
          continue
        }
        const payload = action === 'COMPLETE' ? { ...item, status: 'COMPLETED', completedAt: new Date().toISOString() }
          : action === 'MOVE' ? { ...item, listId: values.listId }
            : action === 'PRIORITY' ? { ...item, priority: values.priority }
              : action === 'RESCHEDULE' ? { ...item, dueAt: values.dueAt || null }
              : action === 'TAGS' ? { ...item, tagIds: [...new Set([...(item.tagIds || []), ...(values.tagIds || [])])] }
                  : null
        if (!payload) throw new Error(`不支持的批量操作：${action}`)
        changed.push(await engine.put('task', { ...payload, id: item.publicId, publicId: undefined }, options))
      }
      await this.hydrate()
      this.scheduleSync()
      return { affected: changed.length, items: changed.map(localTask) }
    },
    async sortTask(item, sortOrder) {
      const current = this.tasks.find(value => value.publicId === item.publicId) || item
      const siblings = this.tasks.filter(value => value.listId === current.listId && value.status === current.status && !value.deleted)
        .sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0))
      const currentIndex = siblings.findIndex(value => value.publicId === current.publicId)
      if (currentIndex < 0) return current
      siblings.splice(currentIndex, 1)
      siblings.splice(Math.max(0, Math.min(Number(sortOrder) || 0, siblings.length)), 0, current)
      const pendingOperations = await engine.pendingOperations(SCOPE)
      for (let index = 0; index < siblings.length; index += 1) {
        const sibling = siblings[index]
        if (sibling.sortOrder !== undefined && sibling.sortOrder !== null && Number(sibling.sortOrder) === index) continue
        const pending = pendingOperations.find(value => value.entityType === 'task' && value.entityId === sibling.publicId)
        await engine.put('task', { ...sibling, id: sibling.publicId, sortOrder: index }, {
          bookId: SCOPE, opId: pending?.opId, baseRevision: pending?.baseRevision ?? sibling.revision ?? 0
        })
      }
      await this.hydrate()
      this.scheduleSync()
      return this.tasks.find(value => value.publicId === current.publicId) || current
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
      const groupId = payload.groupId || ''
      const { groupId: ignoredGroupId, ...listPayload } = payload
      const created = await engine.put('task-list', { ...listPayload, id: createClientId(), revision: 0,
        archived: Boolean(payload.archived), sortOrder: payload.sortOrder ?? this.lists.length }, { bookId: SCOPE })
      await this.hydrate()
      if (groupId) await this.assignListGroup({ ...created, publicId: created.id }, groupId)
      this.scheduleSync()
      return created
    },
    async updateList(item, payload) {
      const { groupId: ignoredGroupId, ...listPayload } = payload
      const changed = await engine.put('task-list', { ...item, ...listPayload, id: item.publicId, publicId: undefined },
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
      syncTimer = setTimeout(() => { if (epoch === sessionEpoch) void this.syncNow() }, 120)
    },
    async syncNow() {
      if (!this.online || !this.accountScope) return
      const epoch = sessionEpoch
      const scoped = engine
      if (activeSyncPromise?.epoch === epoch) {
        activeSyncPromise.rerun = true
        return activeSyncPromise.promise
      }
      this.syncing = true
      const promise = Promise.resolve().then(async () => {
        try {
          let result
          let reconcilePass = 0
          do {
            if (activeSyncPromise?.promise === promise) activeSyncPromise.rerun = false
            await reconcileSafeListConflicts(scoped)
            result = await scoped.sync(SCOPE)
            if (result?.resetRequired) result = await scoped.sync(SCOPE)
            const reconciliation = await reconcileSafeListConflicts(scoped)
            if (reconciliation.requeued && reconcilePass < 1) {
              reconcilePass += 1
              if (activeSyncPromise?.promise === promise) activeSyncPromise.rerun = true
              continue
            }
            if (epoch === sessionEpoch && scoped === engine) await this.hydrate()
          } while (epoch === sessionEpoch && scoped === engine && activeSyncPromise?.rerun)
          if (epoch === sessionEpoch && scoped === engine) {
            await this.flushGroupAssignments(scoped)
            await this.hydrate()
            this.syncError = ''
            if (!this.pending && !this.conflicts.length && !this.rejected.length) syncRetryAttempt = 0
            else if (this.pending && !this.conflicts.length) scheduleSyncRetry(this, epoch)
          }
          return result
        } catch (error) {
          if (epoch === sessionEpoch) {
            this.syncError = syncFailureMessage(error)
            await this.hydrate()
            scheduleSyncRetry(this, epoch)
          }
        } finally {
          if (activeSyncPromise?.promise === promise) { this.syncing = false; activeSyncPromise = null }
        }
      })
      activeSyncPromise = { epoch, promise, rerun: false }
      return promise
    },
    async flushGroupAssignments(scoped = engine) {
      let assignments = await readGroupAssignments(scoped)
      for (const assignment of assignments) {
        const localPending = (await scoped.pendingOperations(SCOPE)).some(item => item.entityType === 'task-list' && item.entityId === assignment.listId)
        const conflictPending = (await scoped.conflicts(SCOPE)).some(item => item.operation?.entityType === 'task-list' && item.operation.entityId === assignment.listId)
        if (localPending || conflictPending) continue
        const list = this.lists.find(item => item.publicId === assignment.listId)
        if (!list) continue
        let latest = list
        try {
          await apiAssignTaskListGroup(assignment.listId, assignment.groupId, Number(latest.revision || 0))
        } catch (error) {
          if (error?.response?.status !== 409) throw error
          const remoteLists = await apiListTaskLists()
          const remote = (remoteLists || []).find(value => value.publicId === assignment.listId)
          if (!remote) throw error
          latest = { ...remote, groupId: assignment.groupId }
          if (remote.groupId !== assignment.groupId) {
            await apiAssignTaskListGroup(assignment.listId, assignment.groupId, Number(remote.revision || 0))
          }
        }
        assignments = assignments.filter(value => value.listId !== assignment.listId)
        await writeGroupAssignments(assignments, scoped)
        await scoped.put('task-list', { ...latest, id: list.publicId, groupId: assignment.groupId }, { bookId: SCOPE, recordOp: false })
      }
    },
    async resolveConflict(opId, strategy) {
      await engine.resolveConflict(opId, strategy)
      await this.hydrate()
      this.scheduleSync()
    }
  }
})
