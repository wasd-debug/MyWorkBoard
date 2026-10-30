<template>
  <section class="hd" aria-label="习惯详情">
    <header class="hd-head">
      <span class="hd-icon" :style="iconStyle">{{ habit.icon || '✓' }}</span>
      <div class="hd-title">
        <div class="hd-name-row">
          <b>{{ habit.name }}</b>
          <small v-if="habit.archived" class="hd-badge">已归档</small>
        </div>
        <small class="hd-sub">{{ frequencyLabel(habit) }} · 开始于 {{ startLabel }}</small>
      </div>
      <button v-if="closable" type="button" class="hd-iconbtn" aria-label="关闭详情" @click="emit('close')"><X :size="15" /></button>
      <div ref="menuEl" class="hd-menu">
        <button type="button" class="hd-iconbtn" aria-label="习惯操作" :aria-expanded="menuOpen" @click.stop="menuOpen = !menuOpen"><MoreHorizontal :size="16" /></button>
        <div v-if="menuOpen" class="hd-drop" @click.stop>
          <template v-if="habit.archived">
            <button type="button" @click="pick('archive')"><ArchiveRestore :size="15" />恢复习惯</button>
            <button type="button" class="is-danger" @click="pick('remove')"><Trash2 :size="15" />删除</button>
          </template>
          <template v-else>
            <button type="button" @click="pick('edit')"><Pencil :size="15" />编辑</button>
            <div class="hd-sub" :class="{ open: subMenu === 'focus' }">
              <button type="button" @click.stop="subMenu = subMenu === 'focus' ? '' : 'focus'" @mouseenter="subMenu = 'focus'">
                <CircleDot :size="15" />开始专注<ChevronRight :size="13" class="hd-arrow" />
              </button>
              <div v-if="subMenu === 'focus'" class="hd-sub-list" @mouseleave="subMenu = ''">
                <button type="button" @click="pick('focus', 'POMODORO')">开始番茄专注</button>
                <button type="button" @click="pick('focus', 'TIMER')">开始正计时</button>
              </div>
            </div>
            <div class="hd-sub" :class="{ open: subMenu === 'style' }">
              <button type="button" @click.stop="subMenu = subMenu === 'style' ? '' : 'style'" @mouseenter="subMenu = 'style'">
                <CircleCheck :size="15" />打卡样式<ChevronRight :size="13" class="hd-arrow" />
              </button>
              <div v-if="subMenu === 'style'" class="hd-sub-list" @mouseleave="subMenu = ''">
                <button type="button" :class="{ on: (habit.checkStyle || 'CHECK') === 'CHECK' }" @click="pick('style', 'CHECK')">对勾</button>
                <button type="button" :class="{ on: habit.checkStyle === 'ICON' }" @click="pick('style', 'ICON')">习惯图标</button>
              </div>
            </div>
            <button type="button" @click="pick('archive')"><FolderInput :size="15" />归档</button>
            <button type="button" class="is-danger" @click="pick('remove')"><Trash2 :size="15" />删除</button>
          </template>
        </div>
      </div>
    </header>

    <div class="hd-stats">
      <div class="hd-stat">
        <span class="hd-stat-label is-green"><BadgeCheck :size="13" />月打卡</span>
        <b>{{ monthDone }}<em>天</em></b>
      </div>
      <div class="hd-stat">
        <span class="hd-stat-label is-blue"><Zap :size="13" />总打卡</span>
        <b>{{ total }}<em>天</em></b>
      </div>
      <div class="hd-stat">
        <span class="hd-stat-label is-orange"><Percent :size="13" />月完成率</span>
        <b>{{ monthRate }}<em>%</em></b>
      </div>
      <button type="button" class="hd-stat is-toggle" :aria-label="streakMode === 'current' ? '切换为最高连续' : '切换为当前连续'" @click="toggleStreak">
        <span class="hd-stat-label is-red">
          <Flame :size="13" />{{ streakMode === 'current' ? '当前连续' : '最高连续' }}
          <ArrowLeftRight :size="11" class="hd-swap" />
        </span>
        <b>{{ streakMode === 'current' ? streak : longest }}<em>天</em></b>
      </button>
    </div>

    <div class="hd-cal">
      <header class="hd-cal-head">
        <button type="button" class="hd-cal-nav" aria-label="上个月" @click="moveMonth(-1)"><ChevronLeft :size="16" /></button>
        <div class="hd-cal-month">
          <b>{{ cursor.month }}月</b>
          <button v-if="!isCurrentMonth" type="button" class="hd-cal-today" @click="resetMonth">今天</button>
        </div>
        <button type="button" class="hd-cal-nav" aria-label="下个月" @click="moveMonth(1)"><ChevronRight :size="16" /></button>
      </header>
      <div class="hd-cal-week">
        <span v-for="label in WEEKDAY_LABELS" :key="label">周{{ label }}</span>
      </div>
      <div class="hd-cal-grid">
        <button
          v-for="cell in cells"
          :key="cell.key"
          type="button"
          class="hd-cell"
          :class="[`is-${stateOf(cell.key)}`, { 'is-out': !cell.inMonth, 'is-today': cell.isToday }]"
          :disabled="stateOf(cell.key) === 'future' || stateOf(cell.key) === 'none'"
          :title="`${cell.key} · ${stateText(stateOf(cell.key))}`"
          @click="emit('toggle', cell.key)"
        >
          <span class="hd-cell-day">{{ cell.day }}</span>
          <span class="hd-cell-mark" :class="`is-${stateOf(cell.key)}`">
            <template v-if="stateOf(cell.key) === 'done'">
              <span v-if="habit.checkStyle === 'ICON'" class="hd-cell-emoji">{{ habit.icon }}</span>
              <Check v-else :size="12" />
            </template>
            <X v-else-if="stateOf(cell.key) === 'missed'" :size="12" />
            <i v-else-if="stateOf(cell.key) === 'skip'" class="hd-cell-dot" />
            <i v-else-if="stateOf(cell.key) === 'partial'" class="hd-cell-ring" :style="{ '--fill': `${partialHeight(cell.key)}%` }" />
          </span>
        </button>
      </div>
    </div>

    <div class="hd-logs">
      <h3>记录日志</h3>
      <div v-if="logs.length" class="hd-log-scroll">
        <ul class="hd-log-list">
          <li v-for="log in logs" :key="log.publicId">
            <span class="hd-log-mark" :class="{ 'is-skip': log.status === 'SKIP' }">
              <Check v-if="log.status !== 'SKIP'" :size="11" />
              <X v-else :size="11" />
            </span>
            <div class="hd-log-main">
              <div class="hd-log-top">
                <span class="hd-log-date">{{ logDateLabel(logKey(log)) }}</span>
                <span class="hd-log-week">{{ weekdayLabel(logKey(log)) }}</span>
                <span v-if="log.mood" class="hd-log-mood">{{ log.mood }}</span>
                <span class="hd-log-actions">
                  <button type="button" aria-label="编辑日志" @click="emit('log', log)"><Pencil :size="12" /></button>
                  <button type="button" aria-label="删除日志" @click="emit('deleteLog', log)"><Trash2 :size="12" /></button>
                </span>
              </div>
              <p v-if="logLine(log)" class="hd-log-text">{{ logLine(log) }}</p>
              <p v-if="log.note" class="hd-log-note">{{ log.note }}</p>
            </div>
          </li>
        </ul>
      </div>
      <p v-else class="hd-logs-empty">这个月没有写打卡日志哦～</p>
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  ArrowLeftRight, ArchiveRestore, BadgeCheck, Check, ChevronLeft, ChevronRight, CircleCheck, CircleDot,
  Flame, FolderInput, MoreHorizontal, Pencil, Percent, Trash2, X, Zap
} from 'lucide-vue-next'
import {
  WEEKDAY_LABELS, currentStreak, dayState, frequencyLabel, longestStreak, logDateLabel, monthLogs, monthMatrix,
  monthStats, parseKey, progressOf, targetAmount, totalDone, weekdayLabel
} from './habitUtils.js'

