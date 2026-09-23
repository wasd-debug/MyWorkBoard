import { test, expect } from '@playwright/test'
import { markSessionBeforeLoad, registerUser } from './helpers.js'

const response = {
  content: '已查询到一个**默认账本**。', provider: 'test', configured: true, sessionId: 'session-1',
  durationMs: 1834, firstTokenMs: 212,
  usage: { inputTokens: 1250, outputTokens: 86, totalTokens: 1336, cacheHitTokens: 900, cacheMissTokens: 350, reasoningTokens: 26 },
  modelExecutions: [{ round: 1, durationMs: 1600, firstTokenMs: 190, usage: { totalTokens: 1336 } }],
  toolExecutions: [{ name: 'ledger.books.list', status: 'COMPLETED', summary: '当前可访问 1 个账本', durationMs: 21 }],
}

const trace = {
  turnId: 'turn-1', status: 'COMPLETED', providerType: 'DEEPSEEK', modelName: 'deepseek-chat',
  totalDurationMs: 1834, firstTokenMs: 212, totalTokens: 1336, currency: 'CNY', estimatedCost: 0.00318,
  modelExecutions: [{ round: 1, durationMs: 1600, firstTokenMs: 190, inputTokens: 1250, outputTokens: 86, cacheHitTokens: 900, cacheMissTokens: 350, reasoningTokens: 26, totalTokens: 1336, currency: 'CNY', pricingTier: 'OFF_PEAK', estimatedCost: 0.00318 }],
  toolExecutions: response.toolExecutions.map((item, index) => ({ sequence: index + 1, ...item })),
}

async function setupAgent(page, name, options = {}) {
  const auth = await registerUser(page.request, name)
  await markSessionBeforeLoad(page, auth.user.id)
  let sessions = options.sessions || []
  let groups = options.groups || []
  let messages = options.messages || []
  let queue = options.queue || []
  let queueRevision = 0
  const queueRequests = []
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
  await page.route('**/api/v1/agent/turns/*/trace', route => {
    const turnId = new URL(route.request().url()).pathname.split('/').at(-2)
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { ...trace, turnId } }) })
  })
  await page.route('**/api/v1/agent/sessions/*/turns', async route => {
    const body = route.request().postDataJSON()
    const createdAt = new Date().toISOString()
    messages = [
      ...messages,
      { id: messages.length + 1, turnId: 'turn-1', role: 'user', content: body.message, metadataJson: null, createdAt },
      { id: messages.length + 2, turnId: 'turn-1', role: 'assistant', content: response.content, metadataJson: JSON.stringify(response), createdAt },
    ]
    await route.fulfill({
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
    })
  })
  await page.route('**/api/v1/agent/sessions/*/queue/order', async route => {
    const body = route.request().postDataJSON()
    queueRequests.push({ type: 'reorder', turnIds: body.turnIds })
    if (body.revision !== queueRevision) return route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ detail: '队列版本冲突，请刷新后重试' }) })
    queue = body.turnIds.map(id => queue.find(item => item.id === id)).filter(Boolean)
    queueRevision += 1
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { revision: queueRevision, items: queue } }) })
  })
  await page.route('**/api/v1/agent/sessions/*/queue', async route => {
    if (route.request().method() === 'GET') return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { revision: queueRevision, items: queue } }) })
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON()
      const sessionId = new URL(route.request().url()).pathname.split('/').at(-2)
      const item = { id: `queued-${body.clientRequestId}`, sessionId, clientRequestId: body.clientRequestId, status: 'QUEUED', userMessage: body.message, createdAt: new Date().toISOString() }
      queue = [...queue, item]
      queueRevision += 1
      queueRequests.push({ type: 'enqueue', text: body.message, id: item.id })
      options.onQueueChange?.({ queue, queueRevision, queueRequests, setQueue: next => { queue = next; queueRevision += 1 }, setMessages: next => { messages = next } }, item)
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: item }) })
    }
    return route.fallback()
  })
  await page.route('**/api/v1/agent/queue/*', async route => {
    const turnId = new URL(route.request().url()).pathname.split('/').at(-1)
    queue = queue.filter(item => item.id !== turnId)
    queueRevision += 1
    queueRequests.push({ type: 'remove', id: turnId })
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { id: turnId, status: 'CANCELLED' } }) })
  })
  await page.route('**/api/v1/agent/turns/*/cancel', async route => {
    const turnId = new URL(route.request().url()).pathname.split('/').at(-2)
    queue = queue.filter(item => item.id !== turnId)
    queueRevision += 1
    queueRequests.push({ type: 'cancel', id: turnId })
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { id: turnId, status: 'CANCELLED' } }) })
  })
  await page.route('**/api/v1/agent/turns/*/retry', async route => {
    const originalId = new URL(route.request().url()).pathname.split('/').at(-2)
    const body = route.request().postDataJSON()
    const original = options.failedTurns?.[originalId] || { sessionId: sessions[0]?.id || 'session-1', userMessage: '重试消息' }
    const existing = queue.find(item => item.retryOfTurnId === originalId)
    const item = existing || { id: `retry-${originalId}`, sessionId: original.sessionId, clientRequestId: body.clientRequestId, retryOfTurnId: originalId, status: 'QUEUED', userMessage: original.userMessage, createdAt: new Date().toISOString() }
    if (!existing) { queue = [...queue, item]; queueRevision += 1 }
    queueRequests.push({ type: 'retry', id: item.id, originalId })
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: item }) })
  })
  return { ...auth, queueState: { get queue() { return queue }, get revision() { return queueRevision }, queueRequests, setQueue(next) { queue = next; queueRevision += 1 }, setMessages(next) { messages = next } } }
}

