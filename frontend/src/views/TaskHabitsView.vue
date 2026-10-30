<template>
  <main ref="pageEl" class="hp" :style="pageStyle">
    <div
      class="hp-shell"
      :class="{ 'is-detail': showDetail, 'is-overlay': overlayMode, 'is-collapsed': !overlayMode && !drawer, 'is-resizing': resizing }"
      :style="shellStyle"
    >
      <aside class="hp-side">
        <header class="hp-head">
          <div class="hp-title-wrap">
            <button type="button" class="hp-title" :aria-expanded="titleMenu" @click="toggleTitleMenu">
              {{ archivedMode ? '已归档习惯' : '习惯' }}<ChevronDown :size="15" />
            </button>
            <div v-if="titleMenu" class="hp-menu" @click.stop>
              <button type="button" :class="{ on: !archivedMode }" @click="setMode(false)">所有习惯</button>
              <button type="button" :class="{ on: archivedMode }" @click="setMode(true)">已归档习惯</button>
            </div>
          </div>
          <div class="hp-actions">
            <button
              type="button"
              class="hp-icon-btn"
              :aria-label="layout === 'list' ? '切换为卡片样式' : '切换为列表样式'"
              :title="layout === 'list' ? '卡片样式' : '列表样式'"
              @click="toggleLayout"
            >
              <LayoutGrid v-if="layout === 'list'" :size="17" />
              <List v-else :size="17" />
            </button>
            <button type="button" class="hp-icon-btn" aria-label="新建习惯" title="新建习惯" @click="openCreate()"><Plus :size="19" /></button>
            <div class="hp-more-wrap">
              <button type="button" class="hp-icon-btn" aria-label="更多" title="更多" :aria-expanded="moreMenu" @click.stop="moreMenu = !moreMenu"><MoreHorizontal :size="18" /></button>
              <div v-if="moreMenu" class="hp-menu is-more" @click.stop>
                <button type="button" @click="startBatch"><ListChecks :size="15" />批量管理</button>
                <button type="button" @click="openCheckSetting"><Settings2 :size="15" />打卡设置</button>
                <button type="button" @click="setMode(true)"><Archive :size="15" />已归档习惯</button>
                <button type="button" @click="toggleDrawer"><PanelRight :size="15" />{{ drawer ? '收起详情' : '展开详情' }}</button>
                <button type="button" class="is-danger" @click="clearAll"><Trash2 :size="15" />清空所有</button>
              </div>
            </div>
          </div>
        </header>

        <HabitWeekStrip v-if="!archivedMode" :week="week" :progress="weekProgressMap" :active="activeDate" @pick="pickDate" />

        <div class="hp-scroll">
          <template v-if="archivedMode">
            <ul v-if="archivedHabits.length" class="hp-grid" :class="{ 'is-cards': layout === 'grid' }">
              <HabitRow
                v-for="habit in archivedHabits"
                :key="habit.publicId"
                :habit="habit"
                :checkins="checkins"
                :today="activeDate"
                :layout="layout"
                :active="selectedId === habit.publicId"
                @open="select(habit)"
                @menu="openContext($event, habit)"
              />
            </ul>
            <p v-else class="hp-empty">还没有归档的习惯</p>
          </template>

          <template v-else>
            <div class="hp-datebar">
              <button type="button" class="hp-date-btn" @click="resetDate"><ChevronDown :size="13" />{{ dateBarLabel }}</button>
              <button v-if="!isToday" type="button" class="hp-date-x" aria-label="回到今天" @click="resetDate"><X :size="12" /></button>
            </div>

            <section v-for="group in groups" :key="group.key" class="hp-group">
              <div class="hp-group-row">
                <button type="button" class="hp-group-head" :aria-expanded="!collapsed[group.key]" @click="toggleGroup(group.key)">
                  <ChevronDown :size="13" :class="{ 'is-collapsed': collapsed[group.key] }" />
                  <b>{{ group.label }}</b>
                  <em>{{ group.items.length }}</em>
                </button>
                <span class="hp-group-tools">
                  <button type="button" :aria-label="`在${group.label}新建习惯`" :title="'新建'" @click="openCreate(group.key)"><Plus :size="13" /></button>
                  <button type="button" :aria-label="`${group.label}更多`" :title="'更多'" @click="toggleGroup(group.key)"><MoreHorizontal :size="13" /></button>
                </span>
              </div>
              <ul v-show="!collapsed[group.key]" class="hp-grid" :class="{ 'is-cards': layout === 'grid' }">
                <HabitRow
                  v-for="habit in group.items"
                  :key="habit.publicId"
                  :habit="habit"
                  :checkins="checkins"
                  :today="activeDate"
                  :layout="layout"
                  :active="selectedId === habit.publicId"
                  :selectable="batch"
                  :selected="batchPicked.includes(habit.publicId)"
                  @open="batch ? togglePick(habit) : select(habit)"
                  @select="togglePick(habit)"
                  @menu="openContext($event, habit)"
                  @toggle="key => toggleDay(habit, key)"
                />
              </ul>
            </section>
            <p v-if="!groups.length" class="hp-empty">还没有习惯，点右上角 + 新建一个</p>
          </template>
        </div>

        <footer v-if="batch" class="hp-batch">
          <span>已选 {{ batchPicked.length }} 个</span>
          <div>
            <button type="button" @click="archivePicked">归档</button>
            <button type="button" class="is-danger" @click="removePicked">删除</button>
            <button type="button" @click="batch = false; batchPicked = []">取消</button>
          </div>
        </footer>
      </aside>

      <div v-if="!overlayMode && drawer" class="hp-split" role="separator" aria-orientation="vertical" aria-label="拖动调整宽度" @pointerdown="startResize" />

      <aside v-show="overlayMode || drawer" class="hp-detail">        <HabitDetailPanel
          v-if="selected"
          :habit="selected"
          :checkins="checkins"
          :today="today"
          :closable="overlayMode"
          @close="closeDetail"
          @toggle="key => toggleDay(selected, key)"
          @edit="openEdit(selected)"
          @style="value => setStyle(selected, value)"
          @archive="toggleArchive(selected)"
          @remove="removeHabit(selected)"
          @focus="mode => startFocus(selected, mode)"
          @log="log => openLog(selected, log)"
          @delete-log="log => deleteLog(selected, log)"
        />
        <div v-else class="hp-detail-empty">
          <p>选一个习惯看详情</p>
        </div>
      </aside>
    </div>

    <button v-if="!overlayMode && !drawer" type="button" class="hp-reopen" aria-label="展开详情" @click="openDrawer"><PanelRight :size="16" /></button>

    <ul v-if="context" class="hp-context" :style="{ left: `${context.x}px`, top: `${context.y}px` }" @click.stop>
      <template v-if="context.habit.archived">
        <li><button type="button" @click="runContext('restore')"><ArchiveRestore :size="16" />恢复习惯</button></li>
        <li><button type="button" class="is-danger" @click="runContext('remove')"><Trash2 :size="16" />删除</button></li>
      </template>
      <template v-else>
        <li><button type="button" @click="runContext('edit')"><Pencil :size="16" />编辑</button></li>
        <li class="has-sub" @mouseenter="contextSub = 'focus'" @mouseleave="contextSub = ''">
          <button type="button"><CircleDot :size="16" />开始专注<ChevronRight :size="13" class="hp-ctx-arrow" /></button>
          <ul v-if="contextSub === 'focus'" class="hp-context-sub">
            <li><button type="button" @click="runContext('focus', 'POMODORO')">开始番茄专注</button></li>
            <li><button type="button" @click="runContext('focus', 'TIMER')">开始正计时</button></li>
          </ul>
        </li>
        <li class="has-sub" @mouseenter="contextSub = 'style'" @mouseleave="contextSub = ''">
          <button type="button"><CircleCheck :size="16" />打卡样式<ChevronRight :size="13" class="hp-ctx-arrow" /></button>
          <ul v-if="contextSub === 'style'" class="hp-context-sub">
            <li><button type="button" :class="{ on: (context.habit.checkStyle || 'CHECK') === 'CHECK' }" @click="runContext('style', 'CHECK')">对勾</button></li>
            <li><button type="button" :class="{ on: context.habit.checkStyle === 'ICON' }" @click="runContext('style', 'ICON')">习惯图标</button></li>
          </ul>
        </li>
        <li><button type="button" @click="runContext('archive')"><FolderInput :size="16" />归档</button></li>
        <li><button type="button" class="is-danger" @click="runContext('remove')"><Trash2 :size="16" />删除</button></li>
      </template>
    </ul>

    <HabitFormDialog :open="formOpen" :habit="editing" :preset-group="presetGroup" :saving="saving" @close="formOpen = false" @submit="submitForm" />

    <HabitCheckDialog
      :open="Boolean(checkPrompt)"
      :habit="checkPrompt?.habit || null"
      :date="checkPrompt?.date || ''"
      :current="checkPrompt?.current || 0"
      :target="checkPrompt?.target || 1"
      @close="checkPrompt = null"
      @confirm="confirmManualCheck"
    />

    <HabitLogDialog
      :open="Boolean(logTarget)"
      :habit="logTarget?.habit || null"
      :date="logTarget?.date || ''"
      :mood="logTarget?.checkin?.mood || ''"
      :note="logTarget?.checkin?.note || ''"
      @close="logTarget = null"
      @mute="muteLog"
      @save="saveLog"
    />

    <HabitCheckSettingDialog
      :open="checkSettingOpen"
      :show="checkSettingValue"
      @close="checkSettingOpen = false"
      @save="applyCheckSetting"
    />
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Archive, ArchiveRestore, ChevronDown, ChevronRight, CircleCheck, CircleDot, FolderInput, LayoutGrid, List, ListChecks,
  MoreHorizontal, PanelRight, Pencil, Plus, Settings2, Trash2, X
} from 'lucide-vue-next'
import HabitWeekStrip from '../components/habits/HabitWeekStrip.vue'
import HabitRow from '../components/habits/HabitRow.vue'
import HabitDetailPanel from '../components/habits/HabitDetailPanel.vue'
import HabitFormDialog from '../components/habits/HabitFormDialog.vue'
import HabitCheckDialog from '../components/habits/HabitCheckDialog.vue'
import HabitLogDialog from '../components/habits/HabitLogDialog.vue'
import HabitCheckSettingDialog from '../components/habits/HabitCheckSettingDialog.vue'
import {
  dayState, findCheckin, groupHabits, recentWeek, targetAmount, todayKey, weekProgress
} from '../components/habits/habitUtils.js'
import { loadCustomGroups } from '../components/habits/habitGroups.js'
import { useTasksStore } from '../stores/tasks.js'

