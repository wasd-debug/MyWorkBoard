<template>
  <section>
    <div class="page-heading">
      <div class="label">MODULE / SYSTEM.CONFIG</div>
      <h1>设置 <span style="font-size:13px;color:var(--dim);font-weight:500">// SETTINGS</span></h1>
    </div>

    <div class="card set-group appearance-group">
      <h2>外观</h2>
      <div class="row">
        <div class="lbl">配色风格<small>同步调整页面背景、功能卡片、按钮与图表</small></div>
        <div class="ctl accent-options" aria-label="选择主题强调色">
          <button v-for="item in accents" :key="item.key" class="accent-swatch" :class="{ active: appStore.accent === item.key }" :style="{ '--swatch': item.color }" type="button" :aria-label="item.label" :title="item.label" @click="appStore.setAccent(item.key)"></button>
        </div>
      </div>
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
        <Button variant="ghost" :disabled="savingLunch" @click="cancelLunchUpdate">取消</Button>
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
        <div class="lbl">每月排班天数<small>{{ settings.autoDays ? '自动模式：当前 ' + curMonthLabel + ' 共 ' + monthDays + ' 个工作日' + (offWorked > 0 ? '（含假期加班 ' + offWorked + ' 天）' : '') : '用于折算日薪与时薪' }}</small></div>
        <div class="ctl">
          <Input v-if="!settings.autoDays" type="number" aria-label="每月排班天数" min="1" max="31" step="0.25" :model-value="settings.daysPerMonth" @change="value => setNumber('daysPerMonth', value, DEFAULTS.daysPerMonth)" />
          <div v-else class="auto-days num">{{ monthDays }}</div>
        </div>
      </div>
      <p class="hint" v-html="setHint"></p>
    </div>

    <div class="card set-group">
      <h2>数据</h2>
      <div class="io-row">
        <Button size="sm" variant="ghost" @click="doExport">导出到下方文本框</Button>
        <Button size="sm" variant="ghost" @click="doCopy">复制全部数据</Button>
        <Button size="sm" variant="ghost" @click="doImport">从文本框导入</Button>
        <Button size="sm" variant="danger" @click="doClear">清空全部数据</Button>
      </div>
      <Textarea v-model="ioArea" :rows="5" aria-label="工时数据导入导出文本" placeholder="点击「导出」查看全部数据 JSON；粘贴后点「导入」可恢复（会覆盖现有数据）" class="io-area" />
    </div>

    <div class="card set-group model-config">
      <h2>Agent 模型与成本</h2>
      <p class="hint">API Key 只在本次表单中使用，服务端会加密保存；页面不会写入 localStorage 或返回完整密钥。</p>
      <div class="model-toolbar">
        <select v-model="selectedModelId" aria-label="选择模型配置" @change="selectModel">
          <option v-for="item in modelConnections" :key="item.id" :value="item.id">{{ item.displayName }}{{ item.isDefault ? '（默认）' : '' }}</option>
          <option value="">新建模型配置</option>
        </select>
        <Button size="sm" variant="ghost" @click="loadModels">刷新</Button>
      </div>
      <div class="model-grid">
        <label>显示名称<input v-model="modelForm.displayName" maxlength="120" placeholder="例如 DeepSeek 主模型" /></label>
        <label>供应商<select v-model="modelForm.providerType"><option value="DEEPSEEK">DeepSeek</option><option value="OPENAI_COMPATIBLE">OpenAI Compatible</option></select></label>
        <label class="wide">Endpoint<input v-model="modelForm.baseUrl" type="url" placeholder="https://api.deepseek.com/chat/completions" /></label>
        <label>模型<input v-model="modelForm.modelName" placeholder="deepseek-chat" /></label>
        <label>API Key<input v-model="modelForm.apiKey" type="password" autocomplete="new-password" placeholder="不修改请留空" /></label>
        <label>计价模式<select v-model="modelForm.pricing.pricingMode"><option value="FLAT">固定单价</option><option value="DEEPSEEK_PEAK_OFFPEAK">DeepSeek 峰谷时段</option></select></label>
        <label>计价币种<select v-model="modelForm.pricing.currency"><option value="CNY">CNY 人民币</option><option value="USD">USD 美元</option></select></label>
        <template v-if="modelForm.pricing.pricingMode === 'DEEPSEEK_PEAK_OFFPEAK'">
          <p class="pricing-note wide">北京时间工作日 09:00–12:00、14:00–18:00 为高峰，其余为空闲。以下单价均为每 1M Token，可按供应商最新价格自行修改。</p>
          <h3 class="pricing-heading wide">高峰单价</h3>
        </template>
        <label>{{ modelForm.pricing.pricingMode === 'DEEPSEEK_PEAK_OFFPEAK' ? '高峰' : '' }}普通输入 / 1M<input v-model.number="modelForm.pricing.inputPerMillion" type="number" min="0" step="0.000001" /></label>
        <label>{{ modelForm.pricing.pricingMode === 'DEEPSEEK_PEAK_OFFPEAK' ? '高峰' : '' }}输出 / 1M<input v-model.number="modelForm.pricing.outputPerMillion" type="number" min="0" step="0.000001" /></label>
        <label>{{ modelForm.pricing.pricingMode === 'DEEPSEEK_PEAK_OFFPEAK' ? '高峰' : '' }}缓存命中 / 1M<input v-model.number="modelForm.pricing.cacheHitPerMillion" type="number" min="0" step="0.000001" /></label>
        <label>{{ modelForm.pricing.pricingMode === 'DEEPSEEK_PEAK_OFFPEAK' ? '高峰' : '' }}缓存未命中 / 1M<input v-model.number="modelForm.pricing.cacheMissPerMillion" type="number" min="0" step="0.000001" /></label>
        <label>{{ modelForm.pricing.pricingMode === 'DEEPSEEK_PEAK_OFFPEAK' ? '高峰' : '' }}思考过程 / 1M<input v-model.number="modelForm.pricing.reasoningPerMillion" type="number" min="0" step="0.000001" /></label>
        <template v-if="modelForm.pricing.pricingMode === 'DEEPSEEK_PEAK_OFFPEAK'">
          <h3 class="pricing-heading wide">空闲单价</h3>
          <label>空闲普通输入 / 1M<input v-model.number="modelForm.pricing.offPeakInputPerMillion" type="number" min="0" step="0.000001" /></label>
          <label>空闲输出 / 1M<input v-model.number="modelForm.pricing.offPeakOutputPerMillion" type="number" min="0" step="0.000001" /></label>
          <label>空闲缓存命中 / 1M<input v-model.number="modelForm.pricing.offPeakCacheHitPerMillion" type="number" min="0" step="0.000001" /></label>
          <label>空闲缓存未命中 / 1M<input v-model.number="modelForm.pricing.offPeakCacheMissPerMillion" type="number" min="0" step="0.000001" /></label>
          <label>空闲思考过程 / 1M<input v-model.number="modelForm.pricing.offPeakReasoningPerMillion" type="number" min="0" step="0.000001" /></label>
        </template>
        <label>超时（毫秒）<input v-model.number="modelForm.timeoutMs" type="number" min="5000" max="120000" step="1000" /></label>
      </div>
      <div class="io-row model-actions">
        <Button size="sm" @click="saveModel">{{ selectedModelId === 'system-environment' ? '保存成本配置' : selectedModelId ? '保存配置' : '创建配置' }}</Button>
        <Button v-if="selectedModelId" size="sm" variant="ghost" @click="testModel">测试连接</Button>
        <Button v-if="selectedModelId" size="sm" variant="ghost" @click="makeDefault">设为默认</Button>
        <Button v-if="selectedModelId && selectedModelId !== 'system-environment'" size="sm" variant="danger" @click="removeModel">删除</Button>
        <span v-if="modelStatus" class="hint">{{ modelStatus }}</span>
      </div>
    </div>

    <div class="card set-group mcp-config">
      <h2>外部 Agent / MCP</h2>
      <p class="hint">创建只读 Personal Access Token，供 Codex、WorkBuddy 或 MCP Inspector 连接。完整 Token 只显示一次，请妥善保存。</p>
      <div class="mcp-create-grid">
        <label>Token 名称<input v-model="mcpForm.name" maxlength="120" placeholder="例如 本地 Codex" /></label>
        <label>有效期<input v-model="mcpForm.expiresAt" type="datetime-local" /></label>
        <fieldset>
          <legend>只读权限</legend>
          <label><input v-model="mcpForm.scopes" type="checkbox" value="mcp:ledger:read" />账本查询</label>
          <label><input v-model="mcpForm.scopes" type="checkbox" value="mcp:worktime:read" />工时查询</label>
        </fieldset>
        <fieldset v-if="mcpForm.scopes.includes('mcp:ledger:read')">
          <legend>账本范围</legend>
          <label v-for="book in ledgerBooks" :key="book.id || book.publicId"><input v-model="mcpForm.bookIds" type="checkbox" :value="book.id || book.publicId" />{{ book.name }}</label>
          <small>不选择表示允许读取当前用户有权访问的全部账本。</small>
        </fieldset>
      </div>
      <div class="io-row">
        <Button size="sm" :disabled="creatingMcpToken || !mcpForm.name.trim() || !mcpForm.scopes.length" @click="createMcpToken">{{ creatingMcpToken ? '创建中…' : '创建 Token' }}</Button>
        <Button size="sm" variant="ghost" @click="loadMcpTokens">刷新</Button>
      </div>
      <div v-if="createdMcpToken" class="mcp-token-once" role="status">
        <strong>请立即复制，关闭后无法再次查看</strong>
        <code>{{ createdMcpToken }}</code>
        <Button size="sm" @click="copyMcpToken">复制 Token</Button>
        <Button size="sm" variant="ghost" @click="createdMcpToken = ''">我已保存</Button>
      </div>
      <div class="mcp-endpoint"><span>Streamable HTTP 地址</span><code>{{ mcpEndpoint }}</code></div>
      <div v-if="mcpTokens.length" class="mcp-token-list">
        <article v-for="token in mcpTokens" :key="token.id">
          <div><b>{{ token.name }}</b><code>{{ token.tokenHint }}</code><small>{{ token.scopes.join(' · ') }}</small></div>
          <div><span>{{ token.revokedAt ? '已撤销' : token.expiresAt ? `到期 ${formatDateTime(token.expiresAt)}` : '长期有效' }}</span><small>最后使用：{{ token.lastUsedAt ? formatDateTime(token.lastUsedAt) : '尚未使用' }}</small></div>
          <Button v-if="!token.revokedAt" size="sm" variant="danger" @click="revokeMcpToken(token)">撤销</Button>
        </article>
      </div>
      <p v-else class="hint">尚未创建 MCP Token。</p>
    </div>

  </section>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { message } from '../services/message.js'
