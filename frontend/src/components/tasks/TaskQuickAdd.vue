<template>
  <Teleport to="body">
  <div v-if="open" ref="rootEl" class="cal-quick" :style="style" role="dialog" :aria-label="ariaLabel" @click.stop @keydown.esc.prevent="onCardEscape">
    <header class="cal-quick-head">
      <button ref="dateBtn" type="button" class="cal-quick-date" :aria-expanded="dateOpen" @click.stop="dateOpen = !dateOpen">
        <CalendarDays :size="15" />{{ dateLabel }}
      </button>
      <div class="cal-quick-priority">
        <button type="button" class="cal-quick-flag" :class="`is-${String(priority).toLowerCase()}`" aria-label="设置优先级" :aria-expanded="priorityMenu" @click.stop="priorityMenu = !priorityMenu">
          <Flag :size="15" />
        </button>
        <div v-if="priorityMenu" class="cal-quick-menu is-priority" @click.stop>
          <button v-for="option in priorityOptions" :key="option.value" type="button" :class="`is-${option.value.toLowerCase()}`" @click="pickPriority(option.value)">
            <Flag :size="14" /><span class="cal-quick-label">{{ option.label }}</span><Check v-if="priority === option.value" :size="13" />
          </button>
        </div>
      </div>
    </header>

    <textarea
      ref="titleEl"
      class="cal-quick-title"
      :value="title"
      rows="1"
      :placeholder="placeholder"
      @input="onTitleInput"
      @keydown.enter.exact.prevent="$emit('save')"
    ></textarea>

    <div class="cal-quick-desc-row">
      <TaskMarkdownEditor
        :model-value="description"
        compact
        label="任务描述"
        placeholder="+ 输入内容或使用 / 快速插入"
        @update:model-value="$emit('update:description', $event)"
      />
      <button type="button" class="cal-quick-icon-btn" :class="{ on: checklistOpen || checklist.length }" aria-label="检查事项" title="检查事项" @click.stop="togglePanel('checklist')">
        <ListChecks :size="16" />
      </button>
    </div>

    <div v-if="checklistOpen || checklist.length" class="cal-quick-checklist" @click.stop>
      <div v-for="(item, index) in checklist" :key="index" class="cal-quick-check-row">
        <button type="button" class="cal-quick-check-box" :class="{ done: item.completed }" :aria-label="item.title" @click="toggleChecklistItem(index)">
          <Check v-if="item.completed" :size="10" />
        </button>
        <input :value="item.title" maxlength="200" placeholder="检查事项" @input="updateChecklistItem(index, $event.target.value)" @keydown.enter.exact.prevent="addChecklistItem" />
        <button type="button" class="cal-quick-check-remove" aria-label="删除检查事项" @click="removeChecklistItem(index)"><X :size="12" /></button>
      </div>
      <button v-if="checklistOpen" type="button" class="cal-quick-check-add" @click="addChecklistItem"><Plus :size="13" />添加检查事项</button>
    </div>

    <div v-if="tagIds.length" class="cal-quick-tags">
      <span v-for="tag in selectedTags" :key="tag.publicId" class="cal-quick-tag" :style="{ borderColor: tag.color, color: tag.color }">
        {{ tag.name }}<button type="button" :aria-label="`移除标签 ${tag.name}`" @click="$emit('update:tagIds', tagIds.filter(id => id !== tag.publicId))"><X :size="9" /></button>
      </span>
    </div>

    <footer class="cal-quick-foot">
      <button type="button" class="cal-quick-list" :aria-expanded="listMenu" @click.stop="togglePanel('list')">
        <Inbox :size="14" /><span>{{ selectedList?.name || '收集箱' }}</span>
      </button>
      <span class="cal-quick-grow"></span>
      <button type="button" class="cal-quick-more" :aria-expanded="moreMenu" aria-label="更多选项" @click.stop="togglePanel('more')"><MoreHorizontal :size="18" /></button>
      <button type="button" class="cal-quick-save" :disabled="!title.trim() || saving" @click="$emit('save')">{{ saving ? '保存中…' : '保存' }}</button>
    </footer>

    <!-- 清单选择：带搜索的浮层 -->
    <div v-if="listMenu" class="cal-quick-menu is-list" :class="{ 'is-drop': dropDown }" @click.stop>
      <label class="cal-quick-search"><Search :size="13" /><input v-model="listQuery" placeholder="搜索" aria-label="搜索清单" /></label>
      <button v-for="list in filteredLists" :key="list.publicId" type="button" :class="{ on: list.publicId === listId }" @click="pickList(list)">
        <TaskOrganizationIcon :value="list.icon" :size="14" :style="{ color: list.color || 'var(--ink2)' }" />
        <span class="cal-quick-label">{{ list.name }}</span>
        <Check v-if="list.publicId === listId" :size="13" />
      </button>
      <p v-if="!filteredLists.length" class="cal-quick-empty">没有匹配的清单</p>
    </div>

    <!-- 更多：标签 / 检查事项 -->
    <div v-if="moreMenu" class="cal-quick-menu is-more" :class="{ 'is-drop': dropDown }" @click.stop>
      <button type="button" @click.stop="togglePanel('tag')"><Tag :size="14" /><span class="cal-quick-label">标签</span><em>{{ selectedTags.length || '' }}</em></button>
      <button type="button" @click.stop="togglePanel('checklist')"><ListChecks :size="14" /><span class="cal-quick-label">检查事项</span></button>
    </div>

    <!-- 标签选择：搜索 + 多选 + 现场新建 + 取消/确定 -->
    <div v-if="tagMenu" class="cal-quick-menu is-tags" :class="{ 'is-drop': dropDown }" @click.stop>
      <label class="cal-quick-search">
        <Search :size="13" />
        <input v-model="tagQuery" placeholder="输入标签" aria-label="搜索标签" @keydown.enter.prevent="onTagQueryEnter" />
      </label>
      <button v-if="canCreateTag" type="button" class="cal-quick-create-tag" :disabled="creatingTag" @click="createTagFromQuery">
        <Plus :size="13" /><span class="cal-quick-label">{{ creatingTag ? '创建中…' : `创建「${tagQuery.trim()}」` }}</span>
      </button>
      <div class="cal-quick-tag-list">
        <button v-for="tag in filteredTags" :key="tag.publicId" type="button" :class="{ on: tagDraft.includes(tag.publicId) }" @click="toggleTagDraft(tag.publicId)">
          <Tag :size="14" :style="{ color: tag.color || 'var(--ink2)' }" />
          <span class="cal-quick-label">{{ tag.name }}</span>
          <Check v-if="tagDraft.includes(tag.publicId)" :size="13" />
        </button>
        <p v-if="!filteredTags.length && !canCreateTag" class="cal-quick-empty">没有匹配的标签</p>
      </div>
      <div class="cal-quick-panel-actions">
        <button type="button" class="is-ghost" @click="tagMenu = false">取消</button>
        <button type="button" class="is-primary" @click="confirmTags">确定</button>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="dateOpen" class="cal-quick-date-popover" :style="datePopoverStyle" @click.stop>
        <TaskDateTimePopover
          :mode="dateMode"
          :month="dateMonth"
          :calendar-days="dateCells"
          :month-label="dateMonthLabel"
          :selected-key="selectedDateKey"
          :time="timeValue"
          :all-day="allDayModel"
          apply-label="确定"
          @update:mode="dateMode = $event"
          @quick-date="applyQuickDate"
          @move-month="dateMonth = new Date(dateMonth.getFullYear(), dateMonth.getMonth() + $event, 1)"
          @select-date="applyDateKey"
          @clear-date="clearDate"
          @toggle-all-day="allDayModel = $event"
          @apply="confirmDate"
        >
          <template #rows="{ mode }">
            <div class="cal-quick-date-rows">
              <div class="cal-quick-date-row">
                <Clock3 :size="14" /><span>时间</span>
                <input v-model="timeValue" type="time" aria-label="时间" :disabled="allDayModel" />
              </div>
              <label class="cal-quick-date-row is-switch">
                <span>全天</span>
                <button type="button" role="switch" :aria-checked="allDayModel" :class="{ on: allDayModel }" @click="allDayModel = !allDayModel"><i></i></button>
              </label>
              <div class="cal-quick-date-row">
                <Bell :size="14" /><span>提醒</span>
                <select :value="reminderValue" aria-label="提醒" @change="$emit('update:reminderMinutes', $event.target.value === '' ? null : Number($event.target.value))">
                  <option value="">不提醒</option>
                  <option v-for="option in reminderOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
                </select>
              </div>
              <div class="cal-quick-date-row">
                <Repeat2 :size="14" /><span>重复</span>
                <select :value="rrule" aria-label="重复" @change="$emit('update:rrule', $event.target.value)">
                  <option v-for="option in repeatPresets" :key="option.value" :value="option.value">{{ option.label }}</option>
                </select>
              </div>
              <p v-if="mode === 'RANGE'" class="cal-quick-date-hint">时间段模式会同时设置开始与截止时间。</p>
            </div>
          </template>
        </TaskDateTimePopover>
      </div>
    </Teleport>
  </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { Bell, CalendarDays, Check, Clock3, Flag, Inbox, ListChecks, MoreHorizontal, Plus, Repeat2, Search, Tag, X } from 'lucide-vue-next'
