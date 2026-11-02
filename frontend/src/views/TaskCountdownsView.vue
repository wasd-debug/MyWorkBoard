<template>
  <main class="cd">
    <header class="cd-head">
      <div class="cd-title-wrap">
        <button type="button" class="cd-title" :aria-expanded="panels.title" @click="toggle('title')">
          {{ archivedMode ? '已归档倒数纪念日' : '倒数纪念日' }}<ChevronDown :size="15" />
        </button>
        <div v-if="panels.title" class="cd-menu is-title" @click.stop>
          <button type="button" :class="{ on: !archivedMode }" @click="setArchived(false)">所有</button>
          <button type="button" :class="{ on: archivedMode }" @click="setArchived(true)">已归档</button>
        </div>
      </div>

      <div class="cd-actions">
        <button ref="plusButton" type="button" class="cd-icon is-add" aria-label="新建" title="新建" :aria-expanded="panels.plus" @click="toggle('plus')">
          <Plus :size="19" />
        </button>
        <div v-if="panels.plus" class="cd-menu is-plus" role="menu" @click.stop>
          <button v-for="group in groups" :key="group.publicId" type="button" role="menuitem" @click="startCreate(group)">
            <span class="cd-menu-icon">{{ group.icon || '📁' }}</span>{{ group.name }}
          </button>
          <p v-if="!groups.length" class="cd-menu-empty">分组加载中…</p>
        </div>

        <button type="button" class="cd-icon" aria-label="更多" title="更多" :aria-expanded="panels.more" @click="toggle('more')">
          <MoreHorizontal :size="18" />
        </button>
        <div v-if="panels.more" class="cd-menu is-more" role="menu" @click.stop>
          <button type="button" role="menuitem" @click="setArchived(true)"><Archive :size="15" />已归档</button>
          <button type="button" role="menuitem" @click="setArchived(false)"><LayoutGrid :size="15" />所有倒数日</button>
          <button type="button" role="menuitem" class="is-danger" @click="clearAll"><Trash2 :size="15" />清空所有</button>
        </div>
      </div>
    </header>

    <nav class="cd-tabs" aria-label="倒数纪念日分组">
      <button type="button" :class="{ on: activeTab === 'ALL' }" @click="activeTab = 'ALL'">所有</button>
      <button
        v-for="group in groups"
        :key="group.publicId"
        type="button"
        :class="{ on: activeTab === group.publicId }"
        @click="activeTab = group.publicId"
        @contextmenu.prevent="openTabMenu($event, group)"
      >
        <span v-if="group.icon" class="cd-tab-icon">{{ group.icon }}</span>{{ group.name }}
      </button>
      <button type="button" class="cd-tab-add" aria-label="新建分组" title="新建分组" :aria-expanded="panels.group" @click="openGroupEditor(null)">
        <Plus :size="13" />
      </button>

      <div v-if="panels.tab" class="cd-menu is-tab" role="menu" :style="tabMenuStyle" @click.stop>
        <button type="button" role="menuitem" @click="openGroupEditor(tabTarget)"><Pencil :size="14" />重命名</button>
        <button type="button" role="menuitem" class="is-danger" :disabled="groups.length <= 1" @click="removeGroup(tabTarget)">
          <Trash2 :size="14" />删除分组
        </button>
      </div>

      <div v-if="panels.group" class="cd-menu is-group" role="dialog" :aria-label="groupEditor.publicId ? '重命名分组' : '新建分组'" @click.stop>
        <p class="cd-group-title">{{ groupEditor.publicId ? '重命名分组' : '新建分组' }}</p>
        <input v-model.trim="groupEditor.name" class="cd-group-input" maxlength="12" placeholder="分组名称" aria-label="分组名称" @keydown.enter.prevent="saveGroup" />
        <div class="cd-group-row">
          <button
            v-for="icon in GROUP_ICONS"
            :key="icon"
            type="button"
            class="cd-group-emoji"
            :class="{ on: groupEditor.icon === icon }"
            @click="groupEditor.icon = icon"
          >{{ icon }}</button>
        </div>
        <div class="cd-group-row">
          <button
            v-for="color in GROUP_COLORS"
            :key="color"
            type="button"
            class="cd-group-color"
            :class="{ on: groupEditor.color === color }"
            :style="{ background: color }"
            :aria-label="`颜色 ${color}`"
            @click="groupEditor.color = color"
          ></button>
        </div>
        <div class="cd-group-foot">
          <button type="button" class="cd-btn" @click="closePanels">取消</button>
          <button type="button" class="cd-btn is-primary" :disabled="!groupEditor.name || groupSaving" @click="saveGroup">
            {{ groupSaving ? '保存中…' : '确定' }}
          </button>
        </div>
      </div>
    </nav>

    <p v-if="error" class="cd-error" role="alert">{{ error }}</p>

    <div ref="gridEl" class="cd-grid" :style="{ '--cols': columns, '--cw': `${cardWidth}px` }">
      <CountdownCard
        v-for="item in visible"
        :key="item.publicId"
        :item="item"
        :today="today"
        :now="now"
        @cycle="cycleUnitOf(item)"
        @pin="togglePin(item)"
        @edit="startEdit(item)"
        @style="openStyle(item)"
        @note="openNote(item)"
        @archive="setArchivedState(item, true)"
        @restore="setArchivedState(item, false)"
        @remove="remove(item)"
      />
    </div>

    <p v-if="!visible.length && store.ready" class="cd-empty">
      {{ archivedMode ? '还没有归档的倒数纪念日' : '还没有倒数纪念日，点右上角「+」新建一个吧' }}
    </p>

    <CountdownFormDialog
      :open="formOpen"
      :item="editing"
      :groups="groups"
      :initial-group="createGroup"
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
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { Archive, ChevronDown, LayoutGrid, MoreHorizontal, Pencil, Plus, Trash2 } from 'lucide-vue-next'
import { useExclusivePanels } from '../utils/exclusivePanels.js'
import { message } from '../services/message.js'
import { useTasksStore } from '../stores/tasks.js'
import CountdownCard from '../components/countdowns/CountdownCard.vue'
import CountdownFormDialog from '../components/countdowns/CountdownFormDialog.vue'
import CountdownNoteDialog from '../components/countdowns/CountdownNoteDialog.vue'
import CountdownStyleDialog from '../components/countdowns/CountdownStyleDialog.vue'
import { cycleUnit, matchesTab, needsTick, sortItems, todayKey } from '../components/countdowns/countdownUtils.js'

