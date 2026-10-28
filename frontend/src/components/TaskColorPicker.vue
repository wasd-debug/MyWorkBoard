<template>
  <div ref="rootElement" class="task-color-picker" :class="{ 'is-inline': inline }" @keydown.esc.stop="closePopovers">
    <template v-if="inline">
      <div class="task-color-inline" role="group" aria-label="选择颜色">
        <div class="task-color-inline-swatches">
          <button
          v-for="color in inboxColors"
            :key="color.name"
            type="button"
            :aria-label="color.name"
            :aria-pressed="modelValue.toLowerCase() === color.value.toLowerCase()"
            :title="color.name"
            @click="selectColor(color.value)"
          ><span class="task-color-dot" :class="{ 'is-default': !color.value, 'is-selected': modelValue.toLowerCase() === color.value.toLowerCase() }" :style="{ backgroundColor: color.value || undefined }"><X v-if="!color.value" :size="12" /><Check v-else-if="modelValue.toLowerCase() === color.value.toLowerCase()" :size="12" /></span></button>
          <button ref="wheelButton" type="button" class="task-color-wheel" aria-label="更多颜色" :aria-expanded="open" @click.stop="togglePalette"><span></span></button>
        </div>
        <DialogRoot :open="open" @update:open="value => { if (!value) closePopovers(); }">
          <DialogPortal>
            <DialogOverlay class="task-color-overlay" />
            <DialogContent ref="palettePanel" class="task-color-palette is-detached" aria-label="更多颜色" @pointerdown.stop @click.stop>
            <header class="task-color-overlay-heading"><DialogTitle as="strong">选择颜色</DialogTitle><button type="button" aria-label="关闭颜色选择" @click="closePopovers"><X :size="18" /></button></header>
            <div class="task-color-group-list">
              <section v-for="group in colorGroups" :key="group.key" class="task-color-group">
                <strong>{{ group.label }}</strong>
                <div class="task-color-group-swatches">
                  <button
                    v-for="(color, index) in group.colors"
                    :key="`${group.key}-${index}`"
                    type="button"
                    :aria-label="color || '默认颜色'"
                    :aria-pressed="normalizedValue === color.toLowerCase()"
                    :title="color || '默认颜色'"
                    @click="selectColor(color)"
                  >
                    <span
                      :class="{ 'is-default': !color, 'is-selected': normalizedValue === color.toLowerCase() }"
                      :style="color ? { backgroundColor: color } : undefined"
                    >
                      <X v-if="!color" :size="12" />
                      <Check v-else-if="normalizedValue === color.toLowerCase()" :size="12" />
                    </span>
                  </button>
                </div>
              </section>
            </div>
            <section class="task-color-custom-section">
              <strong>自定义颜色</strong>
              <button ref="customTrigger" type="button" class="task-color-add" aria-label="添加自定义颜色" @click.stop="openCustom"><Plus :size="14" /></button>
            </section>
            </DialogContent>
          </DialogPortal>
          <DialogRoot :open="customOpen" @update:open="value => { if (!value) customOpen = false; }">
            <DialogPortal>
              <DialogOverlay class="task-color-overlay task-color-custom-overlay" />
              <DialogContent ref="customPanel" class="task-color-custom-editor is-detached" aria-label="自定义颜色" @pointerdown.stop @click.stop>
                <header class="task-color-overlay-heading"><DialogTitle as="strong">自定义颜色</DialogTitle><button type="button" aria-label="关闭自定义颜色" @click="closePopovers"><X :size="18" /></button></header>
                <input v-model="customHex" class="task-color-preview" type="color" aria-label="选择颜色" @input="syncRgbFromHex" />
                <div class="task-color-rgb">
                  <label v-for="(value, index) in customRgb" :key="index"><input type="number" min="0" max="255" :aria-label="`RGB ${['红','绿','蓝'][index]}`" :value="value" @input="updateRgb(index, $event.target.value)" /><span>{{ ['R', 'G', 'B'][index] }}</span></label>
                  <label class="task-color-hex"><input v-model="customHex" maxlength="7" aria-label="Hex" @input="syncRgbFromHex" /><span>Hex</span></label>
                </div>
                <div class="task-color-custom-actions"><button type="button" class="task-color-confirm" @click="confirmCustom">确定</button><button type="button" @click="closePopovers">取消</button></div>
              </DialogContent>
            </DialogPortal>
          </DialogRoot>
        </DialogRoot>
      </div>
    </template>
    <template v-else>
      <button type="button" class="task-color-trigger" :aria-label="`颜色：${selectedLabel}`" :aria-expanded="open" @click="open = !open">
        <span class="task-color-swatch" :class="{ 'is-default': !modelValue }" :style="swatchStyle"></span><span>{{ selectedLabel }}</span><ChevronDown :size="14" />
      </button>
      <div v-if="open" class="task-color-popover" role="dialog" aria-label="选择颜色" @pointerdown.stop @click.stop>
        <div class="task-color-popover-heading"><strong>{{ activeFamily ? `${activeFamily.name}色色阶` : '常用颜色' }}</strong><button v-if="activeFamily" type="button" @click="activeFamily = null">返回</button></div>
        <div v-if="!activeFamily" class="task-color-common" aria-label="常用颜色"><button v-for="color in commonTaskColors" :key="color.name" type="button" :aria-label="color.name" :aria-pressed="modelValue === color.value" :title="color.name" @click="selectColor(color.value)"><span class="task-color-dot" :class="{ 'is-default': !color.value }" :style="{ backgroundColor: color.value || undefined }"><X v-if="!color.value" :size="12" /><Check v-else-if="modelValue === color.value" :size="13" /></span></button></div>
        <div v-if="!activeFamily" class="task-color-family-list" aria-label="按色系选择"><button v-for="family in taskColorFamilies" :key="family.name" type="button" :aria-label="`${family.name}色系`" @click="activeFamily = family"><span v-for="shade in family.shades.slice(2, 6)" :key="shade" :style="{ backgroundColor: shade }"></span></button></div>
        <div v-else class="task-color-shades" :aria-label="`${activeFamily.name}色色阶`"><button v-for="shade in activeFamily.shades" :key="shade" type="button" :aria-label="shade" :aria-pressed="modelValue === shade" @click="selectColor(shade)"><span :style="{ backgroundColor: shade }"><Check v-if="modelValue === shade" :size="13" /></span></button></div>
        <label class="task-color-custom"><span>自定义颜色</span><input type="color" :value="customColor" aria-label="自定义颜色" @input="selectColor($event.target.value)" /></label>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { Check, ChevronDown, Plus, X } from "lucide-vue-next";
