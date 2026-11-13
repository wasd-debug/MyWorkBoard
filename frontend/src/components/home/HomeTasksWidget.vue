<template>
  <HomeWidgetFrame title="任务与习惯" kicker="TODAY" :meta="metaLabel" :editing="editing">
    <template #actions>
      <router-link class="tk-more" to="/tasks/today">全部 →</router-link>
    </template>

    <form class="tk-add" @submit.prevent="addTask">
      <input v-model="draft" type="text" maxlength="200" placeholder="加一条今天的任务，回车保存" aria-label="快速添加今天的任务" />
      <button class="hw-btn primary" type="submit" :disabled="!draft.trim() || adding">{{ adding ? '…' : '添加' }}</button>
    </form>
    <p v-if="addError" class="tk-error">{{ addError }}</p>

    <div v-if="overdue.length" class="tk-block">
      <div class="tk-label is-overdue">逾期 {{ overdue.length }}<em v-if="overdue.length > 2">还有 {{ overdue.length - 2 }} 条</em></div>
      <ul class="tk-list">
        <template v-for="task in overdue.slice(0, 2)" :key="task.publicId">
          <li>
            <button class="tk-check" type="button" :aria-label="`完成 ${task.title}`" @click="complete(task)" />
            <button class="tk-title is-editable" type="button" :title="`编辑「${task.title}」`" @click="toggleEdit(task)">{{ task.title }}</button>
            <span class="tk-badge is-overdue">{{ overdueDays(task) }} 天</span>
            <button class="tk-edit" type="button" :aria-label="`编辑 ${task.title}`" :aria-expanded="editingId === task.publicId" @click="toggleEdit(task)"><Pencil :size="12" /></button>
          </li>
          <li v-if="editingId === task.publicId" class="tk-editor"><HomeTaskEditor :task="task" @close="editingId = ''" /></li>
        </template>
      </ul>
    </div>

    <div class="tk-block">
      <div class="tk-label">今天 {{ today.length }}<em v-if="today.length > 3">还有 {{ today.length - 3 }} 条</em></div>
      <ul v-if="today.length" class="tk-list">
        <template v-for="task in today.slice(0, 3)" :key="task.publicId">
          <li>
            <button class="tk-check" type="button" :aria-label="`完成 ${task.title}`" @click="complete(task)" />
            <button class="tk-title is-editable" type="button" :title="`编辑「${task.title}」`" @click="toggleEdit(task)">{{ task.title }}</button>
            <span v-if="task.priority && task.priority !== 'NONE'" class="tk-priority" :class="`is-${task.priority.toLowerCase()}`" :title="priorityLabel(task.priority)" />
            <button class="tk-edit" type="button" :aria-label="`编辑 ${task.title}`" :aria-expanded="editingId === task.publicId" @click="toggleEdit(task)"><Pencil :size="12" /></button>
          </li>
          <li v-if="editingId === task.publicId" class="tk-editor"><HomeTaskEditor :task="task" @close="editingId = ''" /></li>
        </template>
      </ul>
      <p v-else class="tk-empty">今天没有到期任务</p>
    </div>

    <div v-if="habits.length" class="tk-block">
      <div class="tk-label">习惯 {{ habitDone }}/{{ habits.length }}</div>
      <ul class="tk-list habits">
        <li v-for="habit in habits.slice(0, 4)" :key="habit.publicId">
          <button class="hk-check" :class="`is-${stateOf(habit)}`" type="button" :aria-label="`${habit.name} ${stateOf(habit) === 'done' ? '取消打卡' : '打卡'}`" @click="toggleHabit(habit)">
            <Check v-if="stateOf(habit) === 'done'" :size="11" />
          </button>
          <span class="tk-title" :title="habit.name">{{ habit.name }}</span>
          <span class="tk-badge">{{ streakOf(habit) }} 天</span>
        </li>
      </ul>
    </div>

    <template #footer>
      <span>{{ today.length }} 个今日 · {{ overdue.length }} 个逾期 · {{ habits.length }} 个习惯</span>
      <router-link class="tk-more" to="/tasks/habits">习惯 →</router-link>
    </template>
  </HomeWidgetFrame>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Check, Pencil } from 'lucide-vue-next'
