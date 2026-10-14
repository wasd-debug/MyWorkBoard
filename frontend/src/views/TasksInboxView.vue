<template>
  <section class="task-workspace" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
    <Teleport to="body" :disabled="!compactNavigation">
      <Transition name="task-navigation-fade">
        <button v-if="navigationOpen" class="task-navigation-backdrop" type="button" aria-label="关闭任务菜单" @click="closeNavigation"></button>
      </Transition>
      <aside id="task-navigation" class="task-sidebar" :class="{ open: navigationOpen }" aria-label="任务导航">
        <header class="task-sidebar-head"><strong>任务菜单</strong><button type="button" aria-label="关闭任务菜单" @click="closeNavigation"><X :size="18" /></button></header>
        <nav class="task-sidebar-primary"><RouterLink v-for="item in builtins" :key="item.to" :to="item.to" @click="closeNavigation"><component :is="item.icon" :size="16" /><span>{{ item.label }}</span><small v-if="navCount(item) !== null" class="side-count">{{ navCount(item) }}</small></RouterLink></nav>
        <section class="side-section" :class="{ collapsed: collapsedSections.lists }"><div class="side-heading"><button class="side-section-toggle" type="button" :aria-expanded="!collapsedSections.lists" @click="toggleSection('lists')"><ChevronRight :size="14" :class="{ expanded: !collapsedSections.lists }" /><span>清单</span></button><span><button type="button" aria-label="新建清单" title="新建清单" @click="newList"><Plus :size="15" /></button><button type="button" aria-label="新建清单分组" title="新建清单分组" @click="newGroup"><FolderPlus :size="15" /></button></span></div>
          <nav v-show="!collapsedSections.lists" @dragover.prevent><span v-for="list in ungroupedLists" :key="list.publicId" class="side-item" :draggable="!list.systemKey" @dragstart="startOrganizationDrag('list:', list)" @drop="dropOrganization('list:', list)"><RouterLink :to="list.systemKey === 'INBOX' ? '/tasks/inbox' : `/tasks/list/${list.publicId}`" @click="closeNavigation"><ListTodo :size="16" />{{ list.name }}</RouterLink><span v-if="!list.systemKey" class="side-actions"><button type="button" title="编辑清单" :aria-label="`编辑清单${list.name}`" @click="editList(list)"><Pencil :size="13" /></button><button type="button" title="删除清单" :aria-label="`删除清单${list.name}`" @click="deleteList(list)"><Trash2 :size="13" /></button></span></span><template v-for="group in store.listGroups" :key="group.publicId"><span class="group-row"><button class="group-heading" type="button" @click="toggleGroup(group.publicId)"><ChevronRight :size="14" :class="{ expanded: expandedGroups.has(group.publicId) }" />{{ group.name }}<span>{{ groupedLists(group.publicId).length }}</span></button><span class="side-actions"><button type="button" title="编辑分组" @click="editGroup(group)"><Pencil :size="13" /></button><button type="button" title="删除分组" @click="deleteGroup(group)"><Trash2 :size="13" /></button></span></span><template v-if="expandedGroups.has(group.publicId)"><span v-for="list in groupedLists(group.publicId)" :key="list.publicId" class="side-item nested" draggable="true" @dragstart="startOrganizationDrag(`list:${group.publicId}`, list)" @drop="dropOrganization(`list:${group.publicId}`, list)"><RouterLink :to="`/tasks/list/${list.publicId}`" @click="closeNavigation"><ListTodo :size="16" />{{ list.name }}</RouterLink><span class="side-actions"><button type="button" title="移出分组" @click="assignGroup(list, null)"><Unlink :size="13" /></button><button type="button" title="编辑清单" @click="editList(list)"><Pencil :size="13" /></button></span></span></template></template></nav></section>
        <section class="side-section" :class="{ collapsed: collapsedSections.tags }"><div class="side-heading"><button class="side-section-toggle" type="button" :aria-expanded="!collapsedSections.tags" @click="toggleSection('tags')"><ChevronRight :size="14" :class="{ expanded: !collapsedSections.tags }" /><span>标签</span></button><button type="button" aria-label="新建标签" title="新建标签" @click="newTag"><Plus :size="15" /></button></div><nav v-show="!collapsedSections.tags" @dragover.prevent><span v-for="tag in store.tags" :key="tag.publicId" class="side-item" draggable="true" @dragstart="startOrganizationDrag('tag', tag)" @drop="dropOrganization('tag', tag)"><RouterLink :to="`/tasks/tag/${tag.publicId}`" @click="closeNavigation"><Tag :size="15" />{{ tag.name }}</RouterLink><span class="side-actions"><button type="button" title="编辑标签" :aria-label="`编辑标签${tag.name}`" @click="editTag(tag)"><Pencil :size="13" /></button><button type="button" title="删除标签" :aria-label="`删除标签${tag.name}`" @click="deleteTag(tag)"><Trash2 :size="13" /></button></span></span></nav></section>
        <section class="side-section" :class="{ collapsed: collapsedSections.filters }"><div class="side-heading"><button class="side-section-toggle" type="button" :aria-expanded="!collapsedSections.filters" @click="toggleSection('filters')"><ChevronRight :size="14" :class="{ expanded: !collapsedSections.filters }" /><span>筛选器</span></button><RouterLink class="side-add-link" to="/tasks/search">管理</RouterLink></div><nav v-show="!collapsedSections.filters" @dragover.prevent><span v-for="filter in filters" :key="filter.publicId" class="side-item" draggable="true" @dragstart="startOrganizationDrag('filter', filter)" @drop="dropOrganization('filter', filter)"><RouterLink :to="`/tasks/filter/${filter.publicId}`" @click="closeNavigation"><ListTodo :size="15" />{{ filter.name }}</RouterLink></span><span v-if="!filters.length" class="side-empty">暂无筛选器</span></nav></section>
      </aside>
    </Teleport>

    <main class="tasks-page">
      <div v-if="editorOpen" class="task-core-time-fields" aria-label="任务时间字段"><label>开始时间<Input v-model="form.startLocal" type="datetime-local" /></label><label>截止时间<Input v-model="form.dueLocal" type="datetime-local" /></label><label>持续分钟数<Input v-model.number="form.durationMinutes" type="number" min="0" step="5" /></label><label class="task-inline-check"><input v-model="form.allDay" type="checkbox" />全天任务</label></div>
      <header class="tasks-head"><div class="tasks-title"><button class="task-collapse-button" type="button" :aria-label="compactNavigation ? '打开任务菜单' : (sidebarCollapsed ? '展开任务导航' : '折叠任务导航')" :title="compactNavigation ? '打开任务菜单' : (sidebarCollapsed ? '展开任务导航' : '折叠任务导航')" @click="toggleSidebar"><Menu :size="19" /></button><div><p class="label">TASKS</p><h1>{{ viewTitle }}</h1><p class="tasks-summary">{{ visibleOpen.length }} 项待完成 · {{ visibleCompleted.length }} 项已完成</p></div></div><div class="tasks-head-actions"><button class="task-head-icon" type="button" aria-label="排序方式" title="排序方式" @click="sortBy = sortBy === 'default' ? 'due' : 'default'"><ListTodo :size="17" /></button><Button v-if="viewKey !== 'trash'" @click="openCreate"><Plus :size="16" />新建任务</Button></div></header>
      <div v-if="viewKey !== 'trash'" class="task-quick-add"><Plus :size="17" /><button type="button" @click="openCreate">添加任务至“{{ viewTitle }}”</button><kbd>N</kbd></div>
      <div v-if="viewKey !== 'trash'" class="task-toolbar"><label class="task-sort">排序<select v-model="sortBy"><option value="default">手动</option><option value="due">截止时间</option><option value="priority">优先级</option><option value="title">标题</option></select></label><Button size="sm" variant="ghost" @click="toggleBatch">{{ batchMode ? '退出多选' : '多选模式' }}</Button></div>
      <div v-if="!store.online || appStore.offlineSession || store.pending" class="tasks-warning" role="status"><WifiOff v-if="!store.online || appStore.offlineSession" :size="18" /><span v-if="!store.online || appStore.offlineSession">当前离线，变更已保存在本机。</span><span v-else>{{ store.pending }} 项变更等待同步。</span></div>
      <div v-if="store.conflicts.length" class="tasks-error" role="alert">任务“{{ issueTitle(store.conflicts[0]) }}”发生版本冲突。<Button size="sm" variant="ghost" @click="resolveConflict('server')">采用服务端</Button><Button size="sm" variant="ghost" @click="resolveConflict('local')">保留本地</Button></div>
      <div v-if="store.rejected.length" class="tasks-error" role="alert">任务“{{ issueTitle(store.rejected[0]) }}”未同步：{{ store.rejected[0].result?.message || '服务端拒绝了本次变更' }}</div><div v-if="store.error" class="tasks-error" role="alert">{{ store.error }}</div>

      <div v-if="store.loading && !store.ready" class="tasks-empty">正在加载任务…</div>
      <template v-else-if="viewKey === 'trash'"><div v-if="!visibleTasks.length && !store.deletedLists.length" class="tasks-empty"><Trash2 :size="28" /><strong>回收站是空的</strong></div><div v-else><section v-if="store.deletedLists.length" class="task-group"><h2>已删除清单 <span>{{ store.deletedLists.length }}</span></h2><ul class="task-list"><li v-for="list in store.deletedLists" :key="list.publicId" class="task-row"><ListTodo :size="18" /><span class="task-content"><strong>{{ list.name }}</strong><small>恢复后保留原任务结构和删除状态</small></span><Button size="sm" variant="ghost" @click="restoreList(list)"><RotateCcw :size="15" />恢复清单</Button></li></ul></section><section v-if="visibleTasks.length" class="task-group"><h2>已删除任务 <span>{{ visibleTasks.length }}</span></h2><ul class="task-list"><li v-for="task in visibleTasks" :key="task.publicId" class="task-row"><span class="task-content"><strong>{{ task.title }}</strong><small>{{ listName(task.listId) }} · {{ formatTime(task.deletedAt) }}</small></span><Button size="sm" variant="ghost" @click="restore(task)"><RotateCcw :size="15" />恢复</Button><Button size="sm" variant="danger" @click="purge(task)"><Trash2 :size="15" />清除</Button></li></ul></section></div></template>
      <template v-else><div v-if="!visibleTasks.length" class="tasks-empty"><Inbox :size="28" /><strong>{{ emptyLabel }}</strong></div><div v-else class="tasks-sections">
        <section v-if="visibleOpen.length" class="task-group"><h2>待完成 <span>{{ visibleOpen.length }}</span></h2><ul class="task-list"><li v-for="(task, index) in visibleOpen" :key="task.publicId" class="task-row" :class="{ subtask: task.parentId, selected: selectedIds.has(task.publicId) || selectedTask?.publicId === task.publicId }" :draggable="canDrag(task)" @dragstart="startDrag(task)" @dragover.prevent @drop="dropTask(task, index)"><button v-if="batchMode" class="task-select" type="button" :aria-label="`选择${task.title}`" @click="selectTask(task)"><CheckCircle2 v-if="selectedIds.has(task.publicId)" :size="21" /><Circle v-else :size="21" /></button><button v-else class="task-check" type="button" :aria-label="`完成${task.title}`" @click="complete(task)"><Circle :size="21" /></button><button class="task-content" type="button" @click="batchMode ? selectTask(task) : selectTaskDetail(task)"><strong>{{ task.title }}</strong><small v-if="task.description">{{ task.description }}</small><span class="task-meta"><span>{{ listName(task.listId) }}</span><span v-for="tagId in task.tagIds || []" :key="tagId">#{{ tagName(tagId) }}</span><span v-if="task.dueAt"><CalendarClock :size="13" />{{ formatTime(task.dueAt) }}</span><span v-if="task.totalSubtasks">{{ task.completedSubtasks }}/{{ task.totalSubtasks }} 子任务</span></span></button><span v-if="task.priority !== 'NONE'" class="task-priority" :data-priority="task.priority">{{ priorityLabel(task.priority) }}</span><button v-if="sortBy === 'default' && !batchMode" class="task-move-top" type="button" @click="moveTop(task)">置顶</button></li></ul></section>
        <section v-if="visibleCompleted.length" class="task-group completed"><h2>已完成 <span>{{ visibleCompleted.length }}</span></h2><ul class="task-list"><li v-for="task in visibleCompleted" :key="task.publicId" class="task-row" :class="{ selected: selectedTask?.publicId === task.publicId }"><button class="task-check checked" type="button" :aria-label="`重新打开${task.title}`" @click="reopen(task)"><CheckCircle2 :size="21" /></button><button class="task-content" type="button" @click="selectTaskDetail(task)"><strong>{{ task.title }}</strong></button><Button variant="icon" size="sm" :aria-label="`删除${task.title}`" title="删除" @click="remove(task)"><Trash2 :size="15" /></Button></li></ul></section>
      </div></template>

      <div v-if="batchMode && selectedTasks.length" class="task-batch-bar" role="toolbar" aria-label="批量操作"><strong>已选 {{ selectedTasks.length }} 项</strong><Button size="sm" @click="runBatch('COMPLETE')">完成</Button><select v-model="batchListId" aria-label="移动到清单"><option value="">移动到…</option><option v-for="list in store.lists.filter(item => !item.archived)" :key="list.publicId" :value="list.publicId">{{ list.name }}</option></select><Button size="sm" :disabled="!batchListId" @click="runBatch('MOVE', { listId: batchListId })">移动</Button><select v-model="batchPriority" aria-label="设置优先级"><option value="NONE">无优先级</option><option value="LOW">低</option><option value="MEDIUM">中</option><option value="HIGH">高</option></select><Button size="sm" @click="runBatch('PRIORITY', { priority: batchPriority })">优先级</Button><Input v-model="batchDueLocal" aria-label="批量改期" type="datetime-local" /><Button size="sm" :disabled="!batchDueLocal" @click="runBatch('RESCHEDULE', { dueAt: new Date(batchDueLocal).toISOString() })">改期</Button><select v-model="batchTagId" aria-label="批量添加标签"><option value="">添加标签…</option><option v-for="tag in store.tags" :key="tag.publicId" :value="tag.publicId">{{ tag.name }}</option></select><Button size="sm" :disabled="!batchTagId" @click="runBatch('TAGS', { tagIds: [batchTagId] })">加标签</Button><select v-model.number="batchReminderOffset" aria-label="批量设置提醒"><option :value="0">准时提醒</option><option :value="30">提前 30 分钟</option><option :value="60">提前 1 小时</option><option :value="1440">提前 1 天</option></select><Button size="sm" @click="runBatch('REMINDER', { reminder: { kind: 'RELATIVE', offsetMinutes: batchReminderOffset, channel: 'IN_APP', dailyUntilDone: false } })">提醒</Button><Button size="sm" variant="danger" @click="runBatch('DELETE')">删除</Button><button type="button" aria-label="退出多选" @click="toggleBatch"><X :size="18" /></button></div>

      <Drawer v-model:open="editorOpen" :title="editing ? '编辑任务' : '新建任务'"><form id="task-editor" class="task-form" @submit.prevent="save"><label>标题<Input v-model="form.title" placeholder="要完成什么？" /></label><label>描述<textarea v-model="form.description" class="ui-textarea" placeholder="补充必要信息"></textarea></label><div class="task-form-grid"><label>清单<select v-model="form.listId"><option v-for="list in store.lists.filter(item => !item.archived)" :key="list.publicId" :value="list.publicId">{{ list.name }}</option></select></label><label>父任务<select v-model="form.parentId"><option value="">无</option><option v-for="task in parentOptions" :key="task.publicId" :value="task.publicId">{{ task.title }}</option></select></label></div><div class="task-form-grid"><label>优先级<select v-model="form.priority"><option value="NONE">无</option><option value="LOW">低</option><option value="MEDIUM">中</option><option value="HIGH">高</option></select></label><label>截止时间<Input v-model="form.dueLocal" type="datetime-local" /></label></div><div class="task-form-grid"><label>重复<select v-model="form.recurrence"><option value="">不重复</option><option value="FREQ=DAILY">每天</option><option value="FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR">每个工作日</option><option value="FREQ=WEEKLY">每周</option><option value="FREQ=MONTHLY">每月</option><option value="FREQ=YEARLY">每年</option><option value="CUSTOM">自定义</option></select></label><label v-if="form.recurrence">循环基准<select v-model="form.recurrenceAnchor"><option value="DUE_DATE">按到期日期</option><option value="COMPLETION_DATE">按完成日期</option></select></label></div><label v-if="form.recurrence === 'CUSTOM'">RRULE<Input v-model="form.customRrule" placeholder="FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,FR" /></label><fieldset class="task-reminder-editor"><legend>提醒</legend><label class="task-inline-check"><input v-model="form.reminderEnabled" type="checkbox" /> 启用站内提醒</label><div v-if="form.reminderEnabled" class="task-form-grid"><label>方式<select v-model="form.reminderKind"><option value="RELATIVE">相对截止时间</option><option value="ABSOLUTE">指定时间</option></select></label><label v-if="form.reminderKind === 'RELATIVE'">提前<select v-model.number="form.reminderOffset"><option :value="0">准时</option><option :value="5">5 分钟</option><option :value="15">15 分钟</option><option :value="30">30 分钟</option><option :value="60">1 小时</option><option :value="1440">1 天</option></select></label><label v-else>提醒时间<Input v-model="form.remindLocal" type="datetime-local" /></label></div></fieldset><fieldset v-if="store.tags.length" class="task-tags"><legend>标签</legend><label v-for="tag in store.tags" :key="tag.publicId"><input v-model="form.tagIds" type="checkbox" :value="tag.publicId" />{{ tag.name }}</label></fieldset><label>检查项<textarea v-model="form.checklistText" class="ui-textarea checklist-input" placeholder="每行一个检查项"></textarea></label><p v-if="editorError" class="task-form-error" role="alert">{{ editorError }}</p></form><section v-if="editing" class="task-activity"><h3>活动记录</h3><p v-if="activityLoading">加载中…</p><p v-else-if="!activities.length">暂无活动记录</p><ol v-else><li v-for="item in activities" :key="item.publicId"><strong>{{ item.summary }}</strong><time>{{ formatTime(item.createdAt) }}</time></li></ol></section><template #footer><Button v-if="editing" class="task-delete" variant="danger" :disabled="store.saving" @click="remove(editing)"><Trash2 :size="15" />删除</Button><Button type="submit" form="task-editor" :disabled="store.saving || !form.title.trim()"><Save :size="15" />保存</Button></template></Drawer>
    </main>
    <aside class="task-detail-pane" :class="{ active: selectedTask }" aria-label="任务详情"><div v-if="selectedTask" class="task-detail"><header><span>{{ listName(selectedTask.listId) }}</span><div><button type="button" aria-label="编辑任务" title="编辑任务" @click="openEdit(selectedTask)"><Pencil :size="16" /></button><button type="button" aria-label="关闭任务详情" title="关闭任务详情" @click="selectedTask = null"><X :size="17" /></button></div></header><section class="task-detail-title"><button class="task-check" type="button" :aria-label="selectedTask.status === 'COMPLETED' ? `重新打开${selectedTask.title}` : `完成${selectedTask.title}`" @click="selectedTask.status === 'COMPLETED' ? reopen(selectedTask) : complete(selectedTask)"><CheckCircle2 v-if="selectedTask.status === 'COMPLETED'" :size="22" /><Circle v-else :size="22" /></button><div><h2>{{ selectedTask.title }}</h2><p v-if="selectedTask.description">{{ selectedTask.description }}</p><p v-else class="detail-placeholder">暂无描述</p></div></section><dl><div><dt>清单</dt><dd>{{ listName(selectedTask.listId) }}</dd></div><div><dt>日期</dt><dd>{{ selectedTask.dueAt ? formatTime(selectedTask.dueAt) : '无日期' }}</dd></div><div><dt>优先级</dt><dd>{{ priorityLabel(selectedTask.priority) || '无' }}</dd></div><div v-if="selectedTask.rrule"><dt>重复</dt><dd>{{ selectedTask.rrule }}</dd></div></dl><section v-if="selectedTask.checklist?.length" class="detail-checklist"><h3>检查项 <span>{{ selectedTask.checklist.filter(item => item.completed).length }}/{{ selectedTask.checklist.length }}</span></h3><p v-for="item in selectedTask.checklist" :key="item.publicId || item.title" :class="{ done: item.completed }"><CheckCircle2 v-if="item.completed" :size="16" /><Circle v-else :size="16" />{{ item.title }}</p></section><section class="task-activity detail-activity"><h3>活动记录</h3><p v-if="activityLoading">加载中…</p><p v-else-if="!activities.length">{{ store.online ? '暂无活动记录' : '离线时无法加载活动记录' }}</p><ol v-else><li v-for="item in activities" :key="item.publicId"><strong>{{ item.summary }}</strong><time>{{ formatTime(item.createdAt) }}</time></li></ol></section><footer><Button variant="danger" size="sm" @click="remove(selectedTask)"><Trash2 :size="15" />删除任务</Button></footer></div><div v-else class="task-detail-empty"><span class="task-detail-spark" aria-hidden="true">✦</span><strong>选择一个任务</strong><p>任务详情、检查项和活动记录会显示在这里</p></div></aside>
    <Drawer v-model:open="organizationOpen" :title="`${organizationItem ? '编辑' : '新建'}${organizationType === 'list' ? '清单' : organizationType === 'group' ? '清单分组' : '标签'}`">
      <form id="organization-editor" class="task-form" @submit.prevent="saveOrganization">
        <label>名称<Input v-model="organizationForm.name" /></label>
        <label v-if="organizationType !== 'group'">颜色<Input v-model="organizationForm.color" type="color" /></label>
        <label>排序位置<Input v-model="organizationForm.sortOrder" type="number" min="0" /></label>
        <template v-if="organizationType === 'list'">
          <label>图标<Input v-model="organizationForm.icon" placeholder="可留空" /></label>
          <label>清单分组<select v-model="organizationForm.groupId"><option value="">未分组</option><option v-for="group in store.listGroups" :key="group.publicId" :value="group.publicId">{{ group.name }}</option></select></label>
          <label><span><input v-model="organizationForm.archived" type="checkbox" /> 归档清单</span></label>
        </template>
        <label v-else-if="organizationType === 'tag'">父标签<select v-model="organizationForm.parentId"><option value="">无</option><option v-for="tag in store.tags.filter(item => !item.parentId && item.publicId !== organizationItem?.publicId)" :key="tag.publicId" :value="tag.publicId">{{ tag.name }}</option></select></label>
        <p v-if="organizationError" class="task-form-error" role="alert">{{ organizationError }}</p>
      </form>
      <template #footer><Button type="submit" form="organization-editor" :disabled="!organizationForm.name.trim()">保存</Button></template>
    </Drawer>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { CalendarClock, CheckCircle2, ChevronRight, Circle, FolderPlus, Inbox, ListTodo, Menu, Pencil, Plus, RotateCcw, Save, Tag, Trash2, Unlink, WifiOff, X } from 'lucide-vue-next'
