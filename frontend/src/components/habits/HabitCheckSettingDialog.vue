<template>
  <Teleport to="body">
    <div v-if="open" class="hs-mask" @click.self="emit('close')">
      <section class="hs-sheet" role="dialog" aria-modal="true" aria-label="打卡设置" @click.stop>
        <span class="hs-dot" aria-hidden="true" />
        <h2>打卡设置</h2>
        <label class="hs-row">
          <button type="button" class="hs-switch" :class="{ on: value }" role="switch" :aria-checked="value" aria-label="在“今天”、“最近7天”中显示" @click="value = !value"><i /></button>
          <span>在“今天”、“最近7天”中显示</span>
        </label>
        <footer class="hs-foot">
          <button type="button" class="hs-btn" @click="emit('close')">取消</button>
          <button type="button" class="hs-btn is-primary" @click="emit('save', value)">确定</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  show: { type: Boolean, default: true }
})
const emit = defineEmits(['close', 'save'])

const value = ref(true)
watch(() => props.open, open => {
  if (open) value.value = props.show
}, { immediate: true })
</script>

<style scoped>
.hs-mask { position: fixed; z-index: 176; inset: 0; display: grid; place-items: center; padding: 20px; background: rgb(15 23 42 / 38%); }
.hs-sheet { position: relative; display: grid; gap: 14px; width: min(420px, calc(100vw - 28px)); padding: 18px 20px 16px; border-radius: 18px; background: var(--card); box-shadow: 0 30px 66px rgb(15 23 42 / 28%); }
.hs-dot { position: absolute; top: 12px; left: 12px; width: 9px; height: 9px; border-radius: 50%; background: #f26d63; }
.hs-sheet h2 { margin: 0; font-size: 15px; font-weight: 600; text-align: center; }
.hs-row { display: flex; align-items: center; gap: 10px; color: var(--ink2); font-size: 13.5px; }
.hs-switch { position: relative; width: 38px; height: 22px; flex: 0 0 auto; padding: 0; border: 0; border-radius: 999px; background: var(--line2); cursor: pointer; transition: background .16s ease; }
.hs-switch i { position: absolute; top: 2px; left: 2px; width: 18px; height: 18px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgb(15 23 42 / 22%); transition: transform .16s ease; }
.hs-switch.on { background: var(--priority-low, #4d78db); }
.hs-switch.on i { transform: translateX(16px); }
.hs-foot { display: flex; justify-content: flex-end; gap: 10px; }
.hs-btn { min-width: 88px; height: 36px; border: 1px solid var(--line2); border-radius: 9px; background: transparent; color: var(--ink2); font: inherit; font-size: 13.5px; cursor: pointer; }
.hs-btn.is-primary { border-color: var(--priority-low, #4d78db); background: var(--priority-low, #4d78db); color: #fff; font-weight: 600; }
</style>
