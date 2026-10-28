<template>
  <aside class="cal-sidebar" aria-label="日历筛选">
    <section class="cal-mini" aria-label="迷你月历">
      <header class="cal-mini-head">
        <strong>{{ miniTitle }}</strong>
        <div class="cal-mini-actions">
          <button type="button" aria-label="上个月" title="上个月" @click="$emit('move-month', -1)"><ChevronLeft :size="14" /></button>
          <button type="button" aria-label="下个月" title="下个月" @click="$emit('move-month', 1)"><ChevronRight :size="14" /></button>
          <button type="button" class="cal-mini-today" title="回到今天" @click="$emit('go-today')">今天</button>
        </div>
      </header>
      <div class="cal-mini-grid">
        <span v-for="label in WEEKDAY_LABELS" :key="label" class="cal-mini-dow">{{ label }}</span>
        <button
          v-for="cell in cells"
          :key="cell.key"
          type="button"
          class="cal-mini-day"
          :class="{ muted: !cell.inMonth, today: cell.isToday, selected: cell.isSelected }"
          :aria-current="cell.isToday ? 'date' : undefined"
          @click="$emit('select', cell.date)"
        >
          <b>{{ cell.day }}</b>
          <small v-if="cell.sub">{{ cell.sub }}</small>
        </button>
      </div>
    </section>

    <div class="cal-scope" role="group" aria-label="显示范围">
      <button type="button" class="cal-scope-row is-all" :class="{ active: scope.mode === 'all' }" @click="selectAll">
        <span class="cal-scope-name">所有</span>
        <span class="cal-radio" :class="{ on: scope.mode === 'all' }" aria-hidden="true"></span>
      </button>

      <div class="cal-scope-group">
        <button type="button" class="cal-scope-head" @click="toggleGroup('listIds')">
          <span>清单</span>
          <span class="cal-check" :class="groupState('listIds')" aria-hidden="true"></span>
        </button>
        <button
          v-for="list in lists"
          :key="list.publicId"
          type="button"
          class="cal-scope-row"
          :class="{ active: isChecked('listIds', list.publicId) }"
          @click="toggleItem('listIds', list.publicId)"
        >
          <TaskOrganizationIcon :value="list.icon" :size="14" :style="{ color: list.color || 'var(--ink2)' }" />
          <span class="cal-scope-name">{{ list.name }}</span>
          <span class="cal-check" :class="{ on: isChecked('listIds', list.publicId) }" aria-hidden="true"></span>
        </button>
      </div>

      <div class="cal-scope-group">
        <button type="button" class="cal-scope-head" @click="toggleGroup('tagIds')">
          <span>标签</span>
          <span class="cal-check" :class="groupState('tagIds')" aria-hidden="true"></span>
        </button>
        <button
          v-for="tag in tags"
          :key="tag.publicId"
          type="button"
          class="cal-scope-row"
          :class="{ active: isChecked('tagIds', tag.publicId) }"
          @click="toggleItem('tagIds', tag.publicId)"
        >
          <Tag :size="14" :style="{ color: tag.color || 'var(--ink2)' }" />
          <span class="cal-scope-name">{{ tag.name }}</span>
          <span class="cal-check" :class="{ on: isChecked('tagIds', tag.publicId) }" aria-hidden="true"></span>
        </button>
      </div>

      <div class="cal-scope-group">
        <div class="cal-scope-head is-static"><span>过滤器</span></div>
        <button
          v-for="filter in filters"
          :key="filter.publicId"
          type="button"
          class="cal-scope-row"
          :class="{ active: scope.mode === 'filter' && scope.filterId === filter.publicId }"
          @click="selectFilter(filter.publicId)"
        >
          <span class="cal-scope-name">{{ filter.name }}</span>
          <span class="cal-radio" :class="{ on: scope.mode === 'filter' && scope.filterId === filter.publicId }" aria-hidden="true"></span>
        </button>
        <p v-if="!filters.length" class="cal-scope-empty">暂无过滤器</p>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { ChevronLeft, ChevronRight, Tag } from 'lucide-vue-next'
