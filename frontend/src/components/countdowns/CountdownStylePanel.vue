<template>
  <div class="sp">
    <div class="sp-preview" :class="`is-${theme.tone}`" :style="theme.style">
      <div class="sp-preview-head">
        <span v-if="showIcon" class="sp-preview-icon" :style="{ background: value.iconColor }">{{ value.iconValue }}</span>
        <span class="sp-preview-title">{{ item?.title || '纪念日' }}</span>
      </div>
      <div class="sp-preview-count" :class="{ 'is-multi': previewParts.length > 1 }">
        <span v-for="(part, index) in previewParts" :key="index"><b>{{ part.value }}</b><i v-if="part.unit">{{ part.unit }}</i></span>
      </div>
      <p class="sp-preview-foot">{{ previewFoot }}</p>
    </div>

    <div class="sp-edit">
      <button type="button" class="sp-edit-btn" @click="chooseImage">编辑图片</button>
      <button v-if="value.coverImage" type="button" class="sp-edit-clear" @click="set({ coverImage: null })">移除图片</button>
      <input ref="fileInput" class="sp-file" type="file" accept="image/*" @change="onFile" />
    </div>

    <div class="sp-presets" role="radiogroup" aria-label="封面样式">
      <button
        v-for="preset in COVER_PRESETS"
        :key="preset.key"
        type="button"
        role="radio"
        :aria-checked="value.cover === preset.key"
        :class="{ on: value.cover === preset.key }"
        :style="presetStyle(preset.key)"
        @click="set({ cover: preset.key })"
      >
        <span class="sp-preset-title">{{ item?.title || '纪念日' }}</span>
        <b class="sp-preset-num">{{ previewParts[0]?.value || '5' }}</b>
        <i class="sp-preset-foot">距离 {{ targetDate }} 还有</i>
        <span class="sp-preset-check"><Check :size="12" /></span>
        <span v-if="preset.key !== 'IMAGE'" class="sp-crown">
          <Crown :size="12" />
        </span>
      </button>
    </div>

    <div class="sp-row">
      <span class="sp-row-label">颜色</span>
      <div class="sp-colors" role="radiogroup" aria-label="卡片颜色">
        <button
          v-for="color in PRESET_COLORS"
          :key="color"
          type="button"
          role="radio"
          :aria-checked="value.color === color"
          :class="{ on: value.color === color }"
          :style="{ background: color }"
          :aria-label="`颜色 ${color}`"
          @click="set({ color })"
        ></button>
      </div>
    </div>

    <div class="sp-field">
      <span class="sp-field-label">显示时间样式</span>
      <div class="sp-select">
        <button type="button" :aria-expanded="unitOpen" @click="unitOpen = !unitOpen">{{ unitLabel(value.unit) }}<ChevronDown :size="15" /></button>
        <div v-if="unitOpen" class="sp-pop" role="listbox">
          <button
            v-for="unit in DISPLAY_UNITS"
            :key="unit.key"
            type="button"
            role="option"
            :aria-selected="value.unit === unit.key"
            :class="{ on: value.unit === unit.key }"
            @click="chooseUnit(unit.key)"
          >
            {{ unit.label }}<Check v-if="value.unit === unit.key" :size="16" />
          </button>
        </div>
      </div>
    </div>

    <div class="sp-field is-switch">
      <span class="sp-field-label">精确到秒</span>
      <button
        type="button"
        class="sp-switch"
        :class="{ on: value.preciseSeconds }"
        role="switch"
        :aria-checked="value.preciseSeconds"
        @click="set({ preciseSeconds: !value.preciseSeconds })"
      ><span></span></button>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Check, ChevronDown, Crown } from 'lucide-vue-next'
import { COVER_PRESETS, DISPLAY_UNITS, PRESET_COLORS, cardTheme, countParts, footerText, unitLabel } from './countdownUtils.js'

const props = defineProps({
  modelValue: { type: Object, required: true },
  item: { type: Object, default: null },
  today: { type: String, required: true }
})
const emit = defineEmits(['update:modelValue'])

