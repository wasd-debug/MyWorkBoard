<template>
  <Teleport to="body">
    <div v-if="open" class="hf-mask" @click.self="emit('close')">
      <section class="hf-sheet" role="dialog" aria-modal="true" :aria-label="habit ? '编辑习惯' : '添加习惯'" @click.stop>
        <span class="hf-dot" aria-hidden="true" />
        <header class="hf-head">
          <h2>{{ habit ? '编辑习惯' : '添加习惯' }}</h2>
        </header>

        <div class="hf-body">
          <div class="hf-top">
            <div class="hf-icon-wrap">
              <button type="button" class="hf-icon" :style="iconStyle" aria-label="选择图标" :aria-expanded="openPanel === 'icon'" @click="togglePanel('icon')">
                {{ iconPreview }}
              </button>
              <span class="hf-icon-pen" aria-hidden="true"><Pencil :size="9" /></span>
            </div>
            <input v-model.trim="draft.name" class="hf-name" maxlength="40" placeholder="习惯名称" aria-label="习惯名称" />
          </div>

          <div class="hf-field">
            <span class="hf-label">频率</span>
            <button type="button" class="hf-value" :aria-expanded="openPanel === 'frequency'" @click="togglePanel('frequency')">
              <span>{{ frequencyLabel(draft) }}</span><ChevronDown :size="14" />
            </button>
            <div v-if="openPanel === 'frequency'" class="hf-pop is-freq">
              <div class="hf-seg">
                <button v-for="tab in FREQUENCY_TABS" :key="tab.key" type="button" :class="{ on: freq.mode === tab.key }" @click="freq.mode = tab.key">{{ tab.label }}</button>
              </div>

              <template v-if="freq.mode === 'DAY'">
                <p class="hf-pop-hint">在这些天</p>
                <div class="hf-week">
                  <button v-for="(label, index) in WEEKDAY_LABELS" :key="label" type="button" :class="{ on: freq.days.includes(index + 1) }" @click="toggleWeekday(index + 1)">{{ label }}</button>
                </div>
              </template>

              <template v-else-if="freq.mode === 'WEEK'">
                <p class="hf-pop-hint">每周 {{ freq.count }} 天</p>
                <div class="hf-week">
                  <button v-for="n in 7" :key="n" type="button" :class="{ on: freq.count === n }" @click="freq.count = n">{{ n }}</button>
                </div>
              </template>

              <template v-else>
                <div class="hf-line">
                  <span>每</span>
                  <input v-model="freq.interval" class="hf-num" inputmode="numeric" aria-label="间隔天数" @input="sanitizeDigit('interval', 999)" />
                  <span>天</span>
                </div>
              </template>

              <footer class="hf-pop-foot">
                <button type="button" @click="openPanel = ''">取消</button>
                <button type="button" class="is-primary" @click="applyFrequency">确定</button>
              </footer>
            </div>
          </div>

          <div class="hf-field">
            <span class="hf-label">目标</span>
            <button type="button" class="hf-value" :aria-expanded="openPanel === 'target'" @click="togglePanel('target')">
              <span>{{ targetLabel(draft) }}</span><ChevronDown :size="14" />
            </button>
            <div v-if="openPanel === 'target'" class="hf-pop is-target">
              <button type="button" class="hf-option" :class="{ on: target.type === 'CHECK' }" @click="target.type = 'CHECK'">
                当天完成打卡<Check v-if="target.type === 'CHECK'" :size="15" />
              </button>
              <button type="button" class="hf-option" :class="{ on: target.type === 'QUANTITY' }" @click="target.type = 'QUANTITY'">
                当天完成一定量<Check v-if="target.type === 'QUANTITY'" :size="15" />
              </button>

              <div v-if="target.type === 'QUANTITY'" class="hf-quantity">
                <div class="hf-line">
                  <span>每天</span>
                  <input v-model="target.times" class="hf-num is-sm" inputmode="numeric" aria-label="每天目标量" @input="sanitizeDigit('times', 999)" />
                  <input v-model.trim="target.unit" class="hf-unit" maxlength="8" placeholder="单位" aria-label="单位" />
                </div>
                <div class="hf-line">
                  <span>打卡时</span>
                  <select v-model="target.mode" class="hf-select" aria-label="打卡方式">
                    <option v-for="item in CHECK_MODES" :key="item.key" :value="item.key">{{ item.label }}</option>
                  </select>
                </div>
                <div class="hf-line">
                  <span>每次记录({{ target.unit || '单位' }})</span>
                  <input v-model="target.per" class="hf-num is-sm" inputmode="numeric" aria-label="每次记录量" @input="sanitizeDigit('per', 99999)" />
                </div>
              </div>

              <footer class="hf-pop-foot">
                <button type="button" @click="openPanel = ''">取消</button>
                <button type="button" class="is-primary" @click="applyTarget">确定</button>
              </footer>
            </div>
          </div>

          <div class="hf-field">
            <span class="hf-label">开始日期</span>
            <button type="button" class="hf-value" :aria-expanded="openPanel === 'start'" @click="togglePanel('start')">
              <span>{{ startText }}</span><ChevronDown :size="14" />
            </button>
            <div v-if="openPanel === 'start'" class="hf-pop is-calendar">
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

          <div class="hf-field">
            <span class="hf-label">坚持天数 <HelpCircle :size="12" class="hf-help" /></span>
            <button type="button" class="hf-value" :aria-expanded="openPanel === 'goal'" @click="togglePanel('goal')">
              <span>{{ goalText }}</span><ChevronDown :size="14" />
            </button>
            <div v-if="openPanel === 'goal' || openPanel === 'goalCustom'" class="hf-pop is-list">
              <button v-for="option in GOAL_PRESETS" :key="option.value" type="button" class="hf-option" :class="{ on: Number(draft.goalDays || 0) === option.value }" @click="pickGoal(option.value)">
                {{ option.label }}<Check v-if="Number(draft.goalDays || 0) === option.value" :size="15" />
              </button>
              <button type="button" class="hf-option" :class="{ on: customGoalActive }" @click="startCustomGoal">
                自定义<Check v-if="customGoalActive" :size="15" />
              </button>
              <div v-if="openPanel === 'goalCustom'" class="hf-custom">
                <input
                  ref="goalInputEl"
                  v-model="customGoal"
                  class="hf-custom-input"
                  inputmode="numeric"
                  maxlength="3"
                  placeholder="1 - 999"
                  aria-label="自定义坚持天数"
                  @input="sanitizeDigit('goal', 999)"
                  @keydown.enter.prevent="commitCustomGoal"
                />
                <div class="hf-custom-foot">
                  <button type="button" @click="openPanel = 'goal'">取消</button>
                  <button type="button" class="is-primary" :disabled="!customGoalValid" @click="commitCustomGoal">确定</button>
                </div>
              </div>
            </div>
          </div>

          <div class="hf-field">
            <span class="hf-label">所属分组</span>
            <button type="button" class="hf-value" :aria-expanded="openPanel === 'group'" @click="togglePanel('group')">
              <span>{{ groupLabel(draft.groupKey, customGroups) }}</span><ChevronDown :size="14" />
            </button>
            <div v-if="openPanel === 'group' || openPanel === 'groupAdd'" class="hf-pop is-list">
              <button v-for="group in allGroups" :key="group.key" type="button" class="hf-option" :class="{ on: draft.groupKey === group.key }" @click="pickGroup(group.key)">
                {{ group.label }}<Check v-if="draft.groupKey === group.key" :size="15" />
              </button>
              <button type="button" class="hf-option is-add" @click="startAddGroup">添加分组</button>
              <div v-if="openPanel === 'groupAdd'" class="hf-custom">
                <input
                  ref="groupInputEl"
                  v-model="newGroup"
                  class="hf-custom-input"
                  maxlength="16"
                  placeholder="新分组"
                  aria-label="新分组名称"
                  @keydown.enter.prevent="commitAddGroup"
                />
                <div class="hf-custom-foot">
                  <button type="button" @click="openPanel = 'group'">取消</button>
                  <button type="button" class="is-primary" :disabled="!groupValid" @click="commitAddGroup">确定</button>
                </div>
              </div>
            </div>
          </div>

          <div class="hf-field">
            <span class="hf-label">提醒</span>
            <div class="hf-remind">
              <span v-for="(time, index) in times" :key="`${time}-${index}`" class="hf-chip">
                <button type="button" class="hf-chip-time" @click="togglePanel('remind')">{{ time }}</button>
                <button type="button" class="hf-chip-x" :aria-label="`删除 ${time}`" @click="removeRemind(index)"><X :size="11" /></button>
              </span>
              <button type="button" class="hf-add" aria-label="添加提醒" :aria-expanded="openPanel === 'remind'" @click="togglePanel('remind')"><Plus :size="15" /></button>
              <div v-if="openPanel === 'remind'" class="hf-pop is-remind">
                <button v-for="time in REMIND_PRESETS" :key="time" type="button" class="hf-option" :class="{ on: times.includes(time) }" @click="pickRemind(time)">
                  {{ time }}<Check v-if="times.includes(time)" :size="15" />
                </button>
              </div>
            </div>
          </div>

          <div class="hf-divider" />

          <label class="hf-switch-row">
            <button type="button" class="hf-switch" :class="{ on: draft.autoPopupLog }" role="switch" :aria-checked="draft.autoPopupLog" aria-label="自动弹出打卡日志" @click="draft.autoPopupLog = !draft.autoPopupLog"><i /></button>
            <span>自动弹出打卡日志</span>
          </label>
        </div>

        <footer class="hf-foot">
          <button type="button" class="hf-btn" @click="emit('close')">取消</button>
          <button type="button" class="hf-btn is-primary" :disabled="!canSave || saving" @click="submit">{{ saving ? '保存中…' : '确定' }}</button>
        </footer>

        <div v-if="openPanel === 'icon'" class="hf-icon-pop" @click.stop>
          <div class="hf-icon-preview">
            <span class="hf-icon-big" :style="iconStyle">{{ draft.icon || '📖' }}</span>
            <button type="button" class="hf-icon-big is-text" :class="{ on: iconMode === 'TEXT' }" @click="pickTextIcon">{{ textIcon }}</button>
          </div>
          <div class="hf-icon-grid">
            <button
              v-for="item in ICONS"
              :key="item.emoji"
              type="button"
              class="hf-icon-cell"
              :class="{ on: iconMode === 'EMOJI' && draft.icon === item.emoji }"
              :style="{ background: item.color }"
              @click="pickIcon(item)"
            >{{ item.emoji }}</button>
          </div>
          <footer class="hf-pop-foot">
            <button type="button" @click="openPanel = ''">取消</button>
            <button type="button" class="is-primary" @click="openPanel = ''">确定</button>
          </footer>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Check, ChevronDown, HelpCircle, Pencil, Plus, X } from 'lucide-vue-next'
