<template>
  <Teleport to="body">
    <div v-if="open" class="habit-form-mask" @click.self="emit('close')">
      <section class="habit-form" role="dialog" aria-label="添加习惯" @click.stop>
        <header class="habit-form-head">
          <h2>{{ habit ? '编辑习惯' : '添加习惯' }}</h2>
          <button type="button" class="habit-form-close" aria-label="关闭" @click="emit('close')"><X :size="16" /></button>
        </header>

        <div class="habit-form-body">
          <div class="habit-form-top">
            <div class="habit-field is-icon">
              <button type="button" class="habit-icon-btn" :style="iconStyle" aria-label="选择图标" :aria-expanded="openPanel === 'icon'" @click="togglePanel('icon')">
                {{ draft.icon || '✓' }}
              </button>
              <div v-if="openPanel === 'icon'" class="habit-panel is-icon">
                <div class="habit-emoji-grid">
                  <button v-for="emoji in EMOJIS" :key="emoji" type="button" :class="{ on: draft.icon === emoji }" @click="draft.icon = emoji">{{ emoji }}</button>
                </div>
                <div class="habit-color-row">
                  <button
                    v-for="color in COLORS"
                    :key="color"
                    type="button"
                    class="habit-color-dot"
                    :class="{ on: draft.color === color }"
                    :style="{ background: color }"
                    :aria-label="`颜色 ${color}`"
                    @click="draft.color = color"
                  />
                </div>
              </div>
            </div>
            <input v-model.trim="draft.name" class="habit-form-name" maxlength="40" placeholder="每天进步一点点" aria-label="习惯名称" />
          </div>

          <div class="habit-field">
            <span class="habit-field-label">频率</span>
            <button type="button" class="habit-field-value" :aria-expanded="openPanel === 'frequency'" @click="togglePanel('frequency')">
              {{ frequencyText }}<ChevronDown :size="14" />
            </button>
            <div v-if="openPanel === 'frequency'" class="habit-panel is-frequency">
              <div class="habit-seg">
                <button v-for="option in FREQUENCY_OPTIONS" :key="option.value" type="button" :class="{ on: draft.frequency === option.value }" @click="draft.frequency = option.value">{{ option.label }}</button>
              </div>
              <div v-if="draft.frequency === 'CUSTOM'" class="habit-weekdays">
                <span>在这些天</span>
                <div>
                  <button
                    v-for="(label, index) in WEEKDAY_LABELS"
                    :key="label"
                    type="button"
                    :class="{ on: draft.customDays.includes(index + 1) }"
                    @click="toggleWeekday(index + 1)"
                  >{{ label }}</button>
                </div>
              </div>
              <div v-else-if="draft.frequency === 'WEEKLY_N' || draft.frequency === 'MONTHLY_N'" class="habit-count-row">
                <span>{{ draft.frequency === 'WEEKLY_N' ? '每周' : '每月' }}</span>
                <input v-model.number="draft.targetCount" type="number" min="1" max="99" aria-label="次数" />
                <span>{{ draft.frequency === 'WEEKLY_N' ? '天' : '次' }}</span>
              </div>
              <footer class="habit-panel-foot"><button type="button" class="is-primary" @click="openPanel = ''">确定</button></footer>
            </div>
          </div>

          <div class="habit-field">
            <span class="habit-field-label">目标</span>
            <button type="button" class="habit-field-value" :aria-expanded="openPanel === 'target'" @click="togglePanel('target')">
              {{ targetText }}<ChevronDown :size="14" />
            </button>
            <div v-if="openPanel === 'target'" class="habit-panel is-target">
              <label class="habit-radio"><input v-model="draft.targetType" type="radio" value="CHECK" />当天完成打卡</label>
              <label class="habit-radio"><input v-model="draft.targetType" type="radio" value="QUANTITY" />当天完成一定量</label>
              <div v-if="draft.targetType === 'QUANTITY'" class="habit-count-row">
                <span>每天</span>
                <input v-model.number="draft.targetCount" type="number" min="1" max="99" aria-label="每天次数" />
                <span>次</span>
              </div>
              <div v-if="draft.targetType === 'QUANTITY'" class="habit-count-row">
                <span>单位</span>
                <input v-model.trim="draft.amountUnit" type="text" maxlength="8" placeholder="毫升 / kg" aria-label="完成量单位" />
              </div>
              <footer class="habit-panel-foot"><button type="button" class="is-primary" @click="openPanel = ''">确定</button></footer>
            </div>
          </div>

          <div class="habit-field">
            <span class="habit-field-label">开始日期</span>
            <button type="button" class="habit-field-value" :aria-expanded="openPanel === 'start'" @click="togglePanel('start')">
              {{ startText }}<ChevronDown :size="14" />
            </button>
            <div v-if="openPanel === 'start'" class="habit-panel is-calendar">
              <TaskDateTimePopover
                mode="DATE"
                :show-tabs="false"
                :month="calendarMonth"
                :calendar-days="calendarCells"
                :selected-key="draft.startDate"
                apply-label="确定"
                clear-label="取消"
                @quick-date="applyQuickDate"
                @move-month="moveMonth"
                @select-date="draft.startDate = $event"
                @clear-date="openPanel = ''"
                @apply="openPanel = ''"
              />
            </div>
          </div>

          <div class="habit-field">
            <span class="habit-field-label">坚持天数</span>
            <button type="button" class="habit-field-value" :aria-expanded="openPanel === 'goal'" @click="togglePanel('goal')">
              {{ goalText }}<ChevronDown :size="14" />
            </button>
            <div v-if="openPanel === 'goal'" class="habit-panel is-list">
              <button v-for="option in GOAL_PRESETS" :key="option.value" type="button" :class="{ on: Number(draft.goalDays || 0) === option.value }" @click="pickGoal(option.value)">
                {{ option.label }}<Check v-if="Number(draft.goalDays || 0) === option.value" :size="13" />
              </button>
              <div class="habit-count-row is-custom">
                <span>自定义</span>
                <input v-model.number="customGoal" type="number" min="1" max="3650" aria-label="自定义天数" @change="pickGoal(customGoal)" />
                <span>天</span>
              </div>
            </div>
          </div>

          <div class="habit-field">
            <span class="habit-field-label">所属分组</span>
            <button type="button" class="habit-field-value" :aria-expanded="openPanel === 'group'" @click="togglePanel('group')">
              {{ groupText }}<ChevronDown :size="14" />
            </button>
            <div v-if="openPanel === 'group'" class="habit-panel is-list">
              <button v-for="group in GROUPS" :key="group.key" type="button" :class="{ on: draft.groupKey === group.key }" @click="pickGroup(group.key)">
                {{ group.label }}<Check v-if="draft.groupKey === group.key" :size="13" />
              </button>
            </div>
          </div>

          <div class="habit-field">
            <span class="habit-field-label">提醒</span>
            <button type="button" class="habit-field-value is-remind" :aria-expanded="openPanel === 'remind'" @click="togglePanel('remind')">
              <template v-if="draft.remindAt">{{ draft.remindAt }}<X :size="13" @click.stop="draft.remindAt = ''" /></template>
              <Plus v-else :size="15" />
            </button>
            <div v-if="openPanel === 'remind'" class="habit-panel is-list is-remind">
              <button type="button" :class="{ on: !draft.remindAt }" @click="pickRemind('')">不提醒<Check v-if="!draft.remindAt" :size="13" /></button>
              <button v-for="time in REMIND_PRESETS" :key="time" type="button" :class="{ on: draft.remindAt === time }" @click="pickRemind(time)">
                {{ time }}<Check v-if="draft.remindAt === time" :size="13" />
              </button>
            </div>
          </div>

          <label class="habit-auto-log">
            <input v-model="draft.autoPopupLog" type="checkbox" />自动弹出打卡日志
          </label>
        </div>

        <footer class="habit-form-foot">
          <button type="button" class="habit-btn is-primary" :disabled="!canSave || saving" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
          <button type="button" class="habit-btn" @click="emit('close')">取消</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Check, ChevronDown, Plus, X } from 'lucide-vue-next'
