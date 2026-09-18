import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list']],
  outputDir: '/private/tmp/salary-sync-playwright-artifacts',
  globalSetup: './e2e/global-setup.js',
  globalTeardown: './e2e/global-teardown.js',
  use: {
    baseURL: 'http://127.0.0.1:14173',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    reducedMotion: 'reduce',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure'
  },
  webServer: {
    command: 'VITE_API_PROXY_TARGET=http://127.0.0.1:18080 npm run dev -- --host 127.0.0.1 --port 14173',
    url: 'http://127.0.0.1:14173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000
  },
  projects: [
    { name: 'desktop-chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 960 } } },
    { name: 'desktop-webkit', use: { ...devices['Desktop Safari'], viewport: { width: 1440, height: 960 } } },
    { name: 'mobile-375-chromium', use: { ...devices['iPhone 13'], browserName: 'chromium' } },
    { name: 'visual-320-chromium', testMatch: /visual-a11y\.spec\.js/, use: { browserName: 'chromium', viewport: { width: 320, height: 720 }, isMobile: true, hasTouch: true } },
    { name: 'visual-768-chromium', testMatch: /visual-a11y\.spec\.js/, use: { browserName: 'chromium', viewport: { width: 768, height: 1024 } } },
    { name: 'visual-1024-chromium', testMatch: /visual-a11y\.spec\.js/, use: { browserName: 'chromium', viewport: { width: 1024, height: 768 } } }
  ]
})
