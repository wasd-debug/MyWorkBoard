<template>
  <li
    class="hr"
    :class="{ 'is-active': active, 'is-grid': layout === 'grid', 'is-selectable': selectable, 'is-picked': selected }"
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

    <span class="hr-icon" :style="iconStyle">{{ habit.icon || '✓' }}</span>

    <div class="hr-main">
      <b class="hr-name">{{ habit.name }}</b>
      <span class="hr-meta">
        <span class="hr-total" :title="`累计完成 ${total} 天`"><Zap :size="11" />{{ total }} 天</span>
        <span class="hr-streak" :class="{ 'is-zero': !streak }" :title="`当前连续 ${streak} 天`"><Flame :size="11" />{{ streak }} 天</span>
      </span>
    </div>

    <button
      type="button"
      class="hr-check"
      :class="`is-${stateOf(today)}`"
      :disabled="stateOf(today) === 'future' || stateOf(today) === 'none'"
      :aria-label="`${habit.name} 今天 ${stateText(stateOf(today))}`"
      :title="stateText(stateOf(today))"
      @click.stop="emit('toggle', today)"
    >
      <span v-if="stateOf(today) === 'done' && habit.checkStyle === 'ICON'" class="hr-check-icon">{{ habit.icon }}</span>
      <Check v-else-if="stateOf(today) === 'done'" :size="14" :stroke-width="3" />
      <X v-else-if="stateOf(today) === 'missed'" :size="14" :stroke-width="3" />
      <span v-else-if="stateOf(today) === 'skip'" class="hr-check-dot" />
    </button>
  </li>
</template>

<script setup>
import { computed } from 'vue'
import { Check, Flame, X, Zap } from 'lucide-vue-next'
import { currentStreak, dayLabel, dayState, goalLabel, totalDone } from './habitUtils.js'

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
  background: backgroundOf(props.habit.color),
  color: props.habit.color || 'var(--accent)'
}))

/** 参考图图标是「纯色圆底 + emoji」，不用半透明混色，颜色不够亮时压一档 */
function backgroundOf(color) {
  return color ? `color-mix(in srgb, ${color} 78%, #fff)` : 'var(--paper)'
}

function stateOf(key) {
  return dayState(props.habit, props.checkins, key, props.today)
}

function stateText(state) {
  return { done: '已完成', partial: '未达标', missed: '未完成', skip: '已跳过', todo: '待打卡', future: '未来', none: '不在这天' }[state] || ''
}

void dayLabel
</script>

<style scoped>
.hr {
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 54px;
  padding: 9px 12px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: var(--card);
  box-shadow: 0 1px 2px rgb(35 43 57 / 6%), 0 0 0 1px rgb(35 43 57 / 4%);
  cursor: pointer;
  list-style: none;
  transition: background .14s ease, border-color .14s ease, box-shadow .14s ease;
}
.hr:hover { background: color-mix(in srgb, var(--paper) 55%, var(--card)); }
.hr.is-active {
  border-color: #3d8fce;
  background: var(--card);
  box-shadow: 0 2px 10px rgb(61 143 206 / 12%);
}
.hr.is-selectable { background: color-mix(in srgb, var(--paper) 45%, var(--card)); }
.hr.is-picked { border-color: color-mix(in srgb, #3d8fce 55%, transparent); }
.hr-pick { margin: 0; accent-color: #3d8fce; }

.hr-icon {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  font-size: 17px;
  line-height: 1;
}

.hr-main { display: grid; flex: 1; min-width: 0; gap: 2px; }
.hr-name {
  overflow: hidden;
  color: var(--ink);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -.01em;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hr-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--muted);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
}
.hr-meta span { display: inline-flex; align-items: center; gap: 3px; }
.hr-total svg { color: #3d8fce; }
.hr-streak { color: #e0503f; }
.hr-streak svg { color: #e0503f; }
.hr-streak.is-zero { color: var(--dim, var(--muted)); }
.hr-streak.is-zero svg { color: var(--dim, var(--muted)); }

/* 今天的状态圆：参考图是 26px 实心/空心圆 */
.hr-check {
  display: grid;
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
  place-items: center;
  padding: 0;
  border: 1.5px solid var(--line2);
  border-radius: 50%;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  transition: background .14s ease, border-color .14s ease, transform .1s ease;
}
.hr-check:not(:disabled):hover { transform: scale(1.06); border-color: #3d8fce; }
.hr-check:disabled { cursor: default; opacity: .75; }
.hr-check.is-done { border-color: #3d8fce; background: #3d8fce; color: #fff; }
.hr-check.is-missed { border-color: color-mix(in srgb, #e34d59 45%, transparent); color: #e34d59; }
.hr-check.is-skip { border-color: var(--line2); }
.hr-check.is-future, .hr-check.is-none { border-color: color-mix(in srgb, var(--line2) 55%, transparent); }
.hr-check-dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.hr-check-icon { font-size: 13px; line-height: 1; }

/* 卡片样式：两列网格，卡片本身不变 */
.hr.is-grid { padding: 9px 11px; }
</style>
