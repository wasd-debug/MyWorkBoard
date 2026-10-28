<template>
  <div v-if="open" class="cal-settings-mask" @click.self="$emit('close')">
    <section class="cal-settings" role="dialog" aria-label="显示设置">
      <header>
        <h2>显示设置</h2>
        <button type="button" aria-label="关闭显示设置" @click="$emit('close')"><X :size="16" /></button>
      </header>

      <nav class="cal-settings-tabs" role="tablist">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.key"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >{{ tab.label }}</button>
      </nav>

      <div class="cal-settings-body">
        <template v-if="activeTab === 'task'">
          <div class="cal-setting-row">
            <span>显示方式</span>
            <select :value="settings.taskColorMode" @change="patch({ taskColorMode: $event.target.value })">
              <option v-for="mode in TASK_COLOR_MODES" :key="mode.value" :value="mode.value">{{ mode.label }}</option>
            </select>
          </div>
          <p class="cal-setting-hint">{{ hintOf(TASK_COLOR_MODES, settings.taskColorMode) }}</p>
          <div class="cal-setting-list">
            <div v-for="list in lists" :key="list.publicId" class="cal-setting-item">
              <span class="cal-setting-icon"><TaskOrganizationIcon :value="list.icon" :size="14" :style="{ color: list.color || 'var(--ink2)' }" /></span>
              <span class="cal-setting-name">{{ list.name }}</span>
              <button
                type="button"
                class="cal-swatch"
                :style="{ background: list.color || '#4d96ff' }"
                :aria-label="`修改「${list.name}」颜色`"
                @click.stop="openPalette($event, list.color || '#4d96ff', color => $emit('update-list-color', list, color))"
              ></button>
            </div>
          </div>
        </template>

        <template v-else-if="activeTab === 'habit'">
          <div class="cal-setting-row">
            <span>显示方式</span>
            <select :value="settings.habitColorMode" @change="patch({ habitColorMode: $event.target.value })">
              <option v-for="mode in HABIT_COLOR_MODES" :key="mode.value" :value="mode.value">{{ mode.label }}</option>
            </select>
          </div>
          <p class="cal-setting-hint">{{ hintOf(HABIT_COLOR_MODES, settings.habitColorMode) }}</p>
          <div v-if="settings.habitColorMode === 'fixed'" class="cal-setting-item">
            <span class="cal-setting-icon"><Circle :size="12" /></span>
            <span class="cal-setting-name">所有习惯</span>
            <button
              type="button"
              class="cal-swatch"
              :style="{ background: settings.habitFixedColor }"
              aria-label="修改习惯固定颜色"
              @click.stop="openPalette($event, settings.habitFixedColor, color => patch({ habitFixedColor: color }))"
            ></button>
          </div>
          <div v-else-if="settings.habitColorMode === 'habit'" class="cal-setting-list">
            <div v-for="habit in habits" :key="habit.publicId" class="cal-setting-item">
              <span class="cal-setting-icon"><Circle :size="12" /></span>
              <span class="cal-setting-name">{{ habit.name }}</span>
              <button
                type="button"
                class="cal-swatch"
                :style="{ background: habit.color || '#f0b429' }"
                :aria-label="`修改「${habit.name}」颜色`"
                @click.stop="openPalette($event, habit.color || '#f0b429', color => $emit('update-habit-color', habit, color))"
              ></button>
            </div>
          </div>
          <p v-else class="cal-setting-hint">当前所有习惯统一使用主题色，颜色块只在切换为「按习惯颜色」后可调。</p>
        </template>

        <template v-else-if="activeTab === 'countdown'">
          <div class="cal-setting-row">
            <span>显示方式</span>
            <select :value="settings.countdownColorMode" @change="patch({ countdownColorMode: $event.target.value })">
              <option v-for="mode in COUNTDOWN_COLOR_MODES" :key="mode.value" :value="mode.value">{{ mode.label }}</option>
            </select>
          </div>
          <p class="cal-setting-hint">{{ hintOf(COUNTDOWN_COLOR_MODES, settings.countdownColorMode) }}</p>
          <div v-if="settings.countdownColorMode === 'fixed'" class="cal-setting-item">
            <span class="cal-setting-icon"><Hourglass :size="12" /></span>
            <span class="cal-setting-name">所有纪念日</span>
            <button
              type="button"
              class="cal-swatch"
              :style="{ background: settings.countdownFixedColor }"
              aria-label="修改纪念日固定颜色"
              @click.stop="openPalette($event, settings.countdownFixedColor, color => patch({ countdownFixedColor: color }))"
            ></button>
          </div>
          <div v-else class="cal-setting-list">
            <div v-for="item in countdowns" :key="item.publicId" class="cal-setting-item">
              <span class="cal-setting-icon"><Hourglass :size="12" /></span>
              <span class="cal-setting-name">{{ item.title }}</span>
              <button
                type="button"
                class="cal-swatch"
                :style="{ background: item.color || '#e8994a' }"
                :aria-label="`修改「${item.title}」颜色`"
                @click.stop="openPalette($event, item.color || '#e8994a', color => $emit('update-countdown-color', item, color))"
              ></button>
            </div>
            <p v-if="!countdowns.length" class="cal-setting-hint">还没有倒数纪念日。</p>
          </div>
        </template>
        <template v-else>
          <p class="cal-setting-hint">系统日历可以叠加显示其它模块的数据，关闭后该层不再请求与绘制。</p>
          <div class="cal-setting-list">
            <label v-for="item in LAYER_OPTIONS" :key="item.key" class="cal-setting-item is-check">
              <component :is="item.icon" :size="13" />
              <span class="cal-setting-name">{{ item.label }}</span>
              <input
                type="checkbox"
                :checked="layers[item.key] !== false"
                :aria-label="`显示${item.label}`"
                @change="toggleLayer(item.key, $event.target.checked)"
              />
            </label>
          </div>
        </template>
      </div>

      <div v-if="palette.open" class="cal-palette" :style="paletteStyle" @click.stop>
        <header><b>选择颜色</b><button type="button" aria-label="关闭色板" @click="palette.open = false"><X :size="12" /></button></header>
        <div v-for="group in COLOR_GROUPS" :key="group.key" class="cal-palette-group">
          <span>{{ group.label }}</span>
          <div>
            <button
              v-for="color in group.colors"
              :key="`${group.key}-${color || 'none'}`"
              type="button"
              class="cal-palette-chip"
              :class="{ none: !color, on: color && color === palette.value }"
              :style="{ background: color || 'transparent' }"
              :aria-label="color || '无颜色'"
              @click="applyColor(color)"
            ></button>
          </div>
        </div>
        <div class="cal-palette-custom">
          <span>自定义</span>
          <input v-model="palette.custom" type="text" placeholder="#4d96ff" aria-label="自定义颜色" @keydown.enter.prevent="applyColor(palette.custom)" />
          <button type="button" @click="applyColor(palette.custom)">应用</button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { Briefcase, CalendarDays, Circle, Coins, Hourglass, X } from 'lucide-vue-next'
