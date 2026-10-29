<template>
  <main class="focus-page">
    <header class="focus-head">
      <h1>番茄专注</h1>
      <div class="focus-modes" role="tablist" aria-label="计时模式">
        <button type="button" role="tab" :class="{ on: mode === 'POMODORO' }" :aria-selected="mode === 'POMODORO'" @click="switchMode('POMODORO')">番茄计时</button>
        <button type="button" role="tab" :class="{ on: mode === 'STOPWATCH' }" :aria-selected="mode === 'STOPWATCH'" @click="switchMode('STOPWATCH')">正计时</button>
      </div>
      <div class="focus-head-actions">
        <button type="button" class="focus-icon-btn" aria-label="添加常用专注" title="添加常用专注" @click="openPresetDialog()"><Plus :size="18" /></button>
        <div class="focus-menu-wrap">
          <button type="button" class="focus-icon-btn" aria-label="更多" title="更多" :aria-expanded="moreOpen" @click.stop="moreOpen = !moreOpen"><MoreHorizontal :size="18" /></button>
          <div v-if="moreOpen" class="focus-menu is-page" @click.stop>
            <button type="button" @click="goStats"><ChartPie :size="15" />统计</button>
            <button type="button" @click="openSettings"><Settings2 :size="15" />专注设置</button>
          </div>
        </div>
      </div>
    </header>

    <p v-if="focus.error" class="focus-error" role="alert">{{ focus.error }}</p>

    <div class="focus-body">
      <section class="focus-stage">
        <div class="focus-target">
          <button type="button" :aria-expanded="presetMenuOpen" @click.stop="presetMenuOpen = !presetMenuOpen">
            <span class="focus-target-icon">{{ activePreset?.icon || '🍅' }}</span>
            <span>{{ activePreset?.name || '专注' }}</span>
            <ChevronRight :size="14" />
          </button>
          <div v-if="presetMenuOpen" class="focus-menu is-presets" @click.stop>
            <p v-if="!focus.presets.length" class="focus-menu-empty">还没有常用专注</p>
            <button v-for="preset in focus.presets" :key="preset.id" type="button" :class="{ on: preset.id === activePresetId }" @click="pickPreset(preset)">
              <span class="focus-menu-icon">{{ preset.icon }}</span>
              <span>{{ preset.name }}</span>
              <em>{{ preset.mode === 'STOPWATCH' ? '正计时' : `${preset.minutes} 分钟` }}</em>
            </button>
            <button type="button" class="focus-menu-add" @click="openPresetDialog()"><Plus :size="14" />添加常用专注</button>
          </div>
        </div>

        <div class="focus-ring" :class="{ 'is-running': focus.current }">
          <svg viewBox="0 0 360 360" aria-hidden="true">
            <circle class="ring-track" cx="180" cy="180" r="158" />
            <circle
              v-if="mode !== 'STOPWATCH'"
              class="ring-progress"
              cx="180"
              cy="180"
              r="158"
              :stroke-dasharray="ringCircumference"
              :stroke-dashoffset="ringOffset"
            />
            <g v-else class="ring-ticks">
              <line v-for="tick in ticks" :key="tick.index" :x1="tick.x1" :y1="tick.y1" :x2="tick.x2" :y2="tick.y2" :class="{ on: tick.on }" />
            </g>
          </svg>
          <strong>{{ clockLabel }}</strong>
        </div>

        <p class="focus-stage-hint">{{ stageHint }}</p>

        <div class="focus-actions">
          <button v-if="!focus.current" type="button" class="focus-primary" :disabled="focus.loading" @click="startFocus">开始</button>
          <template v-else>
            <button type="button" class="focus-primary" :disabled="focus.loading" @click="focus.complete()">结束</button>
            <button type="button" class="focus-ghost" :disabled="focus.loading" @click="focus.abort()">放弃</button>
          </template>
        </div>
      </section>

      <aside class="focus-side">
        <section class="focus-overview" aria-label="概览">
          <h2>概览</h2>
          <div class="focus-cards">
            <div class="focus-card"><span>今日番茄</span><b>{{ focus.stats?.todayCount ?? 0 }}</b></div>
            <div class="focus-card"><span>今日专注时长</span><b>{{ durationLabel(focus.stats?.todayMinutes) }}</b></div>
            <div class="focus-card"><span>总番茄</span><b>{{ focus.stats?.totalCount ?? 0 }}</b></div>
            <div class="focus-card"><span>总专注时长</span><b>{{ durationLabel(focus.stats?.totalMinutes) }}</b></div>
          </div>
        </section>

        <section class="focus-records" aria-label="专注记录">
          <header>
            <h2>专注记录</h2>
            <div class="focus-record-actions">
              <button type="button" class="focus-icon-btn" aria-label="添加专注记录" title="添加专注记录" @click="openRecordDialog"><Plus :size="16" /></button>
              <div class="focus-menu-wrap">
                <button type="button" class="focus-icon-btn" aria-label="记录选项" title="记录选项" :aria-expanded="recordMenuOpen" @click.stop="recordMenuOpen = !recordMenuOpen"><MoreHorizontal :size="16" /></button>
                <div v-if="recordMenuOpen" class="focus-menu is-record" @click.stop>
                  <button type="button" @click="toggleBatch"><ListChecks :size="15" />批量管理</button>
                  <button type="button" @click="openExport"><Download :size="15" />导出 Excel</button>
                  <button type="button" class="is-danger" @click="clearAll"><Trash2 :size="15" />清空所有</button>
                </div>
              </div>
            </div>
          </header>

          <div v-if="batchMode" class="focus-batch">
            <label><input type="checkbox" :checked="allSelected" @change="toggleSelectAll" />全选</label>
            <span>已选 {{ selectedIds.length }} 条</span>
            <button type="button" class="is-danger" :disabled="!selectedIds.length" @click="deleteSelected">删除</button>
            <button type="button" @click="exitBatch">退出</button>
          </div>

          <div v-if="!focus.groupedSessions.length && !focus.loadingSessions" class="focus-empty">本周还没有专注记录</div>

          <div v-for="group in focus.groupedSessions" :key="group.key" class="focus-group">
            <h3>{{ dateLabel(group.date) }}</h3>
            <ul>
              <li v-for="session in group.items" :key="session.publicId" :class="{ 'is-aborted': session.status !== 'COMPLETED' }">
                <label v-if="batchMode" class="focus-row-check"><input v-model="selectedIds" type="checkbox" :value="session.publicId" /></label>
                <span class="focus-row-dot" aria-hidden="true"></span>
                <div class="focus-row-main">
                  <span class="focus-row-time">{{ timeRange(session) }}</span>
                  <span class="focus-row-task">{{ session.taskTitle || '未关联任务' }}<em v-if="session.note"> · {{ session.note }}</em></span>
                </div>
                <span class="focus-row-duration">{{ shortMinutes(session.actualMinutes) }}</span>
              </li>
            </ul>
          </div>

          <button v-if="focus.hasMore && focus.sessions.length" type="button" class="focus-more" :disabled="focus.loadingSessions" @click="focus.loadMore()">
            {{ focus.loadingSessions ? '加载中…' : '加载更多' }}
          </button>
        </section>
      </aside>
    </div>

    <div v-if="presetDialog.open" class="focus-dialog-mask" @click.self="presetDialog.open = false">
      <section class="focus-dialog" role="dialog" aria-label="添加常用专注">
        <h2>添加常用专注</h2>
        <div class="focus-preset-icon-row">
          <span class="focus-preset-icon">{{ presetForm.icon }}</span>
          <input v-model.trim="presetForm.name" class="focus-input" placeholder="名称" maxlength="30" />
        </div>
        <div class="focus-emoji-row">
          <button v-for="emoji in emojiChoices" :key="emoji" type="button" :class="{ on: presetForm.icon === emoji }" @click="presetForm.icon = emoji">{{ emoji }}</button>
        </div>
        <h3>计时模式</h3>
        <label class="focus-radio"><input v-model="presetForm.mode" type="radio" value="POMODORO" />番茄计时
          <input v-model.number="presetForm.minutes" class="focus-input is-mini" type="number" min="1" max="180" />分钟
        </label>
        <label class="focus-radio"><input v-model="presetForm.mode" type="radio" value="STOPWATCH" />正计时</label>
        <footer>
          <button type="button" class="focus-primary" :disabled="!presetForm.name" @click="savePreset">保存</button>
          <button type="button" class="focus-ghost" @click="presetDialog.open = false">取消</button>
        </footer>
      </section>
    </div>

    <div v-if="recordDialog.open" class="focus-dialog-mask" @click.self="recordDialog.open = false">
      <section class="focus-dialog" role="dialog" aria-label="添加专注记录">
        <h2>添加专注记录</h2>
        <label class="focus-field"><span>专注任务</span>
          <select v-model="recordForm.taskId">
            <option value="">选择任务</option>
            <option v-for="task in openTasks" :key="task.publicId" :value="task.publicId">{{ task.title }}</option>
          </select>
        </label>
        <label class="focus-field"><span>开始时间</span><input v-model="recordForm.startLocal" type="datetime-local" /></label>
        <label class="focus-field"><span>结束时间</span><input v-model="recordForm.endLocal" type="datetime-local" /></label>
        <div class="focus-field"><span>类型</span><p class="focus-field-value">正计时：{{ recordMinutes }} 分钟</p></div>
        <label class="focus-field is-textarea"><span>专注笔记</span><textarea v-model="recordForm.note" maxlength="500" placeholder="记录你的想法…"></textarea></label>
        <p v-if="recordDialog.error" class="focus-dialog-error" role="alert">{{ recordDialog.error }}</p>
        <footer>
          <button type="button" class="focus-primary" :disabled="!canSaveRecord" @click="saveRecord">保存</button>
          <button type="button" class="focus-ghost" @click="recordDialog.open = false">取消</button>
        </footer>
      </section>
    </div>

    <div v-if="settingsOpen" class="focus-dialog-mask" @click.self="settingsOpen = false">
      <section class="focus-dialog is-settings" role="dialog" aria-label="专注设置">
        <h2>专注设置</h2>
        <h3>计时选项</h3>
        <label class="focus-field"><span>番茄时长</span><input v-model.number="settingForm.focusMinutes" type="number" min="1" max="180" /><em>分钟</em></label>
        <label class="focus-field"><span>短休息时长</span><input v-model.number="settingForm.shortBreakMinutes" type="number" min="1" max="60" /><em>分钟</em></label>
        <label class="focus-field"><span>长休息时长</span><input v-model.number="settingForm.longBreakMinutes" type="number" min="1" max="120" /><em>分钟</em></label>
        <label class="focus-field"><span>长休息间隔番茄数</span><input v-model.number="settingForm.longBreakInterval" type="number" min="2" max="12" /><em>个</em></label>
        <h3>自动选项</h3>
        <label class="focus-field is-switch"><span>自动开始下个番茄</span>
          <button type="button" role="switch" :aria-checked="settingForm.autoStartNext" :class="{ on: settingForm.autoStartNext }" @click="settingForm.autoStartNext = !settingForm.autoStartNext"><i></i></button>
        </label>
        <label class="focus-field is-switch"><span>自动休息</span>
          <button type="button" role="switch" :aria-checked="settingForm.autoStartBreak" :class="{ on: settingForm.autoStartBreak }" @click="settingForm.autoStartBreak = !settingForm.autoStartBreak"><i></i></button>
        </label>
        <footer>
          <button type="button" class="focus-primary" :disabled="savingSettings" @click="saveSettings">{{ savingSettings ? '保存中…' : '保存' }}</button>
          <button type="button" class="focus-ghost" @click="settingsOpen = false">取消</button>
        </footer>
      </section>
    </div>

    <div v-if="exportDialog.open" class="focus-dialog-mask" @click.self="exportDialog.open = false">
      <section class="focus-dialog" role="dialog" aria-label="导出专注记录">
        <h2>导出专注记录</h2>
        <label class="focus-field"><span>开始日期</span><input v-model="exportDialog.from" type="date" /></label>
        <label class="focus-field"><span>结束日期</span><input v-model="exportDialog.to" type="date" /></label>
        <p v-if="exportDialog.error" class="focus-dialog-error" role="alert">{{ exportDialog.error }}</p>
        <footer>
          <button type="button" class="focus-primary" :disabled="exportDialog.busy" @click="exportExcel">{{ exportDialog.busy ? '导出中…' : '导出 Excel' }}</button>
          <button type="button" class="focus-ghost" @click="exportDialog.open = false">取消</button>
        </footer>
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ChartPie, ChevronRight, Download, ListChecks, MoreHorizontal, Plus, Settings2, Trash2 } from 'lucide-vue-next'
import { apiListFocusSessions } from '../../packages/api-client/src/index.js'
import { useTaskFocusStore } from '../stores/taskFocus.js'
import { useTasksStore } from '../stores/tasks.js'
import { createFocusPreset } from '../utils/focusPresets.js'
import { message } from '../services/message.js'

