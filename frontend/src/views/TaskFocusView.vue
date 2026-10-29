<template>
  <main class="focus-page">
    <div ref="shellEl" class="focus-shell" :style="{ '--focus-split': splitColumns }">
      <section class="focus-pane">
        <header class="focus-pane-head">
          <h1>番茄专注</h1>
          <div class="focus-modes" role="tablist" aria-label="计时模式">
            <button type="button" role="tab" :class="{ on: mode === 'POMODORO' }" :aria-selected="mode === 'POMODORO'" @click="switchMode('POMODORO')">番茄计时</button>
            <button type="button" role="tab" :class="{ on: mode === 'STOPWATCH' }" :aria-selected="mode === 'STOPWATCH'" @click="switchMode('STOPWATCH')">正计时</button>
          </div>
          <div class="focus-head-actions">
            <button v-if="narrow" type="button" class="focus-icon-btn" aria-label="展开概览" title="展开概览" @click="drawerOpen = true"><PanelRightOpen :size="18" /></button>
            <button type="button" class="focus-icon-btn" aria-label="添加常用专注" title="添加常用专注" @click="openPresetDialog()"><Plus :size="18" /></button>
            <div class="focus-menu-wrap">
              <button type="button" class="focus-icon-btn" aria-label="提示音与白噪音" title="提示音与白噪音" :aria-expanded="openMenu === 'sound'" @click.stop="toggleMenu('sound')">
                <VolumeX v-if="!sound.enabled" :size="18" />
                <Volume2 v-else :size="18" />
              </button>
              <div v-if="openMenu === 'sound'" class="focus-menu is-sound" @click.stop>
                <div class="focus-sound-head">
                  <span>提示音与白噪音</span>
                  <button type="button" role="switch" class="focus-switch" :aria-checked="sound.enabled" :aria-label="sound.enabled ? '关闭声音' : '开启声音'" @click="toggleSoundEnabled"><i></i></button>
                </div>
                <label class="focus-sound-volume" :class="{ 'is-off': !sound.enabled }">
                  <span>音量</span>
                  <input v-model.number="sound.volume" type="range" min="0" max="100" step="1" :disabled="!sound.enabled" aria-label="音量" @input="onVolumeInput" />
                  <em>{{ sound.volume }}%</em>
                </label>
                <p class="focus-sound-label">番茄完成提示音</p>
                <div class="focus-sound-list">
                  <button v-for="option in CHIME_OPTIONS" :key="option.id" type="button" :class="{ on: sound.chime === option.id }" @click="pickChime(option.id)">
                    <span>{{ option.label }}</span>
                    <Check v-if="sound.chime === option.id" :size="13" />
                  </button>
                </div>
                <p class="focus-sound-label">专注期间白噪音</p>
                <div class="focus-sound-list">
                  <button v-for="option in NOISE_OPTIONS" :key="option.id" type="button" :class="{ on: sound.noise === option.id }" @click="pickNoise(option.id)">
                    <span>{{ option.label }}</span>
                    <Check v-if="sound.noise === option.id" :size="13" />
                  </button>
                </div>
                <button type="button" class="focus-sound-preview" :disabled="!sound.enabled || sound.noise === 'none'" @click="toggleNoisePreview">
                  <Square v-if="noisePreviewing" :size="12" />
                  <Play v-else :size="12" />
                  {{ noisePreviewing ? '停止试听' : '试听白噪音' }}
                </button>
              </div>
            </div>
            <div class="focus-menu-wrap">
              <button type="button" class="focus-icon-btn" aria-label="更多" title="更多" :aria-expanded="openMenu === 'more'" @click.stop="toggleMenu('more')"><MoreHorizontal :size="18" /></button>
              <div v-if="openMenu === 'more'" class="focus-menu is-page" @click.stop>
                <button type="button" @click="goStats"><ChartPie :size="15" />统计</button>
                <button type="button" @click="openSettings"><Settings2 :size="15" />专注设置</button>
              </div>
            </div>
          </div>
        </header>

        <p v-if="focus.error" class="focus-error" role="alert">{{ focus.error }}</p>

        <div class="focus-stage">
          <div class="focus-target">
            <button type="button" :aria-expanded="openMenu === 'preset'" @click.stop="toggleMenu('preset')">
              <span class="focus-target-icon">{{ activePreset?.icon || '🍅' }}</span>
              <span>{{ activePreset?.name || '专注' }}</span>
              <ChevronRight :size="14" />
            </button>
            <div v-if="openMenu === 'preset'" class="focus-menu is-presets" @click.stop>
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
              <button type="button" class="focus-primary" :disabled="focus.loading" @click="finishFocus('COMPLETED')">结束</button>
              <button type="button" class="focus-ghost" :disabled="focus.loading" @click="finishFocus('ABORTED')">放弃</button>
            </template>
          </div>
        </div>
      </section>

      <div
        class="focus-splitter"
        :class="{ 'is-dragging': splitDragging }"
        role="separator"
        aria-orientation="vertical"
        aria-label="拖动调整两栏宽度"
        :aria-valuenow="Math.round(splitRatio * 100)"
        aria-valuemin="34"
        aria-valuemax="72"
        tabindex="0"
        @pointerdown="startSplitDrag"
        @keydown="onSplitKeydown"
      ></div>

      <aside class="focus-side" :class="{ 'is-drawer': narrow, 'is-open': drawerOpen }">
        <div class="focus-side-head">
          <h2 class="focus-side-title">概览</h2>
          <button v-if="narrow" type="button" class="focus-icon-btn" aria-label="收起" title="收起" @click="drawerOpen = false"><X :size="16" /></button>
        </div>
        <div class="focus-cards">
          <div class="focus-card"><span>今日番茄</span><b>{{ focus.stats?.todayCount ?? 0 }}</b></div>
          <div class="focus-card"><span>今日专注时长</span><b>{{ durationLabel(focus.stats?.todayMinutes) }}</b></div>
          <div class="focus-card"><span>总番茄</span><b>{{ focus.stats?.totalCount ?? 0 }}</b></div>
          <div class="focus-card"><span>总专注时长</span><b>{{ durationLabel(focus.stats?.totalMinutes) }}</b></div>
        </div>

        <section class="focus-records" aria-label="专注记录">
          <header>
            <h2>专注记录</h2>
            <div class="focus-record-actions">
              <button type="button" class="focus-icon-btn" aria-label="添加专注记录" title="添加专注记录" @click="openRecordDialog"><Plus :size="16" /></button>
              <div class="focus-menu-wrap">
                <button type="button" class="focus-icon-btn" aria-label="记录选项" title="记录选项" :aria-expanded="openMenu === 'record'" @click.stop="toggleMenu('record')"><MoreHorizontal :size="16" /></button>
                <div v-if="openMenu === 'record'" class="focus-menu is-record" @click.stop>
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

          <div class="focus-records-scroll">
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
          </div>
        </section>
      </aside>
    </div>
    <div v-if="narrow && drawerOpen" class="focus-drawer-mask" @click="drawerOpen = false"></div>

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
import { ChartPie, Check, ChevronRight, Download, ListChecks, MoreHorizontal, PanelRightOpen, Play, Plus, Settings2, Square, Trash2, Volume2, VolumeX, X } from 'lucide-vue-next'
import { apiListFocusSessions } from '../../packages/api-client/src/index.js'
import { useTaskFocusStore } from '../stores/taskFocus.js'
import { useTasksStore } from '../stores/tasks.js'
import { createFocusPreset } from '../utils/focusPresets.js'
import {
  CHIME_OPTIONS, NOISE_OPTIONS, playFocusChime, readFocusSound, setFocusNoiseVolume,
  startFocusNoise, stopFocusNoise, writeFocusSound
} from '../utils/focusSound.js'
import { message } from '../services/message.js'

