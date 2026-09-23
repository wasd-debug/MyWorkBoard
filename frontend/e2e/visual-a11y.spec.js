import AxeBuilder from '@axe-core/playwright'
import { test, expect } from '@playwright/test'
import { bootstrapTemplateLedger, registerUser, selectLedgerBeforeLoad } from './helpers.js'

const ROUTES = [
  ['/punch', '打卡'],
  ['/records', '记录'],
  ['/stats', '统计'],
  ['/ledger', '账本'],
  ['/ledger/transactions', '流水'],
  ['/ledger/reports', '基础统计'],
  ['/settings', '设置']
]

function watchConsole(page) {
  const messages = []
  page.on('console', message => {
    if (message.type() === 'warning' || message.type() === 'error') messages.push(`${message.type()}: ${message.text()}`)
  })
  page.on('pageerror', error => messages.push(`pageerror: ${error.message}`))
  return messages
}

async function expectAccessible(page, label) {
  const result = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  const serious = result.violations.filter(item => item.impact === 'serious' || item.impact === 'critical')
  expect(serious, `${label}: ${serious.map(item => `${item.id} (${item.nodes.length})`).join(', ')}`).toEqual([])
}

async function expectPageSettled(page) {
  await expect(page.getByText('正在切换页面…')).toBeHidden()
  await expect(page.locator('.ledger-loading-overlay:visible')).toHaveCount(0, { timeout: 15_000 })
}

async function expectLayout(page, mobile) {
  const metrics = await page.evaluate(() => ({
    documentWidth: document.documentElement.scrollWidth,
    viewportWidth: document.documentElement.clientWidth,
    unnamed: [...document.querySelectorAll('button, a[href], input, select, textarea')]
      .filter(element => {
        const style = getComputedStyle(element)
        if (style.display === 'none' || style.visibility === 'hidden') return false
        const nativeLabel = 'labels' in element && element.labels?.length
        return !nativeLabel && !String(element.getAttribute('aria-label') || element.getAttribute('title') || element.textContent || element.value || '').trim()
      }).length,
    headings: [...document.querySelectorAll('h1, h2, h3, h4, h5, h6')]
      .filter(element => getComputedStyle(element).display !== 'none')
      .map(element => Number(element.tagName.slice(1))),
    undersizedTargets: [...document.querySelectorAll('button, a[href], input, select, textarea, [role="button"], [role="tab"], [role="switch"]')]
      .filter(element => {
        const rect = element.getBoundingClientRect()
        const style = getComputedStyle(element)
        return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0
          && (rect.width < 44 || rect.height < 44)
      })
      .map(element => ({ tag: element.tagName, text: String(element.getAttribute('aria-label') || element.textContent || '').trim(), rect: element.getBoundingClientRect().toJSON() }))
  }))
  expect(metrics.documentWidth).toBeLessThanOrEqual(metrics.viewportWidth + 1)
  expect(metrics.unnamed).toBe(0)
  expect(metrics.headings[0]).toBe(1)
  for (let index = 1; index < metrics.headings.length; index += 1) {
    expect(metrics.headings[index] - metrics.headings[index - 1]).toBeLessThanOrEqual(1)
  }
  if (mobile) expect(metrics.undersizedTargets).toEqual([])
}

