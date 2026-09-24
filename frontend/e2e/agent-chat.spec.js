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

test('shows the complete ledger card and returns from preview to editing', async ({ page }) => {
  const now = new Date().toISOString()
  const fields = [
    { name: 'kind', label: '收支类型', type: 'select', required: true, options: [{ value: 'EXPENSE', label: '支出' }, { value: 'INCOME', label: '收入' }] },
    { name: 'amount', label: '金额', type: 'money', required: true },
    { name: 'occurredOn', label: '发生日期', type: 'date', required: true },
    { name: 'accountId', label: '账户', type: 'entity-picker', required: true, options: [{ value: 'boc', label: '中行卡' }] },
    { name: 'categoryId', label: '二级分类', type: 'entity-picker', required: true, options: [{ value: 'software', label: '学习进修 / 软件' }] },
    { name: 'merchantId', label: '商家 / 对方', type: 'entity-picker', required: false, options: [{ value: 'relay', label: '中转站' }] },
    { name: 'memberId', label: '成员', type: 'entity-picker', required: false, options: [{ value: 'me', label: '我' }] },
    { name: 'projectId', label: '项目', type: 'entity-picker', required: false, options: [{ value: 'growth', label: '个人成长' }] },
    { name: 'note', label: '备注', type: 'textarea', required: false },
  ]
  const input = { bookId: 'book-1', kind: 'EXPENSE', amount: 50, occurredOn: '2026-09-24', accountId: 'boc', categoryId: 'software', merchantId: 'relay', memberId: 'me', projectId: 'growth', note: '中转站' }
  const action = {
    status: 'NEEDS_CONFIRMATION', summary: '请确认这笔支出', actionId: 'action-preview-1', expiresAt: now,
    structuredContent: { actionType: 'ledger.transaction.create', input, suggestedFields: ['accountId', 'categoryId', 'merchantId'], fields, preview: { ...input, accountName: '中行卡', categoryPath: '学习进修 / 软件', merchantName: '中转站', memberName: '我', projectName: '个人成长' } },
  }
  await setupAgent(page, 'agent-complete-ledger-card', {
    sessions: [{ id: 'session-1', title: '完整记账卡片', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [
      { id: 1, turnId: 'turn-action', role: 'user', content: '中转站花了 50，也是软件里', metadataJson: null, createdAt: now },
      { id: 2, turnId: 'turn-action', role: 'assistant', content: '已整理好完整记账预览。', metadataJson: JSON.stringify({ ...response, actions: [action] }), createdAt: now },
    ],
  })
  await page.route('**/api/v1/agent/actions/action-preview-1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'WAITING_CONFIRMATION' } }) }))
  await page.route('**/api/v1/agent/actions/action-preview-1/answer', async route => {
    const body = route.request().postDataJSON()
    const next = { ...action, actionId: 'action-preview-2', structuredContent: { ...action.structuredContent, input: body, preview: { ...action.structuredContent.preview, ...body, amount: Number(body.amount) } } }
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: next }) })
  })
  await page.goto('/')

  await expect(page.getByText('学习进修 / 软件')).toBeVisible()
  await expect(page.getByText('个人成长')).toBeVisible()
  await page.getByRole('button', { name: '返回编辑' }).click()
  await expect(page.getByLabel('金额')).toHaveValue('50')
  await expect(page.getByRole('combobox', { name: '账户筛选' })).toHaveValue('中行卡')
  await expect(page.getByText('智能匹配')).toHaveCount(3)
  await page.getByLabel('金额').fill('58.8')

  await page.evaluate(() => { localStorage.setItem('st_theme', 'dark'); localStorage.setItem('st_accent', 'sun') })
  await page.reload()
  await page.getByRole('button', { name: '返回编辑' }).click()
  const primary = page.getByRole('button', { name: '生成预览' })
  const colors = await primary.evaluate(element => { const style = getComputedStyle(element); return { color: style.color, background: style.backgroundColor } })
  expect(colors.color).not.toBe(colors.background)
  await primary.click()
  await expect(page.getByRole('button', { name: '确认并保存' })).toBeVisible()
})

