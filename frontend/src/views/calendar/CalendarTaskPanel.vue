<template>
  <aside class="cal-plan" aria-label="安排任务">
    <header>
      <div>
        <b>安排任务</b>
        <small>{{ groups.reduce((sum, group) => sum + group.items.length, 0) }} 项待安排</small>
      </div>
      <button type="button" aria-label="关闭安排任务" @click="$emit('close')"><X :size="15" /></button>
    </header>

    <div class="cal-plan-controls">
      <select :value="groupBy" aria-label="分组方式" @change="$emit('update:groupBy', $event.target.value)">
        <option v-for="mode in PLAN_GROUP_MODES" :key="mode.value" :value="mode.value">{{ mode.label }}</option>
      </select>
      <div class="cal-plan-chips">
        <button type="button" class="cal-plan-chip" :class="{ on: undated }" :aria-pressed="undated" @click="$emit('update:undated', !undated)">无日期</button>
        <button type="button" class="cal-plan-chip" :class="{ on: overdue }" :aria-pressed="overdue" @click="$emit('update:overdue', !overdue)">已过期</button>
      </div>
      <div class="cal-plan-toggles">
        <label><input type="checkbox" :checked="hideNotes" @change="$emit('update:hideNotes', $event.target.checked)" />隐藏笔记</label>
        <label><input type="checkbox" :checked="hideHierarchy" @change="$emit('update:hideHierarchy', $event.target.checked)" />隐藏任务层级</label>
      </div>
    </div>

    <div class="cal-plan-list">
      <section v-for="group in groups" :key="group.key" class="cal-plan-group">
        <header>
          <span v-if="group.color" class="cal-plan-dot" :style="{ background: group.color }"></span>
          <b>{{ group.label }}</b>
          <small>{{ group.items.length }}</small>
        </header>
        <button
          v-for="task in group.items"
          :key="task.publicId"
          type="button"
          class="cal-plan-row"
          :class="{ 'is-child': task.parentId }"
          draggable="true"
          :title="`拖到日历上排期：${task.title}`"
          @dragstart="startDrag(task, $event)"
          @dragend="$emit('drag-end')"
          @click="$emit('schedule', task)"
        >
          <span class="cal-plan-flag" :class="`is-${(task.priority || 'NONE').toLowerCase()}`"><Flag :size="11" /></span>
          <span class="cal-plan-title">{{ task.title }}</span>
          <span v-if="task.openChecklistCount" class="cal-plan-extra">+{{ task.openChecklistCount }}</span>
        </button>
      </section>
      <p v-if="!groups.length" class="cal-plan-empty">没有符合条件的任务。试试打开「已过期」或关闭「隐藏笔记」。</p>
    </div>

    <footer>
      <p>拖动任务到日历上排期，或点任务排到当前选中日期。</p>
    </footer>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { Flag, X } from 'lucide-vue-next'
import { PLAN_GROUP_MODES, groupPlanTasks, planCandidates } from './calendarPlan.js'

const props = defineProps({
  tasks: { type: Array, default: () => [] },
  lists: { type: Array, default: () => [] },
  tags: { type: Array, default: () => [] },
  groupBy: { type: String, default: 'list' },
  undated: { type: Boolean, default: true },
  overdue: { type: Boolean, default: false },
  hideNotes: { type: Boolean, default: false },
  hideHierarchy: { type: Boolean, default: false }
})

const emit = defineEmits([
  'close', 'schedule', 'drag-end',
  'update:groupBy', 'update:undated', 'update:overdue', 'update:hideNotes', 'update:hideHierarchy'
])

const groups = computed(() => {
  const pool = planCandidates(props.tasks, {
    lists: props.lists,
    now: new Date(),
    includeUndated: props.undated,
    includeOverdue: props.overdue,
    hideNotes: props.hideNotes,
    hideHierarchy: props.hideHierarchy
  }).map(task => ({
    ...task,
    openChecklistCount: (task.checklist || []).filter(item => !item.completed).length
  }))
  return groupPlanTasks(pool, { groupBy: props.groupBy, lists: props.lists, tags: props.tags })
})

function startDrag(task, event) {
  event.dataTransfer?.setData('text/plain', task.publicId)
  event.dataTransfer?.setData('application/x-calendar-task', task.publicId)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}
</script>

<style scoped>
.cal-plan {
  display: flex;
  flex: 0 0 280px;
  width: 280px;
  min-height: 0;
  flex-direction: column;
  border-left: 1px solid var(--line);
  background: var(--card);
  color: var(--ink);
}
.cal-plan > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 14px 10px;
  border-bottom: 1px solid var(--line);
}
.cal-plan > header b {
  display: block;
  font-size: 13px;
  font-weight: 650;
}
.cal-plan > header small {
  display: block;
  margin-top: 2px;
  color: var(--muted);
  font-size: 10.5px;
}
.cal-plan > header button {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
}
.cal-plan > header button:hover {
  background: var(--paper);
  color: var(--ink);
}
.cal-plan-controls {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
}
.cal-plan-controls select {
  height: 28px;
  padding: 0 6px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--paper);
  color: var(--ink);
  font-size: 11.5px;
}
.cal-plan-chips {
  display: flex;
  gap: 6px;
}
.cal-plan-chip {
  height: 24px;
  padding: 0 9px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: transparent;
  color: var(--muted);
  font-size: 11px;
}
.cal-plan-chip.on {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent) 10%, transparent);
  color: var(--accent);
  font-weight: 600;
}
.cal-plan-toggles {
  display: flex;
  gap: 12px;
  color: var(--muted);
  font-size: 10.5px;
}
.cal-plan-toggles label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}
.cal-plan-toggles input {
  width: 12px;
  height: 12px;
  accent-color: var(--accent);
}
.cal-plan-list {
  flex: 1 1 auto;
  min-height: 0;
  padding: 6px 8px 40px;
  overflow-y: auto;
}
.cal-plan-group > header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 4px 4px;
  color: var(--ink2);
  font-size: 11px;
  font-weight: 700;
}
.cal-plan-group > header small {
  margin-left: auto;
  color: var(--muted);
  font-size: 9.5px;
  font-weight: 500;
}
.cal-plan-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}
.cal-plan-row {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  padding: 0 6px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--ink);
  font-size: 12px;
  cursor: grab;
  text-align: left;
}
.cal-plan-row:hover {
  background: var(--paper);
}
.cal-plan-row:active {
  cursor: grabbing;
}
.cal-plan-row.is-child {
  padding-left: 18px;
}
.cal-plan-flag {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 12px;
  color: var(--muted);
}
.cal-plan-flag.is-high {
  color: var(--priority-high, #e34d59);
}
.cal-plan-flag.is-medium {
  color: var(--priority-medium, #e69b2d);
}
.cal-plan-flag.is-low {
  color: var(--priority-low, #4d78db);
}
.cal-plan-title {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cal-plan-extra {
  flex: 0 0 auto;
  color: var(--muted);
  font-size: 9.5px;
}
.cal-plan-empty {
  margin: 18px 8px;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.6;
}
.cal-plan > footer {
  padding: 8px 12px 12px;
  border-top: 1px solid var(--line);
}
.cal-plan > footer p {
  margin: 0;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.5;
}
@media (max-width: 1023px) {
  .cal-plan {
    position: absolute;
    z-index: 45;
    top: 0;
    right: 0;
    bottom: 0;
    width: min(280px, 82vw);
    box-shadow: -12px 0 32px rgba(15, 23, 42, .18);
  }
}
</style>