const store = useTasksStore()

const CARD_MIN = 240
const GAP = 20
const GROUP_ICONS = ['📁', '🎉', '⏳', '🎂', '🎊', '💼', '🏠', '✈️', '💡', '❤️']
const GROUP_COLORS = ['#4d78db', '#e2685f', '#2fb39a', '#f2b13c', '#a568d4', '#ef7f96']
const activeTab = ref('ALL')
const archivedMode = ref(false)
const panels = reactive({ title: false, plus: false, more: false, tab: false, group: false })
const tabTarget = ref(null)
const tabMenuStyle = ref({})
const groupEditor = reactive({ publicId: '', name: '', icon: '📁', color: '#4d78db' })
const groupSaving = ref(false)
const today = ref(todayKey())
const now = ref(Date.now())
const columns = ref(2)
const cardWidth = ref(CARD_MIN)
const gridEl = ref(null)
const plusButton = ref(null)

const formOpen = ref(false)
const editing = ref(null)
const createGroup = ref(null)
const formError = ref('')
const saving = ref(false)
const styleOpen = ref(false)
const styling = ref(null)
const noteOpen = ref(false)
const noting = ref(null)

const error = computed(() => store.error)
const groups = computed(() => store.countdownGroups || [])
const visible = computed(() => sortItems(
  (store.countdowns || [])
    .filter(item => Boolean(item.archived) === archivedMode.value)
    .filter(item => matchesTab(item, activeTab.value, groups.value)),
  today.value
))
const hasTimeUnit = computed(() => visible.value.some(item => needsTick(item)))

useExclusivePanels('countdowns-page', [
  ['title', () => panels.title, value => { panels.title = value }],
  ['plus', () => panels.plus, value => { panels.plus = value }],
  ['more', () => panels.more, value => { panels.more = value }],
  ['tab', () => panels.tab, value => { panels.tab = value }],
  ['group', () => panels.group, value => { panels.group = value }]
])

// 当前分组被删掉后回落到「所有」，避免停在空白页签
watch(groups, list => {
  if (activeTab.value === 'ALL') return
  if (!list.some(group => group.publicId === activeTab.value)) activeTab.value = 'ALL'
})

