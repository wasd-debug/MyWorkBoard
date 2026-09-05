<template>
  <section class="rounded-2xl border border-border bg-card text-card-foreground shadow-sm">
    <header v-if="$slots.header" class="flex flex-col gap-1 border-b border-border p-5"><slot name="header" /></header>
    <div class="p-5"><slot /></div>
    <footer v-if="$slots.footer" class="border-t border-border p-5"><slot name="footer" /></footer>
  </section>
</template>
