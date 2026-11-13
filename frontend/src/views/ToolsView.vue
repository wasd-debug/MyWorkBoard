<template>
  <main class="tools">
    <header class="tools-head">
      <div>
        <p class="tools-eyebrow">TOOLBOX</p>
        <h1>工具箱</h1>
        <p class="tools-sub">常用计算与记录。计算类工具在你的浏览器里完成，不会上传数据。</p>
      </div>
    </header>

    <div class="tools-body">
      <nav class="tools-rail" aria-label="工具列表">
        <router-link
          v-for="tool in tools"
          :key="tool.key"
          :to="tool.to"
          class="tools-rail-item"
          :class="{ on: tool.key === activeTool.key }"
          :aria-current="tool.key === activeTool.key ? 'page' : undefined"
        >
          <component :is="tool.icon" :size="17" aria-hidden="true" />
          <span><b>{{ tool.label }}</b><small>{{ tool.desc }}</small></span>
        </router-link>
      </nav>

      <section class="tools-panel" :aria-label="activeTool.label">
        <component :is="activeTool.panel" />
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Calculator, CalendarRange, CigaretteOff, ReceiptText } from 'lucide-vue-next'
import MortgagePanel from '../components/tools/MortgagePanel.vue'
import TaxPanel from '../components/tools/TaxPanel.vue'
import DatePanel from '../components/tools/DatePanel.vue'
import QuitSmokingPanel from '../components/tools/QuitSmokingPanel.vue'

const route = useRoute()

const tools = [
  { key: 'mortgage', label: '房贷', desc: '月供、总利息、提前还款', to: '/tools/mortgage', icon: Calculator, panel: MortgagePanel },
  { key: 'tax', label: '个税', desc: '累计预扣、年终奖对比', to: '/tools/tax', icon: ReceiptText, panel: TaxPanel },
  { key: 'date', label: '日期', desc: '相差天数、推算、工作日', to: '/tools/date', icon: CalendarRange, panel: DatePanel },
  { key: 'quit', label: '戒烟', desc: '恢复倒计时与焦油记录', to: '/tools/quit', icon: CigaretteOff, panel: QuitSmokingPanel }
]

const activeTool = computed(() => tools.find(tool => tool.key === route.params.tool) || tools[0])
</script>

<style scoped>
.tools {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  gap: 18px;
  padding: 22px 30px 48px;
  color: var(--ink);
}
.tools-head h1 { margin: 2px 0 6px; font-size: clamp(24px, 2.4vw, 32px); letter-spacing: -.03em; }
.tools-eyebrow { margin: 0; color: var(--accent); font-size: 11px; font-weight: 700; letter-spacing: .18em; }
.tools-sub { margin: 0; max-width: 560px; color: var(--muted); font-size: 13px; line-height: 1.6; }

.tools-body { display: grid; grid-template-columns: 236px minmax(0, 1fr); gap: 18px; align-items: start; }