test('login and error states meet the accessibility baseline', async ({ page }, testInfo) => {
  const consoleMessages = watchConsole(page)
  await page.goto('/punch')
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
  await expectPageSettled(page)
  await expectAccessible(page, 'login')
  await expectLayout(page, testInfo.project.use.isMobile === true)
  await page.getByLabel('用户名').fill('missing-user')
  await page.getByLabel('密码').fill('bad-password')
  await page.getByRole('button', { name: '进入工作台' }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await expectAccessible(page, 'login error')
  expect(consoleMessages.filter(message => !message.includes('status of 401 (Unauthorized)'))).toEqual([])
})

test('authenticated worktime and ledger pages have no serious accessibility or layout failures', async ({ page }, testInfo) => {
  const consoleMessages = watchConsole(page)
  const auth = await registerUser(page.request, 'visual-a11y-e2e')
  const { book } = await bootstrapTemplateLedger(page.request, auth)
  await selectLedgerBeforeLoad(page, auth.user.id, book.id)
  const mobile = testInfo.project.use.isMobile === true

  for (const [route, heading] of ROUTES) {
    await page.goto(route)
    await expect(page.locator('h1').filter({ hasText: heading })).toBeVisible()
    await expectPageSettled(page)
    await expectAccessible(page, route)
    await expectLayout(page, mobile)
  }

  for (const theme of ['light', 'dark']) {
    for (const accent of ['green', 'blue', 'plum', 'rust']) {
      await page.evaluate(({ theme: nextTheme, accent: nextAccent }) => {
        localStorage.setItem('st_theme', nextTheme)
        localStorage.setItem('st_accent', nextAccent)
      }, { theme, accent })
      await page.reload()
      await expect(page.locator('h1').filter({ hasText: '设置' })).toBeVisible()
      await expectPageSettled(page)
      await expectAccessible(page, `settings ${theme}/${accent}`)
    }
  }
  expect(consoleMessages).toEqual([])
})

test('dialogs and report tabs provide complete keyboard behavior', async ({ page }) => {
  const auth = await registerUser(page.request, 'keyboard-e2e')
  const { book } = await bootstrapTemplateLedger(page.request, auth)
  await selectLedgerBeforeLoad(page, auth.user.id, book.id)
  await page.goto('/ledger/transactions')
  const trigger = page.getByRole('button', { name: '记一笔' })
  await trigger.focus()
  await trigger.press('Enter')
  const dialog = page.getByRole('dialog', { name: '新增流水' })
  await expect(dialog).toBeVisible()
  await expect(dialog.locator(':focus')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
  await expect(trigger).toBeFocused()

  await page.goto('/ledger/reports')
  const tabs = page.getByRole('tab')
  await tabs.first().focus()
  await page.keyboard.press('ArrowRight')
  await expect(tabs.nth(1)).toBeFocused()
  await expect(tabs.nth(1)).toHaveAttribute('aria-selected', 'true')
})

test('stable visual baselines cover desktop settings and mobile reports', async ({ page }, testInfo) => {
  test.skip(!['desktop-chromium', 'mobile-375-chromium'].includes(testInfo.project.name))
  const auth = await registerUser(page.request, 'visual-baseline-e2e')
  const { book } = await bootstrapTemplateLedger(page.request, auth)
  await selectLedgerBeforeLoad(page, auth.user.id, book.id)
  await page.addStyleTag({ content: '*,*::before,*::after{animation-duration:0s!important;transition-duration:0s!important}' })

  if (testInfo.project.name === 'desktop-chromium') {
    await page.goto('/settings')
    await expect(page.locator('h1').filter({ hasText: '设置' })).toBeVisible()
    await expectPageSettled(page)
    await expect(page).toHaveScreenshot('settings-light.png', { fullPage: true, animations: 'disabled' })
    return
  }

  await page.goto('/ledger/reports')
  await expect(page.locator('h1').filter({ hasText: '基础统计' })).toBeVisible()
  await expectPageSettled(page)
  await expect(page).toHaveScreenshot('reports-mobile.png', {
    fullPage: true,
    animations: 'disabled',
    maxDiffPixelRatio: 0.0005
  })
})

test('tablet bottom navigation follows the current module and adapts its capacity', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'visual-1024-chromium')
  const auth = await registerUser(page.request, 'tablet-navigation-e2e')
  const { book } = await bootstrapTemplateLedger(page.request, auth)
  await selectLedgerBeforeLoad(page, auth.user.id, book.id)
  await page.goto('/ledger')
  await expect(page.locator('h1').filter({ hasText: '账本' })).toBeVisible()
  await expectPageSettled(page)

  const ledgerNav = page.getByRole('navigation', { name: '账本移动端二级导航' })
  await expect(ledgerNav).toBeVisible()
  await expect(ledgerNav.locator('.mobile-module-name')).toHaveCount(0)
  await expect(ledgerNav.getByRole('button', { name: '总览' })).toHaveAttribute('aria-current', 'page')
  await expect(page.getByRole('navigation', { name: '账本二级导航' })).toBeHidden()

  const directLedgerCount = await ledgerNav.locator('.module-bottom-nav > .bottom-nav-item:not(.bottom-nav-more)').count()
  expect(directLedgerCount).toBeGreaterThan(3)
  expect(directLedgerCount).toBeLessThanOrEqual(9)
  if (directLedgerCount < 9) {
    await ledgerNav.getByRole('button', { name: '更多' }).click()
    const orderPanel = page.getByRole('region', { name: '二级菜单排序' })
    for (const label of ['总览', '流水', '账户', '报表', '定时任务', '管理', '成员与权限', '回收站', '操作日志']) {
      await expect(orderPanel.getByRole('button', { name: new RegExp(`^${label}`) }).first()).toBeVisible()
    }
    await page.getByRole('button', { name: '关闭更多菜单' }).click()
  } else {
    await expect(ledgerNav.getByRole('button', { name: '更多' })).toHaveCount(0)
  }

  await page.goto('/records')
  await expect(page.locator('h1').filter({ hasText: '记录' })).toBeVisible()
  const worktimeNav = page.getByRole('navigation', { name: '工时移动端二级导航' })
  await expect(worktimeNav).toBeVisible()
  await expect(worktimeNav.getByRole('button', { name: '打卡' })).toBeVisible()
  await expect(worktimeNav.getByRole('button', { name: '记录' })).toHaveAttribute('aria-current', 'page')
  await expect(worktimeNav.getByRole('button', { name: '统计' })).toBeVisible()
  await expect(worktimeNav.getByRole('button', { name: '流水' })).toHaveCount(0)
  await expect(worktimeNav.getByRole('button', { name: '更多' })).toHaveCount(0)
})

