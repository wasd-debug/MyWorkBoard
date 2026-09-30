<template>
  <section>
    <div class="page-heading">
      <div class="label">WORKTIME / SETTINGS</div>
      <h1>工时设置</h1>
    </div>

    <div class="card set-group">
      <h2>标准工作时间</h2>
      <div class="row">
        <div class="lbl">标准上班时间<small>每日开始计时的时刻</small></div>
        <div class="ctl"><Input type="time" aria-label="标准上班时间" :model-value="settings.workStart" @change="value => set('workStart', value || DEFAULTS.workStart)" /></div>
      </div>
      <div class="row">
        <div class="lbl">标准下班时间<small>每日结束计时的时刻</small></div>
        <div class="ctl"><Input type="time" aria-label="标准下班时间" :model-value="settings.workEnd" @change="value => set('workEnd', value || DEFAULTS.workEnd)" /></div>
      </div>
      <div class="row">
        <div class="lbl">午休等扣除时长<small>不计入工时的分钟数</small></div>
        <div class="ctl"><Input type="number" aria-label="午休扣除分钟数" min="0" max="240" step="5" :model-value="settings.lunchMin" @change="openLunchDialog" /></div>
      </div>
    </div>

    <Dialog v-model:open="lunchDialogOpen" title="修改午休时长">
      <div class="lunch-recalc-dialog">
        <p>午休将从 <b>{{ settings.lunchMin }} 分钟</b> 修改为 <b>{{ pendingLunchMin }} 分钟</b>。请选择历史数据的处理方式：</p>
        <label><input v-model="lunchScope" type="radio" value="NONE" /> 仅修改设置，不重算已有记录</label>
        <label><input v-model="lunchScope" type="radio" value="ALL" /> 重算全部已有记录</label>
        <label><input v-model="lunchScope" type="radio" value="FROM_DATE" /> 从指定日期开始重算</label>
        <Input v-if="lunchScope === 'FROM_DATE'" v-model="lunchFromDate" aria-label="历史重算起始日期" type="date" />
        <small>重算会更新对应记录的午休快照、工时、加班时长和实际时薪；未选中的历史记录保持原计算口径。</small>
      </div>
      <template #footer>
        <Button variant="ghost" :disabled="savingLunch" @click="lunchDialogOpen = false">取消</Button>
        <Button :disabled="savingLunch || (lunchScope === 'FROM_DATE' && !lunchFromDate)" @click="confirmLunchUpdate">{{ savingLunch ? '处理中…' : '确认修改' }}</Button>
      </template>
    </Dialog>

    <div class="card set-group">
      <h2>排班设置</h2>
      <div class="row">
        <div class="lbl">自动获取法定工作日<small>按节假日调休自动计算当月排班天数</small></div>
        <div class="ctl"><Toggle :model-value="settings.autoDays" aria-label="自动获取法定工作日" @update:model-value="value => set('autoDays', value)" /></div>
      </div>
      <div class="row">
        <div class="lbl">每月排班天数<small>{{ settings.autoDays ? `自动模式：当前 ${curMonthLabel} 共 ${monthDays} 个工作日${offWorked > 0 ? `（含假期加班 ${offWorked} 天）` : ''}` : '用于折算日薪与时薪' }}</small></div>
        <div class="ctl">
          <Input v-if="!settings.autoDays" type="number" aria-label="每月排班天数" min="1" max="31" step="0.25" :model-value="settings.daysPerMonth" @change="value => setNumber('daysPerMonth', value, DEFAULTS.daysPerMonth)" />
          <div v-else class="auto-days num">{{ monthDays }}</div>
        </div>
      </div>
      <p class="hint">{{ setHint }}</p>
    </div>

    <div class="card set-group">
      <h2>工时数据</h2>
      <div class="io-row">
        <Button size="sm" variant="ghost" @click="doExport">导出到下方文本框</Button>
        <Button size="sm" variant="ghost" @click="doCopy">复制全部数据</Button>
        <Button size="sm" variant="ghost" @click="doImport">从文本框导入</Button>
        <Button size="sm" variant="danger" @click="doClear">清空全部数据</Button>
      </div>
      <Textarea v-model="ioArea" :rows="5" aria-label="工时数据导入导出文本" placeholder="点击「导出」查看全部数据 JSON；粘贴后点「导入」可恢复（会覆盖现有数据）" class="io-area" />
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import Button from '../components/ui/Button.vue'
import Dialog from '../components/ui/Dialog.vue'
import Input from '../components/ui/Input.vue'
import Textarea from '../components/ui/Textarea.vue'
import Toggle from '../components/ui/Toggle.vue'
import { message } from '../services/message.js'
import { useAppStore } from '../stores/app'
import { DEFAULT_WORKTIME_SETTINGS as DEFAULTS, useWorktimeStore } from '../stores/worktime.js'
import { CALC } from '../utils/calc'

