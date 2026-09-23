import { test, expect } from '@playwright/test'
import { markSessionBeforeLoad, registerUser } from './helpers.js'

const response = {
  content: '已查询到一个**默认账本**。', provider: 'test', configured: true, sessionId: 'session-1',
  durationMs: 1834, firstTokenMs: 212,
  usage: { inputTokens: 1250, outputTokens: 86, totalTokens: 1336, cacheHitTokens: 900, cacheMissTokens: 350, reasoningTokens: 26 },
  modelExecutions: [{ round: 1, durationMs: 1600, firstTokenMs: 190, usage: { totalTokens: 1336 } }],
  toolExecutions: [{ name: 'ledger.books.list', status: 'COMPLETED', summary: '当前可访问 1 个账本', durationMs: 21 }],
}

async function setupAgent(page, name, options = {}) {
  const auth = await registerUser(page.request, name)
  await markSessionBeforeLoad(page, auth.user.id)
  let sessions = options.sessions || []
  let groups = options.groups || []
  const messages = options.messages || []
  await page.route('**/api/v1/agent/session-groups', async route => {
    const method = route.request().method()
    if (method === 'GET') return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: groups }) })
    if (method === 'POST') {
      const body = route.request().postDataJSON()
      const group = { id: body.id, name: body.name, sortOrder: groups.length, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
      groups = [...groups, group]
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: group }) })
    }
    return route.fallback()
  })
  await page.route('**/api/v1/agent/session-groups/*', async route => {
    const groupId = new URL(route.request().url()).pathname.split('/').at(-1)
    if (route.request().method() === 'PATCH') {
      const group = groups.find(item => item.id === groupId)
      if (group) Object.assign(group, route.request().postDataJSON(), { updatedAt: new Date().toISOString() })
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: group }) })
    }
    if (route.request().method() === 'DELETE') {
      groups = groups.filter(item => item.id !== groupId)
      sessions.forEach(item => { if (item.groupId === groupId) item.groupId = null })
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { deleted: true } }) })
    }
    return route.fallback()
  })
  await page.route('**/api/v1/agent/sessions?*', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: sessions }) }))
  await page.route('**/api/v1/agent/sessions', async route => {
    if (route.request().method() !== 'POST') return route.fallback()
    const body = route.request().postDataJSON()
    const session = { id: body.id, title: body.title, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(), archivedAt: null }
    sessions = [session, ...sessions]
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: session }) })
  })
  await page.route('**/api/v1/agent/sessions/**', async route => {
    const url = new URL(route.request().url())
    const parts = url.pathname.split('/')
    const sessionId = ['archive', 'group'].includes(parts.at(-1)) ? parts.at(-2) : parts.at(-1)
    if (route.request().method() === 'PATCH') {
      const body = route.request().postDataJSON()
      const session = sessions.find(item => item.id === sessionId)
      if (session) {
        if (parts.at(-1) === 'group') session.groupId = body.groupId || null
        else {
          if (body.pinned !== undefined) session.pinnedAt = body.pinned ? new Date().toISOString() : null
          if (body.title !== undefined) session.title = body.title
        }
        session.updatedAt = new Date().toISOString()
      }
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: session }) })
    }
    if (route.request().method() === 'POST' && parts.at(-1) === 'archive') {
      sessions = sessions.filter(item => item.id !== sessionId)
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { archived: true } }) })
    }
    if (route.request().method() === 'DELETE') {
      sessions = sessions.filter(item => item.id !== sessionId)
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { deleted: true } }) })
    }
    return route.fallback()
  })
  await page.route('**/api/v1/agent/sessions/*/messages', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: messages }) }))
  await page.route('**/api/v1/agent/sessions/*/turns', route => route.fulfill({
    status: 200,
    contentType: 'text/event-stream',
    body: [
      `event: turn.received\ndata: ${JSON.stringify({ turnId: 'turn-1', status: 'RECEIVED', replayed: false })}\n\n`,
      `event: turn.status\ndata: ${JSON.stringify({ turnId: 'turn-1', status: 'PLANNING' })}\n\n`,
      `event: tool.started\ndata: ${JSON.stringify({ turnId: 'turn-1', name: 'ledger.books.list' })}\n\n`,
      `event: assistant.delta\ndata: ${JSON.stringify({ turnId: 'turn-1', content: '已查询到一个' })}\n\n`,
      `event: assistant.delta\ndata: ${JSON.stringify({ turnId: 'turn-1', content: '**默认账本**。' })}\n\n`,
      `event: tool.completed\ndata: ${JSON.stringify({ turnId: 'turn-1', execution: response.toolExecutions[0] })}\n\n`,
      `event: turn.completed\ndata: ${JSON.stringify({ turnId: 'turn-1', response })}\n\n`,
    ].join(''),
  }))
  return auth
}

