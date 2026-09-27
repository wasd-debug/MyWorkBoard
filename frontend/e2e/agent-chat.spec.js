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

test('requires explicit selection for ambiguous ledger entities and keeps resolved fields', async ({ page }) => {
  const now = new Date().toISOString()
  const fields = [
    { name: 'kind', label: '收支类型', type: 'select', required: true, options: [{ value: 'EXPENSE', label: '支出' }, { value: 'INCOME', label: '收入' }] },
    { name: 'amount', label: '金额', type: 'money', required: true },
    { name: 'occurredOn', label: '发生日期', type: 'date', required: true },
    { name: 'accountId', label: '账户', type: 'entity-picker', required: true, options: [
      { value: 'boc-credit', label: '中行信用卡', description: 'CREDIT_CARD · CNY · 余额 -1280' },
      { value: 'boc-debit', label: '中行储蓄卡', description: 'BANK · CNY · 余额 5000' },
    ] },
    { name: 'categoryId', label: '二级分类', type: 'entity-picker', required: true, options: [
      { value: 'study-software', label: '学习进修 / 软件', kind: 'EXPENSE' },
      { value: 'work-software', label: '工作支出 / 软件', kind: 'EXPENSE' },
    ] },
    { name: 'memberId', label: '成员', type: 'entity-picker', required: false, options: [{ value: 'me', label: '我', description: 'alice · 所有者' }] },
    { name: 'note', label: '备注', type: 'textarea', required: false },
  ]
  const action = {
    status: 'NEEDS_INPUT', summary: '请选择存在歧义的记账信息', actionId: 'action-ambiguous-1', expiresAt: now,
    structuredContent: {
      actionType: 'ledger.transaction.create',
      input: { bookId: 'book-1', kind: 'EXPENSE', amount: 29.9, occurredOn: '2026-09-24', accountId: null, accountName: '中行', categoryId: null, categoryName: '软件', memberId: 'me', note: '买梯子' },
      suggestedFields: ['memberId'], ambiguousFields: ['accountId', 'categoryId'],
      entityMatches: { accountId: { status: 'ambiguous', query: '中行' }, categoryId: { status: 'ambiguous', query: '软件' }, memberId: { status: 'suggested' } },
      fields,
    },
  }
  await setupAgent(page, 'agent-ambiguous-entity-e2e', {
    sessions: [{ id: 'session-1', title: '实体消歧', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [
      { id: 1, turnId: 'turn-action', role: 'user', content: '中行买软件 29.9', metadataJson: null, createdAt: now },
      { id: 2, turnId: 'turn-action', role: 'assistant', content: '找到了多个可能的账户和分类，请选择。', metadataJson: JSON.stringify({ ...response, actions: [action] }), createdAt: now },
    ],
  })
  await page.route('**/api/v1/agent/actions/action-ambiguous-1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'WAITING_INPUT' } }) }))
  await page.route('**/api/v1/agent/actions/action-ambiguous-1/answer', async route => {
    const body = route.request().postDataJSON()
    expect(body).toMatchObject({ accountId: 'boc-debit', categoryId: 'study-software', memberId: 'me', amount: 29.9 })
    const next = {
      ...action, status: 'NEEDS_CONFIRMATION', summary: '请确认这笔支出', actionId: 'action-ambiguous-2',
      structuredContent: { ...action.structuredContent, input: body, ambiguousFields: [], entityMatches: { accountId: { status: 'exact' }, categoryId: { status: 'exact' }, memberId: { status: 'exact' } }, preview: { ...body, accountName: '中行储蓄卡', categoryPath: '学习进修 / 软件', memberName: '我' } },
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: next }) })
  })
  await page.goto('/')

  await expect(page.getByText('多个候选 · 请选择')).toHaveCount(2)
  const account = page.getByRole('combobox', { name: '账户筛选' })
  await expect(account).toHaveValue('中行')
  await account.click()
  await expect(page.getByText('BANK · CNY · 余额 5000')).toBeVisible()
  await page.getByRole('option', { name: /中行储蓄卡/ }).click()
  const category = page.getByRole('combobox', { name: '二级分类筛选' })
  await expect(category).toHaveValue('软件')
  await category.click()
  await page.getByRole('option', { name: '学习进修 / 软件' }).click()
  await expect(page.getByRole('combobox', { name: '成员筛选' })).toHaveValue('我')
  await page.getByRole('button', { name: '生成预览' }).click()
  await expect(page.getByRole('button', { name: '确认并保存' })).toBeVisible()
  await expect(page.getByText('中行储蓄卡')).toBeVisible()
  await expect(page.getByText('学习进修 / 软件')).toBeVisible()
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

test('confirms a destructive ledger deletion only after showing its full impact', async ({ page }) => {
  const now = new Date().toISOString()
  const action = {
    status: 'NEEDS_CONFIRMATION', summary: '请确认删除这笔流水', actionId: 'delete-ledger-1', expiresAt: now,
    structuredContent: {
      actionType: 'ledger.transaction.delete',
      input: { bookId: 'book-1', transactionId: 'transaction-1' },
      preview: { kind: 'EXPENSE', amount: 29.9, accountName: '现金', categoryPath: '餐饮 / 午餐', merchantName: '中转站', memberName: '我', projectName: '个人成长', occurredOn: '2026-09-24', note: '午餐' },
      effects: ['该流水将从账本列表、报表和统计中移除', '对应账户余额和本地账本投影将重新计算', '删除会进入现有回收站和审计链路，不会立即永久清除'],
    },
  }
  const calls = []
  await setupAgent(page, 'agent-delete-ledger-card', {
    sessions: [{ id: 'session-1', title: '删除流水', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [{ id: 1, turnId: 'turn-delete-ledger', role: 'assistant', content: '已找到唯一流水。', metadataJson: JSON.stringify({ ...response, actions: [action] }), createdAt: now }],
  })
  await page.route('**/api/v1/agent/actions/delete-ledger-1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'WAITING_CONFIRMATION' } }) }))
  await page.route('**/api/v1/agent/actions/delete-ledger-1/approve', route => { calls.push('approve'); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'APPROVED' } }) }) })
  await page.route('**/api/v1/agent/actions/delete-ledger-1/commit', route => { calls.push('commit'); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'COMPLETED', summary: '账本流水已删除', structuredContent: { id: 'transaction-1', deleted: true } } }) }) })
  await page.goto('/')

  const card = page.locator('.agent-action-card.destructive')
  await expect(card).toContainText('餐饮 / 午餐')
  await expect(card).toContainText('中转站')
  await expect(card.getByLabel('操作影响')).toContainText('账户余额')
  await expect(card.getByRole('button', { name: '确认删除' })).toBeVisible()
  await card.getByRole('button', { name: '确认删除' }).click()
  await expect.poll(() => calls).toEqual(['approve', 'commit'])
  await expect(card.locator('.agent-action-result b')).toHaveText('账本流水已删除')
})

