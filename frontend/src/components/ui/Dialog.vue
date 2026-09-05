<template>
  <Teleport to="body">
    <div v-if="open" class="ui-dialog-backdrop" @click.self="$emit('update:open', false)">
      <section class="ui-dialog" role="dialog" aria-modal="true" :aria-label="title">
        <header class="ui-dialog-head">
          <h2>{{ title }}</h2>
          <button type="button" class="ui-dialog-close" aria-label="关闭" @click="$emit('update:open', false)">×</button>
        </header>
        <div class="ui-dialog-body"><slot /></div>
        <footer v-if="$slots.footer" class="ui-dialog-foot"><slot name="footer" /></footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
defineProps({ open: Boolean, title: { type: String, default: '' } })
defineEmits(['update:open'])
</script>
