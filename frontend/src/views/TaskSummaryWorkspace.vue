<template>
  <section ref="workspace" class="summary-workspace" aria-label="任务摘要">
    <header class="summary-head">
      <div><h1>摘要</h1><span>{{ filteredItems.length }} 项记录</span></div>
      <div class="summary-filters">
        <details class="summary-popover"><summary @click="closeOtherPopovers"><CalendarDays :size="15" />{{ dateLabel }}<ChevronDown :size="13" /></summary><div class="summary-menu">
          <button v-for="option in dateOptions" :key="option.value" type="button" :class="{ active: datePreset === option.value }" @click="chooseDatePreset(option.value, $event)">{{ option.label }}<Check v-if="datePreset === option.value" :size="14" /></button>
        </div></details>
        <div v-if="customOpen" class="summary-range" aria-label="自定义时间范围">
          <span>从</span><input v-model="customDraft.from" type="date" :max="customDraft.to || undefined" aria-label="开始日期" />
          <span>到</span><input v-model="customDraft.to" type="date" :min="customDraft.from || undefined" aria-label="结束日期" />
          <button type="button" class="summary-range-apply" :disabled="!customDraft.from || !customDraft.to" @click="applyCustomRange">确定</button>
          <button type="button" class="summary-range-cancel" @click="cancelCustomRange">取消</button>
        </div>
        <details class="summary-popover" data-pop="list" @toggle="onPopoverToggle"><summary @click="closeOtherPopovers"><ListTodo :size="15" />{{ listSummary }}<ChevronDown :size="13" /></summary><div class="summary-menu">
          <button v-for="option in listModes" :key="option.value" type="button" :class="{ active: listMode === option.value }" @click="chooseListMode(option.value)">{{ option.label }}<Check v-if="listMode === option.value" :size="14" /></button>
          <hr /><label v-for="list in availableLists" :key="list.publicId" class="summary-check-option"><input type="checkbox" :checked="draftListIds.includes(list.publicId)" @change="toggleDraftList(list.publicId, $event.target.checked)" />{{ list.name }}</label>
          <div class="summary-menu-actions"><button type="button" class="summary-apply" @click="applyListDraft">确定</button><button type="button" class="summary-dismiss" @click="dismissPopover">取消</button></div>
        </div></details>
        <details class="summary-popover" data-pop="tag" @toggle="onPopoverToggle"><summary @click="closeOtherPopovers"><Tag :size="15" />{{ tagSummary }}<ChevronDown :size="13" /></summary><div class="summary-menu">
          <button v-for="option in tagModes" :key="option.value" type="button" :class="{ active: tagMode === option.value }" @click="chooseTagMode(option.value)">{{ option.label }}<Check v-if="tagMode === option.value" :size="14" /></button>
          <hr /><label v-for="tag in store.tags" :key="tag.publicId" class="summary-check-option"><input type="checkbox" :checked="draftTagIds.includes(tag.publicId)" @change="toggleDraftTag(tag.publicId, $event.target.checked)" />#{{ tag.name }}</label>
          <div class="summary-menu-actions"><button type="button" class="summary-apply" @click="applyTagDraft">确定</button><button type="button" class="summary-dismiss" @click="dismissPopover">取消</button></div>
        </div></details>
        <details class="summary-popover"><summary @click="closeOtherPopovers"><CircleCheck :size="15" />{{ statusLabel }}<ChevronDown :size="13" /></summary><div class="summary-menu">
          <button v-for="option in statusOptions" :key="option.value" type="button" :class="{ active: status === option.value }" @click="chooseStatus(option.value, $event)">{{ option.label }}<Check v-if="status === option.value" :size="14" /></button>
        </div></details>
        <details class="summary-popover" data-pop="priority" @toggle="onPopoverToggle"><summary @click="closeOtherPopovers"><SlidersHorizontal :size="15" />更多<ChevronDown :size="13" /></summary><div class="summary-menu summary-more-menu">
          <strong>优先级</strong><label v-for="option in priorityOptions" :key="option.value" class="summary-check-option"><input v-model="draftPriorities" type="checkbox" :value="option.value" />{{ option.label }}</label>
          <hr /><strong>分组方式</strong><button v-for="option in SUMMARY_GROUPS" :key="option.key" type="button" :class="{ active: groupBy === option.key }" @click="groupBy = option.key">{{ option.label }}<Check v-if="groupBy === option.key" :size="14" /></button>
          <div class="summary-menu-actions"><button type="button" class="summary-apply" @click="applyPriorityDraft">确定</button><button type="button" class="summary-dismiss" @click="dismissPopover">取消</button></div>
        </div></details>
        <details class="summary-popover"><summary @click="closeOtherPopovers"><ListFilter :size="15" />显示内容<ChevronDown :size="13" /></summary><div class="summary-menu summary-fields-menu"><strong>显示内容</strong><label v-for="field in SUMMARY_FIELDS" :key="field.key" class="summary-check-option"><input v-model="fields" type="checkbox" :value="field.key" />{{ field.label }}</label></div></details>
      </div>
    </header>
    <div class="summary-report-area">
      <div class="summary-report-title"><h2>{{ reportTitle }}</h2><span>{{ summaryRangeLabel(dateRange) }}</span></div>
      <div class="summary-report markdown-body" aria-live="polite" v-html="renderedReport"></div>
      <section class="summary-notes" aria-label="摘要补充内容">
        <h2>补充内容</h2>
        <TaskRichTextEditor v-model="notes" label="摘要补充内容" placeholder="添加摘要说明，支持 Markdown 与 / 快捷菜单" />
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { CalendarDays, Check, ChevronDown, CircleCheck, ListFilter, ListTodo, SlidersHorizontal, Tag } from 'lucide-vue-next'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import TaskRichTextEditor from '../components/tasks/TaskRichTextEditor.vue'
import { useTasksStore } from '../stores/tasks.js'
import { SUMMARY_FIELDS, SUMMARY_GROUPS, buildSummaryMarkdown, filterSummaryItems, groupSummaryItems, summaryDateRange, summaryRangeLabel } from './taskSummary.js'

