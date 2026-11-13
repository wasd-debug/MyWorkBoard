<template>
  <div class="hke" @click.stop>
    <div class="hke-row">
      <input
        ref="titleInput"
        v-model="title"
        class="hke-title"
        type="text"
        maxlength="200"
        aria-label="修改任务标题"
        @keydown.enter.prevent="saveTitle"
        @keydown.esc.stop="cancelTitle"
      />
      <button class="hke-chip" type="button" :class="{ active: dateOpen }" :aria-expanded="dateOpen" @click="toggleDate">
        <CalendarDays :size="12" />{{ dueLabel }}
      </button>
      <button class="hke-chip" type="button" :aria-label="task.status === 'COMPLETED' ? '重新打开任务' : '完成任务'" @click="toggleDone">
        <Check v-if="task.status !== 'COMPLETED'" :size="12" /><RotateCcw v-else :size="12" />{{ task.status === 'COMPLETED' ? '重开' : '完成' }}
      </button>
      <button class="hke-close" type="button" aria-label="收起编辑" @click="emit('close')"><X :size="13" /></button>
    </div>

    <div class="hke-priority">
      <span>优先级</span>
      <div class="hw-seg">
        <button
          v-for="option in priorityOptions"
          :key="option.value"
          type="button"
          :class="{ active: (task.priority || 'NONE') === option.value }"
          @click="savePriority(option.value)"
        >{{ option.label }}</button>
      </div>
    </div>

    <div v-if="dateOpen" class="hke-date">
      <TaskDatePanel
        :due-at="task.dueAt || ''"
        :start-at="task.startAt || ''"
        :all-day="Boolean(task.allDay)"
        :rrule="task.rrule || ''"
        :recurrence-anchor="task.recurrenceAnchor || 'DUE_DATE'"
        apply-label="保存"
        @apply="applyDate"
        @clear="clearDate"
      />
      <p class="hke-hint">改期、时间段、重复与提醒都用任务页同一套面板。</p>
    </div>

    <p v-if="error" class="hke-error" role="alert">{{ error }}</p>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { CalendarDays, Check, RotateCcw, X } from 'lucide-vue-next'
import TaskDatePanel from '../tasks/TaskDatePanel.vue'
import { useTasksStore } from '../../stores/tasks.js'
import { message } from '../../services/message.js'
import { TASK_PRIORITIES, TASK_PRIORITY_LABELS, taskDateClear, taskDateUpdate, taskPriorityUpdate, taskTitleUpdate } from './homeWidgetEdits.js'
import { dateKeyOf } from './homeMetrics.js'

/**
 * 首页任务就地编辑：标题、优先级、日期都在这张卡片里改完，不必进任务页。
 * 日期面板直接复用 `TaskDatePanel`（任务页/日历页同一个组件），
 * 保存走 `tasksStore.update`，与完整任务详情同一条写入路径。
 */
const props = defineProps({
  task: { type: Object, required: true }
})
const emit = defineEmits(['close', 'saved'])

const tasksStore = useTasksStore()
const title = ref(props.task.title || '')
const error = ref('')
const dateOpen = ref(false)
const titleInput = ref(null)

const priorityOptions = TASK_PRIORITIES.map(value => ({ value, label: TASK_PRIORITY_LABELS[value] }))

const dueLabel = computed(() => {
  const dueAt = String(props.task.dueAt || '')
  if (!dueAt) return '设置日期'
  const key = dueAt.slice(0, 10)
  const todayKey = dateKeyOf(new Date())
  const tomorrow = new Date()
  tomorrow.setDate(tomorrow.getDate() + 1)
  if (key === todayKey) return '今天'
  if (key === dateKeyOf(tomorrow)) return '明天'
  const clock = props.task.allDay || dueAt.length <= 10 ? '' : ` ${dueAt.slice(11, 16)}`
  return `${key.slice(5).replace('-', '/')}${clock}`
})

// 换一条任务时重新回填；首次挂载由上面的初始值负责，避免打开就是空输入框
watch(() => props.task.publicId, () => {
  title.value = props.task.title || ''
  error.value = ''
  dateOpen.value = false
})

