<template>
  <div ref="scrollEl" class="cal-agenda">
    <div ref="topSentinel" class="cal-agenda-sentinel is-top">
      <span v-if="loadingBefore">正在加载更早的日程…</span>
      <span v-else-if="!moreBefore" class="is-tip">已经是最早的日程</span>
    </div>
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
          <span v-if="event.kind === 'habit' && event.habit?.icon" class="cal-agenda-mark" :style="{ background: event.color }"><TaskOrganizationIcon :value="event.habit.icon" :size="9" /></span>
          <span v-else class="cal-agenda-dot" :style="{ background: event.color }"></span>
          <span class="cal-agenda-title">{{ event.title }}</span>
          <span v-if="event.openChecklist" class="cal-agenda-extra">+{{ event.openChecklist }}</span>
        </button>
      </div>
    </section>
    <p v-if="!visibleDays.length && !loadingBefore" class="cal-agenda-empty">这段时间没有安排</p>
    <div ref="bottomSentinel" class="cal-agenda-sentinel is-bottom">
      <span v-if="loadingAfter">正在加载更晚的日程…</span>
      <span v-else-if="!moreAfter" class="is-tip">已经是最后的日程</span>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { formatWeekday, toDateKey } from './calendarRange.js'
import { tintStyle } from './calendarColors.js'
import TaskOrganizationIcon from '../../components/tasks/TaskOrganizationIcon.vue'

const props = defineProps({
  days: { type: Array, required: true },
  byDay: { type: Map, default: () => new Map() },
  todayKey: { type: String, default: '' },
  lunar: { type: Object, default: () => ({}) },
  holidays: { type: Object, default: () => ({}) },
  moreBefore: { type: Boolean, default: true },
  moreAfter: { type: Boolean, default: true }
})

const emit = defineEmits(['open-event', 'select-day', 'load-more'])

const scrollEl = ref(null)
const topSentinel = ref(null)
const bottomSentinel = ref(null)
const loadingBefore = ref(false)
const loadingAfter = ref(false)
const visibleDays = computed(() => props.days.filter(day => countOf(day) > 0))

let observer = null
let positioned = false
let anchor = null
// 连续补取的次数上限：一次滚动最多自动往前要 3 个批次，避免一口气把半年都拉下来
let chainBudget = 0

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
  return [...(bucket.allDay || []), ...(bucket.timed || [])]
}

function countOf(day) {
  const bucket = props.byDay.get(dayKey(day))
  if (!bucket) return 0
  return (bucket.allDay?.length || 0) + (bucket.timed?.length || 0)
}

/** 真正的滚动容器是外层 .cal-body */
function scroller() {
  return scrollEl.value?.closest('.cal-body') || scrollEl.value?.parentElement || null
}

/** 记录「视口顶部那一天」及其相对偏移；插入更早内容后用它把画面钉住，滚动不会跳 */
function captureAnchor() {
  const root = scroller()
  if (!root) return null
  const top = root.getBoundingClientRect().top
  for (const node of root.querySelectorAll('.cal-agenda-day')) {
    const rect = node.getBoundingClientRect()
    if (rect.bottom > top + 1) return { key: node.dataset.date, offset: rect.top - top }
  }
  return null
}

async function restoreAnchor() {
  await nextTick()
  const root = scroller()
  if (!root || !anchor) return
  const node = root.querySelector(`.cal-agenda-day[data-date="${anchor.key}"]`)
  if (node) {
    const delta = node.getBoundingClientRect().top - root.getBoundingClientRect().top - anchor.offset
    if (Math.abs(delta) > 0.5) root.scrollTop += delta
  }
  anchor = null
}

function requestMore(direction) {
  // 未完成首次定位前不预取：此时列表还空着，哨兵必然相交，会一路把半年的数据拉下来
  if (!positioned) return
  if (direction === 'before') {
    if (loadingBefore.value || !props.moreBefore) return
    anchor = captureAnchor()
    loadingBefore.value = true
    emit('load-more', 'before')
    return
  }
  if (loadingAfter.value || !props.moreAfter) return
  loadingAfter.value = true
  emit('load-more', 'after')
}

/** 定位到「今天的日程」；今天没有条目时落到之后最近的一天，仍没有就停在顶部 */
function scrollToDay() {
  const root = scroller()
  if (!root) return
  const nodes = Array.from(root.querySelectorAll('.cal-agenda-day'))
  const target = nodes.find(node => node.dataset.date >= props.todayKey) || nodes[nodes.length - 1]
  if (!target) return
  const delta = target.getBoundingClientRect().top - root.getBoundingClientRect().top
  if (Math.abs(delta) > 1) root.scrollTop += delta
}

