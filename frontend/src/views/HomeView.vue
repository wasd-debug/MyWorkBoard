<template>
  <main class="home-canvas">
    <header class="home-head">
      <div class="home-head-copy">
        <p class="home-eyebrow">PERSONAL DASHBOARD</p>
        <h1>{{ greeting }}，{{ displayName }}</h1>
        <p class="home-sub">{{ dateLabel }} · {{ headline }}</p>
      </div>
      <div class="home-head-actions">
        <button class="home-edit" type="button" :aria-pressed="editing" @click="toggleEditing">
          <SlidersHorizontal :size="14" />{{ editing ? '完成' : '自定义' }}
        </button>
      </div>
    </header>

    <div v-if="editing" class="home-edit-bar">
      <span>拖动卡片调整顺序，点右上角 × 收起不需要的卡片。</span>
      <button type="button" @click="resetLayout">恢复默认布局</button>
    </div>

    <div v-if="editing && hiddenWidgets.length" class="home-hidden-bar">
      <span>已收起：</span>
      <button v-for="widget in hiddenWidgets" :key="widget.key" type="button" @click="restore(widget.key)">＋ {{ titleOf(widget.key) }}</button>
    </div>

    <div class="home-grid">
      <div
        v-for="widget in visibleLayout"
        :key="widget.key"
        class="home-cell"
        :class="[`span-${widgetSpan(widget.key)}`, `hw-key-${widget.key}`, { 'is-editing': editing, 'is-dragging': dragKey === widget.key, 'is-drop-target': dropKey === widget.key && dragKey !== widget.key }]"
        :draggable="editing"
        @dragstart="onDragStart(widget.key, $event)"
        @dragend="onDragEnd"
        @dragover.prevent="onDragOver(widget.key)"
        @dragleave="onDragLeave(widget.key)"
        @drop.prevent="onDrop(widget.key)"
      >
        <button v-if="editing" class="home-cell-hide" type="button" :aria-label="`收起${titleOf(widget.key)}`" title="收起这张卡片" @click.stop="hide(widget.key)">×</button>
        <component :is="widgetMap[widget.key]" :editing="editing" />
      </div>
    </div>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { SlidersHorizontal } from 'lucide-vue-next'
import HomeTasksWidget from '../components/home/HomeTasksWidget.vue'
import HomeCalendarWidget from '../components/home/HomeCalendarWidget.vue'
import HomeLedgerWidget from '../components/home/HomeLedgerWidget.vue'
import HomeCashflowWidget from '../components/home/HomeCashflowWidget.vue'
import HomeBudgetWidget from '../components/home/HomeBudgetWidget.vue'
import HomeCategoryWidget from '../components/home/HomeCategoryWidget.vue'
import HomeWorktimeWidget from '../components/home/HomeWorktimeWidget.vue'
import HomeAiWidget from '../components/home/HomeAiWidget.vue'
import {
  defaultLayout, HOME_WIDGETS, layoutStorageKey, moveWidget, normalizeLayout, readLayout,
  toggleWidget, visibleWidgets, widgetSpan, writeLayout
} from '../components/home/homeLayout.js'
import { dateKeyOf, monthKeyOf, periodSummary, taskBuckets } from '../components/home/homeMetrics.js'
import { scheduledOn, dayState } from '../components/habits/habitUtils.js'
import { useAppStore } from '../stores/app.js'
import { useTasksStore } from '../stores/tasks.js'
import { useLedgerStore } from '../stores/ledger.js'
import { useWorktimeStore } from '../stores/worktime.js'

const widgetMap = {
  tasks: HomeTasksWidget,
  calendar: HomeCalendarWidget,
  ledger: HomeLedgerWidget,
  cashflow: HomeCashflowWidget,
  budget: HomeBudgetWidget,
  category: HomeCategoryWidget,
  worktime: HomeWorktimeWidget,
  ai: HomeAiWidget
}

const store = useAppStore()
const tasksStore = useTasksStore()
const ledgerStore = useLedgerStore()
const worktimeStore = useWorktimeStore()