import Button from '../components/ui/Button.vue'; import Drawer from '../components/ui/Drawer.vue'; import Input from '../components/ui/Input.vue'
import { useAppStore } from '../stores/app.js'; import { useTasksStore } from '../stores/tasks.js'
import { apiCreateTaskReminder, apiDeleteTaskReminder, apiListTaskActivity, apiListTaskFilters, apiListTaskFilterTasks, apiListTaskReminders, apiUpdateTaskFilter, apiUpdateTaskReminder } from '../../packages/api-client/src/index.js'

const route = useRoute(); const store = useTasksStore(); const appStore = useAppStore(); const editorOpen = ref(false); const editing = ref(null); const editorError = ref(''); const sortBy = ref('default'); const navigationOpen = ref(false); const sidebarCollapsed = ref(false); const expandedGroups = ref(new Set()); const activities = ref([]); const activityLoading = ref(false)
const batchMode = ref(false); const selectedIds = ref(new Set()); const batchListId = ref(''); const batchPriority = ref('NONE'); const batchDueLocal = ref(''); const batchTagId = ref(''); const batchReminderOffset = ref(30); const draggedTask = ref(null)
const selectedTask = ref(null); const filters = ref([]); const filteredTasks = ref([]); const draggedOrganization = ref(null)
const compactNavigation = ref(false)
let compactNavigationQuery
const form = reactive({ title: '', description: '', priority: 'NONE', startLocal: '', dueLocal: '', durationMinutes: 0, allDay: false, listId: '', parentId: '', tagIds: [], checklistText: '', recurrence: '', customRrule: '', recurrenceAnchor: 'DUE_DATE', reminderEnabled: false, reminderKind: 'RELATIVE', reminderOffset: 30, remindLocal: '', reminder: null })
const organizationOpen = ref(false)
const organizationItem = ref(null)
const organizationType = ref('list')
const organizationError = ref('')
const organizationForm = reactive({ name: '', color: '#2563eb', icon: '', sortOrder: 0, archived: false, parentId: '', groupId: '' })
const collapsedSections = reactive({ lists: false, tags: false, filters: false })
const builtins = [{ to: '/tasks/all', label: '所有', icon: ListTodo, count: 'all' }, { to: '/tasks/today', label: '今天', icon: CalendarClock, count: 'today' }, { to: '/tasks/tomorrow', label: '明天', icon: CalendarClock, count: 'tomorrow' }, { to: '/tasks/next7', label: '最近 7 天', icon: CalendarClock, count: 'next7' }, { to: '/tasks/inbox', label: '收件箱', icon: Inbox, count: 'inbox' }, { to: '/tasks/timeline', label: '时间线 / 甘特', icon: CalendarClock }, { to: '/tasks/completed', label: '已完成', icon: CheckCircle2 }, { to: '/tasks/trash', label: '垃圾桶', icon: Trash2 }]
const viewKey = computed(() => route.path.split('/')[2] || 'today')
const viewTitle = computed(() => route.name === 'tasks-list' ? listName(route.params.publicId) : route.name === 'tasks-tag' ? `#${tagName(route.params.publicId)}` : route.name === 'tasks-filter' ? filters.value.find(item => item.publicId === route.params.publicId)?.name || '筛选器' : ({ inbox: '收件箱', today: '今天', tomorrow: '明天', next7: '最近 7 天', all: '全部任务', completed: '已完成', trash: '垃圾桶' })[viewKey.value] || '任务')
const visibleTasks = computed(() => {
  const deletedListIds = new Set(store.deletedLists.map(item => item.publicId))
  if (viewKey.value === 'trash') return store.trashedTasks.filter(item => !deletedListIds.has(item.listId))
  let values = route.name === 'tasks-filter' ? [...filteredTasks.value] : store.tasks.filter(item => !item.deleted && !deletedListIds.has(item.listId))
  if (route.name === 'tasks-list') values = values.filter(item => item.listId === route.params.publicId)
  else if (route.name === 'tasks-tag') values = values.filter(item => item.tagIds?.includes(route.params.publicId))
  else if (viewKey.value === 'inbox') values = values.filter(item => item.listId === store.inbox?.publicId)
  else if (viewKey.value === 'today' || viewKey.value === 'tomorrow' || viewKey.value === 'next7') {
    const archivedIds = new Set(store.lists.filter(item => item.archived).map(item => item.publicId))
    const start = viewKey.value === 'tomorrow' ? addDays(startOfToday(), 1) : startOfToday()
    const end = viewKey.value === 'today' ? addDays(startOfToday(), 1) : viewKey.value === 'tomorrow' ? addDays(startOfToday(), 2) : addDays(startOfToday(), 8)
    values = values.filter(item => !archivedIds.has(item.listId) && item.status !== 'COMPLETED' && item.dueAt && new Date(item.dueAt) < end && new Date(item.dueAt) >= start)
  } else if (viewKey.value === 'completed') values = values.filter(item => item.status === 'COMPLETED')
  if (sortBy.value === 'default') values.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
  if (sortBy.value === 'title') values.sort((a, b) => a.title.localeCompare(b.title, 'zh-CN'))
  if (sortBy.value === 'due') values.sort((a, b) => (a.dueAt ? Date.parse(a.dueAt) : Infinity) - (b.dueAt ? Date.parse(b.dueAt) : Infinity))
  if (sortBy.value === 'priority') { const rank = { HIGH: 0, MEDIUM: 1, LOW: 2, NONE: 3 }; values.sort((a, b) => rank[a.priority] - rank[b.priority]) }
  return values
})
const visibleOpen = computed(() => visibleTasks.value.filter(item => item.status !== 'COMPLETED'))
const visibleCompleted = computed(() => visibleTasks.value.filter(item => item.status === 'COMPLETED'))
const selectedTasks = computed(() => visibleTasks.value.filter(item => selectedIds.value.has(item.publicId)))
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
const ungroupedLists = computed(() => store.lists.filter(item => !item.groupId))
const navigationCounts = computed(() => {
  const today = startOfToday(); const tomorrow = addDays(today, 1); const nextDay = addDays(today, 2); const next7 = addDays(today, 8)
  const hiddenListIds = new Set(store.deletedLists.map(item => item.publicId))
  const archivedListIds = new Set(store.lists.filter(item => item.archived).map(item => item.publicId))
  return store.tasks.reduce((counts, task) => {
    if (task.deleted || hiddenListIds.has(task.listId)) return counts
    counts.all += 1
    if (task.status === 'COMPLETED') return counts
    if (task.listId === store.inbox?.publicId) counts.inbox += 1
    if (!task.dueAt || archivedListIds.has(task.listId)) return counts
    const dueAt = new Date(task.dueAt)
    if (dueAt >= today && dueAt < tomorrow) counts.today += 1
    if (dueAt >= tomorrow && dueAt < nextDay) counts.tomorrow += 1
    if (dueAt >= today && dueAt < next7) counts.next7 += 1
    return counts
  }, { all: 0, today: 0, tomorrow: 0, next7: 0, inbox: 0 })
})
function navCount(item) { return item.count ? navigationCounts.value[item.count] : null }
function groupedLists(groupId) { return store.lists.filter(item => item.groupId === groupId) }
function toggleGroup(id) { const next = new Set(expandedGroups.value); next.has(id) ? next.delete(id) : next.add(id); expandedGroups.value = next; localStorage.setItem(`task-groups:${store.accountScope}`, JSON.stringify([...next])) }
function sectionStateKey() { return `task-sidebar-sections:${store.accountScope}` }
function restoreSectionState() { try { Object.assign(collapsedSections, JSON.parse(localStorage.getItem(sectionStateKey()) || '{}')) } catch { Object.assign(collapsedSections, { lists: false, tags: false, filters: false }) } }
function toggleSection(section) { collapsedSections[section] = !collapsedSections[section]; localStorage.setItem(sectionStateKey(), JSON.stringify(collapsedSections)) }
function startOrganizationDrag(type, item) { draggedOrganization.value = { type, item } }
async function dropOrganization(type, target) {
  const source = draggedOrganization.value; draggedOrganization.value = null
  if (!source || source.type !== type || source.item.publicId === target.publicId || source.item.systemKey) return
  try {
    const values = type.startsWith('list:') ? (type === 'list:' ? ungroupedLists.value.filter(item => !item.systemKey) : groupedLists(type.slice(5))) : type === 'tag' ? store.tags : filters.value
    const from = values.findIndex(item => item.publicId === source.item.publicId); const to = values.findIndex(item => item.publicId === target.publicId)
    if (from < 0 || to < 0) return
    const ordered = [...values]; ordered.splice(to, 0, ordered.splice(from, 1)[0])
    if (type === 'filter') {
      await Promise.all(ordered.map((item, index) => item.sortOrder === index ? null : apiUpdateTaskFilter(item.publicId, filterPayload(item, index), item.revision)))
      await loadFilters()
    } else {
      const update = type.startsWith('list:') ? store.updateList : store.updateTag
      for (let index = 0; index < ordered.length; index += 1) if (ordered[index].sortOrder !== index) await update.call(store, ordered[index], { sortOrder: index })
    }
  } catch (error) { store.error = detail(error, '排序保存失败') }
}
function filterPayload(item, sortOrder) { return { name: item.name, listIds: item.listIds || [], tagIds: item.tagIds || [], priorities: item.priorities || [], status: item.status || 'OPEN', dueRange: item.dueRange || 'ANY', keyword: item.keyword || '', includeSubtasks: item.includeSubtasks !== false, matchAllTags: Boolean(item.matchAllTags), pinned: Boolean(item.pinned), sortOrder } }
async function loadFilters() { if (!store.online) { filters.value = []; filteredTasks.value = []; return }; try { filters.value = (await apiListTaskFilters()).sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)); if (route.name === 'tasks-filter') filteredTasks.value = (await apiListTaskFilterTasks(route.params.publicId, { size: 200 }))?.items || [] } catch (error) { store.error = detail(error, '筛选器加载失败') } }
function resetForm(task = null) { const known = ['', 'FREQ=DAILY', 'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR', 'FREQ=WEEKLY', 'FREQ=MONTHLY', 'FREQ=YEARLY']; const recurrence = known.includes(task?.rrule || '') ? task?.rrule || '' : 'CUSTOM'; activities.value = []; Object.assign(form, { title: task?.title || '', description: task?.description || '', priority: task?.priority || 'NONE', startLocal: task?.startAt ? toLocalInput(task.startAt) : '', dueLocal: task?.dueAt ? toLocalInput(task.dueAt) : '', durationMinutes: task?.durationMinutes || 0, allDay: Boolean(task?.allDay), listId: task?.listId || currentListId(), parentId: task?.parentId || '', tagIds: [...(task?.tagIds || [])], checklistText: (task?.checklist || []).map(item => `${item.completed ? '[x] ' : ''}${item.title}`).join('\n'), recurrence, customRrule: recurrence === 'CUSTOM' ? task.rrule : '', recurrenceAnchor: task?.recurrenceAnchor || 'DUE_DATE', reminderEnabled: false, reminderKind: 'RELATIVE', reminderOffset: 30, remindLocal: '', reminder: null }); editorError.value = '' }
function closeNavigation() { navigationOpen.value = false }
function sidebarStateKey() { return `task-sidebar-collapsed:${store.accountScope}` }
function restoreSidebarState() { sidebarCollapsed.value = localStorage.getItem(sidebarStateKey()) === 'true' }
function toggleSidebar() { if (compactNavigation.value) navigationOpen.value = true; else { sidebarCollapsed.value = !sidebarCollapsed.value; localStorage.setItem(sidebarStateKey(), String(sidebarCollapsed.value)) } }
function updateCompactNavigation(event) { compactNavigation.value = event.matches; if (!event.matches) closeNavigation() }
function currentListId() { return route.name === 'tasks-list' ? String(route.params.publicId) : store.inbox?.publicId || '' }
function openCreate() { editing.value = null; resetForm(); editorOpen.value = true } async function openEdit(task) { editing.value = task; resetForm(task); editorOpen.value = true; if (store.online) { try { const reminders = await apiListTaskReminders(task.publicId); const reminder = reminders?.[0]; if (reminder) Object.assign(form, { reminderEnabled: true, reminderKind: reminder.kind, reminderOffset: reminder.offsetMinutes ?? 30, remindLocal: reminder.remindAt ? toLocalInput(reminder.remindAt) : '', reminder }) } catch (error) { editorError.value = detail(error, '提醒加载失败') } } }
async function selectTaskDetail(task) { selectedTask.value = task; activities.value = []; await loadActivity(task) }
function payload() { const rrule = form.recurrence === 'CUSTOM' ? form.customRrule.trim() : form.recurrence; return { listId: form.listId, parentId: form.parentId || '', tagIds: form.tagIds, checklist: form.checklistText.split('\n').map(line => line.trim()).filter(Boolean).map((line, index) => ({ publicId: editing.value?.checklist?.[index]?.publicId, title: line.replace(/^\[x\]\s*/i, ''), completed: /^\[x\]/i.test(line), sortOrder: index })), title: form.title.trim(), description: form.description.trim(), priority: form.priority, startAt: form.startLocal ? new Date(form.startLocal).toISOString() : '', dueAt: form.dueLocal ? new Date(form.dueLocal).toISOString() : '', durationMinutes: Number(form.durationMinutes) || 0, allDay: form.allDay, timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai', rrule, recurrenceAnchor: rrule ? form.recurrenceAnchor : '' } }
async function save() { editorError.value = ''; try { const saved = editing.value ? await store.update(editing.value, payload()) : await store.create(payload()); if (form.reminderEnabled || form.reminder) { if (!store.online) throw new Error('提醒需要联网保存'); await store.syncNow(); const serverTask = store.tasks.find(item => item.publicId === saved.publicId) || saved; const reminder = { kind: form.reminderKind, offsetMinutes: form.reminderKind === 'RELATIVE' ? form.reminderOffset : null, remindAt: form.reminderKind === 'ABSOLUTE' && form.remindLocal ? new Date(form.remindLocal).toISOString() : null, channel: 'IN_APP', dailyUntilDone: false }; if (form.reminderEnabled) form.reminder ? await apiUpdateTaskReminder(serverTask.publicId, form.reminder.publicId, reminder, form.reminder.revision) : await apiCreateTaskReminder(serverTask.publicId, reminder); else await apiDeleteTaskReminder(serverTask.publicId, form.reminder.publicId, form.reminder.revision) } selectedTask.value = store.tasks.find(item => item.publicId === saved.publicId) || saved; editorOpen.value = false } catch (error) { editorError.value = detail(error, '任务保存失败') } } async function complete(task) { try { await store.complete(task); selectedTask.value = store.tasks.find(item => item.publicId === task.publicId) || null } catch (error) { store.error = detail(error, '完成任务失败') } } async function reopen(task) { try { await store.reopen(task); selectedTask.value = store.tasks.find(item => item.publicId === task.publicId) || null } catch (error) { store.error = detail(error, '重新打开失败') } } async function remove(task) { if (!window.confirm(`删除任务“${task.title}”？`)) return; try { await store.remove(task); if (editing.value?.publicId === task.publicId) editorOpen.value = false; if (selectedTask.value?.publicId === task.publicId) selectedTask.value = null } catch (error) { editorError.value = detail(error, '删除任务失败') } } async function restore(task) { try { await store.restore(task) } catch (error) { store.error = detail(error, '恢复任务失败') } }
async function purge(task) { if (window.prompt(`永久清除后无法恢复。请输入 DELETE:${task.publicId}`) !== `DELETE:${task.publicId}`) return; try { await store.purge(task) } catch (error) { store.error = detail(error, '永久清除失败') } }
async function restoreList(list) { try { await store.restoreList(list) } catch (error) { store.error = detail(error, '恢复清单失败') } }
function toggleBatch() { batchMode.value = !batchMode.value; if (!batchMode.value) selectedIds.value = new Set() }
function selectTask(task) { const next = new Set(selectedIds.value); next.has(task.publicId) ? next.delete(task.publicId) : next.add(task.publicId); selectedIds.value = next }
async function runBatch(action, values = {}) { if (action === 'DELETE' && !window.confirm(`删除选中的 ${selectedTasks.value.length} 项任务？`)) return; try { await store.batchTasks(selectedTasks.value, action, values); selectedIds.value = new Set(); batchMode.value = false } catch (error) { store.error = detail(error, '批量操作失败') } }
function canDrag(task) { return !batchMode.value && sortBy.value === 'default' && route.name === 'tasks-list' && !task.parentId }
function startDrag(task) { if (canDrag(task)) draggedTask.value = task }
async function dropTask(target, index) { const task = draggedTask.value; draggedTask.value = null; if (!task || task.publicId === target.publicId || !canDrag(task)) return; try { await store.sortTask(task, index) } catch (error) { store.error = detail(error, '任务排序失败') } }
async function moveTop(task) { try { await store.sortTask(task, 0) } catch (error) { store.error = detail(error, '任务置顶失败') } }
function editOrganization(type, item = null) {
  closeNavigation()
  organizationType.value = type
  organizationItem.value = item
  organizationError.value = ''
  Object.assign(organizationForm, { name: item?.name || '', color: item?.color || '#2563eb', icon: item?.icon || '',
    sortOrder: item?.sortOrder ?? (type === 'list' ? store.lists.length : store.tags.length),
    archived: Boolean(item?.archived), parentId: item?.parentId || '', groupId: item?.groupId || '' })
  organizationOpen.value = true
}
function newList() { editOrganization('list') }
function newGroup() { editOrganization('group') }
function newTag() { editOrganization('tag') }
function editList(item) { editOrganization('list', item) }
function editGroup(item) { editOrganization('group', item) }
function editTag(item) { editOrganization('tag', item) }
async function saveOrganization() {
  try {
    const value = { ...organizationForm, name: organizationForm.name.trim(), sortOrder: Number(organizationForm.sortOrder) }
    if (organizationType.value === 'group') {
      if (organizationItem.value) await store.updateListGroup(organizationItem.value, value)
      else await store.createListGroup(value)
    } else if (organizationType.value === 'list') {
      if (organizationItem.value) {
        await store.updateList(organizationItem.value, value)
        if ((organizationItem.value.groupId || '') !== value.groupId) await store.assignListGroup(organizationItem.value, value.groupId)
      } else await store.createList(value)
    } else {
      if (organizationItem.value) await store.updateTag(organizationItem.value, value)
      else await store.createTag(value)
    }
    organizationOpen.value = false
  } catch (error) { organizationError.value = detail(error, '保存失败') }
}
async function assignGroup(list, groupId) { try { await store.assignListGroup(list, groupId) } catch (error) { store.error = detail(error, '清单分组保存失败') } }
async function deleteGroup(group) { if (!window.confirm(`删除分组“${group.name}”？组内清单将变为未分组。`)) return; try { await store.deleteListGroup(group) } catch (error) { store.error = detail(error, '删除清单分组失败') } }
async function loadActivity(task) { if (!store.online) return; activityLoading.value = true; try { activities.value = (await apiListTaskActivity(task.publicId, { size: 20 }))?.items || [] } catch (error) { editorError.value = detail(error, '活动记录加载失败') } finally { activityLoading.value = false } }
async function resolveConflict(strategy) { await store.resolveConflict(store.conflicts[0].id, strategy) }
async function deleteList(list) { if (!window.confirm(`删除清单“${list.name}”并将其任务移入回收站？`)) return; try { await store.deleteList(list) } catch (error) { store.error = detail(error, '删除清单失败') } }
async function deleteTag(tag) { if (!window.confirm(`删除标签“${tag.name}”？任务本身不会删除。`)) return; try { await store.deleteTag(tag) } catch (error) { store.error = detail(error, '删除标签失败') } }
function listName(id) { return store.lists.find(item => item.publicId === id)?.name || '收件箱' } function tagName(id) { return store.tags.find(item => item.publicId === id)?.name || '标签' } function priorityLabel(value) { return ({ LOW: '低', MEDIUM: '中', HIGH: '高' })[value] || '' } function formatTime(value) { return value ? new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) : '' } function toLocalInput(value) { const date = new Date(value); return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16) } function detail(error, fallback) { return error?.response?.data?.detail || error?.message || fallback } function issueTitle(issue) { return issue?.operation?.payload?.title || issue?.operation?.entityId || '未知任务' } function startOfToday() { const value = new Date(); value.setHours(0, 0, 0, 0); return value } function addDays(value, days) { const result = new Date(value); result.setDate(result.getDate() + days); return result }
function onEscape(event) { if (event.key === 'Escape') closeNavigation() }
watch(navigationOpen, open => document.body.classList.toggle('task-navigation-open', open))
watch(() => route.fullPath, () => { closeNavigation(); selectedTask.value = null; loadFilters() })
watch(() => store.accountScope, scope => { try { expandedGroups.value = new Set(JSON.parse(localStorage.getItem(`task-groups:${scope}`) || '[]')) } catch { expandedGroups.value = new Set() }; restoreSectionState(); restoreSidebarState(); loadFilters() }, { immediate: true })
watch(() => store.tasks, () => { if (selectedTask.value) selectedTask.value = store.tasks.find(item => item.publicId === selectedTask.value.publicId) || null })
watch(editorOpen, open => { if (open && editing.value) loadActivity(editing.value) })
function onNewRequest() { openCreate() }
onMounted(() => {
  window.addEventListener('task-new-request', onNewRequest)
  store.init().then(loadFilters).catch(() => {})
  compactNavigationQuery = window.matchMedia('(max-width: 1024px)')
  updateCompactNavigation(compactNavigationQuery)
  if (compactNavigationQuery.addEventListener) compactNavigationQuery.addEventListener('change', updateCompactNavigation)
  else compactNavigationQuery.addListener?.(updateCompactNavigation)
  window.addEventListener('keydown', onEscape)
})
onBeforeUnmount(() => {
  window.removeEventListener('task-new-request', onNewRequest)
  document.body.classList.remove('task-navigation-open')
  if (compactNavigationQuery?.removeEventListener) compactNavigationQuery.removeEventListener('change', updateCompactNavigation)
  else compactNavigationQuery?.removeListener?.(updateCompactNavigation)
  window.removeEventListener('keydown', onEscape)
})
</script>