import { DialogContent, DialogOverlay, DialogPortal, DialogRoot, DialogTitle } from "reka-ui";
import { commonTaskColors, isHexColor, taskColorFamilies } from "../views/taskColors.js";
import { COLOR_GROUPS } from "../views/calendar/calendarColors.js";

const props = defineProps({ modelValue: { type: String, default: "" }, inline: { type: Boolean, default: false } });
const emit = defineEmits(["update:modelValue"]);
const inboxColors = [
  commonTaskColors[0],
  commonTaskColors[1],
  commonTaskColors[2],
  commonTaskColors[3],
  { name: "黄绿色", value: "#d5dc39" },
  commonTaskColors[4],
  commonTaskColors[6],
  commonTaskColors[7],
];
const open = ref(false);
const customOpen = ref(false);
const activeFamily = ref(null);
const rootElement = ref(null);
const wheelButton = ref(null);
const customTrigger = ref(null);
const palettePanel = ref(null);
const customPanel = ref(null);
const customColor = ref(isHexColor(props.modelValue) ? props.modelValue : "#2563eb");
const customHex = ref("#000000");
const customRgb = ref([0, 0, 0]);
const selectedLabel = computed(() => commonTaskColors.find(color => color.value.toLowerCase() === props.modelValue.toLowerCase())?.name || props.modelValue || "默认");
const swatchStyle = computed(() => props.modelValue ? { backgroundColor: props.modelValue } : {});
// 色板与日历保持同一套分组（推荐 / 马卡龙 / 莫兰迪 / 洛可可 / 经典 / 孟菲斯）
const colorGroups = COLOR_GROUPS;
const normalizedValue = computed(() => String(props.modelValue || "").toLowerCase());

watch(() => props.modelValue, value => { if (isHexColor(value)) customColor.value = value; });

onMounted(() => {
  document.addEventListener("pointerdown", handleOutsidePointerDown);
  window.addEventListener("keydown", handleWindowKeydown);
});
onBeforeUnmount(() => {
  document.removeEventListener("pointerdown", handleOutsidePointerDown);
  window.removeEventListener("keydown", handleWindowKeydown);
});

