const DEFAULT_DB = 'salary-tracker-sync-v2'
const DEFAULT_STORES = [
  'ledger-books',
  'ledger-accounts',
  'ledger-categories',
  'ledger-merchants',
  'ledger-members',
  'ledger-projects',
  'ledger-roles',
  'ledger-budgets',
  'ledger-transactions'
]

export class SyncEngine {
  constructor({ dbName = DEFAULT_DB, stores = DEFAULT_STORES, transport = {}, entityPrefix = 'ledger' } = {}) {
    this.dbName = dbName
    this.stores = stores
    this.transport = transport
    this.entityPrefix = entityPrefix
    this.memory = new Map([...stores, 'oplog', 'meta', 'conflicts', 'rejected'].map(store => [store, new Map()]))
    this.dbPromise = null
  }

  async open() {
    if (this.dbPromise) return this.dbPromise
    if (typeof indexedDB === 'undefined') return null
    this.dbPromise = new Promise((resolve, reject) => {
      const request = indexedDB.open(this.dbName, 2)
      request.onupgradeneeded = () => {
        const db = request.result
        for (const store of [...this.stores, 'oplog', 'meta', 'conflicts', 'rejected']) {
          if (!db.objectStoreNames.contains(store)) db.createObjectStore(store, { keyPath: 'key' })
        }
      }
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    }).catch(() => null)
    return this.dbPromise
  }

  async close() {
    const db = await this.dbPromise
    db?.close?.()
    this.dbPromise = null
  }

  async put(entityType, value, options = {}) {
    const safeValue = cloneSerializable(value)
    const entityStore = this.storeName(entityType)
    const entityId = String(safeValue.id ?? randomId())
    const bookId = String(options.bookId || safeValue.bookId || (singular(entityType) === 'book' ? entityId : 'default'))
    const opId = options.opId || randomId()
    const record = {
      ...safeValue,
      id: entityId,
      bookId,
      key: entityKey(bookId, entityId),
      updatedAt: safeValue.updatedAt ?? new Date().toISOString()
    }
    // base 是「这次改动之前、最后一次与服务端对齐的状态」，冲突时用它做三段合并：
    // 只把本地真正改过的字段盖到服务端版本上，服务端另改的字段保留，能合就自动重推。
    const pending = options.recordOp === false ? null : await this.read('oplog', opId)
    const previous = pending ? null : await this.read(entityStore, record.key)
    const base = pending?.base ?? (previous ? stripLocal(previous) : null)
    await this.write(entityStore, record)
    if (options.recordOp !== false) {
      await this.write('oplog', {
        key: opId,
        id: opId,
        opId,
        bookId,
        entityType: singular(entityType),
        entityId,
        operation: options.operation || 'UPSERT',
        baseRevision: Number(options.baseRevision ?? safeValue.revision ?? 0),
        base,
        payload: stripLocal(record),
        createdAt: pending?.createdAt || new Date().toISOString()
      })
    }
    return stripLocal(record)
  }

  async remove(entityType, id, options = {}) {
    const record = await this.get(entityType, id, { bookId: options.bookId })
    return this.put(entityType, {
      ...(record || { id: String(id), bookId: options.bookId }),
      deleted: true,
      deletedAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }, {
      ...options,
      operation: 'DELETE',
      baseRevision: options.baseRevision ?? record?.revision ?? 0
    })
  }

  async get(entityType, id, { bookId } = {}) {
    if (bookId) return stripLocal(await this.read(this.storeName(entityType), entityKey(bookId, id)))
    const records = await this.readAll(this.storeName(entityType))
    return stripLocal(records.find(record => String(record.id) === String(id)))
  }

  async list(entityType, { bookId, includeDeleted = false } = {}) {
    return (await this.readAll(this.storeName(entityType)))
      .filter(record => !bookId || String(record.bookId) === String(bookId))
      .filter(record => includeDeleted || !record.deleted)
      .sort((a, b) => String(b.updatedAt || '').localeCompare(String(a.updatedAt || '')))
      .map(stripLocal)
  }

  async replace(entityType, values, { bookId } = {}) {
    const store = this.storeName(entityType)
    const current = await this.readAll(store)
    await Promise.all(current
      .filter(item => !bookId || String(item.bookId) === String(bookId))
      .map(item => this.removeStore(store, item.key)))
    for (const value of values || []) {
      await this.put(entityType, value, { bookId: bookId || value.bookId, recordOp: false })
    }
    return this.list(entityType, { bookId, includeDeleted: true })
  }