<style scoped>
.task-workspace {
  display:grid;
  grid-template-columns:240px minmax(370px,1fr) minmax(320px,36%);
  min-height:calc(100vh - 72px);
  margin:-36px -40px -104px;
  background:var(--card)}
.task-sidebar {
  border-right:1px solid var(--line);
  padding:24px 14px 40px;
  overflow-y:auto;
  background:color-mix(in srgb,var(--card) 96%,var(--paper))}
.task-sidebar-head,
.task-navigation-backdrop {
  display:none}
.task-workspace.sidebar-collapsed{grid-template-columns:48px minmax(370px,1fr) minmax(320px,36%)}
.task-workspace.sidebar-collapsed .task-sidebar{padding-left:4px;padding-right:4px;overflow:hidden}
.task-workspace.sidebar-collapsed .task-sidebar>*{visibility:hidden}
.task-sidebar nav {
  display:grid;
  gap:3px}
.task-sidebar a {
  display:flex;
  align-items:center;
  gap:9px;
  min-height:40px;
  padding:9px 12px;
  color:var(--ink2);
  text-decoration:none;
  border-radius:10px;
  font-size:0.8125rem}
.task-sidebar a.router-link-active {
  background:color-mix(in srgb,var(--ink) 7%,transparent);
  color:var(--ink);
  font-weight:700}
