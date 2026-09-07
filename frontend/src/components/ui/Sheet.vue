<template>
  <Teleport to="body"><div v-if="open" class="ui-sheet-backdrop" @click.self="$emit('update:open', false)"><section class="ui-sheet" role="dialog" aria-modal="true" :aria-label="title"><header class="ui-sheet-head"><div><div class="label">LEDGER / EDITOR</div><h2>{{ title }}</h2></div><button class="ui-sheet-close" type="button" aria-label="关闭" @click="$emit('update:open', false)">×</button></header><div class="ui-sheet-body"><slot /></div><footer v-if="$slots.footer" class="ui-sheet-foot"><slot name="footer" /></footer></section></div></Teleport>
</template>
<script setup>defineProps({ open: Boolean, title: { type: String, default: '' } }); defineEmits(['update:open'])</script>