  async pendingOperations(bookId) {
    return (await this.readAll('oplog'))
      .filter(item => !bookId || String(item.bookId) === String(bookId))
      .map(stripLocal)
  }

  async conflicts(bookId) {
    return (await this.readAll('conflicts'))
      .filter(item => !bookId || String(item.bookId) === String(bookId))
      .map(stripLocal)
  }

  async rejected(bookId) {
    return (await this.readAll('rejected'))
      .filter(item => !bookId || String(item.bookId) === String(bookId))
      .map(stripLocal)
  }

  async sync(bookId) {
    if (!bookId) throw new Error('bookId is required')
    let pushed = 0
    let rebuildAttempts = 0
    // 旧版本留下的存量冲突也先试着自动修复，能合的直接放回队列重推
    await this.rebaseStoredConflicts(bookId)
    let pending = await this.pendingOperations(bookId)
    // 冲突优先自动重推一次：把本地改动的字段合到服务端新版本上，多数「版本已变化」能被吃掉，
    // 用户不再看到冲突；真的同字段互相覆盖时才留冲突记录交给人工处理。
    while (pending.length && this.transport.push && rebuildAttempts < 2) {
      rebuildAttempts += 1
      let retried = false
      const response = await this.transport.push(bookId, pending)
      const byId = new Map(pending.map(operation => [operation.opId, operation]))
      for (const result of response?.results || []) {
        const operation = byId.get(result.opId)
        if (!operation) continue
        if (result.status === 'APPLIED' || result.status === 'DUPLICATE') {
          await this.removeStore('oplog', operation.opId)
          await this.removeStore('conflicts', operation.opId)
          if (result.entity) {
            // 服务端结果里若没带 entityType/entityId，用本地操作补齐，避免写错存储
            await this.merge({
              ...result,
              entityType: result.entityType || operation.entityType,
              entityId: result.entityId || operation.entityId,
              operation: 'UPSERT',
              payload: result.entity
            }, bookId)
          }
          pushed++
        } else if (result.status === 'CONFLICT') {
          const rebase = await this.tryRebase(operation, result, bookId)
          if (rebase === 'retry') {
            retried = true
            continue
          }
          if (rebase === 'resolved') {
            pushed++
            continue
          }
          await this.write('conflicts', {
            key: operation.opId,
            id: operation.opId,
            bookId,
            operation,
            result,
            createdAt: new Date().toISOString()
          })
        } else if (result.status === 'FORBIDDEN' || result.status === 'REJECTED') {
          await this.write('rejected', {
            key: operation.opId,
            id: operation.opId,
            bookId,
            operation,
            result,
            createdAt: new Date().toISOString()
          })
          await this.removeStore('oplog', operation.opId)
        }
      }
      if (!retried) break
      pending = await this.pendingOperations(bookId)
    }

    let pulled = 0
    let cursor = Number(await this.meta(`cursor:${bookId}`) || 0)
    if (this.transport.pull) {
      try {
        let hasMore = true
        const mergeCaches = new Map()
        while (hasMore) {
          const response = await this.transport.pull(bookId, cursor)
          const operations = response?.operations || []
          await this.mergeBatch(operations, bookId, mergeCaches)
          for (const operation of operations) {
            cursor = Math.max(cursor, Number(operation.cursor || 0))
            pulled++
          }
          cursor = Math.max(cursor, Number(response?.cursor || 0))
          hasMore = Boolean(response?.hasMore)
        }
        await this.setMeta(`cursor:${bookId}`, cursor)
      } catch (error) {
        if (error?.response?.status !== 410 && error?.code !== 'SYNC_RESET_REQUIRED') throw error
        await this.resetBook(bookId)
        await this.setMeta(`cursor:${bookId}`, 0)
        return {
          pushed,
          pulled,
          cursor: 0,
          resetRequired: true,
          pending: (await this.pendingOperations(bookId)).length
        }
      }
    }
    return {
      pushed,
      pulled,
      cursor,
      pending: (await this.pendingOperations(bookId)).length,
      conflicts: (await this.conflicts(bookId)).length,
      rejected: (await this.rejected(bookId)).length
    }
  }

