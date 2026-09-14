<template>
  <button
    :type="type"
    class="ledger-action-icon"
    :class="[`action-${action}`, { destructive }]"
    :aria-label="label"
    :title="label"
    :disabled="disabled"
  >
    <component :is="icon" aria-hidden="true" />
  </button>
</template>

<script setup>
import { computed } from 'vue'
import {
  ArrowLeft, ArrowRight, Check, Close, CopyDocument, Delete, Download, Document,
  EditPen, Filter, Grid, Hide, MagicStick, Operation, Plus, Refresh, RefreshLeft,
  Setting, Switch, Upload, View
} from '@element-plus/icons-vue'

const props = defineProps({
  action: { type: String, required: true },
  label: { type: String, required: true },
  type: { type: String, default: 'button' },
  disabled: Boolean
})
const icons = {
  add: Plus, edit: EditPen, delete: Delete, copy: CopyDocument, download: Download, pdf: Document,
  show: View, hide: Hide, switch: Switch, restore: RefreshLeft, refresh: Refresh,
  grid: Grid, ai: MagicStick, layout: Setting, manage: Operation, filter: Filter,
  upload: Upload,
  previous: ArrowLeft, next: ArrowRight, confirm: Check, close: Close
}
const icon = computed(() => icons[props.action] || Grid)
const destructive = computed(() => props.action === 'delete')
</script>

<style scoped>
.ledger-action-icon {
  display: inline-grid;
  place-items: center;
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 1px solid var(--line2);
  border-radius: 5px;
  background: var(--card);
  color: var(--ink2);
  cursor: pointer;
  transition: background .16s ease, color .16s ease, border-color .16s ease, transform .16s ease;
}
.ledger-action-icon svg { width: 16px; height: 16px; transition: transform .16s ease; }
.ledger-action-icon:hover:not(:disabled) {
  border-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent);
  transform: translateY(-1px);
}
.ledger-action-icon:hover:not(:disabled) svg { transform: scale(1.12); }
.ledger-action-icon.destructive:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--down) 48%, var(--line));
  background: color-mix(in srgb, var(--down) 9%, var(--card));
  color: var(--down);
}
.ledger-action-icon:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.ledger-action-icon:disabled { opacity: .45; cursor: not-allowed; }
@media (prefers-reduced-motion: reduce) {
  .ledger-action-icon, .ledger-action-icon svg { transition: none; }
  .ledger-action-icon:hover:not(:disabled), .ledger-action-icon:hover:not(:disabled) svg { transform: none; }
}
</style>