import Button from '../components/ui/Button.vue'
import Input from '../components/ui/Input.vue'
import Textarea from '../components/ui/Textarea.vue'
import Dialog from '../components/ui/Dialog.vue'
import { useAppStore } from '../stores/app'
import { DEFAULT_WORKTIME_SETTINGS as DEFAULTS, useWorktimeStore } from '../stores/worktime.js'
import { CALC } from '../utils/calc'
import { apiCreateAgentModelConnection, apiCreateMcpToken, apiDeleteAgentModelConnection, apiListAgentModelConnections, apiListLedgerBooks, apiListMcpTokens, apiRevokeMcpToken, apiSetDefaultAgentModelConnection, apiTestAgentModelConnection, apiUpdateAgentModelConnection } from '../../packages/api-client/src/index.js'

const appStore = useAppStore()
const store = useWorktimeStore()
const settings = computed(() => store.settings)
const accents = [
  { key: 'sun', label: '日光', color: '#ffd22e' },
  { key: 'ocean', label: '海洋', color: '#68d5cf' },
  { key: 'forest', label: '森林', color: '#91bd58' },
  { key: 'berry', label: '莓果', color: '#c85f8c' },
  { key: 'night', label: '暗夜', color: '#242933' }
]
const ioArea = ref('')
const lunchDialogOpen = ref(false), pendingLunchMin = ref(0), lunchScope = ref('NONE'), lunchFromDate = ref(CALC.dateKey(new Date())), savingLunch = ref(false)
const modelConnections = ref([]), selectedModelId = ref(''), modelStatus = ref('')
const mcpTokens = ref([]), ledgerBooks = ref([]), createdMcpToken = ref(''), creatingMcpToken = ref(false)
const defaultMcpExpiry = () => { const date = new Date(Date.now() + 90 * 24 * 60 * 60 * 1000); date.setSeconds(0, 0); return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16) }
const mcpForm = ref({ name: '本地 Agent', scopes: ['mcp:ledger:read', 'mcp:worktime:read'], bookIds: [], expiresAt: defaultMcpExpiry() })
const mcpEndpoint = computed(() => `${window.location.origin}/mcp`)
const blankModel = () => ({ displayName: '', providerType: 'DEEPSEEK', baseUrl: 'https://api.deepseek.com/chat/completions', modelName: 'deepseek-chat', apiKey: '', timeoutMs: 60000, pricing: { currency: 'CNY', pricingMode: 'FLAT', inputPerMillion: 0, outputPerMillion: 0, cacheHitPerMillion: 0, cacheMissPerMillion: 0, reasoningPerMillion: 0, offPeakInputPerMillion: 0, offPeakOutputPerMillion: 0, offPeakCacheHitPerMillion: 0, offPeakCacheMissPerMillion: 0, offPeakReasoningPerMillion: 0 } })
const modelForm = ref(blankModel())
const curMonthKey = CALC.dateKey(new Date()).slice(0, 7)
const curMonthLabel = `${Number(curMonthKey.slice(5, 7))} 月`
const monthDays = computed(() => CALC.monthWorkdays(curMonthKey, appStore.holidays, store.records, store.settings))
const offWorked = computed(() => CALC.offDaysWorked(curMonthKey, appStore.holidays, store.records, store.settings))
const setHint = computed(() => {
  const std = CALC.stdWorkMin(store.settings)
  if (std <= 0) return '标准上下班时间设置无效（扣除午休后工时 ≤ 0）'
  return `当前标准工时 ${CALC.fmtHours(std)} 小时/天。工资请在「记录」页按月设置（每月可在当月调整税前 / 税后月薪）。`
})

