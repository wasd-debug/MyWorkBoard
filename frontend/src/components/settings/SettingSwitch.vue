<template>
  <button
    type="button"
    class="set-switch"
    role="switch"
    :aria-checked="modelValue"
    :aria-label="label || undefined"
    :class="{ 'is-on': modelValue }"
    @click="emit('update:modelValue', !modelValue)"
  >
    <span class="set-switch-dot"></span>
  </button>
</template>

<script setup>
defineProps({ modelValue: Boolean, label: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue'])
</script>

<style scoped>
.set-switch{position:relative;flex:none;width:34px;height:20px;padding:0;border:0;border-radius:10px;background:var(--line2);cursor:pointer;transition:background .16s ease}
.set-switch.is-on{background:#3b82f6}
.set-switch-dot{position:absolute;top:2px;left:2px;width:16px;height:16px;border-radius:50%;background:#fff;transition:transform .16s ease}
.set-switch.is-on .set-switch-dot{transform:translateX(14px)}
.set-switch:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
</style>
