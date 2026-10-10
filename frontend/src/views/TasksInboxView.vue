<template>
  <section class="task-workspace">
    <button class="task-navigation-trigger" type="button" aria-controls="task-navigation" :aria-expanded="navigationOpen" @click="navigationOpen = true"><Menu :size="18" />任务菜单</button>
    <Teleport to="body" :disabled="!compactNavigation">
      <Transition name="task-navigation-fade">
        <button v-if="navigationOpen" class="task-navigation-backdrop" type="button" aria-label="关闭任务菜单" @click="closeNavigation"></button>
      </Transition>
      <aside id="task-navigation" class="task-sidebar" :class="{ open: navigationOpen }" aria-label="任务导航">
        <header class="task-sidebar-head"><strong>任务菜单</strong><button type="button" aria-label="关闭任务菜单" @click="closeNavigation"><X :size="18" /></button></header>
        <nav><RouterLink v-for="item in builtins" :key="item.to" :to="item.to" @click="closeNavigation"><component :is="item.icon" :size="16" />{{ item.label }}</RouterLink></nav>
        <div class="side-heading"><span>清单</span><button type="button" aria-label="新建清单" title="新建清单" @click="newList"><Plus :size="15" /></button></div>
        <nav><span v-for="list in store.lists" :key="list.publicId" class="side-item"><RouterLink :to="list.systemKey === 'INBOX' ? '/tasks/inbox' : `/tasks/list/${list.publicId}`" @click="closeNavigation"><ListTodo :size="16" />{{ list.name }}</RouterLink><span v-if="!list.systemKey" class="side-actions"><button type="button" title="编辑清单" :aria-label="`编辑清单${list.name}`" @click="editList(list)"><Pencil :size="13" /></button><button type="button" title="删除清单" :aria-label="`删除清单${list.name}`" @click="deleteList(list)"><Trash2 :size="13" /></button></span></span></nav>
        <div class="side-heading"><span>标签</span><button type="button" aria-label="新建标签" title="新建标签" @click="newTag"><Plus :size="15" /></button></div>
        <nav><span v-for="tag in store.tags" :key="tag.publicId" class="side-item"><RouterLink :to="`/tasks/tag/${tag.publicId}`" @click="closeNavigation"><Tag :size="15" />{{ tag.name }}</RouterLink><span class="side-actions"><button type="button" title="编辑标签" :aria-label="`编辑标签${tag.name}`" @click="editTag(tag)"><Pencil :size="13" /></button><button type="button" title="删除标签" :aria-label="`删除标签${tag.name}`" @click="deleteTag(tag)"><Trash2 :size="13" /></button></span></span></nav>
      </aside>
    </Teleport>

    <main class="tasks-page">
      <header class="tasks-head"><div><p class="label">TASKS</p><h1>{{ viewTitle }}</h1><p class="tasks-summary">{{ visibleOpen.length }} 项待完成 · {{ visibleCompleted.length }} 项已完成</p></div><Button v-if="viewKey !== 'trash'" @click="openCreate"><Plus :size="16" />新建任务</Button></header>
      <label v-if="viewKey !== 'trash'" class="task-sort">排序<select v-model="sortBy"><option value="default">默认</option><option value="due">截止时间</option><option value="priority">优先级</option><option value="title">标题</option></select></label>
      <div v-if="!store.online || appStore.offlineSession || store.pending" class="tasks-warning" role="status"><WifiOff v-if="!store.online || appStore.offlineSession" :size="18" /><span v-if="!store.online || appStore.offlineSession">当前离线，变更已保存在本机。</span><span v-else>{{ store.pending }} 项变更等待同步。</span></div>
      <div v-if="store.conflicts.length" class="tasks-error" role="alert">任务“{{ issueTitle(store.conflicts[0]) }}”发生版本冲突。<Button size="sm" variant="ghost" @click="resolveConflict('server')">采用服务端</Button><Button size="sm" variant="ghost" @click="resolveConflict('local')">保留本地</Button></div>
      <div v-if="store.rejected.length" class="tasks-error" role="alert">任务“{{ issueTitle(store.rejected[0]) }}”未同步：{{ store.rejected[0].result?.message || '服务端拒绝了本次变更' }}</div><div v-if="store.error" class="tasks-error" role="alert">{{ store.error }}</div>

      <div v-if="store.loading && !store.ready" class="tasks-empty">正在加载任务…</div>
      <template v-else-if="viewKey === 'trash'"><div v-if="!visibleTasks.length && !store.deletedLists.length" class="tasks-empty"><Trash2 :size="28" /><strong>回收站是空的</strong></div><div v-else><section v-if="store.deletedLists.length" class="task-group"><h2>已删除清单 <span>{{ store.deletedLists.length }}</span></h2><ul class="task-list"><li v-for="list in store.deletedLists" :key="list.publicId" class="task-row"><ListTodo :size="18" /><span class="task-content"><strong>{{ list.name }}</strong><small>恢复后保留原任务结构和删除状态</small></span><Button size="sm" variant="ghost" @click="restoreList(list)"><RotateCcw :size="15" />恢复清单</Button></li></ul></section><section v-if="visibleTasks.length" class="task-group"><h2>已删除任务 <span>{{ visibleTasks.length }}</span></h2><ul class="task-list"><li v-for="task in visibleTasks" :key="task.publicId" class="task-row"><span class="task-content"><strong>{{ task.title }}</strong><small>{{ listName(task.listId) }} · {{ formatTime(task.deletedAt) }}</small></span><Button size="sm" variant="ghost" @click="restore(task)"><RotateCcw :size="15" />恢复</Button><Button size="sm" variant="danger" @click="purge(task)"><Trash2 :size="15" />清除</Button></li></ul></section></div></template>
      <template v-else><div v-if="!visibleTasks.length" class="tasks-empty"><Inbox :size="28" /><strong>{{ emptyLabel }}</strong></div><div v-else class="tasks-sections">
        <section v-if="visibleOpen.length" class="task-group"><h2>待完成 <span>{{ visibleOpen.length }}</span></h2><ul class="task-list"><li v-for="task in visibleOpen" :key="task.publicId" class="task-row" :class="{ subtask: task.parentId }"><button class="task-check" type="button" :aria-label="`完成${task.title}`" @click="complete(task)"><Circle :size="21" /></button><button class="task-content" type="button" @click="openEdit(task)"><strong>{{ task.title }}</strong><small v-if="task.description">{{ task.description }}</small><span class="task-meta"><span>{{ listName(task.listId) }}</span><span v-for="tagId in task.tagIds || []" :key="tagId">#{{ tagName(tagId) }}</span><span v-if="task.dueAt"><CalendarClock :size="13" />{{ formatTime(task.dueAt) }}</span><span v-if="task.totalSubtasks">{{ task.completedSubtasks }}/{{ task.totalSubtasks }} 子任务</span></span></button><span v-if="task.priority !== 'NONE'" class="task-priority" :data-priority="task.priority">{{ priorityLabel(task.priority) }}</span></li></ul></section>
        <section v-if="visibleCompleted.length" class="task-group completed"><h2>已完成 <span>{{ visibleCompleted.length }}</span></h2><ul class="task-list"><li v-for="task in visibleCompleted" :key="task.publicId" class="task-row"><button class="task-check checked" type="button" :aria-label="`重新打开${task.title}`" @click="reopen(task)"><CheckCircle2 :size="21" /></button><button class="task-content" type="button" @click="openEdit(task)"><strong>{{ task.title }}</strong></button><Button variant="icon" size="sm" :aria-label="`删除${task.title}`" title="删除" @click="remove(task)"><Trash2 :size="15" /></Button></li></ul></section>
      </div></template>

      <Drawer v-model:open="editorOpen" :title="editing ? '编辑任务' : '新建任务'"><form id="task-editor" class="task-form" @submit.prevent="save"><label>标题<Input v-model="form.title" placeholder="要完成什么？" /></label><label>描述<textarea v-model="form.description" class="ui-textarea" placeholder="补充必要信息"></textarea></label><div class="task-form-grid"><label>清单<select v-model="form.listId"><option v-for="list in store.lists.filter(item => !item.archived)" :key="list.publicId" :value="list.publicId">{{ list.name }}</option></select></label><label>父任务<select v-model="form.parentId"><option value="">无</option><option v-for="task in parentOptions" :key="task.publicId" :value="task.publicId">{{ task.title }}</option></select></label></div><div class="task-form-grid"><label>优先级<select v-model="form.priority"><option value="NONE">无</option><option value="LOW">低</option><option value="MEDIUM">中</option><option value="HIGH">高</option></select></label><label>截止时间<Input v-model="form.dueLocal" type="datetime-local" /></label></div><div class="task-form-grid"><label>重复<select v-model="form.recurrence"><option value="">不重复</option><option value="FREQ=DAILY">每天</option><option value="FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR">每个工作日</option><option value="FREQ=WEEKLY">每周</option><option value="FREQ=MONTHLY">每月</option><option value="FREQ=YEARLY">每年</option><option value="CUSTOM">自定义</option></select></label><label v-if="form.recurrence">循环基准<select v-model="form.recurrenceAnchor"><option value="DUE_DATE">按到期日期</option><option value="COMPLETION_DATE">按完成日期</option></select></label></div><label v-if="form.recurrence === 'CUSTOM'">RRULE<Input v-model="form.customRrule" placeholder="FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,FR" /></label><fieldset class="task-reminder-editor"><legend>提醒</legend><label class="task-inline-check"><input v-model="form.reminderEnabled" type="checkbox" /> 启用站内提醒</label><div v-if="form.reminderEnabled" class="task-form-grid"><label>方式<select v-model="form.reminderKind"><option value="RELATIVE">相对截止时间</option><option value="ABSOLUTE">指定时间</option></select></label><label v-if="form.reminderKind === 'RELATIVE'">提前<select v-model.number="form.reminderOffset"><option :value="0">准时</option><option :value="5">5 分钟</option><option :value="15">15 分钟</option><option :value="30">30 分钟</option><option :value="60">1 小时</option><option :value="1440">1 天</option></select></label><label v-else>提醒时间<Input v-model="form.remindLocal" type="datetime-local" /></label></div></fieldset><fieldset v-if="store.tags.length" class="task-tags"><legend>标签</legend><label v-for="tag in store.tags" :key="tag.publicId"><input v-model="form.tagIds" type="checkbox" :value="tag.publicId" />{{ tag.name }}</label></fieldset><label>检查项<textarea v-model="form.checklistText" class="ui-textarea checklist-input" placeholder="每行一个检查项"></textarea></label><p v-if="editorError" class="task-form-error" role="alert">{{ editorError }}</p></form><template #footer><Button v-if="editing" class="task-delete" variant="danger" :disabled="store.saving" @click="remove(editing)"><Trash2 :size="15" />删除</Button><Button type="submit" form="task-editor" :disabled="store.saving || !form.title.trim()"><Save :size="15" />保存</Button></template></Drawer>
    </main>
    <Drawer v-model:open="organizationOpen" :title="`${organizationItem ? '编辑' : '新建'}${organizationType === 'list' ? '清单' : '标签'}`">
      <form id="organization-editor" class="task-form" @submit.prevent="saveOrganization">
        <label>名称<Input v-model="organizationForm.name" /></label>
        <label>颜色<Input v-model="organizationForm.color" type="color" /></label>
        <label>排序位置<Input v-model="organizationForm.sortOrder" type="number" min="0" /></label>
        <template v-if="organizationType === 'list'">
          <label>图标<Input v-model="organizationForm.icon" placeholder="可留空" /></label>
          <label><span><input v-model="organizationForm.archived" type="checkbox" /> 归档清单</span></label>
        </template>
        <label v-else>父标签<select v-model="organizationForm.parentId"><option value="">无</option><option v-for="tag in store.tags.filter(item => !item.parentId && item.publicId !== organizationItem?.publicId)" :key="tag.publicId" :value="tag.publicId">{{ tag.name }}</option></select></label>
        <p v-if="organizationError" class="task-form-error" role="alert">{{ organizationError }}</p>
      </form>
      <template #footer><Button type="submit" form="organization-editor" :disabled="!organizationForm.name.trim()">保存</Button></template>
    </Drawer>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Bell, CalendarClock, CheckCircle2, Circle, Inbox, ListTodo, Menu, Pencil, Plus, RotateCcw, Save, Tag, Timer, Trash2, WifiOff, X } from 'lucide-vue-next'