const editing = ref(false)
const layout = ref(readLayout(layoutStorageKey(store.accountScope)))
const dragKey = ref('')
const dropKey = ref('')

const visibleLayout = computed(() => visibleWidgets(layout.value))
const hiddenWidgets = computed(() => normalizeLayout(layout.value).filter(item => item.hidden))
const displayName = computed(() => store.authUser?.nickname || store.authUser?.username || '你')

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 13) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const dateLabel = computed(() => {
  const now = new Date()
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return `${now.getFullYear()} 年 ${now.getMonth() + 1} 月 ${now.getDate()} 日 ${weekdays[now.getDay()]}`
})

const headline = computed(() => {
  const todayKey = dateKeyOf(new Date())
  const buckets = taskBuckets(tasksStore.tasks, new Date())
  const habits = tasksStore.habits.filter(habit => !habit.deleted && scheduledOn(habit, todayKey))
  const habitLeft = habits.filter(habit => !['done', 'skip', 'none'].includes(dayState(habit, tasksStore.habitCheckins, todayKey, todayKey))).length
  const monthKey = monthKeyOf(new Date())
  const spent = periodSummary(ledgerStore.transactions, `${monthKey}-01`, `${monthKey}-31`).expense
  const parts = []
  parts.push(buckets.today.length ? `${buckets.today.length} 个任务到期` : '今天没有到期任务')
  if (buckets.overdue.length) parts.push(`${buckets.overdue.length} 个逾期`)
  if (habits.length) parts.push(`习惯还差 ${habitLeft} 个`)
  if (spent > 0) parts.push(`本月已支出 ¥${Math.round(spent).toLocaleString('zh-CN')}`)
  return parts.join(' · ')
})

function titleOf(key) {
  return HOME_WIDGETS.find(item => item.key === key)?.title || key
}

function persist() {
  writeLayout(layoutStorageKey(store.accountScope), layout.value)
}

function toggleEditing() {
  editing.value = !editing.value
  dragKey.value = ''
  dropKey.value = ''
}

function hide(key) {
  layout.value = toggleWidget(layout.value, key)
  persist()
}

function restore(key) {
  layout.value = toggleWidget(layout.value, key)
  persist()
}

function resetLayout() {
  layout.value = defaultLayout()
  persist()
}