const router = useRouter()
const store = useTasksStore()
const pageEl = ref(null)
const pageHeight = ref(0)
const pageStyle = computed(() => (pageHeight.value ? { height: `${pageHeight.value}px` } : {}))

const SIDE_KEY = 'habit_detail_width_v2'
const LAYOUT_KEY = 'habit_layout_v1'
const LOG_MUTED_KEY = 'habit_log_muted_v1'
const CHECK_SETTING_KEY = 'habit_check_setting_v1'

const today = ref(todayKey())
/** 列表里正在查看/补打卡的日期，默认今天；点顶部周条可切换 */
const activeDate = ref(today.value)
const week = computed(() => recentWeek(today.value))
const archivedMode = ref(false)
const collapsed = reactive({})
const titleMenu = ref(false)
const moreMenu = ref(false)
const batch = ref(false)
const batchPicked = ref([])
const selectedId = ref('')
const formOpen = ref(false)
const editing = ref(null)
const presetGroup = ref('')
const saving = ref(false)
const narrow = ref(false)
const drawer = ref(true)
const detailOpen = ref(false)
const containerWidth = ref(0)
const sideWidth = ref(Number(localStorage.getItem(SIDE_KEY)) || 366)
const resizing = ref(false)
const layout = ref(localStorage.getItem(LAYOUT_KEY) === 'grid' ? 'grid' : 'list')
const customGroups = ref(loadCustomGroups())
const checkSettingOpen = ref(false)
const checkSettingValue = ref(localStorage.getItem(CHECK_SETTING_KEY) !== 'false')
const logMuted = ref(localStorage.getItem(LOG_MUTED_KEY) === 'true')
const checkPrompt = ref(null)
const logTarget = ref(null)
const context = ref(null)
const contextSub = ref('')