import Button from '../components/ui/Button.vue'; import Drawer from '../components/ui/Drawer.vue'; import Input from '../components/ui/Input.vue'
import { useAppStore } from '../stores/app.js'; import { useTasksStore } from '../stores/tasks.js'
import { apiCreateTaskReminder, apiDeleteTaskReminder, apiListTaskReminders, apiUpdateTaskReminder } from '../../packages/api-client/src/index.js'

const route = useRoute(); const store = useTasksStore(); const appStore = useAppStore(); const editorOpen = ref(false); const editing = ref(null); const editorError = ref(''); const sortBy = ref('default'); const navigationOpen = ref(false)
const compactNavigation = ref(false)
let compactNavigationQuery
const form = reactive({ title: '', description: '', priority: 'NONE', dueLocal: '', listId: '', parentId: '', tagIds: [], checklistText: '', recurrence: '', customRrule: '', recurrenceAnchor: 'DUE_DATE', reminderEnabled: false, reminderKind: 'RELATIVE', reminderOffset: 30, remindLocal: '', reminder: null })
const organizationOpen = ref(false)
const organizationItem = ref(null)
const organizationType = ref('list')
const organizationError = ref('')
const organizationForm = reactive({ name: '', color: '#2563eb', icon: '', sortOrder: 0, archived: false, parentId: '' })
const builtins = [{ to: '/tasks/inbox', label: '收件箱', icon: Inbox }, { to: '/tasks/today', label: '今天', icon: CalendarClock }, { to: '/tasks/next7', label: '最近 7 天', icon: CalendarClock }, { to: '/tasks/focus', label: '专注', icon: Timer }, { to: '/tasks/all', label: '全部', icon: ListTodo }, { to: '/tasks/completed', label: '已完成', icon: CheckCircle2 }, { to: '/tasks/inbox-notify', label: '提醒', icon: Bell }, { to: '/tasks/trash', label: '垃圾桶', icon: Trash2 }]
const viewKey = computed(() => route.path.split('/')[2] || 'today')
const viewTitle = computed(() => route.name === 'tasks-list' ? listName(route.params.publicId) : route.name === 'tasks-tag' ? `#${tagName(route.params.publicId)}` : ({ inbox: '收件箱', today: '今天', next7: '最近 7 天', all: '全部任务', completed: '已完成', trash: '垃圾桶' })[viewKey.value] || '任务')
const visibleTasks = computed(() => {
  const deletedListIds = new Set(store.deletedLists.map(item => item.publicId))
  if (viewKey.value === 'trash') return store.trashedTasks.filter(item => !deletedListIds.has(item.listId))
  let values = store.tasks.filter(item => !item.deleted && !deletedListIds.has(item.listId))
  if (route.name === 'tasks-list') values = values.filter(item => item.listId === route.params.publicId)
  else if (route.name === 'tasks-tag') values = values.filter(item => item.tagIds?.includes(route.params.publicId))
  else if (viewKey.value === 'inbox') values = values.filter(item => item.listId === store.inbox?.publicId)
  else if (viewKey.value === 'today' || viewKey.value === 'next7') {
    const archivedIds = new Set(store.lists.filter(item => item.archived).map(item => item.publicId))
    values = values.filter(item => !archivedIds.has(item.listId) && item.status !== 'COMPLETED' && item.dueAt &&
      new Date(item.dueAt) < addDays(startOfToday(), viewKey.value === 'today' ? 1 : 8) &&
      (viewKey.value === 'today' || new Date(item.dueAt) >= startOfToday()))
  } else if (viewKey.value === 'completed') values = values.filter(item => item.status === 'COMPLETED')
  if (sortBy.value === 'title') values.sort((a, b) => a.title.localeCompare(b.title, 'zh-CN'))
  if (sortBy.value === 'due') values.sort((a, b) => (a.dueAt ? Date.parse(a.dueAt) : Infinity) - (b.dueAt ? Date.parse(b.dueAt) : Infinity))
  if (sortBy.value === 'priority') { const rank = { HIGH: 0, MEDIUM: 1, LOW: 2, NONE: 3 }; values.sort((a, b) => rank[a.priority] - rank[b.priority]) }
  return values
})
const visibleOpen = computed(() => visibleTasks.value.filter(item => item.status !== 'COMPLETED'))
const visibleCompleted = computed(() => visibleTasks.value.filter(item => item.status === 'COMPLETED'))
const parentOptions = computed(() => store.openTasks.filter(item => {
  if (item.listId !== form.listId || item.publicId === editing.value?.publicId) return false
  let ancestor = item; let depth = 0; const visited = new Set()
  while (ancestor.parentId) {
    if (visited.has(ancestor.publicId) || ancestor.parentId === editing.value?.publicId) return false
    visited.add(ancestor.publicId); depth += 1
    ancestor = store.tasks.find(value => value.publicId === ancestor.parentId)
    if (!ancestor) return false
  }
  return depth < 2
}))
const emptyLabel = computed(() => viewKey.value === 'today' ? '今天没有安排' : viewKey.value === 'completed' ? '还没有已完成任务' : '这里还没有任务')
function resetForm(task = null) { const known = ['', 'FREQ=DAILY', 'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR', 'FREQ=WEEKLY', 'FREQ=MONTHLY', 'FREQ=YEARLY']; const recurrence = known.includes(task?.rrule || '') ? task?.rrule || '' : 'CUSTOM'; Object.assign(form, { title: task?.title || '', description: task?.description || '', priority: task?.priority || 'NONE', dueLocal: task?.dueAt ? toLocalInput(task.dueAt) : '', listId: task?.listId || currentListId(), parentId: task?.parentId || '', tagIds: [...(task?.tagIds || [])], checklistText: (task?.checklist || []).map(item => `${item.completed ? '[x] ' : ''}${item.title}`).join('\n'), recurrence, customRrule: recurrence === 'CUSTOM' ? task.rrule : '', recurrenceAnchor: task?.recurrenceAnchor || 'DUE_DATE', reminderEnabled: false, reminderKind: 'RELATIVE', reminderOffset: 30, remindLocal: '', reminder: null }); editorError.value = '' }
function closeNavigation() { navigationOpen.value = false }
function updateCompactNavigation(event) { compactNavigation.value = event.matches; if (!event.matches) closeNavigation() }
function currentListId() { return route.name === 'tasks-list' ? String(route.params.publicId) : store.inbox?.publicId || '' }
function openCreate() { editing.value = null; resetForm(); editorOpen.value = true } async function openEdit(task) { editing.value = task; resetForm(task); editorOpen.value = true; if (store.online) { try { const reminders = await apiListTaskReminders(task.publicId); const reminder = reminders?.[0]; if (reminder) Object.assign(form, { reminderEnabled: true, reminderKind: reminder.kind, reminderOffset: reminder.offsetMinutes ?? 30, remindLocal: reminder.remindAt ? toLocalInput(reminder.remindAt) : '', reminder }) } catch (error) { editorError.value = detail(error, '提醒加载失败') } } }
function payload() { const rrule = form.recurrence === 'CUSTOM' ? form.customRrule.trim() : form.recurrence; return { listId: form.listId, parentId: form.parentId || '', tagIds: form.tagIds, checklist: form.checklistText.split('\n').map(line => line.trim()).filter(Boolean).map((line, index) => ({ publicId: editing.value?.checklist?.[index]?.publicId, title: line.replace(/^\[x\]\s*/i, ''), completed: /^\[x\]/i.test(line), sortOrder: index })), title: form.title.trim(), description: form.description.trim(), priority: form.priority, dueAt: form.dueLocal ? new Date(form.dueLocal).toISOString() : '', allDay: false, timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai', rrule, recurrenceAnchor: rrule ? form.recurrenceAnchor : '' } }
async function save() { editorError.value = ''; try { const saved = editing.value ? await store.update(editing.value, payload()) : await store.create(payload()); if (form.reminderEnabled || form.reminder) { if (!store.online) throw new Error('提醒需要联网保存'); await store.syncNow(); const serverTask = store.tasks.find(item => item.publicId === saved.publicId) || saved; const reminder = { kind: form.reminderKind, offsetMinutes: form.reminderKind === 'RELATIVE' ? form.reminderOffset : null, remindAt: form.reminderKind === 'ABSOLUTE' && form.remindLocal ? new Date(form.remindLocal).toISOString() : null, channel: 'IN_APP', dailyUntilDone: false }; if (form.reminderEnabled) form.reminder ? await apiUpdateTaskReminder(serverTask.publicId, form.reminder.publicId, reminder, form.reminder.revision) : await apiCreateTaskReminder(serverTask.publicId, reminder); else await apiDeleteTaskReminder(serverTask.publicId, form.reminder.publicId, form.reminder.revision) } editorOpen.value = false } catch (error) { editorError.value = detail(error, '任务保存失败') } } async function complete(task) { try { await store.complete(task) } catch (error) { store.error = detail(error, '完成任务失败') } } async function reopen(task) { try { await store.reopen(task) } catch (error) { store.error = detail(error, '重新打开失败') } } async function remove(task) { if (!window.confirm(`删除任务“${task.title}”？`)) return; try { await store.remove(task); if (editing.value?.publicId === task.publicId) editorOpen.value = false } catch (error) { editorError.value = detail(error, '删除任务失败') } } async function restore(task) { try { await store.restore(task) } catch (error) { store.error = detail(error, '恢复任务失败') } }
async function purge(task) { if (window.prompt(`永久清除后无法恢复。请输入 DELETE:${task.publicId}`) !== `DELETE:${task.publicId}`) return; try { await store.purge(task) } catch (error) { store.error = detail(error, '永久清除失败') } }
async function restoreList(list) { try { await store.restoreList(list) } catch (error) { store.error = detail(error, '恢复清单失败') } }
function editOrganization(type, item = null) {
  closeNavigation()
  organizationType.value = type
  organizationItem.value = item
  organizationError.value = ''
  Object.assign(organizationForm, { name: item?.name || '', color: item?.color || '#2563eb', icon: item?.icon || '',
    sortOrder: item?.sortOrder ?? (type === 'list' ? store.lists.length : store.tags.length),
    archived: Boolean(item?.archived), parentId: item?.parentId || '' })
  organizationOpen.value = true
}
function newList() { editOrganization('list') }
function newTag() { editOrganization('tag') }
function editList(item) { editOrganization('list', item) }
function editTag(item) { editOrganization('tag', item) }
async function saveOrganization() {
  try {
    const value = { ...organizationForm, name: organizationForm.name.trim(), sortOrder: Number(organizationForm.sortOrder) }
    if (organizationType.value === 'list') {
      if (organizationItem.value) await store.updateList(organizationItem.value, value)
      else await store.createList(value)
    } else {
      if (organizationItem.value) await store.updateTag(organizationItem.value, value)
      else await store.createTag(value)
    }
    organizationOpen.value = false
  } catch (error) { organizationError.value = detail(error, '保存失败') }
}
async function resolveConflict(strategy) { await store.resolveConflict(store.conflicts[0].id, strategy) }
async function deleteList(list) { if (!window.confirm(`删除清单“${list.name}”并将其任务移入回收站？`)) return; try { await store.deleteList(list) } catch (error) { store.error = detail(error, '删除清单失败') } }
async function deleteTag(tag) { if (!window.confirm(`删除标签“${tag.name}”？任务本身不会删除。`)) return; try { await store.deleteTag(tag) } catch (error) { store.error = detail(error, '删除标签失败') } }
function listName(id) { return store.lists.find(item => item.publicId === id)?.name || '收件箱' } function tagName(id) { return store.tags.find(item => item.publicId === id)?.name || '标签' } function priorityLabel(value) { return ({ LOW: '低', MEDIUM: '中', HIGH: '高' })[value] || '' } function formatTime(value) { return value ? new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) : '' } function toLocalInput(value) { const date = new Date(value); return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16) } function detail(error, fallback) { return error?.response?.data?.detail || error?.message || fallback } function issueTitle(issue) { return issue?.operation?.payload?.title || issue?.operation?.entityId || '未知任务' } function startOfToday() { const value = new Date(); value.setHours(0, 0, 0, 0); return value } function addDays(value, days) { const result = new Date(value); result.setDate(result.getDate() + days); return result }
function onEscape(event) { if (event.key === 'Escape') closeNavigation() }
watch(navigationOpen, open => document.body.classList.toggle('task-navigation-open', open))
watch(() => route.fullPath, closeNavigation)
onMounted(() => {
  store.init().catch(() => {})
  compactNavigationQuery = window.matchMedia('(max-width: 1024px)')
  updateCompactNavigation(compactNavigationQuery)
  if (compactNavigationQuery.addEventListener) compactNavigationQuery.addEventListener('change', updateCompactNavigation)
  else compactNavigationQuery.addListener?.(updateCompactNavigation)
  window.addEventListener('keydown', onEscape)
})
onBeforeUnmount(() => {
  document.body.classList.remove('task-navigation-open')
  if (compactNavigationQuery?.removeEventListener) compactNavigationQuery.removeEventListener('change', updateCompactNavigation)
  else compactNavigationQuery?.removeListener?.(updateCompactNavigation)
  window.removeEventListener('keydown', onEscape)
})
</script>