test('can cancel a worktime deletion without calling approve or commit', async ({ page }) => {
  const now = new Date().toISOString()
  const action = {
    status: 'NEEDS_CONFIRMATION', summary: '请确认删除这条工时记录', actionId: 'delete-worktime-1', expiresAt: now,
    structuredContent: {
      actionType: 'worktime.record.delete', input: { recordId: 9 },
      preview: { id: 9, date: '2026-09-23', start: '09:00', end: '21:00', rest: 60, overtimeMin: 150, realHourlyWage: 38.1, note: 'release' },
      effects: ['该记录将从工时列表和统计中移除', '相关加班时间和实际时薪统计将按剩余记录重新展示', '删除成功后会刷新本地工时数据'],
    },
  }
  const calls = []
  await setupAgent(page, 'agent-delete-worktime-card', {
    sessions: [{ id: 'session-1', title: '删除工时', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [{ id: 1, turnId: 'turn-delete-worktime', role: 'assistant', content: '已找到唯一工时记录。', metadataJson: JSON.stringify({ ...response, actions: [action] }), createdAt: now }],
  })
  await page.route('**/api/v1/agent/actions/delete-worktime-1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'WAITING_CONFIRMATION' } }) }))
  await page.route('**/api/v1/agent/actions/delete-worktime-1/reject', route => { calls.push('reject'); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'DENIED' } }) }) })
  await page.route('**/api/v1/agent/actions/delete-worktime-1/approve', route => { calls.push('approve'); return route.abort() })
  await page.route('**/api/v1/agent/actions/delete-worktime-1/commit', route => { calls.push('commit'); return route.abort() })
  await page.goto('/')

  const card = page.locator('.agent-action-card.destructive')
  await expect(card).toContainText('2026-09-23')
  await expect(card).toContainText('¥38.10')
  await card.getByRole('button', { name: '取消', exact: true }).click()
  await expect.poll(() => calls).toEqual(['reject'])
  await expect(card).toContainText('已取消，本次未写入任何数据')
})

