import { defineStore } from 'pinia'
import { createTaskSyncEngine } from '../../packages/sync-engine/src/index.js'
import { apiListDeletedTaskLists, apiListTaskLists, apiListTaskTags, apiListTasks, apiPullTaskSync,
  apiGetHabitStats, apiListCountdowns, apiListHabits, apiPurgeTask, apiPushTaskSync, apiRestoreTask,
  apiRestoreTaskList, apiListTaskListGroups, apiCreateTaskListGroup, apiUpdateTaskListGroup,
  apiDeleteTaskListGroup, apiAssignTaskListGroup, apiBatchTasks,
  apiListCountdownGroups, apiCreateCountdownGroup, apiUpdateCountdownGroup, apiDeleteCountdownGroup } from '../../packages/api-client/src/index.js'
import { accountScopeFor, taskDatabaseName } from '../utils/accountScope.js'
import { createClientId } from '../utils/clientId.js'

const SCOPE = 'tasks'
const SYNC_IDLE_DELAY_MS = 1200
// 单轮同步里变化的任务超过这个数量，就地逐条改内存还不如直接全量重排
const INCREMENTAL_PATCH_LIMIT = 100
// 能就地打补丁的内存集合。键是 sync 引擎里真实的存储名（引擎会做复数化：
// task → tasks、habit-checkin → habit-checkins），不是实体类型名
const PATCHABLE_COLLECTIONS = {
  tasks: 'tasks',
  habits: 'habits',
  'habit-checkins': 'habitCheckins',
  countdowns: 'countdowns'
}

/**
 * 任务列表里影响分组/排序/可见性的字段。动了任何一个，就地改字段会让列表停在
 * 错的顺序上，必须走全量重排。
 */
function taskNeedsRebuild(existing, next) {
  return Boolean(existing.deleted) !== Boolean(next.deleted) ||
    existing.status !== next.status ||
    Number(existing.sortOrder || 0) !== Number(next.sortOrder || 0) ||
    existing.listId !== next.listId ||
    (existing.parentId || null) !== (next.parentId || null) ||
    existing.dueAt !== next.dueAt ||
    Number(next.revision || 0) < Number(existing.revision || 0)
}
/** 首屏兜底下载时的分页并发数与本地批量写入批大小。 */
const PAGE_CONCURRENCY = 5
const LOCAL_WRITE_BATCH = 500
const LIST_GROUPS_CACHE_META_KEY = 'task-list-groups-cache'
const COUNTDOWN_GROUPS_CACHE_META_KEY = 'countdown-groups-cache'
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
const syncHoldTokens = new Set()
let syncHoldSequence = 0
let syncDeferredByEditor = false
let pendingInit = null
let pendingInitWaitRemote = false

function clearState(store) {
  Object.assign(store, { ready: false, loading: false, syncing: false, listGroups: [], lists: [], deletedLists: [], tags: [], tasks: [], total: 0, undoStack: [],
    habits: [], habitCheckins: [], countdowns: [], countdownGroups: [], conflicts: [], rejected: [], pending: 0, error: '', syncError: '' })
}

function localTask(value) {
  const { id, ...rest } = value
  return { ...rest, id, publicId: id }
}

/** 只保留有值的键：调用方把字段显式写成 undefined 时，不应该覆盖库里的真实值 */
function definedOnly(source) {
  const out = {}
  for (const [key, value] of Object.entries(source || {})) if (value !== undefined) out[key] = value
  return out
}

/**
 * 列表基序：sortOrder 相同（导入/同步来的任务大量是 0）时，不能退化成
 * 「谁刚被写过谁在前」——那样编辑一次属性就会看到任务跳到最前面再弹回去。
 * 这里用 createdAt + publicId 兜底，保证同一批数据每次都排成同一个顺序。
 */
