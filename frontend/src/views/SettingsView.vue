<template>
  <section>
    <!-- 标准工作时间 -->
    <div class="card set-group">
      <h3>标准工作时间</h3>
      <div class="row">
        <div class="lbl">标准上班时间</div>
        <div class="ctl"><el-input type="time" :model-value="settings.workStart" @change="v => set('workStart', v || DEFAULTS.workStart)" /></div>
      </div>
      <div class="row">
        <div class="lbl">标准下班时间</div>
        <div class="ctl"><el-input type="time" :model-value="settings.workEnd" @change="v => set('workEnd', v || DEFAULTS.workEnd)" /></div>
      </div>
      <div class="row">
        <div class="lbl">午休等扣除时长<small>不计入工时的分钟数</small></div>
        <div class="ctl"><el-input-number :model-value="settings.lunchMin" :min="0" :max="240" :step="5" :controls="false" style="width:100%" @change="v => set('lunchMin', v || 0)" /></div>
      </div>
    </div>

    <!-- 排班设置 -->
    <div class="card set-group">
      <h3>排班设置</h3>
      <div class="row">
        <div class="lbl">自动获取法定工作日<small>按节假日调休自动计算当月排班天数</small></div>
        <div class="ctl"><el-switch :model-value="settings.autoDays" @change="v => set('autoDays', v)" /></div>
      </div>
      <div class="row">
        <div class="lbl">每月排班天数<small>{{ settings.autoDays ? '自动模式：当前 ' + curMonthLabel + ' 共 ' + monthDays + ' 个工作日' + (offWorked > 0 ? '（含假期加班 ' + offWorked + ' 天）' : '') : '用于折算日薪与时薪' }}</small></div>
        <div class="ctl">
          <el-input-number v-if="!settings.autoDays" :model-value="settings.daysPerMonth" :min="1" :max="31" :step="0.25" :controls="false" style="width:100%" @change="v => set('daysPerMonth', v || DEFAULTS.daysPerMonth)" />
          <div v-else class="auto-days num">{{ monthDays }}</div>
        </div>
      </div>
      <p class="hint" v-html="setHint"></p>
    </div>

    <!-- 数据 -->
    <div class="card set-group">
      <h3>数据</h3>
      <div class="io-row">
        <el-button size="small" @click="doExport">导出到下方文本框</el-button>
        <el-button size="small" @click="doCopy">复制全部数据</el-button>
        <el-button size="small" @click="doImport">从文本框导入</el-button>
        <el-button size="small" type="danger" plain @click="doClear">清空全部数据</el-button>
      </div>
      <el-input type="textarea" v-model="ioArea" :rows="5" resize="vertical"
                placeholder="点击「导出」查看全部数据 JSON；粘贴后点「导入」可恢复（会覆盖现有数据）"
                style="margin-top:10px" />
    </div>

    <p class="hint" style="text-align:center;margin:16px 0 8px">
      数据保存在浏览器本地缓存，服务运行时同步到数据库（MySQL）<br>
      顶栏「数据库」表示已连接本地服务，数据安全存储
    </p>
  </section>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore, DEFAULTS } from '../stores/app'
import { CALC } from '../utils/calc'

const store = useAppStore()
const settings = computed(() => store.settings)
const ioArea = ref('')

/* 本月工作日（自动模式） */
const curMonthKey = CALC.dateKey(new Date()).slice(0, 7)
const curMonthLabel = `${Number(curMonthKey.slice(5, 7))} 月`
const monthDays = computed(() => CALC.monthWorkdays(curMonthKey, store.holidays, store.records, store.settings))
const offWorked = computed(() => CALC.offDaysWorked(curMonthKey, store.holidays, store.records, store.settings))

const setHint = computed(() => {
  const std = CALC.stdWorkMin(store.settings)
  if (std <= 0) return `标准上下班时间设置无效（扣除午休后工时 ≤ 0）`
  return `当前标准工时 ${CALC.fmtHours(std)} 小时/天。工资请在「记录」页按月设置（每月可在当月调整税前 / 税后月薪）。`
})

function set(key, v) {
  store.settings[key] = v
  store.saveAll()
}

function doExport() {
  ioArea.value = JSON.stringify({ settings: store.settings, records: store.records })
  ElMessage.success('已导出到文本框')
}
async function doCopy() {
  const data = JSON.stringify({ settings: store.settings, records: store.records })
  ioArea.value = data
  try {
    await navigator.clipboard.writeText(data)
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    ElMessage.warning('复制失败，请手动长按复制')
  }
}
async function doImport() {
  try {
    const data = JSON.parse(ioArea.value)
    if (typeof data !== 'object' || data === null) throw 0
    await ElMessageBox.confirm('导入会覆盖现有全部数据，确定？', '确认导入', { type: 'warning' })
    store.settings = { ...DEFAULTS, ...(data.settings || {}) }
    store.records = data.records || {}
    store.saveAll()
    ElMessage.success('导入成功')
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error('导入失败：文本框内容不是有效数据')
  }
}
async function doClear() {
  try {
    await ElMessageBox.confirm('确定清空全部打卡记录和设置？此操作不可恢复', '清空数据', { type: 'error' })
    await ElMessageBox.confirm('再次确认：真的要全部清空吗？', '再次确认', { type: 'error' })
    store.settings = { ...DEFAULTS }
    store.records = {}
    store.saveAll()
    ElMessage.success('已清空')
  } catch (e) { /* 取消 */ }
}
</script>
