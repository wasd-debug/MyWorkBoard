<template>
  <article
    class="cd-card"
    :class="{ 'is-archived': archived, 'is-selectable': selectable, 'is-picked': selected, 'is-cover': !isPlain }"
    :style="cardStyle"
    role="button"
    tabindex="0"
    :aria-label="ariaLabel"
    @click="handleMainClick"
    @keydown.enter.prevent="handleMainClick"
  >
    <input
      v-if="selectable"
      class="cd-pick"
      type="checkbox"
      :checked="selected"
      :aria-label="`选择 ${item.title}`"
      @click.stop="emit('select')"
    />

    <header class="cd-card-head">
      <span class="cd-card-icon" aria-hidden="true">{{ item.icon }}</span>
      <h3 class="cd-card-title">{{ item.title }}</h3>
      <span v-if="archived" class="cd-card-flag">已归档</span>
      <span v-else-if="badge" class="cd-card-flag is-badge">{{ badge }}</span>
    </header>

    <button type="button" class="cd-card-count" :title="`点击切换为${nextUnitLabel}`" @click.stop="emit('cycle')">
      <span v-for="part in parts" :key="`${part.value}-${part.unit}`" class="cd-count-part">
        <b>{{ part.value }}</b><i v-if="part.unit">{{ part.unit }}</i>
      </span>
    </button>

    <p class="cd-card-meta">
      <span class="cd-card-distance">{{ directionText }}</span>
      <span class="cd-card-date">{{ typeLabel }}</span>
    </p>

    <div v-if="!selectable" class="cd-card-tools">
      <button type="button" :title="item.pinned ? '取消置顶' : '置顶'" :aria-label="item.pinned ? '取消置顶' : '置顶'" :class="{ 'is-on': item.pinned }" @click.stop="emit('pin')">
        <Pin :size="14" />
      </button>
      <button ref="moreButton" type="button" title="更多" aria-label="更多" :aria-expanded="menuOpen" @click.stop="toggleMenu">
        <MoreHorizontal :size="14" />
      </button>
      <div v-if="menuOpen" class="cd-menu" role="menu">
        <button type="button" role="menuitem" @click.stop="pick('edit')"><Pencil :size="14" />编辑</button>
        <button type="button" role="menuitem" @click.stop="pick('style')"><Palette :size="14" />样式</button>
        <button type="button" role="menuitem" @click.stop="pick('note')"><NotebookPen :size="14" />备注</button>
        <button v-if="!archived" type="button" role="menuitem" @click.stop="pick('archive')"><Archive :size="14" />归档</button>
        <button v-else type="button" role="menuitem" @click.stop="pick('restore')"><ArchiveRestore :size="14" />恢复</button>
        <button type="button" role="menuitem" class="is-danger" @click.stop="pick('remove')"><Trash2 :size="14" />删除</button>
      </div>
    </div>
  </article>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Archive, ArchiveRestore, MoreHorizontal, NotebookPen, Palette, Pencil, Pin, Trash2 } from 'lucide-vue-next'
import { coverBackground, dayBadge, daysUntil, DISPLAY_UNITS, formatCount, relativeText, targetText, typeOf, cycleUnit } from './countdownUtils.js'

const props = defineProps({
  item: { type: Object, required: true },
  today: { type: String, required: true },
  archived: Boolean,
  selectable: Boolean,
  selected: Boolean
})
const emit = defineEmits(['open', 'style', 'note', 'archive', 'restore', 'remove', 'pin', 'cycle', 'select'])

const menuOpen = ref(false)
const moreButton = ref(null)

const isPlain = computed(() => false)
const parts = computed(() => formatCount(props.item, props.item.unit || 'DAY', props.today))
const days = computed(() => daysUntil(props.item, props.today))
const badge = computed(() => dayBadge(days.value))
const directionText = computed(() => {
  const text = relativeText(props.item, props.today)
  return text === '就是今天' ? '就是今天' : `距离 ${targetText(props.item, props.today)} ${text}`
})
const typeLabel = computed(() => typeOf(props.item).label)
const nextUnitLabel = computed(() => {
  const next = cycleUnit(props.item.unit || 'DAY')
  return DISPLAY_UNITS.find(unit => unit.key === next)?.label || '天'
})
const cardStyle = computed(() => ({
  background: coverBackground(props.item.cover, props.item.color),
  borderColor: 'transparent'
}))
const ariaLabel = computed(() => `${props.item.title}，${directionText.value}`)

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}
function closeMenu() {
  menuOpen.value = false
}
function pick(action) {
  menuOpen.value = false
  emit(action)
}
function handleMainClick() {
  if (props.selectable) emit('select')
  else emit('open')
}
function onGlobalPointerDown(event) {
  if (!menuOpen.value) return
  const host = moreButton.value?.parentElement
  if (host && !host.contains(event.target)) closeMenu()
}
watch(menuOpen, open => {
  if (typeof document === 'undefined') return
  if (open) document.addEventListener('pointerdown', onGlobalPointerDown, true)
  else document.removeEventListener('pointerdown', onGlobalPointerDown, true)
})
onBeforeUnmount(() => {
  if (typeof document !== 'undefined') document.removeEventListener('pointerdown', onGlobalPointerDown, true)
})
</script>

