<template>
  <div class="task-color-picker" @keydown.esc.stop="open = false">
    <button
      type="button"
      class="task-color-trigger"
      :aria-label="`颜色：${selectedLabel}`"
      :aria-expanded="open"
      @click="open = !open"
    >
      <span class="task-color-swatch" :class="{ 'is-default': !modelValue }" :style="swatchStyle"></span>
      <span>{{ selectedLabel }}</span>
      <ChevronDown :size="14" />
    </button>
    <div v-if="open" class="task-color-popover" role="dialog" aria-label="选择颜色" @click.stop>
      <div class="task-color-popover-heading">
        <strong>{{ activeFamily ? `${activeFamily.name}色色阶` : '常用颜色' }}</strong>
        <button v-if="activeFamily" type="button" aria-label="返回常用颜色" @click="activeFamily = null">返回</button>
      </div>
      <div v-if="!activeFamily" class="task-color-common" aria-label="常用颜色">
        <button
          v-for="color in commonTaskColors"
          :key="color.name"
          type="button"
          :aria-label="color.name"
          :aria-pressed="modelValue === color.value"
          :title="color.name"
          @click="selectColor(color.value)"
        ><span class="task-color-dot" :class="{ 'is-default': !color.value }" :style="{ backgroundColor: color.value || undefined }"><Check v-if="modelValue === color.value" :size="13" /></span></button>
      </div>
      <div v-if="!activeFamily" class="task-color-family-list" aria-label="按色系选择">
        <button v-for="family in taskColorFamilies" :key="family.name" type="button" :aria-label="`${family.name}色系`" :title="`${family.name}色系`" @click="activeFamily = family">
          <span v-for="shade in family.shades.slice(2, 6)" :key="shade" :style="{ backgroundColor: shade }"></span>
        </button>
      </div>
      <div v-else class="task-color-shades" :aria-label="`${activeFamily.name}色色阶`">
        <button v-for="shade in activeFamily.shades" :key="shade" type="button" :aria-label="shade" :aria-pressed="modelValue === shade" @click="selectColor(shade)">
          <span :style="{ backgroundColor: shade }"><Check v-if="modelValue === shade" :size="13" /></span>
        </button>
      </div>
      <label class="task-color-custom">
        <span>自定义颜色</span>
        <input type="color" :value="customColor" aria-label="自定义颜色" @input="selectColor($event.target.value)" />
      </label>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { Check, ChevronDown } from "lucide-vue-next";
import { commonTaskColors, isHexColor, taskColorFamilies } from "../views/taskColors.js";

const props = defineProps({ modelValue: { type: String, default: "" } });
const emit = defineEmits(["update:modelValue"]);
const open = ref(false);
const activeFamily = ref(null);
const customColor = ref(isHexColor(props.modelValue) ? props.modelValue : "#2563eb");
const selectedLabel = computed(() => commonTaskColors.find(color => color.value.toLowerCase() === props.modelValue.toLowerCase())?.name || props.modelValue || "默认");
const swatchStyle = computed(() => props.modelValue ? { backgroundColor: props.modelValue } : {});

watch(() => props.modelValue, value => { if (isHexColor(value)) customColor.value = value; });

function selectColor(value) {
  emit("update:modelValue", value);
  open.value = false;
  activeFamily.value = null;
}
</script>

<style scoped>
.task-color-picker { position: relative; width: fit-content; max-width: 100%; }
.task-color-trigger { display: inline-flex; align-items: center; gap: 8px; min-height: 38px; padding: 0 10px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); color: var(--ink2); cursor: pointer; }
.task-color-swatch, .task-color-dot { display: grid; flex: none; place-items: center; width: 22px; height: 22px; border: 1px solid color-mix(in srgb, var(--ink) 18%, transparent); border-radius: 50%; color: white; }
.task-color-swatch.is-default, .task-color-dot.is-default { background: linear-gradient(135deg, #ef5350 0 24%, #f2c94c 24% 48%, #48a868 48% 72%, #4d83d1 72%); }
.task-color-popover { position: absolute; z-index: 40; top: calc(100% + 6px); left: 0; display: grid; gap: 11px; width: min(280px, calc(100vw - 40px)); padding: 12px; border: 1px solid var(--line); border-radius: 8px; background: var(--card); box-shadow: 0 12px 32px rgb(0 0 0 / 18%); }
.task-color-popover-heading { display: flex; align-items: center; justify-content: space-between; color: var(--ink); font-size: 12px; }
.task-color-popover-heading button { padding: 3px 6px; border: 0; background: transparent; color: var(--accent); font: inherit; cursor: pointer; }
.task-color-common { display: grid; grid-template-columns: repeat(9, minmax(0, 1fr)); gap: 5px; }
.task-color-common button, .task-color-family-list button, .task-color-shades button { display: grid; place-items: center; min-width: 0; padding: 2px; border: 0; border-radius: 5px; background: transparent; cursor: pointer; }
.task-color-common button:hover, .task-color-family-list button:hover, .task-color-shades button:hover { background: var(--paper); }
.task-color-dot { width: 23px; height: 23px; }
.task-color-family-list { display: grid; grid-template-columns: repeat(9, minmax(0, 1fr)); gap: 4px; }
.task-color-family-list button { grid-template-columns: repeat(2, 1fr); overflow: hidden; width: 24px; height: 30px; padding: 3px; }
.task-color-family-list button span { width: 8px; height: 10px; }
.task-color-family-list button span:first-child { border-radius: 4px 0 0 4px; }
.task-color-family-list button span:last-child { border-radius: 0 4px 4px 0; }
.task-color-shades { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 4px; }
.task-color-shades button { min-height: 34px; }
.task-color-shades button span { display: grid; place-items: center; width: 27px; height: 27px; border: 1px solid color-mix(in srgb, var(--ink) 10%, transparent); border-radius: 50%; color: white; }
.task-color-custom { display: flex !important; align-items: center; justify-content: space-between; gap: 8px !important; padding-top: 9px; border-top: 1px solid var(--line); color: var(--ink2) !important; font-size: 12px !important; }
.task-color-custom input { width: 34px !important; height: 28px !important; min-height: 28px !important; padding: 2px !important; border: 1px solid var(--line) !important; border-radius: 5px; }
</style>
