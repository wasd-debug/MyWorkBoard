<template>
  <button
    type="button"
    role="switch"
    :aria-checked="modelValue"
    :disabled="disabled"
    class="ui-toggle"
    :class="{ on: modelValue }"
    @click="$emit('update:modelValue', !modelValue)"
  >
    <span />
  </button>
</template>

<script setup>
defineProps({ modelValue: Boolean, disabled: Boolean })
defineEmits(['update:modelValue'])
</script>
