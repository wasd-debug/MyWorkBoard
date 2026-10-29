<template>
  <div ref="rootEl" class="focus-dt">
    <button ref="triggerEl" type="button" class="focus-dt-trigger" :class="{ 'is-empty': !modelValue }" :aria-expanded="open" @click.stop="toggle">
      <span>{{ display || placeholder }}</span>
      <ChevronDown :size="15" />
    </button>

    <!-- 浮层挂到 body 上，不受对话框 overflow 裁剪，永远单独盖在最上层 -->
    <Teleport to="body">
      <div v-if="open" ref="popoverEl" class="focus-dt-popover" :style="popoverStyle" @click.stop>
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
    </Teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ChevronDown, Clock3 } from 'lucide-vue-next'
import TaskDateTimePopover from './TaskDateTimePopover.vue'
import { apiGetTaskCalendar } from '../../../packages/api-client/src/index.js'
import { formatLunarLabel } from '../../views/calendar/calendarLunar.js'
import { useExclusivePanels } from '../../utils/exclusivePanels.js'

const props = defineProps({
  /** ISO 字符串，空串表示未选 */
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '选择时间' },
  /** 与同一命名空间内的其它浮层互斥（如补记弹窗里的任务选择器、类型浮层） */
  panelGroup: { type: String, default: '' },
  /** 本浮层在组里的名字，同组内唯一 */
  panelName: { type: String, default: 'date' }
})
const emit = defineEmits(['update:modelValue'])

const rootEl = ref(null)
const triggerEl = ref(null)
const popoverEl = ref(null)
const popoverStyle = ref({})
const open = ref(false)
const month = ref(new Date())
const draftKey = ref('')
const draftTime = ref('')
const lunarMap = ref({})
let lunarEpoch = 0

// 命名空间必须「每个实例各不相同」，否则同组的另一个实例会把广播当自己人跳过；
// 但又要能收到同组兄弟的广播，所以用「组名-字段名」拼出实例级命名空间。
useExclusivePanels(props.panelGroup ? `${props.panelGroup}-${props.panelName}` : `focus-dt-${props.panelName}`, [
  [props.panelName, () => open.value, value => { if (!value) open.value = false }]
])

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

/** 定位到触发按钮下方；下方放不下就翻到上方，再不行就贴住视口底部 */
function placePopover() {
  const trigger = triggerEl.value
  const pop = popoverEl.value
  if (!trigger || !pop) return
  const rect = trigger.getBoundingClientRect()
  // 外层只是定位壳（块级、占满宽度），真实尺寸要量里面的日历面板
  const box = pop.firstElementChild || pop
  const width = box.offsetWidth
  const height = box.offsetHeight
  const margin = 10
  const gap = 6
  let top = rect.bottom + gap
  if (top + height > window.innerHeight - margin) {
    const above = rect.top - gap - height
    top = above >= margin ? above : Math.max(margin, window.innerHeight - margin - height)
  }
  const left = Math.min(Math.max(margin, rect.left), Math.max(margin, window.innerWidth - margin - width))
  popoverStyle.value = { position: 'fixed', top: `${Math.round(top)}px`, left: `${Math.round(left)}px` }
}

function toggle() {
  if (open.value) {
    close()
    return
  }
  syncDraft()
  open.value = true
  void nextTick(placePopover)
  loadLunar(month.value)
}

function close() {
  open.value = false
}

function onDocPointerDown(event) {
  if (!open.value) return
  const target = event.target
  if (rootEl.value?.contains(target) || popoverEl.value?.contains(target)) return
  close()
}

function onViewportChange() {
  if (open.value) placePopover()
}

function onKeydown(event) {
  if (event.key === 'Escape' && open.value) {
    event.stopPropagation()
    close()
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocPointerDown, true)
  window.addEventListener('resize', onViewportChange)
  window.addEventListener('scroll', onViewportChange, true)
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocPointerDown, true)
  window.removeEventListener('resize', onViewportChange)
  window.removeEventListener('scroll', onViewportChange, true)
  window.removeEventListener('keydown', onKeydown)
})

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
/* 浮层自身的位置由内联 style（fixed）决定，这里只管层级和外观 */
.focus-dt-popover { z-index: 220; }
.focus-dt-time { display: flex; align-items: center; gap: 8px; padding: 6px 2px 0; color: var(--muted); }
.focus-dt-time input { flex: 1; min-height: 32px; padding: 0 10px; border: 1px solid var(--line2); border-radius: 8px; background: var(--paper); color: var(--ink); font: inherit; font-size: 13px; }
</style>