function toggle(name) {
  const next = !panels[name]
  panels.title = false
  panels.plus = false
  panels.more = false
  panels[name] = next
}

function closePanels() {
  panels.title = false
  panels.plus = false
  panels.more = false
  panels.tab = false
  panels.group = false
}

/** 页签右键：重命名 / 删除 */
function openTabMenu(event, group) {
  if (!event || !group) return
  tabTarget.value = group
  const width = 150
  const height = 96
  tabMenuStyle.value = {
    position: 'fixed',
    top: `${Math.round(Math.max(8, Math.min(event.clientY + 4, window.innerHeight - height)))}px`,
    left: `${Math.round(Math.max(8, Math.min(event.clientX, window.innerWidth - width - 8)))}px`,
    width: `${width}px`
  }
  panels.group = false
  panels.tab = true
}

/** 新建（target 为 null）与重命名共用同一个面板 */
function openGroupEditor(target) {
  // 点「+」再点一次要能收起，所以这里自己 toggle（document 的 pointerdown 豁免了这个按钮）
  if (!target && panels.group) {
    closePanels()
    return
  }
  groupEditor.publicId = target?.publicId || ''
  groupEditor.name = target?.name || ''
  groupEditor.icon = target?.icon || '📁'
  groupEditor.color = target?.color || '#4d78db'
  panels.tab = false
  panels.plus = false
  panels.more = false
  panels.group = true
}

async function saveGroup() {
  const name = groupEditor.name.trim()
  if (!name || groupSaving.value) return
  groupSaving.value = true
  try {
    if (groupEditor.publicId) {
      const target = groups.value.find(group => group.publicId === groupEditor.publicId)
      if (target) {
        await store.updateCountdownGroup(target, { name, icon: groupEditor.icon, color: groupEditor.color })
      }
    } else {
      const created = await store.createCountdownGroup({ name, icon: groupEditor.icon, color: groupEditor.color })
      activeTab.value = created.publicId
    }
    closePanels()
  } catch (failure) {
    message.error(failure?.message || '分组保存失败')
  } finally {
    groupSaving.value = false
  }
}

async function removeGroup(group) {
  if (!group) return
  closePanels()
  const count = (store.countdowns || []).filter(item => item.groupId === group.publicId).length
  const hint = count ? `「${group.name}」下还有 ${count} 个倒数日，删除后会移到第一个分组。` : `确定删除分组「${group.name}」？`
  if (!window.confirm(hint)) return
  try {
    await store.deleteCountdownGroup(group)
    await store.syncNow()
    message.success(`已删除分组「${group.name}」`)
  } catch (failure) {
    message.error(failure?.message || '删除分组失败')
  }
}

function setArchived(value) {
  archivedMode.value = value
  closePanels()
}

function startCreate(group) {
  createGroup.value = group || groups.value[0] || null
  editing.value = null
  formError.value = ''
  formOpen.value = true
  closePanels()
}
function startEdit(item) {
  editing.value = item
  formError.value = ''
  formOpen.value = true
}

/** 连续写同一实体必须串行，否则旧 revision 会触发冲突 */
let writeChain = Promise.resolve()
function enqueue(task) {
  writeChain = writeChain.then(task).catch(failure => {
    message.error(failure?.message || '保存失败')
  })
  return writeChain
}

function fresh(publicId) {
  return (store.countdowns || []).find(item => item.publicId === publicId) || null
}

async function patch(publicId, changes) {
  const target = fresh(publicId)
  if (!target) return
  await store.updateCountdown(target, changes)
  await store.syncNow()
}

