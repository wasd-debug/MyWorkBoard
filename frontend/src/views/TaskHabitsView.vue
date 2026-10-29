<template>
  <main ref="pageEl" class="habits-page" :style="pageStyle">
    <div class="habits-shell" :class="{ 'is-detail': Boolean(selected) }">
      <aside class="habits-side">
        <header class="habits-side-head">
          <div class="habits-title-wrap">
            <button type="button" class="habits-title" :aria-expanded="titleMenuOpen" @click="toggleTitleMenu">
              {{ archivedMode ? '已归档习惯' : '习惯' }}
              <ChevronDown :size="14" />
            </button>
            <div v-if="titleMenuOpen" class="habits-menu is-title" @click.stop>
              <button type="button" :class="{ on: !archivedMode }" @click="setMode(false)">所有习惯</button>
              <button type="button" :class="{ on: archivedMode }" @click="setMode(true)">已归档习惯</button>
            </div>
          </div>
          <div class="habits-side-actions">
            <button type="button" class="habits-icon-btn" aria-label="新建习惯" title="新建习惯" @click="openCreate"><Plus :size="17" /></button>
            <div class="habits-more-wrap">
              <button type="button" class="habits-icon-btn" aria-label="更多" title="更多" :aria-expanded="moreMenuOpen" @click.stop="moreMenuOpen = !moreMenuOpen"><MoreHorizontal :size="17" /></button>
              <div v-if="moreMenuOpen" class="habits-menu is-more" @click.stop>
                <button type="button" @click="startBatch"><ListChecks :size="14" />批量管理</button>
                <button type="button" @click="setMode(true)"><Archive :size="14" />已归档习惯</button>
                <button type="button" class="is-danger" @click="clearAll"><Trash2 :size="14" />清空所有</button>
              </div>
            </div>
          </div>
        </header>

        <HabitWeekStrip v-if="!archivedMode" :week="week" :progress="weekProgressMap" />

        <div class="habits-scroll">
          <template v-if="archivedMode">
            <ul v-if="archivedHabits.length" class="habits-archive-grid">
              <li v-for="habit in archivedHabits" :key="habit.publicId" :class="{ 'is-active': selectedId === habit.publicId }" @click="select(habit)">
                <span class="habits-archive-icon" :style="iconStyle(habit)">{{ habit.icon || '✓' }}</span>
                <div>
                  <b>{{ habit.name }}</b>
                  <small><Flame :size="11" />{{ totalDone(habit, checkins) }} 天 · <Zap :size="11" />{{ currentStreak(habit, checkins, today) }} 天</small>
                </div>
              </li>
            </ul>
            <p v-else class="habits-empty">还没有归档的习惯</p>
          </template>

          <template v-else>
            <section v-for="group in groups" :key="group.key" class="habits-group">
              <button type="button" class="habits-group-head" :aria-expanded="!collapsed[group.key]" @click="toggleGroup(group.key)">
                <ChevronDown :size="13" :class="{ 'is-collapsed': collapsed[group.key] }" />
                <b>{{ group.label }}</b>
                <em>{{ group.items.length }}</em>
              </button>
              <ul v-show="!collapsed[group.key]">
                <HabitRow
                  v-for="habit in group.items"
                  :key="habit.publicId"
                  :habit="habit"
                  :checkins="checkins"
                  :week="week"
                  :today="today"
                  :active="selectedId === habit.publicId"
                  :selectable="batch"
                  :selected="batchPicked.includes(habit.publicId)"
                  @open="batch ? togglePick(habit) : select(habit)"
                  @select="togglePick(habit)"
                  @toggle="key => toggleDay(habit, key)"
                />
              </ul>
            </section>
            <p v-if="!groups.length" class="habits-empty">还没有习惯，点右上角 + 新建一个</p>
          </template>
        </div>

        <footer v-if="batch" class="habits-batch-bar">
          <span>已选 {{ batchPicked.length }} 个</span>
          <div>
            <button type="button" @click="archivePicked">归档</button>
            <button type="button" class="is-danger" @click="removePicked">删除</button>
            <button type="button" @click="batch = false; batchPicked = []">取消</button>
          </div>
        </footer>
      </aside>

      <aside class="habits-detail-pane">
        <HabitDetailPanel
          v-if="selected"
          :habit="selected"
          :checkins="checkins"
          :today="today"
          :closable="narrow"
          @close="selectedId = ''"
          @toggle="key => toggleDay(selected, key)"
          @edit="openEdit(selected)"
          @style="value => setStyle(selected, value)"
          @archive="toggleArchive(selected)"
          @remove="removeHabit(selected)"
        />
        <div v-else class="habits-detail-empty">
          <p>选一个习惯看详情</p>
        </div>
      </aside>
    </div>

    <HabitFormDialog
      :open="formOpen"
      :habit="editing"
      :saving="saving"
      @close="formOpen = false"
      @submit="submitForm"
    />
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Archive, ChevronDown, Flame, ListChecks, MoreHorizontal, Plus, Trash2, Zap } from 'lucide-vue-next'
import HabitWeekStrip from '../components/habits/HabitWeekStrip.vue'
import HabitRow from '../components/habits/HabitRow.vue'
import HabitDetailPanel from '../components/habits/HabitDetailPanel.vue'
import HabitFormDialog from '../components/habits/HabitFormDialog.vue'
import {
  currentStreak, dayState, doneTarget, findCheckin, groupHabits, recentWeek, todayKey, totalDone, weekProgress
} from '../components/habits/habitUtils.js'
import { useTasksStore } from '../stores/tasks.js'

