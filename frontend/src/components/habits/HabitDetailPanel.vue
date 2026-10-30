<template>
  <section class="hd" aria-label="习惯详情">
    <header class="hd-head">
      <span class="hd-icon" :style="iconStyle">{{ habit.icon || '✓' }}</span>
      <div class="hd-title">
        <b :title="`${frequencyLabel(habit)} · 开始于 ${startLabel}`">{{ habit.name }}</b>
        <small v-if="habit.archived" class="hd-badge">已归档</small>
      </div>
      <button v-if="closable" type="button" class="hd-iconbtn" aria-label="关闭详情" @click="emit('close')"><X :size="16" /></button>
      <div ref="menuEl" class="hd-menu">
        <button type="button" class="hd-iconbtn" aria-label="习惯操作" :aria-expanded="menuOpen" @click.stop="menuOpen = !menuOpen"><MoreHorizontal :size="17" /></button>
        <div v-if="menuOpen" class="hd-drop" @click.stop>
          <template v-if="habit.archived">
            <button type="button" @click="pick('archive')"><ArchiveRestore :size="16" />恢复习惯</button>
            <button type="button" class="is-danger" @click="pick('remove')"><Trash2 :size="16" />删除</button>
          </template>
          <template v-else>
            <button type="button" @click="pick('edit')"><Pencil :size="16" />编辑</button>
            <div class="hd-sub item" :class="{ open: subMenu === 'focus' }">
              <button type="button" @click.stop="subMenu = subMenu === 'focus' ? '' : 'focus'" @mouseenter="subMenu = 'focus'">
                <CircleDot :size="16" />开始专注<ChevronRight :size="14" class="hd-arrow" />
              </button>
              <div v-if="subMenu === 'focus'" class="hd-sub-list" @mouseleave="subMenu = ''">
                <button type="button" @click="pick('focus', 'POMODORO')">开始番茄专注</button>
                <button type="button" @click="pick('focus', 'TIMER')">开始正计时</button>
              </div>
            </div>
            <div class="hd-sub item" :class="{ open: subMenu === 'style' }">
              <button type="button" @click.stop="subMenu = subMenu === 'style' ? '' : 'style'" @mouseenter="subMenu = 'style'">
                <CircleCheck :size="16" />打卡样式<ChevronRight :size="14" class="hd-arrow" />
              </button>
              <div v-if="subMenu === 'style'" class="hd-sub-list" @mouseleave="subMenu = ''">
                <button type="button" :class="{ on: (habit.checkStyle || 'CHECK') === 'CHECK' }" @click="pick('style', 'CHECK')">对勾</button>
                <button type="button" :class="{ on: habit.checkStyle === 'ICON' }" @click="pick('style', 'ICON')">习惯图标</button>
              </div>
            </div>
            <button type="button" @click="pick('archive')"><FolderInput :size="16" />归档</button>
            <button type="button" class="is-danger" @click="pick('remove')"><Trash2 :size="16" />删除</button>
          </template>
        </div>
      </div>
    </header>

    <div class="hd-stats">
      <div class="hd-stat">
        <span class="hd-stat-label is-green"><BadgeCheck :size="14" />月打卡</span>
        <b>{{ monthDone }}<em>天</em></b>
      </div>
      <div class="hd-stat">
        <span class="hd-stat-label is-blue"><Zap :size="14" />总打卡</span>
        <b>{{ total }}<em>天</em></b>
      </div>
      <div class="hd-stat">
        <span class="hd-stat-label is-orange"><Percent :size="14" />月完成率</span>
        <b>{{ monthRate }}<em>%</em></b>
      </div>
      <button type="button" class="hd-stat is-toggle" :aria-label="streakMode === 'current' ? '切换为最高连续' : '切换为当前连续'" @click="toggleStreak">
        <span class="hd-stat-label is-red">
          <Flame :size="14" />{{ streakMode === 'current' ? '当前连续' : '最高连续' }}
          <ArrowLeftRight :size="11" class="hd-swap" />
        </span>
        <b>{{ streakMode === 'current' ? streak : longest }}<em>天</em></b>
      </button>
    </div>

    <div class="hd-cal">
      <header class="hd-cal-head">
        <button type="button" class="hd-cal-nav" aria-label="上个月" @click="moveMonth(-1)"><ChevronLeft :size="17" /></button>
        <div class="hd-cal-month">
          <b>{{ monthLabel }}</b>
          <button v-if="!isCurrentMonth" type="button" class="hd-cal-today" @click="resetMonth">今天</button>
        </div>
        <button type="button" class="hd-cal-nav" aria-label="下个月" @click="moveMonth(1)"><ChevronRight :size="17" /></button>
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
          :title="`${cell.day}日 · ${stateText(stateOf(cell.key))}`"
          @click="emit('toggle', cell.key)"
        >
          <template v-if="stateOf(cell.key) === 'done'">
            <span v-if="habit.checkStyle === 'ICON'" class="hd-cell-emoji">{{ habit.icon }}</span>
            <Check v-else :size="14" :stroke-width="3" />
          </template>
          <X v-else-if="stateOf(cell.key) === 'missed'" :size="14" :stroke-width="3" />
          <i v-else-if="stateOf(cell.key) === 'skip'" class="hd-cell-dot" />
          <i v-else-if="stateOf(cell.key) === 'partial'" class="hd-cell-ring" :style="{ '--fill': `${partialHeight(cell.key)}%` }" />
        </button>
      </div>
    </div>

    <div class="hd-logs">
      <h3>记录日志</h3>
      <div v-if="logs.length" class="hd-log-scroll">
        <ul class="hd-log-list">
          <li v-for="log in logs" :key="log.publicId">
            <span class="hd-log-mark" :class="{ 'is-skip': log.status === 'SKIP' }">
              <Check v-if="log.status !== 'SKIP'" :size="12" :stroke-width="3" />
              <X v-else :size="12" :stroke-width="3" />
            </span>
            <div class="hd-log-main">
              <div class="hd-log-top">
                <span class="hd-log-date">{{ logDateLabel(logKey(log)) }}</span>
                <span class="hd-log-week">{{ weekdayLabel(logKey(log)) }}</span>
                <span v-if="log.mood" class="hd-log-mood">{{ log.mood }}</span>
                <span class="hd-log-actions">
                  <button type="button" aria-label="编辑日志" @click="emit('log', log)"><Pencil :size="13" /></button>
                  <button type="button" aria-label="删除日志" @click="emit('deleteLog', log)"><Trash2 :size="13" /></button>
                </span>
              </div>
              <p v-if="log.note" class="hd-log-note">{{ log.note }}</p>
              <p v-else-if="logLine(log)" class="hd-log-text">{{ logLine(log) }}</p>
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
import { useExclusivePanels } from '../../utils/exclusivePanels.js'

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

