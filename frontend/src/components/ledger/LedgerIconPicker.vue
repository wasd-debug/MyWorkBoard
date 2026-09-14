<template>
  <fieldset class="ledger-icon-picker">
    <legend>{{ label }}</legend>
    <div>
      <button
        v-for="option in iconOptions"
        :key="option.value"
        type="button"
        :class="{ active: modelValue === option.value }"
        :aria-pressed="modelValue === option.value"
        :aria-label="option.label"
        :title="option.label"
        @click="$emit('update:modelValue', option.value)"
      >
        <component :is="option.component" />
      </button>
    </div>
  </fieldset>
</template>

<script setup>
import { computed } from 'vue'
import { ledgerIconOptionsFor } from './resourceIcons'

const props = defineProps({
  modelValue: { type: String, default: '' },
  label: { type: String, default: '图标' },
  type: { type: String, default: 'other' }
})
const iconOptions = computed(() => ledgerIconOptionsFor(props.type))
defineEmits(['update:modelValue'])
</script>

<style scoped>
.ledger-icon-picker {
  margin: 0;
  padding: 0;
  border: 0;
}
.ledger-icon-picker legend {
  margin-bottom: 7px;
  color: var(--ink2);
  font-size: 11px;
  font-weight: 600;
}
.ledger-icon-picker > div {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 6px;
}
.ledger-icon-picker button {
  display: flex;
  min-width: 0;
  min-height: 40px;
  align-items: center;
  justify-content: center;
  padding: 5px;
  border: 1px solid var(--line);
  border-radius: 4px;
  background: var(--paper);
  color: var(--muted);
  font-size: 11px;
}
.ledger-icon-picker button:hover,
.ledger-icon-picker button.active {
  border-color: color-mix(in srgb, var(--accent) 55%, var(--line));
  background: var(--accent-soft);
  color: var(--accent);
}
.ledger-icon-picker svg {
  width: 17px;
  height: 17px;
}
@media (max-width: 560px) {
  .ledger-icon-picker > div { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}
</style>