function togglePalette() {
  open.value = !open.value;
  customOpen.value = false;
}
function closePopovers() { open.value = false; customOpen.value = false; }
function selectColor(value) { emit("update:modelValue", value); closePopovers(); activeFamily.value = null; }
function openCustom() {
  customHex.value = isHexColor(props.modelValue) ? props.modelValue.toUpperCase() : "#000000";
  syncRgbFromHex();
  customOpen.value = true;
}
function handleOutsidePointerDown(event) {
  const target = event.target;
  if (rootElement.value?.contains(target) || palettePanel.value?.$el?.contains(target) || customPanel.value?.$el?.contains(target)) return;
  closePopovers();
}
function handleWindowKeydown(event) { if (event.key === "Escape") closePopovers(); }
function syncRgbFromHex() {
  if (!isHexColor(customHex.value)) return;
  customHex.value = customHex.value.toUpperCase();
  customRgb.value = [1, 3, 5].map(index => parseInt(customHex.value.slice(index, index + 2), 16));
}
function updateRgb(index, raw) {
  const next = [...customRgb.value];
  next[index] = Math.max(0, Math.min(255, Number(raw) || 0));
  customRgb.value = next;
  customHex.value = `#${next.map(value => value.toString(16).padStart(2, "0")).join("")}`.toUpperCase();
}
function confirmCustom() { if (isHexColor(customHex.value)) selectColor(customHex.value.toLowerCase()); }
</script>