import TaskDateTimePopover from './TaskDateTimePopover.vue'
import TaskOrganizationIcon from './TaskOrganizationIcon.vue'
import TaskMarkdownEditor from './TaskMarkdownEditor.vue'
import { useExclusivePanels } from '../../utils/exclusivePanels.js'
import { broadcastPanel } from '../../utils/panelBus.js'

const props = defineProps({
  open: { type: Boolean, default: false },
  dateLabel: { type: String, default: '' },
  dueAt: { type: String, default: '' },
  allDay: { type: Boolean, default: false },
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  priority: { type: String, default: 'NONE' },
  listId: { type: String, default: '' },
  tagIds: { type: Array, default: () => [] },
  checklist: { type: Array, default: () => [] },
  lists: { type: Array, default: () => [] },
  tags: { type: Array, default: () => [] },
  // 传入后可在标签浮层里现场新建，签名 (name) => Promise<tag>
  createTag: { type: Function, default: null },
  // 相对截止时间的提醒分钟数，null 表示不提醒
  reminderMinutes: { type: Number, default: null },
  // 重复规则（RRULE），空串表示不重复
  rrule: { type: String, default: '' },
  saving: { type: Boolean, default: false },
  placeholder: { type: String, default: '准备做什么？' },
  ariaLabel: { type: String, default: '新增任务' },
  style: { type: [Object, String], default: () => ({}) }
})

