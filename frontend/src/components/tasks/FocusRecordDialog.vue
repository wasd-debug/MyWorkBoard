<template>
  <Teleport to="body">
    <div v-if="open" class="focus-record-mask" @click.self="close">
      <section class="focus-record-dialog" role="dialog" aria-label="添加专注记录">
        <header>
          <h2>添加专注记录</h2>
          <button type="button" class="focus-record-close" aria-label="关闭" @click="close"><X :size="16" /></button>
        </header>

        <label class="focus-record-row">
          <span class="focus-record-label">专注任务</span>
          <div class="focus-record-control is-relative">
            <button type="button" class="focus-record-select" :class="{ 'is-empty': !target }" @click.stop="taskPickerOpen = !taskPickerOpen">
              <span>{{ target?.title || '选择任务' }}</span>
              <ChevronDown :size="15" />
            </button>
            <FocusTargetPicker v-if="taskPickerOpen" class="focus-record-picker" @pick="pickTarget" />
          </div>
        </label>

        <div class="focus-record-row">
          <span class="focus-record-label">开始时间</span>
          <FocusDateTimeField v-model="startAt" placeholder="选择时间" />
        </div>

        <div class="focus-record-row">
          <span class="focus-record-label">结束时间</span>
          <FocusDateTimeField v-model="endAt" placeholder="选择时间" />
        </div>

        <div class="focus-record-row">
          <span class="focus-record-label">类型</span>
          <div class="focus-record-control is-relative">
            <button type="button" class="focus-record-select" @click.stop="typeOpen = !typeOpen">
              <span>{{ typeLabel }}</span>
              <ChevronDown :size="15" />
            </button>
            <div v-if="typeOpen" class="focus-type-popover" @click.stop>
              <div class="focus-type-tabs" role="tablist" aria-label="计时类型">
                <button type="button" role="tab" :class="{ on: typeKind === 'POMODORO' }" :aria-selected="typeKind === 'POMODORO'" @click="switchTypeKind('POMODORO')">番茄计时</button>
                <button type="button" role="tab" :class="{ on: typeKind === 'COUNTUP' }" :aria-selected="typeKind === 'COUNTUP'" @click="switchTypeKind('COUNTUP')">正计时</button>
              </div>
              <div class="focus-type-duration">
                <input v-model.number="draftHours" type="number" min="0" max="12" aria-label="小时" />
                <span>小时</span>
                <input v-model.number="draftMinutes" type="number" min="0" max="59" aria-label="分钟" />
                <span>分钟</span>
              </div>
              <footer>
                <button type="button" class="focus-type-ghost" @click="typeOpen = false">取消</button>
                <button type="button" class="focus-type-primary" :disabled="draftTotal <= 0" @click="confirmType">确定</button>
              </footer>
            </div>
          </div>
        </div>

        <div class="focus-record-row is-textarea">
          <span class="focus-record-label">专注笔记</span>
          <textarea v-model="note" class="focus-record-textarea" maxlength="500" placeholder="记录你的想法..."></textarea>
        </div>

        <p v-if="error" class="focus-record-error" role="alert">{{ error }}</p>

        <footer class="focus-record-actions">
          <button type="button" class="focus-record-ghost" @click="close">取消</button>
          <button type="button" class="focus-record-primary" :disabled="!canSave || busy" @click="save">{{ busy ? '保存中…' : '保存' }}</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ChevronDown, X } from 'lucide-vue-next'
import FocusTargetPicker from './FocusTargetPicker.vue'
import FocusDateTimeField from './FocusDateTimeField.vue'
import { useTaskFocusStore } from '../../stores/taskFocus.js'
import { message } from '../../services/message.js'

const props = defineProps({
  open: { type: Boolean, default: false }
})
const emit = defineEmits(['close', 'saved'])

const focus = useTaskFocusStore()

const target = ref(null)
const startAt = ref('')
const endAt = ref('')
const typeKind = ref('POMODORO')
const durationMinutes = ref(0)
const note = ref('')
const taskPickerOpen = ref(false)
const typeOpen = ref(false)
const busy = ref(false)
const error = ref('')

// 类型浮层里的草稿，点「确定」才写回 durationMinutes
const draftHours = ref(0)
const draftMinutes = ref(0)
const draftTotal = computed(() => Math.max(0, Number(draftHours.value) || 0) * 60 + Math.max(0, Number(draftMinutes.value) || 0))

const pomodoroMinutes = computed(() => Number(focus.settings?.focusMinutes) || 25)

