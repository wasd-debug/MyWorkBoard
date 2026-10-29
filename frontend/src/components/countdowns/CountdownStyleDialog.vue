<template>
  <Teleport to="body">
    <div v-if="open" class="cd-modal" role="dialog" aria-modal="true" aria-label="卡片样式" @click.self="emit('close')">
      <div class="cd-sheet">
        <h3 class="cd-sheet-title">样式</h3>

        <div class="cd-preview" :style="{ background: coverBackground(draft.cover, draft.color) }">
          <span class="cd-preview-title">{{ item?.title || '纪念日' }}</span>
          <b class="cd-preview-count">{{ previewCount }}</b>
        </div>

        <div class="cd-option-grid" role="radiogroup" aria-label="卡片样式">
          <button
            v-for="style in COVER_STYLES"
            :key="style.key"
            type="button"
            role="radio"
            :aria-checked="draft.cover === style.key"
            :aria-label="style.label"
            :class="{ active: draft.cover === style.key }"
            :style="{ background: coverBackground(style.key, draft.color) }"
            @click="draft.cover = style.key"
          ><span>{{ style.label }}</span></button>
        </div>

        <div class="cd-colors" role="radiogroup" aria-label="卡片颜色">
          <button
            v-for="color in COVER_COLORS"
            :key="color"
            type="button"
            role="radio"
            :aria-checked="draft.color === color"
            :aria-label="`颜色 ${color}`"
            :class="{ active: draft.color === color }"
            :style="{ background: color }"
            @click="draft.color = color"
          ></button>
        </div>

        <footer class="cd-sheet-foot">
          <button type="button" class="cd-btn is-primary" @click="emit('submit', { ...draft })">确定</button>
          <button type="button" class="cd-btn" @click="emit('close')">取消</button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, reactive, watch } from 'vue'
import { COVER_COLORS, COVER_STYLES, coverBackground, formatCount, todayKey } from './countdownUtils.js'

const props = defineProps({
  open: Boolean,
  item: { type: Object, default: null }
})
const emit = defineEmits(['close', 'submit'])

const draft = reactive({ cover: 'SOLID', color: '#4d78db' })
watch(() => props.open, open => {
  if (!open) return
  draft.cover = props.item?.cover || 'SOLID'
  draft.color = props.item?.color || '#4d78db'
})
const previewCount = computed(() => (props.item ? formatCount(props.item, 'DAY', todayKey()).map(part => `${part.value}${part.unit}`).join('') : '5'))
</script>

<style scoped>
.cd-modal{position:fixed;inset:0;z-index:80;display:grid;place-items:center;padding:20px;background:rgba(12,16,22,.42)}
.cd-sheet{width:min(400px,100%);padding:16px 16px 12px;border:1px solid var(--line);border-radius:12px;background:var(--card);box-shadow:0 24px 60px rgba(15,23,42,.28)}
.cd-sheet-title{margin:0 0 12px;font-size:.86rem;font-weight:600}
.cd-preview{position:relative;display:flex;flex-direction:column;justify-content:space-between;height:132px;padding:12px 14px;border-radius:10px;color:#fff}
.cd-preview-title{align-self:flex-end;font-size:.74rem;font-weight:600;opacity:.92}
.cd-preview-count{font-size:2.6rem;font-weight:600;line-height:1}
.cd-option-grid{display:grid;grid-template-columns:repeat(6,1fr);gap:6px;margin-top:14px}
.cd-option-grid button{position:relative;height:46px;border:2px solid transparent;border-radius:8px;cursor:pointer;overflow:hidden}
.cd-option-grid button.active{border-color:#3b82f6}
.cd-option-grid button span{position:absolute;inset:auto 0 0;padding:2px 0;background:rgba(0,0,0,.42);color:#fff;font-size:.58rem}
.cd-colors{display:flex;flex-wrap:wrap;gap:7px;margin:14px 0 4px}
.cd-colors button{width:20px;height:20px;border:0;border-radius:50%;cursor:pointer;box-shadow:0 0 0 2px transparent}
.cd-colors button.active{box-shadow:0 0 0 2px var(--card),0 0 0 3px #3b82f6}
.cd-sheet-foot{display:flex;justify-content:flex-end;gap:8px;margin-top:14px}
.cd-btn{min-width:64px;height:30px;padding:0 14px;border:1px solid var(--line2);border-radius:7px;background:var(--card);color:var(--ink);font-size:.78rem;cursor:pointer}
.cd-btn.is-primary{border-color:transparent;background:#3b82f6;color:#fff}
</style>
