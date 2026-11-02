<template>
  <section class="set-pane">
    <div class="set-data-card">
      <TaskDataPanel hide-heading />
    </div>
  </section>
</template>

<script setup>
import TaskDataPanel from '../tasks/TaskDataPanel.vue'

defineProps({ pane: { type: String, required: true } })
</script>

<style scoped>
.set-pane{display:grid;gap:14px}
.set-data-card{padding:18px 18px 20px;border-radius:10px;background:var(--set-group-bg,color-mix(in srgb,var(--paper) 62%,var(--card)))}
</style>
