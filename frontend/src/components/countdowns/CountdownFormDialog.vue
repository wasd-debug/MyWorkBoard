<template>
  <Teleport to="body">
    <div v-if="open" class="cf-mask" role="dialog" aria-modal="true" :aria-label="editing ? '编辑倒数纪念日' : '添加倒数纪念日'" @click.self="emit('close')">
      <section class="cf-sheet" @click.stop>
        <button type="button" class="cf-dot" aria-label="关闭" title="关闭" @click="emit('close')"></button>
        <h2 class="cf-title">{{ step === 1 ? (editing ? '编辑' : '添加') : '样式' }}</h2>

        <div v-if="step === 1" class="cf-body">
          <div class="cf-head">
            <button type="button" class="cf-icon" :style="{ background: form.iconColor }" aria-label="选择图标" title="选择图标" @click="iconOpen = true">
              <span>{{ form.iconValue }}</span>
              <i class="cf-icon-pen"><Pencil :size="10" /></i>
            </button>
            <div class="cf-name">
              <input
                ref="titleInput"
                v-model.trim="form.title"
                class="cf-name-input"
                maxlength="40"
                placeholder="名称"
                aria-label="名称"
                @keydown.enter.prevent="next"
              />
              <span class="cf-bookmark" aria-hidden="true"><Bookmark :size="17" /></span>
            </div>
          </div>

          <div class="cf-rows">
            <div class="cf-row">
              <span class="cf-label">日期</span>
              <button ref="dateButton" type="button" class="cf-control" :class="{ 'is-open': openPanel === 'date' }" :aria-expanded="openPanel === 'date'" @click="togglePanel('date', $event)">
                <span :class="{ 'is-placeholder': !form.targetDate }">{{ dateText }}</span>
                <ChevronDown :size="15" />
              </button>
              <div v-if="openPanel === 'date'" class="cf-pop is-date" :class="{ 'is-up': popUp }" @click.stop>
                <div class="cf-seg" role="tablist">
                  <button type="button" :class="{ on: dateTab === 'SOLAR' }" @click="switchDateTab('SOLAR')">公历</button>
                  <button type="button" :class="{ on: dateTab === 'LUNAR' }" @click="switchDateTab('LUNAR')">农历</button>
                </div>
                <div v-if="dateTab === 'SOLAR'" class="cf-date-row">
                  <select v-model.number="solar.year" class="cf-box" aria-label="年">
                    <option v-for="year in solarYears" :key="year" :value="year">{{ year }}年</option>
                  </select>
                  <select v-model.number="solar.month" class="cf-box" aria-label="月">
                    <option v-for="month in 12" :key="month" :value="month">{{ month }}月</option>
                  </select>
                  <select v-model.number="solar.day" class="cf-box" aria-label="日">
                    <option v-for="day in solarDays" :key="day" :value="day">{{ day }}日</option>
                  </select>
                </div>
                <div v-else class="cf-date-row">
                  <select v-model.number="lunar.year" class="cf-box is-line" aria-label="农历年">
                    <option v-for="year in lunarYears" :key="year" :value="year">{{ yearLabel(year) }}</option>
                  </select>
                  <select v-model="lunarMonthKey" class="cf-box is-line" aria-label="农历月">
                    <option v-for="option in lunarMonths" :key="`${option.value}-${option.leap}`" :value="`${option.value}-${option.leap ? 1 : 0}`">{{ option.label }}</option>
                  </select>
                  <select v-model.number="lunar.day" class="cf-box is-line" aria-label="农历日">
                    <option v-for="option in lunarDays" :key="option.value" :value="option.value">{{ option.label }}</option>
                  </select>
                </div>
                <p v-if="dateTab === 'LUNAR' && !lunarSolar" class="cf-tip">该农历日不存在，请调整月份或日期</p>
                <div class="cf-pop-foot">
                  <button type="button" class="cf-btn" @click="openPanel = ''">取消</button>
                  <button type="button" class="cf-btn is-primary" :disabled="dateTab === 'LUNAR' && !lunarSolar" @click="confirmDate">确定</button>
                </div>
              </div>
            </div>

            <div class="cf-row">
              <span class="cf-label">提醒</span>
              <button type="button" class="cf-control" :class="{ 'is-open': openPanel === 'remind' }" :aria-expanded="openPanel === 'remind'" @click="togglePanel('remind', $event)">
                <span>{{ reminderText({ remind: remindDraft.join(',') }) }}</span>
                <ChevronDown :size="15" />
              </button>
              <div v-if="openPanel === 'remind'" class="cf-pop" :class="{ 'is-up': popUp }" @click.stop>
                <div class="cf-list">
                  <button type="button" :class="{ on: !remindDraft.length }" @click="remindDraft = []">
                    无<Check v-if="!remindDraft.length" :size="17" />
                  </button>
                  <button
                    v-for="preset in REMIND_PRESETS"
                    :key="preset.value"
                    type="button"
                    :class="{ on: remindDraft.includes(preset.value) }"
                    @click="toggleRemind(preset.value)"
                  >
                    {{ preset.label }} <em>({{ preset.time }})</em><Check v-if="remindDraft.includes(preset.value)" :size="17" />
                  </button>
                </div>
                <div class="cf-custom">
                  <span>自定义</span>
                  <input v-model="customRemind" inputmode="numeric" maxlength="3" placeholder="提前天数" aria-label="自定义提前天数" @keydown.enter.prevent="addCustomRemind" />
                  <button type="button" class="cf-btn is-mini" @click="addCustomRemind">添加</button>
                </div>
                <div class="cf-pop-foot">
                  <button type="button" class="cf-btn" @click="cancelRemind">取消</button>
                  <button type="button" class="cf-btn is-primary" @click="confirmRemind">确定</button>
                </div>
              </div>
            </div>

            <div class="cf-row">
              <span class="cf-label">重复</span>
              <button type="button" class="cf-control" :class="{ 'is-open': openPanel === 'repeat' }" :aria-expanded="openPanel === 'repeat'" @click="togglePanel('repeat', $event)">
                <span>{{ repeatText({ repeatRule: form.repeatRule, targetDate: form.targetDate }) }}</span>
                <ChevronDown :size="15" />
              </button>
              <div v-if="openPanel === 'repeat'" class="cf-pop" :class="{ 'is-up': popUp }" @click.stop>
                <div class="cf-list">
                  <button
                    v-for="rule in repeatOptions"
                    :key="rule.key"
                    type="button"
                    :class="{ on: repeatKey === rule.key }"
                    @click="pickRepeat(rule.key)"
                  >
                    {{ rule.label }}<Check v-if="repeatKey === rule.key" :size="17" />
                  </button>
                </div>
                <div v-if="repeatKey === 'CUSTOM'" class="cf-custom">
                  <span>每隔</span>
                  <input v-model="customRepeat" inputmode="numeric" maxlength="3" placeholder="天数" aria-label="自定义重复天数" />
                  <span>天</span>
                </div>
                <div class="cf-pop-foot">
                  <button type="button" class="cf-btn" @click="openPanel = ''">取消</button>
                  <button type="button" class="cf-btn is-primary" @click="confirmRepeat">确定</button>
                </div>
              </div>
            </div>

            <div class="cf-row">
              <span class="cf-label">类型</span>
              <button type="button" class="cf-control" :class="{ 'is-open': openPanel === 'type' }" :aria-expanded="openPanel === 'type'" @click="togglePanel('type', $event)">
                <span>{{ typeLabel }}</span>
                <ChevronDown :size="15" />
              </button>
              <div v-if="openPanel === 'type'" class="cf-pop" :class="{ 'is-up': popUp }" @click.stop>
                <div class="cf-list">
                  <button v-for="type in COUNTDOWN_TYPES" :key="type.key" type="button" :class="{ on: form.kind === type.key }" @click="pickType(type)">
                    {{ type.label }}<Check v-if="form.kind === type.key" :size="17" />
                  </button>
                </div>
              </div>
            </div>

            <div class="cf-row">
              <span class="cf-label">显示</span>
              <button type="button" class="cf-control" :class="{ 'is-open': openPanel === 'display' }" :aria-expanded="openPanel === 'display'" @click="togglePanel('display', $event)">
                <span>{{ displayText(form) }}</span>
                <ChevronDown :size="15" />
              </button>
              <div v-if="openPanel === 'display'" class="cf-pop" :class="{ 'is-up': popUp }" @click.stop>
                <p class="cf-group-title">在智能清单中</p>
                <div class="cf-list">
                  <button v-for="rule in DISPLAY_RULES" :key="rule.key" type="button" :class="{ on: form.displayRule === rule.key }" @click="pickDisplay(rule.key)">
                    {{ rule.label }}<Check v-if="form.displayRule === rule.key" :size="17" />
                  </button>
                </div>
              </div>
            </div>
          </div>

          <p v-if="error" class="cf-error" role="alert">{{ error }}</p>
        </div>

        <div v-else class="cf-body is-style">
          <CountdownStylePanel v-model="styleDraft" :item="form" :today="today" />
        </div>

        <footer class="cf-foot">
          <template v-if="step === 1">
            <button type="button" class="cf-btn" @click="emit('close')">取消</button>
            <button type="button" class="cf-btn is-primary" :disabled="!canSave || saving" @click="next">
              {{ saving ? '保存中…' : editing ? '保存' : '下一步' }}
            </button>
          </template>
          <template v-else>
            <button type="button" class="cf-btn" @click="step = 1">上一步</button>
            <button type="button" class="cf-btn is-primary" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
          </template>
        </footer>

        <CountdownIconPicker
          :open="iconOpen"
          :model-value="{ type: form.iconType, value: form.iconValue, color: form.iconColor }"
          @close="iconOpen = false"
          @submit="applyIcon"
        />
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { Bookmark, Check, ChevronDown, Pencil } from 'lucide-vue-next'
import CountdownStylePanel from './CountdownStylePanel.vue'
import CountdownIconPicker from './CountdownIconPicker.vue'
import {
  COUNTDOWN_TYPES, DISPLAY_RULES, REMIND_PRESETS, defaultCountdown, displayText, repeatText,
  reminderText, todayKey, typeOf
} from './countdownUtils.js'
import {
  dayLabel, decodeLunar, encodeLunar, lunarDayOptions, lunarMonthOptions, lunarToSolar, monthLabel, solarToLunar, yearLabel
} from './lunar.js'

