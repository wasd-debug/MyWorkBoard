<template>
  <section ref="workspace" class="summary-workspace" aria-label="任务摘要">
    <header class="summary-head">
      <div class="summary-head-title">
        <h1>摘要</h1>
        <div class="summary-head-actions">
          <details class="summary-icon-menu" data-pop="group">
            <summary aria-label="分组方式" title="分组方式"><ListFilter :size="17" /></summary>
            <div class="summary-menu summary-icon-panel">
              <button v-for="option in SUMMARY_GROUPS" :key="option.key" type="button" :class="{ active: groupBy === option.key }" @click="groupBy = option.key">{{ option.label }}<Check v-if="groupBy === option.key" :size="14" /></button>
            </div>
          </details>
          <details class="summary-icon-menu" data-pop="fields">
            <summary aria-label="显示内容" title="显示内容"><FileText :size="17" /></summary>
            <div class="summary-menu summary-icon-panel summary-fields-menu">
              <label v-for="field in SUMMARY_FIELDS" :key="field.key" class="summary-check-option"><input type="checkbox" :checked="draftFields.includes(field.key)" @change="toggleDraftField(field.key, $event.target.checked)" />{{ field.label }}</label>
              <div class="summary-menu-actions"><button type="button" class="summary-apply" @click="applyFieldsDraft">确定</button><button type="button" class="summary-dismiss" @click="dismissPopover">取消</button></div>
            </div>
          </details>
        </div>
      </div>
      <div class="summary-filters">
        <details class="summary-popover" data-pop="date"><summary @click="closeOtherPopovers"><CalendarDays :size="15" />{{ dateLabel }}<ChevronDown :size="13" /></summary><div class="summary-menu">
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
        <details class="summary-popover" data-pop="status"><summary @click="closeOtherPopovers"><CircleCheck :size="15" />{{ statusLabel }}<ChevronDown :size="13" /></summary><div class="summary-menu">
          <button v-for="option in statusOptions" :key="option.value" type="button" :class="{ active: status === option.value }" @click="chooseStatus(option.value, $event)">{{ option.label }}<Check v-if="status === option.value" :size="14" /></button>
        </div></details>
        <details class="summary-popover" data-pop="priority" @toggle="onPopoverToggle"><summary @click="closeOtherPopovers"><SlidersHorizontal :size="15" />更多<ChevronDown :size="13" /></summary><div class="summary-menu summary-more-menu">
          <strong>优先级</strong><label v-for="option in priorityOptions" :key="option.value" class="summary-check-option"><input v-model="draftPriorities" type="checkbox" :value="option.value" />{{ option.label }}</label>
          <div class="summary-menu-actions"><button type="button" class="summary-apply" @click="applyPriorityDraft">确定</button><button type="button" class="summary-dismiss" @click="dismissPopover">取消</button></div>
        </div></details>
      </div>
    </header>
    <div ref="editorArea" class="summary-editor-area">
      <TaskMilkdownEditor v-model="draft" label="摘要报告" placeholder="筛选后自动生成报告，可直接编辑" />
    </div>
    <footer class="summary-foot">
      <small v-if="feedback" class="summary-feedback" role="status">{{ feedback }}</small>
      <div class="summary-foot-actions">
        <button type="button" class="summary-copy" @click="copyMarkdown">复制</button>
        <div class="summary-save">
          <button type="button" class="summary-save-trigger" :aria-expanded="saveMenuOpen" @click="saveMenuOpen = !saveMenuOpen">保存为<ChevronDown :size="13" /></button>
          <div v-if="saveMenuOpen" class="summary-save-menu">
            <button type="button" @click="saveAsPdf">保存为 PDF</button>
            <button type="button" @click="saveAsImage">保存为图片</button>
            <template v-if="noteLists.length">
              <button v-for="list in noteLists" :key="list.publicId" type="button" @click="saveToNote(list)">保存到笔记清单 · {{ list.name }}</button>
            </template>
            <span v-else class="summary-save-empty">暂无笔记清单</span>
          </div>
        </div>
      </div>
    </footer>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { CalendarDays, Check, ChevronDown, CircleCheck, FileText, ListFilter, ListTodo, SlidersHorizontal, Tag } from 'lucide-vue-next'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import TaskMilkdownEditor from '../components/tasks/TaskMilkdownEditor.vue'
