<template>
  <section>
    <div class="page-heading">
      <div class="label">MODULE / SYSTEM.CONFIG</div>
      <h1>设置 <span style="font-size:13px;color:var(--dim);font-weight:500">// SETTINGS</span></h1>
    </div>

    <div class="card set-group appearance-group">
      <h3>外观</h3>
      <div class="row">
        <div class="lbl">主题强调色<small>用于选中态、按钮、图表与悬浮边缘</small></div>
        <div class="ctl accent-options" aria-label="选择主题强调色">
          <button v-for="item in accents" :key="item.key" class="accent-swatch" :class="{ active: store.accent === item.key }" :style="{ '--swatch': item.color }" type="button" :aria-label="item.label" :title="item.label" @click="store.setAccent(item.key)"></button>
        </div>
      </div>
      <div class="row">
        <div class="lbl">暗夜模式<small>使用深色纸张与低反光悬浮卡片</small></div>
        <div class="ctl"><Toggle :model-value="store.theme === 'dark'" aria-label="暗夜模式" @update:model-value="store.toggleTheme()" /></div>
      </div>
    </div>

    <div class="card set-group">
      <h3>标准工作时间</h3>
      <div class="row">
        <div class="lbl">标准上班时间<small>每日开始计时的时刻</small></div>
        <div class="ctl"><Input type="time" :model-value="settings.workStart" @change="value => set('workStart', value || DEFAULTS.workStart)" /></div>
      </div>
      <div class="row">
        <div class="lbl">标准下班时间<small>每日结束计时的时刻</small></div>
        <div class="ctl"><Input type="time" :model-value="settings.workEnd" @change="value => set('workEnd', value || DEFAULTS.workEnd)" /></div>
      </div>
      <div class="row">
        <div class="lbl">午休等扣除时长<small>不计入工时的分钟数</small></div>
        <div class="ctl"><Input type="number" min="0" max="240" step="5" :model-value="settings.lunchMin" @change="value => setNumber('lunchMin', value, 0)" /></div>
      </div>
    </div>

    <div class="card set-group">
      <h3>排班设置</h3>
      <div class="row">
        <div class="lbl">自动获取法定工作日<small>按节假日调休自动计算当月排班天数</small></div>
        <div class="ctl"><Toggle :model-value="settings.autoDays" @update:model-value="value => set('autoDays', value)" /></div>
      </div>
      <div class="row">
        <div class="lbl">每月排班天数<small>{{ settings.autoDays ? '自动模式：当前 ' + curMonthLabel + ' 共 ' + monthDays + ' 个工作日' + (offWorked > 0 ? '（含假期加班 ' + offWorked + ' 天）' : '') : '用于折算日薪与时薪' }}</small></div>
        <div class="ctl">
          <Input v-if="!settings.autoDays" type="number" min="1" max="31" step="0.25" :model-value="settings.daysPerMonth" @change="value => setNumber('daysPerMonth', value, DEFAULTS.daysPerMonth)" />
          <div v-else class="auto-days num">{{ monthDays }}</div>
        </div>
      </div>
      <p class="hint" v-html="setHint"></p>
    </div>

    <div class="card set-group">
      <h3>数据</h3>
      <div class="io-row">
        <Button size="sm" variant="ghost" @click="doExport">导出到下方文本框</Button>
        <Button size="sm" variant="ghost" @click="doCopy">复制全部数据</Button>
        <Button size="sm" variant="ghost" @click="doImport">从文本框导入</Button>
        <Button size="sm" variant="danger" @click="doClear">清空全部数据</Button>
      </div>
      <Textarea v-model="ioArea" :rows="5" placeholder="点击「导出」查看全部数据 JSON；粘贴后点「导入」可恢复（会覆盖现有数据）" class="io-area" />
    </div>

  </section>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import Button from '../components/ui/Button.vue'
import Input from '../components/ui/Input.vue'
import Textarea from '../components/ui/Textarea.vue'
import Toggle from '../components/ui/Toggle.vue'
import { useAppStore, DEFAULTS } from '../stores/app'
import { CALC } from '../utils/calc'

const store = useAppStore()
const settings = computed(() => store.settings)
const accents = [
  { key: 'green', label: '森林绿', color: '#267251' },
  { key: 'blue', label: '墨水蓝', color: '#3e667d' },
  { key: 'plum', label: '灰紫', color: '#80536c' },
  { key: 'rust', label: '陶土红', color: '#a4573f' }
]
const ioArea = ref('')
const curMonthKey = CALC.dateKey(new Date()).slice(0, 7)
const curMonthLabel = `${Number(curMonthKey.slice(5, 7))} 月`
const monthDays = computed(() => CALC.monthWorkdays(curMonthKey, store.holidays, store.records, store.settings))
const offWorked = computed(() => CALC.offDaysWorked(curMonthKey, store.holidays, store.records, store.settings))
const setHint = computed(() => {
  const std = CALC.stdWorkMin(store.settings)
  if (std <= 0) return '标准上下班时间设置无效（扣除午休后工时 ≤ 0）'
  return `当前标准工时 ${CALC.fmtHours(std)} 小时/天。工资请在「记录」页按月设置（每月可在当月调整税前 / 税后月薪）。`
})

function set(key, value) { store.settings[key] = value; store.saveAll() }
function setNumber(key, value, fallback) {
  const number = Number(value)
  set(key, Number.isFinite(number) ? number : fallback)
}
function doExport() { ioArea.value = JSON.stringify({ settings: store.settings, records: store.records }); ElMessage.success('已导出到文本框') }
async function doCopy() {
  const data = JSON.stringify({ settings: store.settings, records: store.records })
  ioArea.value = data
  try { await navigator.clipboard.writeText(data); ElMessage.success('已复制到剪贴板') } catch (error) { ElMessage.warning('复制失败，请手动长按复制') }
}
async function doImport() {
  let data
  try { data = JSON.parse(ioArea.value); if (typeof data !== 'object' || data === null) throw new Error('invalid') } catch (error) { ElMessage.error('导入失败：文本框内容不是有效数据'); return }
  if (!window.confirm('导入会覆盖现有全部数据，确定？')) return
  store.settings = { ...DEFAULTS, ...(data.settings || {}) }
  store.records = data.records || {}
  store.saveAll()
  ElMessage.success('导入成功')
}
async function doClear() {
  if (!window.confirm('确定清空全部打卡记录和设置？此操作不可恢复')) return
  if (!window.confirm('再次确认：真的要全部清空吗？')) return
  store.settings = { ...DEFAULTS }
  store.records = {}
  store.saveAll()
  ElMessage.success('已清空')
}
</script>
