<template>
  <section>
    <div class="page-heading">
      <div class="label">MODULE / SYSTEM.CONFIG</div>
      <h1>设置 <span style="font-size:13px;color:var(--dim);font-weight:500">// SETTINGS</span></h1>
    </div>
    <nav class="settings-module-nav" aria-label="设置模块">
      <button v-for="item in settingModules" :key="item.key" type="button" :class="{ active: activeModule === item.key }" @click="activeModule = item.key">{{ item.label }}</button>
    </nav>

    <div v-show="activeModule === 'general'" class="card set-group appearance-group">
      <h2>外观</h2>
      <div class="row">
        <div class="lbl">配色风格<small>同步调整页面背景、功能卡片、按钮与图表</small></div>
        <div class="ctl accent-options" aria-label="选择主题强调色">
          <button v-for="item in accents" :key="item.key" class="accent-swatch" :class="{ active: appStore.accent === item.key }" :style="{ '--swatch': item.color }" type="button" :aria-label="item.label" :title="item.label" @click="appStore.setAccent(item.key)"></button>
        </div>
      </div>
    </div>

    <div v-show="activeModule === 'account'" class="card set-group account-settings">
      <h2>账号</h2>
      <div class="row">
        <div class="lbl">退出当前账号<small>退出后需要重新登录才能访问服务端数据</small></div>
        <div class="ctl"><Button size="sm" variant="danger" @click="logout">退出登录</Button></div>
      </div>
    </div>

    <div v-show="activeModule === 'model'" class="card set-group model-config">
      <h2>Agent 模型与成本</h2>
      <p class="hint">主页面仅展示模型状态与成本摘要；API Key、峰谷定价和连接参数在弹框中配置。</p>
      <div class="model-toolbar"><Button size="sm" @click="openModelDialog('')">新增模型</Button><Button size="sm" variant="ghost" @click="loadModels">刷新</Button></div>
      <div v-if="modelConnections.length" class="model-list">
        <article v-for="item in modelConnections" :key="item.id" class="model-list-item">
          <div><strong>{{ item.displayName }}</strong><span v-if="item.isDefault" class="model-badge">默认</span><small>{{ item.providerType }} · {{ item.modelName }} · {{ item.apiKeyConfigured ? `密钥 ${item.apiKeyMask}` : '未配置密钥' }}</small></div>
          <div><span :class="item.enabled ? 'model-enabled' : 'model-disabled'">{{ item.enabled ? '启用' : '已禁用' }}</span><small>{{ item.pricing?.currency || '未定价' }} · {{ item.connectionStatus || '未测试' }}</small></div>
          <div class="model-list-actions"><Button size="sm" variant="ghost" @click="openModelDialog(item.id)">编辑</Button><Button size="sm" variant="ghost" @click="testModel(item.id)">测试连接</Button><Button v-if="!item.systemManaged" size="sm" variant="ghost" @click="toggleModel(item)">{{ item.enabled ? '停用' : '启用' }}</Button><Button v-else size="sm" variant="ghost" disabled title="系统环境配置由服务器环境变量管理">停用</Button><Button v-if="!item.systemManaged" size="sm" variant="danger" @click="removeModel(item.id, item.displayName)">删除</Button><Button v-else size="sm" variant="danger" disabled title="系统环境配置不能删除">删除</Button></div>
        </article>
      </div><p v-else class="hint">暂无模型配置，请先新增。</p>
      <Dialog v-model:open="modelDialogOpen" :title="selectedModelId ? '编辑模型配置' : '新增模型配置'">
        <p class="hint">API Key 只在本次表单中使用，服务端会加密保存；页面不会写入 localStorage 或返回完整密钥。</p>
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
        <template #footer><Button size="sm" variant="ghost" @click="modelDialogOpen = false">取消</Button><Button size="sm" @click="saveModel">{{ selectedModelId ? '保存配置' : '创建配置' }}</Button><Button v-if="selectedModelId && selectedModelId !== 'system-environment'" size="sm" variant="ghost" @click="makeDefault">设为默认</Button></template>
      </Dialog>
      <p v-if="modelStatus" class="hint model-status">{{ modelStatus }}</p>
    </div>

    <div v-show="activeModule === 'operations'" class="card set-group operations-config">
      <div class="operations-heading">
        <div><h2>Agent 运行质量</h2><p class="hint">统计模型调用、工具执行、Token、费用和失败情况；历史费用按当次调用保存的价格快照统计。</p></div>
        <Button size="sm" variant="ghost" :disabled="operationsLoading" @click="loadOperations">{{ operationsLoading ? '加载中…' : '刷新' }}</Button>
      </div>
      <div class="operations-filters">
        <label>时间范围<select v-model="operationsQuery.preset" @change="loadOperations"><option value="TODAY">今日</option><option value="THIS_WEEK">本周</option><option value="THIS_MONTH">本月</option><option value="LAST_7_DAYS">近 7 天</option><option value="LAST_30_DAYS">近 30 天</option><option value="CUSTOM">自定义</option></select></label>
        <label>时间粒度<select v-model="operationsQuery.granularity" @change="loadOperations"><option value="AUTO">自动</option><option value="HOUR">小时</option><option value="DAY">天</option><option value="WEEK">周</option></select></label>
        <label v-if="operationsQuery.preset === 'CUSTOM'">开始日期<input v-model="operationsQuery.from" type="date" @change="loadOperations" /></label>
        <label v-if="operationsQuery.preset === 'CUSTOM'">结束日期<input v-model="operationsQuery.to" type="date" @change="loadOperations" /></label>
      </div>
      <p v-if="operationsError" class="operations-error">{{ operationsError }}</p>
      <template v-if="operationsMetrics">
        <div class="operations-kpis">
          <div><span>对话轮次</span><b>{{ operationsMetrics.summary.turns }}</b><small>成功 {{ operationsMetrics.summary.completed }} · 失败 {{ operationsMetrics.summary.failed }}</small></div>
          <div><span>首字时延</span><b>{{ formatDuration(operationsMetrics.summary.averageFirstTokenMs) }}</b><small>平均值</small></div>
          <div><span>总耗时 P95</span><b>{{ formatDuration(operationsMetrics.summary.p95DurationMs) }}</b><small>已完成 turn</small></div>
          <div><span>Token</span><b>{{ formatNumber(operationsMetrics.summary.totalTokens) }}</b><small>模型调用合计</small></div>
          <div><span>估算费用</span><b>{{ formatMoney(operationsMetrics.summary.totalCost, operationsMetrics.budget.currency) }}</b><small>仅统计 {{ operationsMetrics.budget.currency }}</small></div>
          <div><span>工具失败</span><b :class="operationsMetrics.summary.toolFailures ? 'bad' : 'good'">{{ operationsMetrics.summary.toolFailures }}</b><small>共 {{ operationsMetrics.summary.toolCalls }} 次</small></div>
        </div>
        <div class="operations-columns">
          <div class="operations-subpanel"><h3>趋势 · {{ operationsMetrics.granularity === 'HOUR' ? '小时' : operationsMetrics.granularity === 'WEEK' ? '周' : '天' }}</h3><div class="operations-table-wrap"><table><thead><tr><th>时间</th><th>轮次</th><th>Token</th><th>费用</th></tr></thead><tbody><tr v-for="point in operationsMetrics.trend" :key="point.bucket"><td>{{ point.bucket }}</td><td>{{ point.turns }}</td><td>{{ formatNumber(point.tokens) }}</td><td>{{ formatMoney(point.cost, operationsMetrics.budget.currency) }}</td></tr><tr v-if="!operationsMetrics.trend.length"><td colspan="4" class="empty-cell">当前范围暂无调用</td></tr></tbody></table></div></div>
          <div class="operations-subpanel"><h3>模型费用</h3><div v-for="model in operationsMetrics.models" :key="`${model.provider}-${model.model}-${model.currency}`" class="operations-line"><span>{{ model.model || '未知模型' }}<small>{{ model.provider || '未知供应商' }} · {{ formatNumber(model.tokens) }} Token</small></span><b>{{ formatMoney(model.cost, model.currency || 'UNPRICED') }}</b></div><p v-if="!operationsMetrics.models.length" class="hint">暂无模型调用。</p></div>
        </div>
        <div class="operations-columns">
          <div class="operations-subpanel"><h3>慢工具</h3><div v-for="tool in operationsMetrics.tools.slice(0, 6)" :key="tool.name" class="operations-line"><span>{{ tool.name }}<small>{{ tool.calls }} 次 · 失败 {{ tool.failures }}</small></span><b>{{ formatDuration(tool.averageDurationMs) }}</b></div><p v-if="!operationsMetrics.tools.length" class="hint">暂无工具调用。</p></div>
          <div class="operations-subpanel">
            <h3>预算观察</h3>
            <div class="operations-budget-progress">
              <div><span>今日</span><b>{{ formatMoney(operationsMetrics.budgetProgress.dailyCost, operationsMetrics.budgetProgress.currency) }}</b><small>{{ formatBudgetProgress(operationsMetrics.budgetProgress.dailyPercent) }}</small></div>
              <div><span>本月</span><b>{{ formatMoney(operationsMetrics.budgetProgress.monthlyCost, operationsMetrics.budgetProgress.currency) }}</b><small>{{ formatBudgetProgress(operationsMetrics.budgetProgress.monthlyPercent) }}</small></div>
            </div>
            <div class="budget-grid"><label>币种<select v-model="budgetForm.currency"><option>CNY</option><option>USD</option></select></label><label>每日预算<input v-model.number="budgetForm.dailyLimit" type="number" min="0" step="0.01" placeholder="不限制" /></label><label>每月预算<input v-model.number="budgetForm.monthlyLimit" type="number" min="0" step="0.01" placeholder="不限制" /></label></div>
            <div class="operations-alert-options"><label><input v-model="budgetForm.alertAt50" type="checkbox" />50% 告警</label><label><input v-model="budgetForm.alertAt80" type="checkbox" />80% 告警</label><label><input v-model="budgetForm.alertAt100" type="checkbox" />100% 告警</label></div>
            <p class="hint">预算仅用于告警，不阻断请求；不同币种不会直接相加。</p>
            <div class="operations-budget-actions"><Button size="sm" @click="saveBudget">保存预算</Button><span v-if="budgetStatus" class="hint">{{ budgetStatus }}</span></div>
          </div>
        </div>
        <div v-if="operationsMetrics.failures.length" class="operations-subpanel"><h3>最近失败</h3><div v-for="(failure, index) in operationsMetrics.failures.slice(0, 8)" :key="`${failure.createdAt}-${index}`" class="operations-failure"><b>{{ failure.source }}</b><span>{{ failure.status }}</span><small>{{ formatDateTime(failure.createdAt) }}{{ failure.detail ? ` · ${failure.detail}` : '' }}</small></div></div>
        <section class="operations-section"><h3>预算告警</h3>
          <div v-for="alert in budgetAlerts" :key="alert.id" class="operations-alert"><div><b>{{ alert.type === 'DAILY' ? '日预算' : '月预算' }} {{ alert.threshold }}% · {{ alert.period }}</b><small>{{ formatMoney(alert.current, alert.currency) }} / {{ formatMoney(alert.limit, alert.currency) }} · {{ formatDateTime(alert.createdAt) }}</small></div><Button v-if="alert.status !== 'READ'" size="sm" variant="ghost" @click="markBudgetAlertRead(alert)">标记已读</Button><span v-else class="hint">已读</span></div>
          <p v-if="!budgetAlerts.length" class="hint">暂无预算告警。</p>
        </section>
        <section class="operations-section"><div class="operations-heading"><h3>调用明细</h3><div class="operations-check"><label><input v-model="failuresOnly" type="checkbox" @change="resetCalls" />仅看失败 / 取消</label><label>每页<select v-model.number="callsPageSize" @change="resetCalls"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option><option :value="100">100</option></select></label></div></div>
          <p v-if="callsError" class="operations-error">{{ callsError }}</p>
          <div class="operations-table-wrap"><table class="operations-calls"><thead><tr><th>时间 / 模型</th><th>状态</th><th>Token</th><th>首字</th><th>总耗时</th><th>工具</th><th>操作</th></tr></thead><tbody>
            <template v-for="call in operationCalls" :key="call.turnId"><tr><td>{{ formatDateTime(call.createdAt) }}<small>{{ call.model || call.provider || '未知模型' }}</small></td><td>{{ call.status }}</td><td>{{ formatNumber(call.tokens) }}</td><td>{{ formatDuration(call.firstTokenMs) }}</td><td>{{ formatDuration(call.durationMs) }}</td><td>{{ call.toolCount }}</td><td><Button size="sm" variant="ghost" @click="toggleCallTrace(call.turnId)">{{ expandedTurnId === call.turnId ? '收起' : '明细' }}</Button><RouterLink :to="{ path: '/', query: { session: call.sessionId } }">原会话</RouterLink></td></tr>
              <tr v-if="expandedTurnId === call.turnId"><td colspan="7" class="operations-trace"><p v-if="traceLoading">加载中…</p><p v-else-if="traceError">{{ traceError }}</p><template v-else-if="callTrace"><p v-if="callTrace.failureSummary">{{ callTrace.failureSummary }}</p><p v-for="usage in callTrace.usage" :key="`usage-${usage.round}`">模型第 {{ usage.round }} 轮 · 输入 {{ formatNumber(usage.inputTokens) }} / 输出 {{ formatNumber(usage.outputTokens) }} / 缓存命中 {{ formatNumber(usage.cacheHitTokens) }} / 推理 {{ formatNumber(usage.reasoningTokens) }} Token · {{ formatMoney(usage.cost, usage.currency || 'UNPRICED') }} · {{ formatDuration(usage.durationMs) }}</p><p v-for="tool in callTrace.tools" :key="`tool-${tool.sequence}`">工具 {{ tool.name }} · {{ tool.status }} · {{ formatDuration(tool.durationMs) }}</p><p v-if="!callTrace.usage.length && !callTrace.tools.length && !callTrace.failureSummary">暂无模型或工具执行记录。</p></template></td></tr>
            </template><tr v-if="!operationCalls.length"><td colspan="7" class="empty-cell">{{ callsLoading ? '加载中…' : '当前范围暂无调用' }}</td></tr>
          </tbody></table></div><div class="settings-pagination operations-pages"><Button size="sm" variant="ghost" :disabled="callsPage === 0 || callsLoading" @click="changeCallsPage(-1)">上一页</Button><span>第 {{ callsPage + 1 }} / {{ callsPages }} 页 · 共 {{ callsTotal }} 条</span><Button size="sm" variant="ghost" :disabled="callsPage + 1 >= callsPages || callsLoading" @click="changeCallsPage(1)">下一页</Button></div>
        </section>
      </template>
    </div>

    <div v-show="activeModule === 'mcp'" class="card set-group mcp-config">
      <h2>外部 Agent / MCP</h2>
      <p class="hint">管理外部客户端的访问权限。Token 仅显示一次；写入仍需在网站批准。</p>
      <div class="mcp-entry-actions">
        <Button @click="mcpTokenDialogOpen = true">创建 Token</Button>
        <Button size="sm" variant="ghost" @click="loadMcpTokens">刷新 Token</Button>
      </div>
      <div class="mcp-list-filter"><label>Token 状态<select v-model="mcpTokenStatus" @change="resetMcpTokenPage"><option value="ALL">全部</option><option value="ACTIVE">有效</option><option value="REVOKED">已撤销</option></select></label></div>
      <Dialog v-model:open="mcpTokenDialogOpen" title="创建 MCP Token">
        <p class="hint">配置完成后创建；原始 Token 只展示一次，请立即复制保存。</p>
        <div class="mcp-create-grid">
        <label>Token 名称<input v-model="mcpForm.name" maxlength="120" placeholder="例如 本地 Codex" /></label>
        <label>有效期<input v-model="mcpForm.expiresAt" type="datetime-local" /></label>
        <label>权限模板<select v-model="mcpForm.permissionTemplate"><option value="READ_ONLY">只读</option><option value="PREPARE">可准备</option><option value="COMMIT">可提交</option><option value="FULL_WORKSPACE">完整工作台权限</option></select></label>
        <label>高风险策略<select v-model="mcpForm.highRiskPolicy"><option value="APPROVAL_ONLY">仅允许站内审批</option><option value="DISABLED">完全禁用 R3/R4</option></select></label>
        <label>每分钟限流<input v-model.number="mcpForm.rateLimitPerMinute" type="number" min="1" max="600" step="1" /></label>
        <fieldset>
          <legend>只读权限</legend>
          <label><input v-model="mcpForm.scopes" type="checkbox" value="mcp:ledger:read" />账本查询</label>
          <label><input v-model="mcpForm.scopes" type="checkbox" value="mcp:worktime:read" />工时查询</label>
        </fieldset>
        <fieldset>
          <legend>写入准备权限</legend>
          <label><input v-model="mcpForm.scopes" type="checkbox" value="mcp:ledger:prepare" />准备账本流水操作</label>
          <label><input v-model="mcpForm.scopes" type="checkbox" value="mcp:worktime:prepare" />准备工时操作</label>
          <small>prepare 只生成预览和站内确认链接。</small>
        </fieldset>
        <fieldset>
          <legend>低风险提交权限</legend>
          <label><input v-model="mcpForm.scopes" type="checkbox" value="mcp:ledger:commit" :disabled="!mcpForm.scopes.includes('mcp:ledger:prepare')" />提交已批准的 R2 账本操作</label>
          <label><input v-model="mcpForm.scopes" type="checkbox" value="mcp:worktime:commit" :disabled="!mcpForm.scopes.includes('mcp:worktime:prepare')" />提交已批准的 R2 工时操作</label>
          <small>必须与对应 prepare scope 同时授予；修改、删除和其他 R3/R4 操作仍禁止 MCP commit。</small>
        </fieldset>
        <fieldset v-if="mcpForm.scopes.includes('mcp:ledger:read') || mcpForm.scopes.includes('mcp:ledger:prepare') || mcpForm.scopes.includes('mcp:ledger:commit')">
          <legend>账本范围</legend>
          <label v-for="book in ledgerBooks" :key="book.id || book.publicId"><input v-model="mcpForm.bookIds" type="checkbox" :value="book.id || book.publicId" />{{ book.name }}</label>
          <small>不选择表示允许访问当前用户本来有权使用的全部账本。</small>
        </fieldset>
        </div>
        <template #footer>
          <Button size="sm" variant="ghost" @click="mcpTokenDialogOpen = false">取消</Button>
          <Button size="sm" :disabled="creatingMcpToken || !mcpForm.name.trim() || !mcpForm.scopes.length" @click="createMcpToken">{{ creatingMcpToken ? '创建中…' : '创建 Token' }}</Button>
        </template>
      </Dialog>
      <div v-if="createdMcpToken" class="mcp-token-once" role="status">
        <strong>请立即复制，关闭后无法再次查看</strong>
        <code>{{ createdMcpToken }}</code>
        <Button size="sm" @click="copyMcpToken">复制 Token</Button>
        <Button size="sm" variant="ghost" @click="createdMcpToken = ''">我已保存</Button>
      </div>
      <div class="mcp-endpoint"><span>Streamable HTTP 地址</span><code>{{ mcpEndpoint }}</code></div>
      <div v-if="mcpDiagnostics" class="mcp-diagnostics">
        <div><span>MCP</span><b :class="mcpDiagnostics.enabled ? 'ok' : 'off'">{{ mcpDiagnostics.enabled ? '已启用' : '已关闭' }}</b></div>
        <div><span>OAuth</span><b :class="mcpDiagnostics.oauthEnabled ? 'ok' : 'off'">{{ mcpDiagnostics.oauthEnabled ? '已启用' : '已关闭' }}</b></div>
        <div><span>写入</span><b :class="mcpDiagnostics.writeEnabled ? 'warn' : ''">{{ mcpDiagnostics.writeEnabled ? '已启用' : '只读' }}</b></div>
        <div><span>24 小时调用</span><b>{{ mcpDiagnostics.callsLast24Hours }}</b></div>
        <div><span>24 小时失败</span><b :class="mcpDiagnostics.failuresLast24Hours ? 'off' : ''">{{ mcpDiagnostics.failuresLast24Hours }}</b></div>
        <div><span>协议版本</span><b>{{ mcpDiagnostics.supportedProtocolVersions.join('、') }}</b></div>
      </div>
      <p v-for="warning in mcpDiagnostics?.warnings || []" :key="warning" class="mcp-diagnostic-warning">{{ warning }}</p>
      <div class="io-row">
        <Button size="sm" variant="ghost" @click="loadMcpOperations">刷新诊断</Button>
        <Button size="sm" variant="ghost" :disabled="!mcpDiagnostics" @click="copyMcpDiagnostics">复制脱敏诊断</Button>
      </div>
      <div v-if="mcpTokens.length" class="mcp-token-list">
        <article v-for="token in mcpTokens" :key="token.id">
          <div><b>{{ token.name }}</b><code>{{ token.tokenHint }}</code><small>{{ mcpTemplateLabel(token.permissionTemplate) }} · {{ token.scopes.map(mcpScopeLabel).join(' · ') }}</small></div>
          <div><span>{{ token.revokedAt ? '已撤销' : token.expiresAt ? `到期 ${formatDateTime(token.expiresAt)}` : '长期有效' }}</span><small>{{ token.highRiskPolicy === 'DISABLED' ? '高风险禁用' : '高风险站内审批' }} · {{ token.rateLimitPerMinute }} 次/分钟 · 最后使用：{{ token.lastUsedAt ? formatDateTime(token.lastUsedAt) : '尚未使用' }}</small></div>
          <Button v-if="!token.revokedAt" size="sm" variant="danger" @click="revokeMcpToken(token)">撤销</Button>
          <Button v-else size="sm" variant="danger" @click="purgeMcpToken(token)">删除</Button>
        </article>
      </div>
      <p v-else class="hint">尚未创建 MCP Token。</p>
      <div class="settings-pagination"><Button size="sm" variant="ghost" :disabled="mcpTokenPage === 0 || mcpTokenLoading" @click="changeMcpTokenPage(-1)">上一页</Button><span>第 {{ mcpTokenPage + 1 }} / {{ mcpTokenPages }} 页 · 共 {{ mcpTokenTotal }} 条</span><Button size="sm" variant="ghost" :disabled="mcpTokenPage + 1 >= mcpTokenPages || mcpTokenLoading" @click="changeMcpTokenPage(1)">下一页</Button><label>每页<select v-model.number="mcpTokenPageSize" @change="resetMcpTokenPage"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option><option :value="100">100</option></select></label></div>
      <div class="mcp-oauth-heading"><div><h3>OAuth 已授权应用</h3><p class="hint">远程 MCP 客户端通过 OAuth 2.1 + PKCE 获得访问权限；撤销后 access token 与 refresh token 立即失效。</p></div><Button size="sm" variant="ghost" @click="loadMcpGrants">刷新授权</Button></div>
      <div v-if="mcpGrants.length" class="mcp-token-list">
        <article v-for="grant in mcpGrants" :key="grant.id">
          <div><b>{{ grant.clientName }}</b><code>{{ grant.clientId }}</code><small>{{ grant.scopes.map(mcpScopeLabel).join(' · ') }}</small></div>
          <div><span>{{ grant.bookIds?.length ? `限制 ${grant.bookIds.length} 个账本` : '不含账本权限' }}</span><small>最近授权：{{ formatDateTime(grant.updatedAt) }}</small></div>
          <Button size="sm" variant="danger" @click="revokeMcpGrant(grant)">撤销授权</Button>
        </article>
      </div>
      <p v-else class="hint">尚无 OAuth 外部应用授权。</p>
      <div class="mcp-oauth-heading"><div><h3>已连接客户端</h3><p class="hint">断开客户端会撤销当前用户授予它的全部授权、Token 和未使用授权码。</p></div></div>
      <div v-if="mcpClients.length" class="mcp-token-list">
        <article v-for="client in mcpClients" :key="client.grantId">
          <div><b>{{ client.clientName }}</b><code>{{ client.clientId }}</code><small>{{ client.redirectUris.join(' · ') }}</small></div>
          <div><span>{{ client.revokedAt ? '已断开' : '已连接' }}</span><small>最后使用：{{ client.lastUsedAt ? formatDateTime(client.lastUsedAt) : '尚未使用' }}{{ client.lastUserAgent ? ` · ${client.lastUserAgent}` : '' }}</small></div>
          <Button v-if="!client.revokedAt" size="sm" variant="danger" @click="disconnectMcpClient(client)">断开客户端</Button>
        </article>
      </div>
      <p v-else class="hint">尚无已注册并授权的 OAuth 客户端。</p>
      <div v-if="!mcpEventPanelOpen" class="mcp-secondary-entry"><div><h3>最近连接事件</h3><p class="hint">仅显示脱敏 IP、客户端摘要和协议状态，不记录 Token、授权码或密钥。</p></div><Button size="sm" variant="ghost" @click="mcpEventPanelOpen = true">查看事件</Button></div>
      <div v-else class="mcp-event-panel">
        <div class="mcp-oauth-heading"><div><h3>最近连接事件</h3><p class="hint">事件详情 · 仅显示脱敏信息。</p></div><Button size="sm" variant="ghost" @click="mcpEventPanelOpen = false">返回 MCP</Button></div>
        <div class="mcp-list-filter"><label>事件状态<select v-model="mcpEventStatus" @change="resetMcpEventPage"><option value="ALL">全部</option><option value="SUCCESS">成功</option><option value="FAILED">失败</option></select></label></div>
        <div v-if="mcpEvents.length" class="mcp-event-list">
        <article v-for="(event, index) in mcpEvents" :key="`${event.createdAt}-${index}`">
          <b>{{ mcpEventLabel(event.eventType) }}</b><span :class="event.status === 'SUCCESS' || event.status === 'ACCEPTED' ? 'ok' : 'off'">{{ event.status }}</span>
          <small>{{ formatDateTime(event.createdAt) }}{{ event.maskedIp ? ` · ${event.maskedIp}` : '' }}{{ event.userAgent ? ` · ${event.userAgent}` : '' }}</small>
        </article>
        </div>
        <p v-else class="hint">尚无当前用户可见的 MCP/OAuth 事件。</p>
        <div class="settings-pagination"><Button size="sm" variant="ghost" :disabled="mcpEventPage === 0 || mcpEventLoading" @click="changeMcpEventPage(-1)">上一页</Button><span>第 {{ mcpEventPage + 1 }} / {{ mcpEventPages }} 页 · 共 {{ mcpEventTotal }} 条</span><Button size="sm" variant="ghost" :disabled="mcpEventPage + 1 >= mcpEventPages || mcpEventLoading" @click="changeMcpEventPage(1)">下一页</Button><label>每页<select v-model.number="mcpEventPageSize" @change="resetMcpEventPage"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option><option :value="100">100</option></select></label></div>
      </div>
    </div>

  </section>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { apiListAgentBudgetAlerts, apiMarkAgentBudgetAlertRead, apiListAgentOperationCalls, apiGetAgentOperationCallTrace } from '../../packages/api-client/src/index.js'
