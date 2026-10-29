<template>
  <Teleport to="body">
    <div v-if="open" class="cd-modal" role="dialog" aria-modal="true" :aria-label="editing ? '编辑倒数纪念日' : '新建倒数纪念日'" @click.self="emit('close')">
      <div class="cd-sheet">
        <header class="cd-sheet-head">
          <button ref="iconButton" type="button" class="cd-icon-pick" :style="{ background: `color-mix(in srgb, ${form.color} 22%, transparent)`, color: form.color }" :aria-label="`选择图标，当前 ${form.icon}`" @click="iconOpen = !iconOpen">{{ form.icon }}</button>
          <input v-model.trim="form.title" class="cd-title-input" maxlength="60" placeholder="纪念日名称" aria-label="名称" @keydown.enter.prevent="submit">
          <div v-if="iconOpen" class="cd-icon-grid">
            <button v-for="icon in ICONS" :key="icon" type="button" :class="{ active: icon === form.icon }" @click="pickIcon(icon)">{{ icon }}</button>
          </div>
        </header>

        <div class="cd-rows">
          <label class="cd-row">
            <span>日期</span>
            <input v-model="form.targetDate" type="date" required>
          </label>
          <label class="cd-row">
            <span>提醒</span>
            <select v-model="form.remind">
              <option v-for="option in REMIND_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
          </label>
          <label class="cd-row">
            <span>重复</span>
            <select v-model="form.repeat">
              <option v-for="option in REPEAT_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
          </label>
          <label class="cd-row">
            <span>类型</span>
            <select v-model="form.type">
              <option v-for="option in COUNTDOWN_TYPES" :key="option.key" :value="option.key">{{ option.label }}</option>
            </select>
          </label>
          <label class="cd-row">
            <span>计时方式</span>
            <select v-model="form.countingMode">
              <option value="COUNTDOWN">倒计时</option>
              <option value="COUNTUP">正计时</option>
            </select>
          </label>
          <label class="cd-row">
            <span>显示</span>
            <select v-model="form.display">
              <option v-for="option in DISPLAY_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
          </label>
        </div>

        <p v-if="error" class="cd-sheet-error">{{ error }}</p>

        <footer class="cd-sheet-foot">
          <button type="button" class="cd-btn is-primary" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
          <button type="button" class="cd-btn" @click="emit('close')">取消</button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { COUNTDOWN_TYPES, DISPLAY_OPTIONS, REMIND_OPTIONS, REPEAT_OPTIONS, defaultExtras, todayKey, typeOf } from './countdownUtils.js'

const props = defineProps({
  open: Boolean,
  item: { type: Object, default: null },
  initialType: { type: String, default: 'COUNTDOWN' },
  saving: Boolean,
  error: { type: String, default: '' }
})
const emit = defineEmits(['close', 'submit'])

const ICONS = ['🎉', '⏳', '🎂', '🎊', '💍', '🏖️', '✈️', '🎄', '🎓', '💼', '🏠', '❤️', '🌱', '📚', '🌟', '☀️']
const form = reactive({ title: '', targetDate: todayKey(), color: '#4d78db', icon: '⏳', ...defaultExtras() })
const iconOpen = ref(false)
const iconButton = ref(null)
const editing = ref(false)

function reset() {
  const source = props.item
  editing.value = Boolean(source)
  const base = defaultExtras(typeOf(source || { type: props.initialType }).key)
  Object.assign(form, base, {
    title: source?.title || '',
    targetDate: String(source?.targetDate || todayKey()).slice(0, 10),
    color: source?.color || base.color,
    icon: source?.icon || base.icon,
    type: source?.type || typeOf(source || { type: props.initialType }).key,
    remind: source?.remind || base.remind,
    repeat: source?.repeat || (source?.repeatYearly ? 'YEARLY' : base.repeat),
    countingMode: source?.countingMode || base.countingMode,
    display: source?.display || base.display,
    unit: source?.unit || base.unit,
    cover: source?.cover || base.cover
  })
}
watch(() => props.open, open => {
  if (open) {
    iconOpen.value = false
    reset()
  }
})
function pickIcon(icon) {
  form.icon = icon
  iconOpen.value = false
}
function submit() {
  if (!form.title) return
  emit('submit', { ...form })
}
function onOutside(event) {
  if (iconOpen.value && !event.target.closest('.cd-sheet-head')) iconOpen.value = false
}
watch(iconOpen, open => {
  if (typeof document === 'undefined') return
  if (open) document.addEventListener('pointerdown', onOutside, true)
  else document.removeEventListener('pointerdown', onOutside, true)
})
onBeforeUnmount(() => {
  if (typeof document !== 'undefined') document.removeEventListener('pointerdown', onOutside, true)
})
</script>

<style scoped>
.cd-modal{position:fixed;inset:0;z-index:80;display:grid;place-items:center;padding:20px;background:rgba(12,16,22,.42)}
.cd-sheet{width:min(420px,100%);padding:16px 14px 12px;border:1px solid var(--line);border-radius:12px;background:var(--card);box-shadow:0 24px 60px rgba(15,23,42,.28)}
.cd-sheet-head{position:relative;display:flex;align-items:center;gap:10px;padding:2px 4px 10px}
.cd-icon-pick{display:grid;flex:none;width:30px;height:30px;place-items:center;border:0;border-radius:50%;font-size:16px;cursor:pointer}
.cd-title-input{flex:1;min-height:30px;padding:4px 8px;border:0;border-bottom:1px solid var(--line2);background:transparent;color:var(--ink);font-size:.92rem}
.cd-title-input:focus{outline:none;border-bottom-color:var(--accent)}
.cd-icon-grid{position:absolute;top:40px;left:0;z-index:4;display:grid;grid-template-columns:repeat(8,1fr);gap:2px;padding:6px;border:1px solid var(--line);border-radius:8px;background:var(--card);box-shadow:0 14px 30px rgba(15,23,42,.18)}
.cd-icon-grid button{width:26px;height:26px;border:0;border-radius:6px;background:transparent;font-size:15px;cursor:pointer}
.cd-icon-grid button:hover,.cd-icon-grid button.active{background:var(--panel2)}
.cd-rows{display:grid;gap:2px;padding:4px 2px 6px}
.cd-row{display:flex;align-items:center;gap:10px;min-height:34px;padding:2px 2px}
.cd-row>span{flex:none;min-width:56px;color:var(--muted);font-size:.74rem}
.cd-row>input,.cd-row>select{flex:1;min-height:30px;padding:4px 8px;border:1px solid transparent;border-radius:7px;background:var(--panel2);color:var(--ink);font-size:.78rem}
.cd-row>input:focus,.cd-row>select:focus{outline:none;border-color:var(--accent);background:var(--card)}
.cd-sheet-error{margin:0 0 8px;color:var(--down);font-size:.74rem}
.cd-sheet-foot{display:flex;justify-content:flex-end;gap:8px;padding-top:8px}
.cd-btn{min-width:64px;height:30px;padding:0 14px;border:1px solid var(--line2);border-radius:7px;background:var(--card);color:var(--ink);font-size:.78rem;cursor:pointer}
.cd-btn.is-primary{border-color:transparent;background:#3b82f6;color:#fff}
.cd-btn:disabled{opacity:.6;cursor:default}
</style>