<style scoped>
.cd-card{position:relative;display:flex;flex-direction:column;min-height:192px;padding:14px 15px 12px;border:1px solid var(--line);border-radius:12px;overflow:hidden;cursor:pointer;transition:transform .15s ease,box-shadow .15s ease}
.cd-card:hover{transform:translateY(-1px);box-shadow:0 10px 24px rgba(15,23,42,.12)}
.cd-card:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
.cd-card.is-cover{color:#fff}
.cd-card.is-archived{opacity:.86}
.cd-card.is-picked{outline:2px solid var(--accent)}
.cd-pick{position:absolute;top:10px;left:10px;width:15px;height:15px;z-index:3}
.cd-card-head{display:flex;align-items:center;gap:6px;min-height:22px}
.cd-card-icon{display:grid;width:20px;height:20px;place-items:center;font-size:14px;border-radius:50%;background:rgba(255,255,255,.24)}
.cd-card-title{margin:0;font-size:.82rem;font-weight:600;line-height:1.3;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.cd-card-flag{margin-left:auto;padding:1px 6px;border-radius:8px;background:rgba(0,0,0,.28);font-size:.62rem;font-weight:600;letter-spacing:.04em}
.cd-card-flag.is-badge{background:rgba(255,255,255,.26)}
.cd-card-count{display:flex;align-items:baseline;gap:1px;margin:auto 0 2px;padding:0;border:0;background:transparent;color:inherit;font:inherit;font-variant-numeric:tabular-nums;cursor:pointer;text-align:left}
.cd-card-count:focus-visible{outline:2px solid currentColor;outline-offset:3px;border-radius:4px}
.cd-count-part{display:inline-flex;align-items:baseline}
.cd-count-part b{font-size:2.35rem;font-weight:600;line-height:1}
.cd-count-part i{margin:0 2px 0 1px;font-size:.78rem;font-style:normal;font-weight:500;opacity:.82}
.cd-card:not(.is-cover) .cd-count-part b{font-size:2.1rem}
.cd-card-meta{display:flex;align-items:center;justify-content:space-between;gap:6px;margin:0;font-size:.66rem;opacity:.86}
.cd-card-distance{min-width:0;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.cd-card-date{flex:none;padding:0 5px;border-radius:6px;background:rgba(0,0,0,.16);font-size:.6rem;line-height:1.5}
.cd-card-tools{position:absolute;top:8px;right:8px;display:flex;gap:2px;opacity:0;transition:opacity .14s ease}
.cd-card:hover .cd-card-tools,.cd-card:focus-within .cd-card-tools{opacity:1}
.cd-card-tools>button{display:grid;width:24px;height:24px;place-items:center;border:0;border-radius:6px;background:rgba(0,0,0,.22);color:inherit;cursor:pointer}
.cd-card-tools>button:hover{background:rgba(0,0,0,.34)}
.cd-card-tools>button.is-on{color:#ffd166}
.cd-card:not(.is-cover) .cd-card-tools>button{background:var(--panel2);color:var(--muted)}
.cd-menu{position:absolute;top:28px;right:0;z-index:6;display:grid;min-width:118px;padding:4px;border:1px solid var(--line);border-radius:8px;background:var(--card);box-shadow:0 12px 28px rgba(15,23,42,.18)}
.cd-menu button{display:flex;align-items:center;gap:8px;padding:7px 10px;border:0;border-radius:6px;background:transparent;color:var(--ink);font-size:.76rem;text-align:left;cursor:pointer}
.cd-menu button:hover{background:var(--panel2)}
.cd-menu button.is-danger{color:var(--down)}
@media(max-width:640px){.cd-card{min-height:148px}.cd-count-part b{font-size:1.9rem}}
</style>