// 外部同步改了标题时跟随（如另一设备重命名）；正在输入时不覆盖用户手里这版
watch(() => props.task.title, value => {
  if (document.activeElement === titleInput.value) return
  if (!error.value) title.value = value || ''
})

async function focusTitle() {
  await nextTick()
  titleInput.value?.focus()
}

defineExpose({ focusTitle })

async function saveTitle() {
  const payload = taskTitleUpdate(props.task.title, title.value)
  if (!payload) { title.value = props.task.title || ''; return }
  try {
    await tasksStore.update(props.task, payload)
    error.value = ''
    emit('saved')
  } catch (exception) {
    error.value = exception?.response?.data?.detail || exception?.message || '改名失败'
  }
}

function cancelTitle() {
  title.value = props.task.title || ''
  titleInput.value?.blur()
}

async function savePriority(priority) {
  const payload = taskPriorityUpdate(props.task.priority, priority)
  if (!payload) return
  try {
    await tasksStore.update(props.task, payload)
    error.value = ''
    emit('saved')
  } catch (exception) {
    error.value = exception?.response?.data?.detail || exception?.message || '优先级保存失败'
  }
}

function toggleDate() {
  dateOpen.value = !dateOpen.value
}

async function applyDate(panel) {
  const payload = taskDateUpdate(panel)
  if (!payload) { error.value = '请选择有效的日期'; return }
  try {
    await tasksStore.update(props.task, payload)
    dateOpen.value = false
    error.value = ''
    message.success('任务时间已更新')
    emit('saved')
  } catch (exception) {
    error.value = exception?.response?.data?.detail || exception?.message || '改期失败'
  }
}

async function clearDate() {
  try {
    await tasksStore.update(props.task, taskDateClear())
    dateOpen.value = false
    error.value = ''
    message.success('已清除任务日期')
    emit('saved')
  } catch (exception) {
    error.value = exception?.response?.data?.detail || exception?.message || '清除日期失败'
  }
}

async function toggleDone() {
  try {
    if (props.task.status === 'COMPLETED') await tasksStore.reopen(props.task)
    else await tasksStore.complete(props.task)
    error.value = ''
    emit('saved')
  } catch (exception) {
    error.value = exception?.response?.data?.detail || exception?.message || '状态更新失败'
  }
}
</script>

<style scoped>
.hke { display: flex; flex-direction: column; gap: 8px; margin: 4px 0 8px; padding: 9px 10px; border: 1px solid color-mix(in srgb, var(--accent) 32%, var(--line)); border-radius: 11px; background: color-mix(in srgb, var(--accent-soft) 26%, var(--card)); }
.hke-row { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
.hke-title { min-width: 120px; flex: 1; height: 30px; padding: 0 9px; border: 1px solid var(--line2); border-radius: 8px; background: var(--card); color: var(--ink); font-size: 12.5px; }
.hke-title:focus { border-color: var(--accent); outline: 0; box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 12%, transparent); }
.hke-chip { display: inline-flex; height: 28px; min-height: 0; align-items: center; gap: 4px; padding: 0 8px; border: 1px solid var(--line2); border-radius: 8px; background: var(--card); color: var(--ink2); font-size: 11px; font-weight: 650; }
.hke-chip:hover, .hke-chip.active { border-color: var(--accent); color: var(--accent); }
.hke-close { display: grid; width: 28px; height: 28px; min-width: 0; min-height: 0; place-items: center; padding: 0; border: 0; border-radius: 7px; background: transparent; color: var(--muted); }
.hke-close:hover { background: var(--card); color: var(--ink); }
.hke-priority { display: flex; align-items: center; gap: 8px; color: var(--muted); font-size: 10.5px; }
.hke-date { display: flex; max-height: 360px; flex-direction: column; gap: 4px; overflow: auto; }
.hke-hint { margin: 0; color: var(--muted); font-size: 10px; }
.hke-error { margin: 0; color: var(--down); font-size: 11px; }
@media (max-width: 767px) {
  .hke-chip, .hke-title { height: 34px; }
  .hke-close { width: 34px; height: 34px; }
}
</style>