<style scoped>
.task-workspace {
  display:grid;
  grid-template-columns:220px minmax(0,56.25rem);
  justify-content:center;
  gap:28px;
  padding:28px 24px 100px}
.task-sidebar {
  border-right:1px solid var(--line);
  padding:10px 18px 30px 0}
.task-sidebar-head,
.task-navigation-trigger,
.task-navigation-backdrop {
  display:none}
.task-sidebar nav {
  display:grid;
  gap:3px}
.task-sidebar a {
  display:flex;
  align-items:center;
  gap:9px;
  padding:9px 10px;
  color:var(--ink2);
  text-decoration:none;
  border-radius:4px;
  font-size:0.8125rem}
.task-sidebar a.router-link-active {
  background:var(--accent-soft);
  color:var(--accent);
  font-weight:700}
.side-heading {
  display:flex;
  align-items:center;
  justify-content:space-between;
  margin:24px 8px 7px;
  color:var(--muted);
  font-size:0.6875rem;
  font-weight:700}
.side-heading button {
  border:0;
  background:transparent;
  color:var(--muted);
  padding:4px}
.tasks-page {
  min-width:0}
.tasks-head {
  display:flex;
  justify-content:space-between;
  align-items:flex-end;
  gap:24px;
  border-bottom:1px solid var(--line);
  padding-bottom:22px}
