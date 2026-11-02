<template>
  <article
    class="cdc"
    :class="[`is-${tone}`, { 'is-pinned': item.pinned, 'is-archived': item.archived }]"
    :style="theme.style"
    role="button"
    tabindex="0"
    :aria-label="`${item.title}，${footText}。回车切换显示单位`"
    @click="emit('cycle')"
    @keydown.enter.prevent="emit('cycle')"
    @keydown.space.prevent="emit('cycle')"
    @contextmenu.prevent="openMenuAt($event)"
  >
    <div class="cdc-tools" @click.stop>
      <button
        type="button"
        class="cdc-tool"
        :class="{ 'is-on': item.pinned }"
        :aria-label="item.pinned ? '取消置顶' : '置顶'"
        :title="item.pinned ? '取消置顶' : '置顶'"
        @click="emit('pin')"
      >
        <Star :size="15" :fill="item.pinned ? 'currentColor' : 'none'" />
      </button>
      <button
        ref="moreButton"
        type="button"
        class="cdc-tool is-more"
        aria-label="更多"
        title="更多"
        :aria-expanded="menuOpen"
        @click="toggleMenu"
      >
        <MoreHorizontal :size="16" />
      </button>
    </div>

    <Teleport to="body">
      <div v-if="menuOpen" class="cdc-menu" :style="menuStyle" role="menu" @click.stop>
        <button type="button" role="menuitem" @click="pick('edit')"><Pencil :size="15" />编辑</button>
        <button type="button" role="menuitem" @click="pick('style')"><Palette :size="15" />样式</button>
        <button type="button" role="menuitem" @click="pick('note')"><NotebookPen :size="15" />备注</button>
        <button v-if="!item.archived" type="button" role="menuitem" @click="pick('archive')"><Archive :size="15" />归档</button>
        <button v-else type="button" role="menuitem" @click="pick('restore')"><ArchiveRestore :size="15" />恢复</button>
        <button type="button" role="menuitem" class="is-danger" @click="pick('remove')"><Trash2 :size="15" />删除</button>
      </div>
    </Teleport>

    <div class="cdc-head">
      <span v-if="showIcon" class="cdc-icon" :style="{ background: item.iconColor || 'transparent' }">{{ item.iconValue }}</span>
      <h3 v-if="item.title" class="cdc-title">{{ item.title }}</h3>
    </div>

    <div class="cdc-count" :class="{ 'is-multi': parts.length > 1, 'is-long': parts.length > 2, 'is-tiny': parts.length > 3 }">
      <span v-for="(part, index) in parts" :key="index" class="cdc-part">
        <b :class="{ 'is-clock': part.clock }">{{ part.value }}</b><i v-if="part.unit">{{ part.unit }}</i>
      </span>
    </div>

    <p class="cdc-foot">{{ footText }}</p>
  </article>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Archive, ArchiveRestore, MoreHorizontal, NotebookPen, Palette, Pencil, Star, Trash2 } from 'lucide-vue-next'
import { useExclusivePanels } from '../../utils/exclusivePanels.js'
import { cardTheme, countParts, footerText } from './countdownUtils.js'

const props = defineProps({
  item: { type: Object, required: true },
  today: { type: String, required: true },
  now: { type: Number, default: 0 }
})

const emit = defineEmits(['edit', 'style', 'note', 'archive', 'restore', 'remove', 'pin', 'cycle'])

const menuOpen = ref(false)
const moreButton = ref(null)
const menuStyle = ref({})

// 每张卡一个命名空间：同一张卡的开合不互相打断，但能关掉别的卡片菜单与页面菜单
useExclusivePanels(`countdown-card:${props.item.publicId}`, [
  ['menu', () => menuOpen.value, value => { if (!value) menuOpen.value = false }]
])

const theme = computed(() => cardTheme(props.item))
const tone = computed(() => theme.value.tone)
const parts = computed(() => countParts(props.item, props.today, new Date(props.now || Date.now())))
const footText = computed(() => footerText(props.item, props.today))
const showIcon = computed(() => ['ICON', 'EMOJI', 'TEXT'].includes(props.item.iconType) && props.item.cover !== 'IMAGE')

/** 菜单挂在 body 上：卡片为了圆角图片做了 overflow:hidden，内联菜单会被裁掉 */
function placeMenu(left, top) {
  const width = 146
  const height = 248
  menuStyle.value = {
    position: 'fixed',
    top: `${Math.round(Math.max(8, Math.min(top, window.innerHeight - height)))}px`,
    left: `${Math.round(Math.max(8, Math.min(left, window.innerWidth - width - 8)))}px`,
    width: `${width}px`
  }
  menuOpen.value = true
}

function toggleMenu() {
  if (menuOpen.value) {
    menuOpen.value = false
    return
  }
  const button = moreButton.value
  if (!button) return
  const rect = button.getBoundingClientRect()
  placeMenu(rect.right - 146, rect.bottom + 6)
}

/** 右键菜单：定位到指针处，与「更多」共用同一份菜单 */
function openMenuAt(event) {
  if (event?.clientX == null) return
  placeMenu(event.clientX, event.clientY)
}
function pick(action) {
  menuOpen.value = false
  emit(action)
}
function onPointerDown(event) {
  if (event.target.closest('.cdc-tools') || event.target.closest('.cdc-menu')) return
  menuOpen.value = false
}
function closeOnViewportChange() {
  menuOpen.value = false
}
watch(menuOpen, open => {
  if (typeof document === 'undefined') return
  if (open) {
    document.addEventListener('pointerdown', onPointerDown, true)
    window.addEventListener('resize', closeOnViewportChange)
    window.addEventListener('scroll', closeOnViewportChange, true)
  } else {
    document.removeEventListener('pointerdown', onPointerDown, true)
    window.removeEventListener('resize', closeOnViewportChange)
    window.removeEventListener('scroll', closeOnViewportChange, true)
  }
})
onBeforeUnmount(() => {
  if (typeof document === 'undefined') return
  document.removeEventListener('pointerdown', onPointerDown, true)
  window.removeEventListener('resize', closeOnViewportChange)
  window.removeEventListener('scroll', closeOnViewportChange, true)
})
</script>

