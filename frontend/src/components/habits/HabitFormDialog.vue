<template>
  <Teleport to="body">
    <div v-if="open" class="hf-mask" @click.self="onMaskClick">
      <section ref="sheetEl" class="hf-sheet" role="dialog" aria-modal="true" :aria-label="habit ? '编辑习惯' : '添加习惯'" :style="{ '--pop-max': popMax }" @click.stop>
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
              <span>{{ frequencyLabel(draft) }}</span><ChevronDown :size="15" />
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
                  <button v-for="n in 7" :key="n" type="button" :class="{ on: Number(freq.count) === n }" @click="freq.count = n">{{ n }}</button>
                </div>
              </template>

              <template v-else>
                <div class="hf-line">
                  <span>每</span>
                  <input v-model="freq.interval" class="hf-num is-focus" inputmode="numeric" aria-label="间隔天数" @input="sanitizeDigit('interval', 999)" />
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
              <span>{{ targetLabel(draft) }}</span><ChevronDown :size="15" />
            </button>
            <div v-if="openPanel === 'target'" class="hf-pop is-target">
              <button type="button" class="hf-option" :class="{ on: target.type === 'CHECK' }" @click="target.type = 'CHECK'">
                当天完成打卡<Check v-if="target.type === 'CHECK'" :size="16" />
              </button>
              <button type="button" class="hf-option" :class="{ on: target.type === 'QUANTITY' }" @click="target.type = 'QUANTITY'">
                当天完成一定量<Check v-if="target.type === 'QUANTITY'" :size="16" />
              </button>

              <div v-if="target.type === 'QUANTITY'" class="hf-subcard">
                <div class="hf-line">
                  <span>每天</span>
                  <input v-model="target.times" class="hf-num is-focus" inputmode="numeric" aria-label="每天目标量" @input="sanitizeDigit('times', 999)" />
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
                  <input v-model="target.per" class="hf-num" inputmode="numeric" aria-label="每次记录量" @input="sanitizeDigit('per', 99999)" />
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
              <span>{{ startText }}</span><ChevronDown :size="15" />
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
            <button type="button" class="hf-value" :aria-expanded="openPanel === 'goal' || openPanel === 'goalCustom'" @click="togglePanel('goal')">
              <span>{{ goalText }}</span><ChevronDown :size="15" />
            </button>
            <div v-if="openPanel === 'goal' || openPanel === 'goalCustom'" class="hf-pop is-list" :class="{ 'is-up': popUp }">
              <button v-for="option in GOAL_PRESETS" :key="option.value" type="button" class="hf-option" :class="{ on: Number(draft.goalDays || 0) === option.value }" @click="pickGoal(option.value)">
                {{ option.label }}<Check v-if="Number(draft.goalDays || 0) === option.value" :size="16" />
              </button>
              <button type="button" class="hf-option" :class="{ on: customGoalActive }" @click="startCustomGoal">
                自定义<Check v-if="customGoalActive" :size="16" />
              </button>
              <div v-if="openPanel === 'goalCustom'" class="hf-subcard">
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
            <button type="button" class="hf-value" :aria-expanded="openPanel === 'group' || openPanel === 'groupAdd'" @click="togglePanel('group')">
              <span>{{ groupLabel(draft.groupKey, customGroups) }}</span><ChevronDown :size="15" />
            </button>
            <div v-if="openPanel === 'group' || openPanel === 'groupAdd'" class="hf-pop is-list" :class="{ 'is-up': popUp }">
              <button v-for="group in allGroups" :key="group.key" type="button" class="hf-option" :class="{ on: draft.groupKey === group.key }" @click="pickGroup(group.key)">
                {{ group.label }}<Check v-if="draft.groupKey === group.key" :size="16" />
              </button>
              <button type="button" class="hf-option is-add" @click="startAddGroup">添加分组</button>
              <div v-if="openPanel === 'groupAdd'" class="hf-subcard">
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
              <button type="button" class="hf-add" aria-label="添加提醒" :aria-expanded="openPanel === 'remind'" @click="togglePanel('remind')"><Plus :size="16" /></button>
              <div v-if="openPanel === 'remind'" class="hf-pop is-remind" :class="{ 'is-up': popUp }">
                <button v-for="time in REMIND_PRESETS" :key="time" type="button" class="hf-option" :class="{ on: times.includes(time) }" @click="pickRemind(time)">
                  {{ time }}<Check v-if="times.includes(time)" :size="16" />
                </button>
              </div>
            </div>
          </div>

          <div class="hf-divider" />

          <button type="button" class="hf-switch-row" role="switch" :aria-checked="draft.autoPopupLog" @click="draft.autoPopupLog = !draft.autoPopupLog">
            <span class="hf-switch" :class="{ on: draft.autoPopupLog }"><i /></span>
            <span>自动弹出打卡日志</span>
          </button>
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
  presetGroup: { type: String, default: '' },
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
const sheetEl = ref(null)
const popUp = ref(false)
const popMax = ref('none')
const allGroups = computed(() => [...GROUPS, ...customGroups.value])
const iconStyle = computed(() => ({
  background: draft.color ? `color-mix(in srgb, ${draft.color} 78%, #fff)` : 'var(--paper)',
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
    groupKey: source?.groupKey || props.presetGroup || 'other',
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
  placePanel()
}

/**
 * 浮层默认贴着触发按钮往下弹；下方空间不够就翻上去，
 * 并把高度限制在可用空间内（内部滚动），避免整块白卡顶出弹窗边缘。
 */
async function placePanel() {
  popUp.value = false
  popMax.value = 'none'
  if (!openPanel.value) return
  await nextTick()
  const sheet = sheetEl.value?.getBoundingClientRect()
  const trigger = sheetEl.value?.querySelector('.hf-field [aria-expanded="true"]')
  if (!sheet || !trigger) return
  const rect = trigger.getBoundingClientRect()
  const below = Math.round(sheet.bottom - rect.bottom - 12)
  const above = Math.round(rect.top - sheet.top - 12)
  if (below >= 300 || above <= below) {
    popMax.value = `${Math.max(180, below)}px`
    return
  }
  popUp.value = true
  popMax.value = `${Math.max(180, above)}px`
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
  // 遮罩交给 onMaskClick 处理（要区分「先收浮层」和「关整个弹窗」）
  if (targetEl.classList?.contains('hf-mask')) return
  // 点在这层弹出的内容里、或点在「当前打开那个」的触发按钮上，都不算点到外面
  if (targetEl.closest?.('.hf-pop') || targetEl.closest?.('.hf-icon-pop') || targetEl.closest?.('[aria-expanded="true"]')) return
  openPanel.value = ''
}

/** 浮层开着时点遮罩只收浮层，再点一次才关整个弹窗 */
function onMaskClick() {
  if (openPanel.value) {
    openPanel.value = ''
    return
  }
  emit('close')
}

function onKeydown(event) {
  if (!props.open || event.key !== 'Escape') return
  if (openPanel.value) {
    openPanel.value = ''
    return
  }
  emit('close')
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocPointerDown, true)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocPointerDown, true)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.hf-mask { position: fixed; z-index: 170; inset: 0; display: grid; place-items: center; padding: 20px; background: rgb(24 30 40 / 32%); }
.hf-sheet {
  position: relative;
  display: flex; flex-direction: column;
  width: min(452px, calc(100vw - 24px));
  max-height: calc(100dvh - 40px);
  border-radius: 16px;
  background: var(--card);
  box-shadow: 0 24px 60px rgb(15 23 42 / 24%);
}
.hf-dot { position: absolute; top: 14px; left: 14px; width: 10px; height: 10px; border-radius: 50%; background: #f2706a; }
.hf-head { padding: 18px 20px 8px; }
.hf-head h2 { margin: 0; font-size: 16.5px; font-weight: 600; letter-spacing: -.01em; text-align: center; }

.hf-body { display: flex; flex-direction: column; padding: 2px 20px 0; }

/* 图标 + 名称 */
.hf-top { display: flex; align-items: center; gap: 16px; padding: 8px 0 16px; }
.hf-icon-wrap { position: relative; }
.hf-icon { display: grid; width: 52px; height: 52px; place-items: center; border: 0; border-radius: 50%; font-size: 26px; line-height: 1; cursor: pointer; }
.hf-icon-pen { position: absolute; right: 1px; bottom: 1px; display: grid; width: 17px; height: 17px; place-items: center; border-radius: 50%; background: var(--card); color: var(--muted); box-shadow: 0 0 0 1px var(--line); }
.hf-name {
  flex: 1; min-width: 0; height: 48px; padding: 0 16px;
  border: 1.5px solid #3d8fce; border-radius: 10px;
  background: var(--card); color: var(--ink); font: inherit; font-size: 15px; font-weight: 600;
}
.hf-name:focus { outline: none; box-shadow: 0 0 0 3px rgb(61 143 206 / 14%); }

/* 表单行：左标签 78px，右控件 48px */
.hf-field { position: relative; display: flex; align-items: center; gap: 14px; padding: 7px 0; }
.hf-label { display: flex; flex: 0 0 80px; align-items: center; gap: 3px; color: var(--ink2); font-size: 13.5px; }
.hf-help { color: var(--dim, var(--muted)); }
.hf-value {
  display: flex; flex: 1; min-width: 0; align-items: center; justify-content: space-between; gap: 8px;
  height: 48px; padding: 0 16px;
  border: 1px solid var(--line2); border-radius: 10px;
  background: var(--card); color: var(--ink);
  font: inherit; font-size: 14px; text-align: left; cursor: pointer;
  transition: border-color .14s ease;
}
.hf-value:hover { border-color: color-mix(in srgb, var(--ink) 22%, var(--line2)); }
.hf-value span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hf-value svg { flex: 0 0 auto; color: var(--dim, var(--muted)); }

.hf-pop {
  position: absolute; z-index: 30; top: calc(100% - 1px); left: 94px; right: 0;
  display: grid; gap: 10px; padding: 12px;
  border-radius: 12px; background: var(--card);
  box-shadow: 0 18px 44px rgb(15 23 42 / 18%), 0 0 0 1px color-mix(in srgb, var(--ink) 8%, transparent);
}
.hf-pop.is-target { gap: 2px; padding: 8px; }
.hf-pop.is-list { gap: 1px; padding: 8px; }
.hf-pop.is-calendar { left: auto; right: 0; width: 296px; padding: 4px; }
.hf-pop.is-remind { left: auto; right: 0; width: 138px; gap: 1px; padding: 6px; }
.hf-pop.is-up { top: auto; bottom: calc(100% - 1px); }
.hf-pop { max-height: var(--pop-max, none); }
.hf-pop:not(.is-calendar) { overflow-y: auto; scrollbar-width: thin; }
.hf-pop-hint { margin: 0; padding: 0 2px; color: var(--muted); font-size: 12.5px; }

/* 三页签：选中白底蓝字带阴影，未选浅灰底 */
.hf-seg { display: flex; gap: 6px; }
.hf-seg button {
  padding: 8px 14px; border: 1px solid transparent; border-radius: 8px;
  background: color-mix(in srgb, var(--ink) 5%, transparent); color: var(--ink2);
  font: inherit; font-size: 13px; cursor: pointer;
}
.hf-seg button.on { border-color: var(--line); background: var(--card); color: #3d8fce; font-weight: 600; box-shadow: 0 1px 3px rgb(15 23 42 / 10%); }

.hf-week { display: flex; gap: 8px; }
.hf-week button {
  display: grid; width: 36px; height: 36px; place-items: center;
  border: 0; border-radius: 50%;
  background: color-mix(in srgb, var(--ink) 5%, transparent); color: var(--ink2);
  font: inherit; font-size: 13px; cursor: pointer;
}
.hf-week button.on { background: #3d8fce; color: #fff; font-weight: 600; }

.hf-line { display: flex; align-items: center; gap: 8px; color: var(--ink2); font-size: 13.5px; }
.hf-num {
  width: 66px; height: 36px; outline: none; padding: 0 8px;
  border: 1px solid var(--line2); border-radius: 8px; background: var(--card);
  color: var(--ink); font: inherit; font-size: 13.5px; text-align: center;
}
.hf-num.is-focus { border: 1.5px solid #3d8fce; }
.hf-unit { flex: 1; min-width: 0; height: 36px; outline: none; padding: 0 12px; border: 1px solid var(--line2); border-radius: 8px; background: color-mix(in srgb, var(--ink) 4%, transparent); color: var(--ink); font: inherit; font-size: 13.5px; }
.hf-select { flex: 1; min-width: 0; height: 36px; outline: none; padding: 0 10px; border: 1px solid var(--line2); border-radius: 8px; background: var(--card); color: var(--ink2); font: inherit; font-size: 13.5px; }

/* 「当天完成一定量」的二级面板 */
.hf-subcard { display: grid; gap: 10px; margin-top: 6px; padding: 12px; border-radius: 10px; background: color-mix(in srgb, var(--ink) 3.5%, transparent); }

.hf-option {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  padding: 9px 10px; border: 0; border-radius: 8px;
  background: transparent; color: var(--ink2); font: inherit; font-size: 14px; text-align: left; cursor: pointer;
}
.hf-option:hover { background: color-mix(in srgb, var(--ink) 5%, transparent); }
.hf-option.on { color: #3d8fce; font-weight: 600; }
.hf-option.is-add { color: var(--muted); }

.hf-custom { display: grid; gap: 8px; padding: 8px 2px 2px; }
.hf-custom-input { height: 40px; outline: none; padding: 0 14px; border: 1.5px solid #3d8fce; border-radius: 9px; background: var(--card); color: var(--ink); font: inherit; font-size: 14px; }
.hf-custom-foot { display: flex; gap: 10px; }
.hf-custom-foot button { flex: 1; height: 36px; border: 1px solid var(--line2); border-radius: 8px; background: var(--card); color: var(--ink2); font: inherit; font-size: 13.5px; cursor: pointer; }
.hf-custom-foot button.is-primary { border-color: #3d8fce; background: #3d8fce; color: #fff; }
.hf-custom-foot button:disabled { opacity: .45; cursor: default; }

.hf-pop-foot { display: flex; gap: 10px; padding-top: 4px; }
.hf-pop-foot button { flex: 1; height: 38px; border: 1px solid var(--line2); border-radius: 8px; background: var(--card); color: var(--ink2); font: inherit; font-size: 14px; cursor: pointer; }
.hf-pop-foot button.is-primary { border-color: #3d8fce; background: #3d8fce; color: #fff; font-weight: 600; }

.hf-remind { display: flex; flex: 1; min-width: 0; flex-wrap: wrap; align-items: center; gap: 8px; }
.hf-chip { display: inline-flex; align-items: center; gap: 2px; height: 36px; padding: 0 6px 0 12px; border-radius: 8px; background: color-mix(in srgb, var(--ink) 5%, transparent); }
.hf-chip-time { padding: 0; border: 0; background: transparent; color: var(--ink2); font: inherit; font-size: 13.5px; cursor: pointer; }
.hf-chip-x { display: grid; width: 18px; height: 18px; place-items: center; border: 0; border-radius: 5px; background: transparent; color: var(--muted); cursor: pointer; }
.hf-chip-x:hover { background: var(--line); color: var(--ink); }
.hf-add { display: grid; width: 48px; height: 48px; place-items: center; border: 0; border-radius: 10px; background: color-mix(in srgb, var(--ink) 6%, transparent); color: var(--ink2); cursor: pointer; }
.hf-add:hover { background: color-mix(in srgb, var(--ink) 10%, transparent); }

.hf-divider { height: 1px; margin: 14px 0 4px; background: var(--line); }
.hf-switch-row { display: flex; align-items: center; gap: 12px; padding: 12px 0 6px; border: 0; background: transparent; color: var(--ink); font: inherit; font-size: 14px; text-align: left; cursor: pointer; }
.hf-switch { position: relative; width: 42px; height: 24px; flex: 0 0 auto; border-radius: 999px; background: var(--line2); transition: background .16s ease; }
.hf-switch i { position: absolute; top: 2px; left: 2px; width: 20px; height: 20px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgb(15 23 42 / 22%); transition: transform .16s ease; }
.hf-switch.on { background: #3d8fce; }
.hf-switch.on i { transform: translateX(18px); }

.hf-foot { display: flex; gap: 12px; padding: 14px 20px 18px; }
.hf-btn { flex: 1; height: 44px; border: 1px solid var(--line2); border-radius: 10px; background: var(--card); color: var(--ink); font: inherit; font-size: 14.5px; cursor: pointer; }
.hf-btn.is-primary { border-color: #3d8fce; background: #3d8fce; color: #fff; font-weight: 600; }
.hf-btn.is-primary:hover:not(:disabled) { background: #3480ba; }
.hf-btn:disabled { opacity: .45; cursor: default; }

/* 图标选择弹层：大预览 + 文字图标 + 10 列彩色圆形网格 */
.hf-icon-pop {
  position: absolute; z-index: 40; top: 88px; left: -16px; right: -16px;
  display: grid; gap: 12px; padding: 14px;
  border-radius: 16px; background: var(--card);
  box-shadow: 0 22px 52px rgb(15 23 42 / 22%), 0 0 0 1px color-mix(in srgb, var(--ink) 8%, transparent);
}
.hf-icon-preview { display: flex; align-items: center; gap: 14px; padding-bottom: 12px; border-bottom: 1px solid var(--line); }
.hf-icon-big { display: grid; width: 60px; height: 60px; place-items: center; border: 0; border-radius: 50%; font-size: 31px; line-height: 1; cursor: pointer; }
.hf-icon-big.is-text { border: 1px solid var(--line2); background: var(--card); color: #3d8fce; font-size: 24px; font-weight: 600; }
.hf-icon-big.is-text.on { box-shadow: 0 0 0 2px #3d8fce; }
.hf-icon-grid { display: grid; grid-template-columns: repeat(10, minmax(0, 1fr)); gap: 6px; max-height: 258px; overflow-y: auto; scrollbar-gutter: stable; padding: 2px 6px 2px 2px; }
.hf-icon-cell { display: grid; width: 100%; aspect-ratio: 1; place-items: center; border: 0; border-radius: 50%; font-size: 18px; line-height: 1; cursor: pointer; transition: transform .1s ease; }
.hf-icon-cell:hover { transform: scale(1.08); }
.hf-icon-cell.on { box-shadow: 0 0 0 2px var(--card), 0 0 0 4px #3d8fce; }
</style>

<style>
@media (max-height: 720px) {
  .hf-body { overflow-y: auto; }
}
</style>