const emit = defineEmits([
  'update:title', 'update:description', 'update:priority', 'update:listId',
  'update:tagIds', 'update:checklist', 'update:dueAt', 'update:allDay',
  'update:reminderMinutes', 'update:rrule', 'save', 'close'
])

const REMINDER_OPTIONS = [
  { value: 0, label: '准时' },
  { value: 5, label: '提前 5 分钟' },
  { value: 15, label: '提前 15 分钟' },
  { value: 30, label: '提前 30 分钟' },
  { value: 60, label: '提前 1 小时' },
  { value: 1440, label: '提前 1 天' }
]
const REPEAT_PRESETS = [
  { value: '', label: '不重复' },
  { value: 'FREQ=DAILY', label: '每天' },
  { value: 'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR', label: '每个工作日' },
  { value: 'FREQ=WEEKLY', label: '每周' },
  { value: 'FREQ=MONTHLY', label: '每月' },
  { value: 'FREQ=YEARLY', label: '每年' }
]
const reminderOptions = REMINDER_OPTIONS
const repeatPresets = REPEAT_PRESETS
const reminderValue = computed(() => (props.reminderMinutes === null || props.reminderMinutes === undefined ? '' : String(props.reminderMinutes)))

const PRIORITY_OPTIONS = [
  { value: 'HIGH', label: '高优先级' },
  { value: 'MEDIUM', label: '中优先级' },
  { value: 'LOW', label: '低优先级' },
  { value: 'NONE', label: '无优先级' }
]
const priorityOptions = PRIORITY_OPTIONS