import { useTasksStore } from '../stores/tasks.js'
import { SUMMARY_FIELDS, SUMMARY_GROUPS, buildSummaryMarkdown, filterSummaryItems, groupSummaryItems, summaryDateRange, summaryRangeLabel } from './taskSummary.js'

const store = useTasksStore()
const workspace = ref(null)
const editorArea = ref(null)
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
const draftFields = ref([...fields.value])
const draft = ref('')
const feedback = ref('')
const saveMenuOpen = ref(false)
let feedbackTimer = null
// 清单/标签模式去掉「各个清单」「各标签」选项：勾选列表本身即代表选定范围
const listModes = [
  { value: 'ALL', label: '所有' },
  { value: 'LISTS', label: '所有清单' },
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
]
const priorityOptions = [
  { value: 'HIGH', label: '高' }, { value: 'MEDIUM', label: '中' }, { value: 'LOW', label: '低' }, { value: 'NONE', label: '无' },
]
const availableLists = computed(() => store.lists.filter(list => !list.archived && list.objectType !== 'NOTE'))
const noteLists = computed(() => store.lists.filter(list => !list.archived && list.objectType === 'NOTE'))
const dateRange = computed(() => summaryDateRange(datePreset.value, new Date(), customRange))
const dateLabel = computed(() => {
  if (datePreset.value === 'CUSTOM') {
    return customRange.from && customRange.to ? `${customRange.from} 至 ${customRange.to}` : '自定义'
  }
  return `${dateOptions.find(option => option.value === datePreset.value)?.label || '本周'} (${summaryRangeLabel(dateRange.value)})`
})
const statusLabel = computed(() => statusOptions.find(option => option.value === status.value)?.label || '所有状态')
const listSummary = computed(() => {
  if (listMode.value === 'SELECTED') return `清单 ${listIds.value.length || '选择'}`
  return listModes.find(option => option.value === listMode.value)?.label || '所有'
})
const tagSummary = computed(() => {
  if (tagMode.value === 'SELECTED') return `标签 ${tagIds.value.length || '选择'}`
  return tagModes.find(option => option.value === tagMode.value)?.label || '所有'
})
// 原始条目只做轻量拼接：派生字段（清单名/标签名/主任务名）放到筛选之后再算，
// 否则要为全部 8700+ 任务创建浅拷贝与标签数组（首屏堆内存可达 300MB，低配机器易崩）
const rawItems = computed(() => {
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
    listId: '',
    listName: '打卡',
    completedAt: ['DONE', 'COMPLETED'].includes(item.status) ? item.date : null,
  }))
  return [...store.tasks.filter(item => !item.deleted), ...checkins]
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
const listNameMap = computed(() => new Map(store.lists.map(list => [list.publicId, list.name])))
const tagNameMap = computed(() => new Map(store.tags.map(tag => [tag.publicId, tag.name])))
const parentTitleMap = computed(() => new Map(store.tasks.map(task => [task.publicId, task.title])))
// 仅对筛选结果补派生字段（清单名/标签名/主任务名），数量为匹配项而非全量
const displayItems = computed(() => filteredItems.value.map(item => item.kind === 'checkin' ? item : ({
  ...item,
  kind: 'task',
  listName: listNameMap.value.get(item.listId) || '',
  tagNames: (item.tagIds || []).map(id => tagNameMap.value.get(id)).filter(Boolean),
  parentTitle: item.parentId ? (parentTitleMap.value.get(item.parentId) || '') : '',
})))
const groupNames = computed(() => Object.fromEntries([
  ...store.lists.map(list => [list.publicId, list.name]),
  ...store.tags.map(tag => [tag.publicId, `#${tag.name}`]),
]))
const groups = computed(() => groupSummaryItems(displayItems.value, groupBy.value, groupNames.value))
const autoMarkdown = computed(() => {
  const rangeLabel = datePreset.value === 'CUSTOM'
    ? `${customRange.from} 至 ${customRange.to}`
    : summaryRangeLabel(dateRange.value)
  return `# ${rangeLabel}\n\n${buildSummaryMarkdown(groups.value, fields.value, { status: { OPEN: '未完成', COMPLETED: '已完成', DONE: '已完成', ABANDONED: '已放弃' } })}`
})
// 报告即编辑器内容：筛选变化时自动重建，用户可在编辑器里继续编辑补充
watch(autoMarkdown, value => { draft.value = value }, { immediate: true })
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
function chooseTagMode(value) { tagMode.value = value; if (value !== 'SELECTED') tagIds.value = [] }
function onPopoverToggle(event) {
  if (!event.target.open) return
  const kind = event.target.dataset.pop
  if (kind === 'list') draftListIds.value = [...listIds.value]
  else if (kind === 'tag') draftTagIds.value = [...tagIds.value]
  else if (kind === 'priority') draftPriorities.value = [...priorities.value]
  else if (kind === 'fields') draftFields.value = [...fields.value]
}
function toggleDraftList(id, checked) {
  draftListIds.value = checked ? [...new Set([...draftListIds.value, id])] : draftListIds.value.filter(value => value !== id)
}
function toggleDraftTag(id, checked) {
  draftTagIds.value = checked ? [...new Set([...draftTagIds.value, id])] : draftTagIds.value.filter(value => value !== id)
}
function toggleDraftField(key, checked) {
  draftFields.value = checked ? [...new Set([...draftFields.value, key])] : draftFields.value.filter(value => value !== key)
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
function applyFieldsDraft() {
  fields.value = [...draftFields.value]
  dismissPopover()
}
function dismissPopover(event) {
  const target = event?.target instanceof Element ? event.target : null
  if (target) target.closest('details').open = false
  else workspace.value?.querySelectorAll('details[open]').forEach(details => { details.open = false })
}
function showFeedback(text) {
  feedback.value = text
  if (feedbackTimer) clearTimeout(feedbackTimer)
  feedbackTimer = setTimeout(() => { feedback.value = '' }, 2200)
}
function reportTitle() {
  return datePreset.value === 'CUSTOM' ? `摘要 ${customRange.from} 至 ${customRange.to}` : `摘要 ${summaryRangeLabel(dateRange.value)}`
}
async function copyMarkdown() {
  try {
    await navigator.clipboard.writeText(draft.value)
    showFeedback('已复制 Markdown')
  } catch {
    showFeedback('复制失败，请手动选择内容复制')
  }
  saveMenuOpen.value = false
}
function saveAsPdf() {
  saveMenuOpen.value = false
  window.print()
}
async function saveAsImage() {
  saveMenuOpen.value = false
  try {
    const { default: html2canvas } = await import('html2canvas')
    const target = editorArea.value
    if (!target) return
    // 编辑器内部是三层滚动/定高容器（区域 flex:1 → 编辑器 height:100% → 输入区 overflow:auto）：
    // 导出前全部放开为内容高度，否则长报告只截到可视区域
    const editor = target.querySelector('.task-rich-editor')
    const wrap = target.querySelector('.task-rich-input-wrap')
    const input = target.querySelector('.task-rich-input')
    const previous = {
      areaOverflow: target.style.overflow, areaHeight: target.style.height,
      editorHeight: editor?.style.height || '',
      wrapOverflow: wrap?.style.overflow || '', wrapHeight: wrap?.style.height || '',
      inputHeight: input?.style.height || '',
    }
    target.style.overflow = 'visible'
    target.style.height = 'auto'
    if (editor) editor.style.height = 'auto'
    if (wrap) { wrap.style.overflow = 'visible'; wrap.style.height = 'auto' }
    if (input) input.style.height = 'auto'
    const canvas = await html2canvas(target, {
      backgroundColor: getComputedStyle(document.body).backgroundColor || '#ffffff',
      scale: 2,
      windowWidth: target.scrollWidth,
      windowHeight: target.scrollHeight,
      height: target.scrollHeight,
      onclone: doc => { doc.querySelectorAll('.task-rich-toolbar').forEach(el => { el.style.display = 'none' }) },
    }).finally(() => {
      target.style.overflow = previous.areaOverflow
      target.style.height = previous.areaHeight
      if (editor) editor.style.height = previous.editorHeight
      if (wrap) { wrap.style.overflow = previous.wrapOverflow; wrap.style.height = previous.wrapHeight }
      if (input) input.style.height = previous.inputHeight
    })
    const link = document.createElement('a')
    link.download = `${reportTitle()}.png`
    link.href = canvas.toDataURL('image/png')
    link.click()
    showFeedback('已导出图片')
  } catch (error) {
    showFeedback(error?.message ? `导出图片失败：${error.message}` : '导出图片失败')
  }
}
async function saveToNote(list) {
  saveMenuOpen.value = false
  try {
    const description = DOMPurify.sanitize(marked.parse(draft.value, { async: false }), { ALLOWED_URI_REGEXP: /^(?:https?:|mailto:)/i })
    await store.create({ title: reportTitle(), listId: list.publicId, description })
    showFeedback(`已保存到「${list.name}」`)
  } catch (error) {
    showFeedback(error?.response?.data?.detail || error?.message || '保存到笔记清单失败')
  }
}
function closePopovers(event) {
  if (event.target.closest?.('.summary-popover, .summary-icon-menu')) return
  workspace.value?.querySelectorAll('details[open]').forEach(details => { details.open = false })
}
function closeOtherPopovers(event) {
  const current = event.currentTarget?.parentElement
  workspace.value?.querySelectorAll('details[open]').forEach(details => { if (details !== current) details.open = false })
}
function closeSaveMenu(event) {
  if (event.target.closest?.('.summary-save')) return
  saveMenuOpen.value = false
}
onMounted(() => {
  document.addEventListener('pointerdown', closePopovers)
  document.addEventListener('pointerdown', closeSaveMenu)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', closePopovers)
  document.removeEventListener('pointerdown', closeSaveMenu)
  if (feedbackTimer) clearTimeout(feedbackTimer)
})
</script>

<style scoped>
.summary-workspace{display:flex;min-width:0;height:100%;flex-direction:column;overflow:hidden;background:var(--card);color:var(--ink)}
.summary-head{display:grid;gap:12px;padding:18px 26px 12px;border-bottom:1px solid var(--line)}
.summary-head-title{display:flex;align-items:center;justify-content:space-between;gap:12px}
.summary-head-title h1{margin:0;font-size:21px;font-weight:650}
.summary-head-actions{display:flex;align-items:center;gap:4px}
.summary-icon-menu{position:relative}
.summary-icon-menu>summary{display:grid;width:32px;height:32px;place-items:center;border-radius:6px;color:var(--ink2);list-style:none;cursor:pointer}
.summary-icon-menu>summary::-webkit-details-marker{display:none}
.summary-icon-menu>summary:hover,.summary-icon-menu[open]>summary{background:var(--accent-soft);color:var(--accent)}
.summary-filters{display:flex;flex-wrap:wrap;align-items:center;gap:8px}
.summary-popover>summary,.summary-range{display:inline-flex;min-height:34px;align-items:center;gap:7px;padding:0 10px;border:1px solid var(--line);border-radius:6px;background:var(--paper);color:var(--ink2);font-size:12px}
.summary-range{gap:6px;padding:3px 8px}.summary-range input{width:122px;border:0;background:transparent;color:var(--ink);font:inherit}.summary-range span{color:var(--muted)}
.summary-popover{position:relative}.summary-popover>summary{list-style:none;cursor:pointer}.summary-popover>summary::-webkit-details-marker{display:none}.summary-popover[open]>summary{border-color:var(--accent)}
.summary-menu{position:absolute;z-index:20;top:calc(100% + 5px);left:0;display:grid;min-width:190px;max-height:min(55vh,420px);gap:2px;overflow:auto;padding:6px;border:1px solid var(--line);border-radius:7px;background:var(--card);box-shadow:0 10px 28px rgb(15 23 42 / 16%)}
.summary-menu button{display:flex;min-height:34px;align-items:center;justify-content:space-between;gap:12px;padding:6px 8px;border:0;border-radius:4px;background:transparent;color:var(--ink2);font:inherit;font-size:12px;text-align:left;cursor:pointer}.summary-menu button:hover,.summary-menu button.active{background:var(--accent-soft);color:var(--accent)}.summary-menu strong{padding:5px 8px;color:var(--muted);font-size:11px;font-weight:550}.summary-menu hr{width:100%;margin:4px 0;border:0;border-top:1px solid var(--line)}
/* 右上角图标菜单右对齐展开（需写在 .summary-menu 之后，否则被其 left:0 覆盖而超出视口右缘） */
.summary-menu.summary-icon-panel{left:auto;right:0;min-width:190px}
.summary-check-option{display:flex;min-height:30px;align-items:center;gap:8px;padding:4px 8px;color:var(--ink2);font-size:12px;cursor:pointer}.summary-check-option input{accent-color:var(--accent)}.summary-fields-menu{min-width:180px}
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
.summary-editor-area{min-height:0;flex:1;overflow:auto;padding:14px 26px}
.summary-editor-area :deep(.task-rich-editor){display:flex;height:100%;min-height:320px;flex-direction:column;border:0;border-radius:0;background:transparent}
.summary-editor-area :deep(.task-rich-input-wrap){flex:1;min-height:0;overflow:auto}
.summary-editor-area :deep(.task-rich-input){min-height:100%;padding:4px 2px 40px;font-size:13px}
.summary-editor-area :deep(.task-rich-input table){width:100%;margin:10px 0 16px;border-collapse:collapse;font-size:12px}
.summary-editor-area :deep(.task-rich-input th),
.summary-editor-area :deep(.task-rich-input td){padding:6px 10px;border:1px solid var(--line);text-align:left;vertical-align:top}
.summary-editor-area :deep(.task-rich-input th){background:var(--paper);font-weight:600;white-space:nowrap}
.summary-editor-area :deep(.task-rich-input td:first-child){white-space:nowrap}
.summary-editor-area :deep(.task-rich-toolbar){padding:4px 0;border-bottom:1px solid var(--line);background:transparent}
.summary-editor-area :deep(.task-rich-hint){display:none}
.summary-foot{display:flex;align-items:center;justify-content:flex-end;gap:12px;padding:10px 92px 14px 26px;border-top:1px solid var(--line)}
.summary-feedback{color:var(--muted);font-size:12px}
.summary-foot-actions{display:flex;align-items:center;gap:10px}
.summary-copy,.summary-save-trigger{display:inline-flex;min-height:34px;align-items:center;gap:6px;padding:0 16px;border:1px solid var(--accent);border-radius:6px;background:var(--accent);color:#fff;font:inherit;font-size:12px;cursor:pointer}
.summary-save{position:relative}
.summary-save-trigger{border-color:var(--line);background:var(--paper);color:var(--accent)}
.summary-copy:hover,.summary-save-trigger:hover{filter:brightness(1.06)}
.summary-save-menu{position:absolute;z-index:30;right:0;bottom:calc(100% + 6px);display:grid;min-width:190px;gap:2px;padding:6px;border:1px solid var(--line);border-radius:7px;background:var(--card);box-shadow:0 10px 28px rgb(15 23 42 / 16%)}
.summary-save-menu button{display:flex;min-height:32px;align-items:center;padding:6px 9px;border:0;border-radius:4px;background:transparent;color:var(--ink2);font:inherit;font-size:12px;text-align:left;cursor:pointer}
.summary-save-menu button:hover{background:var(--accent-soft);color:var(--accent)}
.summary-save-empty{padding:6px 9px;color:var(--muted);font-size:12px}
@media(max-width:760px){.summary-head{padding:16px 14px 10px}.summary-filters{gap:6px}.summary-popover>summary{min-height:32px;padding:0 8px}.summary-range{width:100%;justify-content:space-between}.summary-range input{width:min(38vw,120px)}.summary-editor-area{padding:10px 14px}.summary-foot{padding:10px 84px 12px 14px}}
@media print{.summary-head,.summary-foot{display:none}.summary-editor-area{overflow:visible;padding:0}.summary-editor-area :deep(.task-rich-toolbar){display:none}}
</style>
