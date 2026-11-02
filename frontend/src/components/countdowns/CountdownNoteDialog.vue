<template>
  <Teleport to="body">
    <div v-if="open" class="nd-mask" role="dialog" aria-modal="true" aria-label="备注" @click.self="emit('close')">
      <section class="nd-sheet" @click.stop>
        <button type="button" class="nd-dot" aria-label="关闭" title="关闭" @click="emit('close')"></button>
        <header class="nd-head">
          <span class="nd-icon" :style="{ background: item?.iconColor || '#f2b13c' }">{{ item?.iconValue || '⏳' }}</span>
          <h2 class="nd-title">{{ item?.title || '备注' }}</h2>
        </header>

        <div class="nd-field">
          <textarea
            ref="area"
            v-model="text"
            class="nd-input"
            maxlength="1000"
            placeholder="记录点什么…"
            aria-label="备注内容"
          ></textarea>
          <span class="nd-clock" aria-hidden="true"><Clock :size="15" /></span>
        </div>

        <footer class="nd-foot">
          <button type="button" class="nd-btn" @click="emit('close')">取消</button>
          <button type="button" class="nd-btn is-primary" @click="emit('submit', text)">保存</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'
import { Clock } from 'lucide-vue-next'

const props = defineProps({
  open: Boolean,
  item: { type: Object, default: null }
})
const emit = defineEmits(['close', 'submit'])

const text = ref('')
const area = ref(null)

watch(() => props.open, async open => {
  if (!open) return
  text.value = props.item?.note || ''
  await nextTick()
  area.value?.focus()
})
</script>

<style scoped>
.nd-mask{position:fixed;inset:0;z-index:88;display:grid;place-items:center;padding:24px;background:rgba(20,24,32,.3)}
.nd-sheet{position:relative;display:flex;width:min(560px,100%);flex-direction:column;padding:20px 22px 16px;border-radius:16px;background:var(--card,#fff);box-shadow:0 32px 80px rgba(15,23,42,.32)}
.nd-dot{position:absolute;top:12px;left:14px;width:13px;height:13px;min-width:13px;min-height:13px;border:0;border-radius:50%;background:#f2645a;cursor:pointer}
.nd-head{display:flex;align-items:center;justify-content:center;gap:10px;margin-bottom:16px}
.nd-icon{display:grid;width:38px;height:38px;place-items:center;border-radius:50%;font-size:20px;line-height:1}
.nd-title{margin:0;color:var(--ink,#1d1d21);font-size:17px;font-weight:600}
.nd-field{position:relative;flex:1;min-height:0}
.nd-input{width:100%;min-height:240px;padding:14px 16px 34px;border:1.5px solid #3d8fce;border-radius:12px;background:transparent;color:var(--ink,#1d1d21);font:inherit;font-size:14.5px;line-height:1.7;resize:none;outline:none}
.nd-input::placeholder{color:var(--muted,#9aa1ad)}
.nd-clock{position:absolute;right:14px;bottom:12px;display:grid;place-items:center;color:var(--muted,#9aa1ad)}
.nd-foot{display:flex;flex:none;justify-content:flex-end;gap:12px;padding-top:16px}
.nd-btn{min-width:110px;height:38px;padding:0 20px;border:1px solid var(--line,#e2e4ea);border-radius:9px;background:var(--card,#fff);color:var(--ink,#1d1d21);font:inherit;font-size:14.5px;cursor:pointer}
.nd-btn.is-primary{border-color:transparent;background:#3d8fce;color:#fff;font-weight:600}
@media(max-width:520px){.nd-sheet{padding:18px 14px 12px}.nd-input{min-height:180px}.nd-btn{min-width:88px}}
</style>