import HomeWidgetFrame from './HomeWidgetFrame.vue'
import HomeTaskEditor from './HomeTaskEditor.vue'
import { useTasksStore } from '../../stores/tasks.js'
import { message } from '../../services/message.js'
import { dateKeyOf, overdueDays, taskBuckets } from './homeMetrics.js'
import { currentStreak, dayState, scheduledOn } from '../habits/habitUtils.js'

defineProps({ editing: Boolean })

const tasksStore = useTasksStore()
const draft = ref('')
const adding = ref(false)
const addError = ref('')
const editingId = ref('')
const todayKey = dateKeyOf(new Date())

const buckets = computed(() => taskBuckets(tasksStore.tasks, new Date()))
const today = computed(() => buckets.value.today)
const overdue = computed(() => buckets.value.overdue)

const habits = computed(() => tasksStore.habits.filter(habit => !habit.deleted && scheduledOn(habit, todayKey)))
const habitDone = computed(() => habits.value.filter(habit => stateOf(habit) === 'done').length)
const metaLabel = computed(() => `${today.value.length} 个今日 · ${overdue.value.length} 个逾期`)

function stateOf(habit) {
  return dayState(habit, tasksStore.habitCheckins, todayKey, todayKey)
}
function streakOf(habit) {
  return currentStreak(habit, tasksStore.habitCheckins, todayKey)
}
function priorityLabel(priority) {
  return { HIGH: '高优先级', MEDIUM: '中优先级', LOW: '低优先级' }[priority] || priority
}

/** 只允许同时展开一条，避免卡片里堆满日期面板 */
function toggleEdit(task) {
  editingId.value = editingId.value === task.publicId ? '' : task.publicId
}

// 任务完成后会从「今天」列表消失，编辑器随之收起，避免浮在别的任务下面
watch(() => [today.value.map(item => item.publicId).join(','), overdue.value.map(item => item.publicId).join(',')], () => {
  if (!editingId.value) return
  if (![...today.value, ...overdue.value].some(item => item.publicId === editingId.value)) editingId.value = ''
})

async function complete(task) {
  try {
    await tasksStore.complete(task)
  } catch (error) {
    message.error(error?.response?.data?.detail || error?.message || '完成任务失败')
  }
}

async function toggleHabit(habit) {
  const done = stateOf(habit) === 'done'
  try {
    await tasksStore.checkinHabit(habit, { date: todayKey, status: done ? 'CLEAR' : 'DONE' })
  } catch (error) {
    message.error(error?.response?.data?.detail || error?.message || '打卡失败')
  }
}

async function addTask() {
  const title = draft.value.trim()
  if (!title || adding.value) return
  adding.value = true
  addError.value = ''
  try {
    await tasksStore.create({
      title,
      listId: tasksStore.inbox?.publicId || '',
      priority: 'NONE',
      dueAt: `${todayKey}T00:00`,
      allDay: true
    })
    draft.value = ''
    message.success('任务已添加')
  } catch (error) {
    addError.value = error?.response?.data?.detail || error?.message || '添加失败'
  } finally {
    adding.value = false
  }
}
</script>