test('edits a ledger transaction from diff preview and commits the regenerated action', async ({ page }) => {
  const now = new Date().toISOString()
  const fields = [
    { name: 'kind', label: '收支类型', type: 'select', required: true, options: [{ value: 'EXPENSE', label: '支出' }, { value: 'INCOME', label: '收入' }] },
    { name: 'amount', label: '金额', type: 'money', required: true },
    { name: 'occurredOn', label: '发生日期', type: 'date', required: true },
    { name: 'accountId', label: '账户', type: 'entity-picker', required: true, options: [{ value: 'cash', label: '现金' }] },
    { name: 'categoryId', label: '二级分类', type: 'entity-picker', required: true, options: [{ value: 'meal', label: '餐饮 / 午餐', kind: 'EXPENSE' }] },
    { name: 'merchantId', label: '商家 / 对方', type: 'entity-picker', required: false, options: [] },
    { name: 'memberId', label: '成员', type: 'entity-picker', required: false, options: [{ value: 'me', label: '我' }] },
    { name: 'projectId', label: '项目', type: 'entity-picker', required: false, options: [] },
    { name: 'note', label: '备注', type: 'textarea', required: false },
  ]
  const original = { kind: 'EXPENSE', amount: 29.9, accountId: 'cash', accountName: '现金', categoryId: 'meal', categoryPath: '餐饮 / 午餐', memberId: 'me', memberName: '我', occurredOn: '2026-09-24', note: 'normal', revision: 4 }
  const makeAction = (id, amount, note) => ({
    status: 'NEEDS_CONFIRMATION', summary: '请确认修改这笔流水', actionId: id, expiresAt: now,
    structuredContent: {
      actionType: 'ledger.transaction.update', fields, original,
      input: { bookId: 'book-1', transactionId: 'transaction-1', kind: 'EXPENSE', amount, accountId: 'cash', categoryId: 'meal', memberId: 'me', occurredOn: '2026-09-24', note },
      preview: { ...original, amount, note },
      diff: [
        { label: '金额', before: '¥29.9', after: `¥${amount}` },
        { label: '备注', before: 'normal', after: note },
      ],
    },
  })
  const action = makeAction('update-ledger-1', 35, 'release')
  const calls = []
  await setupAgent(page, 'agent-update-ledger-card', {
    sessions: [{ id: 'session-1', title: '修改流水', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [
      { id: 1, turnId: 'turn-update', role: 'user', content: '把这笔午餐改成 35 元', metadataJson: null, createdAt: now },
      { id: 2, turnId: 'turn-update', role: 'assistant', content: '已生成修改预览。', metadataJson: JSON.stringify({ ...response, actions: [action] }), createdAt: now },
    ],
  })
  for (const id of ['update-ledger-1', 'update-ledger-2']) {
    await page.route(`**/api/v1/agent/actions/${id}`, route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'WAITING_CONFIRMATION' } }) }))
  }
  await page.route('**/api/v1/agent/actions/update-ledger-1/answer', async route => {
    const body = route.request().postDataJSON()
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: makeAction('update-ledger-2', Number(body.amount), body.note) }) })
  })
  await page.route('**/api/v1/agent/actions/update-ledger-2/approve', route => { calls.push('approve'); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'APPROVED' } }) }) })
  await page.route('**/api/v1/agent/actions/update-ledger-2/commit', route => { calls.push('commit'); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'COMPLETED', summary: '账本流水已修改', structuredContent: { id: 'transaction-1', revision: 5 } } }) }) })
  await page.goto('/')

  const diff = page.getByLabel('修改差异')
  await expect(diff).toContainText('¥29.9')
  await expect(diff).toContainText('¥35')
  await expect(diff).toContainText('normal')
  await expect(diff).toContainText('release')
  await page.getByRole('button', { name: '返回编辑' }).click()
  await page.getByLabel('金额').fill('38.8')
  await page.getByLabel('备注').fill('final')
  await page.getByRole('button', { name: '生成预览' }).click()
  await expect(diff).toContainText('¥38.8')
  await page.getByRole('button', { name: '确认并保存' }).click()
  await expect.poll(() => calls).toEqual(['approve', 'commit'])
  await expect(page.locator('.agent-action-result b')).toHaveText('账本流水已修改')
})

