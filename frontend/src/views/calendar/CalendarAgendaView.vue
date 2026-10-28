<template>
  <div ref="scrollEl" class="cal-agenda">
    <section v-for="day in visibleDays" :key="dayKey(day)" class="cal-agenda-day" :data-date="dayKey(day)">
      <header class="cal-agenda-head" :class="{ today: dayKey(day) === todayKey }">
        <b>{{ day.getDate() }}</b>
        <span>{{ weekdayLabel(day) }}</span>
        <small v-if="subLabel(day)">{{ subLabel(day) }}</small>
        <em>{{ countOf(day) }} 项</em>
      </header>
      <div class="cal-agenda-body">
        <button
          v-for="event in eventsOf(day)"
          :key="event.id"
          type="button"
          class="cal-agenda-row"
          :class="{ 'is-done': event.done }"
          :style="tintStyle(event.color, 12)"
          @click.stop="$emit('open-event', event, $event)"
        >
          <span class="cal-agenda-time">{{ event.startLabel || '全天' }}</span>
          <span class="cal-agenda-dot" :style="{ background: event.color }"></span>
          <span class="cal-agenda-title">{{ event.title }}</span>
          <span v-if="event.openChecklist" class="cal-agenda-extra">+{{ event.openChecklist }}</span>
        </button>
      </div>
    </section>
    <p v-if="!visibleDays.length" class="cal-agenda-empty">这段时间没有安排</p>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { formatWeekday, toDateKey } from './calendarRange.js'
import { tintStyle } from './calendarColors.js'

const props = defineProps({
  days: { type: Array, required: true },
  byDay: { type: Map, default: () => new Map() },
  todayKey: { type: String, default: '' },
  lunar: { type: Object, default: () => ({}) },
  holidays: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['open-event', 'select-day', 'load-more'])

const scrollEl = ref(null)
let positioned = false
let pendingPrepend = false
let prevScrollHeight = 0
let loadCooldown = 0
let prependTimer
const visibleDays = computed(() => props.days.filter(day => countOf(day) > 0))

function dayKey(day) {
  return toDateKey(day)
}

function weekdayLabel(day) {
  return formatWeekday(day)
}

function subLabel(day) {
  const key = dayKey(day)
  return props.holidays[key]?.name || props.lunar[key] || ''
}

function eventsOf(day) {
  const bucket = props.byDay.get(dayKey(day))
  if (!bucket) return []
  return [...bucket.allDay, ...bucket.timed]
}

function countOf(day) {
  return eventsOf(day).length
}

async function scrollToToday() {
  await nextTick()
  const root = scrollEl.value
  if (!root) return
  const target = root.querySelector(`[data-date="${props.todayKey}"]`)
  if (target) target.scrollIntoView({ block: 'start' })
}

// 真正的滚动容器是外层 .cal-body，这里向上找滚动祖先而不是监听自身
function scroller() {
  return scrollEl.value?.closest('.cal-body') || scrollEl.value?.parentElement || null
}

// 顶部/底部缓冲触发增量加载；向前加载时记录滚动高度，插入后补回偏移，避免视图跳动
function onScroll() {
  const root = scroller()
  if (!root) return
  const now = Date.now()
  if (now - loadCooldown < 400) return
  if (root.scrollTop <= 60) {
    pendingPrepend = true
    prevScrollHeight = root.scrollHeight
    loadCooldown = now
    emit('load-more', 'before')
    window.clearTimeout(prependTimer)
    prependTimer = window.setTimeout(() => { pendingPrepend = false }, 1500)
    return
  }
  if (root.scrollHeight - root.scrollTop - root.clientHeight < 240) {
    loadCooldown = now
    emit('load-more', 'after')
  }
}

watch(() => props.days.length, async () => {
  const root = scroller()
  if (!root) return
  await nextTick()
  if (pendingPrepend) {
    root.scrollTop += root.scrollHeight - prevScrollHeight
    pendingPrepend = false
  }
})

watch(() => visibleDays.value.length, async length => {
  if (!positioned && length) {
    positioned = true
    await scrollToToday()
  }
})

watch(() => props.todayKey, () => {
  positioned = false
  scrollToToday()
})

onMounted(async () => {
  scroller()?.addEventListener('scroll', onScroll, { passive: true })
  await nextTick()
  if (visibleDays.value.length) {
    positioned = true
    await scrollToToday()
  }
})

onBeforeUnmount(() => {
  scroller()?.removeEventListener('scroll', onScroll)
})
</script>

<style scoped>
.cal-agenda {
  min-height: 100%;
  padding: 0 0 56px;
  background: var(--card);
}
.cal-agenda-day {
  border-bottom: 1px solid var(--line);
}
.cal-agenda-day:last-of-type {
  border-bottom: 0;
}
.cal-agenda-head {
  position: sticky;
  z-index: 2;
  top: 0;
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 8px 16px 7px;
  border-bottom: 1px solid var(--line);
  background: color-mix(in srgb, var(--card) 94%, transparent);
  backdrop-filter: blur(6px);
}
.cal-agenda-head b {
  color: var(--ink);
  font-size: 16px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}
.cal-agenda-head span {
  color: var(--ink2);
  font-size: 11.5px;
}
.cal-agenda-head small {
  color: var(--muted);
  font-size: 10.5px;
}
.cal-agenda-head em {
  margin-left: auto;
  color: var(--muted);
  font-size: 10.5px;
  font-style: normal;
}
.cal-agenda-head.today b {
  display: inline-grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--accent);
  color: #fff;
}
.cal-agenda-head.today span,
.cal-agenda-head.today em {
  color: var(--accent);
  font-weight: 650;
}
.cal-agenda-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 12px 10px;
}
.cal-agenda-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  color: var(--ink);
  font-size: 12px;
  text-align: left;
}
.cal-agenda-row:hover {
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--ink) 10%, transparent);
}
.cal-agenda-time {
  flex: 0 0 44px;
  color: var(--ink2);
  font-size: 10.5px;
  font-variant-numeric: tabular-nums;
}
.cal-agenda-dot {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  border-radius: 50%;
}
.cal-agenda-title {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cal-agenda-row.is-done .cal-agenda-title {
  color: var(--muted);
  text-decoration: line-through;
}
.cal-agenda-extra {
  flex: 0 0 auto;
  color: var(--ink2);
  font-size: 9.5px;
}
.cal-agenda-empty {
  margin: 40px 0;
  color: var(--muted);
  font-size: 12px;
  text-align: center;
}
</style>