async function submitForm(payload) {
  saving.value = true
  formError.value = ''
  try {
    if (editing.value) {
      const target = editing.value
      await store.updateCountdown(target, payload)
      await store.syncNow()
    } else {
      await store.createCountdown(payload)
      await store.syncNow()
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
function applyStyle(patchData) {
  const target = styling.value
  styleOpen.value = false
  if (!target) return
  enqueue(() => patch(target.publicId, patchData))
}
function openNote(item) {
  noting.value = item
  noteOpen.value = true
}
function applyNote(text) {
  const target = noting.value
  noteOpen.value = false
  if (!target) return
  enqueue(() => patch(target.publicId, { note: text }))
}

function cycleUnitOf(item) {
  const next = cycleUnit(item.unit || 'DAY')
  enqueue(() => patch(item.publicId, { unit: next }))
}
function togglePin(item) {
  enqueue(() => patch(item.publicId, { pinned: !item.pinned }))
}
function setArchivedState(item, value) {
  enqueue(() => patch(item.publicId, { archived: value }))
}
async function remove(item) {
  if (!window.confirm(`删除「${item.title}」？`)) return
  try {
    await store.deleteCountdown(item)
    await store.syncNow()
    message.success(`已删除「${item.title}」`)
  } catch (failure) {
    message.error(failure?.message || '删除失败')
  }
}
async function clearAll() {
  closePanels()
  const list = [...(store.countdowns || [])]
  if (!list.length) {
    message.info('还没有任何倒数日')
    return
  }
  if (!window.confirm(`清空全部 ${list.length} 个倒数日？删除后无法恢复。`)) return
  for (const item of list) {
    try {
      await store.deleteCountdown(item)
      await store.syncNow()
    } catch {
      // 单条失败不阻断其余删除
    }
  }
}

/** 列数由容器宽度决定，最小 2 列；宽度增长时先放大卡片再增列 */
let observer = null
function measure() {
  const element = gridEl.value
  if (!element) return
  const width = element.clientWidth
  if (!width) return
  let cols = Math.max(2, Math.floor((width + GAP) / (CARD_MIN + GAP)))
  cols = Math.min(cols, 7)
  const widthPerCard = Math.floor((width - GAP * (cols - 1)) / cols)
  if (cols !== columns.value) columns.value = cols
  if (widthPerCard !== cardWidth.value) cardWidth.value = widthPerCard
}

let timer = null
onMounted(async () => {
  document.addEventListener('pointerdown', onDocumentPointerDown, true)
  observer = new ResizeObserver(measure)
  measure()
  if (gridEl.value) observer.observe(gridEl.value)
  timer = window.setInterval(() => {
    const key = todayKey()
    if (key !== today.value) today.value = key
    if (hasTimeUnit.value) now.value = Date.now()
  }, 1000)
  await store.init({ waitForRemote: false })
  try {
    await store.loadCountdownGroups()
  } catch {
    // 离线时沿用本地缓存的分组
  }
  measure()
})

function onDocumentPointerDown(event) {
  const target = event.target
  if (!target?.closest) return
  // 每个浮层只认自己的宿主容器：点宿主之外一律收起，点另一个按钮时先关再开
  if (panels.title && !target.closest('.cd-title-wrap')) panels.title = false
  if ((panels.plus || panels.more) && !target.closest('.cd-actions')) {
    panels.plus = false
    panels.more = false
  }
  // 页签上的两个浮层各自认自己的容器：点别的页签要收起右键菜单，点「+」由它自己 toggle
  if (panels.tab && !target.closest('.cd-menu.is-tab')) panels.tab = false
  if (panels.group && !target.closest('.cd-menu.is-group') && !target.closest('.cd-tab-add')) panels.group = false
}

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown, true)
  if (timer) window.clearInterval(timer)
  if (observer) observer.disconnect()
})
</script>

<style scoped>
.cd {
  display: flex;
  height: 100vh;
  min-height: 0;
  flex-direction: column;
  padding: 20px 30px 24px;
  overflow-y: auto;
  --cd-accent: #3d8fce;
}
.cd-head {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 2px;
}
.cd-title-wrap { position: relative; display: flex; align-items: center; }
.cd-title {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 6px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ink);
  font: inherit;
  font-size: 20px;
  font-weight: 600;
  cursor: pointer;
}
.cd-title:hover { background: color-mix(in srgb, var(--ink) 6%, transparent); }
.cd-actions { position: relative; display: flex; align-items: center; gap: 4px; }
.cd-icon {
  display: grid;
  width: 34px;
  height: 34px;
  min-height: 34px;
  place-items: center;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--ink2);
  cursor: pointer;
}
.cd-icon:hover { background: color-mix(in srgb, var(--ink) 7%, transparent); }
.cd-icon.is-add { background: color-mix(in srgb, var(--ink) 6%, transparent); color: var(--ink); }
.cd-icon.is-add:hover { background: color-mix(in srgb, var(--ink) 11%, transparent); }
.cd-menu {
  position: absolute;
  z-index: 30;
  top: calc(100% + 8px);
  display: grid;
  min-width: 168px;
  padding: 6px;
  border-radius: 12px;
  background: var(--card);
  box-shadow: 0 20px 48px rgba(15, 23, 42, .2);
}
.cd-menu.is-title { left: 0; }
.cd-menu.is-plus { right: 38px; }
.cd-menu.is-more { right: 0; }
.cd-menu button {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ink);
  font: inherit;
  font-size: 14.5px;
  text-align: left;
  cursor: pointer;
}
.cd-menu button:hover { background: color-mix(in srgb, var(--ink) 6%, transparent); }
.cd-menu button.on { color: var(--cd-accent); font-weight: 600; }
.cd-menu button.is-danger { color: #e05a4f; }
.cd-menu-icon { display: grid; width: 22px; place-items: center; font-size: 16px; }
.cd-tabs { position: relative; display: flex; flex: none; align-items: center; gap: 6px; margin: 12px 0 16px; flex-wrap: wrap; }
.cd-tabs > button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  min-height: 30px;
  padding: 0 14px;
  border: 0;
  border-radius: 15px;
  background: transparent;
  color: var(--ink2);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}
