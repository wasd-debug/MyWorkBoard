<template>
  <Teleport to="body">
    <div v-if="open" class="focus-batch-mask" @click.self="close">
      <section class="focus-batch-dialog" role="dialog" aria-label="批量管理">
        <header>
          <h2>批量管理</h2>
          <button type="button" class="focus-batch-close" aria-label="关闭" @click="close"><X :size="16" /></button>
        </header>

        <div class="focus-batch-filters">
          <div class="focus-batch-filter">
            <button type="button" :class="{ on: menu === 'date' }" @click.stop="menu = menu === 'date' ? '' : 'date'">日期<ChevronDown :size="13" /></button>
            <div v-if="menu === 'date'" class="focus-batch-menu" @click.stop>
              <button v-for="option in DATE_OPTIONS" :key="option.id" type="button" :class="{ on: dateFilter === option.id }" @click="pickDate(option.id)">
                <span>{{ option.label }}</span>
                <Check v-if="dateFilter === option.id" :size="14" />
              </button>
              <div v-if="dateFilter === 'custom'" class="focus-batch-custom">
                <input v-model="customFrom" type="date" aria-label="开始日期" />
                <span>—</span>
                <input v-model="customTo" type="date" aria-label="结束日期" />
              </div>
            </div>
          </div>

          <div class="focus-batch-filter">
            <button type="button" :class="{ on: menu === 'duration' }" @click.stop="menu = menu === 'duration' ? '' : 'duration'">时长<ChevronDown :size="13" /></button>
            <div v-if="menu === 'duration'" class="focus-batch-menu" @click.stop>
              <button v-for="option in DURATION_OPTIONS" :key="option.id" type="button" :class="{ on: durationFilter === option.id }" @click="pickDuration(option.id)">
                <span>{{ option.label }}</span>
                <Check v-if="durationFilter === option.id" :size="14" />
              </button>
              <div v-if="durationFilter === 'custom'" class="focus-batch-range">
                <p>输入专注时长范围（5~720 分钟）</p>
                <div>
                  <input v-model.number="customMin" type="number" min="5" max="720" placeholder="最小" aria-label="最小分钟" />
                  <span>—</span>
                  <input v-model.number="customMax" type="number" min="5" max="720" placeholder="最大" aria-label="最大分钟" />
                </div>
              </div>
            </div>
          </div>

          <div class="focus-batch-filter">
            <button type="button" :class="{ on: menu === 'link' }" @click.stop="menu = menu === 'link' ? '' : 'link'">关联状态<ChevronDown :size="13" /></button>
            <div v-if="menu === 'link'" class="focus-batch-menu" @click.stop>
              <button v-for="option in LINK_OPTIONS" :key="option.id" type="button" :class="{ on: linkFilter === option.id }" @click="linkFilter = option.id; menu = ''">
                <span>{{ option.label }}</span>
                <Check v-if="linkFilter === option.id" :size="14" />
              </button>
            </div>
          </div>
        </div>

        <div class="focus-batch-body">
          <p v-if="loading" class="focus-batch-empty">加载中…</p>
          <p v-else-if="!groups.length" class="focus-batch-empty">没有符合条件的记录</p>
          <section v-for="group in groups" :key="group.key" class="focus-batch-group">
            <label class="focus-batch-day">
              <input type="checkbox" :checked="group.allSelected" :indeterminate.prop="group.someSelected" @change="toggleGroup(group)" />
              <b>{{ group.label }}</b>
            </label>
            <ul>
              <li v-for="session in group.items" :key="session.publicId" :class="{ 'is-selected': selected.includes(session.publicId) }">
                <input type="checkbox" :checked="selected.includes(session.publicId)" :aria-label="`选择 ${timeRange(session)}`" @change="toggleOne(session)" />
                <button type="button" class="focus-batch-row" @click="openDetail(session)">
                  <span class="focus-batch-clock" aria-hidden="true"><Timer :size="13" /></span>
                  <span class="focus-batch-row-main">
                    <span class="focus-batch-row-time">{{ timeRange(session) }}</span>
                    <span class="focus-batch-row-title">{{ session.taskTitle || '未关联任务' }}</span>
                  </span>
                  <span class="focus-batch-row-duration">{{ shortMinutes(session.actualMinutes) }}</span>
                </button>
              </li>
            </ul>
          </section>
        </div>

        <footer class="focus-batch-actions">
          <button type="button" class="focus-batch-select-all" @click="toggleAll">{{ allSelected ? '取消全选' : '全选' }}</button>
          <button type="button" class="focus-batch-delete" :disabled="!selected.length || busy" @click="removeSelected">{{ busy ? '删除中…' : `删除${selected.length ? ` (${selected.length})` : ''}` }}</button>
        </footer>

        <div v-if="detail" class="focus-detail-mask" @click.self="detail = null">
          <section class="focus-detail" role="dialog" aria-label="专注记录详情">
            <div class="focus-detail-head">
              <span class="focus-detail-icon" aria-hidden="true"><Timer :size="15" /></span>
              <button type="button" class="focus-detail-task" @click.stop="detailPicker = !detailPicker">{{ detail.taskTitle || '未关联任务' }}</button>
              <button type="button" class="focus-detail-link" :class="{ on: detailPicker }" aria-label="关联任务" @click.stop="detailPicker = !detailPicker"><Link2 :size="16" /></button>
            </div>
            <FocusTargetPicker v-if="detailPicker" inline default-tab="task" @pick="relinkTask" />
            <p class="focus-detail-line"><CircleDot :size="14" />{{ fullRange(detail) }}</p>
            <p class="focus-detail-line"><Clock3 :size="14" />{{ detailMinutes(detail) }} 分钟</p>
            <textarea v-model="detailNote" class="focus-detail-note" maxlength="500" placeholder="记录你的想法..."></textarea>
            <p v-if="detailError" class="focus-detail-error" role="alert">{{ detailError }}</p>
            <footer>
              <button type="button" class="focus-detail-save" :disabled="detailBusy" @click="saveDetail">{{ detailBusy ? '保存中…' : '保存' }}</button>
              <button type="button" class="focus-detail-remove" :disabled="detailBusy" @click="removeDetail">删除这条</button>
            </footer>
          </section>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Check, ChevronDown, CircleDot, Clock3, Link2, Timer, X } from 'lucide-vue-next'
