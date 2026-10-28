<template>
  <section class="ctc" :style="style" role="dialog" aria-label="任务详情" @click.stop="closeMenus">
    <header class="ctc-bar">
      <button
        type="button"
        class="ctc-status"
        :class="{ done: isDone }"
        :aria-pressed="isDone"
        :aria-label="isDone ? '重新打开任务' : '标记完成'"
        @click="$emit(isDone ? 'reopen' : 'complete')"
      >
        <Check v-if="isDone" :size="12" />
      </button>
      <button ref="dateBtn" type="button" class="ctc-date" :class="{ overdue: isOverdue }" :aria-expanded="dateOpen" @click.stop="toggleDate">
        <CalendarClock :size="13" /><span>{{ dateLabel }}</span>
      </button>
      <span class="ctc-repeat" v-if="task.rrule || task.seriesId" title="重复任务"><Repeat2 :size="12" /></span>
      <span class="ctc-spacer"></span>
      <button type="button" class="ctc-flag" :class="`is-${priorityKey}`" :title="`优先级：${priorityLabel}`" :aria-label="`优先级：${priorityLabel}`" @click="cyclePriority">
        <Flag :size="15" />
      </button>
      <button type="button" class="ctc-close" aria-label="关闭详情" @click="$emit('close')"><X :size="14" /></button>
    </header>

    <div v-if="focusMinutes > 0" class="ctc-focus">
      <span>已专注</span><Timer :size="12" /><b>{{ focusLabel }}</b>
    </div>

    <input
      v-model="titleDraft"
      class="ctc-title"
      :class="{ done: isDone }"
      aria-label="任务标题"
      maxlength="200"
      @blur="commitTitle"
      @keydown.enter.prevent="$event.target.blur()"
    />

    <div class="ctc-note">
      <textarea
        v-model="noteDraft"
        class="ctc-note-input"
        :rows="noteRows"
        placeholder="添加描述…"
        aria-label="任务描述"
        @blur="commitNote"
        @input="autosize"
      ></textarea>
    </div>

    <ul v-if="checklist.length" class="ctc-checklist">
      <li v-for="item in checklist" :key="item.publicId || item.title">
        <button type="button" class="ctc-ci" :class="{ done: item.completed }" :aria-pressed="item.completed" @click="toggleChecklist(item)">
          <Check v-if="item.completed" :size="9" />
        </button>
        <span :class="{ done: item.completed }">{{ item.title }}</span>
      </li>
    </ul>

    <div class="ctc-tags">
      <span v-for="tag in taskTags" :key="tag.publicId" class="ctc-tag" :style="{ borderColor: tag.color, color: tag.color }">
        {{ tag.name }}
        <button type="button" :aria-label="`移除标签 ${tag.name}`" @click="removeTag(tag)"><X :size="9" /></button>
      </span>
      <div class="ctc-tag-add">
        <button type="button" class="ctc-tag-plus" aria-label="添加标签" @click.stop="tagMenu = !tagMenu"><Plus :size="11" /></button>
        <div v-if="tagMenu" class="ctc-menu is-tags" @click.stop>
          <button v-for="tag in availableTags" :key="tag.publicId" type="button" @click="addTag(tag)">
            <i :style="{ background: tag.color }"></i>{{ tag.name }}
          </button>
          <p v-if="!availableTags.length">没有更多标签</p>
        </div>
      </div>
    </div>

    <footer class="ctc-footer">
      <div class="ctc-list-wrap">
        <button type="button" class="ctc-list" @click.stop="listMenu = !listMenu">
          <FolderClosed :size="13" :style="{ color: listColor }" />{{ listName }}
        </button>
        <div v-if="listMenu" class="ctc-menu is-lists" @click.stop>
          <button v-for="list in lists" :key="list.publicId" type="button" :class="{ on: list.publicId === task.listId }" @click="moveList(list)">
            <i :style="{ background: list.color || 'var(--muted)' }"></i>{{ list.name }}
          </button>
        </div>
      </div>
      <div class="ctc-actions">
        <button type="button" title="开始专注" aria-label="开始专注" @click="$emit('focus')"><Timer :size="14" /></button>
        <button type="button" title="在任务页打开" aria-label="在任务页打开" @click="$emit('open-detail')"><ExternalLink :size="14" /></button>
        <button type="button" title="删除任务" aria-label="删除任务" class="is-danger" @click="$emit('delete')"><Trash2 :size="14" /></button>
      </div>
    </footer>

    <Teleport to="body">
      <div v-if="dateOpen" class="ctc-date-popover" :style="datePopoverStyle" @click.stop>
        <TaskDateTimePopover
          :mode="dateMode"
          :month="dateMonth"
          :calendar-days="dateCells"
          :month-label="dateMonthLabel"
          :selected-key="dateSelectedKey"
          :time="dueTime"
          :all-day="allDayModel"
          :range-start="startKeyModel"
          :range-end="endKeyModel"
          :range-start-time="startTime"
          :range-end-time="dueTime"
          apply-label="确定"
          @update:mode="dateMode = $event"
          @quick-date="onQuickDate"
          @move-month="dateMonth = shiftMonth(dateMonth, $event)"
          @select-date="onSelectDate"
          @clear-date="clearDate"
          @toggle-all-day="allDayModel = $event"
          @update:range-start-time="startTime = $event"
          @update:range-end-time="dueTime = $event"
          @apply="applyDate"
        >
          <template #rows="{ mode }">
            <div class="ctc-date-rows">
              <div class="ctc-date-row-line">
                <Clock :size="14" /><span>时间</span>
                <input v-if="mode === 'RANGE'" v-model="startTime" type="time" aria-label="开始时间" />
                <input v-model="dueTime" type="time" aria-label="时间" />
              </div>
              <label class="ctc-date-switch">
                <span>全天</span>
                <button type="button" role="switch" :aria-checked="allDayModel" :class="{ on: allDayModel }" @click="allDayModel = !allDayModel"><i></i></button>
              </label>

              <div class="ctc-date-row-line is-row">
                <Bell :size="14" /><span>提醒</span>
                <button type="button" class="ctc-date-rowvalue" :class="{ on: reminderOn }" :aria-expanded="reminderOpen" @click.stop="toggleReminder">
                  {{ reminderOn ? '已开启' : '未设置' }}
                  <ChevronDown :size="13" :class="{ rotated: reminderOpen }" />
                </button>
              </div>
              <div v-if="reminderOpen" class="ctc-date-sub" @click.stop>
                <p v-if="reminderLoading" class="ctc-date-hint">正在读取提醒…</p>
                <template v-else>
                  <label class="ctc-date-check"><input v-model="reminderForm.enabled" type="checkbox" />启用站内提醒</label>
                  <template v-if="reminderForm.enabled">
                    <label class="ctc-date-field">方式
                      <select v-model="reminderForm.kind">
                        <option value="RELATIVE">相对截止时间</option>
                        <option value="ABSOLUTE">指定时间</option>
                      </select>
                    </label>
                    <label v-if="reminderForm.kind === 'RELATIVE'" class="ctc-date-field">提前
                      <select v-model.number="reminderForm.offsetMinutes">
                        <option :value="0">准时</option>
                        <option :value="5">5 分钟</option>
                        <option :value="15">15 分钟</option>
                        <option :value="30">30 分钟</option>
                        <option :value="60">1 小时</option>
                        <option :value="1440">1 天</option>
                      </select>
                    </label>
                    <label v-else class="ctc-date-field">提醒时间<input v-model="reminderForm.remindLocal" type="datetime-local" /></label>
                    <label class="ctc-date-check"><input v-model="reminderForm.dailyUntilDone" type="checkbox" />未完成时每日提醒</label>
                  </template>
                  <p v-if="reminderError" class="ctc-date-error">{{ reminderError }}</p>
                  <div class="ctc-date-actions">
                    <button v-if="reminder" type="button" class="is-ghost" @click="disableReminder">关闭提醒</button>
                    <button type="button" class="is-primary" :disabled="reminderSaving" @click="saveReminder">{{ reminderSaving ? '保存中…' : '保存提醒' }}</button>
                  </div>
                </template>
              </div>

              <div class="ctc-date-row-line is-row">
                <Repeat2 :size="14" /><span>重复</span>
                <button type="button" class="ctc-date-rowvalue" :class="{ on: Boolean(task.rrule) }" :aria-expanded="repeatOpen" @click.stop="repeatOpen = !repeatOpen">
                  {{ recurrenceLabel }}
                  <ChevronDown :size="13" :class="{ rotated: repeatOpen }" />
                </button>
              </div>
              <div v-if="repeatOpen" class="ctc-date-sub" @click.stop>
                <div class="ctc-date-presets">
                  <button
                    v-for="option in repeatPresets"
                    :key="option.value"
                    type="button"
                    :class="{ on: (task.rrule || '') === option.value }"
                    @click="applyRepeat(option.value)"
                  >{{ option.label }}</button>
                </div>
                <p class="ctc-date-hint">更细的重复规则（按周几、按农历、结束条件）请在任务页设置。</p>
              </div>
            </div>
          </template>
        </TaskDateTimePopover>
      </div>
    </Teleport>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { Bell, CalendarClock, Check, ChevronDown, Clock, ExternalLink, Flag, FolderClosed, Plus, Repeat2, Timer, Trash2, X } from 'lucide-vue-next'