const router = useRouter()
const focus = useTaskFocusStore()
const tasksStore = useTasksStore()

const mode = ref('POMODORO')
const activePresetId = ref('')
// 同一时刻只允许一个浮层打开，避免多点几下面就叠在一起
const openMenu = ref('')
function toggleMenu(name) {
  openMenu.value = openMenu.value === name ? '' : name
}
const settingsOpen = ref(false)
const savingSettings = ref(false)
const batchMode = ref(false)
const selectedIds = ref([])

// ---- 声音：音量 / 完成提示音 / 专注期白噪音 ----
const sound = reactive(readFocusSound())
const noisePreviewing = ref(false)

function persistSound() {
  writeFocusSound({ ...sound })
}

function toggleSoundEnabled() {
  sound.enabled = !sound.enabled
  if (!sound.enabled) {
    stopFocusNoise()
    noisePreviewing.value = false
  } else if (focus.current && sound.noise !== 'none') {
    startFocusNoise(sound.noise, sound.volume)
  }
  persistSound()
}

function onVolumeInput() {
  setFocusNoiseVolume(sound.volume)
  persistSound()
}

function pickChime(id) {
  sound.chime = id
  persistSound()
  if (sound.enabled) playFocusChime(id, sound.volume)
}

function pickNoise(id) {
  sound.noise = id
  persistSound()
  noisePreviewing.value = false
  // 没有进行中的专注时只保存选择，不空放声音
  if (!focus.current || !sound.enabled || id === 'none') {
    stopFocusNoise()
    return
  }
  startFocusNoise(id, sound.volume)
}