const props = defineProps({
  open: Boolean,
  item: { type: Object, default: null },
  initialType: { type: String, default: 'ANNIVERSARY' },
  saving: Boolean,
  error: { type: String, default: '' }
})
const emit = defineEmits(['close', 'submit'])

const step = ref(1)
const editing = ref(false)
const form = reactive(defaultCountdown())
const styleDraft = ref({})
const iconOpen = ref(false)
const openPanel = ref('')
const popUp = ref(false)
const today = todayKey()
const titleInput = ref(null)

const solarYears = Array.from({ length: 152 }, (_, index) => 1950 + index)
const solar = reactive({ year: new Date().getFullYear(), month: new Date().getMonth() + 1, day: new Date().getDate() })
const lunar = reactive({ year: new Date().getFullYear(), month: 1, day: 1, leap: false })
const dateTab = ref('SOLAR')
const remindDraft = ref([])
const customRemind = ref('')
const customRepeat = ref('')
const repeatKey = ref('NONE')

const solarDays = computed(() => new Date(solar.year, solar.month, 0).getDate())
watch(solarDays, days => { if (solar.day > days) solar.day = days })

const lunarYears = computed(() => {
  const current = solarToLunar(today)?.year || new Date().getFullYear()
  return Array.from({ length: 121 }, (_, index) => current - 100 + index)
})
const lunarMonths = computed(() => lunarMonthOptions(lunar.year))
const lunarDays = computed(() => lunarDayOptions(lunar.year, lunar.month, lunar.leap))
const lunarMonthKey = computed({
  get: () => `${lunar.month}-${lunar.leap ? 1 : 0}`,
  set: value => {
    const [month, leap] = String(value).split('-').map(Number)
    lunar.month = month
    lunar.leap = Boolean(leap)
    const max = lunarDayOptions(lunar.year, month, lunar.leap).length
    if (lunar.day > max) lunar.day = max
  }
})
const lunarSolar = computed(() => lunarToSolar(lunar.year, lunar.month, lunar.day, lunar.leap))

