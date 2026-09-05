<template>
  <button :type="type" :class="classes" :disabled="disabled || undefined">
    <slot />
  </button>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  variant: { type: String, default: 'primary' },
  size: { type: String, default: 'default' },
  type: { type: String, default: 'button' },
  disabled: Boolean
})

const classes = computed(() => [
  'inline-flex min-h-11 items-center justify-center gap-2 rounded-xl px-4 text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50',
  props.size === 'sm' ? 'min-h-10 px-3 text-xs' : '',
  props.variant === 'outline' ? 'border border-border bg-transparent text-foreground hover:bg-accent' : '',
  props.variant === 'ghost' ? 'text-muted-foreground hover:bg-accent hover:text-foreground' : '',
  props.variant === 'danger' ? 'bg-destructive text-destructive-foreground hover:opacity-90' : '',
  props.variant === 'primary' ? 'bg-primary text-primary-foreground hover:opacity-90' : ''
])
</script>