const router = useRouter()
const focus = useTaskFocusStore()
const tasksStore = useTasksStore()

const mode = ref('POMODORO')
const activePresetId = ref('')
const presetMenuOpen = ref(false)
const moreOpen = ref(false)
const recordMenuOpen = ref(false)
const settingsOpen = ref(false)
const savingSettings = ref(false)
const batchMode = ref(false)
const selectedIds = ref([])

const emojiChoices = ['🍅', '☕️', '📚', '💻', '🏃', '🎯', '🎨', '🎧', '🧠', '🌙']
const presetDialog = reactive({ open: false })
const presetForm = reactive({ name: '', icon: '🍅', mode: 'POMODORO', minutes: 120 })
const recordDialog = reactive({ open: false, error: '' })
const recordForm = reactive({ taskId: '', startLocal: '', endLocal: '', note: '' })
const settingForm = reactive({ focusMinutes: 120, shortBreakMinutes: 10, longBreakMinutes: 30, longBreakInterval: 4, autoStartNext: true, autoStartBreak: true })
const exportDialog = reactive({ open: false, from: '', to: '', busy: false, error: '' })

const openTasks = computed(() => tasksStore.tasks.filter(task => task.status === 'OPEN' && !task.deleted).slice(0, 200))
const activePreset = computed(() => focus.presets.find(item => item.id === activePresetId.value) || null)

