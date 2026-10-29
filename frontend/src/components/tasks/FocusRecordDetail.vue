<template>
  <Teleport to="body">
    <div
      v-if="open && session"
      class="focus-detail-layer"
      :class="{ 'is-modal': isModal }"
      @click.self="isModal && emit('close')"
    >
      <section
        ref="popEl"
        class="focus-detail-card"
        :class="{ 'is-modal': isModal }"
        :style="isModal ? undefined : popStyle"
        role="dialog"
        aria-label="专注记录详情"
        @click.stop
      >
        <header class="focus-detail-head">
          <span class="focus-detail-icon" aria-hidden="true"><Timer :size="15" /></span>
          <button type="button" class="focus-detail-task" :class="{ 'is-empty': !session.taskTitle }" @click="togglePicker">
            {{ session.taskTitle || '未关联任务' }}
          </button>
          <button
            type="button"
            class="focus-detail-link"
            :class="{ on: pickerOpen }"
            aria-label="关联任务"
            title="关联任务"
            :aria-expanded="pickerOpen"
            @click="togglePicker"
          >
            <Link2 :size="16" />
          </button>
          <button v-if="isModal" type="button" class="focus-detail-close" aria-label="关闭" @click="emit('close')"><X :size="16" /></button>
        </header>

        <FocusTargetPicker v-if="pickerOpen" class="focus-detail-picker" :selected-id="session.taskId || ''" @pick="relink" />

        <div class="focus-detail-time">
          <span class="focus-detail-label">开始</span>
          <FocusDateTimeField v-model="startAt" placeholder="选择开始时间" panel-group="focus-detail-time" panel-name="start" />
        </div>
        <div class="focus-detail-time">
          <span class="focus-detail-label">结束</span>
          <FocusDateTimeField v-model="endAt" placeholder="选择结束时间" panel-group="focus-detail-time" panel-name="end" />
        </div>

        <p class="focus-detail-line"><Clock3 :size="14" />{{ durationLabel }}</p>

        <textarea
          v-model="note"
          class="focus-detail-note"
          maxlength="500"
          placeholder="记录你的想法..."
          aria-label="专注笔记"
        ></textarea>

        <p v-if="error || timeError" class="focus-detail-error" role="alert">{{ error || timeError }}</p>

        <footer class="focus-detail-foot">
          <button type="button" class="focus-detail-save" :disabled="busy || Boolean(timeError)" @click="save">{{ busy ? '保存中…' : '保存' }}</button>
          <button type="button" class="focus-detail-remove" aria-label="删除这条记录" title="删除这条记录" :disabled="busy" @click="remove">
            <Trash2 :size="16" />
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Clock3, Link2, Timer, Trash2, X } from 'lucide-vue-next'
import FocusTargetPicker from './FocusTargetPicker.vue'
import FocusDateTimeField from './FocusDateTimeField.vue'
import { useTaskFocusStore } from '../../stores/taskFocus.js'
import { message } from '../../services/message.js'

const props = defineProps({
  open: { type: Boolean, default: false },
  session: { type: Object, default: null },
  /** 记录行元素，用来把浮层贴在那一条旁边（popover 模式） */
  anchorEl: { type: Object, default: null },
  /** popover：贴在记录行旁边的浮层；modal：带遮罩的居中弹窗（批量管理里用） */
  variant: { type: String, default: 'popover' }
})
const emit = defineEmits(['close', 'saved', 'deleted'])

const focus = useTaskFocusStore()
const popEl = ref(null)
const popStyle = ref({})
const note = ref('')
const startAt = ref('')
const endAt = ref('')
const pickerOpen = ref(false)
const busy = ref(false)
const error = ref('')

const isModal = computed(() => props.variant === 'modal')

function syncDraft() {
  note.value = props.session?.note || ''
  startAt.value = props.session?.startedAt || ''
  endAt.value = props.session?.endedAt || ''
  pickerOpen.value = false
  error.value = ''
}

watch(() => props.session?.publicId, syncDraft)