function toggleNoisePreview() {
  if (!sound.enabled || sound.noise === 'none') return
  if (noisePreviewing.value) {
    stopFocusNoise()
    noisePreviewing.value = false
    // 试听结束回到真实会话的噪音状态
    if (focus.current) startFocusNoise(sound.noise, sound.volume)
    return
  }
  noisePreviewing.value = true
  startFocusNoise(sound.noise, sound.volume)
}

// ---- 窄屏降级：只留左栏，右栏变抽屉 ----
const narrow = ref(false)
const drawerOpen = ref(false)
let narrowQuery = null

// ---- 两栏宽度：可拖动，宽度存本地 ----
const SPLIT_KEY = 'task_focus_split_v1'
function readSplit() {
  try {
    const value = Number(localStorage.getItem(SPLIT_KEY))
    if (Number.isFinite(value) && value >= 0.34 && value <= 0.72) return value
  } catch { /* 隐私模式读不到，用默认值 */ }
  return 0.56
}
const splitRatio = ref(readSplit())
const splitDragging = ref(false)
const shellEl = ref(null)
const splitColumns = computed(() => `${splitRatio.value}fr 8px ${1 - splitRatio.value}fr`)
let dragRect = null

function persistSplit() {
  try {
    localStorage.setItem(SPLIT_KEY, String(splitRatio.value))
  } catch { /* 忽略写入失败 */ }
}

function applySplitRatio(value) {
  splitRatio.value = Math.min(0.72, Math.max(0.34, value))
}

function startSplitDrag(event) {
  if (narrow.value) return
  dragRect = shellEl.value?.getBoundingClientRect() || null
  if (!dragRect) return
  splitDragging.value = true
  event.preventDefault()
  window.addEventListener('pointermove', onSplitMove)
  window.addEventListener('pointerup', stopSplitDrag)
  document.body.style.userSelect = 'none'
  document.body.style.cursor = 'col-resize'
}

function onSplitMove(event) {
  if (!splitDragging.value || !dragRect) return
  const usable = Math.max(1, dragRect.width - 8)
  applySplitRatio((event.clientX - dragRect.left) / usable)
}

function stopSplitDrag() {
  if (!splitDragging.value) return
  splitDragging.value = false
  dragRect = null
  window.removeEventListener('pointermove', onSplitMove)
  window.removeEventListener('pointerup', stopSplitDrag)
  document.body.style.userSelect = ''
  document.body.style.cursor = ''
  persistSplit()
}

function onSplitKeydown(event) {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
  event.preventDefault()
  applySplitRatio(splitRatio.value + (event.key === 'ArrowLeft' ? -0.02 : 0.02))
  persistSplit()
}

