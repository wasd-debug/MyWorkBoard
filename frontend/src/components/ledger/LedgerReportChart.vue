<template>
  <div class="ledger-report-chart-wrap">
    <div ref="chartRef" class="ledger-report-chart" :style="{ height }" :aria-label="ariaLabel" role="img"></div>
    <details v-if="tableRows.length" class="chart-data-table">
      <summary>查看图表数据</summary>
      <div class="chart-data-scroll"><table><caption>{{ ariaLabel }}</caption><thead><tr><th>项目</th><th v-for="name in seriesNames" :key="name">{{ name }}</th></tr></thead><tbody><tr v-for="row in tableRows" :key="row.label"><td>{{ row.label }}</td><td v-for="(value, index) in row.values" :key="index">{{ value }}</td></tr></tbody></table></div>
    </details>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TooltipComponent
} from 'echarts/components'
import * as echarts from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer
])

const props = defineProps({
  option: { type: Object, required: true },
  ariaLabel: { type: String, default: '账本报表图表' },
  height: { type: String, default: '280px' }
})
const emit = defineEmits(['chart-click'])

const chartRef = ref(null)
let chart
let resizeObserver
let themeObserver

const seriesList = computed(() => Array.isArray(props.option?.series) ? props.option.series : [])
const seriesNames = computed(() => seriesList.value.map((series, index) => series.name || `系列 ${index + 1}`))
const tableRows = computed(() => {
  const axis = Array.isArray(props.option?.xAxis) ? props.option.xAxis[0] : props.option?.xAxis
  const labels = Array.isArray(axis?.data) ? axis.data : []
  if (!labels.length || !seriesList.value.length) return []
  return labels.map((label, index) => ({
    label,
    values: seriesList.value.map(series => {
      const raw = Array.isArray(series.data) ? series.data[index] : null
      const value = raw && typeof raw === 'object' ? raw.value : raw
      return Array.isArray(value) ? value.join(' / ') : (value ?? '—')
    })
  }))
})

function semanticColors() {
  const style = getComputedStyle(document.documentElement)
  return {
    '$income': style.getPropertyValue('--down').trim() || '#b42318',
    '$expense': style.getPropertyValue('--up').trim() || '#067647',
    '$accent': style.getPropertyValue('--accent').trim() || '#0f5132',
    '$line': style.getPropertyValue('--line').trim() || '#e2ded4',
    '$text': style.getPropertyValue('--ink2').trim() || '#49505c',
    '$muted': style.getPropertyValue('--muted').trim() || '#8b909c',
    '$card': style.getPropertyValue('--card').trim() || '#fffefa'
  }
}

function resolveTokens(value, colors) {
  if (typeof value === 'string') return colors[value] || value
  if (Array.isArray(value)) return value.map(item => resolveTokens(item, colors))
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, resolveTokens(item, colors)]))
  }
  return value
}

async function render() {
  await nextTick()
  if (!chartRef.value?.clientWidth || !chartRef.value?.clientHeight) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
    chart.on('click', params => emit('chart-click', params))
  }
  const resolved = resolveTokens(props.option, semanticColors())
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  chart.setOption({
    animation: true,
    animationDuration: reducedMotion ? 0 : 520,
    animationDurationUpdate: reducedMotion ? 0 : 320,
    animationEasing: 'cubicOut',
    animationEasingUpdate: 'cubicOut',
    ...resolved
  }, true)
  chart.resize()
}

watch(() => props.option, render, { deep: true })

onMounted(() => {
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(chartRef.value)
  themeObserver = new MutationObserver(render)
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
  render()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  themeObserver?.disconnect()
  chart?.dispose()
})
</script>

<style scoped>
.ledger-report-chart {
  width: 100%;
  min-width: 0;
}
.ledger-report-chart-wrap { min-width: 0 }
</style>