watch(() => props.open, async value => {
  if (!value) return
  syncDraft()
  if (isModal.value) return
  await nextTick()
  place()
})

function pad(value) {
  return String(value).padStart(2, '0')
}

const timeError = computed(() => {
  if (!startAt.value || !endAt.value) return '请填写完整的起止时间'
  const start = new Date(startAt.value).getTime()
  const end = new Date(endAt.value).getTime()
  if (Number.isNaN(start) || Number.isNaN(end)) return '时间格式不正确'
  if (end <= start) return '结束时间需要晚于开始时间'
  return ''
})

const durationLabel = computed(() => {
  if (timeError.value) {
    const value = Number(props.session?.actualMinutes) || 0
    return formatDuration(value)
  }
  const minutes = Math.round((new Date(endAt.value).getTime() - new Date(startAt.value).getTime()) / 60000)
  return formatDuration(minutes)
})

function formatDuration(total) {
  const value = Math.max(0, Math.round(Number(total) || 0))
  const hours = Math.floor(value / 60)
  const minutes = value % 60
  if (!hours) return `${minutes} 分钟`
  return minutes ? `${hours} 小时 ${minutes} 分钟` : `${hours} 小时`
}

/** 贴在记录行左侧；左边放不下就放右侧，垂直方向夹在视口内 */
function place() {
  const pop = popEl.value
  const anchor = props.anchorEl
  if (!pop || !anchor) return
  const rect = anchor.getBoundingClientRect()
  const width = pop.offsetWidth
  const height = pop.offsetHeight
  const margin = 12
  const gap = 14
  let left = rect.left - gap - width
  if (left < margin) left = rect.right + gap
  left = Math.min(Math.max(margin, left), Math.max(margin, window.innerWidth - margin - width))
  let top = rect.top - 10
  top = Math.min(Math.max(margin, top), Math.max(margin, window.innerHeight - margin - height))
  popStyle.value = { top: `${Math.round(top)}px`, left: `${Math.round(left)}px` }
}

function onDocPointerDown(event) {
  if (!props.open || isModal.value) return
  const target = event.target
  if (popEl.value?.contains(target)) return
  // 日期浮层被 Teleport 到 body，不在卡片 DOM 里，点它不能算「点外面」
  if (target?.closest?.('.focus-dt-popover')) return
  emit('close')
}

function onViewportChange() {
  if (props.open && !isModal.value) place()
}

