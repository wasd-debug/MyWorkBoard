import { test, expect } from '@playwright/test'
import { bootstrapTemplateLedger, registerUser, selectLedgerBeforeLoad } from './helpers.js'

async function settle(page) {
  await expect(page.locator('h1').filter({ hasText: '设置' })).toBeVisible()
  await expect(page.getByText('正在切换页面…')).toBeHidden()
  await expect(page.locator('.ledger-loading-overlay:visible')).toHaveCount(0, { timeout: 15_000 })
}

test('设置页运行质量与 MCP 区块在各视口保持可读布局', async ({ page }, testInfo) => {
  if (testInfo.project.name === 'mobile-375-chromium') await page.setViewportSize({ width: 375, height: 812 })
  const auth = await registerUser(page.request, 'settings-layout-e2e')
  const { book } = await bootstrapTemplateLedger(page.request, auth)
  await selectLedgerBeforeLoad(page, auth.user.id, book.id)
  await page.goto('/settings')
  await settle(page)

  for (const theme of ['light', 'dark']) {
    await page.evaluate(nextTheme => localStorage.setItem('st_theme', nextTheme), theme)
    await page.reload()
    await settle(page)
    await expect(page.locator('.operations-kpis > div')).toHaveCount(6, { timeout: 15_000 })
    await expect(page.locator('.mcp-diagnostics > div')).toHaveCount(6, { timeout: 15_000 })

    const metrics = await page.evaluate(() => {
      const visible = selector => [...document.querySelectorAll(selector)].filter(element => {
        const style = getComputedStyle(element)
        return style.display !== 'none' && style.visibility !== 'hidden'
      })
      const rect = selector => document.querySelector(selector)?.getBoundingClientRect().toJSON()
      return {
        documentWidth: document.documentElement.scrollWidth,
        viewportWidth: document.documentElement.clientWidth,
        operations: rect('.operations-config'),
        mcp: rect('.mcp-config'),
        kpis: visible('.operations-kpis > div').length,
        diagnostics: visible('.mcp-diagnostics > div').length,
        longText: visible('.mcp-endpoint code, .mcp-token-list code').every(element => {
          const style = getComputedStyle(element)
          return style.whiteSpace !== 'nowrap' || style.overflowWrap === 'anywhere' || style.wordBreak === 'break-word'
        })
      }
    })
    expect(metrics.documentWidth).toBeLessThanOrEqual(metrics.viewportWidth + 1)
    expect(metrics.operations?.width).toBeGreaterThan(0)
    expect(metrics.mcp?.width).toBeGreaterThan(0)
    expect(metrics.kpis).toBe(6)
    expect(metrics.diagnostics).toBe(6)
    expect(metrics.longText).toBeTruthy()
  }

  await expect(page.getByRole('heading', { name: 'Agent 运行质量' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '外部 Agent / MCP' })).toBeVisible()
  await expect(page.getByLabel('时间范围')).toBeVisible()
  await expect(page.getByLabel('时间粒度')).toBeVisible()
  await expect(page.getByRole('button', { name: '刷新诊断' })).toBeVisible()

  if (testInfo.project.name === 'mobile-375-chromium') {
    await expect(page.getByText('Streamable HTTP 地址')).toBeVisible()
    const columns = await page.locator('.mcp-create-grid').evaluate(element => getComputedStyle(element).gridTemplateColumns.split(' ').length)
    expect(columns).toBe(1)
  }
})