const allHabits = computed(() => store.habits || [])
const checkins = computed(() => store.habitCheckins || [])
const habits = computed(() => allHabits.value.filter(habit => !habit.archived))
const archivedHabits = computed(() => allHabits.value.filter(habit => habit.archived))
const groups = computed(() => groupHabits(habits.value, customGroups.value))
const selected = computed(() => allHabits.value.find(habit => habit.publicId === selectedId.value) || null)
// 容器太窄时右侧详情改成覆盖层（抽屉），列表占满
const autoCollapsed = computed(() => !narrow.value && containerWidth.value > 0 && containerWidth.value - sideWidth.value < 340)
const overlayMode = computed(() => narrow.value || autoCollapsed.value)
const showDetail = computed(() => Boolean(selected.value) && (overlayMode.value ? detailOpen.value : drawer.value))
const shellStyle = computed(() => (overlayMode.value ? {} : { gridTemplateColumns: `minmax(320px, 1fr) 6px ${sideWidth.value}px` }))
const weekProgressMap = computed(() => Object.fromEntries(
  week.value.map(day => [day.key, weekProgress(habits.value, checkins.value, [day], today.value)])
))
const isToday = computed(() => activeDate.value === today.value)
const dateBarLabel = computed(() => {
  const [year, month, day] = activeDate.value.split('-')
  return `${Number(month)}月${Number(day)}日${activeDate.value === today.value ? ' 今天' : ''}` || `${year}`
})