.tasks-head h1 {
  margin:2px 0 4px;
  font:700 2.125rem Georgia,serif;
  letter-spacing:0}
.tasks-summary {
  margin:0;
  color:var(--muted)}
.tasks-warning,
.tasks-error {
  display:flex;
  align-items:center;
  gap:9px;
  padding:12px 14px;
  margin-top:18px;
  border:1px solid #d6a13d;
  background:#fff8e8;
  color:#76520c;
  border-radius:4px}
.tasks-error {
  border-color:rgba(180,35,24,
.35);
  background:rgba(180,35,24,
.05);
  color:var(--down)}
.tasks-empty {
  min-height:280px;
  display:flex;
  flex-direction:column;
  align-items:center;
  justify-content:center;
  gap:12px;
  color:var(--muted)}
.tasks-sections {
  display:grid;
  gap:32px;
  margin-top:28px}
.task-group h2 {
  margin:0 0 10px;
  font-size:0.875rem}
.task-group h2 span {
  color:var(--muted);
  font-weight:500}
.task-list {
  list-style:none;
  margin:28px 0 0;
  padding:0;
  border-top:1px solid var(--line)}
.task-group .task-list {
  margin-top:0}
.task-row {
  display:flex;
  align-items:center;
  gap:12px;
  min-height:62px;
  border-bottom:1px solid var(--line);
  padding:8px 4px}
