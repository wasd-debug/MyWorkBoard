<template>
  <section class="hw" :class="{ 'is-editing': editing }">
    <header class="hw-head">
      <div class="hw-title">
        <span v-if="kicker" class="hw-kicker">{{ kicker }}</span>
        <h2>{{ title }}</h2>
        <small v-if="meta">{{ meta }}</small>
      </div>
      <div class="hw-actions">
        <slot name="actions" />
        <span v-if="editing" class="hw-drag" title="按住拖动调整顺序" aria-hidden="true"><GripVertical :size="15" /></span>
      </div>
    </header>
    <div class="hw-body"><slot /></div>
    <footer v-if="$slots.footer" class="hw-foot"><slot name="footer" /></footer>
  </section>
</template>

<script setup>
import { GripVertical } from 'lucide-vue-next'

defineProps({
  title: { type: String, required: true },
  kicker: { type: String, default: '' },
  meta: { type: String, default: '' },
  editing: Boolean
})
</script>

<style scoped>
.hw {
  display: flex;
  height: 100%;
  min-width: 0;
  flex-direction: column;
  padding: 16px 18px 14px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--card);
  box-shadow: 0 1px 2px rgba(20, 23, 28, .04);
  transition: border-color .18s ease, box-shadow .18s ease;
}
.hw:hover { border-color: color-mix(in srgb, var(--accent) 26%, var(--line)); box-shadow: 0 4px 16px color-mix(in srgb, var(--accent) 8%, rgba(20, 23, 28, .06)); }
.hw.is-editing { border-style: dashed; border-color: color-mix(in srgb, var(--accent) 46%, var(--line)); }
.hw-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.hw-title { min-width: 0; }
.hw-kicker { display: block; color: var(--muted); font-size: 10px; font-weight: 700; letter-spacing: .12em; }
.hw-title h2 { margin: 2px 0 0; font-size: 16px; font-weight: 720; letter-spacing: -.02em; }
.hw-title small { display: block; margin-top: 2px; color: var(--muted); font-size: 11px; }
.hw-actions { display: flex; flex: 0 0 auto; align-items: center; gap: 6px; }
.hw-drag { display: grid; width: 26px; height: 26px; place-items: center; border: 1px solid var(--line2); border-radius: 8px; color: var(--muted); cursor: grab; }
.hw-body { min-width: 0; min-height: 0; flex: 1; }
.hw-foot { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 12px; padding-top: 10px; border-top: 1px solid var(--line); color: var(--muted); font-size: 11px; }
.hw :deep(button.hw-btn), .hw :deep(a.hw-btn) { display: inline-flex; height: 28px; align-items: center; gap: 5px; padding: 0 10px; border: 1px solid var(--line2); border-radius: 8px; background: var(--card); color: var(--ink2); font-size: 11px; font-weight: 650; text-decoration: none; }
.hw :deep(button.hw-btn:hover), .hw :deep(a.hw-btn:hover) { border-color: var(--ink2); color: var(--ink); }
.hw :deep(button.hw-btn.primary), .hw :deep(a.hw-btn.primary) { border-color: var(--accent); background: var(--accent); color: var(--paper); }
.hw :deep(button.hw-btn.primary:hover), .hw :deep(a.hw-btn.primary:hover) { filter: brightness(1.06); color: var(--paper); }
.hw :deep(button.hw-btn.ghost), .hw :deep(a.hw-btn.ghost) { border-color: transparent; background: transparent; }
.hw :deep(.hw-seg) { display: inline-flex; padding: 2px; border: 1px solid var(--line); border-radius: 8px; background: var(--paper); }
.hw :deep(.hw-seg button) { height: 24px; padding: 0 9px; border: 0; border-radius: 6px; background: transparent; color: var(--muted); font-size: 11px; font-weight: 650; }
.hw :deep(.hw-seg button.active) { background: var(--card); color: var(--ink); box-shadow: 0 1px 2px rgba(20, 23, 28, .12); }
.hw :deep(.hw-empty) { display: grid; gap: 4px; padding: 22px 6px; color: var(--muted); font-size: 12px; text-align: center; }
</style>
