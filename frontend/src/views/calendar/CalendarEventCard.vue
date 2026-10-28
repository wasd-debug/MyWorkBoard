<template>
  <CalendarTaskCard
    v-if="event.kind === 'task' && task"
    :task="task"
    :lists="lists"
    :tags="tags"
    :lunar="lunar"
    :focus-minutes="focusMinutes"
    :style="style"
    @close="$emit('close')"
    @complete="$emit('complete')"
    @reopen="$emit('reopen')"
    @update="$emit('update', $event)"
    @delete="$emit('delete')"
    @focus="$emit('focus')"
    @open-detail="$emit('open-detail')"
  />

  <section v-else-if="event.kind === 'habit' && habit" class="cal-pop is-habit" :style="style" role="dialog" aria-label="习惯" @click.stop>
    <button type="button" class="cal-pop-close" aria-label="关闭" @click="$emit('close')"><X :size="14" /></button>
    <div class="cal-habit-head">
      <span class="cal-habit-icon" :style="{ background: habit.color || 'var(--accent)' }"><TaskOrganizationIcon :value="habit.icon || '✓'" :size="18" /></span>
      <b>{{ habit.name }}</b>
    </div>
    <button type="button" class="cal-habit-btn" :class="{ 'is-ghost': habitDone }" @click="$emit(habitDone ? 'undo-checkin' : 'checkin')">
      {{ habitDone ? '重置' : '我做到了' }}
    </button>
  </section>

  <section v-else-if="event.kind === 'focus' && session" class="cal-pop is-focus" :style="style" role="dialog" aria-label="专注记录" @click.stop>
    <header class="cal-focus-head">
      <span class="cal-focus-badge" :class="{ done: session.status === 'COMPLETED' }"><Check :size="11" /></span>
      <b>{{ session.taskTitle || '未关联任务' }}</b>
      <div class="cal-focus-link">
        <button type="button" aria-label="修改关联任务" title="修改关联任务" @click.stop="taskMenu = !taskMenu"><Link2 :size="14" /></button>
        <div v-if="taskMenu" class="cal-focus-menu" @click.stop>
          <input v-model="taskQuery" class="cal-focus-search" placeholder="搜索任务…" aria-label="搜索任务" />
          <button v-for="item in taskChoices" :key="item.publicId" type="button" @click="relinkTask(item.publicId)">{{ item.title }}</button>
          <button v-if="session.taskId" type="button" class="is-clear" @click="relinkTask('')">解除关联</button>
          <p v-if="!taskChoices.length">没有匹配的任务</p>
        </div>
      </div>
    </header>
    <p class="cal-focus-row"><Clock :size="13" /><span>{{ spanText }}</span></p>
    <p class="cal-focus-row"><Timer :size="13" /><span>{{ durationText }}</span></p>
    <textarea v-model="noteDraft" class="cal-focus-note" placeholder="记录你的想法…" aria-label="专注想法" @blur="saveNote"></textarea>
    <footer class="cal-focus-foot">
      <button v-if="session.status !== 'RUNNING'" type="button" class="is-danger" title="删除专注记录" aria-label="删除专注记录" @click="$emit('delete-focus')"><Trash2 :size="14" /></button>
    </footer>
  </section>

  <section v-else-if="event.kind === 'countdown' && countdown" class="cal-pop is-countdown" :style="style" role="dialog" aria-label="倒数日" @click.stop>
    <button type="button" class="cal-pop-close" aria-label="关闭" @click="$emit('close')"><X :size="14" /></button>
    <div class="cal-habit-head">
      <span class="cal-habit-icon" :style="{ background: event.color }"><Hourglass :size="16" /></span>
      <b>{{ countdown.title }}</b>
    </div>
    <p class="cal-pop-hint">{{ countdown.repeatYearly ? '每年重复' : '仅此一次' }} · {{ countdown.targetDate }}</p>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Check, Clock, Hourglass, Link2, Timer, Trash2, X } from 'lucide-vue-next'
import CalendarTaskCard from './CalendarTaskCard.vue'
import TaskOrganizationIcon from '../../components/tasks/TaskOrganizationIcon.vue'

const props = defineProps({
  event: { type: Object, required: true },
  task: { type: Object, default: null },
  habit: { type: Object, default: null },
  countdown: { type: Object, default: null },
  session: { type: Object, default: null },
  lists: { type: Array, default: () => [] },
  tags: { type: Array, default: () => [] },
  tasks: { type: Array, default: () => [] },
  lunar: { type: Object, default: () => ({}) },
  focusMinutes: { type: Number, default: 0 },
  style: { type: Object, default: () => ({}) }
})

const emit = defineEmits([
  'close', 'complete', 'reopen', 'update', 'delete', 'focus', 'open-detail',
  'checkin', 'undo-checkin', 'update-focus', 'delete-focus'
])

const noteDraft = ref('')
const taskMenu = ref(false)
const taskQuery = ref('')