  async merge(operation, bookId = operation.bookId) {
    return (await this.mergeBatch([operation], bookId))[0]
  }

  async mergeBatch(operations, bookId, caches = new Map()) {
    const groups = new Map()
    const results = new Array(operations.length)
    operations.forEach((operation, index) => {
      const store = this.storeName(operation.entityType || 'transaction')
      if (!groups.has(store)) groups.set(store, [])
      groups.get(store).push({ operation, index })
    })
    await Promise.all([...groups.entries()].map(async ([store, entries]) => {
      let cache = caches.get(store)
      if (!cache) {
        cache = new Map((await this.readAll(store)).map(record => [String(record.key), record]))
        caches.set(store, cache)
      }
      const writes = new Map()
      for (const { operation, index } of entries) {
        const entityId = String(operation.entityId ?? operation.payload?.id)
        const scope = String(bookId || operation.bookId || operation.payload?.bookId || 'default')
        const key = entityKey(scope, entityId)
        const current = cache.get(key)
        const incoming = {
          ...(operation.payload || {}),
          ...(this.entityPrefix === 'task' ? operation.payload?.extra || {} : {}),
          id: entityId,
          bookId: scope,
          key
        }
        // 服务端 oplog 快照是投影，可能缺字段（任务实体就没有 createdAt / abandonedAt /
        // itemKind）。整条覆盖会让本地实体变成残缺对象——排序兜底字段一丢，刚编辑过的
        // 任务就会跳到列表最前面。这里以本地已有值为底，服务端下发的字段覆盖上去。
        const payload = current ? { ...stripLocal(current), ...incoming } : incoming
        if (operation.operation === 'PURGE') {
          cache.delete(payload.key)
          writes.delete(payload.key)
          await this.removeStore(store, payload.key)
          results[index] = null
          continue
        }
        if (current && Number(current.revision || 0) > Number(payload.revision || 0)) {
          results[index] = stripLocal(current)
          continue
        }
        if (operation.operation === 'DELETE' || payload.deleted) payload.deleted = true
        cache.set(payload.key, payload)
        writes.set(payload.key, payload)
        results[index] = stripLocal(payload)
      }
      await this.writeMany(store, [...writes.values()])
    }))
    return results
  }

  /**
   * 冲突自动修复：服务端版本变了，但本地改动和服务端改动不落在同一批字段上时，
   * 直接把本地改动的字段合到服务端最新版本、用新 revision 重推，不再打扰用户。
   * 返回 'retry' 表示已改写 oplog（下次 push 会重试），'resolved' 表示已采纳服务端并清理，
   * false 表示只能留冲突。
   */
  async tryRebase(operation, result, bookId) {
    const server = result?.serverEntity
    const serverRevision = Number(result?.serverRevision ?? server?.revision ?? 0)
    if (!server || !serverRevision) return false
    if (operation.operation === 'DELETE') {
      // 删除是意图明确的整体操作，直接把基线挪到服务端最新版本再删一次即可
      await this.write('oplog', { ...operation, baseRevision: serverRevision })
      return 'retry'
    }
    const local = operation.payload || {}
    if (!local || !Object.keys(local).length) return false
    const serverFlat = this.flattenEntity(server)
    const merged = rebasePayload(operation.base || null, local, serverFlat)
    if (!merged) return false
    if (!merged.touched) {
      // 本地没有实质改动，只是拿着旧 revision：直接采纳服务端版本，不再重推
      await this.put(operation.entityType, { ...serverFlat, bookId: operation.bookId }, {
        bookId: operation.bookId,
        recordOp: false
      })
      await this.removeStore('oplog', operation.opId)
      return 'resolved'
    }
    const payload = { ...merged.merged, id: operation.entityId, revision: serverRevision }
    await this.put(operation.entityType, { ...payload, bookId: operation.bookId }, {
      bookId: operation.bookId,
      recordOp: false
    })
    await this.write('oplog', { ...operation, payload, baseRevision: serverRevision })
    return 'retry'
  }

  /** 服务端实体在任务域是「扁平字段 + extra」，统一摊平成一份便于逐字段比较 */
  flattenEntity(entity) {
    if (!entity) return {}
    if (this.entityPrefix === 'task' && entity.extra && typeof entity.extra === 'object') {
      return { ...entity, ...entity.extra }
    }
    return { ...entity }
  }

