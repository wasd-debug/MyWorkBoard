<template>
  <Teleport to="body">
    <div v-if="open" class="hk-mask" @click.self="emit('close')">
      <section class="hk-sheet" role="dialog" aria-modal="true" aria-label="跳过打卡" @click.stop>
        <span class="hk-dot" aria-hidden="true" />
        <header class="hk-head">
          <span class="hk-icon" :style="iconStyle">{{ habit?.icon || '✓' }}</span>
          <b>跳过打卡</b>
        </header>
        <p class="hk-date">{{ dateLabel }}</p>

        <div class="hk-chips">
          <button
            v-for="item in PRESETS"
            :key="item"
            type="button"
            class="hk-chip"
            :class="{ on: current === item }"
            @click="current = current === item ? '' : item"
          >{{ item }}</button>
        </div>

        <textarea
          ref="reasonEl"
          v-model="current"
          class="hk-reason"
          maxlength="100"
          placeholder="跳过原因（可不填），比如出差、身体不适"
          aria-label="跳过原因"
        />

        <p class="hk-hint">跳过这天不打断连续打卡，也不会算进完成率。</p>

        <footer class="hk-foot">
          <button type="button" class="hk-btn" @click="emit('close')">取消</button>
          <button type="button" class="hk-btn is-primary" @click="confirm">跳过</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { logDateLabel, weekdayLabel } from './habitUtils.js'

/** 常见跳过原因，点一下直接填进去，也可以自己写 */
const PRESETS = ['身体不适', '出差在外', '计划休息', '临时有事']

const props = defineProps({
  open: { type: Boolean, default: false },
  habit: { type: Object, default: null },
  date: { type: String, default: '' },
  reason: { type: String, default: '' }
})
const emit = defineEmits(['close', 'confirm'])

const current = ref('')
const reasonEl = ref(null)

const dateLabel = computed(() => (props.date ? `${logDateLabel(props.date)} ${weekdayLabel(props.date)}` : ''))
const iconStyle = computed(() => ({
  background: `color-mix(in srgb, ${props.habit?.color || 'var(--accent)'} 22%, transparent)`,
  color: props.habit?.color || 'var(--accent)'
}))

watch(() => props.open, open => {
  if (!open) return
  current.value = props.reason || ''
  nextTick(() => reasonEl.value?.focus())
})

function confirm() {
  emit('confirm', current.value.trim())
}

function onKeydown(event) {
  if (!props.open) return
  if (event.key === 'Escape') emit('close')
  else if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) confirm()
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.hk-mask { position: fixed; z-index: 176; inset: 0; display: grid; place-items: center; padding: 20px; background: rgb(24 30 40 / 32%); }
.hk-sheet { position: relative; display: grid; width: min(432px, calc(100vw - 24px)); gap: 12px; padding: 22px 24px 18px; border-radius: 18px; background: var(--card); box-shadow: 0 26px 62px rgb(15 23 42 / 26%); }
.hk-dot { position: absolute; top: 14px; left: 14px; width: 10px; height: 10px; border-radius: 50%; background: #f2706a; }
.hk-head { display: flex; align-items: center; justify-content: center; gap: 10px; }
.hk-icon { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 50%; font-size: 18px; line-height: 1; }
.hk-head b { font-size: 17px; font-weight: 600; letter-spacing: -.01em; }
.hk-date { margin: 0; color: var(--muted); font-size: 13.5px; text-align: center; }

.hk-chips { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; padding: 2px 0 0; }
.hk-chip {
  padding: 6px 12px; border: 1px solid var(--line2); border-radius: 999px;
  background: transparent; color: var(--ink2); font: inherit; font-size: 12.5px; cursor: pointer;
  transition: background .12s ease, border-color .12s ease, color .12s ease;
}
.hk-chip:hover { border-color: color-mix(in srgb, #3d8fce 45%, var(--line2)); color: var(--ink); }
.hk-chip.on { border-color: #3d8fce; background: color-mix(in srgb, #3d8fce 12%, transparent); color: #3d8fce; font-weight: 600; }

.hk-reason {
  min-height: 92px; padding: 14px 16px;
  border: 0; border-radius: 12px;
  background: color-mix(in srgb, var(--ink) 5%, transparent); color: var(--ink);
  font: inherit; font-size: 14px; line-height: 1.6;
  resize: none;
}
.hk-reason::placeholder { color: var(--dim, var(--muted)); }
.hk-reason:focus { outline: 2px solid rgb(61 143 206 / 26%); outline-offset: -1px; }

.hk-hint { margin: 0; color: var(--dim, var(--muted)); font-size: 12px; text-align: center; }

.hk-foot { display: flex; justify-content: flex-end; gap: 12px; padding-top: 2px; }
.hk-btn { min-width: 104px; height: 44px; border: 1px solid var(--line2); border-radius: 10px; background: var(--card); color: var(--ink); font: inherit; font-size: 14.5px; cursor: pointer; }
.hk-btn.is-primary { border-color: #3d8fce; background: #3d8fce; color: #fff; font-weight: 600; }
.hk-btn.is-primary:hover { background: #3480ba; }
</style>