const titleEl = ref(null)
const dateBtn = ref(null)
const rootEl = ref(null)
const dropDown = ref(false)
const priorityMenu = ref(false)
const listMenu = ref(false)
const moreMenu = ref(false)
const tagMenu = ref(false)
const checklistOpen = ref(false)
const listQuery = ref('')
const tagQuery = ref('')
const tagDraft = ref([])
const creatingTag = ref(false)
const dateOpen = ref(false)
const dateMode = ref('DATE')
const dateMonth = ref(new Date())
const allDayModel = ref(props.allDay)
const timeValue = ref('')
const dateDraft = reactive({ key: '' })

// 卡片里所有浮层互斥：优先级 / 清单 / 更多 / 标签 / 检查事项 / 日期，同一时间只开一个
useExclusivePanels('quickadd', [
  ['priority', () => priorityMenu.value, value => (priorityMenu.value = value)],
  ['list', () => listMenu.value, value => (listMenu.value = value)],
  ['more', () => moreMenu.value, value => (moreMenu.value = value)],
  ['tag', () => tagMenu.value, value => (tagMenu.value = value)],
  ['checklist', () => checklistOpen.value, value => (checklistOpen.value = value)],
  ['date', () => dateOpen.value, value => (dateOpen.value = value)]
], {
  // 页面上别的浮层打开了，整张卡片收起，避免两套浮层叠在一起
  onForeign: () => { if (props.open) emit('close') }
})

watch(() => props.open, async value => {
  if (!value) {
    closePanels()
    return
  }
  // 卡片本身也算一个浮层：打开时通知页面上其它菜单先收起来
  broadcastPanel('quickadd:card')
  await nextTick()
  titleEl.value?.focus?.()
})

watch(() => props.tagIds, value => { tagDraft.value = [...(value || [])] }, { immediate: true })
watch(() => props.allDay, value => { allDayModel.value = Boolean(value) })
watch(() => props.dueAt, value => { timeValue.value = toTimeValue(value) }, { immediate: true })

const selectedList = computed(() => props.lists.find(item => item.publicId === props.listId) || null)
const selectedTags = computed(() => props.tagIds.map(id => props.tags.find(tag => tag.publicId === id)).filter(Boolean))
const filteredLists = computed(() => {
  const query = listQuery.value.trim().toLowerCase()
  return props.lists.filter(list => !query || String(list.name || '').toLowerCase().includes(query))
})
const filteredTags = computed(() => {
  const query = tagQuery.value.trim().toLowerCase()
  return props.tags.filter(tag => !query || String(tag.name || '').toLowerCase().includes(query))
})
const exactTagMatch = computed(() => {
  const query = tagQuery.value.trim().toLowerCase()
  return Boolean(query) && props.tags.some(tag => String(tag.name || '').toLowerCase() === query)
})
const canCreateTag = computed(() => Boolean(props.createTag) && Boolean(tagQuery.value.trim()) && !exactTagMatch.value)