<style scoped>
.task-color-picker { position: relative; width: fit-content; max-width: 100%; }
.task-color-picker.is-inline { width: 100%; }
.task-color-inline { position: relative; width: 100%; }
.task-color-inline-swatches { display: flex; width: 100%; max-width: 100%; align-items: center; gap: 7px; overflow: visible; flex-wrap: wrap; }
.task-color-inline-swatches > button { display: grid; flex: none; place-items: center; width: 24px; height: 24px; padding: 0; border: 0; border-radius: 50%; background: transparent; cursor: pointer; }
.task-color-dot { display: grid; place-items: center; width: 20px; height: 20px; border: 1px solid color-mix(in srgb, var(--ink) 16%, transparent); border-radius: 50%; color: white; }
.task-color-dot.is-default { background: transparent; color: var(--muted); }
.task-color-wheel span { background: linear-gradient(135deg, #ef5350 0 24%, #f2c94c 24% 48%, #48a868 48% 72%, #4d83d1 72%); }
.task-color-dot.is-selected { outline: 2px solid var(--accent); outline-offset: 2px; }
.task-color-wheel span { display: block; width: 20px; height: 20px; border-radius: 50%; }
.task-color-overlay { box-sizing: border-box; position: fixed; z-index: 230; inset: 0; display: grid; place-items: center; padding: 16px; background: rgb(15 18 22 / 38%); }
.task-color-palette { box-sizing: border-box; position: fixed; z-index: 231; top: 50%; left: 50%; display: flex; flex-direction: column; gap: 12px; width: min(390px, calc(100vw - 32px)); min-height: 0; max-height: min(760px, calc(100dvh - 32px)); padding: 14px; transform: translate(-50%, -50%); border: 1px solid var(--line); border-radius: 8px; background: var(--card); box-shadow: 0 18px 48px rgb(0 0 0 / 28%); color: var(--ink); }
.task-color-overlay-heading { position: sticky; z-index: 1; top: -14px; display: flex; align-items: center; justify-content: space-between; min-height: 42px; margin: -14px -14px 0; padding: 0 14px; border-bottom: 1px solid var(--line); background: var(--card); color: var(--ink); }
.task-color-overlay-heading strong { font-size: 14px; font-weight: 650; }
.task-color-overlay-heading button { display: grid; place-items: center; width: 30px; height: 30px; padding: 0; border: 1px solid var(--line); border-radius: 5px; background: transparent; color: var(--ink2); cursor: pointer; }
.task-color-overlay-heading button:hover { background: var(--paper); color: var(--ink); }
.task-color-family-list-scroll { display: grid; flex: 1 1 auto; align-content: start; gap: 12px; min-height: 0; overflow-x: hidden; overflow-y: auto; overscroll-behavior: contain; }
.task-color-group-list { display: grid; flex: 1 1 auto; align-content: start; gap: 14px; min-height: 0; overflow-x: hidden; overflow-y: auto; overscroll-behavior: contain; }
.task-color-group { display: grid; gap: 8px; }
.task-color-group strong { color: var(--ink2); font-size: 12px; font-weight: 600; }
.task-color-group-swatches { display: flex; align-items: center; gap: 9px; flex-wrap: nowrap; }
.task-color-group-swatches button { display: grid; flex: none; place-items: center; width: 26px; height: 26px; padding: 0; border: 0; border-radius: 50%; background: transparent; cursor: pointer; }
.task-color-group-swatches button:hover { background: var(--paper); }
.task-color-group-swatches button span { display: grid; place-items: center; width: 22px; height: 22px; border-radius: 50%; color: #fff; }
.task-color-group-swatches button span.is-default { border: 1.5px dashed color-mix(in srgb, var(--ink) 34%, transparent); color: var(--muted); }
.task-color-group-swatches button span.is-selected { outline: 2px solid var(--accent); outline-offset: 2px; }
.task-color-family { display: grid; gap: 5px; }
.task-color-family strong, .task-color-custom-section strong { font-size: 11px; font-weight: 600; }
.task-color-family > div { display: grid; grid-template-columns: repeat(8, 1fr); gap: 3px; }
.task-color-family button { display: grid; place-items: center; width: 23px; height: 23px; padding: 0; border: 0; border-radius: 50%; background: transparent; cursor: pointer; }
.task-color-family button:hover { background: var(--paper); }
.task-color-family button span { display: grid; place-items: center; width: 18px; height: 18px; border-radius: 50%; color: white; }
.task-color-custom-section { display: grid; flex: none; grid-template-columns: 1fr auto; align-items: center; gap: 6px; padding-top: 10px; border-top: 1px solid var(--line); background: var(--card); }
.task-color-add { display: grid; place-items: center; width: 22px; height: 22px; padding: 0; border: 1px solid var(--line); border-radius: 50%; background: var(--paper); color: var(--ink2); cursor: pointer; }
.task-color-custom-overlay { z-index: 232; background: rgb(15 18 22 / 22%); }
.task-color-custom-editor { box-sizing: border-box; position: fixed; z-index: 233; top: 50%; left: 50%; display: grid; gap: 12px; width: min(340px, calc(100vw - 32px)); padding: 14px; transform: translate(-50%, -50%); border: 1px solid var(--line); border-radius: 8px; background: var(--card); box-shadow: 0 18px 48px rgb(0 0 0 / 28%); color: var(--ink); }
.task-color-custom-editor .task-color-overlay-heading { margin: -14px -14px 0; }
.task-color-preview { width: 100%; height: 170px; padding: 0; border: 0; background: transparent; cursor: pointer; }
.task-color-rgb { display: grid; grid-template-columns: repeat(4, 1fr); gap: 5px; }
.task-color-rgb label { display: grid; gap: 3px; }
.task-color-rgb input { box-sizing: border-box; width: 100%; min-width: 0; height: 25px; padding: 2px 4px; border: 1px solid var(--line); border-radius: 5px; background: var(--paper); color: var(--ink); text-align: center; font: inherit; font-size: 11px; }
.task-color-rgb label span { color: var(--muted); text-align: center; font-size: 10px; }
.task-color-rgb .task-color-hex { grid-column: span 1; }
.task-color-custom-actions { display: flex; justify-content: flex-end; gap: 7px; }
.task-color-custom-actions button { min-height: 28px; padding: 0 10px; border: 1px solid var(--line); border-radius: 5px; background: transparent; color: var(--ink2); font: inherit; font-size: 11px; cursor: pointer; }
.task-color-custom-actions .task-color-confirm { border-color: var(--accent); background: var(--accent); color: white; }
.task-color-trigger { display: inline-flex; align-items: center; gap: 8px; min-height: 38px; padding: 0 10px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); color: var(--ink2); cursor: pointer; }
.task-color-swatch { display: grid; flex: none; place-items: center; width: 22px; height: 22px; border: 1px solid color-mix(in srgb, var(--ink) 18%, transparent); border-radius: 50%; }
.task-color-swatch.is-default { position: relative; background: transparent; }
.task-color-swatch.is-default::after { content: "×"; color: var(--muted); font-size: 17px; line-height: 1; }
.task-color-popover { position: absolute; z-index: 40; top: calc(100% + 6px); left: 0; display: grid; gap: 11px; width: min(280px, calc(100vw - 40px)); padding: 12px; border: 1px solid var(--line); border-radius: 8px; background: var(--card); box-shadow: 0 12px 32px rgb(0 0 0 / 18%); }
.task-color-popover-heading { display: flex; align-items: center; justify-content: space-between; color: var(--ink); font-size: 12px; }
.task-color-popover-heading button { padding: 3px 6px; border: 0; background: transparent; color: var(--accent); font: inherit; cursor: pointer; }
.task-color-common, .task-color-family-list { display: grid; grid-template-columns: repeat(9, minmax(0, 1fr)); gap: 5px; }
.task-color-common button, .task-color-family-list button, .task-color-shades button { display: grid; place-items: center; min-width: 0; padding: 2px; border: 0; border-radius: 5px; background: transparent; cursor: pointer; }
.task-color-common button:hover, .task-color-family-list button:hover, .task-color-shades button:hover { background: var(--paper); }
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
