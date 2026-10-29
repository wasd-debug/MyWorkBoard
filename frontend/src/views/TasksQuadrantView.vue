<template>
  <main class="quadrant-page">
    <header class="quadrant-head">
      <h1>四象限</h1>
      <div class="quadrant-head-actions">
        <button ref="pageMenuAnchor" type="button" class="qn-icon-btn" aria-label="更多" :aria-expanded="pageMenuOpen" @click.stop="pageMenuOpen = !pageMenuOpen">
          <MoreHorizontal :size="18" />
        </button>
        <div v-if="pageMenuOpen" class="qn-menu is-page" @click.stop>
          <button type="button" class="qn-menu-check" :aria-pressed="state.showCompleted" @click="toggleShowCompleted">
            <span class="qn-box" :class="{ on: state.showCompleted }"><Check v-if="state.showCompleted" :size="11" /></span>
            显示已完成
          </button>
        </div>
      </div>
    </header>

    <p v-if="error" class="qn-error">{{ error }}</p>

    <div class="quadrant-grid">
      <section v-for="quadrant in state.quadrants" :key="quadrant.key" class="quadrant-card" :aria-label="quadrant.name">
        <header class="quadrant-card-head">
          <span class="qn-dot" :style="{ background: quadrant.color }"><component :is="iconOf(quadrant.icon)" :size="12" /></span>
          <h2 :style="{ color: quadrant.color }">{{ quadrant.name }}</h2>
          <div class="qn-card-actions">
            <button type="button" class="qn-icon-btn" :aria-label="`在${quadrant.name}添加任务`" :title="`在${quadrant.name}添加任务`" @click.stop="openQuick(quadrant, $event)">
              <Plus :size="15" />
            </button>
            <button type="button" class="qn-icon-btn" :aria-label="`${quadrant.name}更多选项`" :title="`${quadrant.name}更多选项`" @click.stop="openCardMenu(quadrant, $event)">
              <MoreHorizontal :size="15" />
            </button>
          </div>
        </header>

        <div class="quadrant-body">
          <template v-if="groupsOf(quadrant).length">
            <section v-for="group in groupsOf(quadrant)" :key="group.key" class="qn-group">
              <button type="button" class="qn-group-head" :aria-expanded="!isCollapsed(quadrant.key, group.key)" @click="toggleGroup(quadrant.key, group.key)">
                <ChevronDown :size="13" class="qn-chevron" :class="{ collapsed: isCollapsed(quadrant.key, group.key) }" />
                <TaskOrganizationIcon v-if="group.icon" :value="group.icon" :size="13" :style="{ color: group.color || 'var(--ink2)' }" />
                <i v-else-if="group.color" class="qn-dot-sm" :style="{ background: group.color }"></i>
                <span>{{ group.label }}</span>
                <em>{{ group.tasks.length }}</em>
              </button>
              <div v-show="!isCollapsed(quadrant.key, group.key)" class="qn-rows">
                <div
                  v-for="task in group.tasks"
                  :key="task.publicId"
                  class="qn-row"
                  :class="{ 'is-done': task.status === 'COMPLETED' }"
                  role="button"
                  tabindex="0"
                  @click="openTask(task)"
                  @keydown.enter.prevent="openTask(task)"
                >
                  <button
                    type="button"
                    class="qn-check"
                    :class="{ done: task.status === 'COMPLETED' }"
                    :aria-label="task.status === 'COMPLETED' ? '标记未完成' : '标记完成'"
                    :aria-pressed="task.status === 'COMPLETED'"
                    @click.stop="toggleTask(task)"
                  >
                    <Check v-if="task.status === 'COMPLETED'" :size="10" />
                  </button>
                  <span class="qn-title">{{ task.title }}</span>
                  <span class="qn-meta">
                    <span v-for="tag in tagsOf(task)" :key="tag.publicId" class="qn-chip is-tag" :style="{ '--chip': tag.color || 'var(--ink2)' }">{{ tag.name }}</span>
                    <span class="qn-chip is-list"><TaskOrganizationIcon :value="listOf(task)?.icon" :size="11" />{{ listOf(task)?.name || '收集箱' }}</span>
                    <span v-if="dueChip(task)" class="qn-chip is-date" :class="{ overdue: dueChip(task).overdue }"><Clock3 :size="11" />{{ dueChip(task).label }}</span>
                  </span>
                </div>
              </div>
            </section>
          </template>
          <p v-else class="qn-empty">没有任务</p>
        </div>
      </section>
    </div>

    <Teleport to="body">
      <div v-if="cardMenu.open" class="qn-menu is-card" :class="{ 'is-flip': cardMenu.flip }" :style="cardMenuStyle" @click.stop>
        <button type="button" @click="openEdit(cardMenu.quadrant)"><Pencil :size="15" />编辑</button>
        <button type="button" @click="openSubmenu('group')"><Columns3 :size="15" />分组<em>{{ groupLabel(cardMenu.quadrant) }}</em><ChevronRight :size="13" class="qn-chevron-right" /></button>
        <button type="button" @click="openSubmenu('sort')"><ArrowDownWideNarrow :size="15" />排序<em>{{ sortLabel(cardMenu.quadrant) }}</em><ChevronRight :size="13" class="qn-chevron-right" /></button>
        <button type="button" @click="openSubmenu('order')"><ArrowUpDown :size="15" />顺序<em>{{ orderLabel(cardMenu.quadrant) }}</em><ChevronRight :size="13" class="qn-chevron-right" /></button>

        <div v-if="submenu" class="qn-submenu">
          <button
            v-for="option in submenuOptions"
            :key="option.value"
            type="button"
            :class="{ on: currentValue(cardMenu.quadrant) === option.value }"
            @click="chooseOption(option.value)"
          >
            <span>{{ option.label }}</span><Check v-if="currentValue(cardMenu.quadrant) === option.value" :size="13" />
          </button>
        </div>
      </div>
    </Teleport>

    <TaskQuickAdd
      :open="quick.open"
      :style="quickStyle"
      :date-label="quickDateLabel"
      :due-at="quick.dueAt"
      :all-day="quick.allDay"
      :title="quick.title"
      :description="quick.description"
      :priority="quick.priority"
      :list-id="quick.listId"
      :tag-ids="quick.tagIds"
      :checklist="quick.checklist"
      :lists="lists"
      :tags="tags"
      :saving="quick.saving"
      :create-tag="quickCreateTag"
      :reminder-minutes="quick.reminderMinutes"
      :rrule="quick.rrule"
      @update:title="quick.title = $event"
      @update:description="quick.description = $event"
      @update:priority="quick.priority = $event"
      @update:list-id="quick.listId = $event"
      @update:tag-ids="quick.tagIds = $event"
      @update:checklist="quick.checklist = $event"
      @update:due-at="quick.dueAt = $event"
      @update:all-day="quick.allDay = $event"
      @update:reminder-minutes="quick.reminderMinutes = $event"
      @update:rrule="quick.rrule = $event"
      @save="submitQuick"
      @close="closeQuick"
    />

    <QuadrantEditDialog
      :open="editOpen"
      :quadrant="editing"
      :lists="lists"
      :tags="tags"
      @update:open="editOpen = $event"
      @save="saveQuadrant"
      @reset="resetQuadrant"
    />
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDownWideNarrow, ArrowUpDown, CalendarDays, Check, ChevronDown, ChevronRight, Clock3, Columns3, Flag, MoreHorizontal, Pencil, Plus } from 'lucide-vue-next'
import TaskOrganizationIcon from '../components/tasks/TaskOrganizationIcon.vue'
import QuadrantEditDialog from '../components/tasks/QuadrantEditDialog.vue'
import TaskQuickAdd from '../components/tasks/TaskQuickAdd.vue'
import { useAppStore } from '../stores/app.js'
import { useTasksStore } from '../stores/tasks.js'
import { apiCreateTaskReminder } from '../../packages/api-client/src/index.js'
import {
  GROUP_OPTIONS, ORDER_OPTIONS, PRIORITY_OPTIONS, SORT_OPTIONS, buildQuadrantGroups, dateBucket,
  defaultQuadrants, loadState, saveState, splitByQuadrant
} from './taskQuadrant.js'
import { resolveQuadrantIcon } from './taskQuadrantIcons.js'
import { useExclusivePanels } from '../utils/exclusivePanels.js'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const tasksStore = useTasksStore()