import TaskOrganizationIcon from '../../components/tasks/TaskOrganizationIcon.vue'
import { WEEKDAY_LABELS, monthWeeks, toDateKey } from './calendarRange.js'

const props = defineProps({
  anchor: { type: Date, required: true },
  today: { type: Date, required: true },
  selected: { type: Date, default: null },
  lunar: { type: Object, default: () => ({}) },
  holidays: { type: Object, default: () => ({}) },
  lists: { type: Array, default: () => [] },
  tags: { type: Array, default: () => [] },
  filters: { type: Array, default: () => [] },
  scope: { type: Object, required: true }
})

const emit = defineEmits(['move-month', 'go-today', 'select', 'update:scope'])

const miniTitle = computed(() => `${props.anchor.getFullYear()}年${props.anchor.getMonth() + 1}月`)
const todayKey = computed(() => toDateKey(props.today))
const selectedKey = computed(() => (props.selected ? toDateKey(props.selected) : ''))

const cells = computed(() => monthWeeks(props.anchor).flat().map(date => {
  const key = toDateKey(date)
  // 迷你月历优先显示农历（初一是月份、其余是农历日），节假日信息在主日历的事件行里呈现
  const sub = props.lunar[key] || props.holidays[key]?.name || ''
  return {
    key, date, day: date.getDate(), sub,
    inMonth: date.getMonth() === props.anchor.getMonth(),
    isToday: key === todayKey.value,
    isSelected: key === selectedKey.value
  }
}))

const allListIds = computed(() => props.lists.map(item => item.publicId))
const allTagIds = computed(() => props.tags.map(item => item.publicId))

function isChecked(field, id) {
  return props.scope.mode === 'custom' && (props.scope[field] || []).includes(id)
}

function groupState(field) {
  const total = field === 'listIds' ? allListIds.value.length : allTagIds.value.length
  if (!total || props.scope.mode !== 'custom') return {}
  const selected = (props.scope[field] || []).length
  return { on: selected === total, mid: selected > 0 && selected < total }
}

function emitScope(next) {
  emit('update:scope', { mode: 'all', listIds: [], tagIds: [], filterId: '', ...next })
}

function selectAll() {
  emitScope({ mode: 'all' })
}

function toggleGroup(field) {
  const all = field === 'listIds' ? allListIds.value : allTagIds.value
  const current = props.scope.mode === 'custom' ? (props.scope[field] || []) : []
  const next = current.length === all.length ? [] : all
  const listIds = field === 'listIds' ? next : (props.scope.mode === 'custom' ? props.scope.listIds : [])
  const tagIds = field === 'tagIds' ? next : (props.scope.mode === 'custom' ? props.scope.tagIds : [])
  emitScope({ mode: listIds.length || tagIds.length ? 'custom' : 'all', listIds, tagIds })
}

function toggleItem(field, id) {
  const base = props.scope.mode === 'custom' ? props.scope : { listIds: [], tagIds: [] }
  const current = base[field] || []
  const next = current.includes(id) ? current.filter(value => value !== id) : [...current, id]
  const listIds = field === 'listIds' ? next : base.listIds
  const tagIds = field === 'tagIds' ? next : base.tagIds
  emitScope({ mode: listIds.length || tagIds.length ? 'custom' : 'all', listIds, tagIds })
}

function selectFilter(filterId) {
  emitScope({ mode: 'filter', filterId, listIds: [], tagIds: [] })
}
</script>