  /** 把库里已经存在的冲突挨个尝试自动合并，能合的直接回到待推送队列 */
  async rebaseStoredConflicts(bookId) {
    const stored = await this.conflicts(bookId)
    for (const conflict of stored) {
      const operation = conflict.operation
      if (!operation?.opId) continue
      try {
        const rebase = await this.tryRebase(operation, conflict.result, conflict.bookId || bookId)
        if (rebase) await this.removeStore('conflicts', operation.opId)
      } catch {
        // 合不了就保留，交给用户按原有流程处理
      }
    }
  }

  async resolveConflict(opId, strategy, mergedPayload) {
    const conflict = await this.read('conflicts', String(opId))
    if (!conflict) return null
    const original = conflict.operation
    const pending = await this.read('oplog', String(opId))
    const operation = pending || original
    const server = conflict.result?.serverEntity
    const serverRevision = Number(conflict.result?.serverRevision ?? server?.revision ?? 0)
    if (strategy === 'server') {
      if (server) {
        await this.put(operation.entityType, {
          ...server,
          ...(this.entityPrefix === 'task' ? server.extra || {} : {}),
          id: operation.entityId,
          revision: serverRevision
        }, {
          bookId: conflict.bookId,
          recordOp: false
        })
      }
      await this.removeStore('oplog', opId)
    } else {
      const localPayload = operation.payload || original.payload || {}
      const payload = strategy === 'merged'
        ? { ...server, ...(this.entityPrefix === 'task' ? server?.extra || {} : {}), ...mergedPayload }
        : { ...server, ...(this.entityPrefix === 'task' ? server?.extra || {} : {}), ...localPayload }
      payload.id = operation.entityId
      if (serverRevision) payload.revision = serverRevision
      if (server) {
        await this.put(operation.entityType, payload, {
          bookId: conflict.bookId,
          recordOp: false
        })
      }
      await this.write('oplog', {
        ...operation,
        key: operation.opId,
        payload,
        baseRevision: serverRevision
      })
    }
    await this.removeStore('conflicts', opId)
    return true
  }

  async resetBook(bookId) {
    for (const store of this.stores) {
      const records = await this.readAll(store)
      await Promise.all(records
        .filter(item => String(item.bookId) === String(bookId))
        .map(item => this.removeStore(store, item.key)))
    }
  }

  async exportRejected(bookId) {
    const rejected = await this.rejected(bookId)
    return JSON.stringify({
      exportedAt: new Date().toISOString(),
      bookId,
      operations: rejected.map(item => item.operation),
      rejections: rejected.map(item => ({
        opId: item.operation?.opId || item.id,
        entityType: item.operation?.entityType,
        entityId: item.operation?.entityId,
        status: item.result?.status || 'REJECTED',
        message: item.result?.message || '',
        operation: item.operation
      }))
    }, null, 2)
  }

  storeName(entityType) {
    const normalized = singular(entityType)
    if (this.entityPrefix === 'task') {
      if (normalized === 'task-list') return 'task-lists'
      if (normalized === 'task-tag') return 'task-tags'
      if (normalized === 'habit') return 'habits'
      if (normalized === 'habit-checkin') return 'habit-checkins'
      if (normalized === 'countdown') return 'countdowns'
      return 'tasks'
    }
    if (normalized === 'category') return 'ledger-categories'
    return `ledger-${normalized}s`
  }

  async meta(key) {
    return (await this.read('meta', String(key)))?.value
  }

  async setMeta(key, value) {
    return this.write('meta', { key: String(key), id: String(key), value })
  }

  async read(store, key) {
    const db = await this.open()
    if (!db) return this.memory.get(store)?.get(String(key))
    return new Promise(resolve => {
      const request = db.transaction(store, 'readonly').objectStore(store).get(String(key))
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => resolve(undefined)
    })
  }

  async readAll(store) {
    const db = await this.open()
    if (!db) return [...(this.memory.get(store)?.values() || [])]
    return new Promise(resolve => {
      const request = db.transaction(store, 'readonly').objectStore(store).getAll()
      request.onsuccess = () => resolve(request.result || [])
      request.onerror = () => resolve([])
    })
  }