const scope = computed(() => `local:${appStore.authUser?.id || 'local'}`)
const state = reactive(loadState(scope.value))
const now = ref(new Date())
const error = ref('')
const pageMenuOpen = ref(false)
const cardMenu = reactive({ open: false, quadrant: null, x: 0, y: 0, flip: false })
const submenu = ref('')
const editOpen = ref(false)
const editing = ref(null)
// 复用日历那套快速添加卡片（components/tasks/TaskQuickAdd.vue）
const quick = reactive({ open: false, quadrant: null, title: '', description: '', priority: 'NONE', listId: '', tagIds: [], checklist: [], dueAt: '', allDay: false, reminderMinutes: null, rrule: '', saving: false, x: 0, y: 0 })
const pageMenuAnchor = ref(null)

const tasks = computed(() => tasksStore.tasks.filter(item => !item.deleted))
const lists = computed(() => tasksStore.lists.filter(item => !item.archived))
const tags = computed(() => tasksStore.tags)
const split = computed(() => splitByQuadrant(tasks.value, state.quadrants, { now: now.value, showCompleted: state.showCompleted }))

function tasksOf(key) {
  return split.value[key] || []
}

function groupsOf(quadrant) {
  return buildQuadrantGroups(tasksOf(quadrant.key), quadrant, { lists: lists.value, tags: tags.value, now: now.value })
}