const unitOpen = ref(false)
const fileInput = ref(null)

const value = computed(() => props.modelValue)
const previewItem = computed(() => ({ ...(props.item || {}), ...props.modelValue }))
const theme = computed(() => cardTheme(previewItem.value))
const previewParts = computed(() => countParts(previewItem.value, props.today))
const previewFoot = computed(() => footerText(previewItem.value, props.today))
const targetDate = computed(() => previewItem.value.targetDate || props.today)
const showIcon = computed(() => ['ICON', 'EMOJI', 'TEXT'].includes(props.modelValue.iconType) && props.modelValue.cover !== 'IMAGE')

function set(patch) {
  emit('update:modelValue', { ...props.modelValue, ...patch })
}
function chooseUnit(key) {
  unitOpen.value = false
  set({ unit: key })
}
function presetStyle(key) {
  return cardTheme({ ...previewItem.value, cover: key }).style
}
function chooseImage() {
  unitOpen.value = false
  fileInput.value?.click()
}
async function onFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  const dataUrl = await compress(file)
  if (dataUrl) set({ coverImage: dataUrl, cover: 'IMAGE' })
}

/** 压缩到 1000px 宽、JPEG 0.72，避免同步载荷过大 */
function compress(file) {
  return new Promise(resolve => {
    const reader = new FileReader()
    reader.onload = () => {
      const image = new Image()
      image.onload = () => {
        const max = 1000
        const scale = Math.min(1, max / Math.max(image.width, image.height))
        const canvas = document.createElement('canvas')
        canvas.width = Math.round(image.width * scale)
        canvas.height = Math.round(image.height * scale)
        const context = canvas.getContext('2d')
        if (!context) {
          resolve(String(reader.result))
          return
        }
        context.drawImage(image, 0, 0, canvas.width, canvas.height)
        resolve(canvas.toDataURL('image/jpeg', 0.72))
      }
      image.onerror = () => resolve(null)
      image.src = String(reader.result)
    }
    reader.onerror = () => resolve(null)
    reader.readAsDataURL(file)
  })
}

watch(unitOpen, open => {
  if (!open || typeof document === 'undefined') return
  const close = event => {
    if (event.target.closest('.sp-select')) return
    unitOpen.value = false
    document.removeEventListener('pointerdown', close, true)
  }
  document.addEventListener('pointerdown', close, true)
})
onBeforeUnmount(() => {
  if (typeof document === 'undefined') return
  unitOpen.value = false
})
</script>