const props = defineProps({
  habit: { type: Object, required: true },
  checkins: { type: Array, default: () => [] },
  today: { type: String, default: '' },
  closable: { type: Boolean, default: false }
})
const emit = defineEmits(['toggle', 'edit', 'style', 'archive', 'remove', 'close', 'focus', 'log', 'deleteLog'])

const menuEl = ref(null)
const menuOpen = ref(false)
const subMenu = ref('')
const streakMode = ref('current')
const cursor = ref(currentCursor())

function currentCursor() {
  return { year: Number(props.today.slice(0, 4)), month: Number(props.today.slice(5, 7)) }
}

watch(() => props.habit?.publicId, () => {
  menuOpen.value = false
  subMenu.value = ''
  cursor.value = currentCursor()
})

const iconStyle = computed(() => ({
  background: `color-mix(in srgb, ${props.habit.color || 'var(--accent)'} 22%, transparent)`,
  color: props.habit.color || 'var(--accent)'
}))
const amountUnit = computed(() => props.habit.amountUnit || '')
const startLabel = computed(() => {
  if (!props.habit.startDate) return '—'
  const date = parseKey(String(props.habit.startDate).slice(0, 10))
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
})
const total = computed(() => totalDone(props.habit, props.checkins))
const streak = computed(() => currentStreak(props.habit, props.checkins, props.today))
const longest = computed(() => longestStreak(props.habit, props.checkins, props.today))
const cells = computed(() => monthMatrix(cursor.value.year, cursor.value.month, props.today))
const stats = computed(() => monthStats(props.habit, props.checkins, cursor.value.year, cursor.value.month, props.today))
const monthDone = computed(() => stats.value.done)
const monthRate = computed(() => stats.value.rate)
const logs = computed(() => monthLogs(props.habit, props.checkins, `${cursor.value.year}-${String(cursor.value.month).padStart(2, '0')}`))
const isCurrentMonth = computed(() => cursor.value.year === Number(props.today.slice(0, 4)) && cursor.value.month === Number(props.today.slice(5, 7)))

