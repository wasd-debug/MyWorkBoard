import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const view = await readFile(new URL('./TasksEnhancedView.vue', import.meta.url), 'utf8')
const router = await readFile(new URL('../router/index.js', import.meta.url), 'utf8')
const client = await readFile(new URL('../../packages/api-client/src/index.js', import.meta.url), 'utf8')

test('task enhanced workspace exposes all I7 routes', () => {
  for (const path of ['/tasks/board', '/tasks/quadrant', '/tasks/search', '/tasks/stats', '/tasks/data', '/tasks/filter/:publicId']) {
    assert.match(router, new RegExp(path.replace(/[/:]/g, match => `\\${match}`)))
  }
})

test('task import remains preview then explicit commit', () => {
  assert.match(client, /apiPreviewTaskImport/)
  assert.match(client, /\/api\/v1\/tasks\/import\/preview/)
  assert.match(client, /apiCommitTaskImport/)
  assert.match(client, /\/api\/v1\/tasks\/import\/commit/)
  assert.match(view, /预览阶段不会写入任务/)
  assert.match(view, /确认导入/)
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