import TaskDateTimePopover from '../tasks/TaskDateTimePopover.vue'
import { useExclusivePanels } from '../../utils/exclusivePanels.js'
import {
  GOAL_PRESETS, GROUPS, REMIND_PRESETS, WEEKDAY_LABELS, dateKey, parseKey, todayKey
} from './habitUtils.js'

const props = defineProps({
  open: { type: Boolean, default: false },
  habit: { type: Object, default: null },
  saving: { type: Boolean, default: false }
})
const emit = defineEmits(['close', 'submit'])

const EMOJIS = ['✓', '🏃', '💧', '📖', '🧘', '🌙', '💪', '🥗', '✍️', '🎯', '☀️', '🚭', '💊', '🛏️', '🧠', '🍎', '📝', '🎨', '🚴', '⏰', '🧹', '🐱', '🌱', '🎧']
const COLORS = ['#4d78db', '#37b8a6', '#e69b2d', '#e34d59', '#8b5cf6', '#0f9f6e', '#ef7fa8', '#5b6b7d', '#d97706', '#1f9bb5', '#a855f7', '#c2410c']
const FREQUENCY_OPTIONS = [
  { value: 'DAILY', label: '每天' },
  { value: 'CUSTOM', label: '按星期' },
  { value: 'WEEKLY_N', label: '每周 N 天' },
  { value: 'MONTHLY_N', label: '每月 N 次' }
]