.task-sidebar-primary{padding-bottom:18px;border-bottom:1px solid var(--line)}
.task-sidebar-primary a>span{min-width:0;flex:1}
.side-count{margin-left:auto;color:var(--muted);font-size:.75rem;font-weight:500}
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
.side-section.collapsed .side-heading{margin-bottom:0}
.side-section-toggle{display:flex!important;align-items:center;gap:4px;padding:4px 0!important;font:inherit}
.side-section-toggle svg{transition:transform .15s}.side-section-toggle svg.expanded{transform:rotate(90deg)}
.side-add-link{min-height:0!important;padding:4px!important;font-size:.6875rem!important;color:var(--muted)!important}
.side-empty{display:block;padding:8px 12px;color:var(--muted);font-size:.75rem}
.side-heading>span:last-child,.group-row,.group-heading{display:flex;align-items:center}.group-row{min-width:0}.group-heading{min-width:0;flex:1;gap:6px;border:0;background:transparent;color:var(--muted);padding:7px 8px;text-align:left}.group-heading>span{margin-left:auto}.group-heading svg{transition:transform .15s}.group-heading svg.expanded{transform:rotate(90deg)}.side-item.nested{padding-left:12px}.task-activity{margin-top:18px;padding-top:14px;border-top:1px solid var(--line)}.task-activity h3{margin:0 0 10px;font-size:.85rem}.task-activity ol{display:grid;gap:9px;margin:0;padding:0;list-style:none}.task-activity li{display:grid;grid-template-columns:1fr auto;gap:10px;color:var(--ink2);font-size:.75rem}.task-activity time{color:var(--muted);font-weight:400}
.tasks-page {
  min-width:0;
  padding:26px 26px 120px;
  border-right:1px solid var(--line);
  background:var(--card)}