test('streams the first reply and exposes detailed observability', async ({ page, context, browserName }) => {
  await setupAgent(page, 'agent-sse-e2e')
  if (browserName === 'chromium') {
    await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: 'http://127.0.0.1:14173' })
  }
  await page.goto('/')
  const modelPicker = page.locator('.composer-model-picker')
  await expect(modelPicker).toBeVisible()
  const modelPickerBox = await modelPicker.boundingBox()
  expect(modelPickerBox.width).toBeLessThanOrEqual(108)
  expect(modelPickerBox.height).toBeLessThanOrEqual(30)
  await page.getByLabel('给 AI 发送消息').fill('我有哪些账本？')
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.locator('.message-markdown strong')).toHaveText('默认账本')
  await expect(page.locator('.chat-message.user').getByRole('button', { name: /进入/ })).toHaveCount(0)
  await expect(page.locator('.chat-message.assistant').getByRole('button', { name: '进入账本' })).toBeVisible()
  await expect(page.getByText('费用 ¥0.003180')).toBeVisible()
  await page.getByText('费用 ¥0.003180').click()
  await expect(page.getByText('模型调用 1 · 空闲')).toBeVisible()
  await expect(page.getByText('1,336 Tokens')).toBeVisible()
  await page.getByText('1,336 Tokens').click()
  await expect(page.getByText('缓存未命中')).toBeVisible()
  await expect(page.getByText('思考过程')).toBeVisible()
  await expect(page.getByText('输入缓存命中率 72%')).toBeVisible()
  await page.locator('.timing-details > summary').click()
  await expect(page.getByText('首字时延')).toBeVisible()
  await expect(page.locator('.timing-popover').getByText('模型调用 1', { exact: true })).toBeVisible()
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
  await expect(page.locator('.chat-message.user').getByRole('button', { name: /进入/ })).toHaveCount(0)
  await expect(page.locator('.chat-message.assistant').getByRole('button', { name: '进入账本' })).toBeVisible()
  await expect(page.getByText('费用 ¥0.003180')).toBeVisible()
  await page.reload()
  await expect(page.locator('.chat-workspace-head').getByText('历史账本查询', { exact: true })).toBeVisible()
  await expect(page.getByText('1,336 Tokens')).toBeVisible()
  await expect(page.getByText('费用 ¥0.003180')).toBeVisible()
  await expect(page.locator('.chat-message.user').getByRole('button', { name: /进入/ })).toHaveCount(0)
})