const store = useTasksStore()
const pageEl = ref(null)
const pageHeight = ref(0)
const pageStyle = computed(() => (pageHeight.value ? { height: `${pageHeight.value}px` } : {}))

const today = ref(todayKey())
const week = computed(() => recentWeek(today.value))
const archivedMode = ref(false)
const collapsed = reactive({})
const titleMenuOpen = ref(false)
const moreMenuOpen = ref(false)
const batch = ref(false)
const batchPicked = ref([])
const selectedId = ref('')
const formOpen = ref(false)
const editing = ref(null)
const saving = ref(false)
const narrow = ref(false)

const allHabits = computed(() => store.habits || [])
const checkins = computed(() => store.habitCheckins || [])
const habits = computed(() => allHabits.value.filter(habit => !habit.archived))
const archivedHabits = computed(() => allHabits.value.filter(habit => habit.archived))
const groups = computed(() => groupHabits(habits.value))
const selected = computed(() => allHabits.value.find(habit => habit.publicId === selectedId.value) || null)

const weekProgressMap = computed(() => Object.fromEntries(
  week.value.map(day => [day.key, weekProgress(habits.value, checkins.value, [day], today.value)])
))

function iconStyle(habit) {
  return {
    background: `color-mix(in srgb, ${habit.color || 'var(--accent)'} 18%, transparent)`,
    color: habit.color || 'var(--accent)'
  }
}

function syncPageHeight() {
  const el = pageEl.value
  if (!el) return
  const offset = el.getBoundingClientRect().top + window.scrollY
  const parent = el.parentElement
  const padBottom = parent ? Number.parseFloat(getComputedStyle(parent).paddingBottom) || 0 : 0
  pageHeight.value = Math.max(320, Math.round(window.innerHeight - offset - padBottom))
}

function syncNarrow() {
  narrow.value = window.matchMedia('(max-width: 1080px)').matches
}

function toggleGroup(key) {
  collapsed[key] = !collapsed[key]
}

function toggleTitleMenu() {
  titleMenuOpen.value = !titleMenuOpen.value
  moreMenuOpen.value = false
}

function setMode(archived) {
  archivedMode.value = archived
  titleMenuOpen.value = false
  moreMenuOpen.value = false
  batch.value = false
  batchPicked.value = []
  selectedId.value = ''
}

function select(habit) {
  selectedId.value = habit.publicId
  if (window.matchMedia('(max-width: 1080px)').matches) narrow.value = true
}

function startBatch() {
  moreMenuOpen.value = false
  batch.value = true
  batchPicked.value = []
  selectedId.value = ''
}

function togglePick(habit) {
  const index = batchPicked.value.indexOf(habit.publicId)
  if (index >= 0) batchPicked.value.splice(index, 1)
  else batchPicked.value.push(habit.publicId)
}