const openPanel = ref('')
const calendarMonth = ref(new Date())
const customGoal = ref(0)
const draft = reactive({
  name: '', icon: '✓', color: '#4d78db', frequency: 'DAILY', targetCount: 1, customDays: [],
  startDate: todayKey(), remindAt: '', groupKey: 'other', goalDays: 0, checkStyle: 'CHECK',
  targetType: 'CHECK', autoPopupLog: false, amountUnit: ''
})

useExclusivePanels('habit-form', [
  ['panel', () => Boolean(openPanel.value), value => { if (!value) openPanel.value = '' }]
])

watch(() => props.open, value => {
  if (!value) return
  openPanel.value = ''
  const source = props.habit
  Object.assign(draft, {
    name: source?.name || '',
    icon: source?.icon || '✓',
    color: source?.color || '#4d78db',
    frequency: source?.frequency || 'DAILY',
    targetCount: Number(source?.targetCount || 1),
    customDays: [...(source?.customDays || [])].map(Number),
    startDate: String(source?.startDate || todayKey()).slice(0, 10),
    remindAt: source?.remindAt || '',
    groupKey: source?.groupKey || 'other',
    goalDays: Number(source?.goalDays || 0),
    checkStyle: source?.checkStyle || 'CHECK',
    targetType: source?.targetType || 'CHECK',
    autoPopupLog: Boolean(source?.autoPopupLog),
    amountUnit: source?.amountUnit || ''
  })
  customGoal.value = Number(source?.goalDays || 0) || 21
  calendarMonth.value = parseKey(String(draft.startDate).slice(0, 10))
})

const iconStyle = computed(() => ({
  background: `color-mix(in srgb, ${draft.color} 18%, transparent)`,
  color: draft.color
}))
const canSave = computed(() => Boolean(draft.name.trim()))

const frequencyText = computed(() => {
  if (draft.frequency === 'WEEKLY_N') return `每周 ${draft.targetCount} 天`
  if (draft.frequency === 'MONTHLY_N') return `每月 ${draft.targetCount} 次`
  if (draft.frequency === 'CUSTOM') {
    const days = [...draft.customDays].sort()
    return days.length ? `每周${days.map(day => WEEKDAY_LABELS[day - 1]).join('、')}` : '按星期'
  }
  return '每天'
})
const targetText = computed(() => (draft.targetType === 'QUANTITY' ? `每天 ${draft.targetCount} 次` : '当天完成打卡'))
const goalText = computed(() => {
  const goal = Number(draft.goalDays || 0)
  if (!goal) return '永远'
  return `${goal} 天`
})
const groupText = computed(() => GROUPS.find(group => group.key === draft.groupKey)?.label || '其他')
const startText = computed(() => {
  const date = parseKey(String(draft.startDate).slice(0, 10))
  return `${date.getMonth() + 1}月${date.getDate()}日`
})

const calendarCells = computed(() => {
  const month = calendarMonth.value
  const first = new Date(month.getFullYear(), month.getMonth(), 1)
  const offset = (first.getDay() + 6) % 7
  const start = new Date(month.getFullYear(), month.getMonth(), 1 - offset)
  const today = todayKey()
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start)
    date.setDate(start.getDate() + index)
    const key = dateKey(date)
    return { key, date, today: key === today, otherMonth: date.getMonth() !== month.getMonth(), selected: key === draft.startDate }
  })
})

function togglePanel(name) {
  openPanel.value = openPanel.value === name ? '' : name
  if (openPanel.value === 'start') calendarMonth.value = parseKey(draft.startDate)
}

function toggleWeekday(day) {
  const index = draft.customDays.indexOf(day)
  if (index >= 0) draft.customDays.splice(index, 1)
  else draft.customDays.push(day)
}

function pickGoal(value) {
  draft.goalDays = Number(value) || 0
  openPanel.value = ''
}

function pickGroup(key) {
  draft.groupKey = key
  openPanel.value = ''
}