const typeLabel = computed(() => {
  if (typeKind.value === 'POMODORO') {
    const count = durationMinutes.value > 0 ? Math.max(1, Math.round(durationMinutes.value / pomodoroMinutes.value)) : 0
    return `番茄计时：${count} 个番茄`
  }
  const hours = Math.floor(durationMinutes.value / 60)
  const rest = durationMinutes.value % 60
  return `正计时：${hours} 小时 ${rest} 分钟`
})

function pad(value) {
  return String(value).padStart(2, '0')
}

function toLocalInput(iso) {
  const date = new Date(iso)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const canSave = computed(() => {
  if (!startAt.value || !endAt.value) return false
  return new Date(endAt.value).getTime() > new Date(startAt.value).getTime()
})

const diffMinutes = computed(() => {
  if (!startAt.value || !endAt.value) return 0
  const diff = (new Date(endAt.value).getTime() - new Date(startAt.value).getTime()) / 60000
  return diff > 0 ? Math.round(diff) : 0
})

function reset() {
  target.value = null
  note.value = ''
  typeKind.value = 'POMODORO'
  durationMinutes.value = 0
  taskPickerOpen.value = false
  typeOpen.value = false
  error.value = ''
  const now = new Date()
  const minutes = Number(focus.settings?.focusMinutes) || 25
  endAt.value = now.toISOString()
  startAt.value = new Date(now.getTime() - minutes * 60000).toISOString()
  durationMinutes.value = minutes
}

watch(() => props.open, value => {
  if (value) reset()
})

function close() {
  emit('close')
}

function pickTarget(item) {
  target.value = { kind: item.kind, id: item.id, title: item.title }
  taskPickerOpen.value = false
}

/**
 * 类型与起止时间互相反推：
 * - 只填了开始 → 结束 = 开始 + 时长
 * - 只填了结束 → 开始 = 结束 - 时长
 * - 都填了 → 时长取差值（打开类型浮层时回填）
 * - 都填了之后又改类型 → 以类型为准改结束时间
 */
function applyDuration(minutes) {
  if (minutes <= 0) return
  durationMinutes.value = minutes
  if (startAt.value && endAt.value) {
    endAt.value = new Date(new Date(startAt.value).getTime() + minutes * 60000).toISOString()
    return
  }
  if (startAt.value) {
    endAt.value = new Date(new Date(startAt.value).getTime() + minutes * 60000).toISOString()
    return
  }
  if (endAt.value) {
    startAt.value = new Date(new Date(endAt.value).getTime() - minutes * 60000).toISOString()
  }
}

// 用户改开始时间：有时长就顺推结束，否则若有结束时间就回填时长
watch(startAt, (value, previous) => {
  if (!value || value === previous) return
  if (durationMinutes.value > 0) {
    endAt.value = new Date(new Date(value).getTime() + durationMinutes.value * 60000).toISOString()
    return
  }
  if (endAt.value && new Date(endAt.value) > new Date(value)) {
    durationMinutes.value = diffMinutes.value
  }
})

// 用户改结束时间：有开始就回填时长，否则有时长就倒推开始
watch(endAt, value => {
  if (!value) return
  if (startAt.value) {
    const diff = diffMinutes.value
    if (diff > 0) durationMinutes.value = diff
    return
  }
  if (durationMinutes.value > 0) {
    startAt.value = new Date(new Date(value).getTime() - durationMinutes.value * 60000).toISOString()
  }
})

function switchTypeKind(kind) {
  typeKind.value = kind
}

function openTypeEditor() {
  draftHours.value = Math.floor(durationMinutes.value / 60)
  draftMinutes.value = durationMinutes.value % 60
}

watch(typeOpen, value => {
  if (value) openTypeEditor()
})

function confirmType() {
  applyDuration(draftTotal.value)
  typeOpen.value = false
}

async function save() {
  if (!canSave.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await focus.createRecord({
      ...(target.value && target.value.kind !== 'preset' ? { taskId: target.value.id } : {}),
      startedAt: startAt.value,
      endedAt: endAt.value,
      note: note.value
    })
    message.success('已添加专注记录')
    emit('saved')
    emit('close')
  } catch (cause) {
    error.value = cause?.response?.data?.detail || cause?.message || '保存失败'
  } finally {
    busy.value = false
  }
}

defineExpose({ toLocalInput, reactive })
</script>

<style scoped>
.focus-record-mask { position: fixed; z-index: 130; inset: 0; display: grid; place-items: center; padding: 20px; background: rgb(15 23 42 / 42%); }
.focus-record-dialog { display: flex; width: min(560px, calc(100vw - 32px)); max-height: calc(100dvh - 40px); flex-direction: column; gap: 14px; overflow: auto; padding: 20px 24px 18px; border-radius: 18px; background: var(--card); box-shadow: 0 30px 70px rgb(15 23 42 / 30%); }
.focus-record-dialog > header { position: relative; display: flex; align-items: center; justify-content: center; }
.focus-record-dialog h2 { margin: 0; font-size: 1.06rem; font-weight: 700; }
.focus-record-close { position: absolute; top: -4px; right: -6px; display: grid; width: 28px; height: 28px; place-items: center; border: 0; border-radius: 8px; background: transparent; color: var(--muted); cursor: pointer; }
.focus-record-close:hover { background: var(--paper); color: var(--ink); }
.focus-record-row { display: flex; align-items: flex-start; gap: 14px; }
.focus-record-label { flex: 0 0 84px; padding-top: 10px; color: var(--ink); font-size: 13.5px; font-weight: 600; }
.focus-record-control { position: relative; flex: 1; min-width: 0; }
.focus-record-select { display: flex; width: 100%; min-height: 40px; align-items: center; justify-content: space-between; gap: 8px; padding: 0 12px; border: 1px solid var(--line2); border-radius: 10px; background: var(--card); color: var(--ink); font: inherit; font-size: 13.5px; text-align: left; cursor: pointer; }
.focus-record-select.is-empty { color: var(--muted); }
.focus-record-select:hover { border-color: color-mix(in srgb, var(--accent) 40%, var(--line2)); }
.focus-picker.focus-record-picker { top: calc(100% + 6px); left: 0; width: 100%; max-height: 320px; transform: none; }
.focus-record-row.is-textarea { align-items: flex-start; }
.focus-record-textarea { flex: 1; min-width: 0; min-height: 132px; padding: 10px 12px; border: 1px solid var(--line2); border-radius: 10px; background: var(--card); color: var(--ink); font: inherit; font-size: 13.5px; resize: vertical; }
.focus-record-textarea::placeholder { color: var(--muted); }
.focus-record-error { margin: 0; color: var(--down, #e34d59); font-size: 12px; }
.focus-record-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 2px; }
.focus-record-ghost { min-width: 96px; min-height: 38px; border: 1px solid var(--line2); border-radius: 10px; background: var(--card); color: var(--ink); font: inherit; font-size: 13.5px; cursor: pointer; }
.focus-record-ghost:hover { background: var(--paper); }
.focus-record-primary { min-width: 110px; min-height: 38px; border: 1px solid var(--accent); border-radius: 10px; background: var(--accent); color: var(--accent-contrast, #fff); font: inherit; font-size: 13.5px; cursor: pointer; }
.focus-record-primary:disabled { opacity: .45; cursor: default; }

/* 类型浮层 */
.focus-type-popover { position: absolute; z-index: 25; top: calc(100% + 6px); left: 0; display: grid; width: min(280px, 100%); gap: 10px; padding: 12px; border: 1px solid var(--line2); border-radius: 12px; background: var(--card); box-shadow: 0 20px 44px rgb(15 23 42 / 20%); }
.focus-type-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 3px; padding: 3px; border-radius: 9px; background: var(--paper); }
.focus-type-tabs button { min-height: 28px; border: 0; border-radius: 7px; background: transparent; color: var(--muted); font: inherit; font-size: 12.5px; cursor: pointer; }
.focus-type-tabs button.on { background: var(--card); color: var(--ink); box-shadow: 0 1px 3px rgb(15 23 42 / 12%); }
.focus-type-duration { display: flex; align-items: center; gap: 8px; }
.focus-type-duration input { flex: 0 0 64px; box-sizing: border-box; min-height: 34px; padding: 0 6px; border: 1px solid var(--line2); border-radius: 8px; background: var(--paper); color: var(--ink); font: inherit; font-size: 13px; text-align: center; }
.focus-type-duration span { flex: 0 0 auto; color: var(--muted); font-size: 12px; white-space: nowrap; }
.focus-type-popover footer { display: flex; justify-content: flex-end; gap: 8px; }
.focus-type-ghost { min-width: 68px; min-height: 32px; border: 1px solid var(--line2); border-radius: 8px; background: transparent; color: var(--ink2); font: inherit; font-size: 12.5px; cursor: pointer; }
.focus-type-primary { min-width: 68px; min-height: 32px; border: 1px solid var(--accent); border-radius: 8px; background: var(--accent); color: var(--accent-contrast, #fff); font: inherit; font-size: 12.5px; cursor: pointer; }
.focus-type-primary:disabled { opacity: .45; cursor: default; }
</style>
