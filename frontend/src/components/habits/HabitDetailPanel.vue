<template>
  <section class="habit-detail" aria-label="习惯详情">
    <header class="habit-detail-head">
      <span class="habit-detail-icon" :style="iconStyle">{{ habit.icon || '✓' }}</span>
      <div class="habit-detail-title">
        <b>{{ habit.name }}</b>
        <span v-if="habit.archived" class="habit-badge">已归档</span>
        <small>{{ frequencyLabel(habit) }} · 开始于 {{ startLabel }}</small>
      </div>
      <button v-if="closable" type="button" class="habit-detail-iconbtn" aria-label="关闭详情" @click="emit('close')"><X :size="15" /></button>
      <div ref="menuEl" class="habit-detail-menu">
        <button
          type="button"
          class="habit-detail-iconbtn"
          aria-label="习惯操作"
          :aria-expanded="menuOpen"
          @click.stop="menuOpen = !menuOpen"
        >
          <MoreHorizontal :size="16" />
        </button>
        <div v-if="menuOpen" class="habit-detail-dropdown" @click.stop>
          <button type="button" @click="pick('edit')"><Pencil :size="14" />编辑</button>
          <div class="habit-style-group">
            <span><LayoutGrid :size="14" />打卡样式</span>
            <button type="button" :class="{ on: (habit.checkStyle || 'CHECK') === 'CHECK' }" @click="pick('style', 'CHECK')"><Check :size="14" />对勾</button>
            <button type="button" :class="{ on: habit.checkStyle === 'ICON' }" @click="pick('style', 'ICON')"><LayoutGrid :size="14" />习惯图标</button>
          </div>
          <button type="button" @click="pick('archive')"><Archive :size="14" />{{ habit.archived ? '恢复' : '归档' }}</button>
          <button type="button" class="is-danger" @click="pick('remove')"><Trash2 :size="14" />删除</button>
        </div>
      </div>
    </header>

    <div class="habit-stats" :class="{ 'is-quantity': habit.targetType === 'QUANTITY' }">
      <div class="habit-stat">
        <span><BadgeCheck :size="13" />月打卡</span>
        <b>{{ monthDone }}<em>天</em></b>
      </div>
      <div class="habit-stat">
        <span><Zap :size="13" />总打卡</span>
        <b>{{ total }}<em>天</em></b>
      </div>
      <div class="habit-stat">
        <span><Percent :size="13" />月完成率</span>
        <b>{{ monthRate }}<em>%</em></b>
      </div>
      <div class="habit-stat">
        <span><Flame :size="13" />当前连续</span>
        <b>{{ streak }}<em>天</em></b>
      </div>
      <template v-if="habit.targetType === 'QUANTITY'">
        <div class="habit-stat">
          <span><Droplets :size="13" />月完成量</span>
          <b>{{ monthAmount }}<em>{{ amountUnit }}</em></b>
        </div>
        <div class="habit-stat">
          <span><BarChart3 :size="13" />总完成量</span>
          <b>{{ totalAmount }}<em>{{ amountUnit }}</em></b>
        </div>
      </template>
    </div>

    <div class="habit-cal">
      <header class="habit-cal-head">
        <button type="button" class="habit-cal-nav" aria-label="上个月" @click="moveMonth(-1)"><ChevronLeft :size="15" /></button>
        <b>{{ cursor.year }}年{{ cursor.month }}月</b>
        <button type="button" class="habit-cal-nav" aria-label="下个月" @click="moveMonth(1)"><ChevronRight :size="15" /></button>
      </header>
      <div class="habit-cal-week">
        <span v-for="label in WEEKDAY_LABELS" :key="label">{{ label }}</span>
      </div>
      <div class="habit-cal-grid">
        <button
          v-for="cell in cells"
          :key="cell.key"
          type="button"
          class="habit-cal-cell"
          :class="[`is-${stateOf(cell.key)}`, { 'is-out': !cell.inMonth, 'is-today': cell.isToday }]"
          :disabled="stateOf(cell.key) === 'future' || stateOf(cell.key) === 'none'"
          :title="`${dayLabel(cell.key)} · ${stateText(stateOf(cell.key))}`"
          @click="emit('toggle', cell.key)"
        >
          <span>{{ cell.day }}</span>
          <i v-if="stateOf(cell.key) === 'done'" class="habit-cal-dot" />
          <i v-else-if="stateOf(cell.key) === 'skip'" class="habit-cal-dash" />
        </button>
      </div>
    </div>

    <div class="habit-logs">
      <h3>{{ cursor.month }}月打卡日志</h3>
      <ul v-if="logs.length">
        <li v-for="log in logs" :key="log.publicId">
          <span class="habit-log-date">{{ dayLabel(String(log.date).slice(0, 10)) }}</span>
          <span class="habit-log-state" :class="{ 'is-skip': log.status === 'SKIP' }">
            {{ log.status === 'SKIP' ? '跳过' : '完成' }}<template v-if="habit.targetType === 'QUANTITY' && log.amount"> · {{ log.amount }}{{ amountUnit }}</template>
            <template v-else-if="doneTarget(habit) > 1"> · {{ log.count }}/{{ doneTarget(habit) }} 次</template>
          </span>
        </li>
      </ul>
      <p v-else class="habit-logs-empty">这个月没有打卡日志哦</p>
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  Archive, BadgeCheck, BarChart3, Check, ChevronLeft, ChevronRight, Droplets,
  Flame, LayoutGrid, MoreHorizontal, Pencil, Percent, Trash2, X, Zap
} from 'lucide-vue-next'
import {
  WEEKDAY_LABELS, currentStreak, dayLabel, dayState, doneTarget, findCheckin,
  frequencyLabel, monthLogs, monthMatrix, parseKey, scheduledOn, totalDone
} from './habitUtils.js'

