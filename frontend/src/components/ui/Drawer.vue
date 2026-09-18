<template>
  <DialogRoot :open="open" @update:open="$emit('update:open', $event)">
    <DialogPortal>
      <DialogOverlay class="ui-drawer-backdrop" />
      <DialogContent class="ui-drawer" :aria-describedby="undefined">
        <div class="ui-drawer-handle" aria-hidden="true" />
        <header class="ui-drawer-head">
          <DialogTitle as="h2">{{ title }}</DialogTitle>
          <DialogClose class="ui-sheet-close" aria-label="关闭">×</DialogClose>
        </header>
        <div class="ui-drawer-body"><slot /></div>
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