test('filters account and category action fields by typing', async ({ page }) => {
  const now = new Date().toISOString()
  const action = {
    status: 'NEEDS_INPUT', summary: '请补充记账所需信息', actionId: 'action-ledger-1', expiresAt: now,
    structuredContent: {
      actionType: 'ledger.transaction.create',
      input: { bookId: 'book-1', kind: 'EXPENSE', amount: 29.9, accountId: null, categoryId: null, occurredOn: '2026-09-23', note: '买梯子' },
      fields: [
        { name: 'accountId', label: '账户', type: 'entity-picker', required: true, options: [{ value: 'cash', label: '现金' }, { value: 'boc', label: '中行卡' }] },
        { name: 'categoryId', label: '二级分类', type: 'entity-picker', required: true, options: [{ value: 'meal', label: '午餐' }, { value: 'software', label: '软件' }] },
      ],
    },
  }
  await setupAgent(page, 'agent-entity-picker-e2e', {
    sessions: [{ id: 'session-1', title: '记账补充', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [
      { id: 1, turnId: 'turn-action', role: 'user', content: '帮我记账', metadataJson: null, createdAt: now },
      { id: 2, turnId: 'turn-action', role: 'assistant', content: '还需要补充两个字段。', metadataJson: JSON.stringify({ ...response, actions: [action] }), createdAt: now },
    ],
  })
  await page.route('**/api/v1/agent/actions/action-ledger-1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'WAITING_INPUT' } }) }))
  await page.route('**/api/v1/agent/actions/action-ledger-1/reject', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { id: 'action-ledger-1', status: 'CANCELLED' } }) }))
  await page.goto('/')

  const account = page.getByRole('combobox', { name: '账户筛选' })
  await account.fill('中行')
  const accountOptions = page.getByRole('listbox', { name: '账户候选项' })
  await expect(accountOptions.getByRole('option')).toHaveCount(1)
  await accountOptions.getByRole('option', { name: '中行卡' }).click()
  await expect(account).toHaveValue('中行卡')

  const category = page.getByRole('combobox', { name: '二级分类筛选' })
  await category.fill('软件')
  const categoryOptions = page.getByRole('listbox', { name: '二级分类候选项' })
  await expect(categoryOptions.getByRole('option')).toHaveCount(1)
  await categoryOptions.getByRole('option', { name: '软件' }).click()
  await expect(category).toHaveValue('软件')
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByText('已取消，本次未写入任何数据')).toBeVisible()
  await expect(page.getByRole('combobox', { name: '账户筛选' })).toBeHidden()
})

test('falls back before the first SSE event', async ({ page }) => {
  const state = await setupAgent(page, 'agent-fallback-e2e')
  await page.route('**/api/v1/agent/sessions/*/turns', route => route.fulfill({ status: 503, contentType: 'application/json', body: '{}' }))
  await page.route('**/api/v1/ai/chat', route => {
    const createdAt = new Date().toISOString()
    const fallback = { ...response, content: '已通过降级路径完成查询。', firstTokenMs: 0 }
    state.queueState.setMessages([
      { id: 1, turnId: 'fallback-turn', role: 'user', content: '降级测试', metadataJson: null, createdAt },
      { id: 2, turnId: 'fallback-turn', role: 'assistant', content: fallback.content, metadataJson: JSON.stringify(fallback), createdAt },
    ])
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: fallback }) })
  })
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

