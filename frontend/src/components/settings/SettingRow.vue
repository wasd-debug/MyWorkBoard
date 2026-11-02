<template>
  <div class="set-row" :class="{ 'is-stacked': stacked }">
    <div class="set-row-label">
      <component :is="icon" v-if="icon" :size="16" class="set-row-icon" aria-hidden="true" />
      <div class="set-row-text">
        <span class="set-row-title">{{ label }}</span>
        <small v-if="desc" class="set-row-desc">{{ desc }}</small>
      </div>
    </div>
    <div class="set-row-control"><slot /></div>
  </div>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  desc: { type: String, default: '' },
  icon: { type: [Object, Function], default: null },
  stacked: Boolean
})
</script>

<style scoped>
.set-row{display:flex;align-items:center;justify-content:space-between;gap:18px;min-height:46px;padding:7px 0}
.set-row.is-stacked{align-items:stretch;flex-direction:column;gap:10px;padding:11px 0}
.set-row-label{display:flex;align-items:center;gap:10px;min-width:0}
.set-row-text{display:grid;gap:2px;min-width:0}
.set-row-icon{flex:none;color:var(--muted)}
.set-row-title{color:var(--ink);font-size:.8rem;line-height:1.4}
.set-row-desc{margin:0;color:var(--muted);font-size:.7rem;line-height:1.5}
.set-row-control{flex:none;display:flex;align-items:center;gap:8px}
.set-row.is-stacked .set-row-control{width:100%}
</style>