function pickRemind(time) {
  draft.remindAt = time
  openPanel.value = ''
}

function moveMonth(delta) {
  calendarMonth.value = new Date(calendarMonth.value.getFullYear(), calendarMonth.value.getMonth() + delta, 1)
}

function applyQuickDate(kind) {
  const date = new Date()
  if (kind === 'TOMORROW') date.setDate(date.getDate() + 1)
  if (kind === 'NEXT_WEEK') date.setDate(date.getDate() + 7)
  if (kind === 'NEXT_MONTH') date.setMonth(date.getMonth() + 1)
  draft.startDate = dateKey(date)
  calendarMonth.value = new Date(date.getFullYear(), date.getMonth(), 1)
}

function submit() {
  if (!canSave.value) return
  emit('submit', {
    name: draft.name.trim(),
    icon: draft.icon,
    color: draft.color,
    frequency: draft.frequency,
    targetCount: Math.max(1, Number(draft.targetCount) || 1),
    customDays: draft.frequency === 'CUSTOM' ? [...draft.customDays].sort() : [],
    startDate: draft.startDate,
    remindAt: draft.remindAt || null,
    groupKey: draft.groupKey,
    goalDays: Number(draft.goalDays) || 0,
    checkStyle: draft.checkStyle,
    targetType: draft.targetType,
    dailyTimes: draft.targetType === 'QUANTITY' ? Math.max(1, Number(draft.targetCount) || 1) : 1,
    autoPopupLog: draft.autoPopupLog,
    amountUnit: draft.targetType === 'QUANTITY' ? draft.amountUnit : ''
  })
}

function onDocPointerDown(event) {
  if (!openPanel.value) return
  if (event.target.closest?.('.habit-field')) return
  openPanel.value = ''
}

onMounted(() => document.addEventListener('pointerdown', onDocPointerDown, true))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocPointerDown, true))
</script>

<style scoped>
.habit-form-mask { position: fixed; z-index: 160; inset: 0; display: grid; place-items: center; padding: 20px; background: rgb(15 23 42 / 40%); }
.habit-form {
  display: grid;
  width: min(432px, calc(100vw - 24px));
  max-height: calc(100dvh - 40px);
  gap: 0;
  border-radius: 18px;
  background: var(--card);
  box-shadow: var(--module-shadow, 0 28px 64px rgb(15 23 42 / 26%));
}
.habit-form-head { display: flex; align-items: center; justify-content: space-between; padding: 14px 18px 6px; }
.habit-form-head h2 { margin: 0; font-size: 14px; font-weight: 600; }
.habit-form-close { display: grid; width: 26px; height: 26px; place-items: center; border: 0; border-radius: 8px; background: transparent; color: var(--muted); cursor: pointer; }
.habit-form-close:hover { background: var(--paper); color: var(--ink); }

.habit-form-body { display: grid; gap: 0; overflow: auto; padding: 4px 18px 8px; }
.habit-form-top { display: flex; align-items: center; gap: 10px; padding-bottom: 8px; }
.habit-icon-btn { display: grid; width: 38px; height: 38px; flex: 0 0 auto; place-items: center; border: 0; border-radius: 12px; font-size: 18px; line-height: 1; cursor: pointer; }
.habit-form-name { flex: 1; min-width: 0; height: 38px; padding: 0 12px; border: 1px solid var(--line2); border-radius: 10px; background: var(--card); color: var(--ink); font: inherit; font-size: 13px; }
.habit-form-name:focus { outline: 2px solid color-mix(in srgb, var(--accent) 30%, transparent); outline-offset: -1px; }

.habit-field { position: relative; display: flex; align-items: center; gap: 12px; padding: 9px 0; border-top: 1px solid var(--line); }
.habit-field-label { flex: 0 0 62px; color: var(--muted); font-size: 12.5px; }
.habit-field-value {
  display: flex; flex: 1; align-items: center; justify-content: space-between; gap: 6px;
  height: 34px; padding: 0 10px;
  border: 1px solid var(--line); border-radius: 9px;
  background: var(--panel, var(--card)); color: var(--ink2);
  font: inherit; font-size: 12.5px; text-align: left; cursor: pointer;
}
.habit-field-value:hover { border-color: var(--line2); color: var(--ink); }
.habit-field-value.is-remind { flex: 0 0 64px; justify-content: center; color: var(--accent); }