function toTimeValue(value) {
  const date = value ? new Date(value) : null
  if (!date || Number.isNaN(date.getTime())) return '09:00'
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function onTitleInput(event) {
  emit('update:title', event.target.value)
  const el = event.target
  el.style.height = 'auto'
  el.style.height = `${Math.min(160, el.scrollHeight)}px`
}

/** 卡片贴近视口顶部时，向上弹的浮层会顶出屏幕，这里改成向下展开 */
function measureDrop() {
  const rect = rootEl.value?.getBoundingClientRect?.()
  dropDown.value = rect ? rect.top < 340 : false
}

function togglePanel(kind) {
  measureDrop()
  if (kind === 'list') listMenu.value = !listMenu.value
  else if (kind === 'more') moreMenu.value = !moreMenu.value
  else if (kind === 'checklist') toggleChecklist()
  else {
    tagMenu.value = !tagMenu.value
    if (tagMenu.value) {
      tagDraft.value = [...(props.tagIds || [])]
      tagQuery.value = ''
    }
  }
}

/** Esc 先收掉卡里打开的浮层，都没有开着时才关卡片 */
function onCardEscape() {
  if (dateOpen.value || priorityMenu.value || listMenu.value || moreMenu.value || tagMenu.value || checklistOpen.value) {
    closePanels()
    return
  }
  emit('close')
}

function closePanels() {
  priorityMenu.value = false
  listMenu.value = false
  moreMenu.value = false
  tagMenu.value = false
  checklistOpen.value = false
  dateOpen.value = false
}

function pickPriority(value) {
  emit('update:priority', value)
  priorityMenu.value = false
}

function pickList(list) {
  emit('update:listId', list.publicId)
  listMenu.value = false
  listQuery.value = ''
}

function toggleTagDraft(id) {
  const next = new Set(tagDraft.value)
  next.has(id) ? next.delete(id) : next.add(id)
  tagDraft.value = [...next]
}

async function createTagFromQuery() {
  const name = tagQuery.value.trim()
  if (!name || !props.createTag || creatingTag.value) return
  creatingTag.value = true
  try {
    const created = await props.createTag(name)
    const id = created?.publicId || created?.id
    if (id && !tagDraft.value.includes(id)) tagDraft.value = [...tagDraft.value, id]
    tagQuery.value = ''
  } finally {
    creatingTag.value = false
  }
}

/** 标签输入框回车：有同名标签就确定，没有就顺手建一个 */
function onTagQueryEnter() {
  if (canCreateTag.value) createTagFromQuery()
  else confirmTags()
}

function confirmTags() {
  emit('update:tagIds', [...tagDraft.value])
  tagMenu.value = false
  tagQuery.value = ''
}

function toggleChecklist() {
  checklistOpen.value = !checklistOpen.value
  if (checklistOpen.value && !props.checklist.length) addChecklistItem()
}

function addChecklistItem() {
  emit('update:checklist', [...props.checklist, { publicId: `new-${Date.now()}-${props.checklist.length}`, title: '', completed: false }])
  checklistOpen.value = true
}

function updateChecklistItem(index, value) {
  const next = props.checklist.map((item, i) => (i === index ? { ...item, title: value } : item))
  emit('update:checklist', next)
}

function toggleChecklistItem(index) {
  const next = props.checklist.map((item, i) => (i === index ? { ...item, completed: !item.completed } : item))
  emit('update:checklist', next)
}

function removeChecklistItem(index) {
  emit('update:checklist', props.checklist.filter((_, i) => i !== index))
}

/* 日期浮层：复用任务页那套 TaskDateTimePopover */
const dateMonthLabel = computed(() => `${dateMonth.value.getFullYear()}年${dateMonth.value.getMonth() + 1}月`)
const selectedDateKey = computed(() => dateDraft.key || (props.dueAt ? keyOf(new Date(props.dueAt)) : ''))
const dateCells = computed(() => {
  const first = new Date(dateMonth.value.getFullYear(), dateMonth.value.getMonth(), 1)
  const offset = (first.getDay() + 6) % 7
  const start = new Date(first.getFullYear(), first.getMonth(), first.getDate() - offset)
  const todayKey = keyOf(new Date())
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + index)
    const key = keyOf(date)
    return { date, key, otherMonth: date.getMonth() !== first.getMonth(), today: key === todayKey, selected: key === selectedDateKey.value }
  })
})
const datePopoverStyle = computed(() => {
  const rect = dateBtn.value?.getBoundingClientRect?.()
  const width = Math.min(378, window.innerWidth - 24)
  const left = rect ? Math.max(12, Math.min(rect.left - 8, window.innerWidth - width - 12)) : 40
  const height = 620
  let top = rect ? rect.bottom + 8 : 80
  if (top + height > window.innerHeight - 12) top = Math.max(12, window.innerHeight - height - 12)
  return { position: 'fixed', left: `${left}px`, top: `${top}px`, width: `${width}px`, zIndex: 1400 }
})

