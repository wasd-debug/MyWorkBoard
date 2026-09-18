<template>
  <SwitchRoot
    :model-value="modelValue"
    :disabled="disabled"
    class="ui-toggle"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <SwitchThumb />
  </SwitchRoot>
</template>

<script setup>
import { SwitchRoot, SwitchThumb } from 'reka-ui'

defineProps({ modelValue: Boolean, disabled: Boolean })
defineEmits(['update:modelValue'])
</script>