test('streams the first reply and exposes detailed observability', async ({ page, context, browserName }) => {
  await setupAgent(page, 'agent-sse-e2e')
  if (browserName === 'chromium') {
    await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: 'http://127.0.0.1:14173' })
  }
  await page.goto('/')
  await page.getByLabel('给 AI 发送消息').fill('我有哪些账本？')
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.locator('.message-markdown strong')).toHaveText('默认账本')
  await expect(page.getByText('1,336 Tokens')).toBeVisible()
  await page.getByText('1,336 Tokens').click()
  await expect(page.getByText('缓存未命中')).toBeVisible()
  await expect(page.getByText('思考过程')).toBeVisible()
  await expect(page.getByText('输入缓存命中率 72%')).toBeVisible()
  await page.locator('.timing-details > summary').click()
  await expect(page.getByText('首字时延')).toBeVisible()
  await expect(page.getByText('模型调用 1')).toBeVisible()
  await expect(page.locator('.timing-popover').getByText('查询账本', { exact: true })).toBeVisible()
  await page.locator('.chat-workspace-head').click()
  await expect(page.getByText('首字时延')).toBeHidden()
  await page.getByRole('button', { name: '复制消息' }).last().click()
  await expect(page.getByRole('button', { name: '消息已复制' })).toBeVisible()
  if (browserName === 'chromium') {
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('已查询到一个**默认账本**。')
  }
  await expect(page.locator('.message-meta time')).toHaveCount(2)
})

test('restores server sessions and completed messages after refresh', async ({ page }) => {
  const now = new Date().toISOString()
  await setupAgent(page, 'agent-restore-e2e', {
    sessions: [{ id: 'session-1', title: '历史账本查询', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [
      { id: 1, turnId: 'turn-old', role: 'user', content: '我有哪些账本？', metadataJson: null, createdAt: now },
      { id: 2, turnId: 'turn-old', role: 'assistant', content: response.content, metadataJson: JSON.stringify(response), createdAt: now },
    ],
  })
  await page.goto('/')
  await expect(page.locator('.chat-workspace-head').getByText('历史账本查询', { exact: true })).toBeVisible()
  await expect(page.locator('.message-markdown strong')).toHaveText('默认账本')
  await page.reload()
  await expect(page.locator('.chat-workspace-head').getByText('历史账本查询', { exact: true })).toBeVisible()
  await expect(page.getByText('1,336 Tokens')).toBeVisible()
})

test('falls back before the first SSE event', async ({ page }) => {
  await setupAgent(page, 'agent-fallback-e2e')
  await page.route('**/api/v1/agent/sessions/*/turns', route => route.fulfill({ status: 503, contentType: 'application/json', body: '{}' }))
  await page.route('**/api/v1/ai/chat', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { ...response, content: '已通过降级路径完成查询。', firstTokenMs: 0 } }) }))
  await page.goto('/')
  await page.getByLabel('给 AI 发送消息').fill('降级测试')
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.getByText('已通过降级路径完成查询。')).toBeVisible()
  await page.locator('.timing-details > summary').click()
  await expect(page.getByText('未采集').first()).toBeVisible()
})