<style scoped>
.sp{display:flex;flex-direction:column;gap:12px}
.sp-preview{position:relative;display:flex;width:min(320px,100%);aspect-ratio:1.39;margin:2px auto 0;flex-direction:column;padding:14px 16px;border-radius:14px;overflow:hidden;isolation:isolate}
.sp-preview::after{position:absolute;z-index:-1;inset:0;background:linear-gradient(180deg,rgba(0,0,0,.12),transparent 30%,transparent 70%,rgba(0,0,0,.16));content:''}
.sp-preview.is-light::after{display:none}
.sp-preview-head{display:flex;align-items:center;justify-content:center;gap:6px}
.sp-preview-icon{display:grid;width:24px;height:24px;place-items:center;border-radius:50%;font-size:14px;line-height:1}
.sp-preview-title{font-size:14px;font-weight:600}
.sp-preview-count{display:flex;flex:1;align-items:center;justify-content:center;gap:2px;color:var(--num);font-size:56px;font-weight:500;line-height:1}
.sp-preview-count.is-multi{font-size:34px}
.sp-preview-count i{margin-left:2px;font-size:14px;font-style:normal;opacity:.9}
.sp-preview-foot{margin:0;color:var(--sub);font-size:11.5px;text-align:center}
.sp-edit{display:flex;align-items:center;justify-content:center;gap:12px}
.sp-edit-btn{padding:4px 12px;border:0;border-radius:8px;background:color-mix(in srgb,#3d8fce 14%,transparent);color:#2f7fb8;font:inherit;font-size:13.5px;cursor:pointer}
.sp-edit-btn:hover{background:color-mix(in srgb,#3d8fce 22%,transparent)}
.sp-edit-clear{padding:4px 8px;border:0;background:transparent;color:var(--muted);font:inherit;font-size:12.5px;cursor:pointer}
.sp-file{display:none}
.sp-presets{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:10px}
.sp-presets button{position:relative;display:flex;aspect-ratio:1.38;flex-direction:column;align-items:center;justify-content:center;gap:1px;padding:6px 4px;border:2px solid transparent;border-radius:10px;overflow:hidden;cursor:pointer}
.sp-presets button.on{border-color:#3d8fce;box-shadow:0 0 0 2px color-mix(in srgb,#3d8fce 20%,transparent)}
.sp-preset-title{font-size:9px;opacity:.9;line-height:1.2;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.sp-preset-num{font-size:20px;font-weight:500;line-height:1}
.sp-preset-foot{font-size:6.5px;font-style:normal;opacity:.62;line-height:1.2;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.sp-preset-check{position:absolute;right:4px;bottom:4px;display:none;width:18px;height:18px;place-items:center;border-radius:50%;background:#3d8fce;color:#fff}
.sp-presets button.on .sp-preset-check{display:grid}
.sp-crown{position:absolute;top:3px;right:3px;display:grid;width:16px;height:16px;place-items:center;border-radius:50%;background:#f2b13c;color:#fff}
.sp-row{display:flex;align-items:center;gap:12px}
.sp-row-label{flex:none;color:var(--ink2,#565d6b);font-size:13.5px}
.sp-colors{display:flex;flex-wrap:wrap;gap:8px}
.sp-colors button{width:22px;height:22px;min-width:22px;min-height:22px;border:0;border-radius:50%;cursor:pointer}
.sp-colors button.on{box-shadow:0 0 0 2px var(--card,#fff),0 0 0 4px #3d8fce}
.sp-field{display:flex;align-items:center;gap:12px;min-height:34px}
.sp-field-label{flex:none;width:96px;color:var(--ink2,#565d6b);font-size:13.5px}
.sp-select{position:relative;flex:1}
.sp-select>button{display:flex;width:100%;height:36px;align-items:center;justify-content:space-between;padding:0 12px;border:1px solid var(--line,#e6e6ea);border-radius:9px;background:transparent;color:var(--ink,#1d1d21);font:inherit;font-size:13.5px;cursor:pointer}
.sp-pop{position:absolute;z-index:12;top:calc(100% + 6px);right:0;left:0;display:grid;max-height:232px;padding:6px;border-radius:11px;background:var(--card,#fff);overflow-y:auto;box-shadow:0 18px 40px rgba(15,23,42,.2)}
.sp-pop button{display:flex;align-items:center;justify-content:space-between;padding:9px 10px;border:0;border-radius:8px;background:transparent;color:var(--ink,#1d1d21);font:inherit;font-size:13.5px;text-align:left;cursor:pointer}
.sp-pop button:hover{background:color-mix(in srgb,var(--ink,#1d1d21) 6%,transparent)}
.sp-pop button.on{color:#3d8fce;font-weight:600}
.sp-field.is-switch{justify-content:flex-start}
.sp-switch{position:relative;width:40px;height:23px;min-height:23px;border:0;border-radius:12px;background:rgba(120,130,145,.35);cursor:pointer;transition:background .16s ease}
.sp-switch span{position:absolute;top:3px;left:3px;width:17px;height:17px;border-radius:50%;background:#fff;transition:transform .16s ease}
.sp-switch.on{background:#3d8fce}
.sp-switch.on span{transform:translateX(17px)}
@media(max-width:560px){
  .sp-presets{grid-template-columns:repeat(3,minmax(0,1fr))}
  .sp-field-label{width:84px}
}
</style>
