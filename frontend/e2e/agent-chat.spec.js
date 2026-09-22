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
