import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const view = await readFile(new URL('./TasksEnhancedView.vue', import.meta.url), 'utf8')
const settingsView = await readFile(new URL('./TaskSettingsView.vue', import.meta.url), 'utf8')
const dataPanel = await readFile(new URL('../components/tasks/TaskDataPanel.vue', import.meta.url), 'utf8')
const inboxView = await readFile(new URL('./TasksInboxView.vue', import.meta.url), 'utf8')
const router = await readFile(new URL('../router/index.js', import.meta.url), 'utf8')
const client = await readFile(new URL('../../packages/api-client/src/index.js', import.meta.url), 'utf8')

test('task enhanced workspace exposes all I7 routes', () => {
  for (const path of ['/tasks/board', '/tasks/quadrant', '/tasks/search', '/tasks/stats', '/tasks/filter/:publicId']) {
    assert.match(router, new RegExp(path.replace(/[/:]/g, match => `\\${match}`)))
  }
})

test('task import remains preview then explicit commit', () => {
  assert.match(client, /apiPreviewTaskImport/)
  assert.match(client, /\/api\/v1\/tasks\/import\/preview/)
  assert.match(client, /apiCommitTaskImport/)
  assert.match(client, /\/api\/v1\/tasks\/import\/commit/)
  assert.match(settingsView, /TaskDataPanel/)
  assert.match(dataPanel, /预览阶段不会写入任务/)
  assert.match(dataPanel, /确认导入/)
  assert.match(router, /path: '\/tasks\/data', redirect: \{ path: '\/tasks\/settings', hash: '#data' \}/)
})

test('mobile board has explicit move control and statistics explain focus basis', () => {
  assert.match(view, /移动到…/)
  assert.match(view, /专注时长是主动专注口径/)
  assert.match(view, /不参与工资、加班或实际时薪计算/)
})

test('search highlights matches and scopes recent history by account', () => {
  assert.match(view, /h\('mark'/)
  assert.match(view, /task_recent_searches:\$\{appStore\.accountScope/)
  assert.match(view, /最近搜索/)
})

test('task legacy batch exposes tomorrow groups and activity timeline', () => {
  assert.match(router, /path: '\/tasks\/tomorrow'/)
  assert.match(inboxView, /新建清单分组/)
  assert.match(inboxView, /task-groups:\$\{store\.accountScope\}/)
  assert.match(inboxView, /活动记录/)
  assert.match(client, /apiListTaskActivity/)
})

test('task list supports atomic batch controls and manual ordering', () => {
  assert.match(inboxView, /多选模式/)
  assert.match(inboxView, /task-batch-bar/)
  assert.match(inboxView, /runBatch\('COMPLETE'\)/)
  assert.match(inboxView, /runBatch\('MOVE'/)
  assert.match(inboxView, /runBatch\('PRIORITY'/)
  assert.match(inboxView, /runBatch\('RESCHEDULE'/)
  assert.match(inboxView, /runBatch\('TAGS'/)
  assert.match(inboxView, /runBatch\('REMINDER'/)
  assert.match(inboxView, /runBatch\('DELETE'\)/)
  assert.match(inboxView, /:draggable="canDrag\(task\)"/)
  assert.match(inboxView, /@drop="dropTask\(task, index\)"/)
  assert.match(inboxView, />置顶</)
  assert.match(client, /apiBatchTasks/)
  assert.match(client, /apiSortTask/)
})