import TaskDateTimePopover from '../../components/tasks/TaskDateTimePopover.vue'
import {
  apiCreateTaskReminder, apiDeleteTaskReminder, apiListTaskReminders, apiUpdateTaskReminder
} from '../../../packages/api-client/src/index.js'

const props = defineProps({
  task: { type: Object, required: true },
  lists: { type: Array, default: () => [] },
  tags: { type: Array, default: () => [] },
  lunar: { type: Object, default: () => ({}) },
  focusMinutes: { type: Number, default: 0 },
  style: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['close', 'complete', 'reopen', 'update', 'delete', 'focus', 'open-detail'])

const PRIORITY_CYCLE = ['HIGH', 'MEDIUM', 'LOW', 'NONE']
const PRIORITY_LABEL = { HIGH: '高', MEDIUM: '中', LOW: '低', NONE: '无' }
const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日']

const titleDraft = ref(props.task.title || '')
const noteDraft = ref(props.task.description || '')
const noteRows = ref(3)
const dateBtn = ref(null)
const dateOpen = ref(false)
const dateMode = ref(props.task.startAt && props.task.dueAt && !props.task.allDay ? 'RANGE' : 'DATE')
const dateMonth = ref(new Date())
const listMenu = ref(false)
const tagMenu = ref(false)
const allDayModel = ref(Boolean(props.task.allDay))
const repeatOpen = ref(false)
const reminderOpen = ref(false)
const reminderLoading = ref(false)
const reminderSaving = ref(false)
const reminderError = ref('')
const reminder = ref(null)
const reminderForm = reactive({ enabled: false, kind: 'RELATIVE', offsetMinutes: 30, remindLocal: '', dailyUntilDone: false })
const startTime = ref(toLocalTime(props.task.startAt))
const dueTime = ref(toLocalTime(props.task.dueAt))
const startKeyModel = ref(toKey(props.task.startAt))
const endKeyModel = ref(toKey(props.task.dueAt))

watch(() => props.task.publicId, () => {
  titleDraft.value = props.task.title || ''
  noteDraft.value = props.task.description || ''
  dateOpen.value = false
  listMenu.value = false
  tagMenu.value = false
  repeatOpen.value = false
  reminderOpen.value = false
  reminder.value = null
  reminderError.value = ''
  Object.assign(reminderForm, { enabled: false, kind: 'RELATIVE', offsetMinutes: 30, remindLocal: '', dailyUntilDone: false })
  allDayModel.value = Boolean(props.task.allDay)
  startTime.value = toLocalTime(props.task.startAt)
  dueTime.value = toLocalTime(props.task.dueAt)
  startKeyModel.value = toKey(props.task.startAt)
  endKeyModel.value = toKey(props.task.dueAt)
})

watch(() => props.task.title, value => {
  if (!titleDraft.value || document.activeElement?.classList?.contains('ctc-title') === false) titleDraft.value = value || titleDraft.value
})

const isDone = computed(() => props.task.status === 'COMPLETED')
const priorityKey = computed(() => String(props.task.priority || 'NONE').toLowerCase())
const priorityLabel = computed(() => PRIORITY_LABEL[props.task.priority || 'NONE'] || '无')

const REPEAT_PRESETS = [
  { value: '', label: '不重复' },
  { value: 'FREQ=DAILY', label: '每天' },
  { value: 'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR', label: '每个工作日' },
  { value: 'FREQ=WEEKLY', label: '每周' },
  { value: 'FREQ=MONTHLY', label: '每月' },
  { value: 'FREQ=YEARLY', label: '每年' }
]
const repeatPresets = REPEAT_PRESETS
const recurrenceLabel = computed(() => {
  const value = props.task.rrule || ''
  if (!value) return '不重复'
  return REPEAT_PRESETS.find(option => option.value === value)?.label || '自定义'
})
const reminderOn = computed(() => Boolean(reminder.value) || reminderForm.enabled)
const checklist = computed(() => (props.task.checklist || []).filter(item => item && item.title))
const taskTags = computed(() => {
  const ids = props.task.tagIds || []
  return ids.map(id => props.tags.find(tag => tag.publicId === id)).filter(Boolean)
})
const availableTags = computed(() => {
  const ids = new Set(props.task.tagIds || [])
  return props.tags.filter(tag => !ids.has(tag.publicId))
})
const listName = computed(() => props.lists.find(item => item.publicId === props.task.listId)?.name || '收集箱')
const listColor = computed(() => props.lists.find(item => item.publicId === props.task.listId)?.color || 'var(--muted)')
const focusLabel = computed(() => {
  const minutes = props.focusMinutes
  if (minutes < 60) return `${Math.round(minutes)}m`
  const hours = minutes / 60
  return `${Math.round(hours * 10) / 10}h`
})

const dateLabel = computed(() => {
  const raw = props.task.dueAt || props.task.startAt
  if (!raw) return '设置日期'
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return '设置日期'
  const base = `${date.getMonth() + 1}月${date.getDate()}日`
  if (props.task.allDay) return base
  return `${base} ${toLocalTime(raw)}`
})
const isOverdue = computed(() => {
  const raw = props.task.dueAt || props.task.startAt
  if (!raw || isDone.value) return false
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return false
  if (props.task.allDay) {
    const today = new Date()
    return date.getFullYear() < today.getFullYear()
      || (date.getFullYear() === today.getFullYear() && date.getMonth() < today.getMonth())
      || (date.getFullYear() === today.getFullYear() && date.getMonth() === today.getMonth() && date.getDate() < today.getDate())
  }
  return date.getTime() < Date.now()
})

const dateMonthLabel = computed(() => `${dateMonth.value.getFullYear()}年${dateMonth.value.getMonth() + 1}月`)
const dateSelectedKey = computed(() => endKeyModel.value || startKeyModel.value || '')
const dateCells = computed(() => {
  const first = new Date(dateMonth.value.getFullYear(), dateMonth.value.getMonth(), 1)
  const offset = (first.getDay() + 6) % 7
  const start = new Date(first.getFullYear(), first.getMonth(), first.getDate() - offset)
  const todayKey = toKey(new Date())
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + index)
    const key = toKey(date)
    return {
      date,
      key,
      lunar: props.lunar[key] || '',
      otherMonth: date.getMonth() !== first.getMonth(),
      today: key === todayKey,
      selected: key === dateSelectedKey.value,
      repeat: Boolean(props.task.rrule) && key === dateSelectedKey.value
    }
  })
})