const repeatOptions = computed(() => {
  const source = new Date(form.targetDate || today)
  const safe = Number.isNaN(source.getTime()) ? new Date(today) : source
  const weekday = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][safe.getDay()]
  return [
    { key: 'NONE', label: '无' },
    { key: 'DAILY', label: '每天' },
    { key: 'WEEKLY', label: `每周（${weekday}）` },
    { key: 'MONTHLY', label: `每月（${safe.getDate()}日）` },
    { key: 'YEARLY', label: `每年（${safe.getMonth() + 1}月${safe.getDate()}日）` },
    { key: 'CUSTOM', label: '自定义' }
  ]
})

const dateText = computed(() => {
  if (form.calendarType === 'LUNAR' && form.lunarDate) {
    const parsed = decodeLunar(form.lunarDate)
    if (parsed) return `农历 ${monthLabel(parsed.month, parsed.leap)}${dayLabel(parsed.day)}`
  }
  return form.targetDate || '选择日期'
})
const typeLabel = computed(() => (COUNTDOWN_TYPES.find(item => item.key === form.kind) || COUNTDOWN_TYPES[0]).label)
const canSave = computed(() => Boolean(form.title && form.targetDate))

watch([() => form.calendarType, () => form.lunarDate, () => form.targetDate], () => {
  if (form.calendarType !== 'LUNAR' || !form.lunarDate) return
  const parsed = decodeLunar(form.lunarDate)
  if (!parsed) return
  lunar.month = parsed.month
  lunar.day = parsed.day
  lunar.leap = parsed.leap
  const year = solarToLunar(form.targetDate)?.year
  if (year) lunar.year = year
})