import TaskDateTimePopover from '../tasks/TaskDateTimePopover.vue'
import { useExclusivePanels } from '../../utils/exclusivePanels.js'
import {
  CHECK_MODES, FREQUENCY_TABS, GOAL_MAX, GOAL_PRESETS, GROUPS, REMIND_PRESETS, WEEKDAY_LABELS,
  blankHabit, dateKey, frequencyLabel, groupLabel, parseKey, targetLabel, todayKey
} from './habitUtils.js'
import { loadCustomGroups, saveCustomGroups, groupKeyFromLabel } from './habitGroups.js'

const props = defineProps({
  open: { type: Boolean, default: false },
  habit: { type: Object, default: null },
  saving: { type: Boolean, default: false }
})
const emit = defineEmits(['close', 'submit'])

const PALETTE = [
  '#f7c66a', '#f0a273', '#7fc3a8', '#6fb3e0', '#b3a4e8', '#f18fa4', '#8fd08b',
  '#f2b8a2', '#9ad4e2', '#e5bcd8', '#a8c7a0', '#cfd3da'
]
const EMOJI_SET = [
  '📖', '🖊️', '🏃', '💧', '🧘', '🌙', '💪', '🥗', '🎯', '☀️', '🚭', '💊', '🛏️', '🧠', '🍎', '📝', '🎨', '🚴', '⏰',
  '🧹', '🐱', '🌱', '🎧', '😴', '🍵', '🫧', '🧴', '🪥', '🚿', '🕶️', '🧢', '👟', '🏊', '🧗', '🚶', '🤸', '⚽', '🏀',
  '🎾', '🏸', '🥊', '🤿', '🧘‍♀️', '🛹', '🪂', '🎹', '🎸', '🎤', '🎬', '📷', '🎮', '🧩', '♟️', '📚', '🗞️', '✏️', '🖍️',
  '💻', '⌨️', '🖥️', '📱', '☎️', '📮', '🧾', '💡', '🕯️', '🪴', '🌷', '🌸', '🍀', '🌵', '🐶', '🐕', '🐈', '🐟', '🐦',
  '🍚', '🍜', '🥛', '☕', '🥤', '🍊', '🍌', '🥕', '🍇', '🥦', '🫐', '🍫', '🥚', '🧊', '🫖', '🍽️', '🧼', '🧽', '🗑️',
  '🛒', '💊', '🩺', '🪞', '💄', '💅', '🧴', '🪒', '👓', '🛌', '🪟', '🚪', '🔑', '🪙', '💴', '📦', '🎁', '☂️', '🌈'
]
const ICONS = [...new Set(EMOJI_SET)].map((emoji, index) => ({ emoji, color: PALETTE[index % PALETTE.length] }))