async function toggleDay(habit, key) {
  if (!habit) return
  const state = dayState(habit, checkins.value, key, today.value)
  if (state === 'future' || state === 'none') return
  const existing = findCheckin(checkins.value, habit.publicId, key)
  const status = state === 'done' ? 'SKIP' : 'DONE'
  // 直接写 checkin：一次点击要么「打满当天目标」，要么退回未完成，不做累加
  await store.saveEfficiency('habit-checkin', {
    ...(existing || {}),
    habitId: habit.publicId,
    date: key,
    status,
    count: status === 'DONE' ? doneTarget(habit) : 0,
    publicId: existing?.publicId || crypto.randomUUID(),
    revision: existing?.revision || 0
  })
}

function openCreate() {
  editing.value = null
  formOpen.value = true
  moreMenuOpen.value = false
}

function openEdit(habit) {
  editing.value = habit
  formOpen.value = true
}

async function submitForm(payload) {
  saving.value = true
  try {
    if (editing.value) {
      await store.updateHabit(editing.value, payload)
      selectedId.value = editing.value.publicId
    } else {
      const sortOrder = allHabits.value.length
      const id = await store.createHabit({ ...payload, archived: false, remindAt: payload.remindAt || null, sortOrder })
      if (id) selectedId.value = id
    }
    formOpen.value = false
  } finally {
    saving.value = false
  }
}

async function setStyle(habit, value) {
  await store.updateHabit(habit, { checkStyle: value })
}

async function toggleArchive(habit) {
  await store.updateHabit(habit, { archived: !habit.archived })
  if (habit.archived) selectedId.value = ''
}

async function removeHabit(habit) {
  if (!window.confirm(`删除习惯「${habit.name}」？打卡记录会一起消失。`)) return
  await store.deleteHabit(habit)
  selectedId.value = ''
}

async function archivePicked() {
  for (const id of batchPicked.value) {
    const habit = allHabits.value.find(item => item.publicId === id)
    if (habit) await store.updateHabit(habit, { archived: true })
  }
  batch.value = false
  batchPicked.value = []
}

async function removePicked() {
  if (!window.confirm(`删除选中的 ${batchPicked.value.length} 个习惯？`)) return
  for (const id of batchPicked.value) {
    const habit = allHabits.value.find(item => item.publicId === id)
    if (habit) await store.deleteHabit(habit)
  }
  batch.value = false
  batchPicked.value = []
}

async function clearAll() {
  moreMenuOpen.value = false
  const list = habits.value
  if (!list.length) return
  if (!window.confirm(`清空全部 ${list.length} 个习惯？此操作不可撤销。`)) return
  for (const habit of list) await store.deleteHabit(habit)
  selectedId.value = ''
}

function onDocPointerDown(event) {
  if (event.target.closest?.('.habits-title-wrap') || event.target.closest?.('.habits-more-wrap')) return
  titleMenuOpen.value = false
  moreMenuOpen.value = false
}

let onResize = null

onMounted(async () => {
  await store.init({ waitForRemote: false })
  onResize = () => {
    syncPageHeight()
    syncNarrow()
  }
  window.addEventListener('resize', onResize)
  document.addEventListener('pointerdown', onDocPointerDown, true)
  syncNarrow()
  await nextTick()
  syncPageHeight()
  window.setTimeout(syncPageHeight, 260)
  if (habits.value.length) selectedId.value = habits.value[0].publicId
})

onBeforeUnmount(() => {
  if (onResize) window.removeEventListener('resize', onResize)
  document.removeEventListener('pointerdown', onDocPointerDown, true)
})
</script>