const store = useTasksStore()
const workspace = ref(null)
const datePreset = ref('THIS_WEEK')
const customRange = reactive({ from: '', to: '' })
const customDraft = reactive({ from: '', to: '' })
const customOpen = ref(false)
let presetBeforeCustom = 'THIS_WEEK'
const listMode = ref('ALL')
const listIds = ref([])
const draftListIds = ref([])
const tagMode = ref('ALL')
const tagIds = ref([])
const draftTagIds = ref([])
const status = ref('ALL')
const priorities = ref([])
const draftPriorities = ref([])
const groupBy = ref('status')
const fields = ref(['status', 'progress', 'completedAt', 'taskTime', 'title'])
const notesKey = computed(() => `task-summary-notes:${store.accountScope || 'guest'}`)
const notes = ref('')
const listModes = [
  { value: 'ALL', label: '所有' },
  { value: 'LISTS', label: '所有清单' },
  { value: 'SELECTED', label: '各个清单' },
  { value: 'CHECKINS', label: '打卡' },
]
const dateOptions = [
  { value: 'TODAY', label: '今天' }, { value: 'TOMORROW', label: '明天' }, { value: 'YESTERDAY', label: '昨天' },
  { value: 'THIS_WEEK', label: '本周' }, { value: 'NEXT_WEEK', label: '下周' }, { value: 'LAST_WEEK', label: '上周' },
  { value: 'THIS_MONTH', label: '本月' }, { value: 'LAST_MONTH', label: '上月' }, { value: 'CUSTOM', label: '自定义' },
]
const statusOptions = [
  { value: 'ALL', label: '所有状态' }, { value: 'COMPLETED', label: '已完成' }, { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'INCOMPLETE', label: '未完成' }, { value: 'ABANDONED', label: '已放弃' },
]
const tagModes = [
  { value: 'ALL', label: '所有' },
  { value: 'HAS', label: '有标签' },
  { value: 'NONE', label: '无标签' },
  { value: 'SELECTED', label: '各标签' },
]
const priorityOptions = [
  { value: 'HIGH', label: '高' }, { value: 'MEDIUM', label: '中' }, { value: 'LOW', label: '低' }, { value: 'NONE', label: '无' },
]
const availableLists = computed(() => store.lists.filter(list => !list.archived && list.objectType !== 'NOTE'))
const dateRange = computed(() => summaryDateRange(datePreset.value, new Date(), customRange))
const dateLabel = computed(() => {
  if (datePreset.value === 'CUSTOM') {
    return customRange.from && customRange.to ? `${customRange.from} 至 ${customRange.to}` : '自定义'
  }
  return `${dateOptions.find(option => option.value === datePreset.value)?.label || '本周'} (${summaryRangeLabel(dateRange.value)})`
})
const statusLabel = computed(() => statusOptions.find(option => option.value === status.value)?.label || '所有状态')
const listSummary = computed(() => listMode.value === 'SELECTED' ? `清单 ${listIds.value.length || '选择'}` : listModes.find(option => option.value === listMode.value)?.label)
const tagSummary = computed(() => tagMode.value === 'SELECTED' ? `标签 ${tagIds.value.length || '选择'}` : tagModes.find(option => option.value === tagMode.value)?.label)
const rawItems = computed(() => {
  const lists = new Map(store.lists.map(list => [list.publicId, list]))
  const tags = new Map(store.tags.map(tag => [tag.publicId, tag]))
  const tasks = store.tasks.filter(item => !item.deleted).map(item => ({
    ...item,
    kind: 'task',
    listName: lists.get(item.listId)?.name || '',
    tagNames: (item.tagIds || []).map(id => tags.get(id)?.name).filter(Boolean),
    parentTitle: store.tasks.find(parent => parent.publicId === item.parentId)?.title || '',
  }))
  const habits = new Map(store.habits.map(habit => [habit.publicId, habit]))
  const checkins = store.habitCheckins.filter(item => !item.deleted).map(item => ({
    ...item,
    kind: 'checkin',
    title: habits.get(item.habitId)?.name || '习惯打卡',
    status: ['DONE', 'COMPLETED'].includes(item.status) ? 'DONE' : 'OPEN',
    date: item.date,
    dueAt: item.date,
    focusMinutes: 0,
    description: '',
    tagIds: [],
    tagNames: [],
    listName: '打卡',
    completedAt: ['DONE', 'COMPLETED'].includes(item.status) ? item.date : null,
  }))
  return [...tasks, ...checkins]
})
const filteredItems = computed(() => datePreset.value === 'CUSTOM' && (!customRange.from || !customRange.to)
  ? []
  : filterSummaryItems(rawItems.value, {
  range: dateRange.value,
  listMode: listMode.value,
  listIds: listIds.value,
  tagMode: tagMode.value,
  tagIds: tagIds.value,
  status: status.value,
  priorities: priorities.value,
}))
const groupNames = computed(() => Object.fromEntries([
  ...store.lists.map(list => [list.publicId, list.name]),
  ...store.tags.map(tag => [tag.publicId, `#${tag.name}`]),
]))
const groups = computed(() => groupSummaryItems(filteredItems.value, groupBy.value, groupNames.value))
const reportMarkdown = computed(() => buildSummaryMarkdown(groups.value, fields.value, { status: { OPEN: '未完成', COMPLETED: '已完成', DONE: '已完成', ABANDONED: '已放弃' } }))
const renderedReport = computed(() => DOMPurify.sanitize(marked.parse(reportMarkdown.value, { async: false }), { ALLOWED_URI_REGEXP: /^(?:https?:|mailto:)/i }))
const reportTitle = computed(() => datePreset.value === 'CUSTOM'
  ? `任务摘要 · ${customRange.from || '开始日期'} 至 ${customRange.to || '结束日期'}`
  : `任务摘要 · ${summaryRangeLabel(dateRange.value)}`)