function onDragStart(key, event) {
  if (!editing.value) return
  dragKey.value = key
  event.dataTransfer?.setData('text/plain', key)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

function onDragOver(key) {
  if (!editing.value || !dragKey.value) return
  dropKey.value = key
}

function onDragLeave(key) {
  if (dropKey.value === key) dropKey.value = ''
}

function onDragEnd() {
  dragKey.value = ''
  dropKey.value = ''
}

function onDrop(key) {
  if (!editing.value) return
  const from = dragKey.value
  dragKey.value = ''
  dropKey.value = ''
  if (!from || from === key) return
  layout.value = moveWidget(layout.value, from, key)
  persist()
}

onMounted(async () => {
  if (!ledgerStore.ready) {
    try { await ledgerStore.init({ waitForRemote: true }) } catch { /* 账本加载失败时组件按空数据处理 */ }
  }
  if (!tasksStore.ready) {
    try { await tasksStore.init({ waitForRemote: true }) } catch { /* 同上 */ }
  }
  if (!Object.keys(worktimeStore.records).length) {
    try { await worktimeStore.fetch() } catch { /* 工时接口失败时显示 0 */ }
  }
})
</script>

<style scoped>
.home-canvas { height: 100%; overflow-y: auto; padding: 24px 30px 40px; background: var(--paper); }
.home-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; margin-bottom: 18px; }
.home-eyebrow { margin: 0 0 4px; color: var(--accent); font-size: 10px; font-weight: 750; letter-spacing: .16em; }
.home-head h1 { margin: 0; font-size: clamp(24px, 2.2vw, 32px); font-weight: 730; letter-spacing: -.04em; }
.home-sub { margin: 6px 0 0; color: var(--muted); font-size: 12.5px; }
.home-edit { display: inline-flex; height: 34px; align-items: center; gap: 6px; padding: 0 13px; border: 1px solid var(--line2); border-radius: 10px; background: var(--card); color: var(--ink2); font-size: 12px; font-weight: 650; }
.home-edit:hover { border-color: var(--accent); color: var(--accent); }
.home-edit[aria-pressed="true"] { border-color: var(--accent); background: var(--accent); color: #fff; }
.home-edit-bar {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  margin-bottom: 12px; padding: 9px 13px; border: 1px dashed color-mix(in srgb, var(--accent) 40%, var(--line));
  border-radius: 12px; background: color-mix(in srgb, var(--accent-soft) 32%, var(--card)); color: var(--ink2); font-size: 11.5px;
}
.home-edit-bar button { padding: 0; border: 0; background: transparent; color: var(--accent); font-size: 11.5px; font-weight: 650; }
.home-hidden-bar { display: flex; flex-wrap: wrap; align-items: center; gap: 7px; margin-bottom: 12px; color: var(--muted); font-size: 11.5px; }
.home-hidden-bar button { padding: 5px 10px; border: 1px dashed var(--line2); border-radius: 999px; background: var(--card); color: var(--ink2); font-size: 11.5px; }
.home-hidden-bar button:hover { border-color: var(--accent); color: var(--accent); }
.home-grid { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); gap: 14px; align-items: stretch; }
.home-cell { position: relative; min-width: 0; }
/* 同一行的卡片等高：高度写死在这里，卡片内部按 flex 分配，避免被邻居的内容高度拉扯 */
.home-cell.hw-key-tasks, .home-cell.hw-key-calendar, .home-cell.hw-key-ledger { height: 500px; }
.home-cell.hw-key-cashflow, .home-cell.hw-key-budget, .home-cell.hw-key-category { height: 330px; }
.home-cell.hw-key-worktime, .home-cell.hw-key-ai { height: 360px; }
.home-cell.span-3 { grid-column: span 3; }
.home-cell.span-4 { grid-column: span 4; }
.home-cell.span-5 { grid-column: span 5; }
.home-cell.span-8 { grid-column: span 8; }
.home-cell.is-editing { cursor: grab; }
.home-cell.is-editing:active { cursor: grabbing; }
.home-cell.is-dragging { opacity: .45; }
.home-cell.is-drop-target :deep(.hw) { border-color: var(--accent); box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 26%, transparent); }
.home-cell-hide {
  position: absolute; z-index: 3; top: -7px; right: -7px;
  display: grid; width: 22px; height: 22px; place-items: center; padding: 0;
  border: 1px solid var(--line2); border-radius: 50%; background: var(--card); color: var(--muted);
  font-size: 13px; line-height: 1;
}
.home-cell-hide:hover { border-color: var(--down); color: var(--down); }
@media (max-width: 1439px) {
  .home-grid { grid-template-columns: repeat(6, minmax(0, 1fr)); }
  .home-cell.span-8, .home-cell.span-5 { grid-column: span 6; }
  .home-cell.span-4, .home-cell.span-3 { grid-column: span 3; }
}
@media (max-width: 900px) {
  .home-canvas { padding: 18px 14px 32px; }
  .home-cell.hw-key-tasks, .home-cell.hw-key-calendar, .home-cell.hw-key-ledger,
  .home-cell.hw-key-cashflow, .home-cell.hw-key-budget, .home-cell.hw-key-category,
  .home-cell.hw-key-worktime, .home-cell.hw-key-ai { height: auto; min-height: 0; }
  .home-head { align-items: flex-start; flex-direction: column; gap: 10px; }
  .home-grid { grid-template-columns: minmax(0, 1fr); gap: 12px; }
  .home-cell.span-8, .home-cell.span-5, .home-cell.span-4, .home-cell.span-3 { grid-column: span 1; }
  .home-edit-bar { flex-direction: column; align-items: flex-start; gap: 6px; }
}
</style>
