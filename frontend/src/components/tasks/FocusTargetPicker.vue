<template>
  <div class="focus-picker" :class="{ 'is-inline': inline }" @click.stop>
    <div class="focus-picker-tabs" role="tablist" aria-label="选择专注对象">
      <button v-for="tab in TABS" :key="tab.id" type="button" role="tab" :class="{ on: tabId === tab.id }" :aria-selected="tabId === tab.id" @click="tabId = tab.id">{{ tab.label }}</button>
    </div>
    <label class="focus-picker-search">
      <Search :size="15" />
      <input v-model.trim="query" type="search" placeholder="搜索" />
    </label>
    <div class="focus-picker-body">
      <template v-if="tabId === 'habit'">
        <h4 class="focus-picker-group"><Repeat :size="13" />习惯</h4>
        <ul class="focus-picker-list">
          <li v-for="habit in habitRows" :key="habit.publicId">
            <button type="button" @click="emit('pick', { kind: 'habit', id: habit.publicId, title: habit.title, icon: habit.icon || '🔁' })">
              <span class="focus-picker-icon">{{ habit.icon || '🔁' }}</span>
              <span class="focus-picker-title">{{ habit.title }}</span>
            </button>
          </li>
        </ul>
        <p v-if="!habitRows.length" class="focus-picker-empty">{{ query ? '没有匹配的习惯' : '还没有习惯' }}</p>
      </template>

      <template v-else>
        <section v-for="group in groups" :key="group.key">
          <h4 class="focus-picker-group">
            <component :is="group.icon" :size="13" />
            {{ group.label }}
            <button type="button" class="focus-picker-toggle" :aria-expanded="!isCollapsed(group.key)" @click="toggleGroup(group.key)">
              <ChevronRight :size="13" :class="{ 'is-open': !isCollapsed(group.key) }" />
            </button>
          </h4>
          <ul v-if="!isCollapsed(group.key)" class="focus-picker-list">
            <li v-for="item in group.items" :key="item.key" :class="{ 'is-child': item.isChild }">
              <button type="button" @click="emit('pick', item)">
                <span v-if="item.icon" class="focus-picker-icon">{{ item.icon }}</span>
                <span v-else class="focus-picker-dot" :style="{ borderColor: item.color }"></span>
                <span class="focus-picker-title">{{ item.title }}</span>
                <span class="focus-picker-when">{{ item.whenLabel }}</span>
                <Check v-if="selectedId && item.id === selectedId" :size="13" class="focus-picker-check" />
              </button>
            </li>
          </ul>
        </section>
        <p v-if="!groups.length" class="focus-picker-empty">{{ query ? '没有匹配的结果' : '还没有可专注的任务' }}</p>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { CalendarDays, Check, ChevronRight, History, ListTodo, Repeat, Search, Sparkles } from 'lucide-vue-next'
import { useTaskFocusStore } from '../../stores/taskFocus.js'
import { useTasksStore } from '../../stores/tasks.js'
import { PRIORITY_COLORS } from '../../views/calendar/calendarColors.js'

const props = defineProps({
  selectedId: { type: String, default: '' },
  /** 弹窗内嵌使用时去掉浮层定位与阴影 */
  inline: { type: Boolean, default: false },
  defaultTab: { type: String, default: 'task' }
})
const emit = defineEmits(['pick'])

const focus = useTaskFocusStore()
const tasksStore = useTasksStore()

const TABS = [
  { id: 'recent', label: '最近' },
  { id: 'task', label: '任务' },
  { id: 'habit', label: '习惯' }
]
const DATE_GROUPS = [
  { key: 'today', label: '今天', icon: CalendarDays },
  { key: 'tomorrow', label: '明天', icon: CalendarDays },
  { key: 'week', label: '未来 7 天', icon: CalendarDays },
  { key: 'later', label: '以后', icon: CalendarDays },
  { key: 'none', label: '无日期', icon: ListTodo }
]

const tabId = ref(props.defaultTab)
const query = ref('')
const collapsedGroups = ref([])

function priorityColor(task) {
  return PRIORITY_COLORS[task?.priority] || PRIORITY_COLORS.NONE
}

function startOfDay(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
}

function dueBucket(task) {
  if (!task.dueAt) return 'none'
  const days = Math.round((startOfDay(new Date(task.dueAt)) - startOfDay(new Date())) / 86400000)
  if (days <= 0) return 'today'
  if (days === 1) return 'tomorrow'
  if (days <= 7) return 'week'
  return 'later'
}

