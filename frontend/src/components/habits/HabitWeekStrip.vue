<template>
  <div class="habit-week">
    <div
      v-for="day in week"
      :key="day.key"
      class="habit-week-day"
      :class="{ 'is-today': day.isToday }"
    >
      <small>{{ day.weekday }}</small>
      <b>{{ day.day }}</b>
      <svg class="habit-week-arc" viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="12" r="9" class="habit-week-track" />
        <circle
          cx="12"
          cy="12"
          r="9"
          class="habit-week-value"
          :stroke-dasharray="`${(progress[day.key]?.percent || 0) * 0.5655} 56.55`"
        />
      </svg>
      <span class="sr-only">{{ progress[day.key]?.done || 0 }}/{{ progress[day.key]?.total || 0 }}</span>
    </div>
  </div>
</template>

<script setup>
defineProps({
  week: { type: Array, default: () => [] },
  /** { [dateKey]: { done, total, percent } } */
  progress: { type: Object, default: () => ({}) }
})
</script>

<style scoped>
.habit-week {
  display: grid;
  grid-template-columns: repeat(7, 46px);
  gap: 4px;
  justify-content: start;
  padding: 2px 0 6px;
}
.habit-week-day {
  display: grid;
  justify-items: center;
  gap: 1px;
  padding: 6px 0 5px;
  border-radius: 12px;
  color: var(--muted);
}
.habit-week-day.is-today { background: color-mix(in srgb, var(--ink) 7%, transparent); color: var(--ink); }
.habit-week-day small { font-size: 10.5px; letter-spacing: .04em; }
.habit-week-day b { font-size: 12.5px; font-weight: 600; font-variant-numeric: tabular-nums; }
.habit-week-arc { width: 17px; height: 17px; transform: rotate(-90deg); }
.habit-week-track { fill: none; stroke: var(--line); stroke-width: 2.4; }
.habit-week-value { fill: none; stroke: var(--habit-done, #4d78db); stroke-width: 2.4; stroke-linecap: round; transition: stroke-dasharray .2s ease; }
.habit-week-day.is-today .habit-week-value { stroke: var(--habit-done, #4d78db); }
</style>
