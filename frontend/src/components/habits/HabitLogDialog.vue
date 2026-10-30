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
import { MOODS, logDateLabel, weekdayLabel } from './habitUtils.js'

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

const dateLabel = computed(() => (props.date ? `${logDateLabel(props.date)} ${weekdayLabel(props.date)}` : ''))
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
.hl-mask { position: fixed; z-index: 176; inset: 0; display: grid; place-items: center; padding: 20px; background: rgb(24 30 40 / 32%); }
.hl-sheet { position: relative; display: grid; width: min(462px, calc(100vw - 24px)); gap: 12px; padding: 22px 24px 18px; border-radius: 18px; background: var(--card); box-shadow: 0 26px 62px rgb(15 23 42 / 26%); }
.hl-dot { position: absolute; top: 14px; left: 14px; width: 10px; height: 10px; border-radius: 50%; background: #f2706a; }
.hl-head { display: flex; align-items: center; justify-content: center; gap: 10px; }
.hl-icon { display: grid; width: 40px; height: 40px; place-items: center; border-radius: 50%; font-size: 21px; line-height: 1; }
.hl-head b { font-size: 17px; font-weight: 600; letter-spacing: -.01em; }
.hl-date { margin: 0; color: var(--muted); font-size: 14.5px; text-align: center; }

.hl-moods { display: flex; justify-content: center; gap: 14px; padding: 8px 0 4px; }
.hl-mood {
  display: grid; width: 52px; height: 52px; place-items: center;
  border: 0; border-radius: 50%; background: transparent;
  font-size: 30px; line-height: 1; cursor: pointer;
  filter: grayscale(1); opacity: .5;
  transition: transform .12s ease, filter .12s ease, opacity .12s ease;
}
.hl-mood:hover { transform: translateY(-3px); opacity: .7; }
.hl-mood.on { filter: none; opacity: 1; transform: translateY(-3px); }

.hl-note {
  min-height: 158px; padding: 16px;
  border: 0; border-radius: 12px;
  background: color-mix(in srgb, var(--ink) 5%, transparent); color: var(--ink);
  font: inherit; font-size: 14.5px; line-height: 1.6;
  resize: none;
}
.hl-note::placeholder { color: var(--dim, var(--muted)); }
.hl-note:focus { outline: 2px solid rgb(61 143 206 / 26%); outline-offset: -1px; }

.hl-foot { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding-top: 2px; }
.hl-foot > div { display: flex; gap: 12px; }
.hl-mute { padding: 6px 2px; border: 0; background: transparent; color: #3d8fce; font: inherit; font-size: 14px; cursor: pointer; }
.hl-btn { min-width: 104px; height: 44px; border: 1px solid var(--line2); border-radius: 10px; background: var(--card); color: var(--ink); font: inherit; font-size: 14.5px; cursor: pointer; }
.hl-btn.is-primary { border-color: #3d8fce; background: #3d8fce; color: #fff; font-weight: 600; }
.hl-btn.is-primary:hover { background: #3480ba; }
</style>
