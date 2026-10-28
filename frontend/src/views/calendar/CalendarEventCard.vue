<template>
  <section class="cal-card" :style="style" role="dialog" :aria-label="heading" @click.stop>
    <header>
      <span class="cal-card-dot" :style="{ background: event.color }"></span>
      <div class="cal-card-title">
        <b>{{ isTask && task ? task.title : event.title }}</b>
        <small>{{ subtitle }}</small>
      </div>
      <button type="button" class="cal-card-close" aria-label="关闭详情" @click="$emit('close')"><X :size="14" /></button>
    </header>

    <div v-if="isTask && task" class="cal-card-body">
      <button
        type="button"
        class="cal-card-check"
        :class="{ 'is-done': task.status === 'COMPLETED' }"
        :aria-pressed="task.status === 'COMPLETED'"
        @click="$emit(task.status === 'COMPLETED' ? 'reopen' : 'complete')"
      >
        <Check v-if="task.status === 'COMPLETED'" :size="12" />
        <span>{{ task.status === 'COMPLETED' ? '已完成，点击恢复' : '标记完成' }}</span>
      </button>

      <div class="cal-card-meta">
        <span><CalendarDays :size="12" />{{ scheduleText }}</span>
        <span v-if="listName"><FolderClosed :size="12" />{{ listName }}</span>
        <span v-if="task.openChecklistCount"><ListChecks :size="12" />还有 {{ task.openChecklistCount }} 项检查</span>
      </div>

      <div class="cal-card-row">
        <span class="cal-card-label">优先级</span>
        <div class="cal-card-flags">
          <button
            v-for="item in priorities"
            :key="item.value"
            type="button"
            :class="['cal-flag', `is-${item.value.toLowerCase()}`, { on: (task.priority || 'NONE') === item.value }]"
            :aria-pressed="(task.priority || 'NONE') === item.value"
            :title="item.label"
            @click="$emit('priority', item.value)"
          ><Flag :size="12" /></button>
        </div>
      </div>

      <div class="cal-card-row">
        <span class="cal-card-label">清单</span>
        <select :value="task.listId" aria-label="移动到清单" @change="$emit('move', $event.target.value)">
          <option v-for="list in lists" :key="list.publicId" :value="list.publicId">{{ list.name }}</option>
        </select>
      </div>
    </div>

    <div v-else-if="event.kind === 'habit' && habit" class="cal-card-body">
      <button
        type="button"
        class="cal-card-check"
        :class="{ 'is-done': habitDone }"
        :aria-pressed="habitDone"
        @click="$emit(habitDone ? 'undo-checkin' : 'checkin')"
      >
        <Check v-if="habitDone" :size="12" />
        <span>{{ habitDone ? '今天已完成，点击撤销' : '我做到了' }}</span>
      </button>
      <p class="cal-card-hint">{{ habitDone ? '已计入今天的打卡与连续天数。' : '打卡后计入连续天数与完成率。' }}<template v-if="habitStreak > 0"> 当前连续 {{ habitStreak }} 天。</template></p>
    </div>

    <div v-else-if="event.kind === 'countdown' && countdown" class="cal-card-body">
      <p class="cal-card-hint">{{ countdown.repeatYearly ? '每年重复' : '仅此一次' }} · 目标日期 {{ countdown.targetDate }}</p>
    </div>

    <div v-else-if="event.kind === 'focus'" class="cal-card-body">
      <p class="cal-card-hint">专注 {{ event.startLabel }}<template v-if="event.endLabel"> – {{ event.endLabel }}</template> · {{ event.done ? '已完成' : '已中断' }}</p>
    </div>

    <footer>
      <button v-if="isTask && task" type="button" class="cal-card-btn" @click="$emit('focus')"><Timer :size="13" />专注</button>
      <button v-if="isTask && task" type="button" class="cal-card-btn" @click="$emit('open-detail')"><ExternalLink :size="13" />详情</button>
      <button v-if="event.kind === 'habit'" type="button" class="cal-card-btn" @click="$emit('open-detail')"><ExternalLink :size="13" />习惯页</button>
      <button v-if="event.kind === 'countdown'" type="button" class="cal-card-btn" @click="$emit('open-detail')"><ExternalLink :size="13" />倒数日</button>
      <button v-if="event.kind === 'focus'" type="button" class="cal-card-btn" @click="$emit('open-detail')"><ExternalLink :size="13" />专注页</button>
      <button v-if="isTask && task" type="button" class="cal-card-btn is-danger" @click="$emit('delete')"><Trash2 :size="13" />删除</button>
    </footer>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { CalendarDays, Check, ExternalLink, Flag, FolderClosed, ListChecks, Timer, Trash2, X } from 'lucide-vue-next'

const props = defineProps({
  event: { type: Object, required: true },
  task: { type: Object, default: null },
  habit: { type: Object, default: null },
  countdown: { type: Object, default: null },
  lists: { type: Array, default: () => [] },
  habitStreak: { type: Number, default: 0 },
  style: { type: Object, default: () => ({}) }
})

