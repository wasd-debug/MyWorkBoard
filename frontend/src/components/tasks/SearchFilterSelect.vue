<template>
  <div class="sfs">
    <button
      ref="triggerEl"
      type="button"
      class="sfs-trigger"
      :class="{ 'is-on': isActive, 'is-open': open }"
      :aria-expanded="open"
      :aria-label="triggerLabel"
      @click.stop="toggle"
    >
      <span class="sfs-text">{{ triggerLabel }}</span>
      <ChevronDown :size="13" aria-hidden="true" />
    </button>
    <Teleport to="body">
      <div
        v-if="open"
        ref="menuEl"
        class="sfs-menu"
        :class="{ 'is-multi': multiple }"
        :style="menuStyle"
        role="menu"
        :aria-label="label"
        @click.stop
      >
        <button
          v-if="multiple"
          type="button"
          class="sfs-item"
          role="menuitemradio"
          :aria-checked="!modelValue.length"
          :class="{ selected: !modelValue.length }"
          @click="pickAll"
        >
          <span>{{ allLabel }}</span><Check v-if="!modelValue.length" :size="15" />
        </button>
        <button
          v-for="option in options"
          :key="option.value"
          type="button"
          class="sfs-item"
          role="menuitemradio"
          :class="{ selected: isSelected(option.value) }"
          :aria-checked="isSelected(option.value)"
          :disabled="option.disabled"
          @click="pick(option.value)"
        >
          <span class="sfs-item-main">
            <i v-if="option.dot" class="sfs-dot" :style="{ background: option.dot }" aria-hidden="true"></i>
            <span class="sfs-item-label">{{ option.label }}</span>
            <small v-if="option.hint">{{ option.hint }}</small>
          </span>
          <Check v-if="isSelected(option.value)" :size="15" />
        </button>
        <div v-if="custom && modelValue === 'CUSTOM'" class="sfs-custom">
          <label><span>开始</span><input type="date" :value="customRange.start" aria-label="开始日期" @input="setCustom('start', $event.target.value)" /></label>
          <label><span>结束</span><input type="date" :value="customRange.end" :min="customRange.start || undefined" aria-label="结束日期" @input="setCustom('end', $event.target.value)" /></label>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Check, ChevronDown } from 'lucide-vue-next'
import { useExclusivePanels } from '../../utils/exclusivePanels.js'

const props = defineProps({
  label: { type: String, default: '' },
  options: { type: Array, default: () => [] },
  modelValue: { type: [String, Array], default: 'ALL' },
  multiple: { type: Boolean, default: false },
  allLabel: { type: String, default: '所有' },
  custom: { type: Boolean, default: false },
  customRange: { type: Object, default: () => ({ start: '', end: '' }) }
})
const emit = defineEmits(['update:modelValue', 'update:customRange', 'change'])

const open = ref(false)
const triggerEl = ref(null)
const menuEl = ref(null)
const menuStyle = ref({ visibility: 'hidden' })

// 一页里的多个下拉互斥：开一个自动收其它
useExclusivePanels('search-filter', [['menu', () => open.value, value => { open.value = value }]])

const selectedValues = computed(() => (Array.isArray(props.modelValue) ? props.modelValue : [props.modelValue]))
const isActive = computed(() => (Array.isArray(props.modelValue) ? props.modelValue.length > 0 : props.modelValue !== 'ALL'))
const triggerLabel = computed(() => {
  if (props.multiple) {
    if (!props.modelValue.length) return props.label
    if (props.modelValue.length === 1) {
      const option = props.options.find(item => item.value === props.modelValue[0])
      return option ? option.label : props.label
    }
    return `${props.label} · ${props.modelValue.length}`
  }
  const option = props.options.find(item => item.value === props.modelValue)
  return option ? option.label : props.label
})

watch(open, async value => {
  if (!value) return
  await nextTick()
  position()
})