test('edits and confirms worktime settings with history recalculation', async ({ page }) => {
  const now = new Date().toISOString()
  const fields = [
    { name: 'salaryPre', label: '税前月薪', type: 'money', required: false },
    { name: 'workStart', label: '标准上班时间', type: 'time', required: true },
    { name: 'workEnd', label: '标准下班时间', type: 'time', required: true },
    { name: 'lunchMin', label: '午休（分钟）', type: 'number', required: true },
    { name: 'lunchScope', label: '历史工时处理', type: 'select', required: true, options: [{ value: 'NONE', label: '仅影响新记录' }, { value: 'ALL', label: '重算全部历史' }, { value: 'FROM_DATE', label: '从指定日期重算' }] },
    { name: 'fromDate', label: '重算起始日期', type: 'date', required: false },
  ]
  const makeAction = (id, input) => ({
    status: 'NEEDS_CONFIRMATION', summary: '请确认修改设置并重算历史工时', actionId: id, expiresAt: now,
    structuredContent: {
      actionType: 'worktime.settings.update', fields, input, preview: input,
      diff: [{ label: '税前月薪', before: '15000', after: String(input.salaryPre) }, { label: '午休', before: '60', after: String(input.lunchMin) }],
      effects: ['将重新计算范围内工时记录的午休快照、加班分钟和实际时薪'],
    },
  })
  const action = makeAction('settings-1', { salaryPre: 18000, workStart: '09:00', workEnd: '18:00', lunchMin: 45, lunchScope: 'FROM_DATE', fromDate: '2026-09-01' })
  const calls = []
  await setupAgent(page, 'agent-settings-card', {
    sessions: [{ id: 'session-1', title: '工时设置', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [{ id: 1, turnId: 'turn-settings', role: 'assistant', content: '已生成设置修改预览。', metadataJson: JSON.stringify({ ...response, actions: [action] }), createdAt: now }],
  })
  for (const id of ['settings-1', 'settings-2']) await page.route(`**/api/v1/agent/actions/${id}`, route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'WAITING_CONFIRMATION' } }) }))
  await page.route('**/api/v1/agent/actions/settings-1/answer', async route => {
    const body = route.request().postDataJSON(); calls.push('answer')
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: makeAction('settings-2', body) }) })
  })
  await page.route('**/api/v1/agent/actions/settings-2/approve', route => { calls.push('approve'); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'APPROVED' } }) }) })
  await page.route('**/api/v1/agent/actions/settings-2/commit', route => { calls.push('commit'); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'COMPLETED', summary: '工时设置已更新', structuredContent: { recalculatedRecords: 18 } } }) }) })
  await page.goto('/')

  const card = page.locator('.agent-action-card')
  await expect(card).toContainText('从 2026-09-01 重算')
  await expect(card.getByLabel('操作影响')).toContainText('实际时薪')
  await card.getByRole('button', { name: '返回编辑' }).click()
  await page.getByLabel('午休（分钟）').fill('50')
  await card.getByRole('button', { name: '生成预览' }).click()
  await card.getByRole('button', { name: '确认并保存' }).click()
  await expect.poll(() => calls).toEqual(['answer', 'approve', 'commit'])
  await expect(card.locator('.agent-action-result b')).toHaveText('工时设置已更新')
})

test('shows ledger management card and blocks chat commit for R4 book deletion', async ({ page }) => {
  const now = new Date().toISOString()
  const action = {
    status: 'NEEDS_CONFIRMATION', summary: '账本删除属于高风险操作，当前仅生成影响预览', actionId: 'book-delete-1', expiresAt: now,
    structuredContent: {
      actionType: 'ledger.book.delete', input: { bookId: 'book-1' }, webApprovalRequired: true, commitAvailable: false,
      preview: { resourceType: '账本', operation: '删除账本', before: { id: 'book-1', name: '家庭账本', currency: 'CNY', revision: 4, memberCount: 2, transactionCount: 36 }, after: { bookId: 'book-1' } },
      effects: ['账本包含 36 笔流水和 2 名成员', '当前增量不会执行账本删除，需等待站内高风险审批中心'],
    },
  }
  await setupAgent(page, 'agent-book-delete-r4', {
    sessions: [{ id: 'session-1', title: '删除账本', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [{ id: 1, turnId: 'turn-book-delete', role: 'assistant', content: '只能先查看影响。', metadataJson: JSON.stringify({ ...response, actions: [action] }), createdAt: now }],
  })
  await page.route('**/api/v1/agent/actions/book-delete-1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'WAITING_CONFIRMATION' } }) }))
  await page.goto('/')

  const card = page.locator('.agent-action-card.destructive')
  await expect(card).toContainText('家庭账本')
  await expect(card.getByLabel('操作影响')).toContainText('36 笔流水')
  await expect(card.getByRole('button', { name: '需要站内高风险审批' })).toBeDisabled()
  await expect(card.getByRole('button', { name: '确认删除' })).toHaveCount(0)
})

test('edits and confirms a category budget with usage details', async ({ page }) => {
  const now = new Date().toISOString()
  const fields = [
    { name: 'monthKey', label: '预算月份', type: 'month', required: true },
    { name: 'categoryId', label: '支出分类（留空为总预算）', type: 'entity-picker', required: false, options: [{ value: 'food', label: '餐饮', kind: 'EXPENSE' }] },
    { name: 'budget', label: '预算金额', type: 'money', required: true },
  ]
  const makeAction = (id, budget) => ({
    status: 'NEEDS_CONFIRMATION', summary: '请确认设置预算', actionId: id, expiresAt: now,
    structuredContent: {
      actionType: 'ledger.budget.upsert', fields,
      input: { bookId: 'book-1', monthKey: '2026-10', categoryId: 'food', categoryName: '餐饮', scope: 'CATEGORY', budget, spent: 300, usageRate: Number((30000 / budget).toFixed(2)) },
      preview: { resourceType: '预算', operation: '设置预算', monthKey: '2026-10', categoryName: '餐饮', budget, spent: 300, usageRate: Number((30000 / budget).toFixed(2)), after: { monthKey: '2026-10', categoryId: 'food', budget } },
      diff: [{ label: '预算金额', before: '1500', after: String(budget) }],
    },
  })
  const calls = []
  await setupAgent(page, 'agent-budget-card', {
    sessions: [{ id: 'session-1', title: '设置预算', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [{ id: 1, turnId: 'turn-budget', role: 'assistant', content: '已生成预算调整预览。', metadataJson: JSON.stringify({ ...response, actions: [makeAction('budget-1', 1500)] }), createdAt: now }],
  })
  for (const id of ['budget-1', 'budget-2']) await page.route(`**/api/v1/agent/actions/${id}`, route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'WAITING_CONFIRMATION' } }) }))
  await page.route('**/api/v1/agent/actions/budget-1/answer', async route => {
    const body = route.request().postDataJSON(); calls.push('answer')
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: makeAction('budget-2', body.budget) }) })
  })
  await page.route('**/api/v1/agent/actions/budget-2/approve', route => { calls.push('approve'); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'APPROVED' } }) }) })
  await page.route('**/api/v1/agent/actions/budget-2/commit', route => { calls.push('commit'); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'COMPLETED', summary: '预算已保存', structuredContent: { id: 'budget-food' } } }) }) })
  await page.goto('/')

  const card = page.locator('.agent-action-card')
  await expect(card).toContainText('已使用')
  await expect(card).toContainText('20.00%')
  await card.getByRole('button', { name: '返回编辑' }).click()
  await page.getByLabel('预算金额').fill('1800')
  await card.getByRole('button', { name: '生成预览' }).click()
  await expect(card).toContainText('16.67%')
  await card.getByRole('button', { name: '确认并保存' }).click()
  await expect.poll(() => calls).toEqual(['answer', 'approve', 'commit'])
  await expect(card.locator('.agent-action-result b')).toHaveText('预算已保存')
})