watch(() => props.session?.publicId, () => {
  noteDraft.value = props.session?.note || ''
  taskMenu.value = false
  taskQuery.value = ''
}, { immediate: true })

const habitDone = computed(() => Boolean(props.event?.done))

const spanText = computed(() => {
  const session = props.session
  if (!session) return ''
  const start = new Date(session.startedAt)
  const end = session.endedAt ? new Date(session.endedAt) : null
  const format = date => `${date.getMonth() + 1}月${date.getDate()}日 ${pad(date.getHours())}:${pad(date.getMinutes())}`
  return end ? `${format(start)} - ${format(end)}` : `${format(start)} - 进行中`
})

const durationText = computed(() => {
  const minutes = Number(props.session?.actualMinutes) || Number(props.session?.plannedMinutes) || 0
  if (!minutes) return '0m'
  if (minutes < 60) return `${minutes}m`
  const hours = minutes / 60
  return `${Math.round(hours * 10) / 10}h`
})

const taskChoices = computed(() => {
  const query = taskQuery.value.trim()
  return props.tasks
    .filter(item => !item.deleted && item.itemKind !== 'NOTE')
    .filter(item => !query || String(item.title || '').includes(query))
    .slice(0, 30)
})

function pad(value) {
  return String(value).padStart(2, '0')
}

function saveNote() {
  const next = noteDraft.value
  if (next === (props.session?.note || '')) return
  emit('update-focus', { note: next })
}

function relinkTask(taskId) {
  taskMenu.value = false
  emit('update-focus', { taskId })
}
</script>

<style scoped>
.cal-pop {
  position: fixed;
  z-index: 58;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--line2);
  border-radius: 12px;
  background: var(--card);
  box-shadow: 0 20px 48px rgb(15 23 42 / 24%);
  color: var(--ink);
}
.cal-pop.is-habit {
  width: 268px;
}
.cal-pop.is-focus {
  width: 316px;
}
.cal-pop.is-countdown {
  width: 262px;
}
.cal-pop-close {
  position: absolute;
  top: 8px;
  right: 8px;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}
.cal-pop-close:hover {
  background: var(--paper);
  color: var(--ink);
}
.cal-habit-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-right: 22px;
}
.cal-habit-icon {
  display: grid;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  font-size: 18px;
  line-height: 1;
}
.cal-habit-head b {
  font-size: 17px;
  font-weight: 650;
}
.cal-habit-btn {
  width: 100%;
  min-height: 36px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: var(--accent);
  color: var(--accent-contrast, #fff);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.cal-habit-btn.is-ghost {
  border-color: var(--line2);
  background: transparent;
  color: var(--ink2);
}
.cal-habit-btn.is-ghost:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.cal-focus-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-right: 4px;
}
.cal-focus-badge {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--accent);
  color: var(--accent-contrast, #fff);
}
.cal-focus-badge:not(.done) {
  background: color-mix(in srgb, var(--accent) 22%, transparent);
  color: var(--accent);
}
.cal-focus-head b {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  font-size: 14px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cal-focus-link {
  position: relative;
  flex: 0 0 auto;
}
.cal-focus-link > button {
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
.cal-focus-link > button:hover {
  background: var(--paper);
  color: var(--ink);
}
.cal-focus-menu {
  position: absolute;
  top: 28px;
  right: 0;
  z-index: 4;
  display: flex;
  width: 208px;
  max-height: 232px;
  flex-direction: column;
  gap: 1px;
  overflow: auto;
  padding: 6px;
  border: 1px solid var(--line2);
  border-radius: 8px;
  background: var(--card);
  box-shadow: 0 14px 32px rgb(15 23 42 / 20%);
}
.cal-focus-search {
  min-height: 27px;
  margin-bottom: 3px;
  padding: 0 7px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: 12px;
}
.cal-focus-menu button {
  min-height: 26px;
  padding: 0 7px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--ink2);
  font-size: 12px;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cal-focus-menu button:hover {
  background: var(--paper);
  color: var(--ink);
}
.cal-focus-menu button.is-clear {
  color: var(--down, #e34d59);
}
.cal-focus-menu p {
  margin: 6px 7px;
  color: var(--muted);
  font-size: 11px;
}
.cal-focus-row {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0;
  color: var(--ink2);
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
}
.cal-focus-row svg {
  color: var(--muted);
}
.cal-focus-note {
  width: 100%;
  min-height: 84px;
  padding: 9px 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: var(--paper);
  color: var(--ink);
  font: inherit;
  font-size: 12.5px;
  line-height: 1.55;
  resize: vertical;
}
.cal-focus-note:focus {
  outline: none;
  border-color: color-mix(in srgb, var(--accent) 45%, var(--line));
  background: var(--card);
}
.cal-focus-foot {
  display: flex;
  justify-content: flex-end;
}
.cal-focus-foot button {
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
.cal-focus-foot button.is-danger:hover {
  color: var(--down, #e34d59);
}
.cal-pop-hint {
  margin: 0;
  color: var(--muted);
  font-size: 11.5px;
}
</style>