import TaskOrganizationIcon from '../../components/tasks/TaskOrganizationIcon.vue'
import {
  COLOR_GROUPS, COUNTDOWN_COLOR_MODES, HABIT_COLOR_MODES, TASK_COLOR_MODES, normalizeColor
} from './calendarColors.js'

const props = defineProps({
  open: { type: Boolean, default: false },
  lists: { type: Array, default: () => [] },
  habits: { type: Array, default: () => [] },
  countdowns: { type: Array, default: () => [] },
  settings: { type: Object, required: true },
  aggregate: { type: Boolean, default: false },
  layers: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['close', 'update:settings', 'update:layers', 'update-list-color', 'update-habit-color', 'update-countdown-color'])

const tabs = computed(() => {
  const base = [
    { key: 'task', label: '任务' },
    { key: 'habit', label: '习惯' },
    { key: 'countdown', label: '倒数纪念日' }
  ]
  return props.aggregate ? [...base, { key: 'layers', label: '叠加层' }] : base
})
const activeTab = ref('task')
const LAYER_OPTIONS = [
  { key: 'worktime', label: '工时', icon: Briefcase },
  { key: 'ledger', label: '流水', icon: Coins },
  { key: 'holiday', label: '节假日与调休', icon: CalendarDays },
  { key: 'habit', label: '习惯完成情况', icon: Circle }
]

function toggleLayer(key, checked) {
  emit('update:layers', { ...props.layers, [key]: checked })
}
const palette = reactive({ open: false, x: 0, y: 0, value: '', custom: '', apply: null })
const paletteStyle = computed(() => ({ left: `${palette.x}px`, top: `${palette.y}px` }))

function hintOf(modes, value) {
  return modes.find(item => item.value === value)?.hint || ''
}

function patch(values) {
  emit('update:settings', { ...props.settings, ...values })
}

function openPalette(event, current, apply) {
  const width = 232
  const height = 300
  const x = Math.min(Math.max(12, event.clientX - 120), Math.max(12, window.innerWidth - width - 12))
  const y = Math.min(Math.max(12, event.clientY - 20), Math.max(12, window.innerHeight - height - 12))
  Object.assign(palette, { open: true, x, y, value: current, custom: current, apply })
}

function applyColor(color) {
  const value = normalizeColor(color, '')
  if (!value) return
  palette.apply?.(value)
  palette.open = false
}
</script>

<style scoped>
.cal-settings-mask {
  position: fixed;
  z-index: 62;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(15, 23, 42, .38);
}
.cal-settings {
  display: flex;
  width: min(520px, calc(100vw - 32px));
  max-height: min(640px, calc(100vh - 80px));
  flex-direction: column;
  overflow: hidden;
  border-radius: 12px;
  background: var(--card);
  box-shadow: 0 26px 60px rgba(15, 23, 42, .28);
}
.cal-settings > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 13px 16px;
  border-bottom: 1px solid var(--line);
}
.cal-settings h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 650;
}
.cal-settings > header button {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
}
.cal-settings-tabs {
  display: flex;
  gap: 4px;
  padding: 10px 16px 0;
  border-bottom: 1px solid var(--line);
}
.cal-settings-tabs button {
  padding: 6px 10px 8px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--muted);
  font-size: 12.5px;
  font-weight: 600;
}
.cal-settings-tabs button.active {
  border-bottom-color: var(--accent);
  color: var(--ink);
}
.cal-settings-body {
  flex: 1 1 auto;
  min-height: 0;
  padding: 14px 16px 18px;
  overflow-y: auto;
}
.cal-setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
  font-weight: 600;
}
.cal-setting-row > span {
  white-space: nowrap;
}
.cal-setting-row select {
  width: auto;
  max-width: 200px;
  height: 30px;
  padding: 0 8px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: var(--paper);
  color: var(--ink);
  font-size: 12px;
}
.cal-setting-hint {
  margin: 8px 0 12px;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.5;
}
.cal-setting-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-top: 6px;
  border-top: 1px solid var(--line);
}
.cal-setting-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  font-size: 12.5px;
}
.cal-setting-icon {
  display: inline-grid;
  flex: 0 0 18px;
  place-items: center;
  width: 18px;
  overflow: hidden;
  color: var(--muted);
  font-size: 9px;
  white-space: nowrap;
}
.cal-setting-item.is-check {
  cursor: pointer;
}
.cal-setting-item.is-check input {
  width: 14px;
  height: 14px;
  accent-color: var(--accent);
}
.cal-setting-name {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cal-swatch {
  flex: 0 0 auto;
  width: 16px;
  height: 16px;
  border: 0;
  border-radius: 50%;
  cursor: pointer;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, .12);
}
.cal-swatch:hover {
  transform: scale(1.12);
}
.cal-palette {
  position: fixed;
  z-index: 70;
  width: 232px;
  padding: 10px 12px 12px;
  border: 1px solid var(--line2);
  border-radius: 10px;
  background: var(--card);
  box-shadow: 0 18px 44px rgba(15, 23, 42, .22);
}
.cal-palette > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.cal-palette > header b {
  font-size: 11.5px;
  font-weight: 650;
}
.cal-palette > header button {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--muted);
}
.cal-palette-group {
  margin-bottom: 6px;
}
.cal-palette-group > span {
  display: block;
  margin-bottom: 3px;
  color: var(--muted);
  font-size: 9.5px;
}
.cal-palette-group > div {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.cal-palette-chip {
  width: 18px;
  height: 18px;
  border: 0;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, .12);
}
.cal-palette-chip.none {
  border: 1px dashed var(--line2);
}
.cal-palette-chip.on {
  box-shadow: 0 0 0 2px var(--card), 0 0 0 3px var(--accent);
}
.cal-palette-custom {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--line);
}
.cal-palette-custom span {
  color: var(--muted);
  font-size: 9.5px;
}
.cal-palette-custom input {
  min-width: 0;
  flex: 1;
  height: 24px;
  padding: 0 6px;
  border: 1px solid var(--line);
  border-radius: 5px;
  background: var(--paper);
  color: var(--ink);
  font-size: 10.5px;
}
.cal-palette-custom button {
  height: 24px;
  padding: 0 8px;
  border: 1px solid var(--accent);
  border-radius: 5px;
  background: var(--accent);
  color: #fff;
  font-size: 10.5px;
}
</style>