import FocusTargetPicker from './FocusTargetPicker.vue'
import { apiListFocusSessions, apiUpdateFocusSession } from '../../../packages/api-client/src/index.js'
import { useTaskFocusStore } from '../../stores/taskFocus.js'
import { message } from '../../services/message.js'

const props = defineProps({
  open: { type: Boolean, default: false }
})
const emit = defineEmits(['close', 'changed'])

const focus = useTaskFocusStore()

const DATE_OPTIONS = [
  { id: 'all', label: '所有' },
  { id: 'today', label: '今天' },
  { id: 'yesterday', label: '昨天' },
  { id: 'thisWeek', label: '本周' },
  { id: 'lastWeek', label: '上周' },
  { id: 'thisMonth', label: '本月' },
  { id: 'lastMonth', label: '上月' },
  { id: 'custom', label: '自定义' }
]
const DURATION_OPTIONS = [
  { id: 'all', label: '所有' },
  { id: '10', label: '小于 10 分钟' },
  { id: '25', label: '小于 25 分钟' },
  { id: '45', label: '小于 45 分钟' },
  { id: '60', label: '小于 60 分钟' },
  { id: 'custom', label: '自定义' }
]
const LINK_OPTIONS = [
  { id: 'all', label: '所有' },
  { id: 'linked', label: '已关联' },
  { id: 'unlinked', label: '未关联' }
]

const menu = ref('')
const dateFilter = ref('all')
const durationFilter = ref('all')
const linkFilter = ref('all')
const customFrom = ref('')
const customTo = ref('')
const customMin = ref(null)
const customMax = ref(null)

const sessions = ref([])
const loading = ref(false)
const selected = ref([])
const busy = ref(false)

const detail = ref(null)
const detailNote = ref('')
const detailPicker = ref(false)
const detailBusy = ref(false)
const detailError = ref('')

function pad(value) {
  return String(value).padStart(2, '0')
}

