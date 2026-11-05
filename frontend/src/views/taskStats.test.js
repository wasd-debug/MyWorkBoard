import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const view = await readFile(new URL('./TaskStatsView.vue', import.meta.url), 'utf8')
const router = await readFile(new URL('../router/index.js', import.meta.url), 'utf8')
const client = await readFile(new URL('../../packages/api-client/src/index.js', import.meta.url), 'utf8')

test('统计页挂在任务域路由上，侧栏的专注统计入口重定向到统一页签', () => {
  assert.match(router, /path: '\/tasks\/stats', name: 'tasks-stats', component: \(\) => import\('\.\.\/views\/TaskStatsView\.vue'\)/)
  assert.match(router, /path: '\/tasks\/focus\/stats', name: 'tasks-focus-stats', redirect: \{ path: '\/tasks\/stats', query: \{ tab: 'FOCUS' \} \}/)
})

test('统计页三个接口都由服务端聚合，前端不自己算概览数字', () => {
  assert.match(client, /export const apiGetStatsOverview = async \(\) => data\(await api\.get\('\/api\/v1\/tasks\/stats\/overview'\)\)/)
  assert.match(client, /export const apiGetStatsTaskPage = async params => data\(await api\.get\('\/api\/v1\/tasks\/stats\/tasks', \{ params \}\)\)/)
  assert.match(client, /export const apiGetStatsFocusPage = async params => data\(await api\.get\('\/api\/v1\/tasks\/stats\/focus', \{ params \}\)\)/)
  assert.match(view, /import \{ apiGetStatsOverview, apiGetStatsTaskPage, apiGetStatsFocusPage \}/)
})

test('三个页签都存在，且切到总览也会触发加载', () => {
  for (const label of ['总览', '任务', '专注']) {
    assert.match(view, new RegExp(`label: '${label}'`))
  }
  const switchTab = view.match(/function switchTab\(key\) \{[\s\S]*?\n\}/)?.[0] || ''
  assert.match(switchTab, /key === 'OVERVIEW' && !overview\.value\) void loadOverview\(\)/)
  assert.match(switchTab, /key === 'TASK' && !task\.value\) void loadTask\(\)/)
  assert.match(switchTab, /key === 'FOCUS' && !focusPage\.value\) void loadFocus\(\)/)
})

test('页面上的数字全部来自接口返回值，没有写死的示例数据', () => {
  assert.match(view, /\{\{ num\(overview\.totalTasks\) \}\}/)
  assert.match(view, /\{\{ num\(task\.completed\) \}\}/)
  assert.match(view, /\{\{ num\(focusPage\.totalMinutes\)|duration\(focusPage\.totalMinutes\) \}\}/)
  assert.doesNotMatch(view, /\b12,?345\b|\bmockData\b|\bfakeData\b|示例数据/)
})

test('按日/按周/按月与各卡片步进器都会带上日期重新请求', () => {
  assert.match(view, /const taskModes = \[[\s\S]*key: 'DAY'[\s\S]*key: 'WEEK'[\s\S]*key: 'MONTH'/)
  assert.match(view, /function shiftTask\(offset\) \{[\s\S]*void loadTask\(\)/)
  assert.match(view, /function shiftDetail\(offset\) \{[\s\S]*void loadFocus\(\)/)
  assert.match(view, /trendMode: trendMode\.value,[\s\S]*timelineDate: timelineDate\.value,[\s\S]*year: yearValue\.value/)
})

test('没有数据时用空状态占位，而不是画一条贴底的假折线或假圆环', () => {
  assert.match(view, /function nonZero\(list\)/)
  assert.match(view, /v-if="nonZero\(overview\.pomodoroTrend\)"/)
  assert.match(view, /v-if="nonZero\(focusPage\.trend\)"/)
  assert.match(view, /const EMPTY_DONUT = \{/)
  assert.match(view, /暂无数据/)
})

test('时长按小时分钟展示，图表纵轴不会出现 12323m 这种刻度', () => {
  assert.match(view, /function duration\(minutes\) \{[\s\S]*小时[\s\S]*分钟/)
  assert.match(view, /function axisMinutes\(value\) \{[\s\S]*\$\{amount\}m/)
  assert.match(view, /formatter: axisMinutes/)
})