const datePopoverStyle = computed(() => {
  const rect = dateBtn.value?.getBoundingClientRect?.()
  const width = 378
  const left = rect ? Math.min(Math.max(8, rect.left - 12), Math.max(8, window.innerWidth - width - 12)) : 40
  const top = rect ? Math.min(rect.bottom + 6, Math.max(8, window.innerHeight - 560)) : 80
  return { position: 'fixed', left: `${left}px`, top: `${top}px`, zIndex: '70' }
})

function toKey(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function toLocalTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function shiftMonth(month, delta) {
  return new Date(month.getFullYear(), month.getMonth() + delta, 1)
}

// 点卡片空白处收起行内菜单；菜单自身已 stop，不会误关
function closeMenus() {
  listMenu.value = false
  tagMenu.value = false
  repeatOpen.value = false
}

function applyRepeat(value) {
  repeatOpen.value = false
  const next = value === 'CUSTOM' ? 'CUSTOM' : value
  if (next === 'CUSTOM') {
    emit('open-detail')
    return
  }
  if ((props.task.rrule || '') === next) return
  emit('update', { rrule: next })
}

async function toggleReminder() {
  const next = !reminderOpen.value
  reminderOpen.value = next
  repeatOpen.value = false
  if (!next) return
  reminderError.value = ''
  if (reminder.value || reminderForm.enabled) return
  reminderLoading.value = true
  try {
    const list = await apiListTaskReminders(props.task.publicId)
    const found = list?.[0] || null
    reminder.value = found
    if (found) {
      Object.assign(reminderForm, {
        enabled: true,
        kind: found.kind || 'RELATIVE',
        offsetMinutes: found.offsetMinutes ?? 30,
        remindLocal: found.remindAt ? toLocalValue(found.remindAt) : '',
        dailyUntilDone: Boolean(found.dailyUntilDone)
      })
    }
  } catch (cause) {
    reminderError.value = cause?.response?.data?.detail || cause?.message || '提醒读取失败'
  } finally {
    reminderLoading.value = false
  }
}

async function saveReminder() {
  if (reminderSaving.value) return
  reminderError.value = ''
  if (reminderForm.enabled && reminderForm.kind === 'RELATIVE' && !props.task.dueAt) {
    reminderError.value = '相对提醒需要先给任务设置截止时间'
    return
  }
  if (reminderForm.enabled && reminderForm.kind === 'ABSOLUTE' && !reminderForm.remindLocal) {
    reminderError.value = '请选择提醒时间'
    return
  }
  reminderSaving.value = true
  try {
    if (!reminderForm.enabled) {
      if (reminder.value) await apiDeleteTaskReminder(props.task.publicId, reminder.value.publicId, reminder.value.revision)
      reminder.value = null
    } else {
      const payload = {
        kind: reminderForm.kind,
        offsetMinutes: reminderForm.kind === 'RELATIVE' ? Number(reminderForm.offsetMinutes) : null,
        remindAt: reminderForm.kind === 'ABSOLUTE' ? new Date(reminderForm.remindLocal).toISOString() : null,
        channel: 'IN_APP',
        dailyUntilDone: reminderForm.dailyUntilDone
      }
      reminder.value = reminder.value
        ? await apiUpdateTaskReminder(props.task.publicId, reminder.value.publicId, payload, reminder.value.revision)
        : await apiCreateTaskReminder(props.task.publicId, payload)
    }
    reminderOpen.value = false
  } catch (cause) {
    reminderError.value = cause?.response?.data?.detail || cause?.message || '提醒保存失败'
  } finally {
    reminderSaving.value = false
  }
}

async function disableReminder() {
  reminderForm.enabled = false
  await saveReminder()
}

function toLocalValue(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function commitTitle() {
  const next = titleDraft.value.trim()
  if (!next || next === props.task.title) {
    titleDraft.value = props.task.title || ''
    return
  }
  emit('update', { title: next })
}

function commitNote() {
  const next = noteDraft.value
  if (next === (props.task.description || '')) return
  emit('update', { description: next })
}

function autosize(event) {
  const el = event.target
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(180, el.scrollHeight)}px`
}

function cyclePriority() {
  const index = PRIORITY_CYCLE.indexOf(props.task.priority || 'NONE')
  emit('update', { priority: PRIORITY_CYCLE[(index + 1) % PRIORITY_CYCLE.length] })
}

function toggleChecklist(item) {
  const next = (props.task.checklist || []).map(entry => (
    entry === item || entry.publicId === item.publicId ? { ...entry, completed: !entry.completed } : entry
  ))
  emit('update', { checklist: next })
}

function addTag(tag) {
  tagMenu.value = false
  const ids = new Set(props.task.tagIds || [])
  ids.add(tag.publicId)
  emit('update', { tagIds: [...ids] })
}

function removeTag(tag) {
  emit('update', { tagIds: (props.task.tagIds || []).filter(id => id !== tag.publicId) })
}

function moveList(list) {
  listMenu.value = false
  if (list.publicId === props.task.listId) return
  emit('update', { listId: list.publicId })
}

function toggleDate() {
  const next = !dateOpen.value
  dateOpen.value = next
  listMenu.value = false
  tagMenu.value = false
  if (!next) return
  dateMonth.value = new Date(
    Number((endKeyModel.value || startKeyModel.value || '').slice(0, 4)) || new Date().getFullYear(),
    Number((endKeyModel.value || startKeyModel.value || '').slice(5, 7)) - 1 || new Date().getMonth(),
    1
  )
}

function onQuickDate(kind) {
  const today = new Date()
  const target = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  if (kind === 'TOMORROW') target.setDate(target.getDate() + 1)
  if (kind === 'NEXT_WEEK') target.setDate(target.getDate() + 7)
  if (kind === 'NEXT_MONTH') target.setMonth(target.getMonth() + 1)
  const key = toKey(target)
  startKeyModel.value = key
  endKeyModel.value = key
  dateMonth.value = new Date(target.getFullYear(), target.getMonth(), 1)
}

function onSelectDate(key) {
  if (dateMode.value === 'RANGE' && startKeyModel.value && !endKeyModel.value) {
    endKeyModel.value = key
    return
  }
  startKeyModel.value = key
  endKeyModel.value = key
}

function clearDate() {
  startKeyModel.value = ''
  endKeyModel.value = ''
  dateOpen.value = false
  emit('update', { startAt: '', dueAt: '', allDay: false })
}

function applyDate() {
  const startKey = startKeyModel.value || endKeyModel.value
  const endKey = endKeyModel.value || startKeyModel.value
  if (!startKey) {
    clearDate()
    return
  }
  const build = (key, time) => {
    const [hour, minute] = allDayModel.value ? [0, 0] : String(time || '09:00').split(':').map(Number)
    const [year, month, day] = key.split('-').map(Number)
    return new Date(year, month - 1, day, hour || 0, minute || 0, 0)
  }
  const start = build(startKey, startTime.value || '09:00')
  let due = build(endKey || startKey, dueTime.value || startTime.value || '10:00')
  if (due.getTime() <= start.getTime()) due = new Date(start.getTime() + 30 * 60000)
  dateOpen.value = false
  emit('update', {
    startAt: start.toISOString(),
    dueAt: due.toISOString(),
    allDay: allDayModel.value
  })
}
</script>

<style scoped>
.ctc {
  position: fixed;
  z-index: 58;
  display: flex;
  width: 340px;
  max-height: min(640px, calc(100vh - 72px));
  flex-direction: column;
  overflow: auto;
  border: 1px solid var(--line2);
  border-radius: 12px;
  background: var(--card);
  box-shadow: 0 20px 48px rgb(15 23 42 / 24%);
  color: var(--ink);
  overscroll-behavior: contain;
}
.ctc-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px 8px;
}
.ctc-status {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 1.6px solid color-mix(in srgb, var(--ink) 34%, transparent);
  border-radius: 5px;
  background: transparent;
  color: #fff;
  cursor: pointer;
}
.ctc-status.done {
  border-color: var(--accent);
  background: var(--accent);
}
.ctc-date {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 24px;
  padding: 0 4px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--ink2);
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}
.ctc-date:hover {
  background: var(--paper);
}
.ctc-date.overdue {
  color: var(--down, #e34d59);
}
.ctc-repeat {
  display: inline-flex;
  color: var(--muted);
}
.ctc-spacer {
  flex: 1;
}
.ctc-flag {
  display: grid;
  place-items: center;
  width: 26px;
  height: 24px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}
.ctc-flag:hover {
  background: var(--paper);
}
.ctc-flag.is-high { color: var(--priority-high, #e34d59); }
.ctc-flag.is-medium { color: var(--priority-medium, #e69b2d); }
.ctc-flag.is-low { color: var(--priority-low, #4d78db); }
.ctc-close {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}
.ctc-close:hover {
  background: var(--paper);
  color: var(--ink);
}
.ctc-focus {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 0 12px 6px;
  color: var(--muted);
  font-size: 11.5px;
}
.ctc-focus b {
  color: var(--accent);
  font-weight: 650;
}
.ctc-title {
  width: 100%;
  padding: 2px 12px 6px;
  border: 0;
  background: transparent;
  color: var(--ink);
  font-size: 17px;
  font-weight: 650;
  line-height: 1.35;
}
.ctc-title:focus {
  outline: none;
}
.ctc-title.done {
  color: var(--muted);
  text-decoration: line-through;
}
.ctc-note {
  padding: 0 12px 8px;
}
.ctc-note-input {
  width: 100%;
  min-height: 54px;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: var(--paper);
  color: var(--ink);
  font: inherit;
  font-size: 12.5px;
  line-height: 1.55;
  resize: vertical;
}
.ctc-note-input:focus {
  outline: none;
  border-color: color-mix(in srgb, var(--accent) 45%, var(--line));
  background: var(--card);
}
.ctc-note-input::placeholder {
  color: var(--muted);
}
.ctc-checklist {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0 12px 8px;
  list-style: none;
}
.ctc-checklist li {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  font-size: 12.5px;
  line-height: 1.5;
}
.ctc-ci {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 14px;
  height: 14px;
  margin-top: 2px;
  padding: 0;
  border: 1.4px solid color-mix(in srgb, var(--ink) 30%, transparent);
  border-radius: 4px;
  background: transparent;
  color: #fff;
  cursor: pointer;
}
.ctc-ci.done {
  border-color: var(--accent);
  background: var(--accent);
}
.ctc-checklist span.done {
  color: var(--muted);
  text-decoration: line-through;
}
.ctc-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
  padding: 0 12px 10px;
}
.ctc-tag {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 21px;
  padding: 0 3px 0 8px;
  border: 1px solid currentColor;
  border-radius: 11px;
  font-size: 11px;
}
.ctc-tag button {
  display: grid;
  place-items: center;
  width: 14px;
  height: 14px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: inherit;
  cursor: pointer;
  opacity: .7;
}
.ctc-tag button:hover {
  opacity: 1;
}
.ctc-tag-add {
  position: relative;
}
.ctc-tag-plus {
  display: grid;
  place-items: center;
  width: 21px;
  height: 21px;
  border: 1px solid var(--line2);
  border-radius: 50%;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}
.ctc-tag-plus:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.ctc-menu {
  position: absolute;
  z-index: 4;
  display: flex;
  min-width: 148px;
  max-height: 220px;
  flex-direction: column;
  overflow: auto;
  padding: 4px;
  border: 1px solid var(--line2);
  border-radius: 8px;
  background: var(--card);
  box-shadow: 0 12px 30px rgb(15 23 42 / 18%);
}
.ctc-menu.is-tags {
  top: 26px;
  left: -8px;
}
.ctc-menu button {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 27px;
  padding: 0 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--ink2);
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}
.ctc-menu button:hover {
  background: var(--paper);
  color: var(--ink);
}
.ctc-menu button.on {
  color: var(--accent);
}
.ctc-menu i {
  flex: 0 0 auto;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.ctc-menu p {
  margin: 6px 8px;
  color: var(--muted);
  font-size: 11px;
}
.ctc-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: auto;
  padding: 8px 12px 10px;
  border-top: 1px solid var(--line);
}
.ctc-list-wrap {
  position: relative;
  min-width: 0;
}
.ctc-list {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 168px;
  min-height: 26px;
  padding: 0 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--ink2);
  font-size: 12px;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ctc-list:hover {
  background: var(--paper);
}
.ctc-menu.is-lists {
  bottom: 30px;
  left: 0;
}
.ctc-actions {
  display: flex;
  gap: 2px;
  margin-left: auto;
}
.ctc-actions button {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}
.ctc-actions button:hover {
  background: var(--paper);
  color: var(--ink);
}
.ctc-actions button.is-danger:hover {
  color: var(--down, #e34d59);
}
.ctc-date-popover {
  width: min(378px, calc(100vw - 16px));
}
.ctc-date-rows {
  display: grid;
  gap: 6px;
  padding-top: 8px;
  border-top: 1px solid var(--line);
}
.ctc-date-row-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 30px;
  color: var(--ink2);
  font-size: 12px;
}
.ctc-date-row-line span {
  flex: 1;
}
.ctc-date-row-line input {
  width: 104px;
  min-height: 28px;
  padding: 0 6px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: 12px;
}
.ctc-date-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 30px;
  color: var(--ink2);
  font-size: 12px;
}
.ctc-date-switch button {
  position: relative;
  width: 38px;
  height: 21px;
  border: 0;
  border-radius: 11px;
  background: var(--line2);
  cursor: pointer;
  transition: background .15s ease;
}
.ctc-date-switch button.on {
  background: var(--accent);
}
.ctc-date-switch button i {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 17px;
  height: 17px;
  border-radius: 50%;
  background: #fff;
  transition: transform .15s ease;
}
.ctc-date-switch button.on i {
  transform: translateX(17px);
}
.ctc-date-row-line.is-row {
  border-top: 1px solid var(--line);
}
.ctc-date-rowvalue {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: auto;
  padding: 2px 4px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--muted);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.ctc-date-rowvalue:hover {
  background: var(--paper);
  color: var(--ink);
}
.ctc-date-rowvalue.on {
  color: var(--accent);
}
.ctc-date-rowvalue svg.rotated {
  transform: rotate(180deg);
}
.ctc-date-sub {
  display: grid;
  gap: 7px;
  padding: 4px 2px 8px 22px;
}
.ctc-date-field {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ink2);
  font-size: 12px;
}
.ctc-date-field select,
.ctc-date-field input {
  min-width: 0;
  flex: 1;
  min-height: 28px;
  padding: 0 6px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: 12px;
}
.ctc-date-check {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--ink2);
  font-size: 12px;
}
.ctc-date-check input {
  width: 14px;
  height: 14px;
  margin: 0;
}
.ctc-date-presets {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px;
}
.ctc-date-presets button {
  min-height: 28px;
  padding: 0 4px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: transparent;
  color: var(--ink2);
  font-size: 11.5px;
  cursor: pointer;
}
.ctc-date-presets button:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.ctc-date-presets button.on {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
}
.ctc-date-hint {
  margin: 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.5;
}
.ctc-date-error {
  margin: 0;
  color: var(--down, #e34d59);
  font-size: 11.5px;
}
.ctc-date-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}
.ctc-date-actions button {
  min-height: 28px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: transparent;
  color: var(--ink2);
  font-size: 12px;
  cursor: pointer;
}
.ctc-date-actions button.is-primary {
  border-color: transparent;
  background: var(--accent);
  color: var(--accent-contrast, #fff);
}
.ctc-date-actions button.is-ghost:hover {
  border-color: var(--down, #e34d59);
  color: var(--down, #e34d59);
}
</style>