.task-row.subtask {
  padding-left:28px}
.task-check {
  border:0;
  background:transparent;
  padding:6px;
  color:var(--muted)}
.task-check.checked {
  color:var(--accent)}
.task-content {
  min-width:0;
  flex:1;
  text-align:left;
  border:0;
  background:transparent;
  padding:6px;
  color:var(--ink)}
.task-content strong,
.task-content small {
  display:block;
  overflow:hidden;
  text-overflow:ellipsis;
  white-space:nowrap}
.task-content small {
  color:var(--muted);
  margin-top:2px}
.task-meta {
  display:flex;
  flex-wrap:wrap;
  gap:8px;
  color:var(--muted);
  font-size:0.75rem;
  margin-top:5px}
.task-meta span {
  display:flex;
  align-items:center;
  gap:3px}
.completed .task-content strong {
  text-decoration:line-through;
  color:var(--muted)}
.task-priority {
  font-size:0.6875rem;
  border-left:2px solid var(--line2);
  padding-left:7px;
  color:var(--muted)}
.task-priority[data-priority="HIGH"] {
  border-color:var(--down);
  color:var(--down)}
.task-priority[data-priority="MEDIUM"] {
  border-color:var(--warn);
  color:var(--warn)}
.task-form {
  display:grid;
  gap:18px}
