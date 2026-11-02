<template>
  <div class="set-select">
    <span class="set-select-value" aria-hidden="true">{{ currentLabel }}</span>
    <select :value="modelValue" :aria-label="label || undefined" @change="emit('update:modelValue', normalize($event.target.value))">
      <option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option>
    </select>
    <ChevronDown :size="13" class="set-select-caret" aria-hidden="true" />
    <RouterLink v-if="to" :to="to" class="set-select-link">{{ linkLabel }}<ChevronRight :size="12" /></RouterLink>
  </div>
</template>

<script setup>
import { computed } from 'vue'
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
const currentLabel = computed(() => {
  const matched = props.options.find(option => String(option.value) === String(props.modelValue))
  return matched ? matched.label : String(props.modelValue ?? '')
})
function normalize(value) {
  const matched = props.options.find(option => String(option.value) === String(value))
  return matched ? matched.value : value
}
</script>

<style scoped>
.set-select{position:relative;display:inline-flex;align-items:center;gap:6px;color:var(--muted)}
.set-select-value{font-size:.78rem;white-space:nowrap}
.set-select select{position:absolute;inset:0;width:100%;height:100%;opacity:0;font-size:.78rem;cursor:pointer;appearance:none}
.set-select-caret{color:var(--muted);pointer-events:none}
.set-select:hover .set-select-value,.set-select:focus-within .set-select-value{color:var(--ink)}
.set-select-link{display:inline-flex;align-items:center;gap:1px;margin-left:4px;color:var(--accent);font-size:.72rem;text-decoration:none}
.set-select-link:hover{text-decoration:underline}
</style>