test('opens GPT-style conversation actions from right click and more button', async ({ page }, testInfo) => {
  const now = new Date().toISOString()
  await setupAgent(page, 'agent-context-menu-e2e', {
    sessions: [
      { id: 'session-1', title: '账本查询', createdAt: now, updatedAt: now, archivedAt: null },
      { id: 'session-2', title: '工时查询', createdAt: now, updatedAt: now, archivedAt: null },
    ],
  })
  await page.goto('/')

  const firstRow = page.locator('[data-session-id="session-1"]')
  if (testInfo.project.name.startsWith('mobile')) {
    await page.getByRole('button', { name: '展开历史会话' }).click()
    await page.getByRole('button', { name: '账本查询的更多操作' }).click()
  } else {
    await firstRow.click({ button: 'right' })
  }
  await expect(page.getByRole('menu', { name: '账本查询的会话操作' })).toBeVisible()
  await expect(page.getByRole('menuitem', { name: '重命名' })).toBeVisible()
  await expect(page.getByRole('menuitem', { name: '归档' })).toBeVisible()
  await expect(page.getByRole('menuitem', { name: '删除' })).toBeVisible()

  await page.getByRole('menuitem', { name: '重命名' }).click()
  await expect(page.getByRole('dialog', { name: '重命名会话' })).toBeVisible()
  await page.getByLabel('会话名称').fill('九月账本复盘')
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.locator('.conversation-list').getByText('九月账本复盘', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: '九月账本复盘的更多操作' }).click()
  if (testInfo.project.name.startsWith('mobile')) await page.getByRole('button', { name: '收起侧栏' }).click()
  else await page.locator('.chat-workspace-head').click()
  await expect(page.getByRole('menu', { name: '九月账本复盘的会话操作' })).toBeHidden()

  if (testInfo.project.name.startsWith('mobile')) await page.getByRole('button', { name: '展开历史会话' }).click()
  await page.getByRole('button', { name: '工时查询的更多操作' }).click()
  await page.getByRole('menuitem', { name: '归档' }).click()
  await expect(page.locator('.conversation-list').getByText('工时查询', { exact: true })).toBeHidden()

  await page.getByRole('button', { name: '九月账本复盘的更多操作' }).click()
  await page.getByRole('menuitem', { name: '删除' }).click()
  await expect(page.getByRole('dialog', { name: '删除会话？' })).toBeVisible()
  await page.getByRole('dialog', { name: '删除会话？' }).getByRole('button', { name: '删除' }).click()
  await expect(page.locator('.conversation-list').getByText('九月账本复盘', { exact: true })).toBeHidden()
})

test('keeps conversation group headers on one compact line', async ({ page }, testInfo) => {
  const now = new Date().toISOString()
  await setupAgent(page, 'agent-group-layout-e2e', {
    sessions: [{ id: 'session-1', title: '账本查询', groupId: null, pinnedAt: null, createdAt: now, updatedAt: now, archivedAt: null }],
    groups: [{ id: 'group-1', name: '工作', sortOrder: 0, createdAt: now, updatedAt: now }],
  })
  await page.goto('/')
  if (testInfo.project.name.startsWith('mobile')) await page.getByRole('button', { name: '展开历史会话' }).click()
  for (const label of ['工作收起', '未分组收起']) {
    const header = page.getByRole('button', { name: label })
    await expect(header).toBeVisible()
    const box = await header.boundingBox()
    expect(box.width).toBeGreaterThan(80)
    expect(await header.evaluate(element => getComputedStyle(element).whiteSpace)).toBe('nowrap')
    expect(await header.evaluate(element => element.scrollWidth <= element.clientWidth && element.scrollHeight <= element.clientHeight)).toBeTruthy()
  }
})

test('creates groups, pins sessions, and drags sessions into and out of groups', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name.startsWith('mobile'), 'HTML drag and drop is desktop-only; the move menu covers mobile.')
  const now = new Date().toISOString()
  await setupAgent(page, 'agent-groups-e2e', {
    sessions: [
      { id: 'session-1', title: '账本查询', groupId: null, pinnedAt: null, createdAt: now, updatedAt: now, archivedAt: null },
      { id: 'session-2', title: '工时查询', groupId: null, pinnedAt: null, createdAt: now, updatedAt: now, archivedAt: null },
    ],
  })
  await page.goto('/')

  await page.getByRole('button', { name: '新建会话分组' }).click()
  await page.getByLabel('分组名称').fill('工作')
  await page.getByRole('dialog', { name: '新建分组' }).getByRole('button', { name: '保存' }).click()
  const workGroup = page.getByRole('button', { name: '工作收起' }).locator('xpath=../..')
  await expect(workGroup).toBeVisible()
  await expect(workGroup.getByText('拖入会话')).toBeVisible()

  await page.getByRole('button', { name: '账本查询的更多操作' }).click()
  await page.getByRole('menuitem', { name: '置顶' }).click()
  await expect(page.locator('[data-group-id="pinned"]')).toContainText('账本查询')

  await page.locator('[data-session-id="session-1"]').dragTo(workGroup)
  await expect(workGroup).toContainText('账本查询')
  await expect(page.locator('[data-group-id="pinned"]')).toHaveCount(0)

  await page.locator('[data-session-id="session-1"]').dragTo(page.locator('[data-group-id="ungrouped"]'))
  await expect(page.locator('[data-group-id="ungrouped"]')).toContainText('账本查询')
  await page.reload()
  await expect(page.locator('[data-group-id="ungrouped"]')).toContainText('账本查询')
  await expect(page.getByRole('button', { name: '工作收起' })).toBeVisible()
})

