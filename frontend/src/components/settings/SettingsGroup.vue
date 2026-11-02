<template>
  <section class="set-card">
    <header v-if="title || desc" class="set-card-head">
      <h4 v-if="title" class="set-card-title">{{ title }}</h4>
      <p v-if="desc" class="set-card-desc">{{ desc }}</p>
    </header>
    <div class="set-card-body"><slot /></div>
  </section>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  desc: { type: String, default: '' }
})
</script>

<style scoped>
.set-card{display:block}
.set-card-head{padding:0 0 8px}
.set-card-title{margin:0;color:var(--ink);font-size:.8rem;font-weight:600}
.set-card-desc{margin:4px 0 0;color:var(--muted);font-size:.7rem;line-height:1.55}
.set-card-body{padding:2px 14px;border-radius:10px;background:var(--set-group-bg,color-mix(in srgb,var(--paper) 62%,var(--card)))}
.set-card-body:deep(.set-row + .set-row){border-top:1px solid var(--set-row-line,color-mix(in srgb,var(--line) 76%,transparent))}
.set-card-body:deep(.set-row:first-child){padding-top:2px}
.set-card-body:deep(> :last-child){padding-bottom:2px}
</style>
