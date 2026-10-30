<template>
  <li
    class="hr"
    :class="{ 'is-active': active, 'is-grid': layout === 'grid', 'is-selectable': selectable }"
    role="button"
    tabindex="0"
    :aria-label="`${habit.name}，${ariaSummary}`"
    @click="selectable ? emit('select') : emit('open')"
    @contextmenu.prevent="emit('menu', $event)"
    @keydown.enter.prevent="selectable ? emit('select') : emit('open')"
  >
    <input
      v-if="selectable"
      type="checkbox"
      class="hr-pick"
      :checked="selected"
      :aria-label="`选择 ${habit.name}`"
      @click.stop="emit('select')"
    />

    <div class="hr-body">
      <span class="hr-icon" :style="iconStyle">{{ habit.icon || '✓' }}</span>
      <div class="hr-main">
        <b class="hr-name">{{ habit.name }}</b>
        <span class="hr-meta">
          <span class="hr-goal" :title="`累计完成 ${total} 天`"><Flame :size="12" />{{ goalLabel(habit, total) }}</span>
          <span class="hr-streak" :class="{ 'is-zero': !streak }" :title="`当前连续 ${streak} 天`"><Zap :size="12" />{{ streak }} 天</span>
        </span>
      </div>
    </div>

    <div v-if="layout === 'list'" class="hr-cells">
      <button
        v-for="day in week"
        :key="day.key"
        type="button"
        class="hr-cell"
        :class="`is-${stateOf(day.key)}`"
        :disabled="stateOf(day.key) === 'future' || stateOf(day.key) === 'none'"
        :title="`${dayLabel(day.key)} · ${stateText(stateOf(day.key))}`"
        :aria-label="`${habit.name} ${dayLabel(day.key)} ${stateText(stateOf(day.key))}`"
        @click.stop="emit('toggle', day.key)"
      >
        <span v-if="stateOf(day.key) === 'done' && habit.checkStyle === 'ICON'" class="hr-cell-icon">{{ habit.icon }}</span>
        <Check v-else-if="stateOf(day.key) === 'done'" :size="12" />
        <X v-else-if="stateOf(day.key) === 'missed'" :size="11" />
        <Minus v-else-if="stateOf(day.key) === 'skip'" :size="11" />
        <i v-else class="hr-cell-dot" />
        <i v-if="stateOf(day.key) === 'partial'" class="hr-cell-half" :style="{ height: `${partialHeight(day.key)}%` }" />
      </button>
    </div>

    <button
      v-else
      type="button"
      class="hr-today"
      :class="`is-${stateOf(today)}`"
      :disabled="stateOf(today) === 'future' || stateOf(today) === 'none'"
      :aria-label="`${habit.name} 今天 ${stateText(stateOf(today))}`"
      @click.stop="emit('toggle', today)"
    >
      <span v-if="stateOf(today) === 'done' && habit.checkStyle === 'ICON'" class="hr-cell-icon">{{ habit.icon }}</span>
      <Check v-else-if="stateOf(today) === 'done'" :size="13" />
      <X v-else-if="stateOf(today) === 'missed'" :size="12" />
      <Minus v-else-if="stateOf(today) === 'skip'" :size="12" />
      <i v-else class="hr-cell-dot" />
    </button>
  </li>
</template>

<script setup>
import { computed } from 'vue'
import { Check, Flame, Minus, X, Zap } from 'lucide-vue-next'
import { currentStreak, dayLabel, dayState, goalLabel, progressOf, totalDone } from './habitUtils.js'

const props = defineProps({
  habit: { type: Object, required: true },
  checkins: { type: Array, default: () => [] },
  week: { type: Array, default: () => [] },
  today: { type: String, default: '' },
  layout: { type: String, default: 'list' },
  active: { type: Boolean, default: false },
  selectable: { type: Boolean, default: false },
  selected: { type: Boolean, default: false }
})
const emit = defineEmits(['open', 'toggle', 'select', 'menu'])

const total = computed(() => totalDone(props.habit, props.checkins))
const streak = computed(() => currentStreak(props.habit, props.checkins, props.today))
const ariaSummary = computed(() => `${goalLabel(props.habit, total.value)}，连续 ${streak.value} 天`)
const iconStyle = computed(() => ({
  background: `color-mix(in srgb, ${props.habit.color || 'var(--accent)'} 22%, transparent)`,
  color: props.habit.color || 'var(--accent)'
}))

