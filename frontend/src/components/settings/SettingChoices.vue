<template>
  <div class="set-choices" :class="{ 'is-cards': variant === 'cards' }" role="radiogroup" :aria-label="label || undefined">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      role="radio"
      :aria-checked="modelValue === option.value"
      :class="{ active: modelValue === option.value }"
      @click="emit('update:modelValue', option.value)"
    >
      <span v-if="option.color" class="set-choice-dot" :style="{ background: option.color }"></span>
      <span class="set-choice-label">{{ option.label }}</span>
      <small v-if="option.desc">{{ option.desc }}</small>
    </button>
  </div>
</template>

<script setup>
defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, required: true },
  label: { type: String, default: '' },
  variant: { type: String, default: 'segmented' }
})
const emit = defineEmits(['update:modelValue'])
</script>

<style scoped>
.set-choices{display:inline-flex;align-items:center;gap:4px;padding:2px;border-radius:8px;background:var(--panel2)}
.set-choices button{display:inline-flex;align-items:center;gap:5px;min-height:26px;padding:2px 10px;border:0;border-radius:6px;background:transparent;color:var(--muted);font-size:.75rem;cursor:pointer}
.set-choices button:hover{color:var(--ink)}
.set-choices button.active{background:var(--card);color:var(--ink);font-weight:600;box-shadow:0 1px 3px rgba(15,23,42,.12)}
.set-choice-dot{width:10px;height:10px;border-radius:50%;box-shadow:inset 0 0 0 1px rgba(0,0,0,.08)}
.set-choices.is-cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(112px,1fr));gap:8px;padding:0;background:transparent;width:100%}
.set-choices.is-cards button{flex-direction:column;align-items:flex-start;gap:3px;min-height:56px;padding:9px 11px;border:1px solid var(--line);border-radius:9px;background:var(--card);text-align:left}
.set-choices.is-cards button.active{border-color:#3b82f6;box-shadow:0 0 0 1px #3b82f6}
.set-choices.is-cards small{color:var(--muted);font-size:.66rem;font-weight:400}
</style>