function taskBaseOrder(left, right) {
  const bySort = Number(left.sortOrder || 0) - Number(right.sortOrder || 0)
  if (bySort) return bySort
  const leftCreated = left.createdAt ? Date.parse(left.createdAt) || 0 : 0
  const rightCreated = right.createdAt ? Date.parse(right.createdAt) || 0 : 0
  if (leftCreated !== rightCreated) return leftCreated - rightCreated
  return String(left.publicId || '').localeCompare(String(right.publicId || ''))
}

async function replaceRemoteCollection(scoped, type, records) {
  const pending = new Set((await scoped.pendingOperations(SCOPE))
    .filter(item => item.entityType === type).map(item => item.entityId))
  const local = (await scoped.list(type, { bookId: SCOPE, includeDeleted: true })).filter(item => pending.has(item.id))
  await scoped.replace(type, [...records.filter(item => !pending.has(item.publicId)).map(item => ({ ...item, id: item.publicId })), ...local], { bookId: SCOPE })
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

async function assignListGroupWithRetry(id, groupId, revision, sortOrder) {
  try {
    return await apiAssignTaskListGroup(id, groupId, revision, sortOrder)
  } catch (error) {
    if (error?.response?.status !== 409) throw error
    const latest = (await apiListTaskLists()).find(item => item.publicId === id)
    if (!latest) throw error
    return apiAssignTaskListGroup(id, groupId, latest.revision, sortOrder)
  }
}

export const useTasksStore = defineStore('tasks', {
  state: () => ({
    ready: false, loading: false, saving: false, syncing: false,
    online: typeof navigator === 'undefined' ? true : navigator.onLine,
    accountScope: '', listGroups: [], lists: [], deletedLists: [], tags: [], tasks: [], habits: [], habitCheckins: [], countdowns: [],
    countdownGroups: [],
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
      syncHoldTokens.clear()
      syncDeferredByEditor = false
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
      // 并发 init（例如登录后 App 与页面同时触发）必须复用同一个 hydrate，
      // 否则第二次会关掉 sync-engine 的连接，第一次的 readAll 直接报
      // IDBDatabase「connection is closing」。
      if (pendingInit) {
        if (waitForRemote) pendingInitWaitRemote = true
        return pendingInit
      }
      pendingInitWaitRemote = waitForRemote
      this.loading = true
      pendingInit = (async () => {
        try {
          await this.hydrate()
          if (this.online && this.accountScope) {
            const refresh = this.refreshServer()
            if (pendingInitWaitRemote) await refresh
          }
        } finally {
          this.loading = false
          this.ready = true
          pendingInit = null
          pendingInitWaitRemote = false
        }
      })()
      return pendingInit
    },
    async fetch() { return this.init() },
    async hydrate() {
      const epoch = sessionEpoch
      const scoped = engine
      // 账号作用域切换会重建 engine（旧连接随之关闭），在途事务会抛
      // IDBDatabase「connection is closing」。这种竞态不是真故障：epoch 已变，
      // 数据由后一次 hydrate 负责，这里安静退出即可。
      const stale = () => epoch !== sessionEpoch || scoped !== engine
      let payload
      try {
        payload = await Promise.all([
          scoped.list('task-list', { bookId: SCOPE, includeDeleted: true }), scoped.list('task-tag', { bookId: SCOPE }),
          scoped.list('task', { bookId: SCOPE, includeDeleted: true }),
          scoped.list('habit', { bookId: SCOPE, includeDeleted: true }),
          scoped.list('habit-checkin', { bookId: SCOPE, includeDeleted: true }),
          scoped.list('countdown', { bookId: SCOPE, includeDeleted: true }),
          scoped.conflicts(SCOPE), scoped.rejected(SCOPE), scoped.pendingOperations(SCOPE),
          scoped.meta(LIST_GROUPS_CACHE_META_KEY), scoped.meta(COUNTDOWN_GROUPS_CACHE_META_KEY)
        ])
      } catch (error) {
        if (stale()) return
        throw error
      }
      if (stale()) return
      const [lists, tags, tasks, habits, habitCheckins, countdowns, conflicts, rejected, pending, cachedListGroups, cachedCountdownGroups] = payload
      this.lists = lists.filter(item => !item.deleted).map(item => ({ ...item, publicId: item.id })).sort((a, b) => a.sortOrder - b.sortOrder)
      this.deletedLists = lists.filter(item => item.deleted).map(item => ({ ...item, publicId: item.id }))
      this.tags = tags.map(item => ({ ...item, publicId: item.id })).sort((a, b) => a.sortOrder - b.sortOrder)
      // 手动排序视图依赖基序即 sortOrder 序：hydrate 必须稳定排序，否则刷新后拖动顺序回退
      this.tasks = tasks.map(localTask).sort(taskBaseOrder)
      this.habits = habits.filter(item => !item.deleted).map(localTask).sort((a, b) => a.sortOrder - b.sortOrder)
      this.habitCheckins = habitCheckins.filter(item => !item.deleted).map(localTask)
      this.countdowns = countdowns.filter(item => !item.deleted).map(localTask)
      this.total = this.tasks.length
      this.conflicts = conflicts
      this.rejected = rejected
      this.listGroups = cachedListGroups || []
      this.countdownGroups = cachedCountdownGroups || []
      this.pending = pending.length
    },
    /**
     * 只动了少数几条记录时，就地更新内存里那几条即可。全量 hydrate 要把上万条从
     * IndexedDB 重读、重排、换新数组，进而让整个列表重渲染，一次编辑要等好几秒才
     * 看到结果。遇到增删、改状态、改列表归属、改排序这类会影响分组与排序的结构性
     * 变化，仍回退到全量 hydrate 保正确性。
     */
    async applySyncChanges(changed) {
      if (!changed) return false
      const stores = Object.keys(changed).filter(name => changed[name]?.length)
      if (!stores.length) return false
      const total = stores.reduce((sum, name) => sum + changed[name].length, 0)
      if (total > INCREMENTAL_PATCH_LIMIT) return false
      // 先把快照全部读出来再改内存：中途发现一条读不到（已清理）或本地没有（新增），
      // 就整体放弃，避免改了一半留下不一致
      const pending = []
      for (const store of stores) {
        const listKey = PATCHABLE_COLLECTIONS[store]
        if (!listKey) return false
        const collection = this[listKey]
        if (!collection?.length) return false
        let records
        try {
          records = await Promise.all(changed[store].map(key => engine.read(store, key)))
        } catch {
          // 读不到就老实退回全量 hydrate，别把一次编辑弄失败
          return false
        }
        const byId = new Map(collection.map(item => [item.publicId, item]))
        const pairs = []
        for (const record of records) {
          if (!record) return false
          const next = localTask(record)
          const existing = byId.get(next.publicId)
          if (!existing) return false
          const structural = store === 'tasks'
            ? taskNeedsRebuild(existing, next)
            : Boolean(existing.deleted) !== Boolean(next.deleted)
          if (structural) return false
          pairs.push([existing, next])
        }
        pending.push(pairs)
      }
      for (const pairs of pending) {
        for (const [existing, next] of pairs) {
          for (const key of Object.keys(next)) {
            if (existing[key] !== next[key]) existing[key] = next[key]
          }
        }
      }
      return true
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
          const [listGroups, lists, deletedLists, tags, habits, countdowns, countdownGroups] = await Promise.all([
            apiListTaskListGroups(), apiListTaskLists(), apiListDeletedTaskLists(), apiListTaskTags(), apiListHabits(),
            apiListCountdowns(), apiListCountdownGroups()
          ])
          if (epoch !== sessionEpoch || scoped !== engine) return
          this.listGroups = listGroups || []
          this.countdownGroups = countdownGroups || []
          await scoped.setMeta(LIST_GROUPS_CACHE_META_KEY, this.listGroups)
          await scoped.setMeta(COUNTDOWN_GROUPS_CACHE_META_KEY, this.countdownGroups)
          await replaceRemoteCollection(scoped, 'task-list', [...(lists || []), ...(deletedLists || []).map(item => ({ ...item, deleted: true }))])
          await replaceRemoteCollection(scoped, 'task-tag', tags || [])
          await replaceRemoteCollection(scoped, 'habit', habits || [])
          await replaceRemoteCollection(scoped, 'countdown', countdowns || [])
          const habitStats = await Promise.all((habits || []).map(habit => apiGetHabitStats(habit.publicId)))
          const checkins = habitStats.flatMap(result => result?.checkins || [])
          await replaceRemoteCollection(scoped, 'habit-checkin', checkins || [])
          await this.downloadTasksWhenLocalEmpty(scoped, epoch)
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
    /**
     * 任务列表只做「本地为空」时的兜底下载。
     *
     * 任务实体的增删改由 syncNow 的 oplog 游标增量维护，每轮刷新再按 ALL/TRASH
     * 两个视图翻页重拉一遍是纯冗余：一万条任务就是 100 次串行请求、约 7 秒停顿，
     * 而拉回来的内容与本地游标结果完全一致。正常同步下没有任何网络请求。
     *
     * 留这个兜底是因为 oplog 只记录经过 TaskService 的写入：直接用 SQL 导入的历史
     * 数据没有 oplog，新设备首次登录会拉到空游标。本地已有任务时不再触发。
     */
    async downloadTasksWhenLocalEmpty(scoped, epoch) {
      if (this.tasks.length) return
      const stale = () => epoch !== sessionEpoch || scoped !== engine
      const fetchAllPages = async view => {
        const first = await apiListTasks({ view, page: 0, size: 200 })
        if (stale()) return null
        const items = [...(first?.items || [])]
        const pageCount = Math.ceil(Number(first?.total ?? items.length) / 200)
        // 之前是一页页串行等，一万条要 50 个来回；页与页之间没有依赖，按小批并发。
        for (let start = 1; start < pageCount; start += PAGE_CONCURRENCY) {
          const requests = []
          for (let page = start; page < Math.min(start + PAGE_CONCURRENCY, pageCount); page += 1) {
            requests.push(apiListTasks({ view, page, size: 200 }))
          }
          const pages = await Promise.all(requests)
          if (stale()) return null
          for (const page of pages) items.push(...(page?.items || []))
        }
        return items
      }
      const [active, trashed] = await Promise.all([fetchAllPages('ALL'), fetchAllPages('TRASH')])
      if (!active || !trashed) return
      const pendingIds = new Set((await scoped.pendingOperations(SCOPE)).map(item => item.entityId))
      const seen = new Set()
      const rows = []
      for (const item of [...active, ...trashed]) {
        if (!item?.publicId || seen.has(item.publicId) || pendingIds.has(item.publicId)) continue
        seen.add(item.publicId)
        rows.push({ ...item, id: item.publicId })
      }
      // 逐条 put 时每条各开一个事务，改成一个事务写一批。
      for (let index = 0; index < rows.length; index += LOCAL_WRITE_BATCH) {
        await scoped.putMany('task', rows.slice(index, index + LOCAL_WRITE_BATCH), { bookId: SCOPE })
      }
    },
    async saveLocal(value, operation = 'UPSERT') {
      const id = value.publicId || value.id || createClientId()
      // 详情面板/行数据有时只带「视图用到的那些字段」推回来，直接覆盖会把 createdAt
      // 这类排序依赖的字段抹成 undefined——表现就是改一次属性，任务跳到列表最前面。
      // 这里改成与库内既有实体合并，undefined 不参与覆盖（空串/空数组仍然生效）。
      const existing = this.tasks.find(item => item.publicId === id)
      const merged = existing ? { ...existing, ...definedOnly(value) } : value
      const pending = (await engine.pendingOperations(SCOPE)).find(item => item.entityId === id)
      if (operation === 'DELETE' && pending && Number(pending.baseRevision || 0) === 0) {
        await engine.removeStore('oplog', pending.opId)
        await engine.removeStore('tasks', `${SCOPE}:${id}`)
        await this.hydrate()
        return { ...merged, deleted: true }
      }
      const options = { bookId: SCOPE, opId: pending?.opId, baseRevision: merged.revision || 0 }
      const record = operation === 'DELETE'
        ? await engine.remove('task', id, options)
        : await engine.put('task', { ...merged, id, publicId: undefined }, options)
      // 改标题/备注/标签这类不动结构字段的编辑，直接就地改内存那一条；
      // 新建、删除、改状态、换清单才需要重排全量
      const patched = operation !== 'DELETE' &&
        await this.applySyncChanges({ [engine.storeName('task')]: [`${SCOPE}:${id}`] })
      if (!patched) await this.hydrate()
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
      // 习惯/打卡/倒数日都在各自集合里，改一条不该连带把上万条任务重读重渲染
      const patched = operation !== 'DELETE' &&
        await this.applySyncChanges({ [engine.storeName(type)]: [`${SCOPE}:${id}`] })
      if (!patched) await this.hydrate()
      this.scheduleSync()
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
    /** 分组是纯在线配置，不进同步 oplog；本地留一份缓存供离线显示 */
    async loadCountdownGroups() {
      if (!this.online) return this.countdownGroups
      const groups = await apiListCountdownGroups()
      this.countdownGroups = groups || []
      if (engine) await engine.setMeta(COUNTDOWN_GROUPS_CACHE_META_KEY, this.countdownGroups)
      return this.countdownGroups
    },
    async createCountdownGroup(payload) {
      const group = await apiCreateCountdownGroup(payload)
      this.countdownGroups = [...this.countdownGroups, group].sort((a, b) => a.sortOrder - b.sortOrder)
      if (engine) await engine.setMeta(COUNTDOWN_GROUPS_CACHE_META_KEY, this.countdownGroups)
      return group
    },
    async updateCountdownGroup(group, patch) {
      const saved = await apiUpdateCountdownGroup(group.publicId, {
        name: group.name, icon: group.icon, color: group.color, sortOrder: group.sortOrder, ...patch
      }, group.revision)
      this.countdownGroups = this.countdownGroups.map(item => item.publicId === saved.publicId ? saved : item)
        .sort((a, b) => a.sortOrder - b.sortOrder)
      if (engine) await engine.setMeta(COUNTDOWN_GROUPS_CACHE_META_KEY, this.countdownGroups)
      return saved
    },
    async deleteCountdownGroup(group) {
      await apiDeleteCountdownGroup(group.publicId, group.revision)
      this.countdownGroups = this.countdownGroups.filter(item => item.publicId !== group.publicId)
      // 服务端把组内卡片挪到第一个分组，本地投影同步跟上
      const fallback = this.countdownGroups[0]
      if (fallback) {
        this.countdowns = this.countdowns.map(item => item.groupId === group.publicId
          ? { ...item, groupId: fallback.publicId, kind: fallback.builtinKey || 'CUSTOM' }
          : item)
      }
      if (engine) await engine.setMeta(COUNTDOWN_GROUPS_CACHE_META_KEY, this.countdownGroups)
    },
    async create(payload) {
      this.saving = true
      try {
        const targetListId = payload.listId || this.inbox?.publicId || ''
        const targetList = this.lists.find(list => list.publicId === targetListId)
        // 条目级类型：显式传入优先，否则按所在清单类型推断（笔记清单里新建即为笔记）
        const itemKind = payload.itemKind || (targetList?.objectType === 'NOTE' ? 'NOTE' : 'TASK')
        return await this.saveLocal({ ...payload, itemKind, publicId: createClientId(), listId: targetListId,
          sortOrder: payload.sortOrder ?? this.tasks.filter(item => item.listId === (payload.listId || this.inbox?.publicId || '') && item.status === 'OPEN' && !item.deleted).length,
          status: 'OPEN', source: 'WEB', revision: 0, completedAt: null, abandonedAt: null })
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
    async complete(item) { this.undoStack.push({ item: { ...item }, label: '完成任务' }); return this.saveLocal({ ...item, status: 'COMPLETED', completedAt: new Date().toISOString(), abandonedAt: null }) },
    async abandon(item) {
      if (item.status === 'ABANDONED') return item
      this.undoStack.push({ item: { ...item }, label: '放弃任务' })
      return this.saveLocal({ ...item, status: 'ABANDONED', completedAt: null, abandonedAt: new Date().toISOString() })
    },
    async reopen(item) {
      this.undoStack.push({ item: { ...item }, label: '重新打开任务' })
      if (item.rrule && item.seriesId && this.tasks.some(value => value.seriesId === item.seriesId && value.seriesSequence > (item.seriesSequence || 0) && value.status === 'OPEN' && !value.deleted)) {
        throw new Error('周期任务的下一实例已生成，无法重开当前实例')
      }
      return this.saveLocal({ ...item, status: 'OPEN', completedAt: null, abandonedAt: null })
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
      if (!this.online) throw new Error('清单归属需要联网执行')
      const id = item.publicId || item.id
      const normalizedGroupId = groupId || null
      const current = this.lists.find(value => value.publicId === id) || item
      const epoch = sessionEpoch
      const scoped = engine
      const updated = await assignListGroupWithRetry(id, normalizedGroupId, current.revision, current.sortOrder)
      if (epoch !== sessionEpoch || scoped !== engine) return updated
      await scoped.put('task-list', { ...updated, id, publicId: undefined }, { bookId: SCOPE, recordOp: false })
      this.lists = this.lists.map(value => value.publicId === id ? updated : value)
        .sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0))
      return updated
    },
    async reorderLists(orderedLists, assignment = null) {
      const epoch = sessionEpoch
      const scoped = engine
      const items = orderedLists.filter(item => this.lists.some(list => list.publicId === item.publicId))
      const originalById = new Map(this.lists.map(list => [list.publicId, list]))
      const assignmentGroupId = assignment?.groupId || null
      const changedItems = items.filter(item => {
        const current = originalById.get(item.publicId)
        const groupId = assignment?.listId === item.publicId ? assignmentGroupId : (item.groupId || null)
        return current && (Number(current.sortOrder || 0) !== Number(item.sortOrder || 0)
          || (current.groupId || null) !== groupId)
      })
      if (changedItems.length && !this.online) throw new Error('清单排序需要联网执行')
      for (const item of changedItems) {
        const current = originalById.get(item.publicId)
        const groupId = assignment?.listId === item.publicId ? assignmentGroupId : (item.groupId || null)
        const updated = await assignListGroupWithRetry(item.publicId, groupId, current.revision, item.sortOrder)
        if (epoch !== sessionEpoch || scoped !== engine) return
        await scoped.put('task-list', { ...updated, id: item.publicId, publicId: undefined }, { bookId: SCOPE, recordOp: false })
        this.lists = this.lists.map(value => value.publicId === item.publicId ? updated : value)
          .sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0))
      }
      if (epoch !== sessionEpoch || scoped !== engine) return
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
    async purge(item, options = {}) {
      if (!this.online) throw new Error('永久清除需要联网执行')
      const epoch = sessionEpoch
      const scoped = engine
      try {
        await apiPurgeTask(item.publicId)
      } catch (error) {
        // 404 说明后端已不存在（例如上一轮清空已删掉），按清除成功继续，避免中断整批清空
        if (error?.response?.status !== 404) throw error
      }
      if (epoch !== sessionEpoch || scoped !== engine) return
      await scoped.removeStore('tasks', `${SCOPE}:${item.publicId}`)
      // 批量清空时由调用方在所有条目录完后再统一 hydrate，避免 N 条清空触发 N 次全量拉取
      if (!options.deferHydrate) await this.hydrate()
    },
    async createList(payload) {
      const groupId = payload.groupId || ''
      if (groupId && !this.online) throw new Error('在文件夹内创建清单需要联网执行')
      const { groupId: ignoredGroupId, ...listPayload } = payload
      const created = await engine.put('task-list', { ...listPayload, id: createClientId(), revision: 0,
        archived: Boolean(payload.archived), sortOrder: payload.sortOrder ?? (this.lists.reduce((max, item) => Math.max(max, Number(item.sortOrder) || 0), 0) + 1024) }, { bookId: SCOPE })
      await this.hydrate()
      if (groupId) {
        await this.syncNow()
        if ((await engine.pendingOperations(SCOPE)).some(operation => operation.entityId === created.id)) {
          throw new Error('清单尚未同步成功，请同步后再设置文件夹')
        }
        await this.assignListGroup({ ...created, publicId: created.id }, groupId)
      }
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
      syncTimer = setTimeout(() => {
        syncTimer = undefined
        if (epoch === sessionEpoch) void this.syncNow()
      }, SYNC_IDLE_DELAY_MS)
    },
    holdSync() {
      const token = `editor-${++syncHoldSequence}`
      syncHoldTokens.add(token)
      return token
    },
    releaseSyncHold(token) {
      if (!syncHoldTokens.delete(token) || syncHoldTokens.size || !syncDeferredByEditor) return
      syncDeferredByEditor = false
      queueMicrotask(() => { void this.syncNow() })
    },
    async syncNow() {
      clearTimeout(syncTimer)
      syncTimer = undefined
      if (!this.online || !this.accountScope) return
      if (syncHoldTokens.size) {
        syncDeferredByEditor = true
        return
      }
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
            if (syncHoldTokens.size) {
              syncDeferredByEditor = true
              break
            }
            if (activeSyncPromise?.promise === promise) activeSyncPromise.rerun = false
            await reconcileSafeListConflicts(scoped)
            if (syncHoldTokens.size) {
              syncDeferredByEditor = true
              break
            }
            result = await scoped.sync(SCOPE)
            if (result?.resetRequired) result = await scoped.sync(SCOPE)
            const reconciliation = await reconcileSafeListConflicts(scoped)
            if (reconciliation.requeued && reconcilePass < 1) {
              reconcilePass += 1
              if (activeSyncPromise?.promise === promise) activeSyncPromise.rerun = true
              continue
            }
            // 这里原本还有一次 hydrate()，循环末尾又会再跑一次。同步一轮要重读两遍
            // 全库（上万条）并触发两次全列表重渲染，改成只在循环结束后刷新一次。
            if (syncHoldTokens.size && activeSyncPromise?.rerun) {
              activeSyncPromise.rerun = false
              syncDeferredByEditor = true
            }
          } while (epoch === sessionEpoch && scoped === engine && activeSyncPromise?.rerun && !syncHoldTokens.size)
          if (epoch === sessionEpoch && scoped === engine) {
            // 能增量就直接改那几条，省掉一次上万条的重读 + 全列表重渲染。
            // 但冲突/驳回数量变了必须走全量，否则冲突提示会停在旧状态。
            const patched = await this.applySyncChanges(result?.changed)
            const conflictsDrifted = Number(result?.conflicts ?? 0) !== this.conflicts.length ||
              Number(result?.rejected ?? 0) !== this.rejected.length
            if (patched && !conflictsDrifted) {
              if (typeof result?.pending === 'number') this.pending = result.pending
            } else {
              await this.hydrate()
            }
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
          if (activeSyncPromise?.promise === promise) {
            const rerun = activeSyncPromise.rerun
            this.syncing = false
            activeSyncPromise = null
            if (rerun) {
              if (syncHoldTokens.size) syncDeferredByEditor = true
              else queueMicrotask(() => { void this.syncNow() })
            }
          }
        }
      })
      activeSyncPromise = { epoch, promise, rerun: false }
      return promise
    },
    async resolveConflict(opId, strategy) {
      await engine.resolveConflict(opId, strategy)
      await this.hydrate()
      this.scheduleSync()
    }
  }
})