.task-toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:16px}.task-select,.task-move-top{border:0;background:transparent;color:var(--muted);padding:6px}.task-row[draggable="true"]{cursor:grab}.task-row.selected{background:var(--accent-soft)}.task-batch-bar{position:fixed;z-index:40;left:max(16px,env(safe-area-inset-left));right:max(16px,env(safe-area-inset-right));bottom:calc(84px + env(safe-area-inset-bottom));margin:auto;max-width:52rem;display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap;padding:12px;background:var(--surface);border:1px solid var(--line);box-shadow:0 10px 30px rgba(0,0,0,.14)}.task-batch-bar select{min-height:36px}.task-batch-bar>button:last-child{border:0;background:transparent;color:var(--muted);padding:8px}
.tasks-head {
  display:flex;
  justify-content:space-between;
  align-items:center;
  gap:24px;
  min-height:54px;
  padding-bottom:14px}
.tasks-title,.tasks-head-actions{display:flex;align-items:center;gap:10px}
.task-collapse-button,.task-head-icon{display:grid;place-items:center;width:34px;height:34px;padding:0;border:0;border-radius:8px;background:transparent;color:var(--ink2)}
.task-head-icon:hover{background:var(--paper);color:var(--ink)}
.tasks-head-actions .ui-button{min-height:34px}
.tasks-head h1 {
  margin:0;
  font:700 1.65rem ui-sans-serif,system-ui,sans-serif;
  letter-spacing:-.035em}
