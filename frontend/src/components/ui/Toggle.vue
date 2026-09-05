<template>
  <button
    type="button"
    role="switch"
    :aria-checked="modelValue"
    class="ui-toggle"
    :class="{ on: modelValue }"
    @click="$emit('update:modelValue', !modelValue)"
  >
    <span />
  </button>
</template>

<script setup>
defineProps({ modelValue: Boolean })
defineEmits(['update:modelValue'])
</script>