async function set(key, value) { await store.saveSettings({ [key]: value }) }
async function loadModels() { try { modelConnections.value = await apiListAgentModelConnections(); selectedModelId.value = modelConnections.value.find(item => item.isDefault)?.id || modelConnections.value[0]?.id || ''; selectModel() } catch { modelStatus.value = '模型配置加载失败，请确认已登录' } }
function selectModel() { const found = modelConnections.value.find(item => item.id === selectedModelId.value); if (!found) { modelForm.value = blankModel(); return }; modelForm.value = { ...blankModel(), ...found, apiKey: '', pricing: { ...blankModel().pricing, ...(found.pricing || {}) } }; modelStatus.value = found.apiKeyConfigured ? `已配置密钥（${found.apiKeyMask}）` : '尚未配置 API Key' }
async function saveModel() { try { const payload = { ...modelForm.value, pricing: modelForm.value.pricing }; const result = selectedModelId.value ? await apiUpdateAgentModelConnection(selectedModelId.value, { ...payload, revision: modelConnections.value.find(item => item.id === selectedModelId.value)?.revision }) : await apiCreateAgentModelConnection(payload); modelStatus.value = '模型配置已保存'; await loadModels(); selectedModelId.value = result?.id || selectedModelId.value; selectModel() } catch (error) { modelStatus.value = error?.response?.data?.detail || '模型配置保存失败' } }
async function testModel() { try { const result = await apiTestAgentModelConnection(selectedModelId.value); modelStatus.value = result.success ? `连接成功，耗时 ${result.durationMs}ms` : `连接失败：${result.status}` } catch { modelStatus.value = '连接测试失败' } }
async function makeDefault() { try { await apiSetDefaultAgentModelConnection(selectedModelId.value); await loadModels(); modelStatus.value = '已设为默认模型' } catch { modelStatus.value = '设置默认模型失败' } }
async function removeModel() { if (!window.confirm('删除此模型配置？')) return; try { await apiDeleteAgentModelConnection(selectedModelId.value); await loadModels(); modelStatus.value = '模型配置已删除' } catch { modelStatus.value = '删除模型配置失败' } }
async function loadMcpTokens() { try { [mcpTokens.value, ledgerBooks.value] = await Promise.all([apiListMcpTokens(), apiListLedgerBooks()]) } catch { message.error('MCP Token 列表加载失败') } }
async function createMcpToken() {
  creatingMcpToken.value = true
  try {
    const result = await apiCreateMcpToken({ ...mcpForm.value, expiresAt: mcpForm.value.expiresAt ? new Date(mcpForm.value.expiresAt).toISOString() : null })
    createdMcpToken.value = result.rawToken
    mcpForm.value = { name: '本地 Agent', scopes: [...mcpForm.value.scopes], bookIds: [], expiresAt: defaultMcpExpiry() }
    await loadMcpTokens()
    message.success('MCP Token 已创建，请立即复制')
  } catch (error) { message.error(error?.response?.data?.detail || '创建 MCP Token 失败') }
  finally { creatingMcpToken.value = false }
}
async function copyMcpToken() { try { await navigator.clipboard.writeText(createdMcpToken.value); message.success('Token 已复制') } catch { message.warning('复制失败，请手动复制') } }
async function revokeMcpToken(token) { if (!window.confirm(`撤销“${token.name}”？已连接的外部 Agent 将立即失效。`)) return; try { await apiRevokeMcpToken(token.id); await loadMcpTokens(); message.success('Token 已撤销') } catch { message.error('撤销 Token 失败') } }
function formatDateTime(value) { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) }
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
function cancelLunchUpdate() { lunchDialogOpen.value = false }
async function confirmLunchUpdate() {
  savingLunch.value = true
  try {
    const result = await store.saveLunchSettings({ lunchMin: pendingLunchMin.value, scope: lunchScope.value, fromDate: lunchScope.value === 'FROM_DATE' ? lunchFromDate.value : null })
    lunchDialogOpen.value = false
    message.success(result?.recalculatedRecords ? `午休已修改，已重算 ${result.recalculatedRecords} 条历史记录` : '午休已修改，历史记录保持原口径')
  } catch (error) { message.error(error.response?.data?.detail || '午休设置修改失败') }
  finally { savingLunch.value = false }
}
function doExport() { ioArea.value = JSON.stringify({ settings: store.settings, records: store.records }); message.success('已导出到文本框') }
async function doCopy() {
  const data = JSON.stringify({ settings: store.settings, records: store.records })
  ioArea.value = data
  try { await navigator.clipboard.writeText(data); message.success('已复制到剪贴板') } catch (error) { message.warning('复制失败，请手动长按复制') }
}
async function doImport() {
  let data
  try { data = JSON.parse(ioArea.value); if (typeof data !== 'object' || data === null) throw new Error('invalid') } catch (error) { message.error('导入失败：文本框内容不是有效数据'); return }
  if (!window.confirm('导入会覆盖现有全部数据，确定？')) return
  await store.importResources({ settings: { ...DEFAULTS, ...(data.settings || {}) }, records: data.records || {} })
  message.success('导入成功')
}
async function doClear() {
  if (!window.confirm('确定清空全部打卡记录和设置？此操作不可恢复')) return
  if (!window.confirm('再次确认：真的要全部清空吗？')) return
  await store.clearResources(DEFAULTS)
  message.success('已清空')
}
onMounted(() => Promise.all([loadModels(), loadMcpTokens()]))
</script>
