<template>
  <main class="cd-page">
    <header class="cd-top">
      <div class="cd-title-wrap">
        <button type="button" class="cd-title" :aria-expanded="titleMenu" @click="toggle('title')">
          倒数纪念日
          <ChevronDown :size="14" />
        </button>
        <div v-if="titleMenu" class="cd-pop is-title">
          <button type="button" :class="{ active: !archivedView }" @click="showActive">所有</button>
          <button type="button" :class="{ active: archivedView }" @click="showArchived">已归档</button>
        </div>
      </div>

      <div class="cd-actions">
        <button type="button" class="cd-icon-btn" :aria-expanded="plusMenu" aria-label="新建" title="新建" @click="toggle('plus')">
          <Plus :size="17" />
        </button>
        <div v-if="plusMenu" class="cd-pop is-plus">
          <button v-for="type in COUNTDOWN_TYPES" :key="type.key" type="button" @click="startCreate(type.key)">
            <span class="cd-pop-icon">{{ type.icon }}</span>{{ type.label }}
          </button>
        </div>

        <button type="button" class="cd-icon-btn" :aria-expanded="moreMenu" aria-label="更多" title="更多" @click="toggle('more')">
          <MoreHorizontal :size="17" />
        </button>
        <div v-if="moreMenu" class="cd-pop is-more">
          <button type="button" @click="toggleBatch">{{ batchMode ? '退出批量管理' : '批量管理' }}</button>
          <button type="button" @click="exportCsv">导出</button>
          <button type="button" @click="openCountdownSettings">纪念日设置</button>
          <button type="button" @click="showArchived">归档列表</button>
          <button type="button" class="is-danger" @click="clearAll">清空所有</button>
        </div>
      </div>
    </header>

    <nav class="cd-tabs" aria-label="倒数纪念日分类">
      <button v-for="tab in tabs" :key="tab.key" type="button" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">
        {{ tab.label }}<b v-if="countOf(tab.key)">{{ countOf(tab.key) }}</b>
      </button>
    </nav>

    <p v-if="error" class="cd-error" role="alert">{{ error }}</p>

    <section v-if="visible.length" class="cd-grid">
      <CountdownCard
        v-for="item in visible"
        :key="item.publicId"
        :item="item"
        :today="today"
        :archived="archivedView"
        :selectable="batchMode"
        :selected="picked.includes(item.publicId)"
        @open="startEdit(item)"
        @style="openStyle(item)"
        @note="openNote(item)"
        @archive="moveToArchive(item)"
        @restore="restore(item)"
        @remove="remove(item)"
        @pin="togglePin(item)"
        @cycle="cycleUnitOf(item)"
        @select="togglePick(item)"
      />
    </section>
    <p v-else class="cd-empty">
      {{ archivedView ? '还没有归档的纪念日' : '还没有纪念日，点右上角 + 新建一个吧' }}
    </p>

    <div v-if="batchMode" class="cd-batch">
      <span>已选 {{ picked.length }} 项</span>
      <button type="button" :disabled="!picked.length" @click="batchArchive">归档</button>
      <button type="button" class="is-danger" :disabled="!picked.length" @click="batchRemove">删除</button>
      <button type="button" @click="toggleBatch">取消</button>
    </div>

    <CountdownFormDialog
      :open="formOpen"
      :item="editing"
      :initial-type="createType"
      :saving="saving"
      :error="formError"
      @close="formOpen = false"
      @submit="submitForm"
    />
    <CountdownStyleDialog :open="styleOpen" :item="styling" @close="styleOpen = false" @submit="applyStyle" />
    <CountdownNoteDialog :open="noteOpen" :item="noting" @close="noteOpen = false" @submit="applyNote" />
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronDown, MoreHorizontal, Plus } from 'lucide-vue-next'
import { message } from '../services/message.js'
import { useTasksStore } from '../stores/tasks.js'
import CountdownCard from '../components/countdowns/CountdownCard.vue'
import CountdownFormDialog from '../components/countdowns/CountdownFormDialog.vue'
import CountdownNoteDialog from '../components/countdowns/CountdownNoteDialog.vue'
import CountdownStyleDialog from '../components/countdowns/CountdownStyleDialog.vue'
import { COUNTDOWN_TYPES, cycleUnit, matchesTab, sortItems, targetText, todayKey, typeOf } from '../components/countdowns/countdownUtils.js'
import { dropExtras, mergeExtras, readAll, saveExtras } from '../components/countdowns/countdownExtras.js'

const store = useTasksStore()
const router = useRouter()

const tabs = [{ key: 'ALL', label: '所有' }, ...COUNTDOWN_TYPES.map(type => ({ key: type.key, label: type.label }))]
const activeTab = ref('ALL')
const archivedView = ref(false)
const titleMenu = ref(false)
const plusMenu = ref(false)
const moreMenu = ref(false)
const batchMode = ref(false)
const picked = ref([])
const today = ref(todayKey())
const extras = ref(readAll())
const saving = ref(false)
const formError = ref('')
const formOpen = ref(false)
const editing = ref(null)
const createType = ref('COUNTDOWN')
const styleOpen = ref(false)
const styling = ref(null)
const noteOpen = ref(false)
const noting = ref(null)