test('shows named resource delete impact and confirms through the controlled card', async ({ page }) => {
  const now = new Date().toISOString()
  const action = {
    status: 'NEEDS_CONFIRMATION', summary: '请确认删除商家', actionId: 'merchant-delete-1', expiresAt: now,
    structuredContent: {
      actionType: 'ledger.merchant.delete', input: { bookId: 'book-1', resourceId: 'merchant-1' },
      preview: { resourceType: '商家', operation: '删除商家', before: { id: 'merchant-1', name: '家乐福', icon: 'shop', note: '超市', hidden: false, revision: 3 }, after: { bookId: 'book-1', resourceId: 'merchant-1' } },
      effects: ['当前有 12 笔有效流水关联该商家', '删除采用软删除，历史流水中的关联信息和审计记录仍保留'],
    },
  }
  const calls = []
  await setupAgent(page, 'agent-merchant-delete-card', {
    sessions: [{ id: 'session-1', title: '删除商家', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [{ id: 1, turnId: 'turn-merchant-delete', role: 'assistant', content: '已生成删除影响预览。', metadataJson: JSON.stringify({ ...response, actions: [action] }), createdAt: now }],
  })
  await page.route('**/api/v1/agent/actions/merchant-delete-1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'WAITING_CONFIRMATION' } }) }))
  await page.route('**/api/v1/agent/actions/merchant-delete-1/approve', route => { calls.push('approve'); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'APPROVED' } }) }) })
  await page.route('**/api/v1/agent/actions/merchant-delete-1/commit', route => { calls.push('commit'); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'COMPLETED', summary: '删除商家已完成', structuredContent: { id: 'merchant-1', deleted: true } } }) }) })
  await page.goto('/')

  const card = page.locator('.agent-action-card.destructive')
  await expect(card).toContainText('家乐福')
  await expect(card.getByLabel('操作影响')).toContainText('12 笔有效流水')
  await card.getByRole('button', { name: '确认删除' }).click()
  await expect.poll(() => calls).toEqual(['approve', 'commit'])
  await expect(card.locator('.agent-action-result b')).toHaveText('删除商家已完成')
})