function iconOf(value) {
  return resolveQuadrantIcon(value)
}

function listOf(task) {
  return lists.value.find(item => item.publicId === task.listId)
}

function tagsOf(task) {
  return (task.tagIds || []).map(id => tags.value.find(item => item.publicId === id)).filter(Boolean).slice(0, 2)
}

function dueChip(task) {
  if (!task.dueAt) return null
  const due = new Date(task.dueAt)
  if (Number.isNaN(due.getTime())) return null
  const bucket = dateBucket(task, now.value)
  const clock = `${String(due.getHours()).padStart(2, '0')}:${String(due.getMinutes()).padStart(2, '0')}`
  if (bucket === 'TODAY') return { label: task.allDay ? '今天' : `今天 ${clock}`, overdue: false }
  if (bucket === 'TOMORROW') return { label: task.allDay ? '明天' : `明天 ${clock}`, overdue: false }
  if (bucket === 'OVERDUE') return { label: task.allDay ? '已过期' : `${due.getMonth() + 1}月${due.getDate()}日`, overdue: true }
  return { label: task.allDay ? `${due.getMonth() + 1}月${due.getDate()}日` : `${due.getMonth() + 1}月${due.getDate()}日 ${clock}`, overdue: false }
}

function isCollapsed(quadrantKey, groupKey) {
  return Boolean(state.collapsed[`${quadrantKey}:${groupKey}`])
}

function toggleGroup(quadrantKey, groupKey) {
  const key = `${quadrantKey}:${groupKey}`
  state.collapsed[key] = !state.collapsed[key]
}

function toggleShowCompleted() {
  state.showCompleted = !state.showCompleted
  pageMenuOpen.value = false
}

async function toggleTask(task) {
  try {
    if (task.status === 'COMPLETED') await tasksStore.reopen(task)
    else await tasksStore.complete(task)
  } catch (cause) {
    error.value = cause?.response?.data?.detail || cause?.message || '状态更新失败'
  }
}

function openTask(task) {
  void router.push({ path: '/tasks/all', query: { task: task.publicId } })
}

function addDaysTo(base, days) {
  const date = new Date(base.getFullYear(), base.getMonth(), base.getDate())
  date.setDate(date.getDate() + days)
  return date
}