function position() {
  const trigger = triggerEl.value
  const menu = menuEl.value
  if (!trigger || !menu) return
  const rect = trigger.getBoundingClientRect()
  const width = menu.offsetWidth
  const height = menu.offsetHeight
  const gap = 6
  const pad = 8
  let left = rect.left
  if (left + width > window.innerWidth - pad) left = Math.max(pad, window.innerWidth - width - pad)
  let top = rect.bottom + gap
  if (top + height > window.innerHeight - pad) top = Math.max(pad, rect.top - height - gap)
  menuStyle.value = { left: `${Math.round(left)}px`, top: `${Math.round(top)}px`, visibility: 'visible' }
}

function toggle() {
  open.value = !open.value
}

function isSelected(value) {
  return selectedValues.value.includes(value)
}

function pick(value) {
  if (props.multiple) {
    const next = isSelected(value) ? props.modelValue.filter(item => item !== value) : [...props.modelValue, value]
    emit('update:modelValue', next)
    emit('change', next)
    return
  }
  emit('update:modelValue', value)
  emit('change', value)
  if (value !== 'CUSTOM') open.value = false
}

function pickAll() {
  emit('update:modelValue', [])
  emit('change', [])
  open.value = false
}

function setCustom(key, value) {
  emit('update:customRange', { ...props.customRange, [key]: value })
}

function onPointerDown(event) {
  if (!open.value) return
  if (menuEl.value?.contains(event.target) || triggerEl.value?.contains(event.target)) return
  open.value = false
}

function onKeydown(event) {
  if (event.key === 'Escape' && open.value) {
    event.stopPropagation()
    open.value = false
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', onPointerDown, true)
  document.addEventListener('keydown', onKeydown, true)
  window.addEventListener('resize', position)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onPointerDown, true)
  document.removeEventListener('keydown', onKeydown, true)
  window.removeEventListener('resize', position)
})
</script>

<style scoped>
.sfs { position: relative; display: inline-flex; min-width: 0; }
.sfs-trigger {
  display: inline-flex; align-items: center; gap: 5px; height: 30px; max-width: 220px;
  padding: 0 9px; border: 1px solid transparent; border-radius: 7px; background: transparent;
  color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer;
}
.sfs-trigger:hover { background: color-mix(in srgb, var(--ink) 6%, transparent); color: var(--ink); }
.sfs-trigger.is-on { color: var(--ink); font-weight: 600; }
.sfs-trigger.is-open { background: color-mix(in srgb, var(--ink) 8%, transparent); color: var(--ink); }
.sfs-trigger:focus-visible { outline: 2px solid var(--accent); outline-offset: 1px; }
.sfs-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sfs-menu {
  position: fixed; z-index: 320; display: grid; gap: 2px; width: max-content; min-width: 180px;
  max-width: min(320px, calc(100vw - 24px)); max-height: min(420px, 64vh); overflow: auto;
  padding: 6px; border: 1px solid var(--line); border-radius: 10px; background: var(--card);
  box-shadow: 0 16px 40px rgb(0 0 0 / 26%);
}
.sfs-menu.is-multi { min-width: 220px; }
.sfs-item {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
  min-height: 34px; padding: 0 9px; border: 0; border-radius: 7px; background: transparent;
  color: var(--ink2); font: inherit; font-size: 13px; text-align: left; cursor: pointer;
}
.sfs-item:hover:not(:disabled) { background: color-mix(in srgb, var(--ink) 7%, transparent); color: var(--ink); }
.sfs-item.selected { color: var(--accent); font-weight: 650; }
.sfs-item:disabled { opacity: .45; cursor: not-allowed; }
.sfs-item-main { display: inline-flex; align-items: center; gap: 7px; min-width: 0; }
.sfs-item-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sfs-item small { color: var(--muted); font-size: 11px; font-weight: 500; }
.sfs-dot { width: 7px; height: 7px; flex: none; border-radius: 50%; }
.sfs-custom { display: grid; gap: 7px; padding: 8px 6px 4px; border-top: 1px solid var(--line); margin-top: 4px; }
.sfs-custom label { display: grid; gap: 4px; font-size: 12px; color: var(--muted); }
.sfs-custom input { min-height: 30px; padding: 3px 7px; border: 1px solid var(--line); border-radius: 6px; background: var(--paper); color: var(--ink); font: inherit; font-size: 12px; }
@media (max-width: 640px) {
  .sfs-trigger { max-width: 140px; }
}
</style>
