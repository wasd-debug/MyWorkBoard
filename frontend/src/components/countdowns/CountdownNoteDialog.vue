<template>
  <Teleport to="body">
    <div v-if="open" class="cd-modal" role="dialog" aria-modal="true" aria-label="备注" @click.self="emit('close')">
      <div class="cd-sheet">
        <header class="cd-sheet-head">
          <span class="cd-icon" :style="{ background: `color-mix(in srgb, ${item?.color || '#4d78db'} 22%, transparent)`, color: item?.color || '#4d78db' }">{{ item?.icon || '⏳' }}</span>
          <b class="cd-sheet-title">{{ item?.title }}</b>
        </header>
        <textarea v-model="draft" class="cd-note" maxlength="1000" placeholder="记录点什么…" aria-label="备注内容"></textarea>
        <footer class="cd-sheet-foot">
          <button type="button" class="cd-btn is-primary" @click="emit('submit', draft)">保存</button>
          <button type="button" class="cd-btn" @click="emit('close')">取消</button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  open: Boolean,
  item: { type: Object, default: null }
})
const emit = defineEmits(['close', 'submit'])
const draft = ref('')
watch(() => props.open, open => {
  if (open) draft.value = props.item?.note || ''
})
</script>

<style scoped>
.cd-modal{position:fixed;inset:0;z-index:80;display:grid;place-items:center;padding:20px;background:rgba(12,16,22,.42)}
.cd-sheet{width:min(440px,100%);padding:16px 16px 12px;border:1px solid var(--line);border-radius:12px;background:var(--card);box-shadow:0 24px 60px rgba(15,23,42,.28)}
.cd-sheet-head{display:flex;align-items:center;justify-content:center;gap:8px;margin-bottom:12px}
.cd-icon{display:grid;width:28px;height:28px;place-items:center;border-radius:50%;font-size:15px}
.cd-sheet-title{font-size:.88rem}
.cd-note{width:100%;min-height:170px;padding:10px 12px;border:1px solid var(--line2);border-radius:9px;background:var(--panel2);color:var(--ink);font-size:.8rem;line-height:1.6;resize:vertical}
.cd-note:focus{outline:none;border-color:var(--accent)}
.cd-sheet-foot{display:flex;justify-content:flex-end;gap:8px;margin-top:12px}
.cd-btn{min-width:64px;height:30px;padding:0 14px;border:1px solid var(--line2);border-radius:7px;background:var(--card);color:var(--ink);font-size:.78rem;cursor:pointer}
.cd-btn.is-primary{border-color:transparent;background:#3b82f6;color:#fff}
</style>
