#!/usr/bin/env node
import { chromium } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const username = process.env.DIDA_QA_USERNAME
const password = process.env.DIDA_QA_PASSWORD
if (!username || !password) throw new Error('Set DIDA_QA_USERNAME and DIDA_QA_PASSWORD for a disposable local QA account')

const output = path.resolve(process.cwd(), '../.local/dida365-evidence/local')
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ headless: true })
const context = await browser.newContext({ viewport: { width: 1453, height: 885 }, deviceScaleFactor: 1 })
const page = await context.newPage()
await page.goto('http://127.0.0.1:5173/tasks/today')
if (await page.getByRole('heading', { name: '欢迎回来' }).count()) {
  await page.locator('.auth-form input').nth(0).fill(username)
  await page.locator('.auth-form input').nth(1).fill(password)
  await page.getByRole('button', { name: '进入工作台' }).click()
  await page.locator('.task-module-rail, .workspace-topbar').first().waitFor({ state: 'attached', timeout: 15000 })
}

const routes = [
  ['today', '/tasks/today'], ['calendar', '/tasks/calendar'], ['focus', '/tasks/focus'],
  ['habits', '/tasks/habits'], ['countdown', '/tasks/countdowns'], ['search', '/tasks/search']
]
for (const [slug, route] of routes) {
  await page.goto(`http://127.0.0.1:5173${route}`)
  await page.waitForTimeout(1000)
  await page.screenshot({ path: path.join(output, `${slug}-full.png`), fullPage: true })
  console.log(`${slug}: ${await page.title()}`)
}
await browser.close()