function pickDate(key) {
  if (!key) return
  activeDate.value = key
}

function resetDate() {
  activeDate.value = today.value
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
  titleMenu.value = !titleMenu.value
  moreMenu.value = false
}

function setMode(archived) {
  archivedMode.value = archived
  titleMenu.value = false
  moreMenu.value = false
  batch.value = false
  batchPicked.value = []
  selectedId.value = ''
}

function select(habit) {
  selectedId.value = habit.publicId
  if (overlayMode.value) {
    detailOpen.value = true
  } else {
    drawer.value = true
  }
}

function closeDetail() {
  detailOpen.value = false
  if (narrow.value || autoCollapsed.value) selectedId.value = ''
}

function toggleLayout() {
  layout.value = layout.value === 'list' ? 'grid' : 'list'
  localStorage.setItem(LAYOUT_KEY, layout.value)
}

function toggleDrawer() {
  moreMenu.value = false
  if (drawer.value) drawer.value = false
  else openDrawer()
}

function openDrawer() {
  drawer.value = true
  if (!selectedId.value && habits.value.length) selectedId.value = habits.value[0].publicId
}

function startBatch() {
  moreMenu.value = false
  batch.value = true
  batchPicked.value = []
  selectedId.value = ''
}

function togglePick(habit) {
  const index = batchPicked.value.indexOf(habit.publicId)
  if (index >= 0) batchPicked.value.splice(index, 1)
  else batchPicked.value.push(habit.publicId)
}