const error = computed(() => store.error)
const merged = computed(() => mergeExtras(store.countdowns, extras.value))
const visible = computed(() => sortItems(
  merged.value
    .filter(item => Boolean(item.archived) === archivedView.value)
    .filter(item => matchesTab(item, activeTab.value)),
  today.value
))
function countOf(key) {
  return merged.value.filter(item => Boolean(item.archived) === archivedView.value && matchesTab(item, key)).length
}

function toggle(which) {
  titleMenu.value = which === 'title' ? !titleMenu.value : false
  plusMenu.value = which === 'plus' ? !plusMenu.value : false
  moreMenu.value = which === 'more' ? !moreMenu.value : false
}
function closeMenus() {
  titleMenu.value = false
  plusMenu.value = false
  moreMenu.value = false
}
function onGlobalPointerDown(event) {
  if (!titleMenu.value && !plusMenu.value && !moreMenu.value) return
  if (!event.target.closest('.cd-top')) closeMenus()
}

function refreshExtras() {
  extras.value = readAll()
}
function patchExtras(publicId, patch) {
  saveExtras(publicId, patch)
  refreshExtras()
}

function showActive() {
  archivedView.value = false
  closeMenus()
  picked.value = []
}
function showArchived() {
  archivedView.value = true
  activeTab.value = 'ALL'
  closeMenus()
  picked.value = []
}

function startCreate(type) {
  createType.value = type
  editing.value = null
  formError.value = ''
  formOpen.value = true
  closeMenus()
}
function startEdit(item) {
  editing.value = item
  formError.value = ''
  formOpen.value = true
}

async function submitForm(payload) {
  saving.value = true
  formError.value = ''
  const type = typeOf(payload).key
  const body = {
    title: payload.title,
    targetDate: payload.targetDate,
    kind: COUNTDOWN_TYPES.find(entry => entry.key === type)?.kind || 'COUNTDOWN',
    repeatYearly: payload.repeat === 'YEARLY' || type === 'BIRTHDAY',
    pinned: editing.value?.pinned || false,
    color: payload.color,
    note: editing.value?.note || ''
  }
  try {
    if (editing.value) {
      await store.updateCountdown(editing.value, body)
      patchExtras(editing.value.publicId, {
        type,
        icon: payload.icon,
        cover: payload.cover,
        color: payload.color,
        remind: payload.remind,
        repeat: payload.repeat,
        countingMode: payload.countingMode,
        display: payload.display
      })
    } else {
      const created = await store.createCountdown(body)
      // saveEfficiency 返回客户端 id，hydrate 后 publicId === id
      const publicId = typeof created === 'string' ? created : created?.publicId
      if (publicId) {
        patchExtras(publicId, {
          type,
          icon: payload.icon,
          cover: payload.cover,
          color: payload.color,
          remind: payload.remind,
          repeat: payload.repeat,
          countingMode: payload.countingMode,
          display: payload.display
        })
      }
    }
    formOpen.value = false
  } catch (failure) {
    formError.value = failure?.message || '保存失败'
  } finally {
    saving.value = false
  }
}

function openStyle(item) {
  styling.value = item
  styleOpen.value = true
}
function applyStyle(patch) {
  if (styling.value) patchExtras(styling.value.publicId, patch)
  styleOpen.value = false
}
function openNote(item) {
  noting.value = item
  noteOpen.value = true
}
async function applyNote(text) {
  if (!noting.value) return
  try {
    await store.updateCountdown(noting.value, { note: text })
    message.success('备注已保存')
  } catch {
    message.error('备注保存失败')
  }
  noteOpen.value = false
}

async function togglePin(item) {
  try {
    await store.updateCountdown(item, { pinned: !item.pinned })
  } catch {
    message.error('置顶失败')
  }
}
function cycleUnitOf(item) {
  patchExtras(item.publicId, { unit: cycleUnit(item.unit || 'DAY') })
}
function moveToArchive(item) {
  patchExtras(item.publicId, { archived: true })
  message.success(`「${item.title}」已归档`)
}
function restore(item) {
  patchExtras(item.publicId, { archived: false })
  message.success(`「${item.title}」已恢复`)
}
async function remove(item) {
  if (!window.confirm(`删除纪念日「${item.title}」？`)) return
  try {
    await store.deleteCountdown(item)
    dropExtras(item.publicId)
    refreshExtras()
  } catch {
    message.error('删除失败')
  }
}

function toggleBatch() {
  batchMode.value = !batchMode.value
  picked.value = []
  closeMenus()
}
function togglePick(item) {
  const index = picked.value.indexOf(item.publicId)
  if (index >= 0) picked.value.splice(index, 1)
  else picked.value.push(item.publicId)
}
async function batchArchive() {
  picked.value.forEach(publicId => saveExtras(publicId, { archived: true }))
  refreshExtras()
  message.success(`已归档 ${picked.value.length} 项`)
  picked.value = []
  batchMode.value = false
}
async function batchRemove() {
  if (!window.confirm(`删除选中的 ${picked.value.length} 个纪念日？`)) return
  const targets = merged.value.filter(item => picked.value.includes(item.publicId))
  for (const item of targets) {
    try {
      await store.deleteCountdown(item)
      dropExtras(item.publicId)
    } catch {
      // 单条失败不阻断其余删除
    }
  }
  refreshExtras()
  picked.value = []
  batchMode.value = false
}

