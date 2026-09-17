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
  constructor({ dbName = DEFAULT_DB, stores = DEFAULT_STORES, transport = {} } = {}) {
    this.dbName = dbName
    this.stores = stores
    this.transport = transport
    this.memory = new Map([...stores, 'oplog', 'meta', 'conflicts', 'rejected'].map(store => [store, new Map()]))
    this.dbPromise = null
  }

  async open() {
    if (this.dbPromise) return this.dbPromise
    if (typeof indexedDB === 'undefined') return null
    this.dbPromise = new Promise((resolve, reject) => {
      const request = indexedDB.open(this.dbName, 1)
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
        payload: stripLocal(record),
        createdAt: new Date().toISOString()
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
    const pending = await this.pendingOperations(bookId)
    if (pending.length && this.transport.push) {
      const response = await this.transport.push(bookId, pending)
      const byId = new Map(pending.map(operation => [operation.opId, operation]))
      for (const result of response?.results || []) {
        const operation = byId.get(result.opId)
        if (!operation) continue
        if (result.status === 'APPLIED' || result.status === 'DUPLICATE') {
          await this.removeStore('oplog', operation.opId)
          if (result.entity) {
            await this.merge({ ...result, operation: 'UPSERT', payload: result.entity }, bookId)
          }
          pushed++
        } else if (result.status === 'CONFLICT') {
          await this.write('conflicts', {
            key: operation.opId,
            id: operation.opId,
            bookId,
            operation,
            result,
            createdAt: new Date().toISOString()
          })
        } else if (result.status === 'FORBIDDEN') {
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
        const payload = {
          ...(operation.payload || {}),
          id: entityId,
          bookId: scope,
          key: entityKey(scope, entityId)
        }
        const current = cache.get(payload.key)
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

  async resolveConflict(opId, strategy, mergedPayload) {
    const conflict = await this.read('conflicts', String(opId))
    if (!conflict) return null
    if (strategy === 'server') {
      const server = conflict.result?.serverEntity
      if (server) {
        await this.put(conflict.operation.entityType, server, {
          bookId: conflict.bookId,
          recordOp: false
        })
      }
      await this.removeStore('oplog', opId)
    } else {
      const payload = strategy === 'merged' ? mergedPayload : conflict.operation.payload
      await this.write('oplog', {
        ...conflict.operation,
        key: conflict.operation.opId,
        payload,
        baseRevision: conflict.result?.serverRevision || 0
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
    return JSON.stringify({
      exportedAt: new Date().toISOString(),
      bookId,
      operations: (await this.rejected(bookId)).map(item => item.operation)
    }, null, 2)
  }

  storeName(entityType) {
    const normalized = singular(entityType)
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
