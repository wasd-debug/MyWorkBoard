import { expect } from '@playwright/test'

export async function registerUser(request, prefix) {
  const username = `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`
  const response = await request.post('/api/v1/auth/register', {
    data: { username, password: 'E2ePassword@2026', nickname: prefix }
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json()).data
}

export async function bootstrapTemplateLedger(request, auth) {
  const headers = { Authorization: `Bearer ${auth.accessToken}` }
  const bookResponse = await request.post('/api/v1/ledger/books', {
    headers,
    data: { name: 'E2E 离线账本', currency: 'CNY', mode: 'SYSTEM_TEMPLATE' }
  })
  expect(bookResponse.ok(), await bookResponse.text()).toBeTruthy()
  const book = (await bookResponse.json()).data
  const accountsResponse = await request.get(`/api/v1/ledger/books/${book.id}/accounts`, { headers })
  expect(accountsResponse.ok(), await accountsResponse.text()).toBeTruthy()
  const accounts = (await accountsResponse.json()).data
  return { book, accounts, headers }
}

export async function createServerBorrowing(request, { bookId, accountId, headers, amount, note }) {
  const response = await request.post(`/api/v1/ledger/books/${bookId}/transactions`, {
    headers: { ...headers, 'Idempotency-Key': crypto.randomUUID() },
    data: {
      kind: 'BORROW_IN',
      amount,
      occurredOn: '2026-09-17',
      accountId,
      note
    }
  })
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json()).data
}

export async function selectLedgerBeforeLoad(page, userId, bookId) {
  await page.addInitScript(({ userId: id, bookId: selectedBook }) => {
    localStorage.setItem(`ledger-current-book:id-${id}`, selectedBook)
  }, { userId, bookId })
}

export async function ledgerSyncState(page, userId, bookId) {
  return page.evaluate(async ({ userId: id, bookId: selectedBook }) => {
    const dbName = `salary-tracker-sync-v2:id-${id}`
    const db = await new Promise((resolve, reject) => {
      const request = indexedDB.open(dbName, 1)
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    })
    const read = store => new Promise((resolve, reject) => {
      const request = db.transaction(store, 'readonly').objectStore(store).getAll()
      request.onsuccess = () => resolve(request.result || [])
      request.onerror = () => reject(request.error)
    })
    const [pending, conflicts, rejected] = await Promise.all([
      read('oplog'), read('conflicts'), read('rejected')
    ])
    db.close()
    const inBook = item => String(item.bookId) === String(selectedBook)
    return {
      pending: pending.filter(inBook).length,
      conflicts: conflicts.filter(inBook).length,
      rejected: rejected.filter(inBook).length
    }
  }, { userId, bookId })
}
