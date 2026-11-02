<template>
  <div class="set-select">
    <select :value="modelValue" :aria-label="label || undefined" @change="emit('update:modelValue', normalize($event.target.value))">
      <option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option>
    </select>
    <ChevronDown :size="13" class="set-select-caret" aria-hidden="true" />
    <RouterLink v-if="to" :to="to" class="set-select-link">{{ linkLabel }}<ChevronRight :size="12" /></RouterLink>
  </div>
</template>

<script setup>
import { ChevronDown, ChevronRight } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, required: true },
  label: { type: String, default: '' },
  to: { type: String, default: '' },
  linkLabel: { type: String, default: '设置' }
})
const emit = defineEmits(['update:modelValue'])
function normalize(value) {
  const matched = props.options.find(option => String(option.value) === String(value))
  return matched ? matched.value : value
}
</script>

<style scoped>
.set-select{display:inline-flex;align-items:center;gap:6px}
.set-select select{min-height:28px;padding:3px 24px 3px 9px;border:1px solid transparent;border-radius:7px;background:var(--panel2);color:var(--ink);font-size:.76rem;appearance:none;cursor:pointer}
.set-select select:hover{border-color:var(--line2)}
.set-select select:focus{outline:none;border-color:var(--accent)}
.set-select-caret{margin-left:-20px;color:var(--muted);pointer-events:none}
.set-select-link{display:inline-flex;align-items:center;gap:1px;margin-left:4px;color:var(--accent);font-size:.72rem;text-decoration:none}
.set-select-link:hover{text-decoration:underline}
</style>