watch(notesKey, key => { notes.value = localStorage.getItem(key) || '' }, { immediate: true })
watch(notes, value => localStorage.setItem(notesKey.value, value))
function chooseListMode(value) { listMode.value = value; if (value !== 'SELECTED') listIds.value = [] }
function chooseDatePreset(value, event) {
  if (value === 'CUSTOM') {
    presetBeforeCustom = datePreset.value === 'CUSTOM' ? presetBeforeCustom : datePreset.value
    customDraft.from = customRange.from
    customDraft.to = customRange.to
    customOpen.value = true
  } else {
    customOpen.value = false
    datePreset.value = value
  }
  event.currentTarget.closest('details').open = false
}
function applyCustomRange() {
  if (!customDraft.from || !customDraft.to) return
  customRange.from = customDraft.from
  customRange.to = customDraft.to
  datePreset.value = 'CUSTOM'
  customOpen.value = false
}
function cancelCustomRange() {
  customOpen.value = false
  if (datePreset.value === 'CUSTOM' && (!customRange.from || !customRange.to)) datePreset.value = presetBeforeCustom
}
function chooseStatus(value, event) { status.value = value; event.currentTarget.closest('details').open = false }
function onPopoverToggle(event) {
  if (!event.target.open) return
  const kind = event.target.dataset.pop
  if (kind === 'list') draftListIds.value = [...listIds.value]
  else if (kind === 'tag') draftTagIds.value = [...tagIds.value]
  else if (kind === 'priority') draftPriorities.value = [...priorities.value]
}
function toggleDraftList(id, checked) {
  draftListIds.value = checked ? [...new Set([...draftListIds.value, id])] : draftListIds.value.filter(value => value !== id)
}
function toggleDraftTag(id, checked) {
  draftTagIds.value = checked ? [...new Set([...draftTagIds.value, id])] : draftTagIds.value.filter(value => value !== id)
}
function applyListDraft() {
  listIds.value = draftListIds.value
  if (draftListIds.value.length) listMode.value = 'SELECTED'
  else if (listMode.value === 'SELECTED') listMode.value = 'ALL'
  dismissPopover()
}
function applyTagDraft() {
  tagIds.value = draftTagIds.value
  if (draftTagIds.value.length) tagMode.value = 'SELECTED'
  else if (tagMode.value === 'SELECTED') tagMode.value = 'ALL'
  dismissPopover()
}
function applyPriorityDraft() {
  priorities.value = draftPriorities.value
  dismissPopover()
}
function dismissPopover(event) {
  const target = event?.target instanceof Element ? event.target : null
  if (target) target.closest('details').open = false
  else workspace.value?.querySelectorAll('.summary-popover[open]').forEach(details => { details.open = false })
}
function closePopovers(event) {
  if (event.target.closest?.('.summary-popover')) return
  workspace.value?.querySelectorAll('details[open]').forEach(details => { details.open = false })
}
function closeOtherPopovers(event) {
  const current = event.currentTarget?.parentElement
  workspace.value?.querySelectorAll('details[open]').forEach(details => { if (details !== current) details.open = false })
}
onMounted(() => document.addEventListener('pointerdown', closePopovers))
onBeforeUnmount(() => document.removeEventListener('pointerdown', closePopovers))
</script>