const plannedMinutes = computed(() => {
  if (focus.current) return Number(focus.current.plannedMinutes) || 0
  if (mode.value === 'STOPWATCH') return 0
  return activePreset.value?.minutes || focus.settings?.focusMinutes || 25
})

const ringCircumference = 2 * Math.PI * 158
const ringOffset = computed(() => {
  const total = (focus.current ? Number(focus.current.plannedMinutes) || 1 : plannedMinutes.value || 1) * 60
  const remain = focus.current ? focus.remainingSeconds : total
  const ratio = Math.min(1, Math.max(0, remain / total))
  return ringCircumference * (1 - ratio)
})

// 正计时的刻度环：一圈 60 分钟，按已用分钟点亮
const ticks = computed(() => {
  const elapsed = focus.current ? Math.floor(focus.elapsedSeconds / 60) : 0
  const lit = elapsed % 60
  return Array.from({ length: 60 }, (_, index) => {
    const angle = (index / 60) * Math.PI * 2 - Math.PI / 2
    return {
      index,
      x1: 180 + Math.cos(angle) * 146,
      y1: 180 + Math.sin(angle) * 146,
      x2: 180 + Math.cos(angle) * 166,
      y2: 180 + Math.sin(angle) * 166,
      on: index < lit
    }
  })
})

