import { test, expect } from '@playwright/test'
import { bootstrapTemplateLedger, createServerBorrowing, ledgerSyncState, registerUser, selectLedgerBeforeLoad } from './helpers.js'

const API_ROUTES = /\/api\/(?:v1\/|holidays(?:\?|$))/

function visibleTransaction(page, amount) {
  return page.locator('tr:visible, article.flow-mobile-item:visible')
    .filter({ hasText: '借入' })
    .filter({ hasText: amount })
    .first()
}

async function setApiOffline(page, offline) {
  if (offline) {
    await page.route(API_ROUTES, route => route.abort('internetdisconnected'))
    await page.evaluate(() => {
      localStorage.setItem('__salary_e2e_api_offline', '1')
      window.dispatchEvent(new Event('offline'))
    })
    return
  }
  await page.unroute(API_ROUTES)
  await page.evaluate(() => {
    localStorage.removeItem('__salary_e2e_api_offline')
    window.dispatchEvent(new Event('online'))
  })
}

async function createBorrowing(page, note, amount, accountId) {
  await page.getByRole('button', { name: '记一笔' }).click()
  const dialog = page.getByRole('dialog', { name: '新增流水' })
  await dialog.getByLabel('流水类型').selectOption('BORROW_IN')
  await dialog.getByLabel('金额').fill(String(amount))
  const account = dialog.getByLabel('账户')
  if (accountId) await account.selectOption(String(accountId))
  else if (!await account.inputValue()) await account.selectOption({ index: 1 })
  await dialog.getByLabel('备注').fill(note)
  await dialog.getByRole('button', { name: '保存流水' }).click()
  await expect(dialog).toBeHidden()
}

async function waitForCachedAccounts(page) {
  await page.getByRole('button', { name: '记一笔' }).click()
  const dialog = page.getByRole('dialog', { name: '新增流水' })
  await expect(dialog.getByLabel('账户').locator('option')).toHaveCount(5)
  await dialog.getByRole('button', { name: '取消' }).click()
  await expect(dialog).toBeHidden()
}

async function editBorrowing(page, fromAmount, toAmount) {
  const transaction = visibleTransaction(page, fromAmount)
  await transaction.getByRole('button', { name: '编辑流水' }).click()
  const dialog = page.getByRole('dialog', { name: '编辑流水' })
  await dialog.getByLabel('金额').fill(toAmount)
  await dialog.getByRole('button', { name: '保存流水' }).click()
  await expect(dialog).toBeHidden()
}

async function deleteBorrowing(page, amount) {
  const transaction = visibleTransaction(page, amount)
  await transaction.getByRole('button', { name: '删除流水' }).click()
  const dialog = page.getByRole('dialog', { name: '删除流水' })
  await dialog.getByRole('button', { name: '确认删除' }).click()
  await expect(dialog).toBeHidden()
}