// 「…」菜单和页面其它浮层互斥：页面开了菜单，这里自动收起
useExclusivePanels('habit-detail', [
  ['menu', () => menuOpen.value, value => {
    menuOpen.value = value
    if (!value) subMenu.value = ''
  }]
])

function onKeydown(event) {
  if (event.key !== 'Escape' || !menuOpen.value) return
  if (subMenu.value) subMenu.value = ''
  else menuOpen.value = false
}

function currentCursor() {
  return { year: Number(props.today.slice(0, 4)), month: Number(props.today.slice(5, 7)) }
}

watch(() => props.habit?.publicId, () => {
  menuOpen.value = false
  subMenu.value = ''
  cursor.value = currentCursor()
})

const iconStyle = computed(() => ({
  background: props.habit.color ? `color-mix(in srgb, ${props.habit.color} 78%, #fff)` : 'var(--paper)',
  color: props.habit.color || 'var(--accent)'
}))
const amountUnit = computed(() => props.habit.amountUnit || '')
const startLabel = computed(() => {
  if (!props.habit.startDate) return '—'
  const date = parseKey(String(props.habit.startDate).slice(0, 10))
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
})
const monthLabel = computed(() => `${['一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '十一', '十二'][cursor.value.month - 1]}月`)
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

onMounted(() => {
  document.addEventListener('pointerdown', onDocPointerDown, true)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocPointerDown, true)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.hd { display: flex; flex-direction: column; gap: 10px; }

.hd-head { display: flex; align-items: center; gap: 11px; padding: 2px 2px 5px; }
.hd-icon { display: grid; width: 40px; height: 40px; flex: 0 0 auto; place-items: center; border-radius: 50%; font-size: 21px; line-height: 1; }
.hd-title { display: flex; flex: 1; min-width: 0; align-items: center; gap: 6px; }
.hd-title b { overflow: hidden; font-size: 16.5px; font-weight: 600; letter-spacing: -.01em; text-overflow: ellipsis; white-space: nowrap; }
.hd-badge { flex: 0 0 auto; padding: 1px 6px; border-radius: 5px; background: var(--paper); color: var(--muted); font-size: 10.5px; white-space: nowrap; }
.hd-iconbtn { display: grid; width: 28px; height: 28px; place-items: center; border: 0; border-radius: 8px; background: transparent; color: var(--muted); cursor: pointer; }
.hd-iconbtn:hover { background: color-mix(in srgb, var(--ink) 7%, transparent); color: var(--ink); }

/* ⌯ 菜单：白卡 14 圆角，行高 34，图标 + 文案 */
.hd-menu { position: relative; }
.hd-drop {
  position: absolute; z-index: 30; top: 34px; right: 0;
  display: grid; gap: 1px; min-width: 176px; padding: 6px;
  border-radius: 14px; background: var(--card);
  box-shadow: 0 18px 44px rgb(15 23 42 / 18%), 0 0 0 1px var(--line);
}
.hd-drop > button, .hd-sub > button {
  display: flex; align-items: center; gap: 10px;
  width: 100%; padding: 8px 10px; border: 0; border-radius: 8px;
  background: transparent; color: var(--ink2); font: inherit; font-size: 13px; text-align: left; cursor: pointer;
}
.hd-drop > button:hover, .hd-sub > button:hover { background: color-mix(in srgb, var(--ink) 6%, transparent); color: var(--ink); }
.hd-drop > button.is-danger { color: var(--down, #e34d59); }
.hd-arrow { margin-left: auto; color: var(--dim, var(--muted)); }
.hd-sub { position: relative; }
/* 详情栏贴着窗口右侧，二级菜单统一往左展开，避免超出可视区 */
.hd-sub-list {
  position: absolute; z-index: 31; top: -6px; right: 100%;
  display: grid; gap: 1px; min-width: 150px; margin-right: 6px; padding: 6px;
  border-radius: 14px; background: var(--card);
  box-shadow: 0 18px 44px rgb(15 23 42 / 18%), 0 0 0 1px var(--line);
}
.hd-sub-list button { padding: 8px 10px; border: 0; border-radius: 8px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; text-align: left; cursor: pointer; }
.hd-sub-list button:hover { background: color-mix(in srgb, var(--ink) 6%, transparent); color: var(--ink); }
.hd-sub-list button.on { color: #3d8fce; font-weight: 600; }

.hd-stats { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.hd-stat {
  display: grid; gap: 8px; padding: 15px 16px;
  border: 0; border-radius: 10px;
  background: var(--card); text-align: left;
  box-shadow: 0 1px 2px rgb(35 43 57 / 6%), 0 0 0 1px rgb(35 43 57 / 4%);
}
.hd-stat.is-toggle { cursor: pointer; }
.hd-stat.is-toggle:hover { box-shadow: 0 4px 12px rgb(35 43 57 / 10%); }
.hd-stat-label { display: flex; align-items: center; gap: 5px; color: var(--ink2); font-size: 12px; }
.hd-stat-label.is-green svg { color: #2fae74; }
.hd-stat-label.is-blue svg { color: #3d8fce; }
.hd-stat-label.is-orange svg { color: #e8912d; }
.hd-stat-label.is-red svg { color: #e0503f; }
.hd-swap { margin-left: 1px; color: var(--dim, var(--muted)); }
.hd-stat b { font-size: 22px; font-weight: 600; letter-spacing: -.02em; font-variant-numeric: tabular-nums; }
.hd-stat em { margin-left: 3px; color: var(--muted); font-size: 12px; font-style: normal; font-weight: 400; }

.hd-cal { padding: 12px 10px 12px; border-radius: 10px; background: var(--card); box-shadow: 0 1px 2px rgb(35 43 57 / 6%), 0 0 0 1px rgb(35 43 57 / 4%); }
.hd-cal-head { display: flex; align-items: center; justify-content: space-between; padding: 0 0 8px; }
.hd-cal-month { display: flex; align-items: center; gap: 6px; }
.hd-cal-month b { font-size: 14.5px; font-weight: 600; }
.hd-cal-today { padding: 1px 7px; border: 1px solid var(--line); border-radius: 6px; background: transparent; color: var(--muted); font: inherit; font-size: 10.5px; cursor: pointer; }
.hd-cal-nav { display: grid; width: 26px; height: 26px; place-items: center; border: 0; border-radius: 7px; background: transparent; color: var(--muted); cursor: pointer; }
.hd-cal-nav:hover { background: color-mix(in srgb, var(--ink) 7%, transparent); color: var(--ink); }
.hd-cal-week { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 2px 4px; padding-bottom: 6px; }
.hd-cal-week span { color: var(--dim, var(--muted)); font-size: 10.5px; text-align: center; }
.hd-cal-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 6px 0; justify-items: center; }

/* 参考图：日历格子就是圆形本身（没有日期数字），完成=蓝底白勾，漏打=红叉，未来=浅灰圆 */
.hd-cell {
  display: grid;
  width: 100%;
  max-width: 30px;
  aspect-ratio: 1;
  justify-self: center;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: color-mix(in srgb, var(--ink) 5%, transparent);
  color: var(--muted);
  font: inherit;
  cursor: pointer;
  transition: transform .1s ease, background .14s ease;
}
.hd-cell:not(:disabled):hover { transform: scale(1.08); }
.hd-cell:disabled { cursor: default; }
.hd-cell.is-out { opacity: .3; }
.hd-cell.is-done { background: #3d8fce; color: #fff; }
.hd-cell.is-done:hover { background: #3480ba; }
.hd-cell.is-missed { background: transparent; color: #e34d59; box-shadow: inset 0 0 0 1.5px color-mix(in srgb, #e34d59 30%, transparent); }
.hd-cell.is-skip { background: transparent; color: var(--dim, var(--muted)); }
.hd-cell.is-todo { background: transparent; box-shadow: inset 0 0 0 1.5px var(--line2); }
.hd-cell.is-today { box-shadow: inset 0 0 0 2px #3d8fce; }
.hd-cell.is-today.is-done { box-shadow: 0 0 0 2px color-mix(in srgb, #3d8fce 28%, transparent); }
.hd-cell.is-future, .hd-cell.is-none { background: color-mix(in srgb, var(--ink) 4%, transparent); }
.hd-cell-dot { width: 5px; height: 5px; border-radius: 50%; background: currentColor; }
.hd-cell-emoji { font-size: 13px; }
.hd-cell-ring {
  width: 62%; height: 62%; border-radius: 50%;
  background: conic-gradient(#3d8fce var(--fill, 50%), var(--line) 0);
  -webkit-mask: radial-gradient(circle, transparent 55%, #000 57%);
  mask: radial-gradient(circle, transparent 55%, #000 57%);
}

.hd-logs { display: grid; gap: 8px; padding: 12px 14px 14px; border-radius: 10px; background: var(--card); box-shadow: 0 1px 2px rgb(35 43 57 / 6%), 0 0 0 1px rgb(35 43 57 / 4%); }
.hd-logs h3 { margin: 0; font-size: 14.5px; font-weight: 600; }
.hd-log-scroll { max-height: 258px; overflow-y: auto; scrollbar-width: thin; }
.hd-log-list { display: grid; gap: 0; margin: 0; padding: 0; list-style: none; }
.hd-log-list li { position: relative; display: flex; gap: 10px; padding: 4px 2px 16px 0; }
.hd-log-list li::before { content: ''; position: absolute; top: 26px; bottom: 2px; left: 9.5px; width: 1px; background: color-mix(in srgb, #3d8fce 22%, transparent); }
.hd-log-list li:last-child::before { display: none; }
.hd-log-mark { display: grid; width: 20px; height: 20px; flex: 0 0 auto; place-items: center; margin-top: 1px; border-radius: 50%; background: #3d8fce; color: #fff; }
.hd-log-mark.is-skip { background: #5a6b7d; }
.hd-log-main { display: grid; flex: 1; min-width: 0; gap: 5px; }
.hd-log-top { display: flex; align-items: center; gap: 6px; color: var(--muted); font-size: 12px; }
.hd-log-date { color: var(--ink2); }
.hd-log-mood { font-size: 13px; line-height: 1; }
.hd-log-actions { display: none; gap: 2px; margin-left: auto; }
.hd-log-list li:hover .hd-log-actions { display: flex; }
.hd-log-actions button { display: grid; width: 22px; height: 22px; place-items: center; border: 0; border-radius: 6px; background: transparent; color: var(--muted); cursor: pointer; }
.hd-log-actions button:hover { background: color-mix(in srgb, var(--ink) 7%, transparent); color: var(--ink); }
.hd-log-text { margin: 0; color: var(--ink); font-size: 12.5px; font-variant-numeric: tabular-nums; }
.hd-log-note { margin: 0; color: var(--ink2); font-size: 13px; line-height: 1.5; white-space: pre-wrap; word-break: break-word; }
.hd-logs-empty { margin: 0; padding: 2px 0 4px; color: var(--dim, var(--muted)); font-size: 12px; }
</style>