function dayKey(value) {
  const date = new Date(value)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function startOfWeek(date) {
  const copy = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  copy.setDate(copy.getDate() - ((copy.getDay() + 6) % 7))
  return copy
}

function dateRange() {
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const day = 86400000
  switch (dateFilter.value) {
    case 'today': return [today, new Date(today.getTime() + day - 1)]
    case 'yesterday': return [new Date(today.getTime() - day), new Date(today.getTime() - 1)]
    case 'thisWeek': return [startOfWeek(today), new Date(today.getTime() + day - 1)]
    case 'lastWeek': {
      const start = startOfWeek(new Date(today.getTime() - 7 * day))
      return [start, new Date(start.getTime() + 7 * day - 1)]
    }
    case 'thisMonth': return [new Date(now.getFullYear(), now.getMonth(), 1), new Date(today.getTime() + day - 1)]
    case 'lastMonth': return [new Date(now.getFullYear(), now.getMonth() - 1, 1), new Date(now.getFullYear(), now.getMonth(), 1)]
    case 'custom': {
      const from = customFrom.value ? new Date(`${customFrom.value}T00:00:00`) : null
      const to = customTo.value ? new Date(`${customTo.value}T23:59:59`) : null
      return [from, to]
    }
    default: return [null, null]
  }
}

const filtered = computed(() => {
  const [from, to] = dateRange()
  const min = durationFilter.value === 'custom' ? Number(customMin.value) || 0 : 0
  const max = durationFilter.value === 'custom'
    ? (Number(customMax.value) || Number.MAX_SAFE_INTEGER)
    : (durationFilter.value === 'all' ? Number.MAX_SAFE_INTEGER : Number(durationFilter.value))
  return sessions.value.filter(session => {
    const at = new Date(session.startedAt)
    if (from && at < from) return false
    if (to && at > to) return false
    const minutes = Number(session.actualMinutes) || 0
    if (min && minutes < min) return false
    if (max !== Number.MAX_SAFE_INTEGER && minutes >= max) return false
    if (linkFilter.value === 'linked' && !session.taskId) return false
    if (linkFilter.value === 'unlinked' && session.taskId) return false
    return true
  })
})

const groups = computed(() => {
  const map = new Map()
  for (const session of filtered.value) {
    const key = dayKey(session.startedAt)
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(session)
  }
  return [...map.entries()]
    .sort((a, b) => (a[0] < b[0] ? 1 : -1))
    .map(([key, items]) => {
      const sorted = [...items].sort((a, b) => new Date(b.startedAt) - new Date(a.startedAt))
      const ids = sorted.map(item => item.publicId)
      const picked = ids.filter(id => selected.value.includes(id)).length
      const [year, month, day] = key.split('-').map(Number)
      return {
        key,
        label: `${month}月${day}日`,
        items: sorted,
        allSelected: picked === ids.length && ids.length > 0,
        someSelected: picked > 0 && picked < ids.length,
        ids
      }
    })
})

const allSelected = computed(() => filtered.value.length > 0 && selected.value.length === filtered.value.length)

function pickDate(id) {
  dateFilter.value = id
  if (id !== 'custom') menu.value = ''
}

function pickDuration(id) {
  durationFilter.value = id
  if (id !== 'custom') menu.value = ''
}

function toggleOne(session) {
  const id = session.publicId
  selected.value = selected.value.includes(id)
    ? selected.value.filter(item => item !== id)
    : [...selected.value, id]
}

function toggleGroup(group) {
  const rest = selected.value.filter(id => !group.ids.includes(id))
  selected.value = group.allSelected ? rest : [...rest, ...group.ids]
}

function toggleAll() {
  selected.value = allSelected.value ? [] : filtered.value.map(item => item.publicId)
}

function timeRange(session) {
  const fmt = date => `${pad(date.getHours())}:${pad(date.getMinutes())}`
  const start = new Date(session.startedAt)
  if (!session.endedAt) return `${fmt(start)} - 进行中`
  return `${fmt(start)} - ${fmt(new Date(session.endedAt))}`
}

function fullRange(session) {
  const fmt = date => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
  const start = fmt(new Date(session.startedAt))
  if (!session.endedAt) return `${start} - 进行中`
  return `${start} - ${fmt(new Date(session.endedAt))}`
}

function detailMinutes(session) {
  const value = Number(session.actualMinutes) || 0
  return value
}

function shortMinutes(minutes) {
  const value = Number(minutes || 0)
  const hours = Math.floor(value / 60)
  const rest = value % 60
  if (!hours) return `${rest}m`
  return rest ? `${hours}h${rest}m` : `${hours}h`
}

async function load() {
  loading.value = true
  try {
    // 批量管理要看全部历史，不能只取本周
    sessions.value = await apiListFocusSessions({})
  } catch {
    sessions.value = []
  } finally {
    loading.value = false
  }
}

watch(() => props.open, value => {
  if (!value) return
  menu.value = ''
  selected.value = []
  detail.value = null
  load()
})

function close() {
  emit('close')
}

function openDetail(session) {
  detail.value = session
  detailNote.value = session.note || ''
  detailPicker.value = false
  detailError.value = ''
}

async function relinkTask(item) {
  if (!detail.value) return
  detail.value = { ...detail.value, taskId: item.kind === 'preset' ? null : item.id, taskTitle: item.title }
  detailPicker.value = false
}

async function saveDetail() {
  if (!detail.value || detailBusy.value) return
  detailBusy.value = true
  detailError.value = ''
  try {
    const updated = await apiUpdateFocusSession(
      detail.value.publicId,
      { taskId: detail.value.taskId || null, note: detailNote.value },
      detail.value.revision
    )
    const index = sessions.value.findIndex(item => item.publicId === updated.publicId)
    if (index >= 0) sessions.value[index] = updated
    detail.value = null
    message.success('已保存')
    emit('changed')
  } catch (error) {
    detailError.value = error?.response?.data?.detail || '保存失败'
  } finally {
    detailBusy.value = false
  }
}

async function removeDetail() {
  if (!detail.value || detailBusy.value) return
  if (!window.confirm('删除这条专注记录？')) return
  detailBusy.value = true
  try {
    await focus.remove(detail.value)
    sessions.value = sessions.value.filter(item => item.publicId !== detail.value.publicId)
    detail.value = null
    message.success('已删除')
    emit('changed')
  } catch (error) {
    detailError.value = error?.response?.data?.detail || '删除失败'
  } finally {
    detailBusy.value = false
  }
}

async function removeSelected() {
  if (!selected.value.length || busy.value) return
  if (!window.confirm(`删除选中的 ${selected.value.length} 条专注记录？`)) return
  busy.value = true
  try {
    const list = sessions.value.filter(item => selected.value.includes(item.publicId))
    await focus.removeMany(list)
    sessions.value = sessions.value.filter(item => !selected.value.includes(item.publicId))
    selected.value = []
    message.success('已删除')
    emit('changed')
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.focus-batch-mask { position: fixed; z-index: 130; inset: 0; display: grid; place-items: center; padding: 20px; background: rgb(15 23 42 / 42%); }
.focus-batch-dialog { display: flex; width: min(600px, calc(100vw - 32px)); max-height: calc(100dvh - 40px); flex-direction: column; border-radius: 18px; background: var(--card); box-shadow: 0 30px 70px rgb(15 23 42 / 30%); }
.focus-batch-dialog > header { position: relative; display: flex; align-items: center; justify-content: center; padding: 18px 20px 12px; }
.focus-batch-dialog h2 { margin: 0; font-size: 1.06rem; font-weight: 700; }
.focus-batch-close { position: absolute; top: 12px; right: 14px; display: grid; width: 28px; height: 28px; place-items: center; border: 0; border-radius: 8px; background: transparent; color: var(--muted); cursor: pointer; }
.focus-batch-close:hover { background: var(--paper); color: var(--ink); }
.focus-batch-filters { display: flex; flex-wrap: wrap; gap: 8px; padding: 0 20px 12px; }
.focus-batch-filter { position: relative; }
.focus-batch-filter > button { display: inline-flex; align-items: center; gap: 4px; min-height: 30px; padding: 0 10px; border: 1px solid var(--line2); border-radius: 8px; background: transparent; color: var(--ink2); font: inherit; font-size: 12.5px; cursor: pointer; }
.focus-batch-filter > button.on { border-color: var(--accent); color: var(--ink); }
.focus-batch-menu { position: absolute; z-index: 10; top: calc(100% + 6px); left: 0; display: grid; min-width: 160px; padding: 6px; border: 1px solid var(--line2); border-radius: 12px; background: var(--card); box-shadow: 0 18px 40px rgb(15 23 42 / 20%); }
.focus-batch-menu > button { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 32px; padding: 0 8px; border: 0; border-radius: 8px; background: transparent; color: var(--ink); font: inherit; font-size: 12.5px; text-align: left; cursor: pointer; }
.focus-batch-menu > button:hover { background: var(--paper); }
.focus-batch-menu > button.on { color: var(--accent); }
.focus-batch-custom, .focus-batch-range { display: grid; gap: 6px; padding: 6px 8px 2px; }
.focus-batch-custom { grid-template-columns: 1fr auto 1fr; align-items: center; }
.focus-batch-custom input, .focus-batch-range input { min-height: 30px; padding: 0 8px; border: 1px solid var(--line2); border-radius: 7px; background: var(--paper); color: var(--ink); font: inherit; font-size: 12px; }
.focus-batch-range p { margin: 0; color: var(--muted); font-size: 11.5px; }
.focus-batch-range > div { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 6px; }
.focus-batch-body { flex: 1; min-height: 0; overflow-y: auto; padding: 0 20px 8px; }
.focus-batch-group { margin-bottom: 6px; }
.focus-batch-day { display: flex; align-items: center; gap: 8px; padding: 8px 0 4px; cursor: pointer; }
.focus-batch-day b { font-size: 13px; }
.focus-batch-group ul { display: grid; margin: 0; padding: 0; list-style: none; }
.focus-batch-group li { display: flex; align-items: flex-start; gap: 8px; padding: 6px 0 6px 6px; border-radius: 10px; }
.focus-batch-group li:hover { background: color-mix(in srgb, var(--paper) 80%, transparent); }
.focus-batch-group li > input { margin-top: 12px; }
.focus-batch-row { display: flex; flex: 1; min-width: 0; align-items: flex-start; gap: 10px; padding: 4px 6px; border: 0; border-radius: 8px; background: transparent; color: inherit; font: inherit; text-align: left; cursor: pointer; }
.focus-batch-clock { display: grid; width: 26px; height: 26px; flex: 0 0 auto; place-items: center; border-radius: 50%; background: color-mix(in srgb, var(--accent) 20%, transparent); color: var(--accent); }
.focus-batch-row-main { display: grid; flex: 1; min-width: 0; gap: 4px; }
.focus-batch-row-time { color: var(--ink2); font-size: 12.5px; font-variant-numeric: tabular-nums; }
.focus-batch-row-title { color: var(--ink); font-size: 13px; }
.focus-batch-row-duration { flex: 0 0 auto; color: var(--muted); font-size: 12.5px; font-variant-numeric: tabular-nums; }
.focus-batch-empty { padding: 26px 0; color: var(--muted); font-size: 12.5px; text-align: center; }
.focus-batch-actions { display: flex; align-items: center; justify-content: space-between; padding: 12px 20px 16px; border-top: 1px solid var(--line); }
.focus-batch-select-all { border: 0; background: transparent; color: var(--accent); font: inherit; font-size: 13px; cursor: pointer; }
.focus-batch-delete { min-height: 32px; padding: 0 14px; border: 0; border-radius: 8px; background: transparent; color: var(--down, #e34d59); font: inherit; font-size: 13px; font-weight: 700; cursor: pointer; }
.focus-batch-delete:disabled { color: var(--muted); cursor: default; }

.focus-detail-mask { position: fixed; z-index: 20; inset: 0; display: grid; place-items: center; padding: 20px; background: rgb(15 23 42 / 38%); }
.focus-detail { display: grid; width: min(460px, 100%); gap: 12px; padding: 18px; border-radius: 16px; background: var(--card); box-shadow: 0 26px 60px rgb(15 23 42 / 28%); }
.focus-detail-head { display: flex; align-items: center; gap: 8px; }
.focus-detail-icon { display: grid; width: 26px; height: 26px; place-items: center; border-radius: 50%; background: var(--paper); color: var(--muted); }
.focus-detail-task { flex: 1; min-width: 0; overflow: hidden; border: 0; background: transparent; color: var(--ink); font: inherit; font-size: 13.5px; text-align: left; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; }
.focus-detail-link { display: grid; width: 30px; height: 30px; place-items: center; border: 0; border-radius: 8px; background: transparent; color: var(--muted); cursor: pointer; }
.focus-detail-link.on, .focus-detail-link:hover { background: var(--paper); color: var(--ink); }
.focus-detail-line { display: flex; align-items: center; gap: 8px; margin: 0; color: var(--ink2); font-size: 13px; font-variant-numeric: tabular-nums; }
.focus-detail-line svg { color: var(--muted); }
.focus-detail-note { min-height: 110px; padding: 10px 12px; border: 0; border-radius: 10px; background: var(--paper); color: var(--ink); font: inherit; font-size: 13.5px; resize: vertical; }
.focus-detail-error { margin: 0; color: var(--down, #e34d59); font-size: 12px; }
.focus-detail footer { display: flex; justify-content: space-between; gap: 10px; }
.focus-detail-save { min-width: 88px; min-height: 34px; border: 1px solid var(--accent); border-radius: 9px; background: var(--accent); color: var(--accent-contrast, #fff); font: inherit; font-size: 13px; cursor: pointer; }
.focus-detail-remove { min-height: 34px; padding: 0 12px; border: 0; border-radius: 9px; background: transparent; color: var(--down, #e34d59); font: inherit; font-size: 13px; cursor: pointer; }
</style>