const openPanel = ref('')
const calendarMonth = ref(new Date())
const customGoal = ref('')
const newGroup = ref('')
const customGroups = ref(loadCustomGroups())
const iconMode = ref('EMOJI')
const goalInputEl = ref(null)
const groupInputEl = ref(null)

const draft = reactive(blankHabit())
const freq = reactive({ mode: 'DAY', days: [1, 2, 3, 4, 5, 6, 7], count: 3, interval: '2' })
const target = reactive({ type: 'CHECK', times: '1', unit: '', mode: 'AUTO', per: '1' })

const times = computed(() => String(draft.remindAt || '').split(',').map(item => item.trim()).filter(Boolean))
const allGroups = computed(() => [...GROUPS, ...customGroups.value])
const iconStyle = computed(() => ({
  background: `color-mix(in srgb, ${draft.color || '#4d78db'} 26%, transparent)`,
  color: draft.color || '#4d78db'
}))
const iconPreview = computed(() => (iconMode.value === 'TEXT' ? textIcon.value : draft.icon || '📖'))
const textIcon = computed(() => (draft.name || '习').trim().slice(0, 1))
const canSave = computed(() => Boolean(draft.name.trim()))
const goalText = computed(() => (Number(draft.goalDays || 0) ? `${draft.goalDays} 天` : '永远'))
const customGoalActive = computed(() => Boolean(Number(draft.goalDays || 0)) && !GOAL_PRESETS.some(option => option.value === Number(draft.goalDays || 0)))
const customGoalValid = computed(() => Number(customGoal.value) >= 1 && Number(customGoal.value) <= GOAL_MAX)
const groupValid = computed(() => Boolean(groupKeyFromLabel(newGroup.value, allGroups.value)))
const startText = computed(() => {
  const date = parseKey(String(draft.startDate).slice(0, 10))
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
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

useExclusivePanels('habit-form', [
  ['panel', () => Boolean(openPanel.value), value => { if (!value) openPanel.value = '' }]
])

watch(() => props.open, value => {
  if (!value) return
  openPanel.value = ''
  customGroups.value = loadCustomGroups()
  const source = props.habit
  Object.assign(draft, blankHabit(), {
    name: source?.name || '',
    icon: source?.icon || '📖',
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
    dailyTimes: Number(source?.dailyTimes || 1),
    autoPopupLog: Boolean(source?.autoPopupLog),
    amountUnit: source?.amountUnit || '',
    intervalDays: Number(source?.intervalDays || 2),
    checkMode: source?.checkMode || 'AUTO',
    perAmount: Number(source?.perAmount || 1),
    showInToday: source?.showInToday !== false
  })
  iconMode.value = source?.icon && source.icon.length === 1 && !EMOJI_SET.includes(source.icon) ? 'TEXT' : 'EMOJI'
  calendarMonth.value = parseKey(String(draft.startDate).slice(0, 10))
  customGoal.value = ''
  newGroup.value = ''
})

function togglePanel(name) {
  if (name === 'frequency') {
    freq.mode = draft.frequency === 'INTERVAL' ? 'INTERVAL'
      : (draft.frequency === 'WEEKLY_N' || draft.frequency === 'MONTHLY_N') ? 'WEEK' : 'DAY'
    freq.days = draft.frequency === 'CUSTOM' ? [...draft.customDays].sort((a, b) => a - b) : [1, 2, 3, 4, 5, 6, 7]
    freq.count = String(draft.targetCount || 3)
    freq.interval = String(draft.intervalDays || 2)
  }
  if (name === 'target') {
    target.type = draft.targetType || 'CHECK'
    target.times = String(draft.dailyTimes || 1)
    target.unit = draft.amountUnit || ''
    target.mode = draft.checkMode || 'AUTO'
    target.per = String(draft.perAmount || 1)
  }
  if (name === 'start') calendarMonth.value = parseKey(draft.startDate)
  openPanel.value = openPanel.value === name ? '' : name
}

/** 数字输入只留正整数，超上限截断 */
function sanitizeDigit(field, max) {
  const raw = String(field === 'goal' ? customGoal.value : field === 'interval' ? freq.interval : field === 'times' ? target.times : target.per)
  const digits = raw.replace(/[^\d]/g, '').replace(/^0+/, '')
  const clipped = digits.slice(0, String(max).length)
  const value = clipped ? String(Math.min(Number(clipped), max)) : ''
  if (field === 'goal') customGoal.value = value
  else if (field === 'interval') freq.interval = value
  else if (field === 'times') target.times = value
  else target.per = value
}

function toggleWeekday(day) {
  const index = freq.days.indexOf(day)
  if (index >= 0) {
    if (freq.days.length === 1) return
    freq.days.splice(index, 1)
  } else {
    freq.days.push(day)
  }
}

function applyFrequency() {
  if (freq.mode === 'WEEK') {
    draft.frequency = 'WEEKLY_N'
    draft.targetCount = Math.max(1, Number(freq.count) || 1)
  } else if (freq.mode === 'INTERVAL') {
    draft.frequency = 'INTERVAL'
    draft.intervalDays = Math.max(1, Number(freq.interval) || 1)
  } else {
    const days = [...freq.days].sort((a, b) => a - b)
    draft.customDays = days.length === 7 ? [] : days
    draft.frequency = days.length === 7 ? 'DAILY' : 'CUSTOM'
    if (days.length === 7) draft.customDays = []
  }
  openPanel.value = ''
}

function applyTarget() {
  draft.targetType = target.type
  draft.dailyTimes = target.type === 'QUANTITY' ? Math.max(1, Number(target.times) || 1) : 1
  draft.amountUnit = target.type === 'QUANTITY' ? target.unit : ''
  draft.checkMode = target.mode
  draft.perAmount = target.type === 'QUANTITY' ? Math.max(1, Number(target.per) || 1) : 1
  openPanel.value = ''
}

function pickGoal(value) {
  draft.goalDays = Number(value) || 0
  openPanel.value = ''
}

function startCustomGoal() {
  customGoal.value = customGoalActive.value ? String(draft.goalDays) : ''
  openPanel.value = 'goalCustom'
  nextTick(() => goalInputEl.value?.focus())
}

function commitCustomGoal() {
  if (!customGoalValid.value) return
  draft.goalDays = Number(customGoal.value)
  openPanel.value = ''
}

function pickGroup(key) {
  draft.groupKey = key
  openPanel.value = ''
}

function startAddGroup() {
  newGroup.value = ''
  openPanel.value = 'groupAdd'
  nextTick(() => groupInputEl.value?.focus())
}

function commitAddGroup() {
  const key = groupKeyFromLabel(newGroup.value, allGroups.value)
  if (!key) return
  customGroups.value = [...customGroups.value, { key, label: newGroup.value.trim().slice(0, 16) }]
  saveCustomGroups(customGroups.value)
  draft.groupKey = key
  openPanel.value = ''
}

function pickRemind(time) {
  const list = [...times.value]
  if (list.includes(time)) {
    draft.remindAt = list.filter(item => item !== time).join(',')
  } else {
    draft.remindAt = [...list, time].sort().join(',')
  }
}

function removeRemind(index) {
  draft.remindAt = times.value.filter((_, idx) => idx !== index).join(',')
}

function pickIcon(item) {
  draft.icon = item.emoji
  draft.color = item.color
  iconMode.value = 'EMOJI'
}

function pickTextIcon() {
  iconMode.value = 'TEXT'
  draft.icon = textIcon.value
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
    icon: iconMode.value === 'TEXT' ? textIcon.value : draft.icon,
    color: draft.color,
    frequency: draft.frequency,
    targetCount: draft.frequency === 'WEEKLY_N' ? Math.max(1, Number(draft.targetCount) || 1) : 1,
    customDays: draft.frequency === 'CUSTOM' ? [...draft.customDays].sort((a, b) => a - b) : [],
    startDate: draft.startDate,
    remindAt: times.value.join(',') || null,
    groupKey: draft.groupKey,
    goalDays: Number(draft.goalDays) || 0,
    checkStyle: draft.checkStyle,
    targetType: draft.targetType,
    dailyTimes: draft.targetType === 'QUANTITY' ? Math.max(1, Number(draft.dailyTimes) || 1) : 1,
    autoPopupLog: draft.autoPopupLog,
    amountUnit: draft.targetType === 'QUANTITY' ? draft.amountUnit : '',
    intervalDays: draft.frequency === 'INTERVAL' ? Math.max(1, Number(draft.intervalDays) || 1) : 2,
    checkMode: draft.checkMode,
    perAmount: draft.targetType === 'QUANTITY' ? Math.max(1, Number(draft.perAmount) || 1) : 1,
    showInToday: draft.showInToday
  })
}

