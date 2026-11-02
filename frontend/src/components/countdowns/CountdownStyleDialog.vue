<template>
  <Teleport to="body">
    <div v-if="open" class="sd-mask" role="dialog" aria-modal="true" aria-label="卡片样式" @click.self="emit('close')">
      <section class="sd-sheet" @click.stop>
        <button type="button" class="sd-dot" aria-label="关闭" title="关闭" @click="emit('close')"></button>
        <h2 class="sd-title">样式</h2>
        <div class="sd-body">
          <CountdownStylePanel v-model="draft" :item="item" :today="today" />
        </div>
        <footer class="sd-foot">
          <button type="button" class="sd-btn" @click="emit('close')">取消</button>
          <button type="button" class="sd-btn is-primary" @click="emit('submit', { ...draft })">保存</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, watch } from 'vue'
import { todayKey } from './countdownUtils.js'
import CountdownStylePanel from './CountdownStylePanel.vue'

const props = defineProps({
  open: Boolean,
  item: { type: Object, default: null }
})
const emit = defineEmits(['close', 'submit'])

const today = todayKey()
const draft = ref({})

watch(() => props.open, open => {
  if (!open) return
  const source = props.item || {}
  draft.value = {
    cover: source.cover || 'IMAGE',
    coverImage: source.coverImage || null,
    color: source.color || '#4d78db',
    iconType: source.iconType || 'EMOJI',
    iconValue: source.iconValue || '⏳',
    iconColor: source.iconColor || '#f2b13c',
    unit: source.unit || 'DAY',
    preciseSeconds: Boolean(source.preciseSeconds)
  }
})
</script>

<style scoped>
.sd-mask{position:fixed;inset:0;z-index:88;display:grid;place-items:center;padding:24px;background:rgba(20,24,32,.3)}
.sd-sheet{position:relative;display:flex;width:min(600px,100%);max-height:min(90vh,760px);flex-direction:column;padding:20px 22px 16px;border-radius:16px;background:var(--card,#fff);box-shadow:0 32px 80px rgba(15,23,42,.32)}
.sd-dot{position:absolute;top:12px;left:14px;width:13px;height:13px;min-width:13px;min-height:13px;border:0;border-radius:50%;background:#f2645a;cursor:pointer}
.sd-title{margin:0 0 12px;color:var(--ink,#1d1d21);font-size:17px;font-weight:600;text-align:center}
.sd-body{flex:1;min-height:0;overflow-y:auto;padding:0 2px 6px;scrollbar-gutter:stable}
.sd-foot{display:flex;flex:none;justify-content:center;gap:14px;padding-top:14px}
.sd-btn{min-width:124px;height:38px;padding:0 20px;border:1px solid var(--line,#e2e4ea);border-radius:9px;background:var(--card,#fff);color:var(--ink,#1d1d21);font:inherit;font-size:14.5px;cursor:pointer}
.sd-btn.is-primary{border-color:transparent;background:#3d8fce;color:#fff;font-weight:600}
@media(max-width:520px){.sd-sheet{padding:18px 14px 12px}.sd-btn{min-width:96px}}
</style>
