<template>
  <input
    :value="modelValue"
    :type="type"
    :min="min"
    :max="max"
    :step="step"
    :placeholder="placeholder"
    :disabled="disabled"
    class="ui-input"
    @input="$emit('update:modelValue', $event.target.value)"
    @change="$emit('change', $event.target.value)"
  >
</template>

<script setup>
defineProps({
  modelValue: { type: [String, Number], default: '' },
  type: { type: String, default: 'text' },
  min: [String, Number],
  max: [String, Number],
  step: [String, Number],
  placeholder: { type: String, default: '' },
  disabled: Boolean
})
defineEmits(['update:modelValue', 'change'])
</script>