test('shows worktime update derived differences without horizontal overflow', async ({ page }) => {
  const now = new Date().toISOString()
  const action = {
    status: 'NEEDS_CONFIRMATION', summary: '请确认修改工时记录', actionId: 'update-worktime-1', expiresAt: now,
    structuredContent: {
      actionType: 'worktime.record.update',
      input: { recordId: 9, date: '2026-09-23', start: '09:00', end: '21:00', rest: 90, note: 'release' },
      fields: [
        { name: 'date', label: '日期', type: 'date', required: true },
        { name: 'start', label: '开始时间', type: 'time', required: true },
        { name: 'end', label: '结束时间', type: 'time', required: false },
        { name: 'rest', label: '额外休息（分钟）', type: 'number', required: false },
        { name: 'note', label: '备注', type: 'textarea', required: false },
      ],
      preview: { date: '2026-09-23', start: '09:00', end: '21:00', rest: 90, overtimeMin: 150, realHourlyWage: 38.1, note: 'release' },
      diff: [
        { label: '结束时间', before: '18:00', after: '21:00' },
        { label: '额外休息', before: '60 分钟', after: '90 分钟' },
        { label: '加班时间', before: '0 分钟', after: '150 分钟' },
        { label: '实际时薪', before: '¥50', after: '¥38.1' },
      ],
    },
  }
  await setupAgent(page, 'agent-update-worktime-card', {
    sessions: [{ id: 'session-1', title: '修改工时', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [{ id: 1, turnId: 'turn-update-worktime', role: 'assistant', content: '已重新计算工时。', metadataJson: JSON.stringify({ ...response, actions: [action] }), createdAt: now }],
  })
  await page.route('**/api/v1/agent/actions/update-worktime-1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'WAITING_CONFIRMATION' } }) }))
  await page.goto('/')

  const diff = page.getByLabel('修改差异')
  await expect(diff).toContainText('加班时间')
  await expect(diff).toContainText('150 分钟')
  await expect(diff).toContainText('实际时薪')
  const fitsViewport = await diff.evaluate(element => element.getBoundingClientRect().right <= document.documentElement.clientWidth)
  expect(fitsViewport).toBe(true)
})

test('confirms multiple ledger actions independently from the batch summary', async ({ page }) => {
  const now = new Date().toISOString()
  const makeAction = (id, amount, note) => ({
    status: 'NEEDS_CONFIRMATION', summary: `请确认${note}`, actionId: id, expiresAt: now,
    structuredContent: {
      actionType: 'ledger.transaction.create', input: { bookId: 'book-1', kind: 'EXPENSE', amount, accountId: 'boc', categoryId: 'software', occurredOn: '2026-09-24', note },
      preview: { kind: 'EXPENSE', amount, accountName: '中行卡', categoryPath: '学习进修 / 软件', occurredOn: '2026-09-24', note },
      fields: [{ name: 'amount', label: '金额', type: 'money', required: true }],
    },
  })
  const actions = [makeAction('batch-action-1', 29.9, '买梯子'), makeAction('batch-action-2', 50, '中转站')]
  const calls = []
  await setupAgent(page, 'agent-batch-ledger-card', {
    sessions: [{ id: 'session-1', title: '多笔记账', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [
      { id: 1, turnId: 'turn-batch', role: 'user', content: '记两笔软件支出', metadataJson: null, createdAt: now },
      { id: 2, turnId: 'turn-batch', role: 'assistant', content: '已生成两笔独立预览。', metadataJson: JSON.stringify({ ...response, actions }), createdAt: now },
    ],
  })
  for (const action of actions) {
    await page.route(`**/api/v1/agent/actions/${action.actionId}`, route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'WAITING_CONFIRMATION' } }) }))
    await page.route(`**/api/v1/agent/actions/${action.actionId}/approve`, route => { calls.push(`approve:${action.actionId}`); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'APPROVED' } }) }) })
    await page.route(`**/api/v1/agent/actions/${action.actionId}/commit`, route => { calls.push(`commit:${action.actionId}`); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'COMPLETED', summary: '账本流水已保存', structuredContent: { id: `transaction-${action.actionId}` } } }) }) })
  }
  await page.goto('/')
  await expect(page.getByText('2 笔待处理')).toBeVisible()
  await expect(page.getByText('支出合计 ¥79.90 · 收入合计 ¥0.00')).toBeVisible()
  await page.getByRole('button', { name: '全部确认' }).click()
  await expect.poll(() => calls).toEqual([
    'approve:batch-action-1', 'commit:batch-action-1',
    'approve:batch-action-2', 'commit:batch-action-2',
  ])
  await expect(page.getByText('已完成')).toHaveCount(2)
  await expect(page.getByText('2 笔待处理')).toBeHidden()
})

