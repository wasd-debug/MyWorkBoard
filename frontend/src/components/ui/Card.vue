<template>
  <section class="card">
    <header v-if="$slots.header"><slot name="header" /></header>
    <div><slot /></div>
    <footer v-if="$slots.footer"><slot name="footer" /></footer>
  </section>
</template>