test('shows a complete recurring transaction preview and confirms creation', async ({ page }) => {
  const now = new Date().toISOString()
  const action = {
    status: 'NEEDS_CONFIRMATION', summary: '请确认创建周期任务', actionId: 'schedule-create-1', expiresAt: now,
    structuredContent: {
      actionType: 'ledger.schedule.create',
      input: { bookId: 'book-1', name: '每月房租', enabled: true, scheduleMode: 'CALENDAR', frequency: 'MONTHLY', intervalValue: 1, startOn: '2026-10-01', monthlyMode: 'DAY_OF_MONTH', dayOfMonth: 1, kind: 'EXPENSE', amount: 3500, accountId: 'boc', categoryId: 'rent', memberId: 'me', note: '房租' },
      fields: [{ name: 'name', label: '任务名称', type: 'text', required: true }],
      preview: { operation: '创建周期任务', after: { name: '每月房租', enabled: true, scheduleMode: 'CALENDAR', frequency: 'MONTHLY', intervalValue: 1, startOn: '2026-10-01', nextRunOn: '2026-10-01', monthlyMode: 'DAY_OF_MONTH', dayOfMonth: 1, kind: 'EXPENSE', amount: 3500, accountName: '中行卡', categoryName: '住房 / 房租', memberName: '我', note: '房租' } },
    },
  }
  const calls = []
  await setupAgent(page, 'agent-schedule-create-card', {
    sessions: [{ id: 'session-1', title: '创建周期任务', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [{ id: 1, turnId: 'turn-schedule-create', role: 'assistant', content: '已生成周期任务预览。', metadataJson: JSON.stringify({ ...response, actions: [action] }), createdAt: now }],
  })
  await page.route('**/api/v1/agent/actions/schedule-create-1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'WAITING_CONFIRMATION' } }) }))
  await page.route('**/api/v1/agent/actions/schedule-create-1/approve', route => { calls.push('approve'); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'APPROVED' } }) }) })
  await page.route('**/api/v1/agent/actions/schedule-create-1/commit', route => { calls.push('commit'); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'COMPLETED', summary: '创建周期任务已完成', structuredContent: { id: 'schedule-1' } } }) }) })
  await page.goto('/')

  const card = page.locator('.agent-action-card')
  await expect(card).toContainText('每月房租')
  await expect(card).toContainText('每月 1 日')
  await expect(card).toContainText('¥3500.00')
  await expect(card).toContainText('住房 / 房租')
  await card.getByRole('button', { name: '确认并保存' }).click()
  await expect.poll(() => calls).toEqual(['approve', 'commit'])
  await expect(card.locator('.agent-action-result b')).toHaveText('创建周期任务已完成')
})

test('uses an explicit immediate-run confirmation and shows duplicate protection', async ({ page }) => {
  const now = new Date().toISOString()
  const action = {
    status: 'NEEDS_CONFIRMATION', summary: '请确认立即执行周期任务', actionId: 'schedule-run-1', expiresAt: now,
    structuredContent: {
      actionType: 'ledger.schedule.run', input: { bookId: 'book-1', taskId: 'schedule-1' },
      preview: { operation: '立即执行周期任务', dueOn: '2026-10-01', before: { id: 'schedule-1', name: '每月房租', enabled: true, scheduleMode: 'CALENDAR', frequency: 'MONTHLY', intervalValue: 1, calendarRule: { monthlyMode: 'DAY_OF_MONTH', dayOfMonth: 1 }, nextRunOn: '2026-10-01', runCount: 2, revision: 3, payload: { kind: 'EXPENSE', amount: 3500, accountId: 'boc', categoryId: 'rent', note: '房租' } } },
      effects: ['确认后将立即生成一笔真实流水并刷新账户余额与报表', '同一任务和到期日已执行时将返回重复，不会再次记账'],
    },
  }
  const calls = []
  await setupAgent(page, 'agent-schedule-run-card', {
    sessions: [{ id: 'session-1', title: '执行周期任务', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [{ id: 1, turnId: 'turn-schedule-run', role: 'assistant', content: '执行前需要确认。', metadataJson: JSON.stringify({ ...response, actions: [action] }), createdAt: now }],
  })
  await page.route('**/api/v1/agent/actions/schedule-run-1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'WAITING_CONFIRMATION' } }) }))
  await page.route('**/api/v1/agent/actions/schedule-run-1/approve', route => { calls.push('approve'); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'APPROVED' } }) }) })
  await page.route('**/api/v1/agent/actions/schedule-run-1/commit', route => { calls.push('commit'); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'COMPLETED', summary: '立即执行周期任务已完成', structuredContent: { status: 'APPLIED', transactionId: 'transaction-1' } } }) }) })
  await page.goto('/')

  const card = page.locator('.agent-action-card')
  await expect(card).toContainText('不会再次记账')
  await expect(card.getByRole('button', { name: '确认并立即执行' })).toBeVisible()
  await card.getByRole('button', { name: '确认并立即执行' }).click()
  await expect.poll(() => calls).toEqual(['approve', 'commit'])
})

