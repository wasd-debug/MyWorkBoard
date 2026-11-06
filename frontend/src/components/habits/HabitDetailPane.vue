<template>
  <div class="hdp" aria-label="习惯详情容器">
    <HabitDetailPanel
      :habit="habit"
      :checkins="checkins"
      :today="today"
      closable
      @close="emit('close')"
      @toggle="key => toggleDay(habit, key)"
      @edit="openEdit(habit)"
      @style="value => setStyle(habit, value)"
      @archive="toggleArchive(habit)"
      @remove="removeHabit(habit)"
      @focus="mode => startFocus(habit, mode)"
      @log="log => openLog(habit, log)"
      @delete-log="log => deleteLog(habit, log)"
      @day-menu="openDayMenu"
    />

    <ul v-if="dayMenu" ref="dayMenuEl" class="hdp-menu" :style="{ left: `${dayMenu.x}px`, top: `${dayMenu.y}px` }" @click.stop>
      <li><button type="button" @click="runDay('log')"><ClipboardList :size="16" />记录日志</button></li>
      <li><button type="button" @click="runDay('skip')"><SkipForward :size="16" />跳过打卡</button></li>
      <li><button type="button" @click="runDay('miss')"><X :size="16" />未完成</button></li>
    </ul>

    <HabitFormDialog :open="formOpen" :habit="editing" :saving="saving" @close="formOpen = false" @submit="submitForm" />
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
      :status="logTarget?.checkin?.status || ''"
      @close="logTarget = null"
      @save="saveLog"
    />
    <HabitSkipDialog
      :open="Boolean(skipTarget)"
      :habit="skipTarget?.habit || null"
      :date="skipTarget?.date || ''"
      :reason="skipTarget?.reason || ''"
      @close="skipTarget = null"
      @confirm="confirmSkip"
    />
  </div>
</template>

<script setup>
// 任务页 / 搜索页右栏复用的习惯详情。与习惯页共用同一套组件与写入口径，
// 但把状态收在组件内部，父级只需要给一个 habit。
import { computed, nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ClipboardList, SkipForward, X } from 'lucide-vue-next'
import HabitDetailPanel from './HabitDetailPanel.vue'
import HabitFormDialog from './HabitFormDialog.vue'
import HabitCheckDialog from './HabitCheckDialog.vue'
import HabitLogDialog from './HabitLogDialog.vue'
import HabitSkipDialog from './HabitSkipDialog.vue'
import { dayState, findCheckin, targetAmount, todayKey } from './habitUtils.js'
import { useTasksStore } from '../../stores/tasks.js'
import { uuid } from '../../utils/uuid.js'

const props = defineProps({
  habit: { type: Object, required: true }
})
const emit = defineEmits(['close', 'removed'])

const router = useRouter()
const store = useTasksStore()
const today = ref(todayKey())
const checkins = computed(() => (store.habitCheckins || []).filter(row => row.habitId === props.habit.publicId))

const formOpen = ref(false)
const editing = ref(null)
const saving = ref(false)
const checkPrompt = ref(null)
const logTarget = ref(null)
const skipTarget = ref(null)
const dayMenu = ref(null)
const dayMenuEl = ref(null)

async function writeCheckin(habit, key, existing, patch) {
  await store.saveEfficiency('habit-checkin', {
    ...(existing || {}),
    habitId: habit.publicId,
    date: key,
    publicId: existing?.publicId || uuid(),
    revision: existing?.revision || 0,
    amount: 0,
    mood: existing?.mood || '',
    note: existing?.note || '',
    ...patch
  })
  // 连续写必须等上一轮落库，否则旧 revision 会打到服务端冲突
  await store.syncNow().catch(() => {})
  return findCheckin(store.habitCheckins || [], habit.publicId, key)
}

async function toggleDay(habit, key) {
  if (!habit) return
  const state = dayState(habit, checkins.value, key, today.value)
  if (state === 'future' || state === 'none') return
  const existing = findCheckin(checkins.value, habit.publicId, key)
  if (state === 'done') {
    await writeCheckin(habit, key, existing, { status: 'CLEAR', count: 0, amount: 0, mood: '', note: '', skipReason: '' })
    return
  }
  if (habit.targetType !== 'QUANTITY') {
    const saved = await writeCheckin(habit, key, existing, { status: 'DONE', count: 1, amount: 0 })
    if (habit.autoPopupLog) openLog(habit, saved || { ...(existing || {}), date: key })
    return
  }
  const target = targetAmount(habit)
  const current = Number(existing?.amount || 0)
  const mode = habit.checkMode || 'AUTO'
  if (mode === 'MANUAL') {
    checkPrompt.value = { habit, date: key, existing, current, target }
    return
  }
  const next = mode === 'ALL' ? target : Math.min(target, current + Math.max(1, Number(habit.perAmount) || 1))
  const saved = await writeCheckin(habit, key, existing, { status: 'DONE', count: Number(existing?.count || 0) + 1, amount: next })
  if (next >= target && habit.autoPopupLog) openLog(habit, saved || { ...(existing || {}), date: key })
}