<style scoped>
.cdc {
  position: relative;
  display: flex;
  min-width: 0;
  flex-direction: column;
  aspect-ratio: 1.39;
  padding: calc(var(--cw, 240px) * .058) calc(var(--cw, 240px) * .065) calc(var(--cw, 240px) * .05);
  border: 0;
  border-radius: 14px;
  overflow: hidden;
  cursor: pointer;
  isolation: isolate;
  transition: transform .16s ease, box-shadow .16s ease;
}
.cdc::after {
  position: absolute;
  z-index: -1;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, .12) 0%, transparent 26%, transparent 72%, rgba(0, 0, 0, .16) 100%);
  content: '';
  pointer-events: none;
}
.cdc.is-light::after { display: none; }
.cdc:hover { transform: translateY(-2px); box-shadow: 0 12px 26px rgba(17, 24, 39, .16); }
.cdc:focus-visible { outline: 2px solid #3d8fce; outline-offset: 2px; }
.cdc.is-archived { opacity: .72; }

.cdc-tools {
  position: absolute;
  z-index: 3;
  top: calc(var(--cw, 240px) * .045);
  right: calc(var(--cw, 240px) * .045);
  display: flex;
  gap: 6px;
  opacity: 0;
  transition: opacity .15s ease;
}
.cdc:hover .cdc-tools,
.cdc:focus-within .cdc-tools { opacity: 1; }
.cdc-tools:has(.is-on) { opacity: 1; }
.cdc-tool {
  display: grid;
  width: calc(var(--cw, 240px) * .11);
  height: calc(var(--cw, 240px) * .11);
  min-width: 26px;
  min-height: 26px;
  place-items: center;
  border: 0;
  border-radius: 9px;
  background: rgba(255, 255, 255, .9);
  color: #2b3240;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(15, 23, 42, .16);
}
.cdc-tool:hover { background: #fff; }
.cdc-tool.is-on { color: #f2b13c; }
.cdc-tool.is-more { background: rgba(30, 35, 46, .74); color: #fff; }
.cdc-tool.is-more:hover { background: rgba(30, 35, 46, .9); }
.cdc.is-light .cdc-tool { background: rgba(255, 255, 255, .92); }

.cdc-menu {
  z-index: 200;
  display: grid;
  padding: 6px;
  border-radius: 12px;
  background: var(--card, #fff);
  box-shadow: 0 18px 44px rgba(15, 23, 42, .22);
}
.cdc-menu button {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ink);
  font: inherit;
  font-size: 13.5px;
  text-align: left;
  cursor: pointer;
}
.cdc-menu button:hover { background: var(--panel2, rgba(15, 23, 42, .06)); }
.cdc-menu button.is-danger { color: #e05a4f; }

.cdc-head {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 0;
}
.cdc-icon {
  display: grid;
  width: calc(var(--cw, 240px) * .1);
  height: calc(var(--cw, 240px) * .1);
  min-width: 20px;
  min-height: 20px;
  place-items: center;
  border-radius: 50%;
  font-size: calc(var(--cw, 240px) * .058);
  line-height: 1;
}
.cdc-title {
  min-width: 0;
  margin: 0;
  font-size: calc(var(--cw, 240px) * .06);
  font-weight: 600;
  line-height: 1.3;
  overflow: hidden;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cdc-count {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-height: 0;
  color: var(--num);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}
.cdc-part { display: inline-flex; align-items: baseline; }
/* 时钟段没有单位字，靠 flex gap 会和前面的数字贴在一起 */
.cdc-count:has(.is-clock) { gap: 8px; }
.cdc-part b {
  font-size: calc(var(--cw, 240px) * .225);
  font-weight: 500;
  letter-spacing: -.02em;
}
.cdc-count.is-multi .cdc-part b { font-size: calc(var(--cw, 240px) * .135); }
.cdc-count.is-long .cdc-part b { font-size: calc(var(--cw, 240px) * .115); }
.cdc-count.is-tiny .cdc-part b { font-size: calc(var(--cw, 240px) * .1); }
.cdc-count.is-multi .cdc-part b.is-clock { font-size: calc(var(--cw, 240px) * .1); letter-spacing: .01em; opacity: .92; }
.cdc-count.is-long .cdc-part b.is-clock,
.cdc-count.is-tiny .cdc-part b.is-clock { font-size: calc(var(--cw, 240px) * .088); letter-spacing: .01em; opacity: .92; }
.cdc-part i {
  margin-left: 1px;
  font-size: calc(var(--cw, 240px) * .05);
  font-style: normal;
  font-weight: 500;
  opacity: .9;
}
.cdc-count.is-multi .cdc-part i { margin: 0 3px 0 1px; }
.cdc-foot {
  margin: 0;
  color: var(--sub);
  font-size: calc(var(--cw, 240px) * .044);
  line-height: 1.4;
  overflow: hidden;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (hover: none) {
  .cdc-tools { opacity: 1; }
}
@media (prefers-reduced-motion: reduce) {
  .cdc { transition: none; }
}
</style>