function dateKeyOf(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function quickDateFor(key) {
  const today = new Date(now.value)
  if (key === 'TODAY') return new Date(today.getFullYear(), today.getMonth(), today.getDate(), 18, 0, 0)
  if (key === 'TOMORROW') return addDaysTo(today, 1).getTime() ? new Date(addDaysTo(today, 1).setHours(9, 0, 0, 0)) : null
  if (key === 'AFTER_TOMORROW') return new Date(addDaysTo(today, 2).setHours(9, 0, 0, 0))
  if (key === 'NEXT_WEEK') return new Date(addDaysTo(today, 7).setHours(9, 0, 0, 0))
  return null
}

const quickDateLabel = computed(() => {
  if (!quick.dueAt) return '无日期'
  const due = new Date(quick.dueAt)
  if (Number.isNaN(due.getTime())) return '无日期'
  const todayKey = dateKeyOf(new Date(now.value))
  const tomorrowKey = dateKeyOf(addDaysTo(new Date(now.value), 1))
  const key = dateKeyOf(due)
  const prefix = key === todayKey ? '今天' : key === tomorrowKey ? '明天' : `周${['日', '一', '二', '三', '四', '五', '六'][due.getDay()]}`
  const base = `${prefix}，${due.getMonth() + 1}月${due.getDate()}日`
  if (quick.allDay) return base
  return `${base}，${String(due.getHours()).padStart(2, '0')}:${String(due.getMinutes()).padStart(2, '0')}`
})

const quickStyle = computed(() => {
  const width = Math.min(420, window.innerWidth - 32)
  return { position: 'fixed', left: `${quick.x}px`, top: `${quick.y}px`, width: `${width}px`, zIndex: 1300 }
})

async function openQuick(quadrant, event) {
  const rect = event?.currentTarget?.getBoundingClientRect?.()
  const defaults = defaultsFor(quadrant)
  const width = Math.min(420, window.innerWidth - 32)
  Object.assign(quick, {
    open: true,
    quadrant,
    title: '',
    description: '',
    tagIds: [...(defaults.tagIds || [])],
    checklist: [],
    dueAt: defaults.dueAt || '',
    allDay: false,
    priority: defaults.priority || 'NONE',
    reminderMinutes: null,
    rrule: '',
    listId: defaults.listId || tasksStore.inbox?.publicId || '',
    x: rect ? Math.max(12, Math.min(rect.left - 8, window.innerWidth - width - 12)) : 40,
    y: rect ? Math.min(rect.bottom + 6, window.innerHeight - 240) : 120
  })
  cardMenu.open = false
  pageMenuOpen.value = false
}

function closeQuick() {
  quick.open = false
}

/** 新任务按象限规则给默认值，保证建完就落在这一格 */
function defaultsFor(quadrant) {
  const rule = quadrant.rule || {}
  const priority = rule.priorities?.length ? rule.priorities[0] : 'NONE'
  const listId = rule.listIds?.length ? rule.listIds[0] : tasksStore.inbox?.publicId
  let dateKey = 'NONE'
  if (rule.dates?.includes('OVERDUE') || rule.dates?.includes('TODAY')) dateKey = 'TODAY'
  else if (rule.dates?.includes('TOMORROW')) dateKey = 'TOMORROW'
  else if (rule.dates?.includes('THIS_WEEK') || rule.dates?.includes('LATER')) dateKey = 'NEXT_WEEK'
  const due = quickDateFor(dateKey)
  return { listId, priority, dateKey, dueAt: due ? due.toISOString() : '', tagIds: (rule.tagIds || []).slice(0, 1) }
}

async function submitQuick() {
  const title = quick.title.trim()
  if (!title || !quick.quadrant) return
  const defaults = defaultsFor(quick.quadrant)
  const due = quick.dueAt ? new Date(quick.dueAt) : null
  quick.saving = true
  try {
    const created = await tasksStore.create({
      ...defaults,
      title,
      description: quick.description,
      allDay: quick.allDay,
      startAt: due ? new Date(due.getTime() - 30 * 60000).toISOString() : '',
      dueAt: due ? due.toISOString() : '',
      priority: quick.priority,
      listId: quick.listId || defaults.listId,
      tagIds: [...quick.tagIds],
      rrule: quick.rrule || '',
      checklist: quick.checklist.filter(item => item.title.trim()).map(item => ({ title: item.title.trim(), completed: item.completed }))
    })
    // 提醒是独立资源，且要等任务同步到服务端才有 publicId 可用
    const publicId = created?.publicId || created?.id
    const reminderMinutes = quick.reminderMinutes
    if (publicId && due && reminderMinutes !== null && reminderMinutes !== undefined) {
      void (async () => {
        try {
          await tasksStore.syncNow()
          await apiCreateTaskReminder(publicId, {
            kind: 'RELATIVE', offsetMinutes: Number(reminderMinutes), remindAt: null, channel: 'IN_APP', dailyUntilDone: false
          })
        } catch { /* 提醒失败不阻塞任务创建 */ }
      })()
    }
    quick.open = false
  } catch (cause) {
    error.value = cause?.response?.data?.detail || cause?.message || '创建任务失败'
  } finally {
    quick.saving = false
  }
}

function openCardMenu(quadrant, event) {
  const rect = event?.currentTarget?.getBoundingClientRect?.()
  cardMenu.quadrant = quadrant
  const menuWidth = 208
  const submenuWidth = 176
  const right = rect ? rect.right : window.innerWidth - 40
  // 没有空间再往右展开子菜单时，整个菜单改成贴右侧、子菜单向左展开
  const flip = right + submenuWidth + 12 > window.innerWidth
  cardMenu.flip = flip
  cardMenu.x = rect ? Math.max(12, Math.min(right - menuWidth, window.innerWidth - menuWidth - 12)) : 40
  cardMenu.y = rect ? Math.min(rect.bottom + 6, window.innerHeight - 260) : 80
  cardMenu.open = true
  submenu.value = ''
  pageMenuOpen.value = false
}

function openSubmenu(name) {
  submenu.value = submenu.value === name ? '' : name
}

const submenuOptions = computed(() => {
  if (submenu.value === 'group') return GROUP_OPTIONS
  if (submenu.value === 'sort') return SORT_OPTIONS
  if (submenu.value === 'order') return ORDER_OPTIONS
  return []
})

function groupLabel(quadrant) {
  return GROUP_OPTIONS.find(item => item.value === quadrant.groupBy)?.label || '清单'
}
function sortLabel(quadrant) {
  return SORT_OPTIONS.find(item => item.value === quadrant.sortBy)?.label || '时间'
}
function orderLabel(quadrant) {
  return ORDER_OPTIONS.find(item => item.value === quadrant.order)?.label || '从旧到新'
}

function currentValue(quadrant) {
  if (!quadrant) return ''
  if (submenu.value === 'group') return quadrant.groupBy
  if (submenu.value === 'sort') return quadrant.sortBy
  if (submenu.value === 'order') return quadrant.order
  return ''
}

function chooseOption(value) {
  const quadrant = cardMenu.quadrant
  if (!quadrant) return
  if (submenu.value === 'group') quadrant.groupBy = value
  else if (submenu.value === 'sort') quadrant.sortBy = value
  else if (submenu.value === 'order') quadrant.order = value
  cardMenu.open = false
  submenu.value = ''
}

function openEdit(quadrant) {
  editing.value = JSON.parse(JSON.stringify(quadrant))
  editOpen.value = true
  cardMenu.open = false
}

function saveQuadrant(next) {
  const index = state.quadrants.findIndex(item => item.key === next.key)
  if (index < 0) return
  state.quadrants[index] = { ...state.quadrants[index], ...next }
  editOpen.value = false
}

function resetQuadrant(key) {
  const fallback = defaultQuadrants().find(item => item.key === key)
  const index = state.quadrants.findIndex(item => item.key === key)
  if (index < 0 || !fallback) return
  state.quadrants[index] = fallback
  editOpen.value = false
}

const cardMenuStyle = computed(() => ({ position: 'fixed', left: `${cardMenu.x}px`, top: `${cardMenu.y}px`, zIndex: 1200 }))

// 页面上的菜单互斥：页面菜单、象限菜单同时只开一个；快速添加卡片里开浮层时这里也会收到通知
useExclusivePanels('quadrant', [
  ['page', () => pageMenuOpen.value, value => (pageMenuOpen.value = value)],
  ['card', () => cardMenu.open, value => (cardMenu.open = value)]
])

const TAG_COLORS = ['#4d78db', '#e34d59', '#e69b2d', '#2fa36b', '#8a63d2', '#12a4b5']

/** 快速添加卡片里现场新建标签 */
async function quickCreateTag(name) {
  const color = TAG_COLORS[tasksStore.tags.length % TAG_COLORS.length]
  const created = await tasksStore.createTag({ name, color, icon: null, parentId: null, sortOrder: tasksStore.tags.length })
  const id = created?.publicId || created?.id
  return { publicId: id, name, color }
}

function closeMenus(event) {
  if (event?.target?.closest?.('.qn-menu') || event?.target?.closest?.('.qn-icon-btn') || event?.target?.closest?.('.cal-quick') || event?.target?.closest?.('.cal-quick-date-popover')) return
  cardMenu.open = false
  pageMenuOpen.value = false
  closeQuick()
}

watch(state, value => saveState(scope.value, value), { deep: true })
watch(scope, () => {
  Object.assign(state, loadState(scope.value))
})
watch(() => route.name, () => {
  cardMenu.open = false
  pageMenuOpen.value = false
})

onMounted(async () => {
  now.value = new Date()
  window.addEventListener('pointerdown', closeMenus)
  try {
    await tasksStore.init()
  } catch (cause) {
    error.value = cause?.message || '任务加载失败'
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('pointerdown', closeMenus)
})
</script>

<style scoped>
:global(.ui-dialog.quadrant-dialog) { width: min(620px, calc(100vw - 32px)); max-width: min(620px, calc(100vw - 32px)); }
:global(.quadrant-dialog .ui-dialog-body) { padding-top: 4px; }
.quadrant-page { display: flex; flex-direction: column; gap: 10px; height: calc(100dvh - 88px); padding: 16px 18px 18px; box-sizing: border-box; }
.quadrant-head { display: flex; align-items: center; justify-content: space-between; }
.quadrant-head h1 { margin: 0; color: var(--ink); font-size: 19px; font-weight: 650; }
.quadrant-head-actions { position: relative; }
.qn-icon-btn { display: grid; place-items: center; width: 26px; height: 26px; padding: 0; border: 0; border-radius: 6px; background: transparent; color: var(--muted); cursor: pointer; }
.qn-icon-btn:hover { background: var(--paper); color: var(--ink); }
.qn-error { margin: 0; padding: 8px 10px; border-left: 3px solid var(--down, #e34d59); background: color-mix(in srgb, var(--down, #e34d59) 8%, transparent); color: var(--ink2); font-size: 12.5px; }

.quadrant-grid { display: grid; flex: 1 1 auto; grid-template-columns: repeat(2, minmax(0, 1fr)); grid-template-rows: repeat(2, minmax(0, 1fr)); gap: 12px; min-height: 0; }
.quadrant-card { display: flex; min-width: 0; min-height: 0; flex-direction: column; padding: 12px 10px 8px; border: 1px solid var(--line); border-radius: 12px; background: var(--card); }
.quadrant-card-head { display: flex; align-items: center; gap: 7px; padding: 0 4px 8px; }
.quadrant-card-head h2 { margin: 0; font-size: 13.5px; font-weight: 650; }
.qn-dot { display: grid; flex: 0 0 auto; place-items: center; width: 18px; height: 18px; border-radius: 50%; color: #fff; }
.qn-count { color: var(--muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.qn-card-actions { display: flex; gap: 2px; margin-left: auto; opacity: 0; transition: opacity .12s ease; }
.quadrant-card:hover .qn-card-actions, .quadrant-card:focus-within .qn-card-actions { opacity: 1; }


.quadrant-body { flex: 1 1 auto; min-height: 0; overflow: auto; overscroll-behavior: contain; }
.qn-group + .qn-group { margin-top: 2px; }
.qn-group-head { display: flex; width: 100%; align-items: center; gap: 6px; min-height: 30px; padding: 0 6px; border: 0; border-radius: 6px; background: transparent; color: var(--ink); font: inherit; font-size: 13px; font-weight: 600; text-align: left; cursor: pointer; }
.qn-group-head:hover { background: var(--paper); }
.qn-group-head em { color: var(--muted); font-size: 12px; font-style: normal; font-weight: 400; }
.qn-chevron { color: var(--muted); transition: transform .12s ease; }
.qn-chevron.collapsed { transform: rotate(-90deg); }
.qn-dot-sm { width: 8px; height: 8px; border-radius: 50%; }
.qn-rows { display: flex; flex-direction: column; gap: 1px; padding: 1px 0 6px; }

.qn-row { display: flex; align-items: center; gap: 8px; min-height: 34px; padding: 0 6px; border-radius: 7px; cursor: pointer; }
.qn-row:hover { background: var(--paper); }
.qn-check { display: grid; flex: 0 0 auto; place-items: center; width: 16px; height: 16px; padding: 0; border: 1.6px solid color-mix(in srgb, var(--ink) 32%, transparent); border-radius: 50%; background: transparent; color: #fff; cursor: pointer; }
.qn-check.done { border-color: var(--accent); background: var(--accent); }
.qn-title { min-width: 0; flex: 1; overflow: hidden; color: var(--ink); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.qn-row.is-done .qn-title { color: var(--muted); text-decoration: line-through; }
.qn-meta { display: flex; flex: 0 0 auto; align-items: center; gap: 5px; }
.qn-chip { display: inline-flex; align-items: center; gap: 3px; max-width: 150px; height: 20px; padding: 0 7px; border-radius: 10px; background: var(--paper); color: var(--ink2); font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.qn-chip.is-tag { background: color-mix(in srgb, var(--chip) 16%, transparent); color: var(--chip); }
.qn-chip.is-date { color: var(--muted); }
.qn-chip.is-date.overdue { color: var(--down, #e34d59); background: color-mix(in srgb, var(--down, #e34d59) 12%, transparent); }
.qn-empty { margin: 0; padding: 40px 0; color: var(--muted); font-size: 12.5px; text-align: center; }

.qn-menu { position: absolute; display: flex; min-width: 196px; flex-direction: column; gap: 1px; padding: 5px; border: 1px solid var(--line2); border-radius: 9px; background: var(--card); box-shadow: 0 16px 36px rgb(15 23 42 / 20%); }
.qn-menu.is-page { top: 30px; right: 0; }
.qn-menu.is-card { min-width: 208px; }
.qn-menu button { display: flex; align-items: center; gap: 8px; min-height: 32px; padding: 0 8px; border: 0; border-radius: 6px; background: transparent; color: var(--ink2); font: inherit; font-size: 12.5px; text-align: left; cursor: pointer; }
.qn-menu button:hover { background: var(--paper); color: var(--ink); }
.qn-menu button em { margin-left: auto; color: var(--muted); font-size: 12px; font-style: normal; }
.qn-menu button .qn-chevron-right { margin-left: auto; color: var(--muted); }
.qn-menu-check { gap: 8px; }
.qn-box { display: grid; place-items: center; width: 15px; height: 15px; border: 1.5px solid color-mix(in srgb, var(--ink) 30%, transparent); border-radius: 4px; color: #fff; }
.qn-box.on { border-color: var(--accent); background: var(--accent); }
.qn-submenu { position: absolute; top: 0; left: calc(100% + 4px); display: flex; min-width: 168px; flex-direction: column; gap: 1px; padding: 5px; border: 1px solid var(--line2); border-radius: 9px; background: var(--card); box-shadow: 0 16px 36px rgb(15 23 42 / 20%); }
/* 靠近右侧边缘时子菜单向左展开，避免整块跑到视口外 */
.qn-menu.is-flip .qn-submenu { left: auto; right: calc(100% + 4px); }
.qn-submenu button { justify-content: space-between; }
.qn-submenu button.on { color: var(--accent); }

@media (max-width: 900px) {
  .quadrant-page { height: auto; min-height: calc(100dvh - 88px); padding: 12px; }
  .quadrant-grid { grid-template-columns: minmax(0, 1fr); grid-template-rows: auto; }
  .quadrant-card { min-height: 220px; }
  .qn-card-actions { opacity: 1; }
  .qn-submenu { position: static; margin-top: 4px; box-shadow: none; }
}
</style>