import { message } from '../services/message.js'
import Button from '../components/ui/Button.vue'
import Dialog from '../components/ui/Dialog.vue'
import { useAppStore } from '../stores/app'
import { CALC } from '../utils/calc'
import { apiCreateAgentModelConnection, apiCreateMcpToken, apiDeleteAgentModelConnection, apiDisconnectMcpOAuthClient, apiGetAgentOperationMetrics, apiGetAgentUsageBudget, apiGetMcpDiagnostics, apiListAgentModelConnections, apiListLedgerBooks, apiListMcpOAuthClients, apiListMcpOAuthGrants, apiListMcpProtocolEvents, apiListMcpTokens, apiPurgeMcpToken, apiRevokeMcpOAuthGrant, apiRevokeMcpToken, apiSaveAgentUsageBudget, apiSetDefaultAgentModelConnection, apiTestAgentModelConnection, apiUpdateAgentModelConnection } from '../../packages/api-client/src/index.js'

const appStore = useAppStore()
const settingModules = [
  { key: 'general', label: '基础设置' },
  { key: 'model', label: '模型与成本' },
  { key: 'operations', label: '运行质量' },
  { key: 'mcp', label: 'MCP / 外部 Agent' },
  { key: 'account', label: '账号' },
]
const activeModule = ref('general')
const accents = [
  { key: 'sun', label: '日光', color: '#ffd22e' },
  { key: 'ocean', label: '海洋', color: '#68d5cf' },
  { key: 'forest', label: '森林', color: '#91bd58' },
  { key: 'berry', label: '莓果', color: '#c85f8c' },
  { key: 'night', label: '暗夜', color: '#242933' }
]
const modelConnections = ref([]), selectedModelId = ref(''), modelStatus = ref(''), modelDialogOpen = ref(false)
const mcpTokens = ref([]), mcpGrants = ref([]), mcpClients = ref([]), mcpEvents = ref([]), mcpDiagnostics = ref(null), ledgerBooks = ref([]), createdMcpToken = ref(''), creatingMcpToken = ref(false)
const mcpTokenPage = ref(0), mcpTokenPageSize = ref(20), mcpTokenPages = ref(1), mcpTokenTotal = ref(0), mcpTokenLoading = ref(false)
const mcpEventPage = ref(0), mcpEventPageSize = ref(20), mcpEventPages = ref(1), mcpEventTotal = ref(0), mcpEventLoading = ref(false)
const mcpTokenStatus = ref('ALL'), mcpEventStatus = ref('ALL')
const mcpTokenDialogOpen = ref(false), mcpEventPanelOpen = ref(false)
const operationsMetrics = ref(null), operationsLoading = ref(false), operationsError = ref(''), budgetStatus = ref('')
const budgetAlerts = ref([]), operationCalls = ref([]), callsPage = ref(0), callsPages = ref(1), callsTotal = ref(0), callsHasNext = ref(false), failuresOnly = ref(false), callsLoading = ref(false), callsError = ref('')
const callsPageSize = ref(20)
const expandedTurnId = ref(''), callTrace = ref(null), traceLoading = ref(false), traceError = ref('')
let operationsRequest = 0, callsRequest = 0, traceRequest = 0, mcpTokenRequest = 0, mcpEventRequest = 0
const operationsQuery = ref({ preset: 'TODAY', granularity: 'AUTO', from: CALC.dateKey(new Date()), to: CALC.dateKey(new Date()) })
const budgetForm = ref({ currency: 'CNY', dailyLimit: null, monthlyLimit: null, singleRequestLimit: null, tokenLimit: null, enforcementMode: 'WARN', revision: null })
const defaultMcpExpiry = () => { const date = new Date(Date.now() + 90 * 24 * 60 * 60 * 1000); date.setSeconds(0, 0); return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16) }
const mcpForm = ref({ name: '本地 Agent', permissionTemplate: 'READ_ONLY', highRiskPolicy: 'APPROVAL_ONLY', rateLimitPerMinute: 120, scopes: ['mcp:ledger:read', 'mcp:worktime:read'], bookIds: [], expiresAt: defaultMcpExpiry() })
const mcpEndpoint = computed(() => `${window.location.origin}/mcp`)
watch(() => [...mcpForm.value.scopes], (scopes) => {
  const next = scopes.filter(scope => scope !== 'mcp:ledger:commit' || scopes.includes('mcp:ledger:prepare'))
    .filter(scope => scope !== 'mcp:worktime:commit' || scopes.includes('mcp:worktime:prepare'))
  if (next.length !== scopes.length) mcpForm.value.scopes = next
})
watch(() => mcpForm.value.permissionTemplate, template => {
  const read = ['mcp:ledger:read', 'mcp:worktime:read']
  const prepare = [...read, 'mcp:ledger:prepare', 'mcp:worktime:prepare']
  mcpForm.value.scopes = template === 'READ_ONLY' ? read : template === 'PREPARE' ? prepare : [...prepare, 'mcp:ledger:commit', 'mcp:worktime:commit']
})
const blankModel = () => ({ displayName: '', providerType: 'DEEPSEEK', baseUrl: 'https://api.deepseek.com/chat/completions', modelName: 'deepseek-chat', apiKey: '', timeoutMs: 60000, pricing: { currency: 'CNY', pricingMode: 'FLAT', inputPerMillion: 0, outputPerMillion: 0, cacheHitPerMillion: 0, cacheMissPerMillion: 0, reasoningPerMillion: 0, offPeakInputPerMillion: 0, offPeakOutputPerMillion: 0, offPeakCacheHitPerMillion: 0, offPeakCacheMissPerMillion: 0, offPeakReasoningPerMillion: 0 } })
const modelForm = ref(blankModel())
async function loadModels() { try { modelConnections.value = await apiListAgentModelConnections() } catch { modelStatus.value = '模型配置加载失败，请确认已登录' } }
function selectModel() { const found = modelConnections.value.find(item => item.id === selectedModelId.value); if (!found) { modelForm.value = blankModel(); return }; modelForm.value = { ...blankModel(), ...found, apiKey: '', pricing: { ...blankModel().pricing, ...(found.pricing || {}) } }; modelStatus.value = found.apiKeyConfigured ? `已配置密钥（${found.apiKeyMask}）` : '尚未配置 API Key' }
function openModelDialog(id) { selectedModelId.value = id; selectModel(); modelStatus.value = ''; modelDialogOpen.value = true }
async function saveModel() { try { const payload = { ...modelForm.value, pricing: modelForm.value.pricing }; const result = selectedModelId.value ? await apiUpdateAgentModelConnection(selectedModelId.value, { ...payload, revision: modelConnections.value.find(item => item.id === selectedModelId.value)?.revision }) : await apiCreateAgentModelConnection(payload); modelStatus.value = '模型配置已保存'; modelDialogOpen.value = false; await loadModels(); selectedModelId.value = result?.id || selectedModelId.value } catch (error) { modelStatus.value = error?.response?.data?.detail || '模型配置保存失败' } }
async function testModel(id = selectedModelId.value) { try { const result = await apiTestAgentModelConnection(id); modelStatus.value = result.success ? `连接成功，耗时 ${result.durationMs}ms` : `连接失败：${result.status}`; await loadModels() } catch { modelStatus.value = '连接测试失败' } }
async function makeDefault() { try { await apiSetDefaultAgentModelConnection(selectedModelId.value); await loadModels(); modelStatus.value = '已设为默认模型' } catch { modelStatus.value = '设置默认模型失败' } }
async function toggleModel(item) { try { await apiUpdateAgentModelConnection(item.id, { enabled: !item.enabled, revision: item.revision }); await loadModels(); modelStatus.value = item.enabled ? '模型已禁用' : '模型已启用' } catch (error) { modelStatus.value = error?.response?.data?.detail || '模型状态更新失败' } }
async function removeModel(id = selectedModelId.value, name = '此模型配置') { if (!window.confirm(`删除“${name}”？`)) return; try { await apiDeleteAgentModelConnection(id); await loadModels(); modelStatus.value = '模型配置已删除' } catch { modelStatus.value = '删除模型配置失败' } }
async function loadMcpTokens() { const request = ++mcpTokenRequest; mcpTokenLoading.value = true; try { const [page, books] = await Promise.all([apiListMcpTokens({ page: mcpTokenPage.value, pageSize: mcpTokenPageSize.value, status: mcpTokenStatus.value }), apiListLedgerBooks()]); if (request !== mcpTokenRequest) return; const result = page?.items ? page : { items: page || [], total: (page || []).length, totalPages: 1 }; mcpTokens.value = result.items || []; mcpTokenTotal.value = Number(result.total || 0); mcpTokenPages.value = Math.max(1, Number(result.totalPages || 1)); ledgerBooks.value = books || [] } catch { if (request === mcpTokenRequest) message.error('MCP Token 列表加载失败') } finally { if (request === mcpTokenRequest) mcpTokenLoading.value = false } }
async function loadMcpGrants() { try { mcpGrants.value = await apiListMcpOAuthGrants() } catch (error) { if (error?.response?.status !== 404) message.error('OAuth 授权列表加载失败') } }
async function loadMcpOperations() { const request = ++mcpEventRequest; mcpEventLoading.value = true; try { const [diagnostics, clients, events] = await Promise.all([apiGetMcpDiagnostics(), apiListMcpOAuthClients(), apiListMcpProtocolEvents({ page: mcpEventPage.value, pageSize: mcpEventPageSize.value, status: mcpEventStatus.value === 'ALL' ? undefined : mcpEventStatus.value })]); if (request !== mcpEventRequest) return; const result = events?.items ? events : { items: events || [], total: (events || []).length, totalPages: 1 }; mcpDiagnostics.value = diagnostics; mcpClients.value = clients || []; mcpEvents.value = result.items || []; mcpEventTotal.value = Number(result.total || 0); mcpEventPages.value = Math.max(1, Number(result.totalPages || 1)) } catch { if (request === mcpEventRequest) message.error('MCP 连接诊断加载失败') } finally { if (request === mcpEventRequest) mcpEventLoading.value = false } }
function resetMcpTokenPage() { mcpTokenPage.value = 0; loadMcpTokens() }
function changeMcpTokenPage(delta) { mcpTokenPage.value = Math.max(0, mcpTokenPage.value + delta); loadMcpTokens() }
function resetMcpEventPage() { mcpEventPage.value = 0; loadMcpOperations() }
function changeMcpEventPage(delta) { mcpEventPage.value = Math.max(0, mcpEventPage.value + delta); loadMcpOperations() }
async function loadOperations() {
  const request = ++operationsRequest
  operationsLoading.value = true; operationsError.value = ''
  resetCalls()
  try {
    const [metrics, budget, alerts] = await Promise.all([apiGetAgentOperationMetrics(operationsQuery.value), apiGetAgentUsageBudget(), apiListAgentBudgetAlerts()])
    if (request !== operationsRequest) return
    operationsMetrics.value = metrics
    budgetAlerts.value = alerts || []
    budgetForm.value = { ...budget }
  } catch (error) { if (request === operationsRequest) operationsError.value = error?.response?.data?.detail || '运行质量数据加载失败' }
  finally { if (request === operationsRequest) operationsLoading.value = false }
}
async function markBudgetAlertRead(alert) { try { await apiMarkAgentBudgetAlertRead(alert.id); alert.status = 'READ' } catch { message.error('告警已读状态保存失败') } }
function resetCalls() { callsPage.value = 0; return loadCalls() }
async function loadCalls() {
  const request = ++callsRequest
  expandedTurnId.value = ''; ++traceRequest; callTrace.value = null
  callsLoading.value = true; callsError.value = ''; operationCalls.value = []
  try {
    const rows = await apiListAgentOperationCalls({ ...operationsQuery.value, failuresOnly: failuresOnly.value, page: callsPage.value, pageSize: callsPageSize.value })
    if (request !== callsRequest) return
    const result = rows?.items ? rows : { items: rows || [], page: callsPage.value, pageSize: callsPageSize.value, total: (rows || []).length, totalPages: (rows || []).length > callsPageSize.value ? callsPage.value + 2 : callsPage.value + 1 }
    operationCalls.value = result.items || []; callsTotal.value = Number(result.total || 0); callsPages.value = Math.max(1, Number(result.totalPages || 1)); callsHasNext.value = callsPage.value + 1 < callsPages.value
  } catch (error) { if (request === callsRequest) { callsHasNext.value = false; callsError.value = error?.response?.data?.detail || '调用列表加载失败' } }
  finally { if (request === callsRequest) callsLoading.value = false }
}
function changeCallsPage(delta) { callsPage.value += delta; loadCalls() }
async function toggleCallTrace(turnId) {
  const request = ++traceRequest
  if (expandedTurnId.value === turnId) { expandedTurnId.value = ''; return }
  expandedTurnId.value = turnId; callTrace.value = null; traceError.value = ''; traceLoading.value = true
  try { const result = await apiGetAgentOperationCallTrace(turnId); if (request === traceRequest) callTrace.value = result }
  catch (error) { if (request === traceRequest) traceError.value = error?.response?.data?.detail || '明细加载失败' }
  finally { if (request === traceRequest) traceLoading.value = false }
}
async function saveBudget() { try { const budget = await apiSaveAgentUsageBudget(budgetForm.value); budgetForm.value.revision = budget.revision; budgetStatus.value = '预算已保存'; await loadOperations() } catch (error) { budgetStatus.value = error?.response?.data?.detail || '预算保存失败' } }
async function createMcpToken() {
  creatingMcpToken.value = true
  try {
    const result = await apiCreateMcpToken({ ...mcpForm.value, expiresAt: mcpForm.value.expiresAt ? new Date(mcpForm.value.expiresAt).toISOString() : null })
    createdMcpToken.value = result.rawToken
    mcpTokenDialogOpen.value = false
    mcpForm.value = { name: '本地 Agent', permissionTemplate: mcpForm.value.permissionTemplate, highRiskPolicy: mcpForm.value.highRiskPolicy, rateLimitPerMinute: mcpForm.value.rateLimitPerMinute, scopes: [...mcpForm.value.scopes], bookIds: [], expiresAt: defaultMcpExpiry() }
    await loadMcpTokens()
    message.success('MCP Token 已创建，请立即复制')
  } catch (error) { message.error(error?.response?.data?.detail || '创建 MCP Token 失败') }
  finally { creatingMcpToken.value = false }
}
async function copyMcpToken() { try { await navigator.clipboard.writeText(createdMcpToken.value); message.success('Token 已复制') } catch { message.warning('复制失败，请手动复制') } }
async function revokeMcpToken(token) { if (!window.confirm(`撤销“${token.name}”？已连接的外部 Agent 将立即失效。`)) return; try { await apiRevokeMcpToken(token.id); await loadMcpTokens(); message.success('Token 已撤销') } catch { message.error('撤销 Token 失败') } }
async function purgeMcpToken(token) {
  if (!window.confirm(`永久删除已撤销的“${token.name}”？凭据无法恢复，但历史调用审计会保留。`)) return
  try {
    await apiPurgeMcpToken(token.id)
    if (mcpTokens.value.length === 1 && mcpTokenPage.value > 0) mcpTokenPage.value -= 1
    await loadMcpTokens()
    message.success('已撤销凭据已删除')
  } catch (error) { message.error(error?.response?.data?.detail || '删除凭据失败') }
}
async function revokeMcpGrant(grant) { if (!window.confirm(`撤销“${grant.clientName}”的 OAuth 授权？该客户端需要重新授权才能连接。`)) return; try { await apiRevokeMcpOAuthGrant(grant.id); await loadMcpGrants(); message.success('OAuth 授权已撤销') } catch { message.error('撤销 OAuth 授权失败') } }
async function disconnectMcpClient(client) { if (!window.confirm(`断开“${client.clientName}”？该客户端的全部授权和 Token 将立即失效。`)) return; try { await apiDisconnectMcpOAuthClient(client.clientId); await Promise.all([loadMcpGrants(), loadMcpOperations()]); message.success('OAuth 客户端已断开') } catch { message.error('断开 OAuth 客户端失败') } }
async function copyMcpDiagnostics() { const report = { generatedAt: new Date().toISOString(), ...mcpDiagnostics.value, clients: mcpClients.value.map(({ clientId, clientName, lastUsedAt, lastUserAgent, revokedAt }) => ({ clientId, clientName, lastUsedAt, lastUserAgent, revokedAt })), recentEvents: mcpEvents.value }; try { await navigator.clipboard.writeText(JSON.stringify(report, null, 2)); message.success('脱敏诊断已复制') } catch { message.warning('复制失败') } }
function formatDateTime(value) { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) }
function formatNumber(value) { return new Intl.NumberFormat('zh-CN').format(Number(value || 0)) }
function formatDuration(value) { if (value == null) return '—'; const ms = Number(value || 0); return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(2)} s` }
function formatMoney(value, currency = 'CNY') { if (value == null) return '—'; return `${currency} ${Number(value).toFixed(4)}` }
function formatBudgetProgress(value) { return value == null ? '未设置预算' : `已使用 ${Number(value).toFixed(1)}%` }
function mcpScopeLabel(value) { return ({ 'mcp:ledger:read': '账本查询', 'mcp:worktime:read': '工时查询', 'mcp:ledger:prepare': '账本准备', 'mcp:worktime:prepare': '工时准备', 'mcp:ledger:commit': '账本低风险提交', 'mcp:worktime:commit': '工时低风险提交' })[value] || value }
function mcpTemplateLabel(value) { return ({ READ_ONLY: '只读', PREPARE: '可准备', COMMIT: '可提交', FULL_WORKSPACE: '完整工作台' })[value] || value }
function mcpEventLabel(value) { return ({ 'client.register': '客户端注册', 'authorization.approved': '授权通过', 'authorization.denied': '授权拒绝', 'code.exchanged': '授权码交换', 'token.refreshed': 'Token 刷新', 'token.revoked': 'Token 撤销', 'grant.revoked': '授权撤销', 'client.disconnected': '客户端断开', 'mcp.request': 'MCP 请求' })[value] || value }
async function logout() {
  if (!window.confirm('确定退出当前账号？')) return
  await appStore.logout()
}
onMounted(() => Promise.all([loadModels(), loadMcpTokens(), loadMcpGrants(), loadMcpOperations(), loadOperations()]))
</script>