test('restores a recycle item only after explicit confirmation', async ({ page }) => {
  const now = new Date().toISOString()
  const action = {
    status: 'NEEDS_CONFIRMATION', summary: '请确认恢复“午餐”', actionId: 'recycle-restore-1', expiresAt: now,
    structuredContent: {
      actionType: 'ledger.recycle.restore', input: { bookId: 'book-1', itemId: 'transaction-1', resourceType: 'transaction' },
      preview: { type: 'transaction', id: 'transaction-1', name: '午餐', deletedAt: '2026-09-27T10:00:00Z', occurredOn: '2026-09-26', amount: 29.9, revision: 3 },
      effects: ['恢复后项目会重新出现在账本及本地同步投影中', '提交前会再次校验回收站状态和 revision'],
    },
  }
  const calls = []
  await setupAgent(page, 'agent-recycle-restore-card', {
    sessions: [{ id: 'session-1', title: '恢复流水', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [{ id: 1, turnId: 'turn-recycle', role: 'assistant', content: '已生成恢复预览。', metadataJson: JSON.stringify({ ...response, actions: [action] }), createdAt: now }],
  })
  await page.route('**/api/v1/agent/actions/recycle-restore-1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'WAITING_CONFIRMATION' } }) }))
  await page.route('**/api/v1/agent/actions/recycle-restore-1/approve', route => { calls.push('approve'); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'APPROVED' } }) }) })
  await page.route('**/api/v1/agent/actions/recycle-restore-1/commit', route => { calls.push('commit'); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'COMPLETED', summary: '回收站项目已恢复', structuredContent: { id: 'transaction-1', revision: 4 } } }) }) })
  await page.goto('/')

  const card = page.locator('.agent-action-card')
  await expect(card).toContainText('午餐')
  await expect(card).toContainText('¥29.90')
  await card.getByRole('button', { name: '确认并恢复' }).click()
  await expect.poll(() => calls).toEqual(['approve', 'commit'])
  await expect(card.locator('.agent-action-result b')).toHaveText('回收站项目已恢复')
})

test('downloads an authenticated ledger export after confirmation', async ({ page }) => {
  const now = new Date().toISOString()
  const action = {
    status: 'NEEDS_CONFIRMATION', summary: '请确认导出 12 笔流水', actionId: 'export-1', expiresAt: now,
    structuredContent: {
      actionType: 'ledger.export', input: { bookId: 'book-1', format: 'csv', from: '2026-09-01', to: '2026-09-27' },
      preview: { format: 'csv', from: '2026-09-01', to: '2026-09-27', estimatedCount: 12, filename: 'ledger-2026-09-27.csv' },
    },
  }
  await setupAgent(page, 'agent-export-card', {
    sessions: [{ id: 'session-1', title: '导出流水', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [{ id: 1, turnId: 'turn-export', role: 'assistant', content: '已准备导出范围。', metadataJson: JSON.stringify({ ...response, actions: [action] }), createdAt: now }],
  })
  await page.route('**/api/v1/agent/actions/export-1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'WAITING_CONFIRMATION' } }) }))
  await page.route('**/api/v1/agent/actions/export-1/approve', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'APPROVED' } }) }))
  await page.route('**/api/v1/agent/actions/export-1/commit', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'COMPLETED', summary: '导出文件已准备', structuredContent: { bookId: 'book-1', format: 'csv', from: '2026-09-01', to: '2026-09-27', filename: 'ledger-2026-09-27.csv', byteSize: 28 } } }) }))
  await page.route('**/api/v1/ledger/books/book-1/export**', route => route.fulfill({ status: 200, contentType: 'text/csv', body: '日期,金额\n2026-09-27,29.90\n' }))
  await page.goto('/')

  const card = page.locator('.agent-action-card')
  await expect(card).toContainText('12 笔')
  const downloadPromise = page.waitForEvent('download')
  await card.getByRole('button', { name: '确认并下载' }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe('ledger-2026-09-27.csv')
})