.tasks-head .label{display:none}
.tasks-summary {
  margin:3px 0 0;
  color:var(--muted);
  font-size:.75rem}
.task-quick-add{display:flex;align-items:center;gap:8px;margin:6px 0 0;padding:0 13px;height:48px;border-radius:10px;background:color-mix(in srgb,var(--paper) 82%,var(--card));color:var(--muted)}
.task-quick-add button{min-width:0;flex:1;border:0;background:transparent;color:inherit;text-align:left}
.task-quick-add kbd{padding:2px 6px;border:1px solid var(--line);border-radius:5px;background:var(--card);font-size:.65rem;font-family:inherit}
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
  gap:24px;
  margin-top:24px}
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
  min-height:54px;
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
.task-content strong{font-size:.875rem;font-weight:560}
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
.task-detail-pane{position:relative;min-width:0;background:color-mix(in srgb,var(--card) 98%,var(--paper));overflow:hidden}
.task-detail-empty{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:40px;text-align:center;color:var(--muted)}
.task-detail-empty strong{margin-top:14px;color:var(--ink2);font-size:.875rem}
.task-detail-empty p{max-width:220px;margin:7px 0 0;font-size:.75rem;line-height:1.6}
.task-detail-spark{display:grid;width:54px;height:54px;place-items:center;border:1px solid var(--line);border-radius:50%;background:var(--card);color:var(--accent);font-size:1.4rem;box-shadow:0 12px 32px rgba(15,23,42,.06)}
.task-detail{height:100%;overflow-y:auto;color:var(--ink)}
.task-detail>header{position:sticky;z-index:2;top:0;display:flex;align-items:center;justify-content:space-between;min-height:56px;padding:0 22px;border-bottom:1px solid var(--line);background:color-mix(in srgb,var(--card) 96%,transparent);backdrop-filter:blur(12px);color:var(--muted);font-size:.75rem}
.task-detail>header div{display:flex;gap:4px}.task-detail>header button{display:grid;width:34px;height:34px;place-items:center;padding:0;border:0;border-radius:8px;background:transparent;color:var(--muted)}.task-detail>header button:hover{background:var(--paper);color:var(--ink)}
.task-detail-title{display:flex;align-items:flex-start;gap:8px;padding:26px 24px 20px;border-bottom:1px solid var(--line)}.task-detail-title>div{min-width:0}.task-detail-title h2{margin:2px 0 10px;font-size:1.25rem;line-height:1.35;overflow-wrap:anywhere}.task-detail-title p{margin:0;color:var(--ink2);font-size:.82rem;line-height:1.7;white-space:pre-wrap}.task-detail-title .detail-placeholder{color:var(--muted)}
.task-detail dl{margin:0;padding:10px 24px;border-bottom:1px solid var(--line)}.task-detail dl>div{display:grid;grid-template-columns:70px minmax(0,1fr);gap:10px;padding:9px 0;font-size:.78rem}.task-detail dt{color:var(--muted)}.task-detail dd{margin:0;overflow-wrap:anywhere}
.detail-checklist,.detail-activity{padding:18px 24px;border-bottom:1px solid var(--line)}.detail-checklist h3,.detail-activity h3{margin:0 0 12px;font-size:.82rem}.detail-checklist h3 span{color:var(--muted);font-weight:500}.detail-checklist p{display:flex;align-items:center;gap:8px;margin:0;padding:9px 0;border-bottom:1px solid var(--line);font-size:.78rem}.detail-checklist p.done{color:var(--muted);text-decoration:line-through}.detail-activity{margin:0}.task-detail>footer{display:flex;justify-content:flex-end;padding:18px 24px}
@media(max-width:1279px){.task-workspace{grid-template-columns:230px minmax(370px,1fr)}.task-detail-pane{display:none}.task-detail-pane.active{position:fixed;z-index:45;top:72px;right:0;bottom:0;display:block;width:min(420px,42vw);border-left:1px solid var(--line);box-shadow:-18px 0 42px rgba(15,23,42,.12)}.tasks-page{border-right:0}.task-workspace.sidebar-collapsed{grid-template-columns:48px minmax(370px,1fr)}}
@media(max-width:1024px) {
  .task-workspace {
  display:block;
  min-height:0;
  margin:-36px -40px -104px;
  padding:20px 16px 96px}
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
.task-collapse-button{display:grid}
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
.side-item[draggable="true"]{cursor:grab}
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

@media(max-width:767px){.task-workspace{margin:-36px -24px -104px}.tasks-page{padding:18px 16px 110px}.task-navigation-trigger{display:none}.tasks-head{align-items:flex-start}.tasks-head-actions{gap:2px}.tasks-head-actions .ui-button{font-size:0;width:36px;padding:0}.tasks-head-actions .ui-button svg{margin:0}.tasks-summary{max-width:150px}.task-toolbar{margin-top:10px}.task-row{min-height:58px}}
@media(max-width:1024px){.task-workspace.sidebar-collapsed{display:block}.task-detail-pane.active{top:0;z-index:1002;width:min(92vw,440px);max-width:none}}

.task-navigation-fade-enter-active,
.task-navigation-fade-leave-active {
  transition:opacity .2s ease}
.task-navigation-fade-enter-from,
.task-navigation-fade-leave-to {
  opacity:0}

.task-core-time-fields{position:fixed;z-index:1200;right:28px;bottom:82px;width:min(720px,calc(100vw - 56px));display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;padding:10px;border:1px solid var(--border);border-radius:10px;background:var(--surface);box-shadow:0 12px 30px rgba(15,23,42,.16)}.task-core-time-fields label{display:grid;gap:4px;font-size:12px;color:var(--muted)}.task-core-time-fields .task-inline-check{display:flex;align-items:center;gap:5px;align-self:end}@media(max-width:700px){.task-core-time-fields{right:12px;bottom:76px;width:calc(100vw - 24px);grid-template-columns:1fr 1fr}}

</style>