.cd-tabs > button:hover { background: color-mix(in srgb, var(--ink) 6%, transparent); }
.cd-tabs > button.on { background: color-mix(in srgb, var(--cd-accent) 16%, transparent); color: var(--cd-accent); font-weight: 600; }
.cd-tab-icon { font-size: 13px; line-height: 1; }
.cd-tabs > .cd-tab-add {
  width: 26px;
  min-width: 26px;
  padding: 0;
  justify-content: center;
  color: var(--muted);
}
.cd-tabs > .cd-tab-add:hover { color: var(--ink); }
.cd-menu-empty { margin: 0; padding: 10px 12px; color: var(--muted); font-size: 13px; }
.cd-menu.is-tab { left: 0; min-width: 150px; }
.cd-menu.is-group { right: 0; left: auto; width: 236px; gap: 8px; padding: 12px; }
.cd-group-title { margin: 0; color: var(--ink); font-size: 13.5px; font-weight: 600; }
.cd-group-input {
  height: 34px;
  padding: 0 10px;
  border: 1px solid color-mix(in srgb, var(--cd-accent) 55%, transparent);
  border-radius: 9px;
  background: var(--card);
  color: var(--ink);
  font: inherit;
  font-size: 13.5px;
  outline: none;
}
.cd-group-input:focus { border-color: var(--cd-accent); }
.cd-group-row { display: flex; flex-wrap: wrap; gap: 4px; }
.cd-group-emoji {
  width: 26px;
  height: 26px;
  min-height: 26px;
  padding: 0;
  border: 0;
  border-radius: 7px;
  background: transparent;
  font-size: 15px;
  line-height: 1;
  cursor: pointer;
}
.cd-group-emoji.on { background: color-mix(in srgb, var(--cd-accent) 18%, transparent); }
.cd-group-color {
  width: 20px;
  height: 20px;
  min-height: 20px;
  padding: 0;
  border: 2px solid transparent;
  border-radius: 50%;
  cursor: pointer;
}
.cd-group-color.on { border-color: var(--ink); }
.cd-group-foot { display: flex; justify-content: flex-end; gap: 8px; margin-top: 2px; }
.cd-btn {
  height: 30px;
  min-height: 30px;
  padding: 0 12px;
  border: 0;
  border-radius: 8px;
  background: color-mix(in srgb, var(--ink) 7%, transparent);
  color: var(--ink);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.cd-btn.is-primary { background: var(--cd-accent); color: #fff; }
.cd-btn.is-primary:disabled { opacity: .55; cursor: not-allowed; }
.cd-menu button:disabled { opacity: .5; cursor: not-allowed; }
.cd-error { margin: 0 0 10px; padding: 9px 11px; border-left: 3px solid #e05a4f; background: color-mix(in srgb, #e05a4f 8%, transparent); font-size: 13px; }
.cd-grid {
  display: grid;
  flex: none;
  grid-template-columns: repeat(var(--cols, 2), minmax(0, 1fr));
  gap: 20px;
  align-content: start;
}
.cd-empty { margin: 64px 0; color: var(--muted); font-size: 14px; text-align: center; }
@media (max-width: 760px) {
  .cd { padding: 16px 14px 20px; }
  .cd-title { font-size: 18px; }
  .cd-grid { gap: 12px; }
}
</style>
