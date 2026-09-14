<template>
  <Transition name="loading-fade">
    <div v-if="open" class="ledger-loading-overlay" role="status" aria-live="polite" :aria-label="label">
      <div class="ledger-loading-panel">
        <span class="ledger-loading-spinner" aria-hidden="true"></span>
        <span>{{ label }}</span>
      </div>
    </div>
  </Transition>
</template>

<script setup>
defineProps({
  open: Boolean,
  label: { type: String, default: '正在加载…' }
})
</script>

<style scoped>
.ledger-loading-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--paper) 64%, transparent);
  backdrop-filter: blur(2px);
}
.ledger-loading-panel {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 12px 17px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--card);
  box-shadow: 0 16px 38px color-mix(in srgb, var(--ink) 12%, transparent);
  color: var(--ink2);
  font-size: 12px;
}
.ledger-loading-spinner {
  width: 17px;
  height: 17px;
  border: 2px solid var(--line2);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: ledger-spin .72s linear infinite;
}
@keyframes ledger-spin { to { transform: rotate(360deg) } }
.loading-fade-enter-active,
.loading-fade-leave-active { transition: opacity .16s ease }
.loading-fade-enter-from,
.loading-fade-leave-to { opacity: 0 }
@media (prefers-reduced-motion: reduce) {
  .ledger-loading-spinner { animation: none }
}
</style>
