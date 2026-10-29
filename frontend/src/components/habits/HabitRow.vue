<template>
  <li
    class="habit-row"
    :class="{ 'is-active': active, 'is-archived': habit.archived, 'is-selectable': selectable }"
    role="button"
    tabindex="0"
    :aria-label="`${habit.name}，${ariaSummary}`"
    @click="selectable ? emit('select') : emit('open')"
    @keydown.enter.prevent="selectable ? emit('select') : emit('open')"
  >
    <input
      v-if="selectable"
      type="checkbox"
      class="habit-pick"
      :checked="selected"
      :aria-label="`选择 ${habit.name}`"
      @click.stop="emit('select')"
    />
    <span class="habit-icon" :style="iconStyle">{{ habit.icon || '✓' }}</span>
    <div class="habit-row-main">
      <b class="habit-name">{{ habit.name }}</b>
      <span class="habit-meta">
        <span class="habit-goal" :title="`累计完成 ${total} 天`">
          <Flame :size="12" />{{ goalLabel(habit, total) }}
        </span>
        <span class="habit-streak" :class="{ 'is-zero': !streak }" :title="`当前连续 ${streak} 天`">
          <Zap :size="12" />{{ streak }} 天
        </span>
      </span>
    </div>
    <div class="habit-cells">
      <button
        v-for="day in week"
        :key="day.key"
        type="button"
        class="habit-cell"
        :class="`is-${stateOf(day.key)}`"
        :disabled="stateOf(day.key) === 'future' || stateOf(day.key) === 'none'"
        :title="`${dayLabel(day.key)} · ${stateText(stateOf(day.key))}`"
        :aria-label="`${habit.name} ${dayLabel(day.key)} ${stateText(stateOf(day.key))}`"
        @click.stop="emit('toggle', day.key)"
      >
        <span v-if="stateOf(day.key) === 'done' && habit.checkStyle === 'ICON'" class="habit-cell-icon">{{ habit.icon }}</span>
        <Check v-else-if="stateOf(day.key) === 'done'" :size="12" />
        <X v-else-if="stateOf(day.key) === 'missed'" :size="11" />
        <Minus v-else-if="stateOf(day.key) === 'skip'" :size="11" />
        <i v-else class="habit-cell-dot" />
      </button>
    </div>
  </li>
</template>

<script setup>
import { computed } from 'vue'
import { Check, Flame, Minus, X, Zap } from 'lucide-vue-next'
import { currentStreak, dayLabel, dayState, goalLabel, totalDone } from './habitUtils.js'

const props = defineProps({
  habit: { type: Object, required: true },
  checkins: { type: Array, default: () => [] },
  week: { type: Array, default: () => [] },
  today: { type: String, default: '' },
  active: { type: Boolean, default: false },
  selectable: { type: Boolean, default: false },
  selected: { type: Boolean, default: false }
})
const emit = defineEmits(['open', 'toggle', 'select'])

const total = computed(() => totalDone(props.habit, props.checkins))
const streak = computed(() => currentStreak(props.habit, props.checkins, props.today))
const ariaSummary = computed(() => `${goalLabel(props.habit, total.value)}，连续 ${streak.value} 天`)
const iconStyle = computed(() => ({
  background: `color-mix(in srgb, ${props.habit.color || 'var(--accent)'} 18%, transparent)`,
  color: props.habit.color || 'var(--accent)'
}))

function stateOf(key) {
  return dayState(props.habit, props.checkins, key, props.today)
}

function stateText(state) {
  return { done: '已完成', missed: '未完成', skip: '已跳过', todo: '待打卡', future: '未来', none: '不在这天' }[state] || ''
}
</script>

<style scoped>
.habit-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 10px;
  border: 1px solid transparent;
  border-radius: 12px;
  cursor: pointer;
  list-style: none;
  transition: background .14s ease, border-color .14s ease;
}
.habit-row:hover { background: color-mix(in srgb, var(--paper) 62%, transparent); }
.habit-row.is-active { border-color: color-mix(in srgb, var(--accent) 46%, transparent); background: color-mix(in srgb, var(--accent) 6%, transparent); }
.habit-row.is-archived { opacity: .72; }
.habit-row.is-selectable { background: color-mix(in srgb, var(--paper) 45%, transparent); }
.habit-pick { margin: 0 2px 0 0; accent-color: var(--accent); }

.habit-icon {
  display: grid;
  width: 28px; height: 28px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 9px;
  font-size: 14px;
  line-height: 1;
}

.habit-row-main { display: grid; flex: 1; min-width: 0; gap: 1px; }
.habit-name { overflow: hidden; color: var(--ink); font-size: 12.8px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.habit-meta { display: flex; align-items: center; gap: 8px; color: var(--muted); font-size: 10.5px; font-variant-numeric: tabular-nums; }
.habit-meta span { display: inline-flex; align-items: center; gap: 2px; }
.habit-goal svg { color: var(--warn, #e69b2d); }
.habit-streak svg { color: var(--priority-high, #e34d59); }
.habit-streak.is-zero { color: var(--dim, var(--muted)); }
.habit-streak.is-zero svg { color: currentColor; }

.habit-cells { display: grid; grid-template-columns: repeat(7, 21px); gap: 3px; flex: 0 0 auto; }
.habit-cell {
  display: grid;
  width: 21px; height: 21px;
  place-items: center;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 50%;
  background: transparent;
  color: var(--muted);
  transition: transform .1s ease, background .14s ease;
}
.habit-cell:not(:disabled):hover { transform: scale(1.12); }
.habit-cell:disabled { cursor: default; }
.habit-cell.is-done { background: var(--habit-done, #4d78db); color: #fff; }
.habit-cell.is-missed { color: var(--down, #e34d59); }
.habit-cell.is-skip { color: var(--dim, var(--muted)); }
.habit-cell.is-todo { border-color: var(--line2); }
.habit-cell.is-none, .habit-cell.is-future { color: var(--line2); }
.habit-cell-dot { display: block; width: 4px; height: 4px; border-radius: 50%; background: currentColor; }
.habit-cell-icon { font-size: 11px; line-height: 1; }

/* 窄的时候把 7 个格子放到第二行，占满宽度 */
@media (max-width: 1180px) {
  .habit-row { flex-wrap: wrap; }
  .habit-cells { order: 3; width: 100%; grid-template-columns: repeat(7, 1fr); margin-top: 4px; }
  .habit-cell { width: 100%; height: 20px; border-radius: 6px; }
}
</style>