/** 写一条打卡（DONE/SKIP），返回写入后的记录 */
async function writeCheckin(habit, key, existing, patch) {
  await store.saveEfficiency('habit-checkin', {
    ...(existing || {}),
    habitId: habit.publicId,
    date: key,
    publicId: existing?.publicId || crypto.randomUUID(),
    revision: existing?.revision || 0,
    amount: 0,
    mood: existing?.mood || '',
    note: existing?.note || '',
    ...patch
  })
  // 连续打卡时如果不等这一轮推送落库，下一条会拿着旧 revision 打到服务端冲突
  await store.syncNow().catch(() => {})
  return findCheckin(store.habitCheckins || [], habit.publicId, key)
}

async function toggleDay(habit, key) {
  if (!habit) return
  const state = dayState(habit, checkins.value, key, today.value)
  if (state === 'future' || state === 'none') return
  const existing = findCheckin(checkins.value, habit.publicId, key)

  // 已完成 → 取消打卡
  if (state === 'done') {
    await writeCheckin(habit, key, existing, { status: 'SKIP', count: 0, amount: 0 })
    return
  }

  // 普通习惯：一次点击即完成
  if (habit.targetType !== 'QUANTITY') {
    const saved = await writeCheckin(habit, key, existing, { status: 'DONE', count: 1, amount: 0 })
    if (habit.autoPopupLog) openLog(habit, saved || { ...(existing || {}), date: key })
    return
  }

  const target = targetAmount(habit)
  const current = Number(existing?.amount || 0)
  const mode = habit.checkMode || 'AUTO'

  // 手动：弹层让用户填数字，不填就当作没打卡
  if (mode === 'MANUAL') {
    checkPrompt.value = { habit, date: key, existing, current, target }
    return
  }

  const next = mode === 'ALL' ? target : Math.min(target, current + Math.max(1, Number(habit.perAmount) || 1))
  const saved = await writeCheckin(habit, key, existing, {
    status: 'DONE',
    count: Number(existing?.count || 0) + 1,
    amount: next
  })
  if (next >= target && habit.autoPopupLog) openLog(habit, saved || { ...(existing || {}), date: key })
}

async function confirmManualCheck(amount) {
  const prompt = checkPrompt.value
  checkPrompt.value = null
  if (!prompt) return
  const target = prompt.target
  const next = Math.min(target, prompt.current + Number(amount || 0))
  const saved = await writeCheckin(prompt.habit, prompt.date, prompt.existing, {
    status: 'DONE',
    count: Number(prompt.existing?.count || 0) + 1,
    amount: next
  })
  if (next >= target && prompt.habit.autoPopupLog) openLog(prompt.habit, saved || { ...(prompt.existing || {}), date: prompt.date })
}

function openCreate(groupKey = '') {
  editing.value = null
  presetGroup.value = groupKey || ''
  formOpen.value = true
  moreMenu.value = false
}

function openEdit(habit) {
  editing.value = habit
  presetGroup.value = ''
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
      const id = await store.createHabit({ ...payload, archived: false, sortOrder })
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
  if (!habit.archived) selectedId.value = ''
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
  moreMenu.value = false
  const list = habits.value
  if (!list.length) return
  if (!window.confirm(`清空全部 ${list.length} 个习惯？此操作不可撤销。`)) return
  for (const habit of list) await store.deleteHabit(habit)
  selectedId.value = ''
}

function openCheckSetting() {
  moreMenu.value = false
  checkSettingValue.value = localStorage.getItem(CHECK_SETTING_KEY) !== 'false'
  checkSettingOpen.value = true
}

async function applyCheckSetting(value) {
  checkSettingOpen.value = false
  checkSettingValue.value = value
  localStorage.setItem(CHECK_SETTING_KEY, String(value))
  for (const habit of allHabits.value) {
    if (Boolean(habit.showInToday) !== value) await store.updateHabit(habit, { showInToday: value })
  }
}

/** 打开打卡日志弹窗；checkin 传 null 时按日期现取 */
function openLog(habit, checkin) {
  if (!habit || !checkin?.date) return
  logTarget.value = { habit, date: String(checkin.date).slice(0, 10), checkin }
}

