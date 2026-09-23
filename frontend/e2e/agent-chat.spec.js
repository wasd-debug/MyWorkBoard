import { test, expect } from '@playwright/test'
import { markSessionBeforeLoad, registerUser } from './helpers.js'

test('agent chat displays the first reply in a new conversation without refresh', async ({ page }) => {
  const auth = await registerUser(page.request, 'agent-first-reply-e2e')
  await markSessionBeforeLoad(page, auth.user.id)
  await page.route('**/api/v1/ai/chat', async route => {
    await new Promise(resolve => setTimeout(resolve, 300))
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data: { content: '**默认账本** 可以正常访问。', provider: 'test', configured: true } }),
    })
  })

  await page.goto('/')
  await page.getByLabel('给 AI 发送消息').fill('我有哪些账本？')
  await page.getByRole('button', { name: '发送' }).click()

  await expect(page.getByText('正在思考…')).toBeAttached()
  await expect(page.locator('.message-markdown strong')).toHaveText('默认账本')
  await expect(page.getByText('可以正常访问。', { exact: false })).toBeVisible()
})

test('agent chat reuses one session id for follow-up questions', async ({ page }) => {
  const auth = await registerUser(page.request, 'agent-context-e2e')
  await markSessionBeforeLoad(page, auth.user.id)
  const requests = []
  await page.route('**/api/v1/ai/chat', async route => {
    const body = route.request().postDataJSON()
    requests.push(body)
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data: {
        content: requests.length === 1 ? '你有一个账本。' : '它叫默认账本。',
        provider: 'test',
        configured: true,
        sessionId: body.sessionId,
      } }),
    })
  })

  await page.goto('/')
  await page.getByLabel('给 AI 发送消息').fill('我有几个账本？')
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.getByText('你有一个账本。')).toBeVisible()
  await page.getByLabel('给 AI 发送消息').fill('它叫什么？')
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.getByText('它叫默认账本。')).toBeVisible()

  expect(requests).toHaveLength(2)
  expect(requests[0].sessionId).toBeTruthy()
  expect(requests[1].sessionId).toBe(requests[0].sessionId)
})

test('agent reply shows tools tokens duration time and supports copying', async ({ page, context }) => {
  const auth = await registerUser(page.request, 'agent-observability-e2e')
  await markSessionBeforeLoad(page, auth.user.id)
  await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: 'http://127.0.0.1:14173' })
  await page.route('**/api/v1/ai/chat', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data: {
        content: '已查询到一个默认账本。', provider: 'test', configured: true,
        durationMs: 1834,
        usage: { inputTokens: 1250, outputTokens: 86, totalTokens: 1336, cacheHitTokens: 900, cacheMissTokens: 350 },
        toolExecutions: [{ name: 'ledger.books.list', status: 'COMPLETED', summary: '当前可访问 1 个账本', durationMs: 21 }],
      } }),
    })
  })

  await page.goto('/')
  await page.getByLabel('给 AI 发送消息').fill('我有哪些账本？')
  await page.getByRole('button', { name: '发送' }).click()

  await expect(page.getByText('已查询到一个默认账本。')).toBeVisible()
  await expect(page.getByText('1,336 Tokens')).toBeVisible()
  await expect(page.getByText('1.8s')).toBeVisible()
  await page.getByText('已调用 1 个工具').click()
  await expect(page.getByText('查询账本')).toBeVisible()
  await expect(page.getByText('当前可访问 1 个账本')).toBeVisible()
  await page.getByRole('button', { name: '复制消息' }).last().click()
  await expect(page.getByRole('button', { name: '消息已复制' })).toBeVisible()
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('已查询到一个默认账本。')
  await expect(page.locator('.message-meta time')).toHaveCount(2)
})

test('agent chat renders markdown and exposes controllable bottom following', async ({ page }) => {
  const auth = await registerUser(page.request, 'agent-chat-e2e')
  await markSessionBeforeLoad(page, auth.user.id)
  await page.addInitScript(userId => {
    const messages = Array.from({ length: 24 }, (_, index) => ({
      id: `history-${index}`,
      role: index % 2 ? 'assistant' : 'user',
      content: `历史消息 ${index + 1}\n\n用于验证长会话滚动。`,
      typing: false,
    }))
    localStorage.setItem(`workspace_ai_conversations_v1:id-${userId}`, JSON.stringify([{
      id: 'scroll-test',
      title: '滚动测试',
      updatedAt: Date.now(),
      messages,
    }]))
  }, auth.user.id)
  await page.route('**/api/v1/ai/chat', async route => {
    await new Promise(resolve => setTimeout(resolve, 600))
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data: { content: '## 测试标题\n\n- 第一项\n- 第二项\n\n`safe-code`', provider: 'test', configured: true } }),
    })
  })

  await page.goto('/')
  const viewport = page.locator('.chat-message-viewport')
  await expect(page.getByText('历史消息 24')).toBeVisible()
  await viewport.evaluate(element => { element.scrollTop = 0 })
  await expect(page.getByRole('button', { name: '滚动到底部并继续跟随' })).toBeVisible()

  await page.getByLabel('给 AI 发送消息').fill('测试 Markdown')
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.getByText('正在思考…')).toBeAttached()
  await expect(page.getByRole('heading', { name: '测试标题' })).toBeAttached()
  await expect(page.locator('.message-markdown li')).toHaveCount(2)
  await expect(page.locator('.message-markdown code')).toHaveText('safe-code')
  await expect(page.getByRole('button', { name: '滚动到底部并继续跟随' })).toBeVisible()

  await page.getByRole('button', { name: '滚动到底部并继续跟随' }).click()
  await expect(page.getByRole('button', { name: '滚动到底部并继续跟随' })).toBeHidden()
})