const appStore = useAppStore()
const store = useWorktimeStore()
const settings = computed(() => store.settings)
const ioArea = ref('')
const lunchDialogOpen = ref(false)
const pendingLunchMin = ref(0)
const lunchScope = ref('NONE')
const lunchFromDate = ref(CALC.dateKey(new Date()))
const savingLunch = ref(false)
const curMonthKey = CALC.dateKey(new Date()).slice(0, 7)
const curMonthLabel = `${Number(curMonthKey.slice(5, 7))} 月`
const monthDays = computed(() => CALC.monthWorkdays(curMonthKey, appStore.holidays, store.records, store.settings))
const offWorked = computed(() => CALC.offDaysWorked(curMonthKey, appStore.holidays, store.records, store.settings))
const setHint = computed(() => {
  const standardMinutes = CALC.stdWorkMin(store.settings)
  return standardMinutes <= 0
    ? '标准上下班时间设置无效（扣除午休后工时不大于 0）'
    : `当前标准工时 ${CALC.fmtHours(standardMinutes)} 小时/天。工资请在“记录”页按月设置。`
})

async function set(key, value) { await store.saveSettings({ [key]: value }) }
function setNumber(key, value, fallback) {
  const number = Number(value)
  set(key, Number.isFinite(number) ? number : fallback)
}
function openLunchDialog(value) {
  const number = Math.min(240, Math.max(0, Number(value) || 0))
  if (number === Number(settings.value.lunchMin || 0)) return
  pendingLunchMin.value = number
  lunchScope.value = 'NONE'
  lunchFromDate.value = CALC.dateKey(new Date())
  lunchDialogOpen.value = true
}
async function confirmLunchUpdate() {
  savingLunch.value = true
  try {
    const result = await store.saveLunchSettings({ lunchMin: pendingLunchMin.value, scope: lunchScope.value, fromDate: lunchScope.value === 'FROM_DATE' ? lunchFromDate.value : null })
    lunchDialogOpen.value = false
    message.success(result?.recalculatedRecords ? `午休已修改，已重算 ${result.recalculatedRecords} 条历史记录` : '午休已修改，历史记录保持原口径')
  } catch (error) {
    message.error(error?.response?.data?.detail || '午休设置修改失败')
  } finally {
    savingLunch.value = false
  }
}
function doExport() { ioArea.value = JSON.stringify({ settings: store.settings, records: store.records }); message.success('已导出到文本框') }
async function doCopy() {
  const data = JSON.stringify({ settings: store.settings, records: store.records })
  ioArea.value = data
  try { await navigator.clipboard.writeText(data); message.success('已复制到剪贴板') } catch { message.warning('复制失败，请手动复制') }
}
async function doImport() {
  let data
  try { data = JSON.parse(ioArea.value); if (typeof data !== 'object' || data === null) throw new Error('invalid') } catch { message.error('导入失败：文本框内容不是有效数据'); return }
  if (!window.confirm('导入会覆盖现有全部工时数据，确定？')) return
  await store.importResources({ settings: { ...DEFAULTS, ...(data.settings || {}) }, records: data.records || {} })
  message.success('工时数据已导入')
}
async function doClear() {
  if (!window.confirm('确定清空全部打卡记录和工时设置？此操作不可恢复')) return
  if (!window.confirm('再次确认：真的要全部清空吗？')) return
  await store.clearResources(DEFAULTS)
  message.success('工时数据已清空')
}
</script>