const clockLabel = computed(() => {
  if (focus.current) return clock(focus.isCountUp ? focus.elapsedSeconds : focus.remainingSeconds)
  return clock((mode.value === 'STOPWATCH' ? 0 : plannedMinutes.value) * 60)
})

const stageHint = computed(() => {
  if (focus.current) {
    const target = focus.current.taskTitle || activePreset.value?.name || '专注'
    return focus.isCountUp ? `正计时中 · ${target}` : `专注中 · ${target}`
  }
  return mode.value === 'STOPWATCH' ? '点开始进入正计时' : `${plannedMinutes.value} 分钟一段`
})

function clock(seconds) {
  const value = Math.max(0, Math.round(seconds))
  return `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`
}

function durationLabel(minutes) {
  const value = Number(minutes || 0)
  const hours = Math.floor(value / 60)
  const rest = value % 60
  return hours ? `${hours}h ${rest}m` : `${rest}m`
}

function shortMinutes(minutes) {
  const value = Number(minutes || 0)
  const hours = Math.floor(value / 60)
  const rest = value % 60
  if (!hours) return `${rest}m`
  return rest ? `${hours}h${rest}m` : `${hours}h`
}

function timeRange(session) {
  const start = new Date(session.startedAt)
  const end = session.endedAt ? new Date(session.endedAt) : null
  const fmt = date => `${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`
  return end ? `${fmt(start)}-${fmt(end)}` : `${fmt(start)}-进行中`
}

function dateLabel(date) {
  const now = new Date()
  const prefix = date.toDateString() === now.toDateString() ? '今天 · ' : ''
  return `${prefix}${date.getMonth() + 1}月${date.getDate()}日`
}

function switchMode(value) {
  mode.value = value
  presetMenuOpen.value = false
}

function pickPreset(preset) {
  activePresetId.value = preset.id
  mode.value = preset.mode === 'STOPWATCH' ? 'STOPWATCH' : 'POMODORO'
  presetMenuOpen.value = false
}

function openPresetDialog() {
  presetMenuOpen.value = false
  moreOpen.value = false
  Object.assign(presetForm, { name: '', icon: '🍅', mode: mode.value, minutes: plannedMinutes.value || 120 })
  presetDialog.open = true
}

function savePreset() {
  const preset = createFocusPreset({ ...presetForm })
  focus.savePresetList([...focus.presets, preset])
  activePresetId.value = preset.id
  mode.value = preset.mode === 'STOPWATCH' ? 'STOPWATCH' : 'POMODORO'
  presetDialog.open = false
  message.success('已添加常用专注')
}

async function startFocus() {
  if (focus.loading) return
  try {
    await focus.start({ plannedMinutes: mode.value === 'STOPWATCH' ? 0 : plannedMinutes.value })
  } catch { /* store 已写入 error */ }
}

function openSettings() {
  moreOpen.value = false
  Object.assign(settingForm, {
    focusMinutes: focus.settings?.focusMinutes ?? 120,
    shortBreakMinutes: focus.settings?.shortBreakMinutes ?? 10,
    longBreakMinutes: focus.settings?.longBreakMinutes ?? 30,
    longBreakInterval: focus.settings?.longBreakInterval ?? 4,
    autoStartNext: Boolean(focus.settings?.autoStartNext),
    autoStartBreak: Boolean(focus.settings?.autoStartBreak)
  })
  settingsOpen.value = true
}

