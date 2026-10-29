<template>
  <div class="set-row" :class="{ 'is-stacked': stacked }">
    <div class="set-row-label">
      <span class="set-row-title">{{ label }}</span>
      <small v-if="desc" class="set-row-desc">{{ desc }}</small>
    </div>
    <div class="set-row-control"><slot /></div>
  </div>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  desc: { type: String, default: '' },
  stacked: Boolean
})
</script>

<style scoped>
.set-row{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:9px 0;border-bottom:1px solid color-mix(in srgb,var(--line) 60%,transparent)}
.set-row:last-child{border-bottom:0}
.set-row.is-stacked{align-items:flex-start;flex-direction:column;gap:8px}
.set-row-label{display:grid;gap:2px;min-width:0}
.set-row-title{font-size:.8rem;color:var(--ink)}
.set-row-desc{margin:0;color:var(--muted);font-size:.7rem;line-height:1.5}
.set-row-control{flex:none;display:flex;align-items:center;gap:8px}
.set-row.is-stacked .set-row-control{width:100%}
</style>