defineEmits(['close', 'complete', 'reopen', 'priority', 'move', 'delete', 'focus', 'open-detail', 'checkin', 'undo-checkin'])

const priorities = [
  { value: 'HIGH', label: '高优先级' },
  { value: 'MEDIUM', label: '中优先级' },
  { value: 'LOW', label: '低优先级' },
  { value: 'NONE', label: '无优先级' }
]

const isTask = computed(() => props.event?.kind === 'task')
const habitDone = computed(() => Boolean(props.event?.done))
const heading = computed(() => props.event?.title || '详情')
const listName = computed(() => props.lists.find(item => item.publicId === props.task?.listId)?.name || '')
const subtitle = computed(() => {
  if (isTask.value) {
    if (props.event.startLabel && props.event.endLabel) return `${props.event.startLabel} – ${props.event.endLabel}`
    return props.event.allDay ? '全天' : props.event.startLabel || ''
  }
  if (props.event.kind === 'habit') return '习惯'
  if (props.event.kind === 'countdown') return '倒数日'
  if (props.event.kind === 'focus') return '专注记录'
  return ''
})

function dayLabel(key) {
  const [year, month, day] = String(key || '').split('-').map(Number)
  if (!year) return key || ''
  const date = new Date(year, month - 1, day)
  const weekday = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()]
  return `${month}月${day}日 ${weekday}`
}

const scheduleText = computed(() => {
  const event = props.event
  if (event.allDay) {
    const span = event.startKey === event.endKey ? dayLabel(event.startKey) : `${dayLabel(event.startKey)} – ${dayLabel(event.endKey)}`
    return `${span} · 全天`
  }
  return `${dayLabel(event.startKey)} ${event.startLabel || ''}${event.endLabel ? ` – ${event.endLabel}` : ''}`
})
</script>

<style scoped>
.cal-card {
  position: fixed;
  z-index: 58;
  display: flex;
  width: 296px;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--line2);
  border-radius: 10px;
  background: var(--card);
  box-shadow: 0 18px 44px rgba(15, 23, 42, .22);
}
.cal-card > header {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 11px 12px 9px;
  border-bottom: 1px solid var(--line);
}
.cal-card-dot {
  flex: 0 0 auto;
  width: 9px;
  height: 9px;
  margin-top: 4px;
  border-radius: 50%;
}
.cal-card-title {
  min-width: 0;
  flex: 1;
}
.cal-card-title b {
  display: block;
  overflow: hidden;
  color: var(--ink);
  font-size: 13px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cal-card-title small {
  display: block;
  margin-top: 2px;
  color: var(--muted);
  font-size: 10.5px;
}
.cal-card-close {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
}
.cal-card-close:hover {
  background: var(--paper);
  color: var(--ink);
}
.cal-card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
}
.cal-card-check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--line2);
  border-radius: 7px;
  background: transparent;
  color: var(--ink2);
  font-size: 12px;
}
.cal-card-check:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.cal-card-check.is-done {
  border-color: color-mix(in srgb, var(--accent) 40%, var(--line));
  background: color-mix(in srgb, var(--accent) 8%, transparent);
  color: var(--accent);
}
.cal-card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  color: var(--muted);
  font-size: 11px;
}
.cal-card-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.cal-card-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.cal-card-label {
  color: var(--muted);
  font-size: 11px;
}
.cal-card-flags {
  display: inline-flex;
  gap: 2px;
}
.cal-flag {
  display: grid;
  place-items: center;
  width: 26px;
  height: 24px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
}
.cal-flag:hover {
  background: var(--paper);
}
.cal-flag.on.is-high {
  background: color-mix(in srgb, var(--priority-high, #e34d59) 14%, transparent);
  color: var(--priority-high, #e34d59);
}
.cal-flag.on.is-medium {
  background: color-mix(in srgb, var(--priority-medium, #e69b2d) 14%, transparent);
  color: var(--priority-medium, #e69b2d);
}
.cal-flag.on.is-low {
  background: color-mix(in srgb, var(--priority-low, #4d78db) 14%, transparent);
  color: var(--priority-low, #4d78db);
}
.cal-flag.on.is-none {
  background: var(--paper);
  color: var(--ink2);
}
.cal-card-row select {
  max-width: 168px;
  height: 27px;
  padding: 0 6px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--paper);
  color: var(--ink);
  font-size: 11.5px;
}
.cal-card-hint {
  margin: 0;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.5;
}
.cal-card > footer {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 9px 12px 11px;
  border-top: 1px solid var(--line);
}
.cal-card-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: transparent;
  color: var(--ink2);
  font-size: 11.5px;
}
.cal-card-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.cal-card-btn.is-danger:hover {
  border-color: var(--down);
  color: var(--down);
}
</style>
