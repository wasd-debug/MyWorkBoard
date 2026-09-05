<template>
  <textarea
    :value="modelValue"
    :rows="rows"
    :placeholder="placeholder"
    class="ui-textarea"
    @input="$emit('update:modelValue', $event.target.value)"
  />
</template>

<script setup>
defineProps({
  modelValue: { type: String, default: '' },
  rows: { type: Number, default: 5 },
  placeholder: { type: String, default: '' }
})
defineEmits(['update:modelValue'])
</script>