async function saveSettings() {
  savingSettings.value = true
  try {
    await focus.saveSettings({ ...settingForm })
    settingsOpen.value = false
    message.success('专注设置已保存')
  } catch (error) {
    message.error(error?.response?.data?.detail || '保存失败')
  } finally {
    savingSettings.value = false
  }
}

const recordMinutes = computed(() => {
  if (!recordForm.startLocal || !recordForm.endLocal) return 0
  const diff = (new Date(recordForm.endLocal).getTime() - new Date(recordForm.startLocal).getTime()) / 60000
  return diff > 0 ? Math.round(diff) : 0
})
const canSaveRecord = computed(() => recordMinutes.value > 0 && !focus.loading)

function toLocalInput(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function openRecordDialog() {
  recordMenuOpen.value = false
  recordDialog.error = ''
  recordForm.taskId = ''
  recordForm.note = ''
  const now = new Date()
  recordForm.startLocal = toLocalInput(new Date(now.getTime() - 30 * 60000))
  recordForm.endLocal = toLocalInput(now)
  recordDialog.open = true
}

async function saveRecord() {
  recordDialog.error = ''
  try {
    await focus.createRecord({
      taskId: recordForm.taskId || undefined,
      startedAt: new Date(recordForm.startLocal).toISOString(),
      endedAt: new Date(recordForm.endLocal).toISOString(),
      note: recordForm.note
    })
    recordDialog.open = false
    message.success('已添加专注记录')
  } catch (error) {
    recordDialog.error = error?.response?.data?.detail || error?.message || '保存失败'
  }
}

function toggleBatch() {
  recordMenuOpen.value = false
  batchMode.value = !batchMode.value
  selectedIds.value = []
}

function exitBatch() {
  batchMode.value = false
  selectedIds.value = []
}

const allSelected = computed(() => focus.sessions.length > 0 && selectedIds.value.length === focus.sessions.length)

function toggleSelectAll(event) {
  selectedIds.value = event.target.checked ? focus.sessions.map(item => item.publicId) : []
}

async function deleteSelected() {
  const list = focus.sessions.filter(item => selectedIds.value.includes(item.publicId))
  if (!list.length || !window.confirm(`删除选中的 ${list.length} 条专注记录？`)) return
  await focus.removeMany(list)
  exitBatch()
  message.success('已删除')
}

async function clearAll() {
  recordMenuOpen.value = false
  if (!focus.sessions.length) return
  if (!window.confirm(`清空当前列表里的 ${focus.sessions.length} 条专注记录？此操作不可撤销。`)) return
  await focus.removeMany([...focus.sessions])
  message.success('已清空')
}

function openExport() {
  recordMenuOpen.value = false
  exportDialog.error = ''
  const today = new Date()
  const from = new Date(today.getFullYear(), today.getMonth(), 1)
  exportDialog.from = `${from.getFullYear()}-${String(from.getMonth() + 1).padStart(2, '0')}-${String(from.getDate()).padStart(2, '0')}`
  exportDialog.to = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  exportDialog.open = true
}

function escapeXml(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;' }[char]))
}

/** Excel 2003 XML：纯文本表格，Excel / WPS 直接打开，不用额外依赖 */
async function exportExcel() {
  exportDialog.error = ''
  if (!exportDialog.from || !exportDialog.to) {
    exportDialog.error = '请选择日期范围'
    return
  }
  if (exportDialog.from > exportDialog.to) {
    exportDialog.error = '开始日期不能晚于结束日期'
    return
  }
  exportDialog.busy = true
  try {
    const to = new Date(`${exportDialog.to}T00:00:00`)
    to.setDate(to.getDate() + 1)
    const toKey = `${to.getFullYear()}-${String(to.getMonth() + 1).padStart(2, '0')}-${String(to.getDate()).padStart(2, '0')}`
    const rows = await apiListFocusSessions({ from: exportDialog.from, to: toKey })
    if (!rows?.length) {
      exportDialog.error = '这个区间没有专注记录'
      return
    }
    const header = ['开始时间', '结束时间', '任务', '时长(分钟)', '状态', '笔记']
    const cell = (value, isNumber = false) => `<Cell><Data ss:Type="${isNumber ? 'Number' : 'String'}">${escapeXml(value)}</Data></Cell>`
    const xml = [
      '<?xml version="1.0" encoding="UTF-8"?>',
      '<?mso-application progid="Excel.Sheet"?>',
      '<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">',
      '<Worksheet ss:Name="专注记录"><Table>',
      `<Row>${header.map(value => cell(value)).join('')}</Row>`,
      ...rows.map(session => `<Row>${
        [
          cell(new Date(session.startedAt).toLocaleString('zh-CN')),
          cell(session.endedAt ? new Date(session.endedAt).toLocaleString('zh-CN') : ''),
          cell(session.taskTitle || '未关联任务'),
          cell(session.actualMinutes ?? 0, true),
          cell(session.status === 'COMPLETED' ? '已完成' : '已放弃'),
          cell(session.note || '')
        ].join('')
      }</Row>`),
      '</Table></Worksheet></Workbook>'
    ].join('\n')
    const blob = new Blob([xml], { type: 'application/vnd.ms-excel;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `专注记录_${exportDialog.from}_${exportDialog.to}.xls`
    link.click()
    URL.revokeObjectURL(url)
    exportDialog.open = false
    message.success(`已导出 ${rows.length} 条记录`)
  } catch (error) {
    exportDialog.error = error?.response?.data?.detail || '导出失败'
  } finally {
    exportDialog.busy = false
  }
}

function goStats() {
  moreOpen.value = false
  router.push('/tasks/focus/stats')
}

function closeMenus(event) {
  if (event?.target?.closest?.('.focus-menu-wrap') || event?.target?.closest?.('.focus-target')) return
  moreOpen.value = false
  recordMenuOpen.value = false
  presetMenuOpen.value = false
}

watch(() => focus.settings, value => {
  if (!value) return
  Object.assign(settingForm, {
    focusMinutes: value.focusMinutes,
    shortBreakMinutes: value.shortBreakMinutes,
    longBreakMinutes: value.longBreakMinutes,
    longBreakInterval: value.longBreakInterval,
    autoStartNext: Boolean(value.autoStartNext),
    autoStartBreak: Boolean(value.autoStartBreak)
  })
}, { immediate: true })

watch(() => focus.current, value => {
  if (value) mode.value = Number(value.plannedMinutes) ? 'POMODORO' : 'STOPWATCH'
})

onMounted(async () => {
  window.addEventListener('pointerdown', closeMenus)
  await tasksStore.init({ waitForRemote: false })
  await focus.refresh()
  // 已有进行中的会话时，模式要跟它一致（正计时/番茄）
  if (focus.current) mode.value = Number(focus.current.plannedMinutes) ? 'POMODORO' : 'STOPWATCH'
  if (focus.presets.length) activePresetId.value = focus.presets[0].id
})
onBeforeUnmount(() => window.removeEventListener('pointerdown', closeMenus))
</script>

<style scoped>
.focus-page { display: flex; flex-direction: column; min-height: 100%; padding: 18px 22px 40px; color: var(--ink); }
.focus-head { display: flex; align-items: center; gap: 16px; margin-bottom: 14px; }
.focus-head h1 { margin: 0; font-size: 1.16rem; font-weight: 700; }
.focus-modes { display: flex; gap: 2px; padding: 3px; border-radius: 9px; background: var(--paper); }
.focus-modes button { min-height: 26px; padding: 0 12px; border: 0; border-radius: 7px; background: transparent; color: var(--muted); font: inherit; font-size: 12.5px; cursor: pointer; }
.focus-modes button.on { background: var(--card); color: var(--ink); box-shadow: 0 1px 3px rgb(15 23 42 / 12%); }
.focus-head-actions { display: flex; align-items: center; gap: 2px; margin-left: auto; }
.focus-icon-btn { display: grid; width: 30px; height: 30px; place-items: center; border: 0; border-radius: 8px; background: transparent; color: var(--ink2); cursor: pointer; }
.focus-icon-btn:hover { background: var(--paper); color: var(--ink); }
.focus-menu-wrap { position: relative; }

.focus-body { display: grid; grid-template-columns: minmax(340px, 1fr) minmax(360px, 1fr); gap: 26px; align-items: start; }

.focus-stage { display: grid; justify-items: center; gap: 10px; padding: 8px 0 20px; }
.focus-target { position: relative; }
.focus-target > button { display: inline-flex; align-items: center; gap: 6px; padding: 4px 8px; border: 0; border-radius: 8px; background: transparent; color: var(--muted); font: inherit; font-size: 13px; cursor: pointer; }
.focus-target > button:hover { background: var(--paper); color: var(--ink); }
.focus-target-icon { font-size: 14px; }

.focus-ring { position: relative; display: grid; width: min(340px, 44vh); aspect-ratio: 1; place-items: center; margin: 4px 0 2px; }
.focus-ring svg { position: absolute; inset: 0; width: 100%; height: 100%; transform: rotate(-90deg); }
.ring-track { fill: none; stroke: color-mix(in srgb, var(--ink) 9%, transparent); stroke-width: 1.5; }
.ring-progress { fill: none; stroke: color-mix(in srgb, var(--ink) 26%, transparent); stroke-width: 2; stroke-linecap: round; transition: stroke-dashoffset .4s linear; }
.focus-ring.is-running .ring-progress { stroke: var(--accent); }
.ring-ticks line { stroke: color-mix(in srgb, var(--ink) 16%, transparent); stroke-width: 2; stroke-linecap: round; }
.ring-ticks line.on { stroke: color-mix(in srgb, var(--ink) 42%, transparent); }
.focus-ring strong { font-size: clamp(2.4rem, 5.6vw, 3.4rem); font-weight: 600; font-variant-numeric: tabular-nums; letter-spacing: .02em; }

.focus-stage-hint { margin: 0; color: var(--muted); font-size: 12.5px; }
.focus-actions { display: flex; gap: 10px; margin-top: 14px; }
.focus-primary { min-width: 128px; min-height: 38px; padding: 0 22px; border: 1px solid var(--accent); border-radius: 10px; background: var(--accent); color: var(--accent-contrast, #fff); font: inherit; font-size: 13.5px; cursor: pointer; }
.focus-primary:disabled { opacity: .55; cursor: default; }
.focus-ghost { min-height: 38px; padding: 0 18px; border: 1px solid var(--line2); border-radius: 10px; background: transparent; color: var(--ink2); font: inherit; font-size: 13.5px; cursor: pointer; }
.focus-ghost:hover { background: var(--paper); }

.focus-side { display: grid; gap: 22px; }
.focus-overview h2, .focus-records h2 { margin: 0 0 10px; font-size: 13px; font-weight: 700; }
.focus-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.focus-card { display: grid; gap: 6px; padding: 12px 14px; border: 1px solid var(--line); border-radius: 10px; background: var(--paper); }
.focus-card span { color: var(--muted); font-size: 11.5px; }
.focus-card b { font-size: 1.16rem; font-weight: 700; }

.focus-records { display: grid; gap: 8px; }
.focus-records > header { display: flex; align-items: center; justify-content: space-between; }
.focus-records > header h2 { margin: 0; }
.focus-record-actions { display: flex; gap: 2px; }
.focus-group h3 { margin: 10px 0 6px; color: var(--muted); font-size: 11.5px; font-weight: 600; }
.focus-group ul { display: grid; gap: 2px; margin: 0; padding: 0; list-style: none; }
.focus-group li { display: flex; align-items: center; gap: 9px; padding: 7px 6px; border-radius: 8px; }
.focus-group li:hover { background: var(--paper); }
.focus-group li.is-aborted { opacity: .55; }
.focus-row-check input { width: 13px; height: 13px; }
.focus-row-dot { flex: 0 0 auto; width: 7px; height: 7px; border-radius: 50%; background: color-mix(in srgb, var(--accent) 70%, transparent); }
.focus-row-main { display: grid; flex: 1; gap: 2px; min-width: 0; }
.focus-row-time { color: var(--ink2); font-size: 12.5px; font-variant-numeric: tabular-nums; }
.focus-row-task { overflow: hidden; color: var(--muted); font-size: 11.5px; text-overflow: ellipsis; white-space: nowrap; }
.focus-row-task em { font-style: normal; }
.focus-row-duration { flex: 0 0 auto; color: var(--ink2); font-size: 12px; }
.focus-more { grid-column: 1 / -1; justify-self: center; margin-top: 8px; padding: 6px 16px; border: 1px solid var(--line2); border-radius: 8px; background: transparent; color: var(--ink2); font: inherit; font-size: 12px; cursor: pointer; }
.focus-empty { padding: 18px 0; color: var(--muted); font-size: 12.5px; }

.focus-batch { display: flex; align-items: center; gap: 12px; padding: 8px 10px; border: 1px solid var(--line); border-radius: 8px; background: var(--paper); color: var(--ink2); font-size: 12px; }
.focus-batch label { display: inline-flex; align-items: center; gap: 6px; cursor: pointer; }
.focus-batch button { padding: 3px 10px; border: 1px solid var(--line2); border-radius: 6px; background: transparent; color: var(--ink2); font: inherit; font-size: 12px; cursor: pointer; }
.focus-batch button.is-danger { border-color: color-mix(in srgb, var(--down, #e34d59) 55%, transparent); color: var(--down, #e34d59); }

.focus-menu { position: absolute; z-index: 40; display: grid; min-width: 168px; padding: 5px; border: 1px solid var(--line2); border-radius: 10px; background: var(--card); box-shadow: 0 16px 36px rgb(15 23 42 / 20%); }
.focus-menu.is-page { top: 34px; right: 0; }
.focus-menu.is-record { top: 32px; right: 0; }
.focus-menu.is-presets { top: 34px; left: 0; min-width: 220px; max-height: 320px; overflow: auto; }
.focus-menu button { display: flex; align-items: center; gap: 9px; min-height: 32px; padding: 0 8px; border: 0; border-radius: 7px; background: transparent; color: var(--ink2); font: inherit; font-size: 12.5px; text-align: left; cursor: pointer; }
.focus-menu button span { flex: 1; }
.focus-menu button em { color: var(--muted); font-size: 11px; font-style: normal; }
.focus-menu button:hover { background: var(--paper); color: var(--ink); }
.focus-menu button.on { color: var(--accent); }
.focus-menu button.is-danger:hover { background: color-mix(in srgb, var(--down, #e34d59) 12%, transparent); color: var(--down, #e34d59); }
.focus-menu-empty { margin: 6px 8px; color: var(--muted); font-size: 12px; }
.focus-menu-icon { width: 16px; text-align: center; }
.focus-menu-add { margin-top: 2px; border-top: 1px solid var(--line) !important; color: var(--accent) !important; }

.focus-dialog-mask { position: fixed; z-index: 120; inset: 0; display: grid; place-items: center; padding: 20px; background: rgb(15 23 42 / 46%); }
.focus-dialog { display: grid; gap: 12px; width: min(420px, calc(100vw - 32px)); padding: 18px; border: 1px solid var(--line2); border-radius: 14px; background: var(--card); box-shadow: 0 24px 60px rgb(15 23 42 / 32%); }
.focus-dialog.is-settings { width: min(460px, calc(100vw - 32px)); }
.focus-dialog h2 { margin: 0; font-size: 1rem; }
.focus-dialog h3 { margin: 4px 0 0; font-size: 12.5px; font-weight: 700; }
.focus-dialog footer { display: flex; justify-content: flex-end; gap: 10px; margin-top: 6px; }
.focus-dialog footer .focus-primary { min-width: 96px; min-height: 34px; }
.focus-dialog footer .focus-ghost { min-height: 34px; }
.focus-input, .focus-dialog select, .focus-dialog input[type='date'], .focus-dialog input[type='datetime-local'], .focus-dialog input[type='number'], .focus-dialog textarea {
  min-height: 32px; padding: 0 9px; border: 1px solid var(--line2); border-radius: 8px; background: var(--paper); color: var(--ink); font: inherit; font-size: 12.5px;
}
.focus-dialog textarea { min-height: 74px; padding: 8px 9px; resize: vertical; }
.focus-field { display: flex; align-items: center; gap: 10px; color: var(--ink2); font-size: 12.5px; }
.focus-field > span { flex: 0 0 96px; color: var(--muted); }
.focus-field > input, .focus-field > select { flex: 1; min-width: 0; }
.focus-field > em { color: var(--muted); font-size: 11.5px; font-style: normal; }
.focus-field.is-textarea { align-items: flex-start; }
.focus-field.is-textarea textarea { flex: 1; min-width: 0; }
.focus-field-value { flex: 1; margin: 0; color: var(--ink2); }
.focus-field.is-switch { justify-content: space-between; }
.focus-field.is-switch button { position: relative; width: 40px; height: 22px; border: 0; border-radius: 11px; background: var(--line2); cursor: pointer; }
.focus-field.is-switch button.on { background: var(--accent); }
.focus-field.is-switch button i { position: absolute; top: 2px; left: 2px; width: 18px; height: 18px; border-radius: 50%; background: #fff; transition: transform .15s ease; }
.focus-field.is-switch button.on i { transform: translateX(18px); }
.focus-preset-icon-row { display: flex; align-items: center; gap: 10px; }
.focus-preset-icon { display: grid; width: 40px; height: 40px; place-items: center; border-radius: 50%; background: color-mix(in srgb, var(--accent) 22%, transparent); font-size: 18px; }
.focus-preset-icon-row .focus-input { flex: 1; }
.focus-emoji-row { display: flex; flex-wrap: wrap; gap: 6px; }
.focus-emoji-row button { width: 30px; height: 30px; border: 1px solid transparent; border-radius: 8px; background: var(--paper); font-size: 15px; cursor: pointer; }
.focus-emoji-row button.on { border-color: var(--accent); }
.focus-radio { display: flex; align-items: center; gap: 8px; color: var(--ink2); font-size: 12.5px; cursor: pointer; }
.focus-input.is-mini { width: 72px; min-height: 30px; text-align: center; }
.focus-dialog-error { margin: 0; color: var(--down, #e34d59); font-size: 12px; }
.focus-error { margin: 0 0 12px; padding: 8px 10px; border-left: 3px solid var(--down, #e34d59); background: color-mix(in srgb, var(--down, #e34d59) 10%, transparent); font-size: 12.5px; }

@media (max-width: 1080px) {
  .focus-body { grid-template-columns: 1fr; gap: 18px; }
  .focus-page { padding: 16px 14px 90px; }
}
</style>