test('queues follow-up messages and stops following as soon as the user scrolls up', async ({ page }) => {
  await setupAgent(page, 'agent-queue-e2e')
  let turnCount = 0
  await page.route('**/api/v1/agent/sessions/*/turns', async route => {
    turnCount += 1
    const current = turnCount
    if (current === 1) await new Promise(resolve => setTimeout(resolve, 700))
    const turnResponse = { ...response, content: current === 1 ? '第一条回答\n\n'.repeat(80) : '第二条排队回答' }
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: [
        `event: turn.received\ndata: ${JSON.stringify({ turnId: `turn-${current}`, status: 'RECEIVED', replayed: false })}\n\n`,
        `event: assistant.delta\ndata: ${JSON.stringify({ turnId: `turn-${current}`, content: turnResponse.content })}\n\n`,
        `event: turn.completed\ndata: ${JSON.stringify({ turnId: `turn-${current}`, response: turnResponse })}\n\n`,
      ].join(''),
    })
  })

  await page.goto('/')
  const composer = page.getByLabel('给 AI 发送消息')
  await composer.fill('第一条消息')
  await page.getByRole('button', { name: '发送' }).click()
  await composer.fill('第二条消息')
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.getByText('等待发送 1 条')).toBeVisible()
  await expect(page.getByText('第二条消息', { exact: true })).toBeVisible()
  await expect(page.getByText('第二条排队回答', { exact: true })).toBeVisible()
  await expect(page.getByText('等待发送 1 条')).toBeHidden()

  const viewport = page.locator('.chat-message-viewport')
  await viewport.evaluate(element => { element.scrollTop = Math.max(0, element.scrollHeight - element.clientHeight - 1) })
  await viewport.dispatchEvent('wheel', { deltaY: -80 })
  await expect(page.getByRole('button', { name: '滚动到底部并继续跟随' })).toBeVisible()
  const before = await viewport.evaluate(element => element.scrollTop)
  await page.waitForTimeout(100)
  expect(await viewport.evaluate(element => element.scrollTop)).toBe(before)
  await page.getByRole('button', { name: '滚动到底部并继续跟随' }).click()
  await expect(page.getByRole('button', { name: '滚动到底部并继续跟随' })).toBeHidden()
})

test('reorders queued messages by dragging before they execute', async ({ page }) => {
  await setupAgent(page, 'agent-queue-order-e2e')
  const executionOrder = []
  await page.route('**/api/v1/agent/sessions/*/turns', async route => {
    const body = route.request().postDataJSON()
    executionOrder.push(body.message)
    if (body.message === '第一条消息') await new Promise(resolve => setTimeout(resolve, 900))
    const turnResponse = { ...response, content: `已处理：${body.message}` }
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: [
        `event: turn.received\ndata: ${JSON.stringify({ turnId: `turn-${executionOrder.length}`, status: 'RECEIVED', replayed: false })}\n\n`,
        `event: assistant.delta\ndata: ${JSON.stringify({ content: turnResponse.content })}\n\n`,
        `event: turn.completed\ndata: ${JSON.stringify({ response: turnResponse })}\n\n`,
      ].join(''),
    })
  })

  await page.goto('/')
  const composer = page.getByLabel('给 AI 发送消息')
  for (const content of ['第一条消息', '第二条消息', '第三条消息']) {
    await composer.fill(content)
    await page.getByRole('button', { name: '发送' }).click()
  }
  await expect(page.getByText('等待发送 2 条')).toBeVisible()
  await page.locator('.prompt-queue li').filter({ hasText: '第三条消息' }).dragTo(page.locator('.prompt-queue li').filter({ hasText: '第二条消息' }))
  await expect(page.getByText('已处理：第三条消息')).toBeVisible()
  await expect(page.getByText('已处理：第二条消息')).toBeVisible()
  expect(executionOrder).toEqual(['第一条消息', '第三条消息', '第二条消息'])
})