test('uploads a ledger file then approves the import in the R4 approval center', async ({ page }) => {
  const now = new Date().toISOString()
  const action = {
    status: 'NEEDS_INPUT', summary: '请选择账本文件生成导入预览', actionId: 'import-preview-1', expiresAt: now,
    structuredContent: {
      actionType: 'ledger.import.preview', input: { bookId: 'book-1' }, missingFields: ['batchId'],
      fields: [{ name: 'importFile', label: '账本文件', type: 'file', required: true, accept: '.csv,.xls,.xlsx' }],
    },
  }
  const parsedAction = {
    status: 'NEEDS_CONFIRMATION', summary: '导入预览已生成，本轮不会写入流水', actionId: 'import-preview-2', expiresAt: now,
    structuredContent: {
      actionType: 'ledger.import.preview', input: { bookId: 'book-1', batchId: 'batch-1' }, commitAvailable: false, webApprovalRequired: true,
      preview: { batchId: 'batch-1', filename: 'ledger.csv', template: 'STANDARD', validCount: 1, duplicateCount: 1, errorCount: 1, toCreate: { accounts: [], categories: [] }, rows: [
        { sheet: 'CSV', rowNumber: 2, status: 'VALID', kind: 'EXPENSE', amount: 29.9, errors: [] },
        { sheet: 'CSV', rowNumber: 3, status: 'DUPLICATE', kind: 'EXPENSE', amount: 18, errors: [] },
        { sheet: 'CSV', rowNumber: 4, status: 'ERROR', errors: ['二级分类不能为空'] },
      ] },
    },
  }
  await setupAgent(page, 'agent-import-preview-card', {
    sessions: [{ id: 'session-1', title: '导入预览', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [{ id: 1, turnId: 'turn-import', role: 'assistant', content: '请选择文件。', metadataJson: JSON.stringify({ ...response, actions: [action] }), createdAt: now }],
  })
  await page.route('**/api/v1/agent/actions/import-preview-1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'WAITING_INPUT' } }) }))
  await page.route('**/api/v1/ledger/books/book-1/imports/preview**', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: parsedAction.structuredContent.preview }) }))
  await page.route('**/api/v1/agent/actions/import-preview-1/answer', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: parsedAction }) }))
  let approval = {
    id: 'approval-1', actionId: 'import-confirm-1', toolName: 'ledger.import.confirm.prepare', bookId: 'book-1',
    status: 'PENDING', summary: '确认导入“ledger.csv”中的 1 笔有效流水',
    payload: { preview: parsedAction.structuredContent.preview, effects: ['只写入预览中状态为 VALID 的流水', '重复流水默认跳过，错误行不会写入'] },
    result: null, expiresAt: now, createdAt: now, updatedAt: now,
  }
  await page.route('**/api/v1/agent/tools/ledger.import.confirm.prepare/invoke', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ data: {
      status: 'NEEDS_CONFIRMATION', summary: '已创建高风险导入审批，请前往站内审批中心处理',
      structuredContent: { approvalId: approval.id, webApprovalRequired: true, commitAvailable: false },
      actionId: approval.actionId, confirmationUrl: `/approvals/${approval.id}`, expiresAt: now,
    } }),
  }))
  await page.route('**/api/v1/agent/approvals', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: [approval] }) }))
  await page.route('**/api/v1/agent/approvals/approval-1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: approval }) }))
  await page.route('**/api/v1/agent/approvals/approval-1/approve', route => {
    approval = { ...approval, status: 'COMPLETED', result: { status: 'COMPLETED', summary: '导入完成，共写入 1 笔流水', structuredContent: { createdCount: 1 } } }
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: approval }) })
  })
  await page.goto('/')

  const card = page.locator('.agent-action-card')
  await page.getByLabel('账本文件').setInputFiles({ name: 'ledger.csv', mimeType: 'text/csv', buffer: Buffer.from('交易类型,日期,金额\n支出,2026-09-27,29.9') })
  await card.getByRole('button', { name: '生成预览' }).click()
  await expect(card.getByLabel('导入预览结果')).toContainText('有效')
  await expect(card.getByLabel('导入预览结果')).toContainText('重复')
  await expect(card.getByLabel('导入预览结果')).toContainText('二级分类不能为空')
  await card.getByRole('button', { name: '提交站内审批' }).click()
  await expect(page).toHaveURL(/\/approvals\/approval-1$/)
  await expect(page.getByRole('heading', { name: /确认导入/ })).toBeVisible()
  await expect(page.getByText('预计写入').locator('..').getByText('1')).toBeVisible()
  await page.getByRole('button', { name: '批准并导入 1 笔' }).click()
  await expect(page.getByText('导入完成，共写入 1 笔流水')).toBeVisible()
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