function whenLabel(task) {
  if (!task.dueAt) return ''
  const due = new Date(task.dueAt)
  // 只有带具体时间的任务才显示 HH:mm，全天任务显示日期短语
  if (!task.allDay && (due.getHours() || due.getMinutes())) {
    return `${String(due.getHours()).padStart(2, '0')}:${String(due.getMinutes()).padStart(2, '0')}`
  }
  const days = Math.round((startOfDay(due) - startOfDay(new Date())) / 86400000)
  if (days <= 0) return '今天'
  if (days === 1) return '明天'
  return `${due.getMonth() + 1}月${due.getDate()}日`
}

/** 按 parentId 建树：父任务不在集合里的（父任务已完成等）当根处理，避免子任务丢失 */
function buildTree(items) {
  const byId = new Map(items.map(item => [item.publicId, item]))
  const childrenOf = new Map()
  for (const item of items) {
    const key = item.parentId && byId.has(item.parentId) ? item.parentId : ''
    if (!childrenOf.has(key)) childrenOf.set(key, [])
    childrenOf.get(key).push(item)
  }
  return { roots: childrenOf.get('') || [], childrenOf }
}

/** 深度优先摊平：只对根任务分日期桶，子任务永远跟在父任务下面 */
function flattenTree(roots, childrenOf, groupKey) {
  const rows = []
  const walk = (task, depth) => {
    rows.push({
      key: `${groupKey}-${task.publicId}`,
      kind: 'task',
      id: task.publicId,
      title: task.title,
      whenLabel: whenLabel(task),
      color: priorityColor(task),
      isChild: depth > 0
    })
    for (const child of childrenOf.get(task.publicId) || []) walk(child, depth + 1)
  }
  for (const root of roots) walk(root, 0)
  return rows
}

const pickerTasks = computed(() => tasksStore.tasks.filter(task => task.status === 'OPEN' && !task.deleted))

const habitRows = computed(() => {
  const value = query.value.toLowerCase()
  return tasksStore.habits
    .filter(habit => !habit.deleted && (!value || String(habit.title || '').toLowerCase().includes(value)))
    .slice(0, 80)
})

const groups = computed(() => {
  const value = query.value.toLowerCase()
  const match = text => !value || String(text || '').toLowerCase().includes(value)

  if (tabId.value === 'recent') {
    const result = []
    const presets = focus.presets.filter(preset => match(preset.name))
    if (presets.length) {
      result.push({
        key: 'presets',
        label: '常用专注',
        icon: Sparkles,
        items: presets.map(preset => ({
          key: `preset-${preset.id}`,
          kind: 'preset',
          id: preset.id,
          title: preset.name,
          whenLabel: preset.mode === 'STOPWATCH' ? '正计时' : `${preset.minutes} 分钟`,
          color: 'var(--muted)',
          isChild: false
        }))
      })
    }
    const seen = new Set()
    const rows = []
    for (const session of focus.sessions) {
      if (!session.taskId || seen.has(session.taskId)) continue
      seen.add(session.taskId)
      const task = pickerTasks.value.find(item => item.publicId === session.taskId)
      const title = task?.title || session.taskTitle
      if (!title || !match(title)) continue
      rows.push({
        key: `recent-${session.taskId}`,
        kind: 'task',
        id: session.taskId,
        title,
        whenLabel: '最近',
        color: priorityColor(task),
        isChild: false
      })
      if (rows.length >= 8) break
    }
    if (rows.length) result.push({ key: 'recent', label: '最近专注', icon: History, items: rows })
    return result
  }

  const { roots, childrenOf } = buildTree(pickerTasks.value.filter(task => match(task.title)))
  const buckets = new Map()
  for (const task of roots) {
    const key = dueBucket(task)
    if (!buckets.has(key)) buckets.set(key, [])
    buckets.get(key).push(task)
  }
  const result = DATE_GROUPS
    .filter(group => buckets.get(group.key)?.length)
    .map(group => {
      const sorted = [...buckets.get(group.key)].sort((a, b) => {
        const left = a.dueAt ? new Date(a.dueAt).getTime() : Number.MAX_SAFE_INTEGER
        const right = b.dueAt ? new Date(b.dueAt).getTime() : Number.MAX_SAFE_INTEGER
        return left - right || Number(a.sortOrder || 0) - Number(b.sortOrder || 0)
      })
      return { key: group.key, label: group.label, icon: group.icon, items: flattenTree(sorted, childrenOf, group.key) }
    })
  // 参考图里「任务」页签末尾还挂一个习惯分组，习惯也能作为专注目标
  if (habitRows.value.length) {
    result.push({
      key: 'habits',
      label: '习惯',
      icon: Repeat,
      items: habitRows.value.map(habit => ({
        key: `habit-${habit.publicId}`,
        kind: 'habit',
        id: habit.publicId,
        title: habit.title,
        icon: habit.icon || '🔁',
        whenLabel: '',
        color: 'transparent',
        isChild: false
      }))
    })
  }
  return result
})