test('mobile module menu order persists and active overflow items stay reachable', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile-375-chromium')
  const auth = await registerUser(page.request, 'mobile-navigation-order-e2e')
  const { book } = await bootstrapTemplateLedger(page.request, auth)
  await selectLedgerBeforeLoad(page, auth.user.id, book.id)
  await page.goto('/ledger')
  const nav = page.getByRole('navigation', { name: '账本移动端二级导航' })
  await expect(nav).toBeVisible()
  await expect(nav.locator('.mobile-module-name')).toHaveCount(0)
  const directCount = await nav.locator('.module-bottom-nav > .bottom-nav-item:not(.bottom-nav-more)').count()
  expect(directCount).toBeGreaterThanOrEqual(1)
  expect(directCount).toBeLessThan(9)
  await expect(nav.locator('.module-bottom-nav > .bottom-nav-item:not(.bottom-nav-more) svg')).toHaveCount(directCount)

  await nav.getByRole('button', { name: '更多' }).click()
  const panel = page.getByRole('region', { name: '二级菜单排序' })
  await expect(panel.locator('.mobile-order-link svg')).toHaveCount(9)
  const closeAlignment = await panel.getByRole('button', { name: '关闭' }).evaluate(button => {
    const icon = button.querySelector('svg')
    const buttonRect = button.getBoundingClientRect()
    const iconRect = icon.getBoundingClientRect()
    return {
      buttonWidth: buttonRect.width,
      buttonHeight: buttonRect.height,
      deltaX: Math.abs((buttonRect.left + buttonRect.width / 2) - (iconRect.left + iconRect.width / 2)),
      deltaY: Math.abs((buttonRect.top + buttonRect.height / 2) - (iconRect.top + iconRect.height / 2))
    }
  })
  expect(closeAlignment.buttonWidth).toBe(44)
  expect(closeAlignment.buttonHeight).toBe(44)
  expect(closeAlignment.deltaX).toBeLessThanOrEqual(1)
  expect(closeAlignment.deltaY).toBeLessThanOrEqual(1)
  const transactions = panel.locator('li').filter({ hasText: '流水' })
  await transactions.getByRole('button', { name: '流水上移' }).click()
  await page.reload()
  await nav.getByRole('button', { name: '更多' }).click()
  const labels = await panel.locator('.mobile-order-link span').allTextContents()
  expect(labels.indexOf('流水')).toBeLessThan(labels.indexOf('总览'))

  await page.goto('/ledger/manage?view=audit')
  await expect(page.getByRole('navigation', { name: '账本移动端二级导航' }).getByRole('button', { name: '操作日志' })).toHaveAttribute('aria-current', 'page')
})

test('mobile header keeps theme sync settings and account shortcuts', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile-375-chromium')
  const auth = await registerUser(page.request, 'mobile-header-actions-e2e')
  await selectLedgerBeforeLoad(page, auth.user.id, 'mobile-header-placeholder')
  await page.goto('/')
  const header = page.locator('.workspace-topbar')
  for (const name of ['切换配色风格', '帮助中心', '设置', '退出登录']) {
    await expect(header.getByRole(name === '设置' ? 'link' : 'button', { name })).toBeVisible()
  }
  const metrics = await header.evaluate(element => ({ scrollWidth: element.scrollWidth, clientWidth: element.clientWidth }))
  expect(metrics.scrollWidth).toBeLessThanOrEqual(metrics.clientWidth)
})