async function saveLog({ mood, note }) {
  const target = logTarget.value
  logTarget.value = null
  if (!target) return
  // 弹窗开着的这段时间里记录可能已变（revision 变了），保存前重新取一次最新值
  const current = findCheckin(store.habitCheckins || [], target.habit.publicId, target.date) || target.checkin
  if (!current) return
  await store.saveEfficiency('habit-checkin', { ...current, mood, note })
  await store.syncNow().catch(() => {})
}

async function deleteLog(habit, log) {
  if (!window.confirm('删除这条打卡记录？当天会变成未完成。')) return
  const current = findCheckin(store.habitCheckins || [], habit.publicId, String(log.date).slice(0, 10)) || log
  await store.saveEfficiency('habit-checkin', current, 'DELETE')
}

function muteLog() {
  logMuted.value = true
  localStorage.setItem(LOG_MUTED_KEY, 'true')
}

function startFocus(habit, mode) {
  router.push({ path: '/tasks/focus', query: { title: habit.name, mode } })
}

function openContext(event, habit) {
  if (batch.value) return
  context.value = { habit, x: event.clientX, y: event.clientY }
  contextSub.value = ''
}

async function runContext(action, value) {
  const habit = context.value?.habit
  context.value = null
  if (!habit) return
  if (action === 'edit') openEdit(habit)
  else if (action === 'style') await setStyle(habit, value)
  else if (action === 'focus') startFocus(habit, value)
  else if (action === 'archive') await toggleArchive(habit)
  else if (action === 'restore') await store.updateHabit(habit, { archived: false })
  else if (action === 'remove') await removeHabit(habit)
}