.tools-rail { display: flex; flex-direction: column; gap: 4px; position: sticky; top: 12px; }
.tools-rail-item {
  display: flex; align-items: center; gap: 10px; padding: 10px 12px;
  border: 1px solid transparent; border-radius: 12px;
  color: var(--ink); text-decoration: none; transition: background .15s ease, border-color .15s ease;
}
.tools-rail-item span { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.tools-rail-item b { font-size: 13.5px; font-weight: 640; }
.tools-rail-item small { color: var(--muted); font-size: 11px; line-height: 1.3; }
.tools-rail-item:hover { background: color-mix(in srgb, var(--ink) 4%, transparent); }
.tools-rail-item.on {
  border-color: color-mix(in srgb, var(--accent) 38%, var(--line));
  background: color-mix(in srgb, var(--accent) 10%, transparent);
}
.tools-rail-item.on svg { color: var(--accent); }

.tools-panel { min-width: 0; }

/* 面板共享样式：面板组件只写语义类名，视觉在这里统一，避免五份复制。 */
.tools-panel :deep(.tool-card) {
  display: grid; gap: 14px; padding: 18px;
  border: 1px solid var(--line); border-radius: 16px; background: var(--card);
}
.tools-panel :deep(.tool-card + .tool-card) { margin-top: 14px; }
.tools-panel :deep(.tool-card > header) { display: flex; flex-wrap: wrap; align-items: baseline; gap: 8px; }
.tools-panel :deep(.tool-card h2) { margin: 0; font-size: 15px; font-weight: 680; }
.tools-panel :deep(.tool-card header small) { color: var(--muted); font-size: 11.5px; }

.tools-panel :deep(.tool-grid) { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; }
.tools-panel :deep(.tool-field) { display: flex; min-width: 0; flex-direction: column; gap: 5px; }
.tools-panel :deep(.tool-field > span) { color: var(--muted); font-size: 11.5px; }
.tools-panel :deep(.tool-field input),
.tools-panel :deep(.tool-field select) {
  width: 100%; min-width: 0; padding: 8px 10px;
  border: 1px solid var(--line); border-radius: 9px;
  background: var(--paper); color: var(--ink); font-size: 13px;
}
.tools-panel :deep(.tool-field input:focus),
.tools-panel :deep(.tool-field select:focus) { outline: 2px solid color-mix(in srgb, var(--accent) 45%, transparent); outline-offset: 1px; }

.tools-panel :deep(.tool-segmented) { display: inline-flex; flex-wrap: wrap; gap: 4px; padding: 3px; border: 1px solid var(--line); border-radius: 10px; background: var(--paper); }
.tools-panel :deep(.tool-segmented button) {
  min-width: 0; padding: 6px 11px; border: 0; border-radius: 7px;
  background: transparent; color: var(--muted); font-size: 12px; cursor: pointer;
}
.tools-panel :deep(.tool-segmented button.on) { background: var(--card); color: var(--ink); font-weight: 640; box-shadow: 0 1px 2px rgba(0,0,0,.08); }

.tools-panel :deep(.tool-stats) { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 10px; }
.tools-panel :deep(.tool-stat) { display: grid; gap: 3px; padding: 10px 12px; border-radius: 11px; background: color-mix(in srgb, var(--ink) 4%, transparent); }
.tools-panel :deep(.tool-stat b) { font-size: 17px; font-weight: 700; font-variant-numeric: tabular-nums; }
.tools-panel :deep(.tool-stat span) { color: var(--muted); font-size: 11px; }
.tools-panel :deep(.tool-stat.is-accent b) { color: var(--accent); }

.tools-panel :deep(.tool-table-wrap) { overflow-x: auto; }
.tools-panel :deep(table.tool-table) { width: 100%; border-collapse: collapse; font-size: 12px; font-variant-numeric: tabular-nums; }
.tools-panel :deep(table.tool-table th),
.tools-panel :deep(table.tool-table td) { padding: 6px 8px; border-bottom: 1px solid var(--line); text-align: right; white-space: nowrap; }
.tools-panel :deep(table.tool-table th:first-child),
.tools-panel :deep(table.tool-table td:first-child) { text-align: left; }
.tools-panel :deep(table.tool-table th) { color: var(--muted); font-weight: 600; }

.tools-panel :deep(.tool-disclaimer) { margin: 8px 0 0; color: var(--muted); font-size: 11px; line-height: 1.6; }
.tools-panel :deep(.tool-error) { margin: 0; color: #c2410c; font-size: 12px; }
.tools-panel :deep(.tool-hint) { margin: 0; color: var(--muted); font-size: 11.5px; line-height: 1.6; }
.tools-panel :deep(.tool-row) { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.tools-panel :deep(.tool-btn) {
  min-width: 0; padding: 7px 13px; border: 1px solid var(--line); border-radius: 9px;
  background: var(--card); color: var(--ink); font-size: 12.5px; cursor: pointer;
}
.tools-panel :deep(.tool-btn.is-primary) { border-color: transparent; background: var(--accent); color: var(--paper); font-weight: 640; }
.tools-panel :deep(.tool-btn:disabled) { opacity: .5; cursor: not-allowed; }

@media (max-width: 900px) {
  .tools { padding: 16px 16px 96px; }
  .tools-body { grid-template-columns: minmax(0, 1fr); gap: 12px; }
  /* 五个工具平分一行：390px 下正好放下，更窄时整体缩小而不是出现横向滚动条 */
  .tools-rail {
    position: static; display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 6px; overflow: visible;
  }
  .tools-rail-item { min-width: 0; padding: 8px 6px; gap: 5px; justify-content: center; border-color: var(--line); }
  .tools-rail-item span { min-width: 0; }
  .tools-rail-item b { font-size: 12.5px; white-space: nowrap; }
  .tools-rail-item svg { width: 15px; height: 15px; flex: 0 0 auto; }
  .tools-rail-item small { display: none; }
}
@media (max-width: 360px) {
  .tools-rail-item { flex-direction: column; gap: 3px; padding: 6px 2px; }
  .tools-rail-item b { font-size: 11.5px; }
}
</style>