test('restores a server queued follow-up and stops following as soon as the user scrolls up', async ({ page }) => {
  const now = new Date().toISOString()
  let serverState
  await setupAgent(page, 'agent-queue-e2e', {
    onQueueChange(state, item) {
      serverState = state
      setTimeout(() => {
        state.setQueue([])
        state.setMessages([
          { id: 1, turnId: 'turn-1', role: 'user', content: '第一条消息', metadataJson: null, createdAt: now },
          { id: 2, turnId: 'turn-1', role: 'assistant', content: '第一条回答\n\n'.repeat(80), metadataJson: JSON.stringify(response), createdAt: now },
          { id: 3, turnId: item.id, role: 'user', content: item.userMessage, metadataJson: null, createdAt: now },
          { id: 4, turnId: item.id, role: 'assistant', content: '第二条排队回答', metadataJson: JSON.stringify({ ...response, content: '第二条排队回答' }), createdAt: now },
        ])
      }, 900)
    },
  })
  await page.route('**/api/v1/agent/sessions/*/turns', async route => {
    await new Promise(resolve => setTimeout(resolve, 700))
    const turnResponse = { ...response, content: '第一条回答\n\n'.repeat(80) }
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: [
        `event: turn.received\ndata: ${JSON.stringify({ turnId: 'turn-1', status: 'RECEIVED', replayed: false })}\n\n`,
        `event: assistant.delta\ndata: ${JSON.stringify({ turnId: 'turn-1', content: turnResponse.content })}\n\n`,
        `event: turn.completed\ndata: ${JSON.stringify({ turnId: 'turn-1', response: turnResponse })}\n\n`,
      ].join(''),
    })
  })

  await page.goto('/')
  const composer = page.getByLabel('给 AI 发送消息')
  await composer.fill('第一条消息')
  await page.getByRole('button', { name: '发送' }).click()
  await composer.fill('第二条消息')
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.getByText('队列 1 条')).toBeVisible()
  await expect(page.getByText('第二条消息', { exact: true })).toBeVisible()
  expect(serverState).toBeTruthy()
  await page.reload()
  await expect(page.getByText('第二条消息', { exact: true })).toBeVisible()
  await expect(page.getByText('第二条排队回答', { exact: true })).toBeVisible({ timeout: 5_000 })
  await expect(page.getByText('队列 1 条')).toBeHidden()

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
  const state = await setupAgent(page, 'agent-queue-order-e2e')
  await page.route('**/api/v1/agent/sessions/*/turns', async route => {
    await new Promise(resolve => setTimeout(resolve, 1200))
    const turnResponse = { ...response, content: '已处理：第一条消息' }
    await route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: [
        `event: turn.received\ndata: ${JSON.stringify({ turnId: 'turn-running', status: 'RECEIVED', replayed: false })}\n\n`,
        `event: assistant.delta\ndata: ${JSON.stringify({ turnId: 'turn-running', content: turnResponse.content })}\n\n`,
        `event: turn.completed\ndata: ${JSON.stringify({ turnId: 'turn-running', response: turnResponse })}\n\n`,
      ].join(''),
    })
  })

  await page.goto('/')
  const composer = page.getByLabel('给 AI 发送消息')
  for (const content of ['第一条消息', '第二条消息', '第三条消息']) {
    await composer.fill(content)
    await page.getByRole('button', { name: '发送' }).click()
  }
  await expect(page.getByText('队列 2 条')).toBeVisible()
  await page.locator('.prompt-queue li').filter({ hasText: '第三条消息' }).dragTo(page.locator('.prompt-queue li').filter({ hasText: '第二条消息' }))
  await expect.poll(() => state.queueState.queueRequests.filter(item => item.type === 'reorder').at(-1)?.turnIds).toEqual([
    state.queueState.queue.find(item => item.userMessage === '第三条消息')?.id,
    state.queueState.queue.find(item => item.userMessage === '第二条消息')?.id,
  ])
  await page.reload()
  const queueRows = page.locator('.prompt-queue li p')
  await expect(queueRows.nth(0)).toContainText('第三条消息')
  await expect(queueRows.nth(1)).toContainText('第二条消息')
})

test('cancels a running server turn from the queue', async ({ page }) => {
  const now = new Date().toISOString()
  const state = await setupAgent(page, 'agent-cancel-e2e', {
    sessions: [{ id: 'session-1', title: '取消测试', createdAt: now, updatedAt: now, archivedAt: null }],
    queue: [{ id: 'turn-running', sessionId: 'session-1', clientRequestId: 'request-running', status: 'PLANNING', userMessage: '正在处理的问题', createdAt: now }],
  })
  await page.goto('/')
  await expect(page.locator('.prompt-queue li').filter({ hasText: '正在处理的问题' })).toBeVisible()
  await page.getByRole('button', { name: '停止生成' }).click()
  await expect.poll(() => state.queueState.queueRequests.some(item => item.type === 'cancel' && item.id === 'turn-running')).toBeTruthy()
  await expect(page.locator('.prompt-queue li').filter({ hasText: '正在处理的问题' })).toBeHidden()
})

test('retries a failed restored turn only once', async ({ page }) => {
  const now = new Date().toISOString()
  const state = await setupAgent(page, 'agent-retry-e2e', {
    sessions: [{ id: 'session-1', title: '重试测试', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [
      { id: 1, turnId: 'turn-failed', role: 'user', content: '失败的问题', metadataJson: null, createdAt: now },
      { id: 2, turnId: 'turn-failed', role: 'assistant', content: '模型暂时不可用', metadataJson: JSON.stringify({ status: 'FAILED' }), createdAt: now },
    ],
    failedTurns: { 'turn-failed': { sessionId: 'session-1', userMessage: '失败的问题' } },
  })
  await page.goto('/')
  const retry = page.getByRole('button', { name: '重试这条消息' })
  await expect(retry).toBeVisible()
  await retry.click()
  await expect(page.getByText('失败的问题', { exact: true }).last()).toBeVisible()
  await retry.click()
  await expect.poll(() => state.queueState.queue.filter(item => item.retryOfTurnId === 'turn-failed').length).toBe(1)
  expect(state.queueState.queueRequests.filter(item => item.type === 'retry')).toHaveLength(2)
})