watch(() => props.open, async open => {
  if (!open) return
  openPanel.value = ''
  step.value = 1
  editing.value = Boolean(props.item)
  const source = props.item
  const base = defaultCountdown(source ? typeOf(source).key : props.initialType)
  const clean = source ? Object.fromEntries(Object.entries(source).filter(([key]) => !['id', 'publicId', 'revision', 'deleted', 'deletedAt', 'updatedAt', 'extra'].includes(key))) : {}
  Object.assign(form, base, { targetDate: source ? source.targetDate : '' }, source ? {
    ...clean,
    ...source,
    kind: source.kind || base.kind,
    remind: source.remind || '',
    repeatRule: source.repeatRule || (source.repeatYearly ? 'YEARLY' : base.repeatRule),
    iconType: source.iconType || base.iconType,
    iconValue: source.iconValue || base.iconValue,
    iconColor: source.iconColor || base.iconColor
  } : {})
  form.coverImage = source?.coverImage || null
  styleDraft.value = {
    cover: form.cover,
    coverImage: form.coverImage,
    color: form.color,
    iconType: form.iconType,
    iconValue: form.iconValue,
    iconColor: form.iconColor,
    unit: form.unit,
    preciseSeconds: Boolean(form.preciseSeconds)
  }
  const target = new Date(form.targetDate || today)
  solar.year = target.getFullYear()
  solar.month = target.getMonth() + 1
  solar.day = target.getDate()
  const lunarSource = solarToLunar(form.targetDate || today)
  if (lunarSource) {
    lunar.year = lunarSource.year
    lunar.month = lunarSource.month
    lunar.day = lunarSource.day
    lunar.leap = lunarSource.leap
  }
  dateTab.value = form.calendarType === 'LUNAR' ? 'LUNAR' : 'SOLAR'
  remindDraft.value = String(form.remind || '').split(',').map(value => value.trim()).filter(Boolean).map(Number).filter(Number.isFinite)
  repeatKey.value = String(form.repeatRule || 'NONE').startsWith('CUSTOM') ? 'CUSTOM' : form.repeatRule || 'NONE'
  customRepeat.value = String(form.repeatRule || '').startsWith('CUSTOM:') ? form.repeatRule.split(':')[1] : ''
  customRemind.value = ''
  await nextTick()
  titleInput.value?.focus()
})