async function confirmManualCheck(amount) {
  const prompt = checkPrompt.value
  checkPrompt.value = null
  if (!prompt) return
  const next = Math.min(prompt.target, prompt.current + Number(amount || 0))
  const saved = await writeCheckin(prompt.habit, prompt.date, prompt.existing, {
    status: 'DONE',
    count: Number(prompt.existing?.count || 0) + 1,
    amount: next
  })
  if (next >= prompt.target && prompt.habit.autoPopupLog) openLog(prompt.habit, saved || { ...(prompt.existing || {}), date: prompt.date })
}

function openEdit(habit) {
  editing.value = habit
  formOpen.value = true
}

async function submitForm(payload) {
  saving.value = true
  try {
    if (editing.value) await store.updateHabit(editing.value, payload)
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
  if (!habit.archived) emit('close')
}

async function removeHabit(habit) {
  if (!window.confirm(`删除习惯「${habit.name}」？打卡记录会一起消失。`)) return
  await store.deleteHabit(habit)
  emit('removed')
}

function openLog(habit, checkin) {
  if (!habit || !checkin?.date) return
  logTarget.value = { habit, date: String(checkin.date).slice(0, 10), checkin }
}

async function saveLog({ mood, note }) {
  const target = logTarget.value
  logTarget.value = null
  if (!target) return
  const current = findCheckin(store.habitCheckins || [], target.habit.publicId, target.date) || target.checkin
  if (!current) return
  await store.saveEfficiency('habit-checkin', { ...current, mood, note })
  await store.syncNow().catch(() => {})
}

async function deleteLog(habit, log) {
  if (!window.confirm('删除这条打卡记录？当天会变成未完成。')) return
  const key = String(log.date).slice(0, 10)
  const current = findCheckin(store.habitCheckins || [], habit.publicId, key) || log
  await writeCheckin(habit, key, current, { status: 'CLEAR', count: 0, amount: 0, mood: '', note: '', skipReason: '' })
}

async function openLogForDay(habit, key) {
  let current = findCheckin(store.habitCheckins || [], habit.publicId, key)
  if (!current || current.status === 'CLEAR') {
    current = await writeCheckin(habit, key, current || null, {
      status: 'DONE',
      count: 1,
      amount: habit.targetType === 'QUANTITY' ? targetAmount(habit) : 0,
      mood: '',
      note: '',
      skipReason: ''
    })
  }
  if (current) logTarget.value = { habit, date: key, checkin: current }
}

async function openDayMenu({ event, key }) {
  if (!event) return
  dayMenu.value = { key, x: event.clientX, y: event.clientY }
  await nextTick()
  const el = dayMenuEl.value
  if (!el || !dayMenu.value) return
  const rect = el.getBoundingClientRect()
  const pad = 8
  dayMenu.value = {
    ...dayMenu.value,
    x: Math.max(pad, Math.min(event.clientX, window.innerWidth - rect.width - pad)),
    y: Math.max(pad, Math.min(event.clientY, window.innerHeight - rect.height - pad))
  }
}

async function runDay(action) {
  const menu = dayMenu.value
  const habit = props.habit
  dayMenu.value = null
  if (!menu || !habit) return
  const key = menu.key
  if (action === 'log') {
    await openLogForDay(habit, key)
    return
  }
  const existing = findCheckin(store.habitCheckins || [], habit.publicId, key)
  if (action === 'skip') {
    skipTarget.value = { habit, date: key, reason: existing?.status === 'SKIP' ? existing.skipReason || '' : '' }
    return
  }
  await writeCheckin(habit, key, existing, { status: 'MISS', count: 0, amount: 0, mood: '', note: '', skipReason: '' })
}

async function confirmSkip(reason) {
  const target = skipTarget.value
  skipTarget.value = null
  if (!target) return
  const existing = findCheckin(store.habitCheckins || [], target.habit.publicId, target.date)
  await writeCheckin(target.habit, target.date, existing, {
    status: 'SKIP',
    count: 0,
    amount: 0,
    note: '',
    skipReason: String(reason || '').slice(0, 100)
  })
}

function startFocus(habit, mode) {
  router.push({ path: '/tasks/focus', query: { title: habit.name, mode } })
}
</script>

<style scoped>
.hdp { position: relative; display: flex; min-height: 0; flex: 1; flex-direction: column; overflow: auto; }
.hdp-menu { position: fixed; z-index: 340; display: grid; gap: 2px; min-width: 168px; padding: 6px; border: 1px solid var(--line); border-radius: 10px; background: var(--card); box-shadow: 0 16px 40px rgb(0 0 0 / 26%); list-style: none; }
.hdp-menu li { list-style: none; }
.hdp-menu button { display: flex; align-items: center; gap: 9px; width: 100%; min-height: 34px; padding: 0 9px; border: 0; border-radius: 7px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; text-align: left; cursor: pointer; }
.hdp-menu button:hover { background: color-mix(in srgb, var(--ink) 7%, transparent); color: var(--ink); }
</style>