<style scoped>
.tk-more { color: var(--accent); font-size: 11px; font-weight: 650; }
.tk-add { display: flex; gap: 6px; margin-bottom: 8px; }
.tk-add input { min-width: 0; height: 32px; flex: 1; padding: 0 10px; border: 1px solid var(--line2); border-radius: 9px; background: var(--paper); color: var(--ink); font-size: 12px; }
.tk-add input:focus { border-color: var(--accent); outline: 0; box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 12%, transparent); }
.tk-add .hw-btn { height: 32px; }
.tk-error { margin: -4px 0 8px; color: var(--down); font-size: 11px; }
.tk-block + .tk-block { margin-top: 8px; padding-top: 7px; border-top: 1px dashed var(--line); }
.tk-label { display: flex; align-items: baseline; gap: 6px; margin-bottom: 4px; color: var(--muted); font-size: 10px; font-weight: 700; letter-spacing: .06em; }
.tk-label em { margin-left: auto; color: var(--muted); font-size: 10px; font-style: normal; font-weight: 500; letter-spacing: 0; }
.tk-label.is-overdue { color: var(--down); }
.tk-list { display: flex; flex-direction: column; gap: 1px; margin: 0; padding: 0; list-style: none; }
.tk-list li { display: flex; min-height: 26px; align-items: center; gap: 8px; }
.tk-check { position: relative; flex: 0 0 15px; width: 15px; height: 15px; min-width: 0; min-height: 0; padding: 0; border: 1.5px solid var(--line2); border-radius: 50%; background: transparent; }
.tk-check:hover { border-color: var(--accent); background: color-mix(in srgb, var(--accent) 12%, transparent); }
.tk-title { min-width: 0; flex: 1; overflow: hidden; color: var(--ink2); font-size: 12.5px; text-overflow: ellipsis; white-space: nowrap; }
.tk-title.is-editable { padding: 0; border: 0; background: transparent; font: inherit; font-size: 12.5px; text-align: left; }
.tk-title.is-editable:hover { color: var(--accent); text-decoration: underline; text-underline-offset: 2px; }
.tk-edit { display: grid; width: 22px; height: 22px; min-width: 0; min-height: 0; flex: 0 0 22px; place-items: center; padding: 0; border: 0; border-radius: 6px; background: transparent; color: var(--muted); opacity: 0; transition: opacity .15s ease; }
.tk-list li:hover .tk-edit, .tk-edit:focus-visible, .tk-edit[aria-expanded="true"] { opacity: 1; }
.tk-edit:hover { background: var(--card); color: var(--accent); }
.tk-editor { display: block; padding: 0; }
.tk-editor:hover .tk-edit { opacity: 1; }
.tk-badge { flex: 0 0 auto; color: var(--muted); font-size: 10px; font-variant-numeric: tabular-nums; }
.tk-badge.is-overdue { color: var(--down); }
.tk-priority { flex: 0 0 6px; width: 6px; height: 6px; border-radius: 50%; }
.tk-priority.is-high { background: var(--priority-high); }
.tk-priority.is-medium { background: var(--priority-medium); }
.tk-priority.is-low { background: var(--priority-low); }
.tk-rest { color: var(--muted); font-size: 10.5px; }
.tk-empty { margin: 2px 0 0; color: var(--muted); font-size: 11px; }
.habits .hk-check { display: grid; width: 16px; height: 16px; min-width: 0; min-height: 0; flex: 0 0 16px; place-items: center; padding: 0; border: 1.5px solid var(--line2); border-radius: 5px; background: transparent; color: var(--paper); }
.habits .hk-check.is-done { border-color: var(--accent); background: var(--accent); }
.habits .hk-check.is-missed { border-color: color-mix(in srgb, var(--down) 46%, var(--line2)); }
.habits .hk-check.is-skip { border-style: dashed; }
@media (max-width: 767px) {
  .tk-list li { min-height: 32px; }
  .tk-check { flex-basis: 20px; width: 20px; height: 20px; }
  .habits .hk-check { flex-basis: 20px; width: 20px; height: 20px; border-radius: 6px; }
  .tk-title { font-size: 13px; }
  /* 触屏没有 hover，编辑入口必须常显 */
  .tk-edit { width: 30px; height: 30px; flex-basis: 30px; opacity: 1; }
}
</style>
