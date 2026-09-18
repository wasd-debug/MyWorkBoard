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
