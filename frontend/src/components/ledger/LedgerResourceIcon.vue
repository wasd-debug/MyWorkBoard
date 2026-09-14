<template>
  <span
    class="ledger-resource-icon"
    :class="{ compact }"
    :style="resolvedColor ? { color: resolvedColor, borderColor: colorMix } : undefined"
    :title="title || undefined"
    aria-hidden="true"
  >
    <component :is="component" />
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { ledgerIconComponent, ledgerIconFallback } from './resourceIcons'

const props = defineProps({
  icon: { type: String, default: '' },
  type: { type: String, default: 'other' },
  color: { type: String, default: '' },
  title: { type: String, default: '' },
  compact: Boolean
})

const component = computed(() => ledgerIconComponent(props.icon, ledgerIconFallback(props.type), props.type))
const resolvedColor = computed(() => props.color || '')
const colorMix = computed(() => resolvedColor.value ? `color-mix(in srgb, ${resolvedColor.value} 35%, var(--line))` : '')
</script>

<style scoped>
.ledger-resource-icon {
  display: inline-grid;
  box-sizing: border-box;
  place-items: center;
  align-items: center;
  justify-content: center;
  flex: none;
  min-width: 30px;
  max-width: 30px;
  width: 30px;
  min-height: 30px;
  max-height: 30px;
  height: 30px;
  border: 1px solid var(--line);
  border-radius: 50%;
  background: var(--card);
  color: var(--accent);
}
.ledger-resource-icon.compact {
  min-width: 22px;
  max-width: 22px;
  width: 22px;
  min-height: 22px;
  max-height: 22px;
  height: 22px;
}
.ledger-resource-icon :deep(svg) {
  display: block;
  flex: none;
  width: 15px;
  height: 15px;
}
.ledger-resource-icon.compact :deep(svg) {
  width: 12px;
  height: 12px;
}
</style>
