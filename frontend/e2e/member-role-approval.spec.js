import { test, expect } from '@playwright/test'
import { markSessionBeforeLoad, registerUser } from './helpers.js'

test('submits member and role changes through the R4 approval center', async ({ page }) => {
  const target = await registerUser(page.request, 'r4-target-member')
  const owner = await registerUser(page.request, 'r4-owner')
  await markSessionBeforeLoad(page, owner.user.id)

  await page.goto('/ledger/manage?view=members&section=members')
  await expect(page.getByRole('heading', { name: '成员与角色权限' })).toBeVisible()
  await page.getByRole('button', { name: '添加成员' }).click()
  await page.getByLabel('已注册用户名').fill(target.user.username)
  await page.getByLabel('账本角色').selectOption({ label: '成员' })
  await page.getByRole('button', { name: '提交审批' }).click()

  await expect(page).toHaveURL(/\/approvals\//)
  await expect(page.getByRole('heading', { name: /添加成员/ })).toBeVisible()
  await expect(page.locator('.approval-change').getByText(target.user.nickname, { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '批准并创建' }).click()
  await expect(page.getByText('成员已添加')).toBeVisible()
  await page.getByRole('link', { name: '查看成员与权限' }).click()
  await expect(page).toHaveURL(/\/ledger\/manage\?view=members/)
  await expect(page.locator('.member-table').getByText(target.user.nickname, { exact: true })).toBeVisible()

  await page.goto('/ledger/manage?view=members&section=roles')
  await page.getByRole('button', { name: '新增角色' }).click()
  await page.getByLabel('角色名称').fill('费用审核员')
  await page.getByLabel('管理全部流水').check()
  await page.getByRole('button', { name: '提交审批' }).click()

  await expect(page).toHaveURL(/\/approvals\//)
  await expect(page.getByRole('heading', { name: /创建角色/ })).toBeVisible()
  await expect(page.locator('.approval-change').getByText('费用审核员', { exact: true })).toBeVisible()
  await expect(page.getByText('+ 管理全部流水')).toBeVisible()
  await page.getByRole('button', { name: '批准并创建' }).click()
  await expect(page.getByText('角色已创建')).toBeVisible()
  await page.getByRole('link', { name: '查看成员与权限' }).click()
  await expect(page).toHaveURL(/\/ledger\/manage\?view=members/)
  await page.getByRole('button', { name: '角色与权限' }).click()
  await expect(page.locator('.role-table').getByText('费用审核员', { exact: true })).toBeVisible()
})