<style scoped>
.habits-page { display: flex; height: 100vh; min-height: 0; overflow: hidden; padding: 22px 26px 0; --habit-done: var(--priority-low, #4d78db); }
.habits-shell { display: grid; grid-template-columns: minmax(320px, 470px) minmax(340px, 1fr); gap: 18px; width: 100%; max-width: 1060px; margin: 0 auto; min-height: 0; }

.habits-side { display: flex; min-height: 0; flex-direction: column; }
.habits-side-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 0 6px 6px; }
.habits-title-wrap { position: relative; }
.habits-title {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 4px 6px; border: 0; border-radius: 8px;
  background: transparent; color: var(--ink);
  font: inherit; font-size: 15px; font-weight: 600; cursor: pointer;
}
.habits-title:hover { background: color-mix(in srgb, var(--ink) 6%, transparent); }
.habits-side-actions { display: flex; align-items: center; gap: 2px; }
.habits-icon-btn { display: grid; width: 30px; height: 30px; place-items: center; border: 0; border-radius: 9px; background: transparent; color: var(--muted); cursor: pointer; }
.habits-icon-btn:hover { background: color-mix(in srgb, var(--ink) 7%, transparent); color: var(--ink); }
.habits-more-wrap { position: relative; }

.habits-menu {
  position: absolute; z-index: 40; top: 34px; left: 0;
  display: grid; min-width: 150px; padding: 6px;
  border: 1px solid var(--line); border-radius: 12px;
  background: var(--card); box-shadow: var(--module-shadow, 0 18px 44px rgb(15 23 42 / 18%));
}
.habits-menu.is-more { left: auto; right: 0; }
.habits-menu button { display: flex; align-items: center; gap: 8px; padding: 7px 9px; border: 0; border-radius: 8px; background: transparent; color: var(--ink2); font: inherit; font-size: 12.5px; text-align: left; cursor: pointer; }
.habits-menu button:hover { background: var(--paper); color: var(--ink); }
.habits-menu button.on { color: var(--accent); font-weight: 600; }
.habits-menu button.is-danger { color: var(--down, #e34d59); }

.habits-scroll { flex: 1; min-height: 0; overflow-y: auto; padding: 2px 2px 16px; scrollbar-gutter: stable; }

.habits-group { padding: 6px 0 2px; }
.habits-group-head { display: flex; align-items: center; gap: 5px; width: 100%; padding: 4px 6px; border: 0; border-radius: 8px; background: transparent; color: var(--muted); font: inherit; font-size: 11.5px; cursor: pointer; }
.habits-group-head:hover { color: var(--ink2); }
.habits-group-head b { font-weight: 600; }
.habits-group-head em { color: var(--dim, var(--muted)); font-style: normal; }
.habits-group-head svg { transition: transform .16s ease; }
.habits-group-head svg.is-collapsed { transform: rotate(-90deg); }
.habits-group ul { display: grid; gap: 1px; margin: 0; padding: 0; list-style: none; }

.habits-archive-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin: 0; padding: 4px 2px; list-style: none; }
.habits-archive-grid li { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid var(--line); border-radius: 12px; background: var(--card); cursor: pointer; }
.habits-archive-grid li.is-active { border-color: color-mix(in srgb, var(--accent) 46%, transparent); }
.habits-archive-grid b { display: block; font-size: 12.5px; }
.habits-archive-grid small { display: inline-flex; align-items: center; gap: 3px; color: var(--muted); font-size: 10.5px; }
.habits-archive-icon { display: grid; width: 28px; height: 28px; flex: 0 0 auto; place-items: center; border-radius: 9px; }

.habits-empty { padding: 40px 10px; color: var(--muted); font-size: 12.5px; text-align: center; }

.habits-batch-bar { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 8px 10px; border: 1px solid var(--line); border-radius: 12px; background: var(--card); font-size: 12px; color: var(--muted); }
.habits-batch-bar div { display: flex; gap: 6px; }
.habits-batch-bar button { padding: 4px 10px; border: 1px solid var(--line); border-radius: 7px; background: transparent; color: var(--ink2); font: inherit; font-size: 12px; cursor: pointer; }
.habits-batch-bar button.is-danger { color: var(--down, #e34d59); }

.habits-detail-pane { min-height: 0; overflow-y: auto; padding-bottom: 18px; scrollbar-gutter: stable; }
.habits-detail-empty { display: grid; height: 100%; place-items: center; color: var(--muted); font-size: 12.5px; }

/* 窄屏：详情变覆盖层，列表占满 */
@media (max-width: 1080px) {
  .habits-page { padding: 16px 14px 0; }
  .habits-shell { grid-template-columns: 1fr; }
  .habits-detail-pane { position: fixed; z-index: 120; inset: 0; display: none; padding: 16px; background: var(--card); overflow-y: auto; }
  .habits-shell.is-detail .habits-detail-pane { display: block; }
  .habits-archive-grid { grid-template-columns: 1fr; }
}
</style>