const props = defineProps({
  habit: { type: Object, required: true },
  checkins: { type: Array, default: () => [] },
  today: { type: String, default: '' },
  closable: { type: Boolean, default: false }
})
const emit = defineEmits(['toggle', 'edit', 'style', 'archive', 'remove', 'close'])

const menuEl = ref(null)
const menuOpen = ref(false)
const cursor = ref({ year: Number(props.today.slice(0, 4)), month: Number(props.today.slice(5, 7)) })

watch(() => props.habit?.publicId, () => {
  menuOpen.value = false
  cursor.value = { year: Number(props.today.slice(0, 4)), month: Number(props.today.slice(5, 7)) }
})

const iconStyle = computed(() => ({
  background: `color-mix(in srgb, ${props.habit.color || 'var(--accent)'} 18%, transparent)`,
  color: props.habit.color || 'var(--accent)'
}))
const startLabel = computed(() => {
  if (!props.habit.startDate) return '—'
  const date = parseKey(String(props.habit.startDate).slice(0, 10))
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
})
const amountUnit = computed(() => props.habit.amountUnit || '')
const total = computed(() => totalDone(props.habit, props.checkins))
const streak = computed(() => currentStreak(props.habit, props.checkins, props.today))
const cells = computed(() => monthMatrix(cursor.value.year, cursor.value.month, props.today))
const logs = computed(() => monthLogs(props.habit, props.checkins, `${cursor.value.year}-${String(cursor.value.month).padStart(2, '0')}`))

const monthDone = computed(() => cells.value.filter(cell => cell.inMonth && stateOf(cell.key) === 'done').length)
const monthTarget = computed(() => cells.value.filter(cell => cell.inMonth && scheduledOn(props.habit, cell.key)
  && cell.key <= props.today && cell.key >= String(props.habit.startDate || '').slice(0, 10)).length)
const monthRate = computed(() => (monthTarget.value ? Math.round((monthDone.value * 100) / monthTarget.value) : 0))

const monthAmount = computed(() => {
  const prefix = `${cursor.value.year}-${String(cursor.value.month).padStart(2, '0')}`
  return sumAmount(item => String(item.date).slice(0, 7) === prefix)
})
const totalAmount = computed(() => sumAmount(() => true))

function sumAmount(filter) {
  return props.checkins
    .filter(item => item.habitId === props.habit.publicId && filter(item))
    .reduce((sum, item) => sum + Number(item.amount || 0), 0)
}

function stateOf(key) {
  return dayState(props.habit, props.checkins, key, props.today)
}

function stateText(state) {
  return { done: '已完成', missed: '未完成', skip: '已跳过', todo: '待打卡', future: '未来', none: '不在这天' }[state] || ''
}

function moveMonth(delta) {
  const next = new Date(cursor.value.year, cursor.value.month - 1 + delta, 1)
  cursor.value = { year: next.getFullYear(), month: next.getMonth() + 1 }
}

function pick(action, value) {
  menuOpen.value = false
  if (action === 'style') emit('style', value)
  else emit(action)
}

function onDocPointerDown(event) {
  if (!menuOpen.value) return
  if (menuEl.value?.contains(event.target)) return
  menuOpen.value = false
}

onMounted(() => document.addEventListener('pointerdown', onDocPointerDown, true))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocPointerDown, true))
</script>

<style scoped>
.habit-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 18px 18px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--card);
}