  async write(store, value) {
    const safeValue = cloneSerializable(value)
    const record = { ...safeValue, key: String(safeValue.key ?? safeValue.id) }
    const db = await this.open()
    if (!db) {
      if (!this.memory.has(store)) this.memory.set(store, new Map())
      this.memory.get(store).set(record.key, record)
      return record
    }
    return new Promise((resolve, reject) => {
      const tx = db.transaction(store, 'readwrite')
      tx.objectStore(store).put(record)
      tx.oncomplete = () => resolve(record)
      tx.onerror = () => reject(tx.error)
    })
  }

  async writeMany(store, values) {
    const records = values.map(value => {
      const safeValue = cloneSerializable(value)
      return { ...safeValue, key: String(safeValue.key ?? safeValue.id) }
    })
    if (!records.length) return []
    const db = await this.open()
    if (!db) {
      if (!this.memory.has(store)) this.memory.set(store, new Map())
      const target = this.memory.get(store)
      records.forEach(record => target.set(record.key, record))
      return records
    }
    return new Promise((resolve, reject) => {
      const tx = db.transaction(store, 'readwrite')
      const objectStore = tx.objectStore(store)
      records.forEach(record => objectStore.put(record))
      tx.oncomplete = () => resolve(records)
      tx.onerror = () => reject(tx.error)
    })
  }

  async removeStore(store, key) {
    const db = await this.open()
    if (!db) {
      this.memory.get(store)?.delete(String(key))
      return
    }
    return new Promise(resolve => {
      const tx = db.transaction(store, 'readwrite')
      tx.objectStore(store).delete(String(key))
      tx.oncomplete = () => resolve()
    })
  }
}

function singular(entityType) {
  let value = String(entityType || '').replace(/^ledger-/, '').toLowerCase()
  if (value.endsWith('ies')) return `${value.slice(0, -3)}y`
  if (value.endsWith('s')) value = value.slice(0, -1)
  return value
}

function entityKey(bookId, id) {
  return `${bookId}:${id}`
}

/** 合并时忽略的元字段：它们由引擎/服务端自己维护 */
const META_FIELDS = new Set(['id', 'key', 'bookId', 'updatedAt', 'revision', 'deleted', 'deletedAt'])

function sameValue(left, right) {
  if (left === right) return true
  if (left == null || right == null) return left == null && right == null
  if (typeof left !== 'object' || typeof right !== 'object') return false
  return JSON.stringify(left) === JSON.stringify(right)
}

/**
 * 三段合并：base=上次与服务端对齐的状态，local=本地快照，server=服务端新版本。
 * 只把本地真正改过、且服务端没动过的字段写回；两边都改了同一个字段就放弃（返回 null）。
 * 返回 { merged, touched }，touched=0 表示本地其实什么也没改（只是 revision 过期）。
 */
function rebasePayload(base, local, server) {
  if (!base || !server) return null
  const merged = { ...server }
  let touched = 0
  for (const [key, value] of Object.entries(local)) {
    if (META_FIELDS.has(key)) continue
    if (sameValue(value, base[key])) continue
    // 服务端把这个字段改成了第三个值 → 真冲突，交给人工
    if (!sameValue(server[key], base[key]) && !sameValue(server[key], value)) return null
    merged[key] = value
    touched += 1
  }
  return { merged, touched }
}

function stripLocal(value) {
  if (!value) return value
  const result = cloneSerializable(value)
  delete result.key
  return result
}

function cloneSerializable(value, seen = new WeakMap()) {
  if (value === null || value === undefined) return value
  if (typeof value === 'function' || typeof value === 'symbol') return undefined
  if (typeof value !== 'object') return value
  if (value instanceof Date) return value.toISOString()
  if (seen.has(value)) return seen.get(value)
  const result = Array.isArray(value) ? [] : {}
  seen.set(value, result)
  for (const [key, child] of Object.entries(value)) {
    const cloned = cloneSerializable(child, seen)
    if (cloned !== undefined) result[key] = cloned
  }
  return result
}

function randomId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function createLedgerSyncEngine(options = {}) {
  if ('transport' in options || 'dbName' in options || 'stores' in options) return new SyncEngine(options)
  return new SyncEngine({ transport: options })
}

export function createTaskSyncEngine(options = {}) {
  return new SyncEngine({
    dbName: options.dbName || 'salary-tracker-task-sync:anonymous',
    stores: options.stores || ['task-lists', 'task-tags', 'tasks', 'habits', 'habit-checkins', 'countdowns'],
    transport: options.transport || {},
    entityPrefix: 'task'
  })
}