test('offline ledger mutation survives reload and syncs when network returns', async ({ page }) => {
  const auth = await registerUser(page.request, 'ledger-offline-e2e')
  const { book, accounts, headers } = await bootstrapTemplateLedger(page.request, auth)
  await createServerBorrowing(page.request, { bookId: book.id, accountId: accounts[0].id, headers, amount: 21.1, note: 'edit offline' })
  await createServerBorrowing(page.request, { bookId: book.id, accountId: accounts[0].id, headers, amount: 32.2, note: 'delete offline' })
  await page.addInitScript(() => {
    Object.defineProperty(Navigator.prototype, 'onLine', {
      configurable: true,
      get: () => localStorage.getItem('__salary_e2e_api_offline') !== '1'
    })
  })
  await selectLedgerBeforeLoad(page, auth.user.id, book.id)

  await page.goto('/ledger/transactions')
  await expect(page.locator('h1').filter({ hasText: '流水' })).toBeVisible()
  await expect(page.getByText('正在切换页面…')).toBeHidden()
  await expect(page.getByRole('button', { name: '记一笔' })).toBeVisible()
  await waitForCachedAccounts(page)
  await expect(visibleTransaction(page, '21.10')).toBeVisible()
  await expect(visibleTransaction(page, '32.20')).toBeVisible()

  await setApiOffline(page, true)
  await createBorrowing(page, 'offline survives reload', 88.6)
  await editBorrowing(page, '21.10', '22.20')
  await deleteBorrowing(page, '32.20')
  await expect(visibleTransaction(page, '88.60')).toBeVisible()
  await expect(visibleTransaction(page, '22.20')).toBeVisible()
  await expect(visibleTransaction(page, '32.20')).toBeHidden()
  await expect(page.getByLabel('账本同步状态')).toContainText('3 项待同步')
  await expect.poll(() => ledgerSyncState(page, auth.user.id, book.id)).toEqual({ pending: 3, conflicts: 0, rejected: 0 })

  await page.reload()
  await expect(page.locator('h1').filter({ hasText: '流水' })).toBeVisible()
  await expect(visibleTransaction(page, '88.60')).toBeVisible()
  await expect(visibleTransaction(page, '22.20')).toBeVisible()
  await expect(visibleTransaction(page, '32.20')).toBeHidden()
  expect(await page.evaluate(() => navigator.onLine)).toBeFalsy()
  await expect.poll(() => ledgerSyncState(page, auth.user.id, book.id)).toEqual({ pending: 3, conflicts: 0, rejected: 0 })

  await setApiOffline(page, false)
  await expect.poll(() => ledgerSyncState(page, auth.user.id, book.id), { timeout: 20_000 }).toEqual({ pending: 0, conflicts: 0, rejected: 0 })
  await expect(visibleTransaction(page, '88.60')).toBeVisible()
  await expect(visibleTransaction(page, '22.20')).toBeVisible()
  await expect(visibleTransaction(page, '32.20')).toBeHidden()
})