.habit-detail-head { display: flex; align-items: center; gap: 10px; }
.habit-detail-icon { display: grid; width: 34px; height: 34px; flex: 0 0 auto; place-items: center; border-radius: 11px; font-size: 17px; line-height: 1; }
.habit-detail-title { display: grid; flex: 1; min-width: 0; gap: 1px; }
.habit-detail-title b { overflow: hidden; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.habit-detail-title small { color: var(--muted); font-size: 11px; }
.habit-badge { justify-self: start; padding: 1px 6px; border-radius: 5px; background: var(--paper); color: var(--muted); font-size: 10.5px; }
.habit-detail-iconbtn { display: grid; width: 28px; height: 28px; place-items: center; border: 0; border-radius: 8px; background: transparent; color: var(--muted); cursor: pointer; }
.habit-detail-iconbtn:hover { background: var(--paper); color: var(--ink); }

.habit-detail-menu { position: relative; }
.habit-detail-dropdown {
  position: absolute; z-index: 30; top: 34px; right: 0;
  display: grid; min-width: 168px; padding: 6px;
  border: 1px solid var(--line); border-radius: 12px;
  background: var(--card); box-shadow: var(--module-shadow, 0 16px 40px rgb(15 23 42 / 18%));
}
.habit-detail-dropdown button,
.habit-style-group button {
  display: flex; align-items: center; gap: 8px;
  width: 100%; padding: 7px 9px; border: 0; border-radius: 8px;
  background: transparent; color: var(--ink2); font: inherit; font-size: 12.5px; text-align: left; cursor: pointer;
}
.habit-detail-dropdown button:hover,
.habit-style-group button:hover { background: var(--paper); color: var(--ink); }
.habit-detail-dropdown button.is-danger { color: var(--down, #e34d59); }
.habit-style-group { display: grid; gap: 2px; margin: 4px 0; padding: 4px 0; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); }
.habit-style-group > span { display: flex; align-items: center; gap: 8px; padding: 4px 9px; color: var(--muted); font-size: 11px; }
.habit-style-group button { padding-left: 20px; }
.habit-style-group button.on { color: var(--accent); font-weight: 600; }

.habit-stats { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.habit-stat { display: grid; gap: 3px; padding: 10px 12px; border-radius: 12px; background: var(--paper); }
.habit-stat span { display: flex; align-items: center; gap: 5px; color: var(--muted); font-size: 11px; }
.habit-stat b { font-size: 17px; font-weight: 600; font-variant-numeric: tabular-nums; }
.habit-stat em { margin-left: 2px; color: var(--muted); font-size: 11px; font-style: normal; font-weight: 400; }

.habit-cal { padding: 10px 12px 12px; border-radius: 14px; background: var(--paper); }
.habit-cal-head { display: flex; align-items: center; justify-content: space-between; padding: 2px 2px 8px; }
.habit-cal-head b { font-size: 12.5px; font-weight: 600; }
.habit-cal-nav { display: grid; width: 24px; height: 24px; place-items: center; border: 0; border-radius: 7px; background: transparent; color: var(--muted); cursor: pointer; }
.habit-cal-nav:hover { background: var(--card); color: var(--ink); }
.habit-cal-week, .habit-cal-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 2px; }
.habit-cal-week span { padding-bottom: 4px; color: var(--muted); font-size: 10.5px; text-align: center; }
.habit-cal-cell {
  position: relative;
  display: grid; place-items: center;
  width: 100%; max-width: 32px; justify-self: center;
  aspect-ratio: 1;
  border: 0; border-radius: 50%;
  background: transparent;
  color: var(--ink2);
  font: inherit; font-size: 11.5px;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}
.habit-cal-cell:not(:disabled):hover { background: color-mix(in srgb, var(--accent) 10%, transparent); }
.habit-cal-cell:disabled { cursor: default; }
.habit-cal-cell.is-out { color: color-mix(in srgb, var(--muted) 55%, transparent); }
.habit-cal-cell.is-today { box-shadow: inset 0 0 0 1px var(--line2); }
.habit-cal-cell.is-done { background: var(--habit-done, #4d78db); color: #fff; font-weight: 600; }
.habit-cal-cell.is-missed { color: var(--down, #e34d59); }
.habit-cal-cell.is-skip { color: var(--dim, var(--muted)); }
.habit-cal-dot { position: absolute; bottom: 3px; width: 3px; height: 3px; border-radius: 50%; background: currentColor; opacity: .85; }
.habit-cal-dash { position: absolute; bottom: 4px; width: 6px; height: 1.5px; border-radius: 1px; background: currentColor; }

.habit-logs { display: grid; gap: 6px; }
.habit-logs h3 { margin: 0; font-size: 12px; font-weight: 600; }
.habit-logs ul { display: grid; gap: 2px; margin: 0; padding: 0; list-style: none; }
.habit-logs li { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 6px 8px; border-radius: 8px; background: var(--paper); font-size: 11.5px; }
.habit-log-date { color: var(--ink2); }
.habit-log-state { color: var(--accent); font-variant-numeric: tabular-nums; }
.habit-log-state.is-skip { color: var(--muted); }
.habit-logs-empty { margin: 0; padding: 14px 0; color: var(--muted); font-size: 11.5px; text-align: center; }
</style>