function exportCsv() {
  const rows = [['标题', '类型', '目标日期', '备注']]
  merged.value.forEach(item => rows.push([item.title, typeOf(item).label, targetText(item, today.value), item.note || '']))
  const text = rows.map(row => row.map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob([`\uFEFF${text}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `倒数纪念日-${today.value}.csv`
  link.click()
  URL.revokeObjectURL(url)
  closeMenus()
  message.success('已导出 CSV')
}
function openCountdownSettings() {
  closeMenus()
  router.push({ path: '/tasks/app-settings', query: { pane: 'modules' } })
}
async function clearAll() {
  closeMenus()
  if (!merged.value.length) {
    message.info('还没有任何纪念日')
    return
  }
  if (!window.confirm('清空所有纪念日？删除后无法恢复。')) return
  for (const item of [...merged.value]) {
    try {
      await store.deleteCountdown(item)
      dropExtras(item.publicId)
    } catch {
      // 忽略单条失败
    }
  }
  refreshExtras()
}

let timer = null
onMounted(async () => {
  document.addEventListener('pointerdown', onGlobalPointerDown, true)
  timer = window.setInterval(() => { today.value = todayKey() }, 60000)
  await store.init({ waitForRemote: false })
  refreshExtras()
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onGlobalPointerDown, true)
  if (timer) window.clearInterval(timer)
})
</script>

<style scoped>
.cd-page{max-width:1080px;margin:0 auto;padding:26px 26px 96px}
.cd-top{position:relative;display:flex;align-items:center;gap:8px}
.cd-title-wrap{position:relative}
.cd-actions{position:relative;display:flex;align-items:center;gap:2px;margin-left:4px}
.cd-title{display:inline-flex;align-items:center;gap:5px;padding:2px 0;border:0;background:transparent;color:var(--ink);font-size:1.22rem;font-weight:700;cursor:pointer}
.cd-title:hover{color:var(--accent)}
.cd-actions{position:relative;display:flex;align-items:center;gap:2px}
.cd-icon-btn{display:grid;width:30px;height:30px;place-items:center;border:0;border-radius:7px;background:transparent;color:var(--muted);cursor:pointer}
.cd-icon-btn:hover{background:var(--panel2);color:var(--ink)}
.cd-pop{position:absolute;top:calc(100% + 6px);z-index:20;display:grid;min-width:132px;padding:4px;border:1px solid var(--line);border-radius:9px;background:var(--card);box-shadow:0 14px 32px rgba(15,23,42,.16)}
.cd-pop.is-title{left:0}
.cd-pop.is-plus,.cd-pop.is-more{right:0}
.cd-pop button{display:flex;align-items:center;gap:8px;padding:7px 10px;border:0;border-radius:6px;background:transparent;color:var(--ink);font-size:.78rem;text-align:left;cursor:pointer}
.cd-pop button:hover{background:var(--panel2)}
.cd-pop button.active{color:var(--accent);font-weight:600}
.cd-pop button.is-danger{color:var(--down)}
.cd-pop-icon{font-size:14px}
.cd-tabs{display:flex;align-items:center;gap:6px;margin:14px 0 16px;overflow-x:auto}
.cd-tabs button{display:inline-flex;align-items:center;gap:4px;padding:4px 11px;border:1px solid transparent;border-radius:14px;background:transparent;color:var(--muted);font-size:.78rem;white-space:nowrap;cursor:pointer}
.cd-tabs button:hover{background:var(--panel2);color:var(--ink)}
.cd-tabs button.active{background:var(--accent);color:var(--paper);font-weight:600}
.cd-tabs b{font-size:.66rem;font-weight:600;opacity:.72}
.cd-error{margin:0 0 12px;padding:9px 11px;border-left:3px solid var(--down);background:color-mix(in srgb,var(--down) 8%,transparent);font-size:.78rem}
.cd-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(204px,1fr));gap:12px}
.cd-empty{margin:60px 0;color:var(--muted);font-size:.82rem;text-align:center}
.cd-batch{position:fixed;right:0;bottom:0;left:50px;z-index:40;display:flex;align-items:center;justify-content:center;gap:10px;padding:9px 16px;border-top:1px solid var(--line);background:var(--card);font-size:.78rem}
.cd-batch button{height:28px;padding:0 12px;border:1px solid var(--line2);border-radius:6px;background:var(--card);color:var(--ink);font-size:.76rem;cursor:pointer}
.cd-batch button.is-danger{border-color:transparent;background:var(--down);color:#fff}
.cd-batch button:disabled{opacity:.5;cursor:default}
@media(max-width:760px){.cd-page{padding:18px 14px 96px}.cd-grid{grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:10px}.cd-batch{left:0}}
</style>