function stateOf(key) {
  return dayState(props.habit, props.checkins, key, props.today)
}

function stateText(state) {
  return { done: '已完成', partial: '未达标', missed: '未完成', skip: '已跳过', todo: '待打卡', future: '未来', none: '不在这天' }[state] || ''
}

function logKey(log) {
  return String(log.date).slice(0, 10)
}

function logLine(log) {
  if (props.habit.targetType === 'QUANTITY') {
    const amount = Number(log.amount || 0)
    if (amount > 0) return `${amount}${amountUnit.value} · 目标 ${targetAmount(props.habit)}${amountUnit.value}`
    return ''
  }
  return ''
}

function partialHeight(key) {
  const checkin = props.checkins.find(item => item.habitId === props.habit.publicId && String(item.date).slice(0, 10) === key)
  return Math.round(progressOf(props.habit, checkin) * 100)
}

function moveMonth(delta) {
  const next = new Date(cursor.value.year, cursor.value.month - 1 + delta, 1)
  cursor.value = { year: next.getFullYear(), month: next.getMonth() + 1 }
}

function resetMonth() {
  cursor.value = currentCursor()
}

function toggleStreak() {
  streakMode.value = streakMode.value === 'current' ? 'longest' : 'current'
}

function pick(action, value) {
  menuOpen.value = false
  subMenu.value = ''
  if (action === 'style') emit('style', value)
  else if (action === 'focus') emit('focus', value)
  else emit(action)
}

function onDocPointerDown(event) {
  if (!menuOpen.value) return
  if (menuEl.value?.contains(event.target)) return
  menuOpen.value = false
  subMenu.value = ''
}

onMounted(() => document.addEventListener('pointerdown', onDocPointerDown, true))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocPointerDown, true))
</script>

<style scoped>
.hd { display: flex; flex-direction: column; gap: 10px; }