function keyOf(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

watch(dateOpen, value => {
  if (!value) return
  dateDraft.key = props.dueAt ? keyOf(new Date(props.dueAt)) : keyOf(new Date())
  dateMonth.value = new Date(new Date(props.dueAt || Date.now()).getFullYear(), new Date(props.dueAt || Date.now()).getMonth(), 1)
})

function applyQuickDate(kind) {
  const base = new Date()
  const target = new Date(base.getFullYear(), base.getMonth(), base.getDate())
  if (kind === 'TOMORROW') target.setDate(target.getDate() + 1)
  if (kind === 'NEXT_WEEK') target.setDate(target.getDate() + 7)
  if (kind === 'NEXT_MONTH') target.setMonth(target.getMonth() + 1)
  dateDraft.key = keyOf(target)
  dateMonth.value = new Date(target.getFullYear(), target.getMonth(), 1)
}

function applyDateKey(key) {
  dateDraft.key = key
}

function clearDate() {
  emit('update:dueAt', '')
  dateOpen.value = false
}

function confirmDate() {
  if (!dateDraft.key) {
    clearDate()
    return
  }
  const [hour, minute] = allDayModel.value ? [0, 0] : String(timeValue.value || '09:00').split(':').map(Number)
  const [year, month, day] = dateDraft.key.split('-').map(Number)
  const date = new Date(year, month - 1, day, hour || 0, minute || 0, 0)
  emit('update:allDay', allDayModel.value)
  emit('update:dueAt', date.toISOString())
  dateOpen.value = false
}
</script>

<style scoped>
.cal-quick {
  box-sizing: border-box;
  display: flex;
  width: min(420px, calc(100vw - 32px));
  flex-direction: column;
  gap: 4px;
  padding: 14px 14px 10px;
  border: 1px solid var(--line2);
  border-radius: 12px;
  background: var(--card);
  box-shadow: 0 22px 52px rgba(15, 23, 42, .22);
  color: var(--ink);
}
.cal-quick-head { display: flex; align-items: center; justify-content: space-between; }
.cal-quick-date { display: inline-flex; align-items: center; gap: 6px; padding: 3px 4px; border: 0; border-radius: 6px; background: transparent; color: var(--accent); font: inherit; font-size: 14px; font-weight: 600; cursor: pointer; }
.cal-quick-date:hover { background: var(--paper); }
.cal-quick-priority { position: relative; }
.cal-quick-flag { display: grid; place-items: center; width: 30px; height: 28px; border: 0; border-radius: 7px; background: transparent; color: var(--muted); cursor: pointer; }
.cal-quick-flag:hover { background: var(--paper); }
.cal-quick-flag.is-high { color: var(--priority-high, #e34d59); }
.cal-quick-flag.is-medium { color: var(--priority-medium, #e69b2d); }
.cal-quick-flag.is-low { color: var(--priority-low, #4d78db); }

.cal-quick-title { width: 100%; min-height: 34px; margin-top: 6px; padding: 0; border: 0; background: transparent; color: var(--ink); font: inherit; font-size: 20px; font-weight: 600; line-height: 1.45; outline: none; box-shadow: none; resize: none; overflow: hidden; }
.cal-quick-title::placeholder { color: var(--muted); font-weight: 500; }
.cal-quick-desc-row { display: flex; align-items: flex-start; gap: 8px; }
.cal-quick-desc-row :deep(.task-md-editor) { flex: 1; min-width: 0; }
.cal-quick-desc-row :deep(.task-md-input) { min-height: 22px; padding: 2px 0; border: 0; background: transparent; font-size: 13.5px; resize: none; overflow: hidden; }
.cal-quick-icon-btn { display: grid; flex: 0 0 auto; place-items: center; width: 28px; height: 26px; margin-top: 2px; border: 0; border-radius: 6px; background: transparent; color: var(--muted); cursor: pointer; }
.cal-quick-icon-btn:hover, .cal-quick-icon-btn.on { background: var(--paper); color: var(--ink); }

.cal-quick-checklist { display: flex; flex-direction: column; gap: 2px; margin: 4px 0 2px; }
.cal-quick-check-row { display: flex; align-items: center; gap: 7px; }
.cal-quick-check-box { display: grid; flex: 0 0 auto; place-items: center; width: 14px; height: 14px; padding: 0; border: 1.4px solid color-mix(in srgb, var(--ink) 30%, transparent); border-radius: 4px; background: transparent; color: #fff; cursor: pointer; }
.cal-quick-check-box.done { border-color: var(--accent); background: var(--accent); }
.cal-quick-check-row input { flex: 1; min-width: 0; padding: 4px 0; border: 0; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; outline: none; }
.cal-quick-check-remove { display: grid; place-items: center; width: 18px; height: 18px; border: 0; border-radius: 4px; background: transparent; color: var(--muted); cursor: pointer; opacity: 0; }
.cal-quick-check-row:hover .cal-quick-check-remove { opacity: 1; }
.cal-quick-check-add { display: inline-flex; align-items: center; gap: 4px; align-self: flex-start; padding: 4px 2px; border: 0; background: transparent; color: var(--muted); font: inherit; font-size: 12.5px; cursor: pointer; }
.cal-quick-check-add:hover { color: var(--accent); }

.cal-quick-tags { display: flex; flex-wrap: wrap; gap: 5px; padding: 2px 0 4px; }
.cal-quick-tag { display: inline-flex; align-items: center; gap: 2px; height: 21px; padding: 0 3px 0 8px; border: 1px solid currentColor; border-radius: 11px; font-size: 11px; }
.cal-quick-tag button { display: grid; place-items: center; width: 14px; height: 14px; padding: 0; border: 0; border-radius: 50%; background: transparent; color: inherit; cursor: pointer; opacity: .7; }

.cal-quick-foot { position: relative; display: flex; align-items: center; gap: 8px; margin-top: auto; padding-top: 8px; }
.cal-quick-grow { flex: 1; }
.cal-quick-list { display: inline-flex; max-width: 200px; align-items: center; gap: 6px; min-height: 28px; padding: 0 6px; border: 0; border-radius: 7px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer; overflow: hidden; white-space: nowrap; }
.cal-quick-list span { overflow: hidden; text-overflow: ellipsis; }
.cal-quick-list:hover { background: var(--paper); color: var(--ink); }
.cal-quick-more { display: grid; place-items: center; width: 28px; height: 28px; border: 0; border-radius: 6px; background: transparent; color: var(--ink2); cursor: pointer; }
.cal-quick-more:hover { background: var(--paper); }
.cal-quick-save { height: 28px; padding: 0 14px; border: 1px solid var(--accent); border-radius: 6px; background: var(--accent); color: var(--accent-contrast, #fff); font: inherit; font-size: 12.5px; cursor: pointer; }
.cal-quick-save:disabled { opacity: .5; }

.cal-quick-menu { position: absolute; z-index: 3; display: flex; min-width: 196px; max-height: 300px; flex-direction: column; gap: 1px; overflow: auto; padding: 6px; border: 1px solid var(--line2); border-radius: 10px; background: var(--card); box-shadow: 0 18px 40px rgb(15 23 42 / 20%); }
.cal-quick-menu.is-priority { top: 32px; right: 0; }
.cal-quick-menu.is-list { bottom: 40px; left: 0; width: min(300px, calc(100vw - 48px)); }
.cal-quick-menu.is-more { right: 0; bottom: 40px; min-width: 168px; }
.cal-quick-menu.is-tags { right: -4px; bottom: 40px; width: min(320px, calc(100vw - 48px)); }
/* 卡片靠近视口顶部时改向下展开，避免浮层顶出屏幕 */
.cal-quick-menu.is-list.is-drop,
.cal-quick-menu.is-more.is-drop,
.cal-quick-menu.is-tags.is-drop { top: calc(100% + 4px); bottom: auto; }
.cal-quick-menu button { display: flex; align-items: center; gap: 8px; min-height: 32px; padding: 0 8px; border: 0; border-radius: 6px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; text-align: left; cursor: pointer; }
.cal-quick-menu button .cal-quick-label { flex: 1; min-width: 0; overflow: hidden; text-align: left; text-overflow: ellipsis; white-space: nowrap; }
/* 图标解析不到时会退化成 <span>，不能让它跟着伸展，否则文字被挤到中间 */
.cal-quick-menu button > span:not(.cal-quick-label) { flex: 0 0 auto; }
.cal-quick-menu button em { color: var(--muted); font-size: 11px; font-style: normal; }
.cal-quick-menu button:hover { background: var(--paper); color: var(--ink); }
.cal-quick-menu button.on { color: var(--accent); }
.cal-quick-menu button.is-high svg { color: var(--priority-high, #e34d59); }
.cal-quick-menu button.is-medium svg { color: var(--priority-medium, #e69b2d); }
.cal-quick-menu button.is-low svg { color: var(--priority-low, #4d78db); }
.cal-quick-menu button.cal-quick-create-tag { margin-bottom: 2px; color: var(--accent); }
.cal-quick-menu button.cal-quick-create-tag:disabled { opacity: .6; cursor: default; }
.cal-quick-search { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; padding: 6px 8px; border-bottom: 1px solid var(--line); color: var(--muted); }
.cal-quick-search input { flex: 1; min-width: 0; padding: 2px 0; border: 0; background: transparent; color: var(--ink); font: inherit; font-size: 12.5px; outline: none; }
.cal-quick-tag-list { display: flex; flex-direction: column; gap: 1px; max-height: 220px; overflow: auto; }
.cal-quick-panel-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 6px; }
.cal-quick-panel-actions button { min-height: 30px; padding: 0 14px; border: 1px solid var(--line); border-radius: 6px; background: transparent; color: var(--ink2); font-size: 12.5px; cursor: pointer; }
.cal-quick-panel-actions button.is-primary { border-color: var(--accent); background: var(--accent); color: var(--accent-contrast, #fff); }
.cal-quick-empty { margin: 6px 8px; color: var(--muted); font-size: 12px; }

.cal-quick-date-rows { display: grid; gap: 6px; padding-top: 8px; border-top: 1px solid var(--line); }
.cal-quick-date-row { display: flex; align-items: center; gap: 8px; min-height: 30px; color: var(--ink2); font-size: 12px; }
.cal-quick-date-row span { flex: 1; }
.cal-quick-date-row select { width: 128px; min-height: 28px; padding: 0 4px; border: 1px solid var(--line); border-radius: 6px; background: var(--surface); color: var(--ink); font: inherit; font-size: 12px; }
.cal-quick-date-row input { width: 104px; min-height: 28px; padding: 0 6px; border: 1px solid var(--line); border-radius: 6px; background: var(--surface); color: var(--ink); font: inherit; font-size: 12px; }
.cal-quick-date-row.is-switch { justify-content: space-between; }
.cal-quick-date-row.is-switch button { position: relative; width: 38px; height: 21px; border: 0; border-radius: 11px; background: var(--line2); cursor: pointer; }
.cal-quick-date-row.is-switch button.on { background: var(--accent); }
.cal-quick-date-row.is-switch button i { position: absolute; top: 2px; left: 2px; width: 17px; height: 17px; border-radius: 50%; background: #fff; transition: transform .15s ease; }
.cal-quick-date-row.is-switch button.on i { transform: translateX(17px); }
.cal-quick-date-hint { margin: 0; color: var(--muted); font-size: 11.5px; }
.cal-quick-date-popover :deep(.task-date-time-popover) { width: 100%; box-shadow: 0 18px 40px rgb(15 23 42 / 22%); }
</style>
