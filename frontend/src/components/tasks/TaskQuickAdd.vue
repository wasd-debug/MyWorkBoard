<template>
  <div v-if="open" class="cal-quick" :style="style" role="dialog" :aria-label="ariaLabel" @click.stop>
    <header>
      <span class="cal-quick-date"><CalendarDays :size="14" />{{ dateLabel }}</span>
      <button
        type="button"
        class="cal-quick-flag"
        :class="{ on: priority === 'HIGH' }"
        :aria-pressed="priority === 'HIGH'"
        aria-label="设为高优先级"
        title="高优先级"
        @click="$emit('update:priority', priority === 'HIGH' ? 'NONE' : 'HIGH')"
      >
        <Flag :size="14" />
      </button>
    </header>
    <textarea
      ref="inputEl"
      class="cal-quick-input"
      :value="title"
      rows="3"
      :placeholder="placeholder"
      @input="$emit('update:title', $event.target.value)"
      @keydown.enter.exact.prevent="$emit('save')"
      @keydown.esc.prevent="$emit('close')"
    ></textarea>
    <footer>
      <div class="cal-quick-list">
        <FolderClosed :size="14" />
        <select :value="listId" aria-label="清单" @change="$emit('update:listId', $event.target.value)">
          <option v-for="list in lists" :key="list.publicId" :value="list.publicId">{{ list.name }}</option>
        </select>
      </div>
      <div class="cal-quick-actions">
        <button type="button" class="cal-quick-cancel" @click="$emit('close')">取消</button>
        <button type="button" class="cal-quick-save" :disabled="!title.trim() || saving" @click="$emit('save')">{{ saving ? '保存中…' : '保存' }}</button>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'
import { CalendarDays, Flag, FolderClosed } from 'lucide-vue-next'

const props = defineProps({
  open: { type: Boolean, default: false },
  dateLabel: { type: String, default: '' },
  title: { type: String, default: '' },
  priority: { type: String, default: 'NONE' },
  listId: { type: String, default: '' },
  lists: { type: Array, default: () => [] },
  saving: { type: Boolean, default: false },
  placeholder: { type: String, default: '准备做什么？' },
  ariaLabel: { type: String, default: '新增任务' },
  style: { type: [Object, String], default: () => ({}) }
})

defineEmits(['update:title', 'update:priority', 'update:listId', 'save', 'close'])

const inputEl = ref(null)

// 每次打开自动聚焦到输入框，和日历里点空白新建的体感一致
watch(() => props.open, async value => {
  if (!value) return
  await nextTick()
  inputEl.value?.focus()
})
</script>

<style scoped>
.cal-quick {
  box-sizing: border-box;
  display: flex;
  width: min(420px, calc(100vw - 32px));
  flex-direction: column;
  padding: 14px 14px 10px;
  border: 1px solid var(--line2);
  border-radius: 12px;
  background: var(--card);
  box-shadow: 0 22px 52px rgba(15, 23, 42, .22);
  color: var(--ink);
}
.cal-quick header { display: flex; align-items: center; justify-content: space-between; }
.cal-quick-date { display: inline-flex; align-items: center; gap: 6px; color: var(--accent); font-size: 12.5px; font-weight: 600; }
.cal-quick-flag { display: grid; place-items: center; width: 26px; height: 26px; border: 0; border-radius: 6px; background: transparent; color: var(--muted); cursor: pointer; }
.cal-quick-flag:hover { background: var(--paper); }
.cal-quick-flag.on { color: var(--priority-high, #e34d59); }
.cal-quick-input { width: 100%; min-height: 74px; margin: 10px 0 12px; padding: 0; border: 0; background: transparent; color: var(--ink); font: inherit; font-size: 17px; line-height: 1.5; outline: none; box-shadow: none; resize: none; }
.cal-quick-input::placeholder { color: var(--muted); }
.cal-quick footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.cal-quick-list { display: inline-flex; align-items: center; gap: 4px; color: var(--ink2); font-size: 12px; }
.cal-quick-list select { max-width: 132px; border: 0; background: transparent; color: var(--ink2); font-size: 12px; outline: none; }
.cal-quick-actions { display: flex; gap: 6px; }
.cal-quick-actions button { height: 28px; padding: 0 12px; border: 1px solid var(--line); border-radius: 6px; background: transparent; color: var(--ink2); font-size: 12px; cursor: pointer; }
.cal-quick-save { border-color: var(--accent) !important; background: var(--accent) !important; color: #fff !important; }
.cal-quick-save:disabled { opacity: .5; }
</style>