function settle() {
  if (loadingBefore.value) {
    loadingBefore.value = false
    void restoreAnchor()
  }
  loadingAfter.value = false
}

/** 内容不足以撑满视口时再补一批；最多连补 2 批，避免在空档里无限往前拉 */
async function maybeChain() {
  await nextTick()
  const root = scroller()
  if (!root || loadingBefore.value || !props.moreBefore) return
  if (root.scrollTop > 24) return
  if (root.scrollHeight > root.clientHeight + 200) return
  chainBudget += 1
  if (chainBudget > 2) return
  requestMore('before')
}

function positionOnce() {
  if (positioned || !visibleDays.value.length) return
  positioned = true
  void nextTick().then(scrollToDay)
}

watch(() => props.days.length, () => {
  const shownBefore = visibleDays.value.length
  settle()
  positionOnce()
  if (!positioned) return
  // 上一批没有带来任何条目时补一批，用户不会觉得「滚了没反应」
  if (visibleDays.value.length === shownBefore) void maybeChain()
})

// 首次拿到数据时（byDay 重建）再定位一次：挂载时列表往往还是空的
watch(() => props.byDay, () => {
  positionOnce()
}, { flush: 'post' })

watch(() => props.todayKey, () => {
  positioned = false
  chainBudget = 0
  positionOnce()
})

// 兜底：哨兵离开视口后再次进入不会重复触发，用户继续上滚时需要重新发起
function onScroll() {
  const root = scroller()
  if (!root) return
  chainBudget = 0
  if (!loadingBefore.value && props.moreBefore && root.scrollTop <= 24) requestMore('before')
  else if (!loadingAfter.value && props.moreAfter && root.scrollHeight - root.scrollTop - root.clientHeight < 160) requestMore('after')
}

/** 顶部已经滚不动时（scrollTop 恒为 0）不会再产生 scroll 事件，
 *  必须靠 wheel / touchmove 判断「用户还想继续往上」，否则表现为上滚没反应 */
function onWheel(event) {
  const root = scroller()
  if (!root) return
  if (event.deltaY < 0 && root.scrollTop <= 4) requestMore('before')
  else if (event.deltaY > 0 && root.scrollHeight - root.scrollTop - root.clientHeight < 120) requestMore('after')
}

let touchStartY = 0
function onTouchStart(event) {
  touchStartY = event.touches?.[0]?.clientY ?? 0
}
function onTouchMove(event) {
  const root = scroller()
  if (!root) return
  const current = event.touches?.[0]?.clientY ?? touchStartY
  if (current - touchStartY > 12 && root.scrollTop <= 4) requestMore('before')
}

onMounted(async () => {
  const root = scroller()
  root?.addEventListener('scroll', onScroll, { passive: true })
  root?.addEventListener('wheel', onWheel, { passive: true })
  root?.addEventListener('touchstart', onTouchStart, { passive: true })
  root?.addEventListener('touchmove', onTouchMove, { passive: true })
  await nextTick()
  positionOnce()
  if (typeof IntersectionObserver === 'undefined' || !root) return
  observer = new IntersectionObserver(entries => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue
      requestMore(entry.target === topSentinel.value ? 'before' : 'after')
    }
  }, { root, rootMargin: '360px 0px 360px 0px', threshold: 0 })
  if (topSentinel.value) observer.observe(topSentinel.value)
  if (bottomSentinel.value) observer.observe(bottomSentinel.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  const root = scroller()
  root?.removeEventListener('scroll', onScroll)
  root?.removeEventListener('wheel', onWheel)
  root?.removeEventListener('touchstart', onTouchStart)
  root?.removeEventListener('touchmove', onTouchMove)
})
</script>

<style scoped>
.cal-agenda {
  min-height: 100%;
  padding: 0 0 56px;
  background: var(--card);
}
.cal-agenda-sentinel {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  color: var(--muted);
  font-size: 11px;
}
.cal-agenda-sentinel.is-bottom {
  min-height: 48px;
}
.cal-agenda-sentinel .is-tip {
  opacity: .65;
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
.cal-agenda-mark {
  display: inline-grid;
  flex: 0 0 auto;
  width: 14px;
  height: 14px;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  font-size: 9px;
  line-height: 1;
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
