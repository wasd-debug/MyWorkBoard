<template>
  <div class="focus-dt">
    <button type="button" class="focus-dt-trigger" :class="{ 'is-empty': !modelValue }" :aria-expanded="open" @click.stop="toggle">
      <span>{{ display || placeholder }}</span>
      <ChevronDown :size="15" />
    </button>
    <div v-if="open" class="focus-dt-popover" @click.stop>
      <TaskDateTimePopover
        mode="DATE"
        :show-tabs="false"
        :month="month"
        :calendar-days="cells"
        :month-label="monthLabel"
        :selected-key="draftKey"
        apply-label="确定"
        clear-label="取消"
        @quick-date="applyQuickDate"
        @move-month="moveMonth"
        @select-date="draftKey = $event"
        @clear-date="close"
        @apply="commit"
      >
        <template #rows>
          <label class="focus-dt-time">
            <Clock3 :size="15" />
            <input v-model="draftTime" type="time" aria-label="时间" />
          </label>
        </template>
      </TaskDateTimePopover>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ChevronDown, Clock3 } from 'lucide-vue-next'
import TaskDateTimePopover from './TaskDateTimePopover.vue'
import { apiGetTaskCalendar } from '../../../packages/api-client/src/index.js'
import { formatLunarLabel } from '../../views/calendar/calendarLunar.js'

const props = defineProps({
  /** ISO 字符串，空串表示未选 */
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '选择时间' }
})
const emit = defineEmits(['update:modelValue'])

const open = ref(false)
const month = ref(new Date())
const draftKey = ref('')
const draftTime = ref('')
const lunarMap = ref({})
let lunarEpoch = 0

function pad(value) {
  return String(value).padStart(2, '0')
}

function dateKey(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

const display = computed(() => {
  if (!props.modelValue) return ''
  const date = new Date(props.modelValue)
  if (Number.isNaN(date.getTime())) return ''
  const today = new Date()
  const sameDay = date.toDateString() === today.toDateString()
  const prefix = sameDay ? '今天' : `${date.getMonth() + 1}月${date.getDate()}日`
  return `${prefix} ${pad(date.getHours())}:${pad(date.getMinutes())}`
})

const monthLabel = computed(() => `${month.value.getFullYear()}年${month.value.getMonth() + 1}月`)

const cells = computed(() => {
  const first = new Date(month.value.getFullYear(), month.value.getMonth(), 1)
  // 周一作为一周起始，与日历页保持一致
  const offset = (first.getDay() + 6) % 7
  const start = new Date(first)
  start.setDate(1 - offset)
  const todayKey = dateKey(new Date())
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start)
    date.setDate(start.getDate() + index)
    const key = dateKey(date)
    return {
      key,
      date,
      lunar: lunarMap.value[key] || '',
      otherMonth: date.getMonth() !== month.value.getMonth(),
      today: key === todayKey,
      selected: key === draftKey.value,
      repeat: false
    }
  })
})

async function loadLunar(target) {
  const epoch = ++lunarEpoch
  const from = dateKey(new Date(target.getFullYear(), target.getMonth(), 1))
  const to = dateKey(new Date(target.getFullYear(), target.getMonth() + 1, 0))
  try {
    const data = await apiGetTaskCalendar({ from, to, layers: 'holiday', lunar: 1 })
    if (epoch !== lunarEpoch) return
    lunarMap.value = Object.fromEntries((data?.lunar || []).map(item => [item.date, formatLunarLabel(item)]))
  } catch {
    /* 农历只是装饰，取不到不影响选日期 */
  }
}

function syncDraft() {
  const date = props.modelValue ? new Date(props.modelValue) : new Date()
  const valid = !Number.isNaN(date.getTime())
  draftKey.value = dateKey(valid ? date : new Date())
  draftTime.value = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  month.value = new Date((valid ? date : new Date()).getFullYear(), (valid ? date : new Date()).getMonth(), 1)
}

function toggle() {
  if (open.value) {
    close()
    return
  }
  syncDraft()
  open.value = true
  loadLunar(month.value)
}

function close() {
  open.value = false
}

function moveMonth(delta) {
  month.value = new Date(month.value.getFullYear(), month.value.getMonth() + delta, 1)
  loadLunar(month.value)
}

function applyQuickDate(kind) {
  const date = new Date()
  if (kind === 'TOMORROW') date.setDate(date.getDate() + 1)
  if (kind === 'NEXT_WEEK') date.setDate(date.getDate() + 7)
  if (kind === 'NEXT_MONTH') date.setMonth(date.getMonth() + 1)
  draftKey.value = dateKey(date)
  month.value = new Date(date.getFullYear(), date.getMonth(), 1)
  loadLunar(month.value)
}

function commit() {
  const [year, monthPart, day] = draftKey.value.split('-').map(Number)
  const [hours, minutes] = (draftTime.value || '00:00').split(':').map(Number)
  if (!year || !monthPart || !day) {
    close()
    return
  }
  emit('update:modelValue', new Date(year, monthPart - 1, day, hours || 0, minutes || 0).toISOString())
  close()
}

watch(() => props.modelValue, () => {
  if (open.value) syncDraft()
})
</script>

<style scoped>
.focus-dt { position: relative; flex: 1; min-width: 0; }
.focus-dt-trigger { display: flex; width: 100%; min-height: 40px; align-items: center; justify-content: space-between; gap: 8px; padding: 0 12px; border: 1px solid var(--line2); border-radius: 10px; background: var(--card); color: var(--ink); font: inherit; font-size: 13.5px; cursor: pointer; }
.focus-dt-trigger.is-empty { color: var(--muted); }
.focus-dt-trigger:hover { border-color: color-mix(in srgb, var(--accent) 40%, var(--line2)); }
.focus-dt-popover { position: absolute; z-index: 60; top: calc(100% + 6px); left: 0; }
.focus-dt-time { display: flex; align-items: center; gap: 8px; padding: 6px 2px 0; color: var(--muted); }
.focus-dt-time input { flex: 1; min-height: 32px; padding: 0 10px; border: 1px solid var(--line2); border-radius: 8px; background: var(--paper); color: var(--ink); font: inherit; font-size: 13px; }
</style>
