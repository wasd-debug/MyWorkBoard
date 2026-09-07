const DEFAULT_DB = 'salary-tracker-sync-v1'
const DEFAULT_STORES = ['ledger-accounts', 'ledger-categories', 'ledger-transactions', 'ledger-recurring']

export class SyncEngine {
  constructor({ dbName = DEFAULT_DB, stores = DEFAULT_STORES, transport = {} } = {}) {
    this.dbName = dbName
    this.stores = stores
    this.transport = transport
    this.memory = new Map(stores.map(store => [store, new Map()]))
    this.memory.set('oplog', new Map())
    this.memory.set('meta', new Map())
    this.dbPromise = null
  }

  async open() {
    if (this.dbPromise) return this.dbPromise
    if (typeof indexedDB === 'undefined') return null
    this.dbPromise = new Promise((resolve, reject) => {
      const request = indexedDB.open(this.dbName, 1)
      request.onupgradeneeded = () => {
        const db = request.result
        for (const store of [...this.stores, 'oplog', 'meta']) {
          if (!db.objectStoreNames.contains(store)) db.createObjectStore(store, { keyPath: 'id' })
        }
      }
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    }).catch(() => null)
    return this.dbPromise
  }

  async put(entityType, value, { opId = cryptoRandom(), operation = 'UPSERT', recordOp = true } = {}) {
    const entityStore = this.storeName(entityType)
    const record = { ...value, id: String(value.id ?? cryptoRandom()), updatedAt: value.updatedAt ?? new Date().toISOString() }
    await this.write(entityStore, record)
    if (recordOp) await this.write('oplog', { id: opId, opId, entityType, entityId: record.id, operation, payload: record, createdAt: new Date().toISOString() })
    return record
  }

  async remove(entityType, id, { opId = cryptoRandom() } = {}) {
    const record = await this.get(entityType, id)
    const deleted = { ...(record || { id: String(id) }), id: String(id), deleted: true, updatedAt: new Date().toISOString() }
    return this.put(entityType, deleted, { opId, operation: 'DELETE' })
  }

  async get(entityType, id) { return this.read(this.storeName(entityType), String(id)) }
  async list(entityType, { includeDeleted = false } = {}) {
    const records = await this.readAll(this.storeName(entityType))
    return records.filter(record => includeDeleted || !record.deleted).sort((a, b) => String(b.updatedAt || '').localeCompare(String(a.updatedAt || '')))
  }
  async pendingOperations() { return this.readAll('oplog') }

  async sync() {
    const push = this.transport.push
    const pull = this.transport.pull
    let pushed = 0
    const pending = await this.pendingOperations()
    if (pending.length && push) {
      const result = await push(pending)
      pushed = Number(result?.accepted ?? pending.length)
      if (pushed > 0) await Promise.all(pending.slice(0, pushed).map(operation => this.removeStore('oplog', operation.id)))
    }
    let pulled = 0
    let cursor = Number(await this.meta('cursor') || 0)
    if (pull) {
      const result = await pull(cursor)
      for (const operation of result?.operations || []) {
        await this.merge(operation)
        cursor = Math.max(cursor, Number(operation.cursor || 0))
        pulled++
      }
      await this.setMeta('cursor', cursor)
    }
    return { pushed, pulled, cursor, pending: (await this.pendingOperations()).length }
  }

  async merge(operation) {
    const entityType = operation.entityType || 'transaction'
    const payload = { ...(operation.payload || {}), id: String(operation.entityId ?? operation.payload?.id) }
    const store = this.storeName(entityType)
    let current = await this.get(entityType, payload.id)
    if (!current && payload.clientOpId) {
      const candidates = await this.readAll(store)
      const local = candidates.find(item => item.clientOpId && item.clientOpId === payload.clientOpId)
      if (local) {
        current = local
        if (String(local.id) !== payload.id) await this.removeStore(store, local.id)
      }
    }
    const currentVersion = Number(current?.revision || 0)
    const incomingVersion = Number(payload.revision || 0)
    if (current && currentVersion > incomingVersion) return current
    if (operation.operation === 'DELETE' || payload.deleted) payload.deleted = true
    await this.write(store, payload)
    return payload
  }

  storeName(entityType) {
    const normalized = entityType.replace(/^ledger-/, '')
    if (normalized === 'category') return 'ledger-categories'
    if (normalized === 'transaction') return 'ledger-transactions'
    if (normalized === 'account') return 'ledger-accounts'
    if (normalized === 'recurring') return 'ledger-recurring'
    return `ledger-${normalized}s`
  }
  async meta(key) { const value = await this.read('meta', key); return value?.value }
  async setMeta(key, value) { return this.write('meta', { id: key, value }) }

  async read(store, id) {
    const db = await this.open()
    if (!db) return this.memory.get(store)?.get(String(id))
    return new Promise(resolve => { const tx = db.transaction(store, 'readonly'); const request = tx.objectStore(store).get(String(id)); request.onsuccess = () => resolve(request.result); request.onerror = () => resolve(undefined) })
  }
  async readAll(store) {
    const db = await this.open()
    if (!db) return [...(this.memory.get(store)?.values() || [])]
    return new Promise(resolve => { const tx = db.transaction(store, 'readonly'); const request = tx.objectStore(store).getAll(); request.onsuccess = () => resolve(request.result || []); request.onerror = () => resolve([]) })
  }
  async write(store, value) {
    const db = await this.open()
    if (!db) { if (!this.memory.has(store)) this.memory.set(store, new Map()); this.memory.get(store).set(String(value.id), value); return value }
    return new Promise((resolve, reject) => { const tx = db.transaction(store, 'readwrite'); tx.objectStore(store).put(value); tx.oncomplete = () => resolve(value); tx.onerror = () => reject(tx.error) })
  }
  async removeStore(store, id) {
    const db = await this.open()
    if (!db) { this.memory.get(store)?.delete(String(id)); return }
    return new Promise(resolve => { const tx = db.transaction(store, 'readwrite'); tx.objectStore(store).delete(String(id)); tx.oncomplete = () => resolve() })
  }
}

function cryptoRandom() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function createLedgerSyncEngine(transport) { return new SyncEngine({ transport }) }
