import { test, expect } from '@playwright/test'
import { bootstrapTemplateLedger, registerUser, selectLedgerBeforeLoad } from './helpers.js'

test('approves recycle purge and book deletion through the R4 approval center', async ({ page }) => {
  const auth = await registerUser(page.request, 'r4-destructive')
  const { book, accounts, headers } = await bootstrapTemplateLedger(page.request, auth)
  await selectLedgerBeforeLoad(page, auth.user.id, book.id)

  const deletedAccount = accounts[0]
  const deleteResponse = await page.request.delete(`/api/v1/ledger/books/${book.id}/accounts/${deletedAccount.id}`, {
    headers: { ...headers, 'If-Match': String(deletedAccount.revision), 'Idempotency-Key': crypto.randomUUID() }
  })
  expect(deleteResponse.ok(), await deleteResponse.text()).toBeTruthy()

  await page.goto('/ledger/manage?view=recycle')
  const recycleRow = page.locator('.recycle-table .data-row').filter({ hasText: deletedAccount.name })
  await expect(recycleRow).toBeVisible()
  await recycleRow.getByRole('button', { name: '永久删除条目' }).click()
  await page.getByRole('button', { name: '提交审批' }).click()

  await expect(page).toHaveURL(/\/approvals\//)
  await expect(page.getByRole('heading', { name: /永久清除 1 个回收站项目/ })).toBeVisible()
  await expect(page.getByText('冻结项目（共 1 项）')).toBeVisible()
  await page.getByRole('button', { name: '批准并永久清除 1 项' }).click()
  await expect(page.getByText('已永久清除 1 个回收站项目')).toBeVisible()
  await page.getByRole('link', { name: '查看回收站' }).click()
  await expect(page).toHaveURL(/\/ledger\/manage\?view=recycle/)
  await expect(page.locator('.recycle-table .data-row')).toHaveCount(0)

  await page.goto('/ledger/manage?view=books')
  const bookRow = page.locator('.book-table .data-row').filter({ hasText: book.name })
  await expect(bookRow).toBeVisible()
  await bookRow.getByRole('button', { name: '删除账本' }).click()
  await page.getByRole('button', { name: '删除账本' }).click()

  await expect(page).toHaveURL(/\/approvals\//)
  await expect(page.getByRole('heading', { name: new RegExp(`删除账本.*${book.name}`) })).toBeVisible()
  await expect(page.getByText('有效流水')).toBeVisible()
  await expect(page.getByText('回收站项目', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '批准并删除账本' }).click()
  await expect(page.getByText('账本已删除')).toBeVisible()
  await page.getByRole('link', { name: '查看账本列表' }).click()
  await expect(page).toHaveURL(/\/ledger\/manage\?view=books/)
  await expect(page.locator('.book-table').getByText(book.name, { exact: true })).toHaveCount(0)
  await expect(page.locator('.book-table .data-row')).toHaveCount(1)
})