test('sync errors are actionable and stay isolated by book and user', async ({ page }) => {
  const auth = await registerUser(page.request, 'ledger-sync-errors-e2e')
  const { book, accounts, headers } = await bootstrapTemplateLedger(page.request, auth)
  const isolatedBookResponse = await page.request.post('/api/v1/ledger/books', {
    headers,
    data: { name: 'E2E 隔离账本', currency: 'CNY', mode: 'BLANK' }
  })
  expect(isolatedBookResponse.ok(), await isolatedBookResponse.text()).toBeTruthy()
  const isolatedBook = (await isolatedBookResponse.json()).data
  const transaction = await createServerBorrowing(page.request, {
    bookId: book.id,
    accountId: accounts[0].id,
    headers,
    amount: 41.1,
    note: 'conflict source'
  })
  await page.addInitScript(() => {
    Object.defineProperty(Navigator.prototype, 'onLine', {
      configurable: true,
      get: () => localStorage.getItem('__salary_e2e_api_offline') !== '1'
    })
  })
  await selectLedgerBeforeLoad(page, auth.user.id, book.id)
  await page.goto('/ledger/transactions')
  await expect(page.locator('h1').filter({ hasText: '流水' })).toBeVisible()
  await waitForCachedAccounts(page)
  await expect(visibleTransaction(page, '41.10')).toBeVisible()

  await setApiOffline(page, true)
  await editBorrowing(page, '41.10', '42.20')
  const concurrentUpdate = await page.request.patch(`/api/v1/ledger/books/${book.id}/transactions/${transaction.id}`, {
    headers: { ...headers, 'If-Match': String(transaction.revision), 'Idempotency-Key': crypto.randomUUID() },
    data: { amount: 43.3, note: 'server wins' }
  })
  expect(concurrentUpdate.ok(), await concurrentUpdate.text()).toBeTruthy()

  await setApiOffline(page, false)
  await expect.poll(() => ledgerSyncState(page, auth.user.id, book.id), { timeout: 20_000 })
    .toEqual({ pending: 1, conflicts: 1, rejected: 0 })
  const syncStatus = page.getByLabel('账本同步状态')
  await expect(syncStatus).toContainText('1 个冲突')
  await syncStatus.getByRole('button', { name: '采用服务端版本' }).click()
  await expect.poll(() => ledgerSyncState(page, auth.user.id, book.id), { timeout: 20_000 })
    .toEqual({ pending: 0, conflicts: 0, rejected: 0 })
  await expect(visibleTransaction(page, '43.30')).toBeVisible()

  await setApiOffline(page, true)
  const deletedAccount = accounts[1]
  const deleteResponse = await page.request.delete(`/api/v1/ledger/books/${book.id}/accounts/${deletedAccount.id}`, {
    headers: { ...headers, 'If-Match': String(deletedAccount.revision), 'Idempotency-Key': crypto.randomUUID() }
  })
  expect(deleteResponse.ok(), await deleteResponse.text()).toBeTruthy()
  await createBorrowing(page, 'must be rejected', 54.4, deletedAccount.id)

  await setApiOffline(page, false)
  await expect.poll(() => ledgerSyncState(page, auth.user.id, book.id), { timeout: 20_000 })
    .toEqual({ pending: 0, conflicts: 0, rejected: 1 })
  await expect(syncStatus).toContainText('1 个拒绝')
  await expect(syncStatus).toContainText('账户')

  const downloadPromise = page.waitForEvent('download')
  await syncStatus.getByRole('button', { name: '导出拒绝记录' }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe(`ledger-rejected-${book.id}.json`)
  const stream = await download.createReadStream()
  const chunks = []
  for await (const chunk of stream) chunks.push(chunk)
  const exported = JSON.parse(Buffer.concat(chunks).toString('utf8'))
  expect(exported.bookId).toBe(book.id)
  expect(exported.operations).toHaveLength(1)
  expect(exported.rejections[0].status).toBe('REJECTED')
  expect(exported.rejections[0].message).toBeTruthy()

  await page.goto('/ledger/manage?view=books')
  const isolatedBookRow = page.locator('.book-table .data-row').filter({ hasText: isolatedBook.name })
  await isolatedBookRow.getByRole('button', { name: '切换到账本' }).click()
  await expect(isolatedBookRow).toContainText('当前')
  await expect(page.getByText('正在保存更改…')).toBeHidden()
  await expect.poll(() => ledgerSyncState(page, auth.user.id, isolatedBook.id))
    .toEqual({ pending: 0, conflicts: 0, rejected: 0 })
  await page.goto('/ledger/transactions')
  await expect(page.getByLabel('账本同步状态')).toBeHidden()
  expect(await ledgerSyncState(page, auth.user.id, book.id)).toEqual({ pending: 0, conflicts: 0, rejected: 1 })

  const secondAuth = await registerUser(page.request, 'ledger-second-user-e2e')
  const secondLedger = await bootstrapTemplateLedger(page.request, secondAuth)
  await page.evaluate(({ userId, bookId }) => {
    localStorage.setItem(`ledger-current-book:id-${userId}`, bookId)
  }, { userId: secondAuth.user.id, bookId: secondLedger.book.id })
  await page.reload()
  await expect(page.locator('h1').filter({ hasText: '流水' })).toBeVisible()
  await expect(page.getByLabel('账本同步状态')).toBeHidden()
  await expect.poll(() => ledgerSyncState(page, secondAuth.user.id, secondLedger.book.id))
    .toEqual({ pending: 0, conflicts: 0, rejected: 0 })
  expect(await ledgerSyncState(page, auth.user.id, book.id)).toEqual({ pending: 0, conflicts: 0, rejected: 1 })

  const loginResponse = await page.request.post('/api/v1/auth/login', {
    data: { username: auth.user.username, password: 'E2ePassword@2026' }
  })
  expect(loginResponse.ok(), await loginResponse.text()).toBeTruthy()
  await page.evaluate(({ userId, bookId }) => {
    localStorage.setItem(`ledger-current-book:id-${userId}`, bookId)
  }, { userId: auth.user.id, bookId: book.id })
  await page.reload()
  await expect(page.locator('h1').filter({ hasText: '流水' })).toBeVisible()
  await expect(page.getByLabel('账本同步状态')).toContainText('1 个拒绝')
})