/** 浮层：靠近底部时向上展开 */
function togglePanel(name, event) {
  const next = openPanel.value === name ? '' : name
  openPanel.value = next
  if (!next) return
  const rect = event.currentTarget.getBoundingClientRect()
  popUp.value = rect.bottom + 340 > window.innerHeight
}

function switchDateTab(tab) {
  dateTab.value = tab
  if (tab === 'LUNAR') {
    const source = solarToLunar(new Date(solar.year, solar.month - 1, solar.day))
    if (source) {
      lunar.year = source.year
      lunar.month = source.month
      lunar.day = source.day
      lunar.leap = source.leap
    }
  }
}

function confirmDate() {
  if (dateTab.value === 'SOLAR') {
    const key = `${solar.year}-${String(solar.month).padStart(2, '0')}-${String(solar.day).padStart(2, '0')}`
    form.targetDate = key
    form.calendarType = 'SOLAR'
    form.lunarDate = null
  } else {
    const date = lunarSolar.value
    if (!date) return
    form.targetDate = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
    form.calendarType = 'LUNAR'
    form.lunarDate = encodeLunar(lunar.month, lunar.day, lunar.leap)
  }
  openPanel.value = ''
}

function toggleRemind(value) {
  const index = remindDraft.value.indexOf(value)
  if (index >= 0) remindDraft.value.splice(index, 1)
  else remindDraft.value = [...remindDraft.value, value].sort((a, b) => a - b)
}
function addCustomRemind() {
  const value = Number(customRemind.value)
  if (!Number.isFinite(value) || value < 0 || value > 365) return
  if (!remindDraft.value.includes(value)) remindDraft.value = [...remindDraft.value, value].sort((a, b) => a - b)
  customRemind.value = ''
}
function cancelRemind() {
  remindDraft.value = String(form.remind || '').split(',').map(value => value.trim()).filter(Boolean).map(Number)
  openPanel.value = ''
}
function confirmRemind() {
  form.remind = remindDraft.value.join(',')
  openPanel.value = ''
}

function pickRepeat(key) {
  repeatKey.value = key
  if (key !== 'CUSTOM') {
    form.repeatRule = key
    openPanel.value = ''
  }
}
function confirmRepeat() {
  if (repeatKey.value === 'CUSTOM') {
    const every = Number(customRepeat.value)
    if (!Number.isFinite(every) || every < 1 || every > 365) return
    form.repeatRule = `CUSTOM:${every}`
  } else {
    form.repeatRule = repeatKey.value
  }
  openPanel.value = ''
}

function pickType(type) {
  form.kind = type.key
  if (!form.iconValue || COUNTDOWN_TYPES.some(item => item.icon === form.iconValue)) {
    form.iconValue = type.icon
    form.iconColor = type.color
  }
  if (type.key === 'BIRTHDAY' && form.repeatRule === 'NONE') form.repeatRule = 'YEARLY'
  openPanel.value = ''
}
function pickDisplay(key) {
  form.displayRule = key
  openPanel.value = ''
}

function applyIcon(icon) {
  form.iconType = icon.type
  form.iconValue = icon.value
  form.iconColor = icon.color
  iconOpen.value = false
}

