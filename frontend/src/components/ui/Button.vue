<template>
  <button :type="type" :class="classes" :disabled="disabled || undefined">
    <slot />
  </button>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  variant: { type: String, default: 'ink' },
  size: { type: String, default: 'default' },
  type: { type: String, default: 'button' },
  disabled: Boolean
})

const classes = computed(() => [
  'ui-button',
  `variant-${props.variant}`,
  `size-${props.size}`
])
</script>
