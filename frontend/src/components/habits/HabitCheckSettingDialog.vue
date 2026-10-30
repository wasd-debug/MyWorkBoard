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
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  show: { type: Boolean, default: true }
})
const emit = defineEmits(['close', 'save'])

const value = ref(true)
watch(() => props.open, open => {
  if (open) value.value = props.show
}, { immediate: true })

function onKeydown(event) {
  if (props.open && event.key === 'Escape') emit('close')
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.hs-mask { position: fixed; z-index: 176; inset: 0; display: grid; place-items: center; padding: 20px; background: rgb(24 30 40 / 32%); }
.hs-sheet { position: relative; display: grid; gap: 20px; width: min(430px, calc(100vw - 24px)); padding: 22px 24px 18px; border-radius: 18px; background: var(--card); box-shadow: 0 26px 62px rgb(15 23 42 / 26%); }
.hs-dot { position: absolute; top: 14px; left: 14px; width: 10px; height: 10px; border-radius: 50%; background: #f2706a; }
.hs-sheet h2 { margin: 6px 0 8px; font-size: 17px; font-weight: 600; letter-spacing: -.01em; text-align: center; }
.hs-row { display: flex; align-items: center; gap: 14px; color: var(--ink); font-size: 15px; cursor: pointer; }
.hs-switch { position: relative; width: 46px; height: 26px; flex: 0 0 auto; padding: 0; border: 0; border-radius: 999px; background: color-mix(in srgb, var(--ink) 16%, transparent); cursor: pointer; transition: background .16s ease; }
.hs-switch i { position: absolute; top: 2px; left: 2px; width: 22px; height: 22px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgb(15 23 42 / 22%); transition: transform .16s ease; }
.hs-switch.on { background: #3d8fce; }
.hs-switch.on i { transform: translateX(20px); }
.hs-foot { display: flex; justify-content: flex-end; gap: 14px; padding-top: 4px; }
.hs-btn { min-width: 132px; height: 44px; border: 1px solid var(--line2); border-radius: 10px; background: var(--card); color: var(--ink); font: inherit; font-size: 14.5px; cursor: pointer; }
.hs-btn.is-primary { border-color: #3d8fce; background: #3d8fce; color: #fff; font-weight: 600; }
.hs-btn.is-primary:hover { background: #3480ba; }
</style>