function onDocPointerDown(event) {
  if (!openPanel.value) return
  const targetEl = event.target
  if (targetEl.closest?.('.hf-field') || targetEl.closest?.('.hf-icon-wrap') || targetEl.closest?.('.hf-icon-pop')) return
  openPanel.value = ''
}

onMounted(() => document.addEventListener('pointerdown', onDocPointerDown, true))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocPointerDown, true))
</script>

<style scoped>
.hf-mask { position: fixed; z-index: 170; inset: 0; display: grid; place-items: center; padding: 20px; background: rgb(15 23 42 / 38%); }
.hf-sheet {
  position: relative;
  display: flex; flex-direction: column;
  width: min(432px, calc(100vw - 24px));
  max-height: calc(100dvh - 40px);
  border-radius: 18px;
  background: var(--card);
  box-shadow: 0 30px 70px rgb(15 23 42 / 26%);
}
.hf-dot { position: absolute; top: 12px; left: 12px; width: 9px; height: 9px; border-radius: 50%; background: #f26d63; }
.hf-head { padding: 14px 18px 4px; }
.hf-head h2 { margin: 0; font-size: 15px; font-weight: 600; text-align: center; }

.hf-body { display: flex; flex-direction: column; padding: 6px 18px 4px; }

.hf-top { display: flex; align-items: center; gap: 14px; padding: 6px 0 10px; }
.hf-icon-wrap { position: relative; }
.hf-icon { display: grid; width: 44px; height: 44px; place-items: center; border: 0; border-radius: 50%; font-size: 22px; line-height: 1; cursor: pointer; }
.hf-icon-pen { position: absolute; right: -1px; bottom: 0; display: grid; width: 15px; height: 15px; place-items: center; border-radius: 50%; background: var(--card); color: var(--muted); box-shadow: 0 0 0 1px var(--line); }
.hf-name {
  flex: 1; min-width: 0; height: 42px; padding: 0 14px;
  border: 1.5px solid var(--priority-low, #4d78db); border-radius: 10px;
  background: var(--card); color: var(--ink); font: inherit; font-size: 15px; font-weight: 600;
}
.hf-name:focus { outline: 2px solid color-mix(in srgb, var(--priority-low, #4d78db) 26%, transparent); outline-offset: -1px; }

.hf-field { position: relative; display: flex; align-items: center; gap: 12px; padding: 8px 0; }
.hf-label { display: flex; flex: 0 0 74px; align-items: center; gap: 3px; color: var(--muted); font-size: 13px; }
.hf-help { color: var(--dim, var(--muted)); }
.hf-value {
  display: flex; flex: 1; min-width: 0; align-items: center; justify-content: space-between; gap: 6px;
  height: 40px; padding: 0 12px;
  border: 1px solid var(--line); border-radius: 10px;
  background: var(--panel, var(--paper)); color: var(--ink);
  font: inherit; font-size: 13.5px; text-align: left; cursor: pointer;
}
.hf-value:hover { border-color: var(--line2); }
.hf-value span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hf-value svg { flex: 0 0 auto; color: var(--muted); }

.hf-pop {
  position: absolute; z-index: 30; top: calc(100% - 2px); left: 74px; right: 0;
  display: grid; gap: 8px; padding: 10px;
  border-radius: 14px; background: var(--card);
  box-shadow: 0 16px 40px rgb(15 23 42 / 18%), 0 0 0 1px var(--line);
}
.hf-pop.is-target { gap: 2px; padding: 8px; }
.hf-pop.is-list { gap: 1px; padding: 8px; }
.hf-pop.is-calendar { left: auto; right: 0; width: 292px; padding: 4px; }
.hf-pop.is-remind { left: auto; right: 0; width: 132px; max-height: 240px; overflow-y: auto; gap: 1px; padding: 6px; }
.hf-pop-hint { margin: 0; padding: 0 2px; color: var(--muted); font-size: 12.5px; }

.hf-seg { display: flex; gap: 4px; padding: 3px; border-radius: 9px; background: var(--paper); }
.hf-seg button { flex: 1; padding: 6px 4px; border: 0; border-radius: 7px; background: transparent; color: var(--muted); font: inherit; font-size: 12.5px; cursor: pointer; }
.hf-seg button.on { background: var(--card); color: var(--priority-low, #4d78db); font-weight: 600; box-shadow: 0 1px 3px rgb(15 23 42 / 10%); }

.hf-week { display: flex; gap: 6px; }
.hf-week button { display: grid; flex: 1; height: 30px; place-items: center; border: 0; border-radius: 50%; background: var(--paper); color: var(--ink2); font: inherit; font-size: 12.5px; cursor: pointer; }
.hf-week button.on { background: var(--priority-low, #4d78db); color: #fff; font-weight: 600; }

.hf-line { display: flex; align-items: center; gap: 8px; color: var(--ink2); font-size: 13px; }
.hf-num { width: 62px; height: 34px; outline: none; padding: 0 8px; border: 1.5px solid var(--priority-low, #4d78db); border-radius: 8px; background: var(--card); color: var(--ink); font: inherit; font-size: 13px; text-align: center; }
.hf-num.is-sm { border-color: var(--line); }
.hf-unit { flex: 1; min-width: 0; height: 34px; outline: none; padding: 0 10px; border: 1px solid var(--line); border-radius: 8px; background: var(--paper); color: var(--ink); font: inherit; font-size: 13px; }
.hf-select { flex: 1; min-width: 0; height: 34px; outline: none; padding: 0 8px; border: 1px solid var(--line); border-radius: 8px; background: var(--paper); color: var(--ink2); font: inherit; font-size: 13px; }
.hf-quantity { display: grid; gap: 8px; padding: 8px 2px 6px; }

.hf-option { display: flex; align-items: center; justify-content: space-between; padding: 9px 10px; border: 0; border-radius: 8px; background: transparent; color: var(--ink2); font: inherit; font-size: 13.5px; text-align: left; cursor: pointer; }
.hf-option:hover { background: var(--paper); }
.hf-option.on { color: var(--priority-low, #4d78db); font-weight: 600; }
.hf-option.is-add { color: var(--muted); font-size: 13px; }

.hf-custom { display: grid; gap: 8px; padding: 8px 2px 2px; }
.hf-custom-input { height: 36px; outline: none; padding: 0 12px; border: 1.5px solid var(--priority-low, #4d78db); border-radius: 9px; background: var(--card); color: var(--ink); font: inherit; font-size: 13.5px; }
.hf-custom-foot { display: flex; gap: 8px; }
.hf-custom-foot button { flex: 1; height: 32px; border: 1px solid var(--line2); border-radius: 8px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer; }
.hf-custom-foot button.is-primary { border-color: var(--priority-low, #4d78db); background: var(--priority-low, #4d78db); color: #fff; }
.hf-custom-foot button:disabled { opacity: .45; cursor: default; }

.hf-pop-foot { display: flex; gap: 8px; padding-top: 2px; }
.hf-pop-foot button { flex: 1; height: 32px; border: 1px solid var(--line2); border-radius: 8px; background: var(--card); color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer; }
.hf-pop-foot button.is-primary { border-color: var(--priority-low, #4d78db); background: var(--priority-low, #4d78db); color: #fff; }

.hf-remind { display: flex; flex: 1; min-width: 0; flex-wrap: wrap; align-items: center; gap: 6px; }
.hf-chip { display: inline-flex; align-items: center; gap: 2px; padding: 4px 4px 4px 10px; border-radius: 8px; background: var(--paper); font-size: 12.5px; }
.hf-chip-time { padding: 0; border: 0; background: transparent; color: var(--ink2); font: inherit; font-size: 12.5px; cursor: pointer; }
.hf-chip-x { display: grid; width: 16px; height: 16px; place-items: center; border: 0; border-radius: 4px; background: transparent; color: var(--muted); cursor: pointer; }
.hf-chip-x:hover { background: var(--line); color: var(--ink); }
.hf-add { display: grid; width: 30px; height: 30px; place-items: center; border: 1px solid var(--line); border-radius: 8px; background: var(--paper); color: var(--muted); cursor: pointer; }

.hf-divider { height: 1px; margin: 10px 0 4px; background: var(--line); }
.hf-switch-row { display: flex; align-items: center; gap: 10px; padding: 10px 0 4px; color: var(--ink2); font-size: 13.5px; }
.hf-switch { position: relative; width: 38px; height: 22px; padding: 0; border: 0; border-radius: 999px; background: var(--line2); cursor: pointer; transition: background .16s ease; }
.hf-switch i { position: absolute; top: 2px; left: 2px; width: 18px; height: 18px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgb(15 23 42 / 22%); transition: transform .16s ease; }
.hf-switch.on { background: var(--priority-low, #4d78db); }
.hf-switch.on i { transform: translateX(16px); }

.hf-foot { display: flex; gap: 10px; padding: 12px 18px 16px; }
.hf-btn { flex: 1; height: 38px; border: 1px solid var(--line2); border-radius: 10px; background: transparent; color: var(--ink2); font: inherit; font-size: 14px; cursor: pointer; }
.hf-btn.is-primary { border-color: var(--priority-low, #4d78db); background: var(--priority-low, #4d78db); color: #fff; font-weight: 600; }
.hf-btn:disabled { opacity: .45; cursor: default; }

.hf-icon-pop {
  position: absolute; z-index: 40; top: 92px; left: -22px; right: -22px;
  display: grid; gap: 10px; padding: 12px;
  border-radius: 16px; background: var(--card);
  box-shadow: 0 20px 48px rgb(15 23 42 / 22%), 0 0 0 1px var(--line);
}
.hf-icon-preview { display: flex; align-items: center; gap: 12px; padding-bottom: 10px; border-bottom: 1px solid var(--line); }
.hf-icon-big { display: grid; width: 46px; height: 46px; place-items: center; border: 0; border-radius: 50%; font-size: 24px; line-height: 1; cursor: pointer; }
.hf-icon-big.is-text { border: 1px solid var(--line2); background: var(--card); color: var(--priority-low, #4d78db); font-size: 20px; font-weight: 600; }
.hf-icon-big.is-text.on { box-shadow: 0 0 0 2px var(--priority-low, #4d78db); }
.hf-icon-grid { display: grid; grid-template-columns: repeat(10, minmax(0, 1fr)); gap: 4px; max-height: 300px; overflow-y: auto; scrollbar-gutter: stable; padding: 2px 4px 2px 2px; }
.hf-icon-cell { display: grid; width: 100%; aspect-ratio: 1; place-items: center; border: 0; border-radius: 50%; font-size: 17px; line-height: 1; cursor: pointer; }
.hf-icon-cell.on { box-shadow: 0 0 0 2px var(--card), 0 0 0 4px var(--priority-low, #4d78db); }
</style>

@media (max-height: 720px) {
  .hf-body { overflow-y: auto; }
}