function next() {
  if (!canSave.value) return
  if (editing.value) {
    submit()
    return
  }
  styleDraft.value = {
    ...styleDraft.value,
    cover: form.cover,
    coverImage: form.coverImage,
    color: form.color,
    iconType: form.iconType,
    iconValue: form.iconValue,
    iconColor: form.iconColor
  }
  step.value = 2
}

function submit() {
  if (!canSave.value) return
  const style = step.value === 2 ? styleDraft.value : {}
  const payload = {
    ...form,
    ...style,
    kind: form.kind,
    targetDate: form.targetDate,
    calendarType: form.calendarType,
    lunarDate: form.lunarDate,
    repeatRule: form.repeatRule,
    repeatYearly: String(form.repeatRule || '').startsWith('YEARLY') || form.kind === 'BIRTHDAY',
    coverImage: (style.coverImage ?? form.coverImage) || null
  }
  emit('submit', payload)
}

function onGlobalPointerDown(event) {
  if (!openPanel.value) return
  if (event.target.closest('.cf-row')) return
  openPanel.value = ''
}
watch(openPanel, open => {
  if (typeof document === 'undefined') return
  if (open) document.addEventListener('pointerdown', onGlobalPointerDown, true)
  else document.removeEventListener('pointerdown', onGlobalPointerDown, true)
})
onBeforeUnmount(() => {
  if (typeof document !== 'undefined') document.removeEventListener('pointerdown', onGlobalPointerDown, true)
})
</script>

