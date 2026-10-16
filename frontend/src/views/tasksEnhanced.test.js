import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const view = await readFile(new URL('./TasksEnhancedView.vue', import.meta.url), 'utf8')
const settingsView = await readFile(new URL('./TaskSettingsView.vue', import.meta.url), 'utf8')
const dataPanel = await readFile(new URL('../components/tasks/TaskDataPanel.vue', import.meta.url), 'utf8')
const inboxView = await readFile(new URL('./TasksInboxView.vue', import.meta.url), 'utf8')
const workspaceView = await readFile(new URL('./TaskWorkspaceView.vue', import.meta.url), 'utf8')
const calendarView = await readFile(new URL('./TasksCalendarView.vue', import.meta.url), 'utf8')
const router = await readFile(new URL('../router/index.js', import.meta.url), 'utf8')
const client = await readFile(new URL('../../packages/api-client/src/index.js', import.meta.url), 'utf8')

test('task enhanced workspace exposes all I7 and timeline routes', () => {
  for (const path of ['/tasks/board', '/tasks/quadrant', '/tasks/search', '/tasks/stats', '/tasks/filter/:publicId', '/tasks/timeline']) {
    assert.match(router, new RegExp(path.replace(/[/:]/g, match => `\\${match}`)))
  }
})

test('task timeline and keyboard shortcuts complete the third residual batch', async () => {
  const timeline = await readFile(new URL('./TasksTimelineView.vue', import.meta.url), 'utf8')
  const app = await readFile(new URL('../App.vue', import.meta.url), 'utf8')
  const store = await readFile(new URL('../stores/tasks.js', import.meta.url), 'utf8')
  assert.match(timeline, /时间线 \/ 甘特/)
  assert.match(timeline, /timeline-bar/)
  assert.match(app, /event\.key\.toLowerCase\(\) === 'k'/)
  assert.match(app, /event\.key\.toLowerCase\(\) === 'z'/)
  assert.match(app, /event\.key\.toLowerCase\(\) === 'n'/)
  assert.match(store, /async undoLast\(\)/)
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

test('task inbox uses a ticktick-style three-pane workspace shell', () => {
  assert.match(inboxView, /grid-template-columns:240px minmax\(370px,1fr\) 8px minmax\(320px,var\(--task-detail-width,460px\)\)/)
  assert.match(inboxView, /class="task-detail-pane"/)
  assert.match(inboxView, /添加任务至“\{\{ viewTitle \}\}”/)
  assert.match(inboxView, /class="side-count"/)
  assert.match(inboxView, /@click="toggleSidebar"/)
  assert.doesNotMatch(inboxView, /task-navigation-backdrop|task-navigation-open/)
  assert.match(inboxView, /class="task-detail task-editor-pane"/)
  assert.match(inboxView, /Dialog v-model:open="organizationOpen"/)
  assert.doesNotMatch(inboxView, /<Drawer|ui\/Drawer/)
  assert.match(inboxView, /function openCreate\(\) \{ selectedTask\.value = null/)
  assert.match(inboxView, /task-sidebar-collapsed:\$\{store\.accountScope\}/)
  assert.match(inboxView, /sidebar-collapsed/)
  assert.match(inboxView, /显示详细/)
  assert.match(inboxView, /显示检查事项/)
  assert.match(inboxView, /task-list-view:\$\{store\.accountScope\}/)
  assert.match(inboxView, /class="task-detail-divider"/)
  assert.match(inboxView, /class="task-row-more"/)
  assert.match(inboxView, /class="task-row-menu"/)
  assert.match(inboxView, /逾期/)
})

test('task list routes use the replacement workspace and preserve the task contract', () => {
  for (const path of ['/tasks/inbox', '/tasks/today', '/tasks/tomorrow', '/tasks/next7', '/tasks/all', '/tasks/completed', '/tasks/trash', '/tasks/list/:publicId', '/tasks/tag/:publicId', '/tasks/filter/:publicId', '/tasks/shortcut/:shortcut']) {
    const routePattern = new RegExp(`path: '${path.replace(/[/:]/g, match => `\\${match}`)}'[\\s\\S]*TaskWorkspaceView`)
    assert.match(router, routePattern)
  }
  for (const token of ['clone-task-shell', 'TaskRichTextEditor', 'batchTasks', 'sortTask', 'clone-context-menu', 'store.createList']) assert.match(workspaceView, new RegExp(token.replace('.', '\\.' )))
  assert.match(workspaceView, /route\.name === ["']tasks-trash["'][\s\S]*\? task\.deleted/)
  for (const token of ['parentId', 'tagIds', 'durationMinutes', 'allDay', 'recurrenceAnchor', 'apiListTaskReminders', 'apiCreateTaskReminder', 'apiUpdateTaskReminder', 'apiDeleteTaskReminder', '提醒需要联网保存']) assert.match(workspaceView, new RegExp(token))
})

test('task inbox mirrors ticktick shortcut navigation and mixed efficiency groups', () => {
  assert.match(router, /path: '\/tasks\/shortcut\/:shortcut'/)
  for (const label of ['指派给我', '摘要', '归档', '本周', '下周', '无日期任务', '无标签']) assert.match(inboxView, new RegExp(label))
  assert.match(inboxView, /task-week-nav/)
  assert.match(inboxView, /todayCountdowns/)
  assert.match(inboxView, /todayHabits/)
  assert.match(inboxView, /toggleHabit/)
  assert.match(inboxView, /copyTaskLink/)
})

test('task rows open the persistent detail editor for direct task edits', () => {
  assert.match(inboxView, /async function selectTaskDetail\(task\) \{ await openEdit\(task\) \}/)
  assert.match(inboxView, /task-editor/)
  assert.match(inboxView, /TaskRichTextEditor v-model="form.description"/)
  assert.match(inboxView, /检查项<textarea v-model="form.checklistText"/)
  assert.match(inboxView, /提醒/)
})

test('task rows expose a viewport-safe context menu with contract actions', () => {
  assert.match(inboxView, /@contextmenu\.prevent="openTaskContextMenu\(task, \$event\)"/)
  assert.match(inboxView, /class="task-context-menu"/)
  for (const token of ['打开详情', '标记完成', '重新打开', '创建副本', '删除任务']) assert.match(inboxView, new RegExp(token))
  assert.match(inboxView, /Math\.min\(event\.clientX, window\.innerWidth - 228\)/)
  assert.match(inboxView, /document\.addEventListener\('click', closeTaskContextMenu\)/)
})

test('task detail exposes contract-backed Markdown comments and attachments', () => {
  assert.match(inboxView, /TaskRichTextEditor/)
  for (const token of ['apiListTaskComments', 'apiCreateTaskComment', 'apiUpdateTaskComment', 'apiDeleteTaskComment', 'apiListTaskAttachments', 'apiUploadTaskAttachment', 'apiDeleteTaskAttachment']) assert.match(inboxView, new RegExp(token))
  assert.match(inboxView, /retryAttachment/)
})

test('task calendar edits tasks in a page panel instead of a drawer', () => {
  assert.match(calendarView, /class="calendar-task-editor"/)
  assert.doesNotMatch(calendarView, /<Drawer|ui\/Drawer/)
})

test('task sidebar sections reorder independently and task rows open the detail pane', () => {
  assert.match(inboxView, /collapsedSections\.lists/)
  assert.match(inboxView, /collapsedSections\.tags/)
  assert.match(inboxView, /collapsedSections\.filters/)
  assert.match(inboxView, /dropOrganization\('tag', tag\)/)
  assert.match(inboxView, /dropOrganization\('filter', filter\)/)
  assert.match(inboxView, /apiUpdateTaskFilter/)
  assert.match(inboxView, /selectTaskDetail\(task\)/)
  assert.match(inboxView, /class="task-detail"/)
  assert.match(router, /path: '\/tasks\/filter\/:publicId'[\s\S]*TaskWorkspaceView/)
})

test('task domain exposes a ticktick-style desktop module rail', async () => {
  const rail = await readFile(new URL('../components/tasks/TaskModuleRail.vue', import.meta.url), 'utf8')
  const app = await readFile(new URL('../App.vue', import.meta.url), 'utf8')
  const theme = await readFile(new URL('../styles/theme.css', import.meta.url), 'utf8')
  for (const path of ['/tasks/today', '/tasks/calendar', '/tasks/focus', '/tasks/habits', '/tasks/countdowns', '/tasks/search', '/tasks/settings']) assert.match(rail, new RegExp(path.replaceAll('/', '\\/')))
  assert.match(app, /<TaskModuleRail v-if="isTasks"/)
  assert.match(theme, /workspace-topbar\.task-domain-topbar \{ display:none; \}/)
  assert.match(theme, /margin-left:58px/)
})