.hd-head { display: flex; align-items: center; gap: 10px; padding: 2px 2px 2px; }
.hd-icon { display: grid; width: 34px; height: 34px; flex: 0 0 auto; place-items: center; border-radius: 50%; font-size: 17px; line-height: 1; }
.hd-title { display: grid; flex: 1; min-width: 0; gap: 1px; }
.hd-name-row { display: flex; align-items: center; gap: 6px; }
.hd-title b { overflow: hidden; font-size: 15px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.hd-badge { padding: 1px 6px; border-radius: 5px; background: var(--paper); color: var(--muted); font-size: 10.5px; white-space: nowrap; }
.hd-sub { color: var(--muted); font-size: 11px; }
.hd-iconbtn { display: grid; width: 28px; height: 28px; place-items: center; border: 0; border-radius: 8px; background: transparent; color: var(--muted); cursor: pointer; }
.hd-iconbtn:hover { background: var(--paper); color: var(--ink); }

.hd-menu { position: relative; }
.hd-drop {
  position: absolute; z-index: 30; top: 32px; right: 0;
  display: grid; min-width: 168px; padding: 6px;
  border-radius: 14px; background: var(--card);
  box-shadow: 0 18px 44px rgb(15 23 42 / 18%), 0 0 0 1px var(--line);
}
.hd-drop > button, .hd-sub > button {
  display: flex; align-items: center; gap: 9px;
  width: 100%; padding: 8px 9px; border: 0; border-radius: 8px;
  background: transparent; color: var(--ink2); font: inherit; font-size: 13px; text-align: left; cursor: pointer;
}
.hd-drop > button:hover, .hd-sub > button:hover { background: var(--paper); color: var(--ink); }
.hd-drop > button.is-danger { color: var(--down, #e34d59); }
.hd-arrow { margin-left: auto; color: var(--dim, var(--muted)); }
.hd-sub { position: relative; }
.hd-sub-list {
  position: absolute; z-index: 31; top: 0; left: 100%;
  display: grid; min-width: 138px; margin-left: 4px; padding: 6px;
  border-radius: 12px; background: var(--card);
  box-shadow: 0 18px 44px rgb(15 23 42 / 18%), 0 0 0 1px var(--line);
}
.hd-sub-list button { padding: 8px 10px; border: 0; border-radius: 8px; background: transparent; color: var(--ink2); font: inherit; font-size: 12.5px; text-align: left; cursor: pointer; }
.hd-sub-list button:hover { background: var(--paper); color: var(--ink); }
.hd-sub-list button.on { color: var(--priority-low, #4d78db); font-weight: 600; }

.hd-stats { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.hd-stat {
  display: grid; gap: 4px; padding: 11px 13px;
  border: 0; border-radius: 12px;
  background: var(--card); text-align: left;
  box-shadow: 0 1px 2px rgb(15 23 42 / 6%), 0 0 0 1px var(--line);
}
.hd-stat.is-toggle { cursor: pointer; }
.hd-stat.is-toggle:hover { box-shadow: 0 3px 10px rgb(15 23 42 / 10%), 0 0 0 1px var(--line); }
.hd-stat-label { display: flex; align-items: center; gap: 4px; color: var(--muted); font-size: 11.5px; }
.hd-stat-label.is-green svg { color: #2fae74; }
.hd-stat-label.is-blue svg { color: var(--priority-low, #4d78db); }
.hd-stat-label.is-orange svg { color: #e8912d; }
.hd-stat-label.is-red svg { color: #e0503f; }
.hd-swap { margin-left: 2px; color: var(--dim, var(--muted)); }
.hd-stat b { font-size: 18px; font-weight: 600; font-variant-numeric: tabular-nums; }
.hd-stat em { margin-left: 3px; color: var(--muted); font-size: 11px; font-style: normal; font-weight: 400; }

.hd-cal { padding: 10px 12px 12px; border-radius: 14px; background: var(--card); box-shadow: 0 0 0 1px var(--line); }
.hd-cal-head { display: flex; align-items: center; justify-content: space-between; padding: 0 0 6px; }
.hd-cal-month { display: flex; align-items: center; gap: 6px; }
.hd-cal-month b { font-size: 13px; font-weight: 600; }
.hd-cal-today { padding: 1px 6px; border: 1px solid var(--line); border-radius: 6px; background: transparent; color: var(--muted); font: inherit; font-size: 10.5px; cursor: pointer; }
.hd-cal-nav { display: grid; width: 24px; height: 24px; place-items: center; border: 0; border-radius: 7px; background: transparent; color: var(--muted); cursor: pointer; }
.hd-cal-nav:hover { background: var(--paper); color: var(--ink); }
.hd-cal-week, .hd-cal-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 3px; }
.hd-cal-week span { padding-bottom: 3px; color: var(--dim, var(--muted)); font-size: 10px; text-align: center; }
.hd-cell {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  width: 100%; padding: 2px 0 3px;
  border: 0; border-radius: 9px;
  background: transparent; color: var(--ink2);
  font: inherit; cursor: pointer;
}
.hd-cell:not(:disabled):hover { background: color-mix(in srgb, var(--ink) 6%, transparent); }
.hd-cell:disabled { cursor: default; }
.hd-cell.is-out { opacity: .34; }
.hd-cell.is-today .hd-cell-day { color: var(--priority-low, #4d78db); font-weight: 600; }
.hd-cell-day { font-size: 10.5px; line-height: 1.2; font-variant-numeric: tabular-nums; }
.hd-cell-mark {
  display: grid; place-items: center; gap: 0;
  width: 22px; height: 22px; border-radius: 50%;
  font-size: 10px; line-height: 1;
}
.hd-cell-mark.is-done { background: var(--habit-done, #4d78db); color: #fff; }
.hd-cell-mark.is-done.is-today { box-shadow: 0 0 0 2px color-mix(in srgb, var(--habit-done, #4d78db) 26%, transparent); }
.hd-cell-mark.is-missed { color: var(--down, #e34d59); }
.hd-cell-mark.is-skip { color: var(--dim, var(--muted)); }
.hd-cell-dot { width: 4px; height: 4px; border-radius: 50%; background: currentColor; }
.hd-cell-emoji { font-size: 12px; }
.hd-cell-ring {
  position: relative; width: 20px; height: 20px; border-radius: 50%;
  background: conic-gradient(var(--habit-done, #4d78db) var(--fill, 50%), var(--line) 0);
  -webkit-mask: radial-gradient(circle, transparent 6px, #000 6.5px);
  mask: radial-gradient(circle, transparent 6px, #000 6.5px);
}

.hd-logs { display: grid; gap: 6px; padding: 10px 12px 12px; border-radius: 14px; background: var(--card); box-shadow: 0 0 0 1px var(--line); }
.hd-logs h3 { margin: 0; font-size: 13px; font-weight: 600; }
.hd-log-scroll { max-height: 246px; overflow-y: auto; scrollbar-width: thin; }
.hd-log-list { display: grid; gap: 2px; margin: 0; padding: 0; list-style: none; }
.hd-log-list li { position: relative; display: flex; gap: 8px; padding: 5px 2px 5px 0; }
.hd-log-list li::before { content: ''; position: absolute; top: 22px; bottom: -2px; left: 8px; width: 1px; background: var(--line); }
.hd-log-list li:last-child::before { display: none; }
.hd-log-mark { display: grid; width: 17px; height: 17px; flex: 0 0 auto; place-items: center; margin-top: 2px; border-radius: 50%; background: var(--habit-done, #4d78db); color: #fff; }
.hd-log-mark.is-skip { background: var(--line2); color: var(--muted); }
.hd-log-main { display: grid; flex: 1; min-width: 0; gap: 2px; }
.hd-log-top { display: flex; align-items: center; gap: 6px; color: var(--muted); font-size: 11.5px; }
.hd-log-date { color: var(--ink2); }
.hd-log-mood { font-size: 12px; line-height: 1; }
.hd-log-actions { display: none; gap: 2px; margin-left: auto; }
.hd-log-list li:hover .hd-log-actions { display: flex; }
.hd-log-actions button { display: grid; width: 20px; height: 20px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.hd-log-actions button:hover { background: var(--paper); color: var(--ink); }
.hd-log-text { margin: 0; color: var(--ink); font-size: 12.5px; font-variant-numeric: tabular-nums; }
.hd-log-note { margin: 0; color: var(--muted); font-size: 11.5px; line-height: 1.55; white-space: pre-wrap; word-break: break-word; }
.hd-logs-empty { margin: 0; padding: 8px 0 4px; color: var(--dim, var(--muted)); font-size: 11.5px; }
</style>
