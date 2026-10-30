<template>
  <Teleport to="body">
    <div v-if="open" class="hl-mask" @click.self="emit('close')">
      <section class="hl-sheet" role="dialog" aria-modal="true" aria-label="打卡日志" @click.stop>
        <span class="hl-dot" aria-hidden="true" />
        <header class="hl-head">
          <span class="hl-icon" :style="iconStyle">{{ habit?.icon || '✓' }}</span>
          <b>{{ habit?.name }}</b>
        </header>
        <p class="hl-date">{{ dateLabel }}</p>

        <div class="hl-moods" role="radiogroup" aria-label="心情">
          <button
            v-for="mood in MOODS"
            :key="mood"
            type="button"
            class="hl-mood"
            :class="{ on: currentMood === mood }"
            role="radio"
            :aria-checked="currentMood === mood"
            :aria-label="`心情 ${mood}`"
            @click="currentMood = currentMood === mood ? '' : mood"
          >{{ mood }}</button>
        </div>

        <textarea
          ref="noteEl"
          v-model="currentNote"
          class="hl-note"
          maxlength="500"
          :placeholder="placeholder"
          aria-label="打卡心得"
        />

        <footer class="hl-foot">
          <button type="button" class="hl-mute" @click="dontShowAgain">不再显示</button>
          <div>
            <button type="button" class="hl-btn" @click="emit('close')">取消</button>
            <button type="button" class="hl-btn is-primary" @click="save">保存</button>
          </div>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { MOODS, logDateLabel } from './habitUtils.js'

const props = defineProps({
  open: { type: Boolean, default: false },
  habit: { type: Object, default: null },
  date: { type: String, default: '' },
  mood: { type: String, default: '' },
  note: { type: String, default: '' }
})
const emit = defineEmits(['close', 'save', 'mute'])

const currentMood = ref('')
const currentNote = ref('')
const noteEl = ref(null)

const dateLabel = computed(() => (props.date ? logDateLabel(props.date) : ''))
const placeholder = computed(() => (props.mood === 'SKIP' ? '这次先跳过，记一下原因吧' : '打卡成功！记录一下心得吧'))
const iconStyle = computed(() => ({
  background: `color-mix(in srgb, ${props.habit?.color || 'var(--accent)'} 22%, transparent)`,
  color: props.habit?.color || 'var(--accent)'
}))

watch(() => props.open, open => {
  if (!open) return
  currentMood.value = props.mood || ''
  currentNote.value = props.note || ''
  nextTick(() => noteEl.value?.focus())
})

function save() {
  emit('save', { mood: currentMood.value, note: currentNote.value.trim() })
}

function dontShowAgain() {
  emit('mute')
  emit('save', { mood: currentMood.value, note: currentNote.value.trim() })
}
</script>

<style scoped>
.hl-mask { position: fixed; z-index: 176; inset: 0; display: grid; place-items: center; padding: 20px; background: rgb(15 23 42 / 38%); }
.hl-sheet { position: relative; display: grid; width: min(420px, calc(100vw - 28px)); gap: 10px; padding: 18px 20px 16px; border-radius: 18px; background: var(--card); box-shadow: 0 30px 66px rgb(15 23 42 / 28%); }
.hl-dot { position: absolute; top: 12px; left: 12px; width: 9px; height: 9px; border-radius: 50%; background: #f26d63; }
.hl-head { display: flex; align-items: center; justify-content: center; gap: 8px; }
.hl-icon { display: grid; width: 30px; height: 30px; place-items: center; border-radius: 9px; font-size: 16px; }
.hl-head b { font-size: 15px; font-weight: 600; }
.hl-date { margin: 0; color: var(--muted); font-size: 12.5px; text-align: center; }

.hl-moods { display: flex; justify-content: center; gap: 12px; padding: 4px 0 2px; }
.hl-mood { display: grid; width: 38px; height: 38px; place-items: center; border: 0; border-radius: 50%; background: var(--paper); font-size: 20px; line-height: 1; cursor: pointer; filter: grayscale(1); opacity: .55; transition: transform .12s ease, filter .12s ease, opacity .12s ease; }
.hl-mood:hover { transform: translateY(-2px); }
.hl-mood.on { filter: none; opacity: 1; box-shadow: 0 0 0 2px var(--priority-low, #4d78db); }

.hl-note {
  min-height: 132px; padding: 12px;
  border: 0; border-radius: 12px;
  background: var(--paper); color: var(--ink);
  font: inherit; font-size: 13px; line-height: 1.6;
  resize: vertical;
}
.hl-note:focus { outline: 2px solid color-mix(in srgb, var(--priority-low, #4d78db) 26%, transparent); outline-offset: -1px; }

.hl-foot { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.hl-foot > div { display: flex; gap: 8px; }
.hl-mute { padding: 4px 2px; border: 0; background: transparent; color: var(--priority-low, #4d78db); font: inherit; font-size: 12.5px; cursor: pointer; }
.hl-btn { min-width: 76px; height: 34px; border: 1px solid var(--line2); border-radius: 9px; background: transparent; color: var(--ink2); font: inherit; font-size: 13px; cursor: pointer; }
.hl-btn.is-primary { border-color: var(--priority-low, #4d78db); background: var(--priority-low, #4d78db); color: #fff; font-weight: 600; }
</style>
