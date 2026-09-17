import assert from 'node:assert/strict'
import test from 'node:test'
import { accountScopeFor, currentLedgerBookStorageKey, ledgerDatabaseName, scopedStorageKey } from './accountScope.js'
import { createClientId } from './clientId.js'

test('无 randomUUID 的普通 HTTP 环境仍生成标准 UUID', () => {
  const id = createClientId({ getRandomValues: bytes => bytes.fill(7) })
  assert.match(id, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
})

test('用户作用域优先使用不可变用户 ID', () => {
  assert.equal(accountScopeFor({ id: 12, username: 'Alice' }), 'id-12')
  assert.equal(accountScopeFor({ username: 'Alice' }), 'username-alice')
})

test('账本、IndexedDB 与本地缓存键按用户隔离', () => {
  const alice = accountScopeFor({ id: 1, username: 'alice' })
  const bob = accountScopeFor({ id: 2, username: 'bob' })
  assert.notEqual(ledgerDatabaseName(alice), ledgerDatabaseName(bob))
  assert.notEqual(scopedStorageKey('st_records', alice), scopedStorageKey('st_records', bob))
  assert.equal(currentLedgerBookStorageKey(alice), 'ledger-current-book:id-1')
})

test('切换用户会清空账本内存状态并更换当前作用域', async () => {
  const values = new Map()
  globalThis.localStorage = {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key)
  }
  const { createPinia, setActivePinia } = await import('pinia')
  const { useLedgerStore } = await import('../stores/ledger.js')
  setActivePinia(createPinia())
  const ledger = useLedgerStore()
  await ledger.switchUser({ id: 1, username: 'alice' })
  ledger.ready = true
  ledger.books = [{ id: 'alice-book' }]
  ledger.currentBookId = 'alice-book'
  ledger.accounts = [{ id: 'alice-account' }]
  localStorage.setItem('ledger-current-book:id-1', 'alice-book')

  await ledger.switchUser({ id: 2, username: 'bob' })

  assert.equal(ledger.accountScope, 'id-2')
  assert.equal(ledger.ready, false)
  assert.equal(ledger.currentBookId, '')
  assert.deepEqual(ledger.books, [])
  assert.deepEqual(ledger.accounts, [])
  assert.equal(localStorage.getItem('ledger-current-book:id-1'), 'alice-book')
})