.habit-panel {
  position: absolute; z-index: 20; top: 42px; left: 62px; right: 0;
  display: grid; gap: 8px; padding: 12px;
  border: 1px solid var(--line); border-radius: 14px;
  background: var(--card); box-shadow: var(--module-shadow, 0 18px 44px rgb(15 23 42 / 18%));
}
.habit-panel.is-icon { left: 0; right: auto; width: 268px; }
.habit-panel.is-calendar { left: auto; right: 0; width: 296px; padding: 6px; }
.habit-panel.is-remind { max-height: 220px; overflow: auto; }
.habit-panel.is-list { gap: 2px; padding: 8px; }
.habit-panel.is-list button { display: flex; align-items: center; justify-content: space-between; padding: 7px 9px; border: 0; border-radius: 8px; background: transparent; color: var(--ink2); font: inherit; font-size: 12.5px; text-align: left; cursor: pointer; }
.habit-panel.is-list button:hover { background: var(--paper); }
.habit-panel.is-list button.on { color: var(--accent); font-weight: 600; }

.habit-emoji-grid { display: grid; grid-template-columns: repeat(8, 1fr); gap: 2px; }
.habit-emoji-grid button { display: grid; width: 100%; aspect-ratio: 1; place-items: center; border: 0; border-radius: 8px; background: transparent; font-size: 15px; cursor: pointer; }
.habit-emoji-grid button:hover { background: var(--paper); }
.habit-emoji-grid button.on { background: color-mix(in srgb, var(--accent) 14%, transparent); }
.habit-color-row { display: flex; flex-wrap: wrap; gap: 6px; padding-top: 6px; border-top: 1px solid var(--line); }
.habit-color-dot { width: 18px; height: 18px; border: 2px solid transparent; border-radius: 50%; cursor: pointer; }
.habit-color-dot.on { border-color: var(--ink); }

.habit-seg { display: grid; grid-template-columns: repeat(2, 1fr); gap: 4px; }
.habit-seg button { padding: 7px 8px; border: 1px solid var(--line); border-radius: 8px; background: transparent; color: var(--ink2); font: inherit; font-size: 12px; cursor: pointer; }
.habit-seg button.on { border-color: var(--accent); background: color-mix(in srgb, var(--accent) 8%, transparent); color: var(--accent); font-weight: 600; }

.habit-weekdays { display: grid; gap: 6px; }
.habit-weekdays > span { color: var(--muted); font-size: 11.5px; }
.habit-weekdays > div { display: flex; gap: 5px; }
.habit-weekdays button { display: grid; width: 30px; height: 30px; place-items: center; border: 0; border-radius: 50%; background: var(--paper); color: var(--ink2); font: inherit; font-size: 12px; cursor: pointer; }
.habit-weekdays button.on { background: var(--accent); color: var(--accent-contrast, #fff); }

.habit-count-row { display: flex; align-items: center; gap: 8px; color: var(--ink2); font-size: 12.5px; }
.habit-count-row.is-custom { padding-top: 6px; border-top: 1px solid var(--line); }
.habit-count-row input { width: 64px; height: 30px; padding: 0 8px; border: 1px solid var(--line); border-radius: 8px; background: var(--paper); color: var(--ink); font: inherit; font-size: 12.5px; text-align: center; }

.habit-radio { display: flex; align-items: center; gap: 7px; color: var(--ink2); font-size: 12.5px; cursor: pointer; }
.habit-radio input { accent-color: var(--accent); }

.habit-panel-foot { display: flex; justify-content: flex-end; gap: 8px; padding-top: 4px; border-top: 1px solid var(--line); }
.habit-panel-foot button { min-width: 64px; height: 30px; border: 1px solid var(--line); border-radius: 8px; background: transparent; color: var(--ink2); font: inherit; font-size: 12.5px; cursor: pointer; }
.habit-panel-foot button.is-primary { border-color: var(--ink); background: var(--ink); color: var(--card); }

.habit-auto-log { display: flex; align-items: center; gap: 8px; padding-top: 12px; border-top: 1px solid var(--line); color: var(--muted); font-size: 12px; cursor: pointer; }
.habit-auto-log input { accent-color: var(--accent); }

.habit-form-foot { display: flex; justify-content: flex-end; gap: 8px; padding: 12px 18px 16px; border-top: 1px solid var(--line); }
.habit-btn { min-width: 78px; height: 34px; border: 1px solid var(--line2); border-radius: 9px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer; }
.habit-btn.is-primary { border-color: var(--accent); background: var(--accent); color: var(--accent-contrast, #fff); font-weight: 600; }
.habit-btn:disabled { opacity: .45; cursor: default; }
</style>