test('edits and atomically confirms one parent batch action', async ({ page }) => {
  const now = new Date().toISOString()
  const fields = [
    { name: 'kind', label: '流水类型', type: 'select', required: true, options: [{ value: 'EXPENSE', label: '支出' }, { value: 'TRANSFER', label: '转账' }] },
    { name: 'amount', label: '金额', type: 'money', required: true },
    { name: 'occurredOn', label: '发生日期', type: 'date', required: true },
    { name: 'accountId', label: '账户', type: 'entity-picker', required: true, options: [{ value: 'cash', label: '现金' }, { value: 'boc', label: '中行卡' }] },
    { name: 'categoryId', label: '二级分类', type: 'entity-picker', required: true, options: [{ value: 'meal', label: '餐饮 / 午餐', kind: 'EXPENSE' }] },
    { name: 'note', label: '备注', type: 'textarea', required: false },
  ]
  const inputs = [
    { bookId: 'book-1', kind: 'EXPENSE', amount: 35, accountId: 'cash', categoryId: 'meal', occurredOn: '2026-09-27', note: '午餐' },
    { bookId: 'book-1', kind: 'EXPENSE', amount: 18, accountId: 'boc', categoryId: 'meal', occurredOn: '2026-09-27', note: '加餐' },
  ]
  const itemContent = input => ({ input, fields, preview: { ...input, accountName: input.accountId === 'cash' ? '现金' : '中行卡', categoryPath: '餐饮 / 午餐' } })
  const makeAction = (id, status, batchInputs) => ({
    status, summary: status === 'NEEDS_INPUT' ? '请补充批量流水信息' : '请确认批量保存 2 笔流水', actionId: id, expiresAt: now,
    structuredContent: {
      actionType: 'ledger.transactions.batch.create', input: { bookId: 'book-1', items: batchInputs },
      items: batchInputs.map(itemContent), totals: { count: 2, income: 0, expense: batchInputs.reduce((sum, item) => sum + Number(item.amount), 0), transfer: 0 },
    },
  })
  const action = makeAction('batch-parent-1', 'NEEDS_INPUT', inputs)
  const calls = []
  await setupAgent(page, 'agent-real-batch-card', {
    sessions: [{ id: 'session-1', title: '真正批量记账', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [{ id: 1, turnId: 'turn-batch-parent', role: 'assistant', content: '已整理成一个批量操作。', metadataJson: JSON.stringify({ ...response, actions: [action] }), createdAt: now }],
  })
  for (const id of ['batch-parent-1', 'batch-parent-2', 'batch-parent-3']) {
    await page.route(`**/api/v1/agent/actions/${id}`, route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: id.endsWith('1') ? 'WAITING_INPUT' : 'WAITING_CONFIRMATION' } }) }))
  }
  await page.route('**/api/v1/agent/actions/batch-parent-1/answer', async route => {
    const body = route.request().postDataJSON()
    calls.push({ type: 'answer', body })
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: makeAction('batch-parent-2', 'NEEDS_CONFIRMATION', body.items) }) })
  })
  await page.route('**/api/v1/agent/actions/batch-parent-2/answer', async route => {
    const body = route.request().postDataJSON()
    calls.push({ type: 'answer', body })
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: makeAction('batch-parent-3', 'NEEDS_CONFIRMATION', body.items) }) })
  })
  await page.route('**/api/v1/agent/actions/batch-parent-3/approve', route => { calls.push({ type: 'approve' }); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'APPROVED' } }) }) })
  await page.route('**/api/v1/agent/actions/batch-parent-3/commit', route => { calls.push({ type: 'commit' }); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'COMPLETED', summary: '已批量保存 2 笔流水', structuredContent: { count: 2 } } }) }) })
  await page.goto('/')

  const items = page.locator('.agent-batch-item')
  await expect(items).toHaveCount(2)
  await items.nth(1).getByLabel('金额').fill('20')
  await page.getByRole('button', { name: '生成批量预览' }).click()
  await expect.poll(() => calls[0]?.body.items[1].amount).toBe(20)
  await expect(page.getByText('支出 ¥55.00')).toBeVisible()
  await page.getByRole('button', { name: '返回编辑' }).click()
  await expect(page.locator('.agent-batch-item')).toHaveCount(2)
  await page.getByRole('button', { name: '生成批量预览' }).click()
  await page.getByRole('button', { name: '确认并保存全部' }).click()
  await expect.poll(() => calls.map(item => item.type)).toEqual(['answer', 'answer', 'approve', 'commit'])
  await expect(page.locator('.agent-action-result b')).toHaveText('已批量保存 2 笔流水')
})

test('shows every fixed item before a strong batch delete confirmation', async ({ page }) => {
  const now = new Date().toISOString()
  const action = {
    status: 'NEEDS_CONFIRMATION', summary: '请确认批量删除 2 笔流水', actionId: 'batch-delete-parent', expiresAt: now,
    structuredContent: {
      actionType: 'ledger.transactions.batch.delete', input: { bookId: 'book-1', items: [{ transactionId: 't1', revision: 2 }, { transactionId: 't2', revision: 4 }] },
      items: [
        { transactionId: 't1', revision: 2, kind: 'TRANSFER', amount: 500, occurredOn: '2026-09-27', accountName: '中行卡', targetAccountName: '支付宝', note: '资金归集' },
        { transactionId: 't2', revision: 4, kind: 'REPAY_DEBT', amount: 200, occurredOn: '2026-09-26', accountName: '现金', merchantName: '小李', note: '还款' },
      ],
      totals: { count: 2, income: 0, expense: 200, transfer: 500 },
      effects: ['仅删除本次预览中固化 ID 与 revision 的流水', '任意一笔版本变化时整批不执行', '转账的关联两端会同步删除', '删除结果进入回收站、审计和同步链路'],
    },
  }
  const calls = []
  await setupAgent(page, 'agent-batch-delete-card', {
    sessions: [{ id: 'session-1', title: '批量删除', createdAt: now, updatedAt: now, archivedAt: null }],
    messages: [{ id: 1, turnId: 'turn-batch-delete', role: 'assistant', content: '已固定两笔目标流水。', metadataJson: JSON.stringify({ ...response, actions: [action] }), createdAt: now }],
  })
  await page.route('**/api/v1/agent/actions/batch-delete-parent', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'WAITING_CONFIRMATION' } }) }))
  await page.route('**/api/v1/agent/actions/batch-delete-parent/reject', route => { calls.push('reject'); return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { status: 'DENIED' } }) }) })
  await page.goto('/')

  const card = page.locator('.agent-action-card.destructive')
  await expect(card).toContainText('转出账户')
  await expect(card).toContainText('支付宝')
  await card.locator('.agent-batch-preview').nth(1).click()
  await expect(card).toContainText('还款')
  await expect(card).toContainText('rev 4')
  await expect(card).toContainText('任意一笔版本变化时整批不执行')
  await card.getByRole('button', { name: '全部取消' }).click()
  await expect.poll(() => calls).toEqual(['reject'])
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