function syncNarrow(matches) {
  narrow.value = matches
  if (matches) stopSplitDrag()
  else drawerOpen.value = false
}

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
  const fmt = date => `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  return end ? `${fmt(start)}-${fmt(end)}` : `${fmt(start)}-进行中`
}

function dateLabel(date) {
  const now = new Date()
  const prefix = date.toDateString() === now.toDateString() ? '今天 · ' : ''
  return `${prefix}${date.getMonth() + 1}月${date.getDate()}日`
}

function switchMode(value) {
  mode.value = value
  openMenu.value = ''
}

function pickPreset(preset) {
  activePresetId.value = preset.id
  mode.value = preset.mode === 'STOPWATCH' ? 'STOPWATCH' : 'POMODORO'
  openMenu.value = ''
}

function openPresetDialog() {
  openMenu.value = ''
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
    // 开始是用户手势，此时建立 AudioContext 才不会被浏览器拦
    if (sound.enabled && sound.noise !== 'none') startFocusNoise(sound.noise, sound.volume)
  } catch { /* store 已写入 error */ }
}

async function finishFocus(kind) {
  if (focus.loading) return
  const before = focus.current
  if (!before) return
  try {
    if (kind === 'COMPLETED') await focus.complete()
    else await focus.abort()
  } finally {
    stopFocusNoise()
    noisePreviewing.value = false
    // 只有正常结束才响，放弃不打断心情
    if (kind === 'COMPLETED' && !focus.current && !focus.error && sound.enabled) {
      playFocusChime(sound.chime, sound.volume)
    }
  }
}

function openSettings() {
  openMenu.value = ''
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
  openMenu.value = ''
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
  openMenu.value = ''
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
  openMenu.value = ''
  if (!focus.sessions.length) return
  if (!window.confirm(`清空当前列表里的 ${focus.sessions.length} 条专注记录？此操作不可撤销。`)) return
  await focus.removeMany([...focus.sessions])
  message.success('已清空')
}

function openExport() {
  openMenu.value = ''
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
  openMenu.value = ''
  router.push('/tasks/focus/stats')
}

function closeMenus(event) {
  if (event?.target?.closest?.('.focus-menu-wrap') || event?.target?.closest?.('.focus-target')) return
  openMenu.value = ''
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

const onNarrowChange = event => syncNarrow(event.matches)

onMounted(async () => {
  window.addEventListener('pointerdown', closeMenus)
  narrowQuery = window.matchMedia('(max-width: 1080px)')
  syncNarrow(narrowQuery.matches)
  narrowQuery.addEventListener('change', onNarrowChange)
  await tasksStore.init({ waitForRemote: false })
  await focus.refresh()
  // 已有进行中的会话时，模式要跟它一致（正计时/番茄）
  if (focus.current) mode.value = Number(focus.current.plannedMinutes) ? 'POMODORO' : 'STOPWATCH'
  if (focus.presets.length) activePresetId.value = focus.presets[0].id
})

onBeforeUnmount(() => {
  window.removeEventListener('pointerdown', closeMenus)
  narrowQuery?.removeEventListener('change', onNarrowChange)
  stopSplitDrag()
  stopFocusNoise()
})
</script>

<style scoped>
.focus-page { display: flex; flex-direction: column; min-height: 100%; color: var(--ink); }

/* 左栏：计时区；顶部是「标题 / 模式居中 / 操作」三段 */
.focus-pane {
  display: flex; min-width: 0; flex-direction: column; padding: 18px 26px 24px;
  background:
    radial-gradient(118% 74% at 50% 112%, color-mix(in srgb, var(--accent) 10%, transparent), transparent 64%),
    linear-gradient(color-mix(in srgb, var(--paper) 46%, transparent), transparent 44%);
}
.focus-pane-head { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 12px; }
.focus-pane-head h1 { justify-self: start; margin: 0; font-size: 1.16rem; font-weight: 700; }
.focus-modes { justify-self: center; display: flex; gap: 2px; padding: 3px; border-radius: 9px; background: var(--paper); }
.focus-modes button { min-height: 26px; padding: 0 12px; border: 0; border-radius: 7px; background: transparent; color: var(--muted); font: inherit; font-size: 12.5px; cursor: pointer; }
.focus-modes button.on { background: var(--card); color: var(--ink); box-shadow: 0 1px 3px rgb(15 23 42 / 12%); }
.focus-head-actions { justify-self: end; display: flex; align-items: center; gap: 2px; }
.focus-icon-btn { display: grid; width: 30px; height: 30px; place-items: center; border: 0; border-radius: 8px; background: transparent; color: var(--ink2); cursor: pointer; }
.focus-icon-btn:hover { background: var(--paper); color: var(--ink); }
.focus-menu-wrap { position: relative; }

.focus-shell { display: grid; flex: 1; grid-template-columns: var(--focus-split, 56fr 8px 44fr); min-height: calc(100vh - 1px); }

/* 中间分界线：可拖动改宽度，命中区域 8px 但视觉只有 1px */
.focus-splitter { position: relative; cursor: col-resize; touch-action: none; outline: none; }
.focus-splitter::before { content: ''; position: absolute; top: 0; bottom: 0; left: 50%; width: 1px; transform: translateX(-50%); background: var(--line); transition: width .12s ease, background .12s ease; }
.focus-splitter:hover::before, .focus-splitter.is-dragging::before { width: 2px; background: color-mix(in srgb, var(--accent) 70%, var(--line)); }
.focus-splitter:focus-visible::before { width: 2px; background: var(--accent); }

.focus-stage { display: flex; flex: 1; flex-direction: column; align-items: center; justify-content: center; gap: 12px; padding: 10px 0 8px; }
.focus-target { position: relative; }
.focus-target > button { display: inline-flex; align-items: center; gap: 6px; padding: 5px 10px 5px 8px; border: 1px solid var(--line); border-radius: 999px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer; transition: border-color .15s ease, background .15s ease; }
.focus-target > button:hover { border-color: var(--line2); background: var(--paper); color: var(--ink); }
.focus-target-icon { font-size: 14px; }

.focus-ring { position: relative; display: grid; width: min(360px, 42vh); aspect-ratio: 1; place-items: center; margin: 2px 0; }
.focus-ring svg { position: absolute; inset: 0; width: 100%; height: 100%; transform: rotate(-90deg); }
.ring-track { fill: none; stroke: color-mix(in srgb, var(--ink) 8%, transparent); stroke-width: 2; }
.ring-progress { fill: none; stroke: color-mix(in srgb, var(--accent) 72%, transparent); stroke-width: 3; stroke-linecap: round; transition: stroke-dashoffset .4s linear; }
.focus-ring.is-running .ring-progress { stroke: var(--accent); }
.ring-ticks line { stroke: color-mix(in srgb, var(--ink) 16%, transparent); stroke-width: 2; stroke-linecap: round; }
.ring-ticks line.on { stroke: var(--accent); }
.focus-ring strong { font-size: clamp(2.6rem, 5.2vw, 3.6rem); font-weight: 500; font-variant-numeric: tabular-nums; letter-spacing: .01em; }

.focus-stage-hint { margin: 0; color: var(--muted); font-size: 12.5px; }
.focus-actions { display: flex; gap: 10px; margin-top: 16px; }
.focus-actions .focus-primary { min-width: 116px; min-height: 40px; padding: 0 28px; border-radius: 999px; }
.focus-actions .focus-ghost { min-height: 40px; border-radius: 999px; }
.focus-primary { min-width: 128px; min-height: 38px; padding: 0 22px; border: 1px solid var(--accent); border-radius: 10px; background: var(--accent); color: var(--accent-contrast, #fff); font: inherit; font-size: 13.5px; cursor: pointer; }
.focus-primary:disabled { opacity: .55; cursor: default; }
.focus-ghost { min-height: 38px; padding: 0 18px; border: 1px solid var(--line2); border-radius: 10px; background: transparent; color: var(--ink2); font: inherit; font-size: 13.5px; cursor: pointer; }
.focus-ghost:hover { background: var(--paper); }

.focus-side { display: flex; min-width: 0; flex-direction: column; gap: 12px; padding: 18px 26px 16px; }
.focus-side-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.focus-side-title { margin: 0; font-size: 1.06rem; font-weight: 700; }
.focus-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.focus-card { display: grid; gap: 8px; padding: 12px 14px; border-radius: 12px; background: var(--paper); }
.focus-card span { color: var(--muted); font-size: 11.5px; }
.focus-card b { font-size: 1.16rem; font-weight: 700; font-variant-numeric: tabular-nums; }

.focus-records { display: flex; flex: 1; flex-direction: column; gap: 8px; min-height: 0; margin-top: 4px; }
.focus-records > header { display: flex; flex: 0 0 auto; align-items: center; justify-content: space-between; }
.focus-records > header h2 { margin: 0; font-size: 1.06rem; font-weight: 700; }
.focus-record-actions { display: flex; gap: 2px; }
.focus-records-scroll { flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior: contain; scrollbar-width: thin; }
.focus-group h3 { margin: 12px 0 4px; color: var(--muted); font-size: 11.5px; font-weight: 600; }
.focus-group:first-child h3 { margin-top: 2px; }
.focus-group ul { display: grid; gap: 0; margin: 0; padding: 0; list-style: none; }
.focus-group li { position: relative; display: flex; align-items: flex-start; gap: 10px; padding: 6px 4px 6px 0; border-radius: 8px; }
.focus-group li:hover { background: color-mix(in srgb, var(--paper) 82%, transparent); }
.focus-group li.is-aborted { opacity: .55; }
/* 时间轴：实心圆点 + 向下延伸的连线 */
.focus-group li:not(:last-child)::after { content: ''; position: absolute; top: 21px; bottom: -2px; left: 4px; width: 1px; background: color-mix(in srgb, var(--accent) 24%, transparent); }
.focus-row-check { margin-top: 2px; }
.focus-row-check input { width: 13px; height: 13px; }
.focus-row-dot { position: relative; z-index: 1; flex: 0 0 auto; width: 9px; height: 9px; margin-top: 7px; border-radius: 50%; background: var(--accent); box-shadow: 0 0 0 3px var(--card); }
.focus-group li.is-aborted .focus-row-dot { background: var(--line2); }
.focus-row-main { display: grid; flex: 1; gap: 3px; min-width: 0; }
.focus-row-time { color: var(--ink); font-size: 12.5px; font-variant-numeric: tabular-nums; }
.focus-row-task { overflow: hidden; color: var(--muted); font-size: 11.5px; text-overflow: ellipsis; white-space: nowrap; }
.focus-row-task em { font-style: normal; }
.focus-row-duration { flex: 0 0 auto; margin-top: 1px; color: var(--ink2); font-size: 12px; font-variant-numeric: tabular-nums; }
.focus-more { display: block; width: 100%; margin: 10px 0 2px; padding: 7px 16px; border: 1px solid var(--line2); border-radius: 9px; background: transparent; color: var(--ink2); font: inherit; font-size: 12px; cursor: pointer; }
.focus-more:hover { background: var(--paper); color: var(--ink); }
.focus-empty { padding: 22px 0; color: var(--muted); font-size: 12.5px; text-align: center; }

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

/* 声音浮层：沿用 .focus-menu 的容器语言（同样的描边/圆角/阴影），内部排成表单 */
.focus-menu.is-sound { top: 34px; right: 0; display: block; width: 244px; padding: 8px; }
.focus-sound-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 2px 6px 8px; color: var(--ink); font-size: 12.5px; font-weight: 700; }
/* 需要盖掉 .focus-menu button 的通用按钮样式（min-height/padding/flex） */
.focus-menu .focus-switch { position: relative; display: block; flex: 0 0 auto; width: 38px; height: 21px; min-height: 21px; padding: 0; border: 0; border-radius: 11px; background: var(--line2); cursor: pointer; transition: background .15s ease; }
.focus-menu .focus-switch[aria-checked='true'] { background: var(--accent); }
.focus-menu .focus-switch i { position: absolute; top: 2px; left: 2px; width: 17px; height: 17px; border-radius: 50%; background: #fff; transition: transform .15s ease; }
.focus-menu .focus-switch[aria-checked='true'] i { transform: translateX(17px); }
.focus-sound-volume { display: flex; align-items: center; gap: 8px; padding: 2px 6px 6px; color: var(--muted); font-size: 11.5px; }
.focus-sound-volume.is-off { opacity: .45; }
.focus-sound-volume input { flex: 1; min-width: 0; height: 3px; accent-color: var(--accent); cursor: pointer; }
.focus-sound-volume em { min-width: 30px; color: var(--ink2); font-style: normal; font-variant-numeric: tabular-nums; text-align: right; }
.focus-sound-label { margin: 6px 0 3px; padding: 0 6px; color: var(--muted); font-size: 11px; }
.focus-sound-list { display: grid; }
.focus-sound-list button { min-height: 30px; }
.focus-sound-preview { display: flex; align-items: center; justify-content: center; gap: 6px; width: calc(100% - 4px); margin: 8px 2px 2px; min-height: 30px; border: 1px solid var(--line2); border-radius: 8px; background: transparent; color: var(--ink2); font: inherit; font-size: 12px; cursor: pointer; }
.focus-sound-preview:hover:not(:disabled) { background: var(--paper); color: var(--ink); }
.focus-sound-preview:disabled { opacity: .5; cursor: default; }

.focus-drawer-mask { position: fixed; z-index: 88; inset: 0; background: rgb(15 23 42 / 34%); }

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
  /* 容不下两栏时：只留左栏，右栏收成从右侧滑出的抽屉 */
  .focus-shell { grid-template-columns: 1fr; grid-template-rows: minmax(0, 1fr); min-height: calc(100dvh - 1px); }
  .focus-splitter { display: none; }
  .focus-pane { justify-content: stretch; border-bottom: 1px solid var(--line); padding: 14px 16px calc(86px + env(safe-area-inset-bottom)); }
  .focus-side.is-drawer {
    position: fixed; z-index: 90; top: 0; right: 0;
    width: min(400px, 92vw); height: 100dvh; overflow: hidden;
    padding: 16px 16px calc(16px + env(safe-area-inset-bottom));
    border-left: 1px solid var(--line); background: var(--card);
    box-shadow: -18px 0 44px rgb(15 23 42 / 18%);
    transform: translateX(102%); transition: transform .24s cubic-bezier(.22, 1, .36, 1);
  }
  .focus-side.is-drawer.is-open { transform: none; }
}
@media (prefers-reduced-motion: reduce) {
  .focus-side.is-drawer { transition: none; }
}
</style>