.task-form label {
  display:grid;
  gap:7px;
  font-size:0.75rem;
  font-weight:600;
  color:var(--ink2)}
.task-form-grid {
  display:grid;
  grid-template-columns:1fr 1fr;
  gap:14px}
.task-tags {
  display:flex;
  flex-wrap:wrap;
  gap:10px;
  border:1px solid var(--line);
  padding:12px}
.task-tags legend {
  font-size:0.75rem;
  font-weight:700}
.task-tags label {
  display:flex;
  align-items:center;
  gap:5px}
.checklist-input {
  min-height:110px}
.task-form-error {
  margin:0;
  color:var(--down)}
.task-delete {
  margin-right:auto}
@media(max-width:1024px) {
  .task-workspace {
  display:block;
  padding:24px 16px 96px}
.task-navigation-trigger {
  display:inline-flex;
  align-items:center;
  gap:8px;
  margin-bottom:18px;
  padding:9px 12px;
  border:1px solid var(--line);
  border-radius:8px;
  background:var(--card);
  color:var(--ink);
  font-weight:700}
.task-navigation-backdrop {
  position:fixed;
  z-index:1000;
  inset:0;
  display:block;
  width:100%;
  height:100%;
  padding:0;
  border:0;
  background:rgba(15,23,42,.38)}
.task-sidebar {
  position:fixed;
  z-index:1001;
  top:0;
  bottom:0;
  left:0;
  width:min(84vw,320px);
  overflow-y:auto;
  border-right:1px solid var(--line);
  padding:18px 16px calc(32px + env(safe-area-inset-bottom));
  background:var(--card);
  box-shadow:18px 0 44px rgba(15,23,42,.2);
  transform:translateX(-105%);
  transition:transform .2s ease}
.task-sidebar.open {
  transform:translateX(0)}
.task-sidebar-head {
  display:flex;
  align-items:center;
  justify-content:space-between;
  margin-bottom:12px;
  padding:0 2px 12px;
  border-bottom:1px solid var(--line)}
.task-sidebar-head button {
  display:grid;
  width:36px;
  height:36px;
  place-items:center;
  padding:0;
  border:1px solid var(--line);
  border-radius:8px;
  background:transparent;
  color:var(--ink)}
.task-sidebar:not(.open) {
  pointer-events:none}
.tasks-head h1 {
  font-size:1.75rem}
.task-form-grid {
  grid-template-columns:1fr}
.task-row {
  gap:7px}
.task-priority {
  display:none}
}

.side-item {
  display:flex;
  align-items:center;
  min-width:0}
.side-item>a {
  min-width:0;
  flex:1}
.side-actions {
  display:flex}
.side-actions button {
  border:0;
  background:transparent;
  color:var(--muted);
  padding:4px}
.side-actions button:hover {
  color:var(--ink)}

.task-sort {
  display:flex;
  align-items:center;
  gap:10px;
  margin-top:16px;
  font-size:.875rem}

.task-sort select {
  width:auto}

@media(max-width:1024px) {
.tasks-head {
  gap:12px}
.tasks-warning,
.tasks-error {
  flex-wrap:wrap}
.task-workspace {
  padding-bottom:calc(96px + env(safe-area-inset-bottom))}
:global(body.task-navigation-open) {
  overflow:hidden}
}

.task-navigation-fade-enter-active,
.task-navigation-fade-leave-active {
  transition:opacity .2s ease}
.task-navigation-fade-enter-from,
.task-navigation-fade-leave-to {
  opacity:0}

</style>
