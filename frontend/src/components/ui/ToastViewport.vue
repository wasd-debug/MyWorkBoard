<template>
  <div class="toast-viewport" aria-live="polite" aria-relevant="additions removals">
    <TransitionGroup name="toast">
      <div v-for="notice in messages" :key="notice.id" class="toast-notice" :class="`type-${notice.type}`" :role="notice.type === 'error' ? 'alert' : 'status'">
        <component :is="icons[notice.type]" class="toast-icon" aria-hidden="true" />
        <span>{{ notice.text }}</span>
        <button type="button" :aria-label="`关闭通知：${notice.text}`" title="关闭通知" @click="dismissMessage(notice.id)"><X aria-hidden="true" /></button>
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup>
import { AlertTriangle, CheckCircle2, CircleX, Info, X } from 'lucide-vue-next'
import { dismissMessage, messages } from '../../services/message.js'

const icons = { success: CheckCircle2, warning: AlertTriangle, error: CircleX, info: Info }
</script>

<style scoped>
.toast-viewport { position: fixed; top: 18px; left: 50%; z-index: 5000; display: flex; width: min(420px,calc(100vw - 28px)); transform: translateX(-50%); flex-direction: column; gap: 8px; pointer-events: none }
.toast-notice { display: grid; min-height: 42px; grid-template-columns: 18px minmax(0,1fr) 28px; align-items: center; gap: 9px; padding: 6px 7px 6px 12px; border: 1px solid var(--line2); border-radius: 6px; background: var(--card); color: var(--ink); box-shadow: 0 10px 28px #0000001f; font-size: 12px; line-height: 1.45; pointer-events: auto }
.toast-icon { width: 17px; height: 17px; color: var(--accent) }.type-success .toast-icon { color: var(--down) }.type-warning .toast-icon,.type-error .toast-icon { color: var(--up) }
.toast-notice button { display: grid; width: 28px; height: 28px; place-items: center; padding: 0; border: 0; border-radius: 4px; background: transparent; color: var(--muted) }.toast-notice button:hover { background: var(--paper); color: var(--ink) }.toast-notice button:focus-visible { outline: 2px solid var(--accent); outline-offset: 1px }.toast-notice button svg { width: 15px; height: 15px }
.toast-enter-active,.toast-leave-active { transition: opacity .18s ease,transform .18s ease }.toast-enter-from,.toast-leave-to { opacity: 0; transform: translateY(-8px) }
@media(max-width:767px){.toast-viewport{top:10px}.toast-notice{min-height:40px}}
@media(prefers-reduced-motion:reduce){.toast-enter-active,.toast-leave-active{transition:none}}
</style>