<style scoped>
.cf-mask{position:fixed;inset:0;z-index:88;display:grid;place-items:center;padding:24px;background:rgba(20,24,32,.3)}
.cf-sheet{position:relative;display:flex;width:min(600px,100%);max-height:min(92vh,860px);flex-direction:column;padding:20px 22px 16px;border-radius:16px;background:var(--card,#fff);box-shadow:0 32px 80px rgba(15,23,42,.32)}
.cf-dot{position:absolute;top:12px;left:14px;width:13px;height:13px;min-width:13px;min-height:13px;border:0;border-radius:50%;background:#f2645a;cursor:pointer}
.cf-title{margin:0 0 16px;color:var(--ink,#1d1d21);font-size:17px;font-weight:600;text-align:center}
.cf-body{flex:1;min-height:0;overflow-y:auto;padding:0 2px;scrollbar-gutter:stable}
.cf-head{display:flex;align-items:center;gap:16px;padding:2px 0 14px}
.cf-icon{position:relative;display:grid;width:54px;height:54px;min-width:54px;min-height:54px;place-items:center;border:0;border-radius:50%;font-size:31px;line-height:1;cursor:pointer}
.cf-icon-pen{position:absolute;right:-2px;bottom:-2px;display:grid;width:19px;height:19px;place-items:center;border:2px solid var(--card,#fff);border-radius:50%;background:#c8ccd4;color:#fff}
.cf-name{position:relative;flex:1;min-width:0}
.cf-name-input{width:100%;height:44px;padding:0 38px 0 14px;border:1.5px solid #3d8fce;border-radius:10px;background:transparent;color:var(--ink,#1d1d21);font:inherit;font-size:15px;outline:none}
.cf-name-input::placeholder{color:var(--muted,#9aa1ad)}
.cf-bookmark{position:absolute;top:50%;right:12px;display:grid;place-items:center;color:var(--muted,#8b92a3);transform:translateY(-50%)}
.cf-rows{display:grid;gap:10px}
.cf-row{position:relative;display:flex;align-items:center;gap:12px}
.cf-label{flex:none;width:48px;color:var(--ink2,#565d6b);font-size:14.5px}
.cf-control{display:flex;flex:1;min-width:0;height:42px;align-items:center;justify-content:space-between;gap:8px;padding:0 12px;border:1px solid var(--line,#e4e6ec);border-radius:10px;background:transparent;color:var(--ink,#1d1d21);font:inherit;font-size:14.5px;cursor:pointer}
.cf-control.is-open{border-color:#3d8fce}
.cf-control span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.cf-control .is-placeholder{color:var(--muted,#9aa1ad)}
.cf-pop{position:absolute;z-index:14;top:calc(100% + 8px);left:60px;right:0;display:flex;flex-direction:column;max-height:340px;padding:8px;border-radius:12px;background:var(--card,#fff);box-shadow:0 20px 48px rgba(15,23,42,.24)}
.cf-pop.is-up{top:auto;bottom:calc(100% + 8px)}
.cf-pop.is-date{left:60px}
.cf-seg{display:flex;gap:4px;margin:2px 2px 12px;padding:3px;border-radius:10px;background:color-mix(in srgb,var(--ink,#1d1d21) 7%,transparent)}
.cf-seg button{flex:1;height:32px;border:0;border-radius:8px;background:transparent;color:var(--ink2,#565d6b);font:inherit;font-size:14px;cursor:pointer}
.cf-seg button.on{background:var(--card,#fff);color:var(--ink,#1d1d21);font-weight:600;box-shadow:0 1px 4px rgba(15,23,42,.1)}
.cf-date-row{display:flex;gap:8px;padding:0 2px}
.cf-box{flex:1;min-width:0;height:40px;padding:0 8px;border:0;border-radius:9px;background:color-mix(in srgb,var(--ink,#1d1d21) 6%,transparent);color:var(--ink,#1d1d21);font:inherit;font-size:14px;text-align:center;text-align-last:center;cursor:pointer}
.cf-box.is-line{border:1px solid var(--line,#e4e6ec);background:transparent}
.cf-tip{margin:8px 2px 0;color:#e05a4f;font-size:12.5px}
.cf-list{display:grid;overflow-y:auto}
.cf-list button{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:11px 10px;border:0;border-radius:8px;background:transparent;color:var(--ink,#1d1d21);font:inherit;font-size:14.5px;text-align:left;cursor:pointer}
.cf-list button:hover{background:color-mix(in srgb,var(--ink,#1d1d21) 6%,transparent)}
.cf-list button.on{color:#3d8fce;font-weight:600}
.cf-list button em{color:var(--muted,#8b92a3);font-style:normal;font-size:13px}
.cf-group-title{margin:6px 10px 4px;color:var(--muted,#8b92a3);font-size:12.5px}
.cf-custom{display:flex;align-items:center;gap:8px;padding:10px;border-top:1px solid var(--line,#eef0f4);color:var(--ink2,#565d6b);font-size:14px}
.cf-custom input{flex:1;min-width:0;height:34px;padding:0 10px;border:1px solid var(--line,#e4e6ec);border-radius:8px;background:transparent;color:var(--ink,#1d1d21);font:inherit;font-size:14px;outline:none}
.cf-pop-foot{display:flex;justify-content:flex-end;gap:10px;padding:10px 4px 2px}
.cf-body.is-style{padding-top:4px}
.cf-error{margin:10px 0 0;color:#e05a4f;font-size:13px}
.cf-foot{display:flex;flex:none;justify-content:center;gap:14px;padding-top:16px}
.cf-btn{min-width:124px;height:40px;padding:0 20px;border:1px solid var(--line,#e2e4ea);border-radius:10px;background:var(--card,#fff);color:var(--ink,#1d1d21);font:inherit;font-size:14.5px;cursor:pointer}
.cf-btn.is-primary{border-color:transparent;background:#3d8fce;color:#fff;font-weight:600}
.cf-btn:disabled{border-color:transparent;background:color-mix(in srgb,#3d8fce 28%,#ffffff);color:#fff;cursor:default}
.cf-btn.is-mini{min-width:56px;height:32px;padding:0 12px;font-size:13px}
@media(max-width:560px){
  .cf-sheet{padding:18px 14px 12px;max-height:94vh}
  .cf-label{width:40px;font-size:13.5px}
  .cf-pop{left:52px}
  .cf-btn{min-width:96px}
}
</style>
