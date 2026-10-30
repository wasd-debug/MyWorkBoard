<template>
  <Teleport to="body">
    <div v-if="open" class="hc-mask" @click.self="emit('close')">
      <section class="hc-sheet" role="dialog" aria-modal="true" aria-label="记录打卡" @click.stop>
        <header class="hc-head">
          <span class="hc-icon" :style="iconStyle">{{ habit?.icon || '✓' }}</span>
          <div>
            <b>{{ habit?.name }}</b>
            <small>{{ dateLabel }} · 今天已记录 {{ current }}/{{ target }}{{ unit }}</small>
          </div>
        </header>

        <div class="hc-body">
          <label class="hc-line">
            <span>本次记录</span>
            <input
              ref="inputEl"
              v-model="value"
              class="hc-input"
              inputmode="numeric"
              maxlength="6"
              :placeholder="`本次记录多少${unit || '量'}`"
              aria-label="本次记录量"
              @input="sanitize"
              @keydown.enter.prevent="confirm"
            />
            <em>{{ unit }}</em>
          </label>
          <p class="hc-hint">目标 {{ target }}{{ unit }}/天，还差 {{ remain }}{{ unit }}</p>
        </div>

        <footer class="hc-foot">
          <button type="button" class="hc-btn" @click="emit('close')">取消</button>
          <button type="button" class="hc-btn is-primary" :disabled="!amount" @click="confirm">保存</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { logDateLabel } from './habitUtils.js'

const props = defineProps({
  open: { type: Boolean, default: false },
  habit: { type: Object, default: null },
  date: { type: String, default: '' },
  current: { type: Number, default: 0 },
  target: { type: Number, default: 1 }
})
const emit = defineEmits(['close', 'confirm'])

const value = ref('')
const inputEl = ref(null)

const unit = computed(() => props.habit?.amountUnit || '')
const dateLabel = computed(() => (props.date ? logDateLabel(props.date) : ''))
const remain = computed(() => Math.max(0, props.target - props.current))
const amount = computed(() => {
  const parsed = Number(value.value)
  return Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : 0
})
const iconStyle = computed(() => ({
  background: `color-mix(in srgb, ${props.habit?.color || 'var(--accent)'} 22%, transparent)`,
  color: props.habit?.color || 'var(--accent)'
}))

watch(() => props.open, open => {
  if (!open) return
  value.value = ''
  nextTick(() => inputEl.value?.focus())
})

function sanitize() {
  value.value = String(value.value).replace(/[^\d]/g, '').slice(0, 6)
}

function confirm() {
  if (!amount.value) return
  emit('confirm', amount.value)
}
</script>

<style scoped>
.hc-mask { position: fixed; z-index: 175; inset: 0; display: grid; place-items: center; padding: 20px; background: rgb(15 23 42 / 38%); }
.hc-sheet { width: min(340px, calc(100vw - 28px)); border-radius: 16px; background: var(--card); box-shadow: 0 26px 60px rgb(15 23 42 / 26%); }
.hc-head { display: flex; align-items: center; gap: 10px; padding: 16px 18px 6px; }
.hc-icon { display: grid; width: 36px; height: 36px; flex: 0 0 auto; place-items: center; border-radius: 11px; font-size: 18px; }
.hc-head b { display: block; font-size: 14px; }
.hc-head small { color: var(--muted); font-size: 11.5px; }
.hc-body { display: grid; gap: 8px; padding: 8px 18px 4px; }
.hc-line { display: flex; align-items: center; gap: 8px; color: var(--muted); font-size: 12.5px; }
.hc-input { flex: 1; min-width: 0; height: 38px; padding: 0 12px; border: 1px solid var(--line); border-radius: 9px; background: var(--panel, var(--paper)); color: var(--ink); font: inherit; font-size: 14px; text-align: center; }
.hc-input:focus { outline: 2px solid color-mix(in srgb, var(--priority-low, #4d78db) 26%, transparent); outline-offset: -1px; }
.hc-line em { color: var(--ink2); font-style: normal; }
.hc-hint { margin: 0; color: var(--dim, var(--muted)); font-size: 11.5px; }
.hc-foot { display: flex; gap: 10px; padding: 12px 18px 16px; }
.hc-btn { flex: 1; height: 36px; border: 1px solid var(--line2); border-radius: 9px; background: transparent; color: var(--ink2); font: inherit; font-size: 13.5px; cursor: pointer; }
.hc-btn.is-primary { border-color: var(--priority-low, #4d78db); background: var(--priority-low, #4d78db); color: #fff; font-weight: 600; }
.hc-btn:disabled { opacity: .45; cursor: default; }
</style>