function onKeydown(event) {
  if (event.key === 'Escape' && props.open) {
    event.stopPropagation()
    emit('close')
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

async function togglePicker() {
  pickerOpen.value = !pickerOpen.value
  if (isModal.value) return
  await nextTick()
  place()
}

function currentPayload() {
  const payload = { note: note.value }
  if (!timeError.value) {
    payload.startedAt = startAt.value
    payload.endedAt = endAt.value
  }
  return payload
}

async function relink(item) {
  if (!props.session) return
  pickerOpen.value = false
  const taskId = item.kind === 'preset' ? null : item.id
  try {
    const updated = await focus.updateRecord(props.session, { taskId, ...currentPayload() })
    emit('saved', updated)
    if (!isModal.value) {
      await nextTick()
      place()
    }
  } catch (cause) {
    error.value = cause?.response?.data?.detail || '关联任务失败'
  }
}

async function save() {
  if (!props.session || busy.value || timeError.value) return
  busy.value = true
  error.value = ''
  try {
    const updated = await focus.updateRecord(props.session, currentPayload())
    message.success('已保存')
    emit('saved', updated)
    emit('close')
  } catch (cause) {
    error.value = cause?.response?.data?.detail || '保存失败'
  } finally {
    busy.value = false
  }
}

async function remove() {
  if (!props.session || busy.value) return
  if (!window.confirm('删除这条专注记录？')) return
  busy.value = true
  error.value = ''
  try {
    await focus.remove(props.session)
    message.success('已删除')
    emit('deleted')
    emit('close')
  } catch (cause) {
    error.value = cause?.response?.data?.detail || '删除失败'
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
/* popover 模式下这层不参与布局，卡片自己 fixed 贴到记录行旁边 */
.focus-detail-layer { display: contents; }
.focus-detail-layer.is-modal { position: fixed; z-index: 140; inset: 0; display: grid; place-items: center; padding: 20px; background: rgb(15 23 42 / 42%); }

.focus-detail-card {
  position: fixed; z-index: 200;
  display: grid; gap: 10px;
  width: min(420px, calc(100vw - 24px));
  box-sizing: border-box; padding: 18px 20px 14px;
  border-radius: 18px; background: var(--card);
  box-shadow: 0 28px 64px rgb(15 23 42 / 26%), 0 2px 6px rgb(15 23 42 / 8%);
}
.focus-detail-card.is-modal { position: relative; z-index: auto; width: min(460px, calc(100vw - 32px)); max-height: calc(100dvh - 40px); overflow: auto; box-shadow: 0 30px 70px rgb(15 23 42 / 30%); }

.focus-detail-head { position: relative; display: flex; align-items: center; gap: 8px; }
.focus-detail-icon { display: grid; width: 26px; height: 26px; flex: 0 0 auto; place-items: center; border-radius: 50%; background: color-mix(in srgb, var(--accent) 16%, transparent); color: var(--accent); }
.focus-detail-task { flex: 1; min-width: 0; overflow: hidden; padding: 2px 4px; border: 0; border-radius: 8px; background: transparent; color: var(--ink); font: inherit; font-size: 15px; font-weight: 600; text-align: left; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; }
.focus-detail-task:hover { background: var(--paper); }
.focus-detail-task.is-empty { color: var(--muted); font-weight: 500; }
.focus-detail-link, .focus-detail-close { display: grid; width: 28px; height: 28px; flex: 0 0 auto; place-items: center; border: 0; border-radius: 8px; background: transparent; color: var(--muted); cursor: pointer; }
.focus-detail-link:hover, .focus-detail-link.on, .focus-detail-close:hover { background: var(--paper); color: var(--accent); }
.focus-detail-close:hover { color: var(--ink); }

/* 选择器铺在标题与底栏之间，正好落在卡片里，不会顶出卡片 */
.focus-picker.focus-detail-picker { top: 44px; right: 10px; bottom: 62px; left: 10px; width: auto; max-height: none; padding: 10px 8px 4px; border-radius: 14px; transform: none; }

.focus-detail-time { display: flex; align-items: center; gap: 10px; }
.focus-detail-label { flex: 0 0 34px; color: var(--muted); font-size: 12.5px; }
.focus-detail-line { display: flex; align-items: center; gap: 8px; margin: 0; color: var(--muted); font-size: 13px; font-variant-numeric: tabular-nums; }
.focus-detail-line svg { flex: 0 0 auto; }

.focus-detail-note { min-height: 118px; padding: 12px 14px; border: 0; border-radius: 12px; background: var(--paper); color: var(--ink); font: inherit; font-size: 13.5px; line-height: 1.6; resize: vertical; }
.focus-detail-note::placeholder { color: var(--muted); }
.focus-detail-note:focus { outline: 2px solid color-mix(in srgb, var(--accent) 34%, transparent); outline-offset: -1px; }

.focus-detail-error { margin: 0; color: var(--down, #e34d59); font-size: 12px; }

.focus-detail-foot { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 2px; padding-top: 12px; border-top: 1px solid var(--line); }
.focus-detail-save { min-width: 88px; min-height: 34px; padding: 0 18px; border: 1px solid var(--accent); border-radius: 9px; background: var(--accent); color: var(--accent-contrast, #fff); font: inherit; font-size: 13px; cursor: pointer; }
.focus-detail-save:disabled { opacity: .5; cursor: default; }
.focus-detail-remove { display: grid; width: 32px; height: 32px; place-items: center; border: 0; border-radius: 8px; background: transparent; color: var(--muted); cursor: pointer; }
.focus-detail-remove:hover { background: color-mix(in srgb, var(--down, #e34d59) 10%, transparent); color: var(--down, #e34d59); }
</style>
