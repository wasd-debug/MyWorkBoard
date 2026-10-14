#!/usr/bin/env node
/**
 * Capture rendered reference evidence from a user-authenticated Chrome tab.
 * Start Chrome with CDP on 9222 and keep the output under .local/ (ignored).
 */
import { chromium } from '@playwright/test'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

const output = path.resolve(process.cwd(), '../.local/dida365-evidence')
const pages = [
  ['today', 'https://dida365.com/webapp/#q/today/tasks'],
  ['calendar', 'https://dida365.com/webapp/#c/all/calendar/m'],
  ['focus', 'https://dida365.com/webapp/#focus'],
  ['habits', 'https://dida365.com/webapp/#q/all/habit'],
  ['countdown', 'https://dida365.com/webapp/#countdown'],
  ['ai', 'https://dida365.com/webapp/#ai'],
  ['search', 'https://dida365.com/webapp/#s']
]

const compact = value => String(value || '').trim().replace(/\s+/g, ' ')
const safe = value => value.replace(/[^a-z0-9_-]+/gi, '-').replace(/^-|-$/g, '').toLowerCase()

await Promise.all(['screenshots', 'dom', 'pages', 'interactions', 'network'].map(name => mkdir(path.join(output, name), { recursive: true })))
const browser = await chromium.connectOverCDP('http://127.0.0.1:9222')
const context = browser.contexts()[0]
const page = context.pages()[0] || await context.newPage()

for (const [slug, url] of pages) {
  const requests = []
  page.on('request', request => {
    if (request.resourceType() === 'xhr' || request.resourceType() === 'fetch') requests.push({ url: request.url(), method: request.method(), resourceType: request.resourceType(), postData: request.postData() })
  })
  const responses = []
  page.on('response', response => {
    if (response.request().resourceType() === 'xhr' || response.request().resourceType() === 'fetch') responses.push({ url: response.url(), status: response.status(), method: response.request().method() })
  })
  await page.goto(url, { waitUntil: 'commit', timeout: 30000 }).catch(() => {})
  await page.waitForTimeout(3000)
  const interactive = await page.locator('button, [role="button"], a, input, select, textarea').evaluateAll(elements => elements.map((element, index) => {
    const rect = element.getBoundingClientRect()
    return { index, tag: element.tagName, text: (element.innerText || element.value || element.getAttribute('aria-label') || element.title || '').trim(), aria: element.getAttribute('aria-label'), title: element.title, href: element.href || null, type: element.type || null, disabled: Boolean(element.disabled), visible: rect.width > 0 && rect.height > 0, rect: { x: rect.x, y: rect.y, width: rect.width, height: rect.height } }
  }))
  const record = { url: page.url(), title: await page.title(), headings: await page.locator('h1,h2,h3').allTextContents(), text: compact(await page.locator('body').innerText()), interactive }
  await page.screenshot({ path: path.join(output, 'screenshots', `${slug}-full.png`), fullPage: true })
  await writeFile(path.join(output, 'dom', `${slug}.html`), await page.content())
  await writeFile(path.join(output, 'pages', `${slug}.json`), JSON.stringify(record, null, 2))
  await writeFile(path.join(output, 'interactions', `${slug}.json`), JSON.stringify(interactive, null, 2))
  await writeFile(path.join(output, 'network', `${slug}-playwright.json`), JSON.stringify({ requests, responses }, null, 2))
  page.removeAllListeners('request')
  page.removeAllListeners('response')
  console.log(`${slug}: ${interactive.length} controls, ${requests.length} requests`)
}
await browser.close()
