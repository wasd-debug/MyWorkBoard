<template>
  <DialogRoot :open="open" @update:open="$emit('update:open', $event)">
    <DialogPortal>
      <DialogOverlay class="ui-dialog-backdrop" />
      <DialogContent class="ui-dialog" :aria-describedby="undefined">
        <header class="ui-dialog-head">
          <DialogTitle as="h2">{{ title }}</DialogTitle>
          <DialogClose class="ui-dialog-close" aria-label="关闭">×</DialogClose>
        </header>
        <div class="ui-dialog-body"><slot /></div>
        <footer v-if="$slots.footer" class="ui-dialog-foot"><slot name="footer" /></footer>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>

<script setup>
import { DialogClose, DialogContent, DialogOverlay, DialogPortal, DialogRoot, DialogTitle } from 'reka-ui'

defineProps({ open: Boolean, title: { type: String, default: '' } })
defineEmits(['update:open'])
</script>
