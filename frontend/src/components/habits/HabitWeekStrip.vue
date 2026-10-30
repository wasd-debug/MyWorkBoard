<template>
  <div class="habit-week">
    <button
      v-for="day in week"
      :key="day.key"
      type="button"
      class="habit-week-day"
      :class="{ 'is-today': day.isToday, 'is-active': day.key === active }"
      :aria-label="`${day.weekday} ${day.day} 日`"
      @click="emit('pick', day.key)"
    >
      <small>{{ day.weekday }}</small>
      <b>{{ day.day }}</b>
      <svg class="habit-week-arc" viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="12" :r="RADIUS" class="habit-week-track" />
        <circle
          v-if="percentOf(day.key) > 0"
          cx="12"
          cy="12"
          :r="RADIUS"
          class="habit-week-value"
          :stroke-dasharray="`${valueLength(day.key)} ${CIRCUM}`"
        />
      </svg>
    </button>
  </div>
</template>

<script setup>
const props = defineProps({
  week: { type: Array, default: () => [] },
  /** { [dateKey]: { done, total, percent } } */
  progress: { type: Object, default: () => ({}) },
  /** 当前选中的日期（点周条切换列表里要打卡的日期） */
  active: { type: String, default: '' }
})
const emit = defineEmits(['pick'])

// 整圈进度环：浅灰底环 + 蓝色进度，蓝色从 12 点方向顺时针铺满，100% 正好闭合
const RADIUS = 9.6
const CIRCUM = 2 * Math.PI * RADIUS

function percentOf(key) {
  return Math.min(100, Math.max(0, Number(props.progress?.[key]?.percent) || 0))
}

/** 0% 直接不画（圆头描边在长度为 0 时会留一个小圆点） */
function valueLength(key) {
  const ratio = percentOf(key) / 100
  if (!ratio) return 0
  return CIRCUM * ratio
}
</script>

<style scoped>
/* 参考图：日期下是一整圈进度环，今天整列浅蓝底块 */
.habit-week {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 6px;
  padding: 4px 0 10px;
}
.habit-week-day {
  display: grid;
  justify-items: center;
  gap: 2px;
  padding: 8px 0 6px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--muted);
  font: inherit;
  cursor: pointer;
  transition: background .14s ease;
}
.habit-week-day:hover { background: color-mix(in srgb, var(--ink) 5%, transparent); }
.habit-week-day.is-today { background: color-mix(in srgb, #3d8fce 9%, transparent); color: var(--ink); }
.habit-week-day.is-active { background: color-mix(in srgb, #3d8fce 15%, transparent); color: var(--ink); }
.habit-week-day small { font-size: 11.5px; letter-spacing: .02em; }
.habit-week-day b { font-size: 15px; font-weight: 600; font-variant-numeric: tabular-nums; letter-spacing: -.01em; }
/* rotate(-90deg)：让进度从 12 点方向开始顺时针铺 */
.habit-week-arc { width: 30px; height: 30px; margin-top: 3px; transform: rotate(-90deg); }
.habit-week-track { fill: none; stroke: color-mix(in srgb, var(--ink) 12%, transparent); stroke-width: 2.6; }
.habit-week-value {
  fill: none;
  stroke: var(--hb-accent, #3d8fce);
  stroke-width: 2.6;
  stroke-linecap: round;
  transition: stroke-dasharray .22s ease;
}
</style>
