<template>
  <DialogRoot :open="open" @update:open="$emit('update:open', $event)">
    <DialogPortal>
      <DialogOverlay class="ui-sheet-backdrop" />
      <DialogContent class="ui-sheet" :aria-describedby="undefined">
        <header class="ui-sheet-head">
          <div><div class="label">LEDGER / EDITOR</div><DialogTitle as="h2">{{ title }}</DialogTitle></div>
          <DialogClose class="ui-sheet-close" aria-label="关闭">×</DialogClose>
        </header>
        <div class="ui-sheet-body"><slot /></div>
        <footer v-if="$slots.footer" class="ui-sheet-foot"><slot name="footer" /></footer>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>

<script setup>
import { DialogClose, DialogContent, DialogOverlay, DialogPortal, DialogRoot, DialogTitle } from 'reka-ui'

defineProps({ open: Boolean, title: { type: String, default: '' } })
defineEmits(['update:open'])
</script>