<style scoped>
.summary-workspace{display:flex;min-width:0;height:100%;flex-direction:column;overflow:hidden;background:var(--card);color:var(--ink)}
.summary-head{display:grid;gap:13px;padding:23px 30px 14px;border-bottom:1px solid var(--line)}
.summary-head>div:first-child{display:flex;align-items:baseline;gap:11px}.summary-head h1{margin:0;font-size:22px;font-weight:650}.summary-head>div:first-child>span,.summary-report-title>span{color:var(--muted);font-size:12px}
.summary-filters{display:flex;flex-wrap:wrap;align-items:center;gap:8px}
.summary-filter,.summary-popover>summary,.summary-range{display:inline-flex;min-height:34px;align-items:center;gap:7px;padding:0 10px;border:1px solid var(--line);border-radius:6px;background:var(--paper);color:var(--ink2);font-size:12px}
.summary-filter select,.summary-select-option select{max-width:180px;border:0;outline:0;background:transparent;color:inherit;font:inherit;cursor:pointer}.summary-range{gap:6px;padding:3px 8px}.summary-range input{width:122px;border:0;background:transparent;color:var(--ink);font:inherit}.summary-range span{color:var(--muted)}
.summary-popover{position:relative}.summary-popover>summary{list-style:none;cursor:pointer}.summary-popover>summary::-webkit-details-marker{display:none}.summary-popover[open]>summary{border-color:var(--accent)}
.summary-menu{position:absolute;z-index:20;top:calc(100% + 5px);left:0;display:grid;min-width:190px;max-height:min(55vh,420px);gap:2px;overflow:auto;padding:6px;border:1px solid var(--line);border-radius:7px;background:var(--card);box-shadow:0 10px 28px rgb(15 23 42 / 16%)}
.summary-menu button{display:flex;min-height:34px;align-items:center;justify-content:space-between;gap:12px;padding:6px 8px;border:0;border-radius:4px;background:transparent;color:var(--ink2);font:inherit;font-size:12px;text-align:left;cursor:pointer}.summary-menu button:hover,.summary-menu button.active{background:var(--accent-soft);color:var(--accent)}.summary-menu strong{padding:5px 8px;color:var(--muted);font-size:11px;font-weight:550}.summary-menu hr{width:100%;margin:4px 0;border:0;border-top:1px solid var(--line)}
.summary-check-option{display:flex;min-height:30px;align-items:center;gap:8px;padding:4px 8px;color:var(--ink2);font-size:12px;cursor:pointer}.summary-check-option input{accent-color:var(--accent)}.summary-select-option{padding:4px 8px}.summary-fields-menu{min-width:180px}
.summary-menu-actions{display:flex;gap:8px;padding:8px 4px 2px;border-top:1px solid var(--line)}
.summary-menu-actions button{flex:1;display:inline-flex;min-height:30px;align-items:center;justify-content:center;padding:0 12px;border:1px solid var(--line);border-radius:6px;font-size:12px;cursor:pointer}
.summary-menu-actions .summary-apply{border-color:var(--accent);background:var(--accent);color:#fff}
.summary-menu-actions .summary-apply:disabled{opacity:.55;cursor:not-allowed}
.summary-menu-actions .summary-dismiss{background:var(--paper);color:var(--ink2)}
.summary-menu-actions button:hover{filter:brightness(1.05)}
.summary-range-apply,.summary-range-cancel{flex:none;min-height:26px;padding:0 10px;border:1px solid var(--line);border-radius:5px;background:var(--paper);color:var(--ink2);font-size:12px;cursor:pointer}
.summary-range-apply{border-color:var(--accent);background:var(--accent);color:#fff}
.summary-range-apply:disabled{opacity:.55;cursor:not-allowed}
.summary-range-apply:hover,.summary-range-cancel:hover{filter:brightness(1.05)}
.summary-report-area{min-height:0;flex:1;overflow:auto;padding:21px 30px 34px}.summary-report-title{display:flex;align-items:baseline;gap:10px;border-bottom:1px solid var(--line)}.summary-report-title h2{margin:0 0 12px;font-size:17px;font-weight:600}.summary-report{max-width:1160px;padding:11px 0 22px;color:var(--ink2);font-size:13px;line-height:1.65}.summary-report :deep(h2){margin:17px 0 8px;color:var(--ink);font-size:14px;font-weight:600}.summary-report :deep(table){width:100%;border-collapse:collapse;font-size:12px}.summary-report :deep(th),.summary-report :deep(td){padding:7px 9px;border:1px solid var(--line);text-align:left;vertical-align:top}.summary-report :deep(th){background:var(--paper);color:var(--ink)}.summary-report :deep(td){overflow-wrap:anywhere}
.summary-notes{max-width:1160px;margin-top:7px}.summary-notes h2{margin:0 0 9px;font-size:14px;font-weight:600}.summary-notes :deep(.task-rich-input){min-height:180px}.summary-notes :deep(.task-rich-hint){display:none}
@media(max-width:760px){.summary-head{padding:17px 15px 12px}.summary-filters{gap:6px}.summary-filter,.summary-popover>summary{min-height:32px;padding:0 7px}.summary-filter select{max-width:120px}.summary-range{width:100%;justify-content:space-between}.summary-range input{width:min(42vw,140px)}.summary-report-area{padding:16px 15px 28px}.summary-report{overflow-x:auto}.summary-report :deep(table){min-width:680px}}
</style>