<style scoped>
.cal-sidebar {
  display: flex;
  flex-direction: column;
  min-height: 0;
  width: 248px;
  border-right: 1px solid var(--line);
  background: var(--card);
  color: var(--ink);
  overflow: hidden;
}
.cal-mini {
  flex: 0 0 auto;
  padding: 14px 12px 10px;
}
.cal-mini-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  min-height: 26px;
  margin-bottom: 8px;
}
.cal-mini-head strong {
  font-size: 13px;
  font-weight: 650;
  letter-spacing: .01em;
}
.cal-mini-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}
.cal-mini-actions button {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
}
.cal-mini-actions button:hover {
  background: var(--paper);
  color: var(--ink);
}
.cal-mini-actions .cal-mini-today {
  width: auto;
  padding: 0 7px;
  color: var(--ink2);
  font-size: 11px;
}
.cal-mini-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 1px 0;
  text-align: center;
}
.cal-mini-dow {
  padding: 2px 0 5px;
  color: var(--muted);
  font-size: 10px;
}
.cal-mini-day {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  min-height: 34px;
  padding: 3px 0 2px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ink);
  line-height: 1.15;
}
.cal-mini-day b {
  font-size: 11.5px;
  font-weight: 600;
}
.cal-mini-day small {
  max-width: 100%;
  overflow: hidden;
  color: var(--muted);
  font-size: 8.5px;
  line-height: 1.1;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cal-mini-day.muted b,
.cal-mini-day.muted small {
  color: var(--dim, var(--muted));
  opacity: .55;
}
.cal-mini-day:hover {
  background: var(--paper);
}
.cal-mini-day.today b {
  display: grid;
  place-items: center;
  width: 19px;
  height: 19px;
  border-radius: 50%;
  background: var(--accent);
  color: #fff;
  font-weight: 700;
}
.cal-mini-day.selected:not(.today) {
  background: var(--accent-soft);
}
.cal-mini-day.selected:not(.today) b {
  color: var(--accent);
  font-weight: 700;
}
.cal-scope {
  flex: 1 1 auto;
  min-height: 0;
  padding: 4px 8px 18px;
  overflow-y: auto;
  border-top: 1px solid var(--line);
}
.cal-scope-group {
  margin-top: 2px;
}
.cal-scope-head,
.cal-scope-row {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 8px;
  min-height: 30px;
  padding: 0 8px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--ink);
  font-size: 12.5px;
  text-align: left;
}
.cal-scope-head {
  margin-top: 8px;
  color: var(--ink2);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .04em;
}
.cal-scope-head.is-static {
  cursor: default;
}
.cal-scope-row:hover,
.cal-scope-head:hover {
  background: var(--paper);
}
.cal-scope-row.active {
  background: color-mix(in srgb, var(--accent) 9%, transparent);
}
.cal-scope-row.is-all {
  font-weight: 650;
}
.cal-scope-name {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cal-scope-row.active .cal-scope-name {
  color: var(--accent);
}
.cal-scope-empty {
  margin: 4px 8px 0;
  color: var(--muted);
  font-size: 11px;
}
.cal-check,
.cal-radio {
  position: relative;
  flex: 0 0 auto;
  width: 14px;
  height: 14px;
  border: 1.5px solid var(--line2);
  background: transparent;
}
.cal-check {
  border-radius: 4px;
}
.cal-radio {
  border-radius: 50%;
}
.cal-check.on,
.cal-check.mid {
  border-color: var(--accent);
  background: var(--accent);
}
.cal-check.on::after {
  position: absolute;
  top: 1px;
  left: 4px;
  width: 3px;
  height: 7px;
  border-right: 2px solid #fff;
  border-bottom: 2px solid #fff;
  content: '';
  transform: rotate(42deg);
}
.cal-check.mid::after {
  position: absolute;
  top: 5px;
  left: 3px;
  width: 6px;
  height: 2px;
  border-radius: 1px;
  background: #fff;
  content: '';
}
.cal-radio.on {
  border-color: var(--accent);
  box-shadow: inset 0 0 0 3px var(--card);
  background: var(--accent);
}
.cal-sidebar::-webkit-scrollbar {
  width: 6px;
}
</style>
