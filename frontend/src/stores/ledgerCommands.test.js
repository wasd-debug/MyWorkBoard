import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { api } from '../api/index.js'
import { useLedgerStore } from './ledger.js'

function response(config, data) {
  return { data: { data }, status: 200, statusText: 'OK', headers: {}, config }
}

async function createOfflineLedger(t) {
  const values = new Map()
  globalThis.localStorage = {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key)
  }
  setActivePinia(createPinia())
  const store = useLedgerStore()
  await store.switchUser({ id: 91, username: 'offline-user' })
  store.currentBookId = 'book-offline'
  store.books = [{ id: 'book-offline', name: 'Offline' }]
  store.online = false
  t.after(async () => { await store.clearSession() })
  return store
}

test('transaction command writes local projection and oplog before network', async t => {
  const store = await createOfflineLedger(t)

  const created = await store.saveTransaction({
    kind: 'EXPENSE',
    amount: 28.5,
    occurredOn: '2026-09-17',
    accountId: 'account-1',
    categoryId: 'category-1',
    note: 'offline lunch'
  })

  assert.match(created.id, /^[0-9a-f-]{36}$/)
  assert.equal(store.transactions.length, 1)
  assert.equal(store.transactions[0].note, 'offline lunch')
  assert.equal(store.pending, 1)

  await store.deleteTransaction(created)

  assert.deepEqual(store.transactions, [])
  assert.equal(store.pending, 2)
})

test('all offline ledger resources write projections and oplog entries', async t => {
  const store = await createOfflineLedger(t)
  const resources = [
    ['account', { name: '现金', accountType: 'cash', currency: 'CNY' }, 'accounts'],
    ['category', { name: '餐饮', kind: 'EXPENSE' }, 'categories'],
    ['merchant', { name: '街角餐厅' }, 'merchants'],
    ['project', { name: '日常' }, 'projects'],
    ['budget', { monthKey: '2026-09', amount: 1200 }, 'budgets']
  ]

  for (const [type, payload, stateKey] of resources) {
    const created = await store.saveResource(type, payload)
    assert.match(created.id, /^[0-9a-f-]{36}$/)
    assert.equal(store[stateKey].some(item => item.id === created.id), true)
  }
  assert.equal(store.pending, resources.length)

  for (const [type, , stateKey] of resources) {
    await store.deleteResource(type, store[stateKey][0])
    assert.equal(store[stateKey].length, 0)
  }
  assert.equal(store.pending, resources.length * 2)
})

test('offline resource facade rejects online-only entity types', async t => {
  const store = await createOfflineLedger(t)

  await assert.rejects(
    store.saveResource('member', { username: 'alice' }),
    error => error.code === 'ONLINE_REQUIRED'
  )
  assert.equal(store.pending, 0)
})

test('online command facade fails explicitly while offline without creating oplog', async t => {
  const store = await createOfflineLedger(t)

  await assert.rejects(
    store.saveMember({ username: 'alice', roleId: 'member-role' }),
    error => error.code === 'ONLINE_REQUIRED'
  )
  await assert.rejects(
    store.runScheduledTask({ id: 'task-1' }),
    error => error.code === 'ONLINE_REQUIRED'
  )

  assert.equal(store.pending, 0)
})

test('refreshServer hydrates resources when a persisted current book is still valid', async t => {
  const store = await createOfflineLedger(t)
  const originalAdapter = api.defaults.adapter
  store.online = true
  api.defaults.adapter = async config => {
    if (config.url === '/v1/ledger/books') {
      return response(config, [{ id: 'book-offline', name: 'Offline', roleCode: 'OWNER' }])
    }
    if (config.url.endsWith('/accounts')) {
      return response(config, [{ id: 'account-1', name: '现金', accountType: 'cash', revision: 1 }])
    }
    if (config.url.endsWith('/categories') || config.url.endsWith('/merchants') ||
        config.url.endsWith('/members') || config.url.endsWith('/projects') ||
        config.url.endsWith('/roles') || config.url.endsWith('/budgets')) {
      return response(config, [])
    }
    if (config.url.endsWith('/sync/push')) return response(config, { results: [] })
    if (config.url.endsWith('/sync/pull')) return response(config, { operations: [], cursor: 0, hasMore: false })
    throw new Error(`unexpected request: ${config.method} ${config.url}`)
  }
  t.after(() => { api.defaults.adapter = originalAdapter })

  await store.refreshServer()

  assert.equal(store.accounts.length, 1)
  assert.equal(store.accounts[0].name, '现金')
})