function stateOf(key) {
  return dayState(props.habit, props.checkins, key, props.today)
}

function stateText(state) {
  return { done: '已完成', partial: '未达标', missed: '未完成', skip: '已跳过', todo: '待打卡', future: '未来', none: '不在这天' }[state] || ''
}

function partialHeight(key) {
  const checkin = props.checkins.find(item => item.habitId === props.habit.publicId && String(item.date).slice(0, 10) === key)
  return Math.round(progressOf(props.habit, checkin) * 100)
}
</script>

<style scoped>
.hr {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: var(--card);
  cursor: pointer;
  list-style: none;
  transition: background .14s ease, border-color .14s ease, box-shadow .14s ease;
}
.hr:hover { background: color-mix(in srgb, var(--paper) 70%, var(--card)); }
.hr.is-active { border-color: color-mix(in srgb, var(--priority-low, #4d78db) 55%, transparent); background: color-mix(in srgb, var(--priority-low, #4d78db) 5%, var(--card)); }
.hr.is-selectable { background: color-mix(in srgb, var(--paper) 55%, var(--card)); }
.hr-pick { margin: 0 2px 0 0; accent-color: var(--priority-low, #4d78db); }

.hr-body { display: flex; flex: 1; min-width: 0; align-items: center; gap: 10px; }
.hr-icon { display: grid; width: 28px; height: 28px; flex: 0 0 auto; place-items: center; border-radius: 50%; font-size: 14px; line-height: 1; }
.hr-main { display: grid; flex: 1; min-width: 0; gap: 1px; }
.hr-name { overflow: hidden; color: var(--ink); font-size: 13px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.hr-meta { display: flex; align-items: center; gap: 8px; color: var(--dim, var(--muted)); font-size: 10.5px; font-variant-numeric: tabular-nums; }
.hr-meta span { display: inline-flex; align-items: center; gap: 2px; }
.hr-goal svg { color: #e8912d; }
.hr-streak svg { color: #e0503f; }
.hr-streak.is-zero { color: var(--dim, var(--muted)); }
.hr-streak.is-zero svg { color: currentColor; }

.hr-cells { display: grid; grid-template-columns: repeat(7, 20px); gap: 3px; flex: 0 0 auto; }
.hr-cell {
  position: relative;
  display: grid; width: 20px; height: 20px; place-items: center;
  padding: 0; border: 1px solid transparent; border-radius: 50%;
  background: transparent; color: var(--muted); overflow: hidden;
  transition: transform .1s ease, background .14s ease;
}
.hr-cell:not(:disabled):hover { transform: scale(1.14); }
.hr-cell:disabled { cursor: default; }
.hr-cell.is-done { background: var(--habit-done, #4d78db); color: #fff; }
.hr-cell.is-missed { color: var(--down, #e34d59); }
.hr-cell.is-skip { color: var(--dim, var(--muted)); }
.hr-cell.is-todo { border-color: var(--line2); }
.hr-cell.is-none, .hr-cell.is-future { color: var(--line2); }
.hr-cell.is-partial { color: var(--priority-low, #4d78db); border-color: color-mix(in srgb, var(--priority-low, #4d78db) 45%, transparent); }
.hr-cell-dot { display: block; width: 4px; height: 4px; border-radius: 50%; background: currentColor; }
.hr-cell-icon { font-size: 10px; line-height: 1; }
.hr-cell-half { position: absolute; bottom: 0; left: 0; right: 0; background: color-mix(in srgb, var(--priority-low, #4d78db) 28%, transparent); }

.hr-today {
  display: grid; width: 26px; height: 26px; flex: 0 0 auto; place-items: center;
  padding: 0; border: 1px solid var(--line2); border-radius: 50%;
  background: transparent; color: var(--muted); cursor: pointer;
}
.hr-today.is-done { border-color: transparent; background: var(--habit-done, #4d78db); color: #fff; }
.hr-today.is-missed { border-color: transparent; color: var(--down, #e34d59); }
.hr-today.is-skip { border-color: transparent; color: var(--dim, var(--muted)); }

/* 网格卡片样式：去掉 7 天格子，只留今天一个圆点 */
.hr.is-grid { flex-direction: row; align-items: center; padding: 10px 12px; border-color: var(--line); }
.hr.is-grid .hr-cells { display: none; }
.hr.is-grid .hr-today { width: 26px; height: 26px; }

</style>
