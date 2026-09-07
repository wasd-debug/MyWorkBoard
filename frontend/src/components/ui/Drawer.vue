<template>
  <Teleport to="body"><div v-if="open" class="ui-drawer-backdrop" @click.self="$emit('update:open', false)"><section class="ui-drawer" role="dialog" aria-modal="true" :aria-label="title"><div class="ui-drawer-handle" aria-hidden="true"></div><header class="ui-drawer-head"><h2>{{ title }}</h2><button class="ui-sheet-close" type="button" aria-label="关闭" @click="$emit('update:open', false)">×</button></header><div class="ui-drawer-body"><slot /></div><footer v-if="$slots.footer" class="ui-sheet-foot"><slot name="footer" /></footer></section></div></Teleport>
  </template>
<script setup>defineProps({ open: Boolean, title: { type: String, default: '' } }); defineEmits(['update:open'])</script>