function startResize(event) {
  if (overlayMode.value) return
  resizing.value = true
  const startX = event.clientX
  const startWidth = sideWidth.value
  const move = moveEvent => {
    // 分栏条在右侧详情左边缘：向左拖变宽，向右拖变窄
    const next = Math.min(680, Math.max(300, startWidth - (moveEvent.clientX - startX)))
    sideWidth.value = Math.round(next)
  }
  const up = () => {
    resizing.value = false
    localStorage.setItem(SIDE_KEY, String(sideWidth.value))
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

function onDocPointerDown(event) {
  if (context.value && !event.target.closest?.('.hp-context')) context.value = null
  if (!event.target.closest?.('.hp-title-wrap') && !event.target.closest?.('.hp-more-wrap')) {
    titleMenu.value = false
    moreMenu.value = false
  }
}

function onResize() {
  syncPageHeight()
  syncNarrow()
}

let heightTimer = 0
let widthObserver = null

onMounted(async () => {
  await store.init({ waitForRemote: false })
  window.addEventListener('resize', onResize)
  document.addEventListener('pointerdown', onDocPointerDown, true)
  syncNarrow()
  if (pageEl.value) {
    containerWidth.value = pageEl.value.clientWidth
    widthObserver = new ResizeObserver(entries => {
      containerWidth.value = Math.round(entries[0].contentRect.width)
    })
    widthObserver.observe(pageEl.value)
  }
  await nextTick()
  syncPageHeight()
  heightTimer = window.setTimeout(syncPageHeight, 260)
  if (habits.value.length) selectedId.value = habits.value[0].publicId
})

onBeforeUnmount(() => {
  widthObserver?.disconnect()
  window.removeEventListener('resize', onResize)
  document.removeEventListener('pointerdown', onDocPointerDown, true)
  window.clearTimeout(heightTimer)
})

watch(() => allHabits.value.length, () => {
  if (!selectedId.value && habits.value.length) selectedId.value = habits.value[0].publicId
})
</script>

<style scoped>
.hp {
  display: flex;
  height: 100vh;
  min-height: 0;
  overflow: hidden;
  padding: 22px 26px 0;
  --hb-accent: #3d8fce;
}
.hp-shell {
  display: grid;
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
  min-height: 0;
}
.hp-shell.is-collapsed { grid-template-columns: minmax(0, 1fr) !important; }
.hp-shell.is-collapsed .hp-split { display: none; }
.hp-shell.is-resizing { cursor: col-resize; user-select: none; }
.hp-shell.is-overlay { grid-template-columns: minmax(0, 1fr) !important; }
.hp-shell.is-overlay .hp-split { display: none; }
.hp-shell.is-overlay .hp-side { padding-right: 0; }
.hp-shell.is-overlay .hp-detail {
  position: fixed; z-index: 120; inset: 0;
  display: none; padding: 16px;
  background: var(--card); overflow-y: auto;
}
.hp-shell.is-overlay.is-detail .hp-detail { display: block; }

.hp-side { display: flex; min-height: 0; min-width: 0; flex-direction: column; padding-right: 4px; }

/* 顶栏：15px 600 标题 + 右侧三个 30px 图标按钮 */
.hp-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 0 2px 8px; }
.hp-title-wrap { position: relative; }
.hp-title {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 3px 5px; border: 0; border-radius: 7px;
  background: transparent; color: var(--ink);
  font: inherit; font-size: 16px; font-weight: 600; letter-spacing: -.01em; cursor: pointer;
}
.hp-title:hover { background: color-mix(in srgb, var(--ink) 6%, transparent); }
.hp-title svg { color: var(--muted); }
.hp-actions { display: flex; align-items: center; gap: 2px; }
.hp-icon-btn { display: grid; width: 30px; height: 30px; place-items: center; border: 0; border-radius: 8px; background: transparent; color: var(--ink2); cursor: pointer; }
.hp-icon-btn:hover { background: color-mix(in srgb, var(--ink) 7%, transparent); color: var(--ink); }
.hp-more-wrap { position: relative; }

.hp-menu {
  position: absolute; z-index: 40; top: 34px; left: 0;
  display: grid; gap: 1px; min-width: 158px; padding: 6px;
  border-radius: 12px; background: var(--card);
  box-shadow: 0 18px 44px rgb(15 23 42 / 18%), 0 0 0 1px var(--line);
}
.hp-menu.is-more { left: auto; right: 0; }
.hp-menu button { display: flex; align-items: center; gap: 9px; padding: 8px 10px; border: 0; border-radius: 8px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; text-align: left; cursor: pointer; }
.hp-menu button:hover { background: color-mix(in srgb, var(--ink) 6%, transparent); color: var(--ink); }
.hp-menu button.on { color: var(--hb-accent); font-weight: 600; }
.hp-menu button.is-danger { color: var(--down, #e34d59); }

.hp-scroll { flex: 1; min-height: 0; overflow-y: auto; padding: 2px 2px 16px; scrollbar-gutter: stable; }

/* 日期条：⌄ 10月30日 ✕ */
.hp-datebar { display: flex; align-items: center; gap: 2px; padding: 2px 4px 6px; }
.hp-date-btn {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 4px; border: 0; border-radius: 6px;
  background: transparent; color: var(--muted); font: inherit; font-size: 12px; cursor: pointer;
}
.hp-date-btn:hover { color: var(--ink2); }
.hp-date-x { display: grid; width: 18px; height: 18px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--dim, var(--muted)); cursor: pointer; }
.hp-date-x:hover { background: color-mix(in srgb, var(--ink) 8%, transparent); color: var(--ink2); }

.hp-grid { display: grid; gap: 8px; margin: 0; padding: 0; list-style: none; }
.hp-grid.is-cards { grid-template-columns: repeat(2, minmax(0, 1fr)); }

.hp-group { padding: 4px 0 8px; }
.hp-group-row { display: flex; align-items: center; gap: 4px; padding: 0 2px 6px; }
.hp-group-head { display: flex; flex: 1; align-items: center; gap: 5px; min-width: 0; padding: 2px 4px; border: 0; border-radius: 6px; background: transparent; color: var(--muted); font: inherit; font-size: 12px; cursor: pointer; }
.hp-group-head:hover { color: var(--ink2); }
.hp-group-head b { font-weight: 600; }
.hp-group-head em { color: var(--dim, var(--muted)); font-style: normal; }
.hp-group-head svg { transition: transform .16s ease; }
.hp-group-head svg.is-collapsed { transform: rotate(-90deg); }
.hp-group-tools { display: flex; gap: 2px; opacity: 0; transition: opacity .14s ease; }
.hp-group-row:hover .hp-group-tools { opacity: 1; }
.hp-group-tools button { display: grid; width: 20px; height: 20px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.hp-group-tools button:hover { background: color-mix(in srgb, var(--ink) 8%, transparent); color: var(--ink); }

.hp-empty { padding: 44px 10px; color: var(--muted); font-size: 13px; text-align: center; }

.hp-batch { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 8px 12px; border-radius: 10px; background: var(--card); box-shadow: 0 0 0 1px var(--line); color: var(--muted); font-size: 12px; }
.hp-batch div { display: flex; gap: 6px; }
.hp-batch button { padding: 4px 10px; border: 1px solid var(--line); border-radius: 7px; background: transparent; color: var(--ink2); font: inherit; font-size: 12px; cursor: pointer; }
.hp-batch button.is-danger { color: var(--down, #e34d59); }

.hp-split { position: relative; cursor: col-resize; }
.hp-split::after { content: ''; position: absolute; top: 4px; bottom: 4px; left: 50%; width: 1px; background: color-mix(in srgb, var(--ink) 6%, transparent); }
.hp-split:hover::after { background: color-mix(in srgb, var(--ink) 16%, transparent); }

.hp-detail { min-height: 0; min-width: 0; overflow-y: auto; padding: 8px 4px 18px 14px; scrollbar-gutter: stable; }
.hp-detail-empty { display: grid; height: 100%; place-items: center; color: var(--muted); font-size: 13px; }

.hp-reopen { position: fixed; z-index: 60; top: 50%; right: 0; transform: translateY(-50%); display: grid; width: 26px; height: 52px; place-items: center; border: 0; border-radius: 10px 0 0 10px; background: var(--card); color: var(--muted); box-shadow: -2px 0 12px rgb(15 23 42 / 12%); cursor: pointer; }

/* 右键菜单：白卡 + 16px 图标 + 13px 文案 */
.hp-context {
  position: fixed; z-index: 200;
  display: grid; gap: 1px; min-width: 168px; margin: 0; padding: 6px;
  border-radius: 14px; background: var(--card); list-style: none;
  box-shadow: 0 18px 44px rgb(15 23 42 / 20%), 0 0 0 1px var(--line);
}
.hp-context li { position: relative; }
.hp-context button { display: flex; align-items: center; gap: 10px; width: 100%; padding: 8px 10px; border: 0; border-radius: 8px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; text-align: left; cursor: pointer; }
.hp-context button:hover { background: color-mix(in srgb, var(--ink) 6%, transparent); color: var(--ink); }
.hp-context button.is-danger, .hp-context li.is-danger button { color: var(--down, #e34d59); }
.hp-context button.on { color: #3d8fce; font-weight: 600; }
.hp-ctx-arrow { margin-left: auto; color: var(--dim, var(--muted)); }
.hp-context-sub {
  position: absolute; top: -6px; left: calc(100% + 6px);
  display: grid; gap: 1px; min-width: 150px; margin: 0; padding: 6px;
  border-radius: 14px; background: var(--card); list-style: none;
  box-shadow: 0 18px 44px rgb(15 23 42 / 18%), 0 0 0 1px var(--line);
}

@media (max-width: 1080px) {
  .hp { padding: 16px 14px 0; }
  .hp-grid.is-cards { grid-template-columns: 1fr; }
}
</style>