test('falls back before the first SSE event', async ({ page }) => {
  const state = await setupAgent(page, 'agent-fallback-e2e')
  await page.route('**/api/v1/agent/turns/fallback-turn/trace', route => route.fulfill({ status: 404, contentType: 'application/json', body: '{}' }))
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

test('keeps the bottom anchor stable when streaming switches to the completed reply', async ({ page }) => {
  const now = new Date().toISOString()
  const previousAnswer = Array.from({ length: 70 }, (_, index) => `历史回复第 ${index + 1} 行`).join('\n\n')
  await setupAgent(page, 'agent-stream-bottom-anchor', {
    sessions: [{ id: 'session-1', title: '流式滚动稳定性', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [
      { id: 1, turnId: 'turn-old', role: 'user', content: '历史问题', metadataJson: null, createdAt: now },
      { id: 2, turnId: 'turn-old', role: 'assistant', content: previousAnswer, metadataJson: JSON.stringify(response), createdAt: now },
    ],
  })
  await page.goto('/')
  const viewport = page.locator('.chat-message-viewport')
  await page.getByLabel('给 AI 发送消息').fill('测试流式结束位置')
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.getByText('已查询到一个').last()).toBeVisible()

  const samples = await viewport.evaluate(async element => {
    const rows = []
    const deadline = performance.now() + 500
    while (performance.now() < deadline) {
      rows.push({ top: element.scrollTop, gap: element.scrollHeight - element.scrollTop - element.clientHeight })
      await new Promise(resolve => requestAnimationFrame(resolve))
    }
    return rows
  })
  expect(Math.max(...samples.map(item => item.gap))).toBeLessThanOrEqual(2)
  expect(Math.max(...samples.map(item => item.top)) - Math.min(...samples.map(item => item.top))).toBeLessThan(160)
  await expect(page.getByRole('button', { name: '滚动到底部并继续跟随' })).toBeHidden()
})

test('keeps the prompt queue outside the message viewport on desktop and mobile', async ({ page }) => {
  const now = new Date().toISOString()
  const longAnswer = Array.from({ length: 18 }, (_, index) => `这是用于验证队列布局的第 ${index + 1} 行回复。`).join('\n')
  await setupAgent(page, 'agent-queue-layout-e2e', {
    sessions: [{ id: 'session-1', title: '队列布局测试', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [
      { id: 1, turnId: 'turn-old', role: 'user', content: '请给我一段较长的回复', metadataJson: null, createdAt: now },
      { id: 2, turnId: 'turn-old', role: 'assistant', content: longAnswer, metadataJson: JSON.stringify(response), createdAt: now },
    ],
    queue: Array.from({ length: 4 }, (_, index) => ({ id: `turn-queued-${index}`, sessionId: 'session-1', clientRequestId: `request-${index}`, status: 'QUEUED', userMessage: `等待处理的消息 ${index + 1}`, createdAt: now })),
  })

  await page.goto('/')
  await expect(page.getByText('队列 4 条')).toBeVisible()
  const geometry = await page.evaluate(() => {
    const viewport = document.querySelector('.chat-message-viewport').getBoundingClientRect()
    const composer = document.querySelector('.chat-composer-wrap').getBoundingClientRect()
    const queue = document.querySelector('.prompt-queue').getBoundingClientRect()
    const lastMessage = document.querySelector('.chat-message:last-child').getBoundingClientRect()
    return { viewportBottom: viewport.bottom, composerTop: composer.top, queueTop: queue.top, lastMessageBottom: lastMessage.bottom }
  })
  expect(Math.abs(geometry.viewportBottom - geometry.composerTop)).toBeLessThanOrEqual(1)
  expect(geometry.queueTop).toBeGreaterThanOrEqual(geometry.viewportBottom - 1)
  expect(geometry.lastMessageBottom).toBeLessThanOrEqual(geometry.viewportBottom + 1)
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
  const successToast = page.locator('.toast-notice.type-success')
  await expect(successToast).toContainText('已停止生成')
  expect(await successToast.locator('.toast-icon').evaluate(element => {
    const probe = document.createElement('span')
    probe.style.color = 'var(--up)'
    document.body.appendChild(probe)
    const matches = getComputedStyle(element).color === getComputedStyle(probe).color
    probe.remove()
    return matches
  })).toBe(true)
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