function isCollapsed(key) {
  return collapsedGroups.value.includes(key)
}

function toggleGroup(key) {
  collapsedGroups.value = isCollapsed(key)
    ? collapsedGroups.value.filter(item => item !== key)
    : [...collapsedGroups.value, key]
}
</script>

<style scoped>
/* 任务选择器：页签 + 搜索 + 按日期分组的任务树 */
.focus-picker { position: absolute; z-index: 45; top: 34px; left: 50%; display: flex; width: min(380px, 84vw); max-height: min(560px, 72vh); flex-direction: column; padding: 14px 0 6px; border: 1px solid var(--line2); border-radius: 18px; background: var(--card); box-shadow: 0 26px 60px rgb(15 23 42 / 22%); transform: translateX(-50%); }
.focus-picker.is-inline { position: static; width: 100%; max-height: 320px; border: 0; border-radius: 0; box-shadow: none; transform: none; padding: 0; }
.focus-picker-tabs { display: flex; justify-content: center; gap: 6px; padding: 0 14px 12px; }
.focus-picker.is-inline .focus-picker-tabs { padding-top: 4px; }
.focus-picker-tabs button { min-height: 28px; padding: 0 14px; border: 0; border-radius: 999px; background: transparent; color: var(--muted); font: inherit; font-size: 13px; cursor: pointer; transition: background .15s ease, color .15s ease; }
.focus-picker-tabs button:hover { color: var(--ink); }
.focus-picker-tabs button.on { background: color-mix(in srgb, var(--accent) 18%, transparent); color: var(--ink); font-weight: 700; }
.focus-picker-search { display: flex; align-items: center; gap: 8px; margin: 0 14px 8px; padding: 0 10px; min-height: 36px; border-radius: 10px; background: var(--paper); color: var(--muted); }
.focus-picker.is-inline .focus-picker-search { margin: 0 0 8px; }
.focus-picker-search input { flex: 1; min-width: 0; border: 0; background: transparent; color: var(--ink); font: inherit; font-size: 13px; outline: none; }
.focus-picker-search input::placeholder { color: var(--muted); }
.focus-picker-body { flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding: 0 8px 6px; scrollbar-width: thin; }
.focus-picker.is-inline .focus-picker-body { padding: 0 0 4px; }
.focus-picker-group { display: flex; align-items: center; gap: 6px; margin: 10px 0 4px; padding: 0 6px; color: var(--ink); font-size: 12.5px; font-weight: 700; }
.focus-picker-group > svg { box-sizing: content-box; padding: 2px; border: 1px solid var(--line); border-radius: 6px; color: var(--muted); }
.focus-picker.is-inline .focus-picker-group { padding: 0; }
.focus-picker-toggle { display: grid; width: 18px; height: 18px; margin-left: 2px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.focus-picker-toggle svg { transition: transform .15s ease; }
.focus-picker-toggle svg.is-open { transform: rotate(90deg); }
.focus-picker-list { display: grid; margin: 0; padding: 0; list-style: none; }
.focus-picker-list li button { display: flex; width: 100%; align-items: center; gap: 10px; min-height: 38px; padding: 0 10px; border: 0; border-radius: 9px; background: transparent; color: var(--ink); font: inherit; font-size: 13.5px; text-align: left; cursor: pointer; }
.focus-picker.is-inline .focus-picker-list li button { padding: 0 4px; }
.focus-picker-list li button:hover { background: var(--paper); }
.focus-picker-list li.is-child { padding-left: 26px; }
.focus-picker-dot { flex: 0 0 auto; width: 15px; height: 15px; border: 1.5px solid var(--line2); border-radius: 50%; }
.focus-picker-icon { flex: 0 0 auto; width: 16px; font-size: 14px; text-align: center; }
.focus-picker-title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.focus-picker-when { flex: 0 0 auto; color: var(--muted); font-size: 11.5px; font-variant-numeric: tabular-nums; }
.focus-picker-check { flex: 0 0 auto; color: var(--accent); }
.focus-picker-empty { margin: 22px 0; color: var(--muted); font-size: 12.5px; text-align: center; }
</style>
