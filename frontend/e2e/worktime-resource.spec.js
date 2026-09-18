import { test, expect } from '@playwright/test'
import { registerUser } from './helpers.js'

test('removed API aliases return 404 without authentication', async ({ request }) => {
  for (const path of ['/api/data', '/api/auth/login', '/api/worktime/snapshot', '/api/ledger/books', '/api/holidays']) {
    const response = await request.get(path)
    expect(response.status(), `${path} should stay removed`).toBe(404)
  }
})

test('worktime UI reads and writes resources without snapshot requests', async ({ page }) => {
  const auth = await registerUser(page.request, 'worktime-resource-e2e')
  const requests = []
  page.on('request', request => {
    if (request.url().includes('/api/v1/worktime/')) {
      requests.push({ method: request.method(), url: new URL(request.url()).pathname })
    }
  })

  await page.goto('/punch')
  await expect(page.getByRole('heading', { name: '打卡' })).toBeVisible()
  await expect(page.getByText('正在切换页面…')).toBeHidden()
  await expect.poll(() => requests.some(item => item.method === 'GET' && item.url === '/api/v1/worktime/settings')).toBeTruthy()
  await expect.poll(() => requests.some(item => item.method === 'GET' && item.url === '/api/v1/worktime/records')).toBeTruthy()

  const date = '2026-09-16'
  await page.getByLabel('打卡日期').fill(date)
  await page.getByLabel('打卡日期').press('Tab')
  await expect(page.getByLabel('实际上班时间')).toHaveValue('09:00')
  await expect(page.getByLabel('实际下班时间')).toHaveValue('18:00')

  const created = page.waitForResponse(response => {
    const request = response.request()
    return request.method() === 'POST' && new URL(response.url()).pathname === '/api/v1/worktime/records'
      && request.postDataJSON()?.date === date
  })
  await page.getByLabel('实际上班时间').fill('08:45')
  await page.getByLabel('实际上班时间').press('Tab')
  const createResponse = await created
  expect(createResponse.ok(), await createResponse.text()).toBeTruthy()
  const createdRecord = (await createResponse.json()).data
  expect(createdRecord.date).toBe(date)
  expect(createdRecord.calcVersion).toBe('phase0-v1')
  expect(createdRecord.timezone).toBe('Asia/Shanghai')

  const updated = page.waitForResponse(response => response.request().method() === 'PATCH'
    && new URL(response.url()).pathname === `/api/v1/worktime/records/${createdRecord.id}`)
  await page.getByLabel('实际下班时间').fill('18:30')
  await page.getByLabel('实际下班时间').press('Tab')
  expect((await updated).ok()).toBeTruthy()

  const deleted = page.waitForResponse(response => response.request().method() === 'DELETE'
    && new URL(response.url()).pathname === `/api/v1/worktime/records/${createdRecord.id}`)
  await page.goto('/records')
  await page.getByRole('tab', { name: '列表' }).click()
  page.once('dialog', dialog => dialog.accept())
  await page.getByRole('button', { name: `删除 ${date} 的打卡记录` }).click()
  expect((await deleted).ok()).toBeTruthy()

  expect(requests.some(item => item.url.includes('/snapshot'))).toBeFalsy()
  expect(requests.some(item => item.method === 'POST' && item.url === '/api/v1/worktime/records')).toBeTruthy()
  expect(requests.some(item => item.method === 'PATCH' && item.url.startsWith('/api/v1/worktime/records/'))).toBeTruthy()
  expect(requests.some(item => item.method === 'DELETE' && item.url.startsWith('/api/v1/worktime/records/'))).toBeTruthy()

  const recordsResponse = await page.request.get('/api/v1/worktime/records', {
    headers: { Authorization: `Bearer ${auth.accessToken}` },
    params: { from: date, to: date }
  })
  expect(recordsResponse.ok(), await recordsResponse.text()).toBeTruthy()
  expect((await recordsResponse.json()).data).toEqual([])

  const removedRoutes = [
    { method: 'post', path: '/api/auth/login', data: {} },
    { method: 'get', path: '/api/worktime/settings' },
    { method: 'get', path: '/api/ledger/books' },
    { method: 'get', path: '/api/data' },
    { method: 'get', path: '/api/v1/worktime/snapshot' }
  ]
  for (const route of removedRoutes) {
    const response = await page.request.fetch(route.path, {
      method: route.method,
      headers: { Authorization: `Bearer ${auth.accessToken}` },
      data: route.data
    })
    expect(response.status(), `${route.method.toUpperCase()} ${route.path} must not remain routable`).toBe(404)
  }
})
