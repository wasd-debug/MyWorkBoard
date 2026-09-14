<template>
  <section ref="reportExportRef" class="ledger-reports">
    <header class="report-tabbar">
      <nav class="report-tabs" aria-label="已添加报表">
        <div
          v-for="item in visibleReportOptions"
          :key="item.value"
          class="report-tab-item"
          :class="{ active: selectedReport === item.value, dragging: draggingReportKey === item.value, 'drag-over': dragOverReportKey === item.value && draggingReportKey !== item.value }"
          draggable="true"
          @dragstart="handleReportDragStart(item.value, $event)"
          @dragover.prevent="handleReportDragOver(item.value, $event)"
          @drop.prevent="handleReportDrop(item.value, $event)"
          @dragend="handleReportDragEnd"
        >
          <button type="button" class="report-tab-label" @click="selectedReport = item.value">{{ item.label }}</button>
          <button type="button" class="report-tab-remove" :aria-label="`从标签栏移除${item.label}`" @click.stop="removeReportTab(item.value)">
            <Close />
          </button>
        </div>
      </nav>
      <div ref="libraryRef" class="report-library-anchor">
        <button type="button" class="report-library-button" aria-label="报表库" title="报表库" :aria-expanded="libraryOpen" @click="toggleLibrary">
          <Grid />
        </button>
        <div v-if="libraryOpen" class="report-library-popover">
          <div class="library-popover-head">
            <div><b>报表库</b><span>选择要显示在顶部的报表</span></div>
            <button type="button" aria-label="关闭报表库" @click="libraryOpen = false"><Close /></button>
          </div>
          <div class="library-grid">
            <button
              v-for="item in reportOptions"
              :key="item.value"
              type="button"
              class="library-report-item"
              :class="{ added: reportTabKeys.has(item.value) }"
              @click="toggleReportTab(item.value)"
            >
              <span><b>{{ item.label }}</b><small>{{ item.description }}</small></span>
              <span
                class="library-report-action"
                :aria-label="reportTabKeys.has(item.value) ? '移除报表' : '添加报表'"
                :title="reportTabKeys.has(item.value) ? '移除报表' : '添加报表'"
              >
                <Remove v-if="reportTabKeys.has(item.value)" aria-hidden="true" />
                <Plus v-else aria-hidden="true" />
              </span>
            </button>
          </div>
        </div>
      </div>
    </header>

    <div class="report-content-heading">
      <div>
        <h1>{{ activeReport.label }}</h1>
        <span>{{ ledger.currentBook?.name || '当前账本' }} · {{ reportRows.length }} 笔流水</span>
      </div>
      <div class="report-heading-actions">
        <LedgerActionIcon action="download" label="导出当前报表图片" :disabled="reportExporting" @click="exportReportImage" />
        <LedgerActionIcon action="pdf" label="导出当前报表 PDF" :disabled="reportExporting" @click="exportReportPdf" />
      </div>
      <div ref="datePickerRef" class="date-picker-anchor">
        <div class="date-trigger">
          <button type="button" :aria-label="rangeScope === 'year' ? '上一年' : rangeScope === 'custom' ? '上一个同长度周期' : '上个月'" @click="shiftPeriod(-1)"><ArrowLeft /></button>
          <button type="button" class="date-trigger-label" :aria-expanded="datePickerOpen" @click="toggleDatePicker">{{ displayPeriodLabel }}</button>
          <button type="button" :aria-label="rangeScope === 'year' ? '下一年' : rangeScope === 'custom' ? '下一个同长度周期' : '下个月'" @click="shiftPeriod(1)"><ArrowRight /></button>
        </div>
        <div v-if="datePickerOpen" class="date-picker-popover">
          <div class="date-mode-switch">
            <button type="button" :class="{ active: pickerMode === 'year' }" @click="setPickerMode('year')">按年</button>
            <button type="button" :class="{ active: pickerMode === 'month' }" @click="setPickerMode('month')">按月</button>
            <button type="button" :class="{ active: pickerMode === 'custom' }" @click="setPickerMode('custom')">自定义</button>
          </div>
          <template v-if="pickerMode === 'custom'">
            <div class="custom-date-range">
              <label>开始日期<input v-model="draftCustomFrom" type="date" /></label>
              <span aria-hidden="true">—</span>
              <label>结束日期<input v-model="draftCustomTo" type="date" /></label>
            </div>
            <div class="custom-date-actions">
              <button type="button" @click="cancelCustomRange">取消</button>
              <button type="button" class="primary" :disabled="!validCustomRange" @click="applyCustomRange">应用范围</button>
            </div>
          </template>
          <template v-else-if="pickerMode === 'year'">
            <div class="picker-period-head">
              <button type="button" aria-label="上一个十年" @click="decadeStart -= 10"><ArrowLeft /></button>
              <b>{{ decadeStart }}–{{ decadeStart + 9 }}年</b>
              <button type="button" aria-label="下一个十年" @click="decadeStart += 10"><ArrowRight /></button>
            </div>
            <div class="picker-year-grid">
              <button v-for="year in pickerYears" :key="year" type="button" :class="{ active: rangeScope === 'year' && period === String(year), current: year === nowYear }" @click="selectYear(year)">
                {{ year === nowYear ? '本年' : year }}
              </button>
            </div>
          </template>
          <template v-else>
            <div class="picker-period-head">
              <button type="button" aria-label="上一年" @click="monthPickerYear--"><ArrowLeft /></button>
              <b>{{ monthPickerYear }}年</b>
              <button type="button" aria-label="下一年" @click="monthPickerYear++"><ArrowRight /></button>
            </div>
            <div class="picker-month-grid">
              <button v-for="month in 12" :key="month" type="button" :class="{ active: period === `${monthPickerYear}-${String(month).padStart(2, '0')}` }" @click="selectMonth(month)">
                {{ month }}月
              </button>
            </div>
          </template>
        </div>
      </div>
    </div>

    <LoadingOverlay :open="reportsLoading || aiLoading || reportExporting" :label="aiLoading ? '正在等待大模型分析月报…' : reportExporting ? '正在生成报表文件…' : '正在加载报表…'" />
    <Transition name="report-panel" mode="out-in">
    <template v-if="selectedReport === 'basic'">
      <div class="basic-dashboard-grid">
        <div class="basic-report-column">
          <Card class="flow-overview-card">
            <div class="flow-summary-banner">
              <span>账本流水统计</span>
              <small>结余</small>
              <strong :class="summary.net >= 0 ? 'income' : 'expense'">¥{{ money(summary.net) }}</strong>
              <div class="flow-summary-values">
                <span>总收入 <b class="income">¥{{ money(summary.income) }}</b></span>
                <i></i>
                <span>总支出 <b class="expense">¥{{ money(summary.expense) }}</b></span>
              </div>
            </div>
            <div class="milestone-row">
              <span class="milestone-icon">▥</span>
              <b>记账里程碑</b>
              <span>记账笔数 <strong>{{ reportRows.length }}</strong></span>
            </div>
          </Card>

          <Card class="ranking-card">
            <template #header>
              <div class="ranking-card-head">
                <h2>支出分布</h2>
                <div class="level-switch" aria-label="支出分类层级">
                  <button type="button" :class="{ active: expenseCategoryLevel === 'primary' }" @click="expenseCategoryLevel = 'primary'">一级</button>
                  <button type="button" :class="{ active: expenseCategoryLevel === 'secondary' }" @click="expenseCategoryLevel = 'secondary'">二级</button>
                </div>
              </div>
            </template>
            <CategoryRanking :rows="basicExpenseCategoryRows" kind="EXPENSE" :level="expenseCategoryLevel" />
          </Card>

          <ReportCard title="资产类账户统计" :subtitle="`资产 ¥${money(totalAssets)}`">
            <LedgerReportChart :option="assetAccountOption" aria-label="资产类账户统计图" height="300px" @chart-click="openEntityTransactions('account', $event.data)" />
          </ReportCard>
        </div>

        <div class="basic-report-column">
          <Card class="ranking-card income-ranking-card">
            <template #header>
              <div class="ranking-card-head">
                <h2>收入来源</h2>
                <div class="level-switch" aria-label="收入分类层级">
                  <button type="button" :class="{ active: incomeCategoryLevel === 'primary' }" @click="incomeCategoryLevel = 'primary'">一级</button>
                  <button type="button" :class="{ active: incomeCategoryLevel === 'secondary' }" @click="incomeCategoryLevel = 'secondary'">二级</button>
                </div>
              </div>
            </template>
            <CategoryRanking :rows="basicIncomeCategoryRows" kind="INCOME" :level="incomeCategoryLevel" />
          </Card>

          <ReportCard :title="trendGranularity === 'month' ? '月度收支趋势' : '每日收支趋势'" subtitle="收入与支出">
            <LedgerReportChart :option="basicCashflowTrendOption" aria-label="收支趋势图" height="430px" />
          </ReportCard>

          <ReportCard title="负债类账户统计" :subtitle="`负债 ¥${money(totalLiabilities)}`">
            <LedgerReportChart :option="liabilityAccountOption" aria-label="负债类账户统计图" height="300px" @chart-click="openEntityTransactions('account', $event.data)" />
          </ReportCard>
        </div>
      </div>
    </template>

    <template v-else-if="selectedReport === 'category'">
      <div class="category-statistics-grid">
        <Card class="category-statistics-card">
          <template #header>
            <div class="category-statistics-head">
              <h2>支出分类统计</h2>
              <div class="category-statistics-total">
                <span>总支出 <strong class="expense">¥{{ money(summary.expense) }}</strong></span>
                <span>记账笔数 <b>{{ summary.expenseCount }}</b></span>
              </div>
            </div>
            <div class="category-level-row">
              <span>分类层级</span>
              <div class="level-switch" aria-label="支出分类层级">
                <button type="button" :class="{ active: expenseCategoryLevel === 'primary' }" @click="expenseCategoryLevel = 'primary'">一级分类</button>
                <button type="button" :class="{ active: expenseCategoryLevel === 'secondary' }" @click="expenseCategoryLevel = 'secondary'">二级分类</button>
              </div>
            </div>
          </template>
          <LedgerReportChart
            :option="categoryExpenseDonutOption"
            aria-label="支出分类占比图，点击分类查看流水明细"
            height="350px"
            @chart-click="openCategoryTransactions('EXPENSE', expenseCategoryLevel, $event.data)"
          />
          <CategoryRanking
            :rows="basicExpenseCategoryRows"
            kind="EXPENSE"
            :level="expenseCategoryLevel"
            clickable
            @select="openCategoryTransactions('EXPENSE', expenseCategoryLevel, $event)"
          />
        </Card>

        <Card class="category-statistics-card">
          <template #header>
            <div class="category-statistics-head">
              <h2>收入分类统计</h2>
              <div class="category-statistics-total">
                <span>总收入 <strong class="income">¥{{ money(summary.income) }}</strong></span>
                <span>收入笔数 <b>{{ summary.incomeCount }}</b></span>
              </div>
            </div>
            <div class="category-level-row">
              <span>分类层级</span>
              <div class="level-switch" aria-label="收入分类层级">
                <button type="button" :class="{ active: incomeCategoryLevel === 'primary' }" @click="incomeCategoryLevel = 'primary'">一级分类</button>
                <button type="button" :class="{ active: incomeCategoryLevel === 'secondary' }" @click="incomeCategoryLevel = 'secondary'">二级分类</button>
              </div>
            </div>
          </template>
          <LedgerReportChart
            :option="categoryIncomeDonutOption"
            aria-label="收入分类占比图，点击分类查看流水明细"
            height="350px"
            @chart-click="openCategoryTransactions('INCOME', incomeCategoryLevel, $event.data)"
          />
          <CategoryRanking
            :rows="basicIncomeCategoryRows"
            kind="INCOME"
            :level="incomeCategoryLevel"
            clickable
            @select="openCategoryTransactions('INCOME', incomeCategoryLevel, $event)"
          />
        </Card>
      </div>
    </template>

    <template v-else-if="selectedReport === 'account'">
      <div class="account-dashboard-grid">
        <Card class="account-overview-card">
          <div class="account-summary-banner">
            <span>账户统计</span>
            <small>净资产</small>
            <strong :class="periodNetAssets >= 0 ? 'income' : 'expense'">¥{{ money(periodNetAssets) }}</strong>
            <div class="account-summary-values">
              <span>资产 <b>¥{{ money(periodTotalAssets) }}</b></span>
              <i></i>
              <span>负债 <b>¥{{ money(periodTotalLiabilities) }}</b></span>
            </div>
          </div>
          <div class="account-trend-panel">
            <LedgerReportChart :option="netAssetTrendOption" aria-label="净资产趋势图" height="210px" />
          </div>
        </Card>

        <Card class="account-ranking-card asset-ranking-card">
          <template #header>
            <div class="account-ranking-head">
              <h2>资产明细</h2>
              <span>资产 <strong>¥{{ money(periodTotalAssets) }}</strong><small>{{ periodBalanceScopeLabel }}</small></span>
            </div>
          </template>
          <AccountRanking :rows="periodAssetAccounts" kind="asset" clickable @select="openEntityTransactions('account', $event)" />
        </Card>

        <Card class="account-ranking-card liability-ranking-card">
          <template #header>
            <div class="account-ranking-head">
              <h2>负债明细</h2>
              <span>负债 <strong>¥{{ money(periodTotalLiabilities) }}</strong></span>
            </div>
          </template>
          <AccountRanking :rows="periodLiabilityAccounts" kind="liability" clickable @select="openEntityTransactions('account', $event)" />
        </Card>
      </div>
    </template>

    <template v-else-if="selectedReport === 'merchant'">
      <div class="merchant-dashboard-grid">
        <Card class="merchant-summary-card">
          <template #header><h2>商家收支统计</h2></template>
          <div class="merchant-summary-content">
            <div class="merchant-summary-identity">
              <LedgerResourceIcon :icon="merchantHeadline.icon" type="merchant" :color="merchantHeadline.color" />
              <span><b>{{ merchantHeadline.name }}</b><small>商家名称</small></span>
            </div>
            <div class="merchant-summary-values">
              <span>总收入 <strong class="income">¥{{ money(summary.income) }}</strong></span>
              <span>总支出 <strong class="expense">¥{{ money(summary.expense) }}</strong></span>
            </div>
          </div>
        </Card>

        <Card class="merchant-distribution-card merchant-expense-card">
          <template #header>
            <div class="merchant-distribution-head">
              <h2>商家支出分布</h2>
              <div>
                <span>总支出 <strong class="expense">¥{{ money(summary.expense) }}</strong></span>
                <span>记账笔数 <b>{{ summary.expenseCount }}</b></span>
              </div>
            </div>
          </template>
          <LedgerReportChart :option="merchantExpenseDonutOption" aria-label="商家支出分布图" height="390px" @chart-click="openEntityTransactions('merchant', $event.data)" />
          <MerchantRanking :rows="merchantExpenseRows" kind="expense" clickable @select="openEntityTransactions('merchant', $event)" />
        </Card>

        <Card class="merchant-distribution-card merchant-income-card">
          <template #header>
            <div class="merchant-distribution-head">
              <h2>商家收入分布</h2>
              <div>
                <span>总收入 <strong class="income">¥{{ money(summary.income) }}</strong></span>
                <span>收入笔数 <b>{{ summary.incomeCount }}</b></span>
              </div>
            </div>
          </template>
          <LedgerReportChart :option="merchantIncomeDonutOption" aria-label="商家收入分布图" height="390px" @chart-click="openEntityTransactions('merchant', $event.data)" />
          <MerchantRanking :rows="merchantIncomeRows" kind="income" clickable @select="openEntityTransactions('merchant', $event)" />
        </Card>
      </div>
    </template>

    <template v-else-if="selectedReport === 'month'">
      <div class="month-dashboard-grid">
        <div class="month-column">
          <Card class="month-summary-card">
            <div class="month-summary-banner">
              <div class="month-summary-head">
                <h2>月度小结</h2>
                <LedgerActionIcon action="ai" :label="aiLoading ? '分析中' : 'DeepSeek 分析'" :disabled="aiLoading || !ledger.online || rangeScope !== 'month'" @click="analyzeMonth" />
              </div>
              <small>结余</small>
              <strong :class="summary.net >= 0 ? 'income' : 'expense'">¥{{ money(summary.net) }}</strong>
              <div class="month-summary-values">
                <span>总支出 <b class="expense">¥{{ money(summary.expense) }}</b></span>
                <i></i>
                <span>总收入 <b class="income">¥{{ money(summary.income) }}</b></span>
              </div>
            </div>
            <div class="month-narrative">
              <h3>{{ aiAnalysis?.headline || `${ledger.currentBook?.name || '当前账本'}的${displayPeriodLabel}` }}</h3>
              <p>{{ aiAnalysis?.summary || monthNarrative }}</p>
              <div v-if="aiAnalysis?.suggestions?.length" class="ai-advice-list">
                <span v-for="item in aiAnalysis.suggestions" :key="item">{{ item }}</span>
              </div>
              <p v-if="aiError" class="month-ai-error">{{ aiError }}</p>
              <small v-else-if="rangeScope !== 'month'">切换到按月查看后可使用 DeepSeek 月度分析</small>
              <small v-else-if="!ledger.online">离线状态下暂不可调用 DeepSeek，不影响本地报表</small>
            </div>
          </Card>

          <Card class="month-top-card">
            <template #header><h2>最大的3笔支出</h2></template>
            <p>本期最大一笔支出 <b class="expense">¥{{ money(topExpenses[0]?.amount) }}</b>，可从账户、分类或商家继续查看流水。</p>
            <TransactionSpotlight :rows="topExpenses" kind="expense" @select-entity="openEntityTransactions($event.type, $event.item)" />
          </Card>

          <Card class="month-top-card">
            <template #header><h2>最高的3笔收入</h2></template>
            <p>本期最高一笔收入 <b class="income">¥{{ money(topIncomes[0]?.amount) }}</b>，点击实体可查看对应明细。</p>
            <TransactionSpotlight :rows="topIncomes" kind="income" @select-entity="openEntityTransactions($event.type, $event.item)" />
          </Card>

          <ReportCard :title="trendGranularity === 'month' ? '月度收支趋势图' : '每日收支趋势图'" :subtitle="rangeLabel">
            <LedgerReportChart :option="periodTrendOption" aria-label="月报收支趋势图" height="360px" />
          </ReportCard>
        </div>

        <div class="month-column">
          <Card class="month-compare-card">
            <div class="month-compare-row">
              <span><i class="expense-dot">↓</i><b>{{ rangeScope === 'year' ? '本年支出' : rangeScope === 'custom' ? '所选范围支出' : '本月支出' }}</b></span>
              <span>总支出 <strong class="expense">¥{{ money(summary.expense) }}</strong><small>{{ comparisonText(summary.expense, previousSummary.expense) }}</small></span>
            </div>
            <div class="month-compare-row">
              <span><i class="income-dot">↑</i><b>{{ rangeScope === 'year' ? '本年收入' : rangeScope === 'custom' ? '所选范围收入' : '本月收入' }}</b></span>
              <span>总收入 <strong class="income">¥{{ money(summary.income) }}</strong><small>{{ comparisonText(summary.income, previousSummary.income) }}</small></span>
            </div>
          </Card>

          <Card class="month-distribution-card">
            <template #header><h2>支出分布</h2></template>
            <LedgerReportChart :option="monthExpenseDonutOption" aria-label="月报支出分布图" height="360px" @chart-click="openCategoryTransactions('EXPENSE', 'primary', $event.data)" />
            <CategoryRanking :rows="monthExpenseRows" kind="EXPENSE" level="primary" clickable :limit="5" @select="openCategoryTransactions('EXPENSE', 'primary', $event)" />
          </Card>

          <Card class="month-distribution-card">
            <template #header><h2>收入来源</h2></template>
            <LedgerReportChart :option="monthIncomeDonutOption" aria-label="月报收入来源图" height="340px" @chart-click="openCategoryTransactions('INCOME', 'primary', $event.data)" />
            <CategoryRanking :rows="monthIncomeRows" kind="INCOME" level="primary" clickable :limit="5" @select="openCategoryTransactions('INCOME', 'primary', $event)" />
          </Card>
        </div>
      </div>
    </template>

    <template v-else-if="selectedReport === 'income-category'">
      <div class="management-dashboard-grid">
        <div class="statistics-column">
          <Card class="statistics-distribution-card">
            <template #header>
              <div class="statistics-card-head">
                <h2>收入统计及分布</h2>
                <span>总收入 <strong class="income">¥{{ money(summary.income) }}</strong></span>
              </div>
            </template>
            <LedgerReportChart :option="incomeManagementCategoryOption" aria-label="收入分类分布图" height="330px" @chart-click="openCategoryTransactions('INCOME', 'primary', $event.data)" />
            <CategoryRanking :rows="incomeManagementCategoryRows" kind="INCOME" level="primary" clickable :limit="8" @select="openCategoryTransactions('INCOME', 'primary', $event)" />
          </Card>

          <Card class="statistics-count-card">
            <template #header>
              <div class="statistics-card-head">
                <h2>销售单量（客户明细）</h2>
                <span>收入笔数 <strong>{{ summary.incomeCount }}</strong></span>
              </div>
            </template>
            <LedgerReportChart :option="incomeMerchantCountOption" aria-label="销售单量客户明细图" height="280px" @chart-click="openEntityTransactions('merchant', $event.data)" />
          </Card>
        </div>

        <div class="statistics-column">
          <Card class="statistics-distribution-card">
            <template #header>
              <div class="statistics-card-head">
                <h2>销售收入（客户明细）</h2>
                <span>总收入 <strong class="income">¥{{ money(summary.income) }}</strong></span>
              </div>
            </template>
            <LedgerReportChart :option="merchantIncomeDonutOption" aria-label="销售收入客户分布图" height="330px" @chart-click="openEntityTransactions('merchant', $event.data)" />
            <MerchantRanking :rows="merchantIncomeRows" kind="income" clickable @select="openEntityTransactions('merchant', $event)" />
          </Card>

          <Card class="statistics-debt-card">
            <template #header>
              <div class="statistics-card-head">
                <h2>当期应收款项统计</h2>
                <span>余额 <strong>¥{{ money(receivableSummary.balance) }}</strong></span>
              </div>
            </template>
            <div class="statistics-debt-row">
              <LedgerResourceIcon icon="cash" type="account" color="#55bfc0" />
              <b>应收款项</b>
              <span><small>余额</small><strong>¥{{ money(receivableSummary.balance) }}</strong><small>流入 ¥{{ money(receivableSummary.inflow) }}</small></span>
            </div>
          </Card>
        </div>
      </div>
    </template>

    <template v-else-if="selectedReport === 'expense-category'">
      <div class="management-dashboard-grid">
        <div class="statistics-column">
          <Card class="statistics-distribution-card">
            <template #header>
              <div class="statistics-card-head">
                <h2>成本统计及分布</h2>
                <span>总支出 <strong class="expense">¥{{ money(summary.expense) }}</strong></span>
              </div>
            </template>
            <LedgerReportChart :option="costManagementCategoryOption" aria-label="成本分类分布图" height="330px" @chart-click="openCategoryTransactions('EXPENSE', 'primary', $event.data)" />
            <CategoryRanking :rows="costManagementCategoryRows" kind="EXPENSE" level="primary" clickable :limit="8" @select="openCategoryTransactions('EXPENSE', 'primary', $event)" />
          </Card>

          <Card class="statistics-count-card">
            <template #header>
              <div class="statistics-card-head">
                <h2>支出单量（商家明细）</h2>
                <span>支出笔数 <strong>{{ summary.expenseCount }}</strong></span>
              </div>
            </template>
            <LedgerReportChart :option="expenseMerchantCountOption" aria-label="支出单量商家明细图" height="280px" @chart-click="openEntityTransactions('merchant', $event.data)" />
          </Card>

          <Card class="statistics-debt-card">
            <template #header>
              <div class="statistics-card-head">
                <h2>当期应付款项统计</h2>
                <span>余额 <strong>¥{{ money(payableSummary.balance) }}</strong></span>
              </div>
            </template>
            <div class="statistics-debt-row">
              <LedgerResourceIcon icon="bank-card" type="account" color="#55bfc0" />
              <b>应付款项</b>
              <span><small>余额</small><strong>¥{{ money(payableSummary.balance) }}</strong><small>流入 ¥{{ money(payableSummary.inflow) }}</small></span>
            </div>
          </Card>
        </div>

        <div class="statistics-column">
          <Card class="statistics-distribution-card">
            <template #header>
              <div class="statistics-card-head">
                <h2>成本统计（商家明细）</h2>
                <span>总支出 <strong class="expense">¥{{ money(summary.expense) }}</strong></span>
              </div>
            </template>
            <LedgerReportChart :option="merchantExpenseDonutOption" aria-label="成本商家分布图" height="330px" @chart-click="openEntityTransactions('merchant', $event.data)" />
            <MerchantRanking :rows="merchantExpenseRows" kind="expense" clickable @select="openEntityTransactions('merchant', $event)" />
          </Card>

          <Card class="statistics-count-card refund-count-card">
            <template #header>
              <div class="statistics-card-head">
                <h2>退款单量（商家明细）</h2>
                <span>记账笔数 <strong>{{ refundRows.length }}</strong></span>
              </div>
            </template>
            <LedgerReportChart :option="refundMerchantCountOption" aria-label="退款单量商家明细图" height="250px" @chart-click="openEntityTransactions('merchant', $event.data)" />
          </Card>
        </div>
      </div>
    </template>

    <template v-else-if="selectedReport === 'account-detail'">
      <div class="account-detail-grid">
        <div class="account-detail-column">
          <Card class="book-account-overview">
            <template #header><h2>账本账户概况</h2></template>
            <AccountOverviewRow label="账本总资产" :value="periodTotalAssets" tone="income" icon="wallet" />
            <AccountOverviewRow label="账本总负债" :value="periodTotalLiabilities" tone="expense" icon="bank-card" />
            <AccountOverviewRow label="账本净资产" :value="periodNetAssets" :tone="periodNetAssets >= 0 ? 'income' : 'expense'" icon="coin" />
            <AccountOverviewRow label="当期净流入" :value="accountBookFlow.net" :tone="accountBookFlow.net >= 0 ? 'income' : 'expense'" icon="cash" />
          </Card>

          <Card class="account-distribution-card">
            <template #header><h2>负债类账户</h2></template>
            <LedgerReportChart :option="liabilityDetailDonutOption" aria-label="负债类账户占比图" height="320px" @chart-click="openEntityTransactions('account', $event.data)" />
            <AccountRanking :rows="periodLiabilityAccounts" kind="liability" clickable @select="openEntityTransactions('account', $event)" />
          </Card>

          <Card class="debt-summary-card">
            <template #header><div><h2>当期应收款统计</h2><span>余额 <strong class="income">¥{{ money(receivableSummary.balance) }}</strong></span></div></template>
            <div class="debt-summary-row"><LedgerResourceIcon icon="cash" type="account" color="#4dbfb9" /><span><b>应收款项</b><small>借出与收债流水</small></span><span><b>余额 ¥{{ money(receivableSummary.balance) }}</b><small>流入 ¥{{ money(receivableSummary.inflow) }}</small></span></div>
          </Card>
        </div>

        <div class="account-detail-column">
          <Card class="account-distribution-card asset-detail-card">
            <template #header><h2>资产类账户</h2></template>
            <LedgerReportChart :option="assetDetailDonutOption" aria-label="资产类账户占比图" height="320px" @chart-click="openEntityTransactions('account', $event.data)" />
            <AccountRanking :rows="periodAssetAccounts" kind="asset" clickable @select="openEntityTransactions('account', $event)" />
          </Card>

          <Card class="account-flow-card">
            <template #header><h2>各账户流入流出统计</h2></template>
            <div class="account-flow-chart"><h3><i class="expense-dot">↓</i>各账户流入柱状图</h3><LedgerReportChart :option="accountInflowOption" aria-label="各账户流入柱状图" height="230px" @chart-click="openEntityTransactions('account', $event.data)" /></div>
            <div class="account-flow-chart"><h3><i class="income-dot">↑</i>各账户流出柱状图</h3><LedgerReportChart :option="accountOutflowOption" aria-label="各账户流出柱状图" height="230px" @chart-click="openEntityTransactions('account', $event.data)" /></div>
            <div class="account-flow-chart"><h3><i class="balance-dot">◇</i>各账户净流入柱状图</h3><LedgerReportChart :option="accountNetflowOption" aria-label="各账户净流入柱状图" height="230px" @chart-click="openEntityTransactions('account', $event.data)" /></div>
          </Card>

          <Card class="debt-summary-card">
            <template #header><div><h2>当期应付款统计</h2><span>余额 <strong class="expense">¥{{ money(payableSummary.balance) }}</strong></span></div></template>
            <div class="debt-summary-row"><LedgerResourceIcon icon="bank-card" type="account" color="#55bcae" /><span><b>应付款项</b><small>借入与还债流水</small></span><span><b>余额 ¥{{ money(payableSummary.balance) }}</b><small>流入 ¥{{ money(payableSummary.inflow) }}</small></span></div>
          </Card>
        </div>
      </div>
    </template>

    <template v-else-if="selectedReport === 'member'">
      <div class="member-dashboard-grid">
        <div class="statistics-column">
          <Card class="statistics-count-card member-count-card">
            <template #header>
              <div class="statistics-card-head">
                <h2>成员记账数据</h2>
                <span>记账笔数 <strong>{{ reportRows.length }}</strong></span>
              </div>
            </template>
            <LedgerReportChart :option="memberCountOption" aria-label="成员记账笔数图" height="320px" @chart-click="openEntityTransactions('member', $event.data)" />
          </Card>

          <Card class="statistics-distribution-card">
            <template #header>
              <div class="statistics-card-head">
                <h2>成员支出统计</h2>
                <span>总支出 <strong class="expense">¥{{ money(summary.expense) }}</strong></span>
              </div>
            </template>
            <LedgerReportChart :option="memberExpenseDonutOption" aria-label="成员支出统计图" height="330px" @chart-click="openEntityTransactions('member', $event.data)" />
            <ResourceRanking :rows="memberExpenseRows" type="member" clickable @select="openEntityTransactions('member', $event)" />
          </Card>
        </div>

        <div class="statistics-column">
          <Card class="cashflow-compare-card">
            <template #header><h2>成员收支对比</h2></template>
            <CashflowCompare :rows="memberRows" type="member" @select="openEntityTransactions('member', $event)" />
          </Card>

          <Card class="statistics-distribution-card member-income-card">
            <template #header>
              <div class="statistics-card-head">
                <h2>成员收入统计</h2>
                <span>总收入 <strong class="income">¥{{ money(summary.income) }}</strong></span>
              </div>
            </template>
            <LedgerReportChart :option="memberIncomeDonutOption" aria-label="成员收入统计图" height="330px" @chart-click="openEntityTransactions('member', $event.data)" />
            <ResourceRanking :rows="memberIncomeRows" type="member" clickable @select="openEntityTransactions('member', $event)" />
          </Card>
        </div>
      </div>
    </template>

    <template v-else-if="selectedReport === 'project-category'">
      <div class="project-category-dashboard-grid">
        <div class="statistics-column">
          <Card class="project-balance-card">
            <LedgerResourceIcon icon="folder" type="project" color="#6d55c4" />
            <b>按项目分类看结余</b>
            <span>结余 <strong :class="projectSummary.net >= 0 ? 'income' : 'expense'">¥{{ money(projectSummary.net) }}</strong></span>
          </Card>

          <Card class="statistics-distribution-card">
            <template #header>
              <div class="statistics-card-head">
                <h2>收入统计</h2>
                <span>总收入 <strong class="income">¥{{ money(projectSummary.income) }}</strong></span>
              </div>
            </template>
            <LedgerReportChart :option="projectIncomeDonutOption" aria-label="项目分类收入统计图" height="330px" @chart-click="openEntityTransactions('project', $event.data)" />
            <ResourceRanking :rows="projectIncomeRows" type="project" clickable @select="openEntityTransactions('project', $event)" />
          </Card>
        </div>

        <div class="statistics-column">
          <Card class="cashflow-compare-card">
            <template #header>
              <div class="statistics-card-head">
                <h2>收支对比</h2>
                <span>总收入 <strong class="income">¥{{ money(projectSummary.income) }}</strong>　总支出 <strong class="expense">¥{{ money(projectSummary.expense) }}</strong></span>
              </div>
            </template>
            <CashflowCompare :rows="projectRows" type="project" @select="openEntityTransactions('project', $event)" />
          </Card>

          <Card class="statistics-distribution-card">
            <template #header>
              <div class="statistics-card-head">
                <h2>支出统计</h2>
                <span>总支出 <strong class="expense">¥{{ money(projectSummary.expense) }}</strong></span>
              </div>
            </template>
            <LedgerReportChart :option="projectExpenseDonutOption" aria-label="项目分类支出统计图" height="330px" @chart-click="openEntityTransactions('project', $event.data)" />
            <ResourceRanking :rows="projectExpenseRows" type="project" clickable @select="openEntityTransactions('project', $event)" />
          </Card>
        </div>
      </div>
    </template>

    <template v-else-if="selectedReport === 'project'">
      <div class="project-dashboard-grid">
        <div class="statistics-column">
          <Card class="project-balance-card">
            <LedgerResourceIcon icon="folder" type="project" color="#6d55c4" />
            <b>总毛利</b>
            <span>结余 <strong :class="projectSummary.net >= 0 ? 'income' : 'expense'">¥{{ money(projectSummary.net) }}</strong></span>
          </Card>

          <Card class="project-ranking-card">
            <template #header>
              <div class="statistics-card-head">
                <h2>项目收入统计</h2>
                <span>总收入 <strong class="income">¥{{ money(projectSummary.income) }}</strong>　收入笔数 <b>{{ projectSummary.incomeCount }}</b></span>
              </div>
            </template>
            <ResourceRanking :rows="projectIncomeRows" type="project" clickable @select="openEntityTransactions('project', $event)" />
          </Card>
        </div>

        <div class="statistics-column">
          <Card class="statistics-distribution-card project-margin-card">
            <template #header><h2>项目结余（毛利）统计</h2></template>
            <LedgerReportChart :option="projectMarginDonutOption" aria-label="项目结余毛利统计图" height="330px" @chart-click="openEntityTransactions('project', $event.data)" />
            <ResourceRanking :rows="projectMarginRows" type="project" value-label="结余" clickable @select="openEntityTransactions('project', $event)" />
          </Card>

          <Card class="project-ranking-card">
            <template #header>
              <div class="statistics-card-head">
                <h2>项目支出统计</h2>
                <span>总支出 <strong class="expense">¥{{ money(projectSummary.expense) }}</strong>　记账笔数 <b>{{ projectSummary.expenseCount }}</b></span>
              </div>
            </template>
            <ResourceRanking :rows="projectExpenseRows" type="project" clickable @select="openEntityTransactions('project', $event)" />
          </Card>
        </div>
      </div>
    </template>
    </Transition>
  </section>
</template>

<script setup>
import { computed, defineComponent, h, onBeforeUnmount, onMounted, ref, TransitionGroup, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, ArrowLeft, ArrowRight, Close, Grid, Plus, Remove } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import html2canvas from 'html2canvas'
import Card from '../components/ui/Card.vue'
import Empty from '../components/ui/Empty.vue'
import Table from '../components/ui/Table.vue'
import LedgerReportChart from '../components/ledger/LedgerReportChart.vue'
import LedgerResourceIcon from '../components/ledger/LedgerResourceIcon.vue'
import LedgerActionIcon from '../components/ledger/LedgerActionIcon.vue'
import LoadingOverlay from '../components/ledger/LoadingOverlay.vue'
import { categoryColor, categorySeries, resourceSeries, stableResourceColor } from '../components/ledger/chartPalette'
import { accountBalancesAt, totalLedgerAssets } from '../components/ledger/ledgerAccounting'
import { apiLedgerMonthlyAnalysis, apiListLedgerBudgets } from '../api'
import { useLedgerStore } from '../stores/ledger'

const ledger = useLedgerStore()
const route = useRoute()
const router = useRouter()
const now = new Date()
const nowYear = now.getFullYear()
const currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
const incomeKinds = new Set(['INCOME', 'BORROW_IN', 'COLLECT_DEBT'])
const expenseKinds = new Set(['EXPENSE', 'LEND_OUT', 'REPAY_DEBT'])
const liabilityTypes = new Set(['card', 'credit', 'credit_card', 'loan'])

const reportOptions = [
  { value: 'basic', label: '基础统计', description: '汇总收支、分类、趋势、账户和预算执行。' },
  { value: 'category', label: '分类', description: '对比收入与支出分类的金额、笔数和占比。' },
  { value: 'account', label: '账户', description: '查看资产、负债、净资产和账户收支。' },
  { value: 'merchant', label: '商家', description: '分析消费商家排行、占比与单笔均值。' },
  { value: 'month', label: '月报', description: '集中查看本期收支、每日趋势、预算与大额流水。' },
  { value: 'expense-category', label: '支出分类', description: '查看指定支出分类的走势、账户与明细。' },
  { value: 'income-category', label: '收入分类', description: '查看指定收入分类的走势、账户与明细。' },
  { value: 'account-detail', label: '账户详情', description: '深入查看单个账户的余额、流入、流出和对方。' },
  { value: 'member', label: '成员', description: '比较账本成员的支出金额、笔数与占比。' },
  { value: 'project-category', label: '项目分类', description: '从项目和分类两个维度交叉汇总流水。' },
  { value: 'project', label: '项目', description: '查看指定项目的收支趋势、分类、账户和流水。' }
]
const reportKeys = new Set(reportOptions.map(item => item.value))
const selectedReport = ref(reportKeys.has(String(route.query.report)) ? String(route.query.report) : 'basic')
const rangeScope = ref(['year', 'month', 'custom'].includes(String(route.query.scope)) ? String(route.query.scope) : 'month')
const period = ref(normalizePeriod(route.query.period, rangeScope.value))
const defaultReportTabs = ['basic', 'category', 'account', 'merchant', 'month', 'expense-category', 'income-category', 'account-detail']
const visibleReportKeys = ref(loadReportTabs())
const reportTabKeys = computed(() => new Set(visibleReportKeys.value))
const visibleReportOptions = computed(() => visibleReportKeys.value
  .map(key => reportOptions.find(item => item.value === key))
  .filter(Boolean))
if (!visibleReportKeys.value.includes(selectedReport.value)) visibleReportKeys.value.push(selectedReport.value)
const libraryRef = ref(null)
const libraryOpen = ref(false)
const datePickerRef = ref(null)
const datePickerOpen = ref(false)
const pickerMode = ref(rangeScope.value)
const customFrom = ref(queryDate(route.query.from) || `${currentMonth}-01`)
const customTo = ref(queryDate(route.query.to) || todayIso())
const draftCustomFrom = ref(customFrom.value)
const draftCustomTo = ref(customTo.value)
const decadeStart = ref(Math.floor(Number(period.value.slice(0, 4)) / 10) * 10)
const pickerYears = computed(() => Array.from({ length: 10 }, (_, index) => decadeStart.value + index))
const monthPickerYear = ref(Number(period.value.slice(0, 4)))
const draggingReportKey = ref('')
const dragOverReportKey = ref('')
const expenseCategoryLevel = ref('primary')
const incomeCategoryLevel = ref('primary')
const categoryLevel = ref('primary')
const selectedCategoryId = ref('')
const selectedAccountId = ref('')
const selectedProjectId = ref('')
const remoteBudgets = ref([])
const aiAnalysis = ref(null)
const aiLoading = ref(false)
const aiError = ref('')
const reportsLoading = ref(true)
const reportExportRef = ref(null)
const reportExporting = ref(false)
let reportsLoadingSafetyTimer
let budgetRequestId = 0

function exportStyleText() {
  const rules = []
  for (const sheet of Array.from(document.styleSheets)) {
    try {
      for (const rule of Array.from(sheet.cssRules || [])) rules.push(rule.cssText)
    } catch {
      // Cross-origin stylesheets are not readable; the report still exports with local styles.
    }
  }
  const root = getComputedStyle(document.documentElement)
  const variables = []
  for (let index = 0; index < root.length; index += 1) {
    const name = root.item(index)
    if (name?.startsWith('--')) variables.push(`${name}:${root.getPropertyValue(name)};`)
  }
  return `:root{${variables.join('')}}${rules.join('\n')}`
}

function exportClone() {
  const source = reportExportRef.value
  if (!source) throw new Error('报表内容尚未准备好')
  const clone = source.cloneNode(true)
  clone.querySelector('.report-heading-actions')?.remove()
  clone.querySelectorAll('.date-picker-popover,.report-library-popover,.report-tab-remove').forEach(node => node.remove())
  const sourceCanvases = Array.from(source.querySelectorAll('canvas'))
  const cloneCanvases = Array.from(clone.querySelectorAll('canvas'))
  sourceCanvases.forEach((canvas, index) => {
    const image = document.createElement('img')
    image.src = canvas.toDataURL('image/png')
    image.width = canvas.width
    image.height = canvas.height
    image.style.cssText = `display:block;width:${canvas.clientWidth || canvas.width}px;height:${canvas.clientHeight || canvas.height}px;`
    cloneCanvases[index]?.replaceWith(image)
  })
  const rect = source.getBoundingClientRect()
  const width = Math.max(1, Math.ceil(source.scrollWidth || rect.width))
  const height = Math.max(1, Math.ceil(source.scrollHeight || rect.height))
  return { clone, width, height, css: exportStyleText() }
}

function serializeExport({ clone, width, height, css }) {
  const markup = new XMLSerializer().serializeToString(clone)
  // Keep the HTML payload in a CDATA style block. Raw CSS such as `@media` and
  // custom-property values can otherwise make the SVG image parser reject the
  // generated source in Chromium.
  const safeCss = css.replaceAll(']]>', ']]]]><![CDATA[>')
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" preserveAspectRatio="none"><foreignObject x="0" y="0" width="${width}" height="${height}"><div xmlns="http://www.w3.org/1999/xhtml" style="width:${width}px;min-height:${height}px;overflow:visible;background:var(--paper)"><style><![CDATA[${safeCss}]]></style>${markup}</div></foreignObject></svg>`
}

function loadExportImage(url) {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('报表图片无法解码，请重试'))
    image.src = url
  })
}

function legacyColorValue(value) {
  return String(value || '').replace(/color\((?:srgb|display-p3)\s+([^\)]+)\)/gi, (_, channels) => {
    const parts = channels.trim().split(/[\s/]+/).filter(Boolean)
    if (parts.length < 3) return _
    const rgb = parts.slice(0, 3).map(part => {
      const number = Number(part)
      return `${Math.round(Math.max(0, Math.min(1, number)) * 255)}`
    })
    const alpha = parts[3] == null ? 1 : Number(parts[3])
    return alpha >= 0.999 ? `rgb(${rgb.join(',')})` : `rgba(${rgb.join(',')},${Math.max(0, Math.min(1, alpha))})`
  })
}

function exportStyleValue(value) {
  const normalized = legacyColorValue(value)
  return /(?:color\(|oklch\(|oklab\(|lch\(|lab\()/i.test(normalized) ? '' : normalized
}

async function renderFlatExport(clone, width, height, background) {
  const frame = document.createElement('iframe')
  frame.setAttribute('aria-hidden', 'true')
  frame.style.cssText = `position:absolute;left:-100000px;top:0;width:${width}px;height:${height}px;border:0;visibility:hidden;`
  document.body.appendChild(frame)
  try {
    const frameDocument = frame.contentDocument
    if (!frameDocument) throw new Error('无法创建导出画布')
    frameDocument.open()
    frameDocument.write('<!doctype html><html><head><meta charset="utf-8"></head><body></body></html>')
    frameDocument.close()
    frameDocument.documentElement.style.background = background
    frameDocument.body.style.cssText = `margin:0;width:${width}px;min-height:${height}px;background:${background};overflow:visible;`
    const root = frameDocument.importNode(clone, true)
    root.style.width = `${width}px`
    root.style.minHeight = `${height}px`
    frameDocument.body.appendChild(root)
    await new Promise(resolve => frame.contentWindow.requestAnimationFrame(() => frame.contentWindow.requestAnimationFrame(resolve)))
    return await html2canvas(root, {
      backgroundColor: background,
      scale: Math.min(2, window.devicePixelRatio || 1),
      useCORS: false,
      allowTaint: false,
      logging: false,
      scrollX: 0,
      scrollY: 0,
      windowWidth: width,
      windowHeight: height
    })
  } finally {
    frame.remove()
  }
}

function downloadExport(blob, filename) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

async function exportReportImage() {
  if (reportExporting.value) return
  reportExporting.value = true
  try {
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    const source = reportExportRef.value
    if (!source) throw new Error('报表内容尚未准备好')
    const clone = source.cloneNode(true)
    clone.dataset.reportExport = 'flat'
    const sourceNodes = [source, ...source.querySelectorAll('*')]
    const cloneNodes = [clone, ...clone.querySelectorAll('*')]
    sourceNodes.forEach((node, index) => {
      const target = cloneNodes[index]
      if (!target) return
      const computed = getComputedStyle(node)
      target.style.cssText = Array.from(computed).filter(name => !name.startsWith('--')).map(name => {
        const value = exportStyleValue(computed.getPropertyValue(name))
        return value ? `${name}:${value};` : ''
      }).join('')
    })
    clone.querySelectorAll('.report-heading-actions,.date-picker-popover,.report-library-popover,.report-tab-remove').forEach(node => node.remove())
    const sourceCanvases = Array.from(source.querySelectorAll('canvas'))
    const cloneCanvases = Array.from(clone.querySelectorAll('canvas'))
    sourceCanvases.forEach((canvas, index) => {
      const image = document.createElement('img')
      image.src = canvas.toDataURL('image/png')
      image.width = canvas.width
      image.height = canvas.height
      image.style.cssText = `display:block;width:${canvas.clientWidth || canvas.width}px;height:${canvas.clientHeight || canvas.height}px;`
      cloneCanvases[index]?.replaceWith(image)
    })
    const rect = source.getBoundingClientRect()
    const width = Math.max(1, Math.ceil(source.scrollWidth || rect.width))
    const height = Math.max(1, Math.ceil(source.scrollHeight || rect.height))
    const background = getComputedStyle(document.documentElement).getPropertyValue('--paper').trim() || '#ffffff'
    const canvas = await renderFlatExport(clone, width, height, background)
    const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/png'))
    if (!blob) throw new Error('图片生成失败')
    downloadExport(blob, `ledger-report-${selectedReport.value}-${period.value}.png`)
    ElMessage.success('报表图片已生成')
  } catch (error) {
    ElMessage.error(error.message || '报表图片导出失败')
  } finally {
    reportExporting.value = false
  }
}

async function exportReportPdf() {
  if (reportExporting.value) return
  reportExporting.value = true
  try {
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    const payload = exportClone()
    const printWindow = window.open('', '_blank')
    if (!printWindow) throw new Error('浏览器阻止了打印窗口，请允许弹出窗口后重试')
    const markup = new XMLSerializer().serializeToString(payload.clone)
    printWindow.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>账本报表 ${payload.width}x${payload.height}</title><style>${payload.css}@page{size:A4;margin:12mm}html,body{margin:0;background:#fff}body{padding:0}.ledger-reports{width:100%;max-width:none;overflow:visible}.report-tabbar,.report-heading-actions,.date-picker-anchor{display:none!important}.report-content-heading{margin-bottom:12px}</style></head><body>${markup}</body></html>`)
    printWindow.document.close()
    let printed = false
    const print = () => { if (printed) return; printed = true; printWindow.focus(); printWindow.print() }
    printWindow.addEventListener('load', print, { once: true })
    window.setTimeout(print, 500)
    ElMessage.success('已打开 PDF 打印窗口，请选择“存储为 PDF”')
  } catch (error) {
    ElMessage.error(error.message || '报表 PDF 导出失败')
  } finally {
    reportExporting.value = false
  }
}

function startReportsLoading() {
  window.clearTimeout(reportsLoadingSafetyTimer)
  reportsLoading.value = true
  reportsLoadingSafetyTimer = window.setTimeout(() => {
    reportsLoading.value = false
  }, 5000)
}

function finishReportsLoading() {
  window.clearTimeout(reportsLoadingSafetyTimer)
  reportsLoading.value = false
}

const activeReport = computed(() => reportOptions.find(item => item.value === selectedReport.value) || reportOptions[0])
const displayPeriodLabel = computed(() => rangeScope.value === 'year'
  ? `${period.value}年`
  : rangeScope.value === 'custom'
    ? `${rangeDates.value.from.replaceAll('-', '/')} — ${rangeDates.value.to.replaceAll('-', '/')}`
    : `${period.value.slice(0, 4)}年${Number(period.value.slice(5))}月`)
const activeAccounts = computed(() => ledger.accounts.filter(item => !item.deleted && !item.hidden))
const activeCategories = computed(() => ledger.categories.filter(item => !item.deleted && !item.hidden))
const activeMerchants = computed(() => ledger.merchants.filter(item => !item.deleted && !item.hidden))
const activeProjects = computed(() => ledger.projects.filter(item => !item.deleted && !item.hidden))
const assetAccounts = computed(() => activeAccounts.value.filter(item => !liabilityTypes.has(item.accountType)))
const liabilityAccounts = computed(() => activeAccounts.value.filter(item => liabilityTypes.has(item.accountType)))
const selectedAccount = computed(() => activeAccounts.value.find(item => String(item.id) === selectedAccountId.value))
const selectedAccountIsLiability = computed(() => liabilityTypes.has(selectedAccount.value?.accountType))
const showsCategoryLevel = computed(() => ['project-category', 'project'].includes(selectedReport.value))
const categoryLevelLabel = computed(() => categoryLevel.value === 'primary' ? '一级分类' : '二级分类')
const rangeDates = computed(() => {
  if (rangeScope.value === 'year') return { from: `${period.value}-01-01`, to: `${period.value}-12-31` }
  if (rangeScope.value === 'custom') return { from: customFrom.value, to: customTo.value }
  return { from: `${period.value}-01`, to: endOfMonth(period.value) }
})
const rangeLabel = computed(() => rangeScope.value === 'year'
  ? `${period.value} 年 1 月 1 日 — 12 月 31 日`
  : rangeScope.value === 'custom'
    ? `${formatChineseDate(rangeDates.value.from)} — ${formatChineseDate(rangeDates.value.to)}`
  : `${Number(period.value.slice(5))} 月 1 日 — ${Number(rangeDates.value.to.slice(-2))} 日`)
const periodBalanceScopeLabel = computed(() => rangeDates.value.to >= todayIso()
  ? '当前余额口径'
  : `截至 ${formatChineseDate(rangeDates.value.to)}`)
const reportRows = computed(() => ledger.transactions.filter(item =>
  !item.deleted && String(item.occurredOn || '') >= rangeDates.value.from && String(item.occurredOn || '') <= rangeDates.value.to))
const summary = computed(() => summarize(reportRows.value))
const totalAssets = computed(() => totalLedgerAssets(activeAccounts.value, ledger.transactions))
const totalLiabilities = computed(() => liabilityAccounts.value.reduce((total, item) => total + Math.abs(Number(item.balance || 0)), 0))
const periodAccountBalances = computed(() => accountBalancesAt(activeAccounts.value, ledger.transactions, rangeDates.value.to))
const periodAssetAccounts = computed(() => accountRankingRows(periodAccountBalances.value.filter(item => !item.liability)))
const periodLiabilityAccounts = computed(() => accountRankingRows(periodAccountBalances.value.filter(item => item.liability)))
const periodTotalAssets = computed(() => totalLedgerAssets(periodAccountBalances.value, ledger.transactions, rangeDates.value.to))
const periodTotalLiabilities = computed(() => periodLiabilityAccounts.value.reduce((total, item) => total + item.amount, 0))
const periodNetAssets = computed(() => periodTotalAssets.value - periodTotalLiabilities.value)
const netAssetTrendRows = computed(() => {
  if (rangeScope.value !== 'year') {
    return [{ key: rangeDates.value.to.slice(0, 7), value: periodNetAssets.value }]
  }
  return Array.from({ length: 12 }, (_, index) => {
    const month = `${period.value}-${String(index + 1).padStart(2, '0')}`
    const balances = accountBalancesAt(activeAccounts.value, ledger.transactions, endOfMonth(month))
    const assets = totalLedgerAssets(balances, ledger.transactions, endOfMonth(month))
    const liabilities = balances.filter(item => item.liability).reduce((total, item) => total + Math.abs(item.balance), 0)
    return { key: month, value: assets - liabilities }
  })
})
const selectableCategories = computed(() => activeCategories.value.filter(item => {
  const kind = selectedReport.value === 'income-category' ? 'INCOME' : 'EXPENSE'
  if (item.kind !== kind) return false
  return categoryLevel.value === 'primary' ? !item.parentId : Boolean(item.parentId)
}))
const selectedCategoryName = computed(() => {
  const item = activeCategories.value.find(category => String(category.id) === selectedCategoryId.value)
  return item ? categoryPath(item) : `全部${selectedReport.value === 'income-category' ? '收入' : '支出'}分类`
})
const selectedProjectName = computed(() => {
  const item = activeProjects.value.find(project => String(project.id) === selectedProjectId.value)
  return item?.name || '全部项目'
})
const validCustomRange = computed(() => Boolean(queryDate(draftCustomFrom.value) && queryDate(draftCustomTo.value) && draftCustomFrom.value <= draftCustomTo.value))
const trendGranularity = computed(() => rangeScope.value === 'year' || (rangeScope.value === 'custom' && daysBetween(rangeDates.value.from, rangeDates.value.to) > 93) ? 'month' : 'day')

const expenseCategoryRows = computed(() => categoryRows('EXPENSE'))
const incomeCategoryRows = computed(() => categoryRows('INCOME'))
const basicExpenseCategoryRows = computed(() => categoryRankingRows('EXPENSE', expenseCategoryLevel.value))
const basicIncomeCategoryRows = computed(() => categoryRankingRows('INCOME', incomeCategoryLevel.value))
const merchantExpenseRows = computed(() => merchantRankingRows(reportRows.value.filter(isExpense)))
const merchantIncomeRows = computed(() => merchantRankingRows(reportRows.value.filter(isIncome)))
const merchantHeadline = computed(() => {
  const rows = [...merchantExpenseRows.value, ...merchantIncomeRows.value]
  if (!rows.length) return { name: '无商家', icon: 'other', color: '#e1b455' }
  const totals = new Map()
  rows.forEach(item => {
    const current = totals.get(item.key) || { ...item, amount: 0 }
    current.amount += item.amount
    totals.set(item.key, current)
  })
  return [...totals.values()].sort((left, right) => right.amount - left.amount)[0]
})
const memberRows = computed(() => resourceRankingRows(reportRows.value, 'member'))
const memberExpenseRows = computed(() => resourceRankingRows(reportRows.value.filter(isExpense), 'member'))
const memberIncomeRows = computed(() => resourceRankingRows(reportRows.value.filter(isIncome), 'member'))
const memberSummary = computed(() => rankingSummary(memberExpenseRows.value))
const projectTransactions = computed(() => reportRows.value)
const projectRows = computed(() => resourceRankingRows(projectTransactions.value, 'project'))
const projectIncomeRows = computed(() => resourceRankingRows(projectTransactions.value.filter(isIncome), 'project'))
const projectExpenseRows = computed(() => resourceRankingRows(projectTransactions.value.filter(isExpense), 'project'))
const projectMarginRows = computed(() => projectRows.value.filter(item => item.income || item.expense).map(item => ({ ...item, amount: Math.abs(item.net), displayAmount: item.net })))
const projectSummary = computed(() => summarize(projectTransactions.value))
const projectCategoryRows = computed(() => groupRows(
  reportRows.value.filter(item => Boolean(item.projectId || item.projectName || item.project)),
  item => `${item.projectName || item.project || resourceName(ledger.projects, item.projectId, '未指定项目')} / ${categoryDisplayName(item)}`
))
const focusedCategoryRows = computed(() => {
  const kindRows = reportRows.value.filter(selectedReport.value === 'income-category' ? isIncome : isExpense)
  if (!selectedCategoryId.value) return kindRows
  if (categoryLevel.value === 'secondary') return kindRows.filter(item => String(item.categoryId) === selectedCategoryId.value)
  return kindRows.filter(item => String(item.parentCategoryId || categoryById(item.categoryId)?.parentId || item.categoryId) === selectedCategoryId.value)
})
const focusedCategorySummary = computed(() => {
  const rows = focusedCategoryRows.value
  const amount = rows.reduce((total, item) => total + Number(item.amount || 0), 0)
  const allAmount = reportRows.value.filter(selectedReport.value === 'income-category' ? isIncome : isExpense)
    .reduce((total, item) => total + Number(item.amount || 0), 0)
  return { amount, count: rows.length, average: rows.length ? amount / rows.length : 0, share: allAmount ? amount / allAmount * 100 : 0 }
})
const flowKindLabel = computed(() => selectedReport.value === 'income-category' ? '收入' : '支出')
const flowTone = computed(() => selectedReport.value === 'income-category' ? 'income' : 'expense')
const flowKind = computed(() => selectedReport.value === 'income-category' ? 'INCOME' : 'EXPENSE')
const focusedPrimaryCategoryRows = computed(() => categoryRankingRows(flowKind.value, 'primary'))
const focusedSecondaryCategoryRows = computed(() => categoryRankingRows(flowKind.value, 'secondary'))
const accountDetailRows = computed(() => reportRows.value.filter(item => String(item.accountId) === selectedAccountId.value))
const accountDetailSummary = computed(() => summarizeAccount(accountDetailRows.value, selectedAccountId.value))
const focusedProjectRows = computed(() => {
  if (!selectedProjectId.value) return reportRows.value.filter(item => Boolean(item.projectId || item.projectName || item.project))
  return reportRows.value.filter(item => String(item.projectId) === selectedProjectId.value)
})
const focusedProjectSummary = computed(() => summarize(focusedProjectRows.value))
const coveredDays = computed(() => new Set(reportRows.value.map(item => item.occurredOn)).size)
const dailyExpenseAverage = computed(() => coveredDays.value ? summary.value.expense / coveredDays.value : 0)
const largestTransactions = computed(() => [...reportRows.value].sort((a, b) => Number(b.amount || 0) - Number(a.amount || 0)).slice(0, 12))
const topExpenses = computed(() => reportRows.value.filter(isExpense).sort((a, b) => Number(b.amount || 0) - Number(a.amount || 0)).slice(0, 3))
const topIncomes = computed(() => reportRows.value.filter(isIncome).sort((a, b) => Number(b.amount || 0) - Number(a.amount || 0)).slice(0, 3))
const previousRangeDates = computed(() => {
  if (rangeScope.value === 'year') {
    const year = String(Number(period.value) - 1)
    return { from: `${year}-01-01`, to: `${year}-12-31` }
  }
  if (rangeScope.value === 'custom') {
    const duration = daysBetween(rangeDates.value.from, rangeDates.value.to) + 1
    const previousTo = shiftDate(rangeDates.value.from, -1)
    return { from: shiftDate(previousTo, -(duration - 1)), to: previousTo }
  }
  const [year, month] = period.value.split('-').map(Number)
  const previous = new Date(year, month - 2, 1)
  const key = `${previous.getFullYear()}-${String(previous.getMonth() + 1).padStart(2, '0')}`
  return { from: `${key}-01`, to: endOfMonth(key) }
})
const previousRows = computed(() => ledger.transactions.filter(item =>
  !item.deleted && String(item.occurredOn || '') >= previousRangeDates.value.from && String(item.occurredOn || '') <= previousRangeDates.value.to))
const previousSummary = computed(() => summarize(previousRows.value))
const monthExpenseRows = computed(() => categoryRankingRows('EXPENSE', 'primary'))
const monthIncomeRows = computed(() => categoryRankingRows('INCOME', 'primary'))
const monthNarrative = computed(() => {
  if (!reportRows.value.length) return '本期还没有流水。记下第一笔后，这里会自动汇总收支、分类和账户变化。'
  const balanceText = summary.value.net >= 0
    ? `本期结余 ¥${money(summary.value.net)}，收入能够覆盖支出。`
    : `本期支出比收入多 ¥${money(Math.abs(summary.value.net))}，现金流处于净流出状态。`
  const top = monthExpenseRows.value[0]
  return `${balanceText}${top ? ` 最大支出分类是“${top.name}”，占总支出的 ${top.share.toFixed(1)}%。` : ''}`
})
const budgetRows = computed(() => remoteBudgets.value.length ? remoteBudgets.value : ledger.budgets.filter(item => item.monthKey === budgetMonth.value))
const budgetMonth = computed(() => rangeScope.value === 'month' ? period.value : rangeScope.value === 'custom' ? rangeDates.value.from.slice(0, 7) : `${period.value}-${currentMonth.slice(5)}`)
const budgetTotal = computed(() => budgetRows.value.reduce((total, item) => total + Number(item.budget || 0), 0))
const budgetSpent = computed(() => budgetRows.value.reduce((total, item) => total + Number(item.spent || 0), 0))
const budgetSubtitle = computed(() => `${budgetMonth.value.replace('-', ' 年 ')} 月 · ¥${money(budgetSpent.value)} / ¥${money(budgetTotal.value)}`)

const expenseCategoryPieOption = computed(() => pieOption(expenseCategoryRows.value, categorySeries(expenseCategoryRows.value)))
const incomeCategoryPieOption = computed(() => pieOption(incomeCategoryRows.value, categorySeries(incomeCategoryRows.value)))
const categoryExpenseDonutOption = computed(() => categoryDonutOption(basicExpenseCategoryRows.value))
const categoryIncomeDonutOption = computed(() => categoryDonutOption(basicIncomeCategoryRows.value))
const monthExpenseDonutOption = computed(() => categoryDonutOption(monthExpenseRows.value))
const monthIncomeDonutOption = computed(() => categoryDonutOption(monthIncomeRows.value))
const basicCashflowTrendOption = computed(() => trendOption(completeTimeSeries(reportRows.value)))
const netAssetTrendOption = computed(() => netWorthTrendOption(netAssetTrendRows.value))
const periodTrendOption = computed(() => trendOption(timeSeries(reportRows.value, trendGranularity.value)))
const assetAccountOption = computed(() => horizontalBarOption(accountBalanceRows(assetAccounts.value), '$income'))
const liabilityAccountOption = computed(() => horizontalBarOption(accountBalanceRows(liabilityAccounts.value), '$expense'))
const budgetOption = computed(() => {
  const total = budgetTotal.value
  const spent = budgetSpent.value
  return {
    tooltip: { trigger: 'item', valueFormatter: value => `¥${money(value)}` },
    legend: { bottom: 0, textStyle: { color: '$text' } },
    series: [{
      type: 'pie', radius: ['58%', '80%'], center: ['50%', '45%'], padAngle: 2,
      label: { show: false },
      itemStyle: { borderColor: '$card', borderWidth: 2, borderRadius: 3 },
      data: total
        ? [
            { name: spent > total ? '预算内支出' : '已支出', value: Math.min(spent, total), itemStyle: { color: spent > total ? '$expense' : '$accent' } },
            ...(spent > total ? [{ name: '超出预算', value: spent - total, itemStyle: { color: '$expense' } }] : []),
            { name: '剩余预算', value: Math.max(total - spent, 0), itemStyle: { color: '$line' } }
          ]
        : [{ name: '未设置预算', value: 1, itemStyle: { color: '$line' } }]
    }]
  }
})
const merchantExpenseDonutOption = computed(() => merchantDonutOption(merchantExpenseRows.value))
const merchantIncomeDonutOption = computed(() => merchantDonutOption(merchantIncomeRows.value))
const incomeManagementCategoryOption = computed(() => categoryDonutOption(incomeManagementCategoryRows.value))
const costManagementCategoryOption = computed(() => categoryDonutOption(costManagementCategoryRows.value))
const incomeMerchantCountOption = computed(() => merchantCountBarOption(merchantIncomeRows.value, '$income'))
const expenseMerchantCountOption = computed(() => merchantCountBarOption(merchantExpenseRows.value, '$expense'))
const refundMerchantCountOption = computed(() => merchantCountBarOption(merchantRankingRows(refundRows.value), '$accent'))
const memberCountOption = computed(() => resourceCountBarOption(memberRows.value, 'memberId'))
const memberExpenseDonutOption = computed(() => resourceDonutOption(memberExpenseRows.value, 'memberId'))
const memberIncomeDonutOption = computed(() => resourceDonutOption(memberIncomeRows.value, 'memberId'))
const projectIncomeDonutOption = computed(() => resourceDonutOption(projectIncomeRows.value, 'projectId'))
const projectExpenseDonutOption = computed(() => resourceDonutOption(projectExpenseRows.value, 'projectId'))
const projectMarginDonutOption = computed(() => resourceDonutOption(projectMarginRows.value, 'projectId'))
const memberBarOption = computed(() => horizontalBarOption(memberRows.value, '$expense'))
const memberPieOption = computed(() => pieOption(memberRows.value, resourceSeries(memberRows.value)))
const projectCategoryOption = computed(() => horizontalBarOption(projectCategoryRows.value, '$accent', 12))
const focusedCategoryTrendOption = computed(() => singleTrendOption(focusedCategoryRows.value, flowKindLabel.value, flowTone.value === 'income' ? '$income' : '$expense'))
const focusedCategoryBarOption = computed(() => horizontalBarOption(selectedReport.value === 'income-category' ? incomeCategoryRows.value : expenseCategoryRows.value, flowTone.value === 'income' ? '$income' : '$expense'))
const focusedCategoryAccountOption = computed(() => pieOption(groupRows(focusedCategoryRows.value, item => item.accountName || '未知账户')))
const accountDetailTrendOption = computed(() => trendOption(timeSeriesForAccount(accountDetailRows.value, selectedAccountId.value)))
const accountDetailCategoryOption = computed(() => horizontalBarOption(groupRows(accountDetailRows.value.filter(isExpense), categoryDisplayName), '$expense'))
const accountDetailCounterpartyOption = computed(() => pieOption(groupRows(accountDetailRows.value, item => item.merchantName || item.payee || item.targetAccountName || '其他')))
const assetDetailDonutOption = computed(() => accountDonutOption(periodAssetAccounts.value))
const liabilityDetailDonutOption = computed(() => accountDonutOption(periodLiabilityAccounts.value))
const accountFlowRows = computed(() => activeAccounts.value.map(account => {
  const flow = summarizeAccount(reportRows.value.filter(item =>
    String(item.accountId) === String(account.id) || String(item.targetAccountId) === String(account.id)), account.id)
  return { ...account, ...flow }
}).filter(item => item.income || item.expense))
const refundRows = computed(() => reportRows.value.filter(item => ['REFUND', 'REFUND_IN', 'REFUND_OUT'].includes(String(item.kind || '').toUpperCase())))
const incomeManagementCategoryRows = computed(() => categoryRankingRows('INCOME', 'primary'))
const costManagementCategoryRows = computed(() => categoryRankingRows('EXPENSE', 'primary'))
const accountBookFlow = computed(() => ({
  income: accountFlowRows.value.reduce((total, item) => total + item.income, 0),
  expense: accountFlowRows.value.reduce((total, item) => total + item.expense, 0),
  net: accountFlowRows.value.reduce((total, item) => total + item.net, 0)
}))
const receivableSummary = computed(() => {
  const lent = reportRows.value.filter(item => item.kind === 'LEND_OUT').reduce((total, item) => total + Number(item.amount || 0), 0)
  const collected = reportRows.value.filter(item => item.kind === 'COLLECT_DEBT').reduce((total, item) => total + Number(item.amount || 0), 0)
  return { balance: Math.max(0, lent - collected), inflow: collected }
})
const payableSummary = computed(() => {
  const borrowed = reportRows.value.filter(item => item.kind === 'BORROW_IN').reduce((total, item) => total + Number(item.amount || 0), 0)
  const repaid = reportRows.value.filter(item => item.kind === 'REPAY_DEBT').reduce((total, item) => total + Number(item.amount || 0), 0)
  return { balance: Math.max(0, borrowed - repaid), inflow: borrowed }
})
const accountInflowOption = computed(() => accountFlowBarOption(accountFlowRows.value, 'income', '$income'))
const accountOutflowOption = computed(() => accountFlowBarOption(accountFlowRows.value, 'expense', '$expense'))
const accountNetflowOption = computed(() => accountFlowBarOption(accountFlowRows.value, 'net', '$accent', true))
const focusedProjectTrendOption = computed(() => trendOption(timeSeries(focusedProjectRows.value, trendGranularity.value)))
const focusedProjectCategoryOption = computed(() => horizontalBarOption(groupRows(focusedProjectRows.value.filter(isExpense), categoryDisplayName), '$expense'))
const focusedProjectAccountOption = computed(() => pieOption(groupRows(focusedProjectRows.value, item => item.accountName || '未知账户')))

const SummaryCard = defineComponent({
  props: { label: String, value: [Number, String], rawValue: String, tone: String, meta: String },
  setup(props) {
    return () => h(Card, { class: 'summary-card' }, {
      header: () => h('span', { class: 'summary-label' }, props.label),
      default: () => [
        h('strong', { class: ['summary-value', props.tone] }, props.rawValue ?? `¥${money(props.value)}`),
        h('span', { class: 'summary-meta' }, props.meta)
      ]
    })
  }
})
const ReportCard = defineComponent({
  props: { title: String, subtitle: String },
  setup(props, { slots, attrs }) {
    return () => h(Card, { ...attrs, class: ['report-card', attrs.class] }, {
      header: () => h('div', { class: 'report-card-heading' }, [h('h2', props.title), h('span', props.subtitle)]),
      default: () => slots.default?.()
    })
  }
})
function rankingDetails(item, width, color, amount, tone = '') {
  return h('div', { class: 'ranking-details' }, [
    h('div', { class: 'ranking-heading' }, [
      h('b', { class: 'ranking-name', title: item.name }, item.name),
      h('span', { class: 'ranking-values' }, [
        h('small', { class: 'ranking-share' }, `${item.share.toFixed(2)}%`),
        h('i', { class: 'ranking-divider', 'aria-hidden': 'true' }),
        h('strong', { class: ['ranking-amount', tone] }, amount)
      ])
    ]),
    h('div', { class: 'ranking-track' }, [
      h('span', { style: { width: `${width}%`, background: color } })
    ])
  ])
}
const CategoryRanking = defineComponent({
  props: {
    rows: { type: Array, default: () => [] },
    kind: { type: String, default: 'EXPENSE' },
    level: { type: String, default: 'primary' },
    limit: { type: Number, default: 8 },
    clickable: Boolean
  },
  emits: ['select'],
  setup(props, { emit }) {
    return () => {
      if (!props.rows.length) return h(Empty, { description: '当前范围没有分类数据' })
      const topAmount = Math.max(...props.rows.map(item => Number(item.amount || 0)), 1)
      return h('div', { class: 'category-ranking' }, [
        ...props.rows.slice(0, props.limit).map((item, index) => h(props.clickable ? 'button' : 'div', {
          class: ['category-ranking-row', { clickable: props.clickable }],
          key: `${props.kind}-${props.level}-${item.categoryId || item.name}`,
          type: props.clickable ? 'button' : undefined,
          disabled: props.clickable && !item.categoryId,
          onClick: props.clickable && item.categoryId ? () => emit('select', item) : undefined
        }, [
          h('span', { class: 'category-rank' }, String(index + 1)),
          h(LedgerResourceIcon, {
            icon: item.icon,
            type: 'category',
            color: item.color,
            title: item.name
          }),
          rankingDetails(
            item, Math.max(3, item.amount / topAmount * 100),
            item.color || (props.kind === 'INCOME' ? 'var(--down)' : 'var(--accent)'),
            money(item.amount)
          )
        ]))
      ])
    }
  }
})
const AccountRanking = defineComponent({
  props: {
    rows: { type: Array, default: () => [] },
    kind: { type: String, default: 'asset' },
    clickable: Boolean
  },
  emits: ['select'],
  setup(props, { emit }) {
    const expanded = ref(false)
    return () => {
      if (!props.rows.length) return h(Empty, { description: props.kind === 'asset' ? '当前没有资产账户' : '当前没有负债账户' })
      const visibleRows = expanded.value ? props.rows : props.rows.slice(0, 5)
      const maxAmount = Math.max(...props.rows.map(item => item.amount), 1)
      return h('div', { class: 'account-ranking' }, [
        h(TransitionGroup, { tag: 'div', name: 'ranking-expand', class: 'account-ranking-list' }, () => visibleRows.map((item, index) => h(props.clickable ? 'button' : 'div', {
          class: ['account-ranking-row', { clickable: props.clickable }],
          key: item.id || item.name,
          type: props.clickable ? 'button' : undefined,
          onClick: props.clickable ? () => emit('select', item) : undefined
        }, [
          h('span', { class: 'account-rank' }, String(index + 1)),
          h(LedgerResourceIcon, {
            icon: item.icon,
            type: 'account',
            color: item.color,
            title: item.name
          }),
          rankingDetails(item, Math.max(2, item.amount / maxAmount * 100), item.color, money(item.amount))
        ]))),
        props.rows.length > 5
          ? h('button', {
              type: 'button',
              class: ['account-ranking-expand', { expanded: expanded.value }],
              onClick: () => { expanded.value = !expanded.value }
            }, [
              expanded.value ? '收起' : '点击展开',
              h(ArrowDown)
            ])
          : null
      ])
    }
  }
})
const MerchantRanking = defineComponent({
  props: {
    rows: { type: Array, default: () => [] },
    kind: { type: String, default: 'expense' },
    clickable: Boolean
  },
  emits: ['select'],
  setup(props, { emit }) {
    const expanded = ref(false)
    return () => {
      if (!props.rows.length) return h(Empty, { description: props.kind === 'income' ? '当前没有商家收入' : '当前没有商家支出' })
      const visibleRows = expanded.value ? props.rows : props.rows.slice(0, 5)
      const maxAmount = Math.max(...props.rows.map(item => item.amount), 1)
      return h('div', { class: 'merchant-ranking' }, [
        h(TransitionGroup, { tag: 'div', name: 'ranking-expand', class: 'merchant-ranking-list' }, () => visibleRows.map((item, index) => h(props.clickable ? 'button' : 'div', {
          class: ['merchant-ranking-row', { clickable: props.clickable }],
          key: item.key,
          type: props.clickable ? 'button' : undefined,
          disabled: props.clickable && !item.merchantId,
          onClick: props.clickable && item.merchantId ? () => emit('select', item) : undefined
        }, [
          h('span', { class: 'merchant-rank' }, String(index + 1)),
          h(LedgerResourceIcon, {
            icon: item.icon,
            type: 'merchant',
            color: item.color,
            title: item.name
          }),
          rankingDetails(item, Math.max(2, item.amount / maxAmount * 100), item.color, money(item.amount))
        ]))),
        props.rows.length > 5
          ? h('button', {
              type: 'button',
              class: ['merchant-ranking-expand', { expanded: expanded.value }],
              onClick: () => { expanded.value = !expanded.value }
            }, [
              expanded.value ? '收起' : '点击展开',
              h(ArrowDown)
            ])
          : null
      ])
    }
  }
})
const ResourceRanking = defineComponent({
  props: {
    rows: { type: Array, default: () => [] },
    type: { type: String, default: 'member' },
    valueLabel: { type: String, default: '' },
    clickable: Boolean
  },
  emits: ['select'],
  setup(props, { emit }) {
    const expanded = ref(false)
    return () => {
      if (!props.rows.length) return h(Empty, { description: props.type === 'project' ? '当前没有项目数据' : '当前没有成员数据' })
      const visibleRows = expanded.value ? props.rows : props.rows.slice(0, 5)
      const maxAmount = Math.max(...props.rows.map(item => Math.abs(Number(item.amount || item.displayAmount || 0))), 1)
      return h('div', { class: 'resource-ranking' }, [
        h(TransitionGroup, { tag: 'div', name: 'ranking-expand', class: 'resource-ranking-list' }, () => visibleRows.map((item, index) => {
          const value = Number(item.displayAmount ?? item.amount ?? 0)
          const positive = value >= 0
          const entityId = props.type === 'project' ? item.projectId : item.memberId
          const isUnassigned = !entityId
          const rowTag = props.clickable && !isUnassigned ? 'button' : 'div'
          return h(rowTag, {
            class: ['resource-ranking-row', { clickable: props.clickable && !isUnassigned }],
            key: `${props.type}-${entityId || item.name}`,
            type: rowTag === 'button' ? 'button' : undefined,
            onClick: rowTag === 'button' ? () => emit('select', item) : undefined
          }, [
            h('span', { class: 'resource-rank' }, String(index + 1)),
            h(LedgerResourceIcon, {
              icon: item.icon,
              type: props.type,
              color: item.color,
              title: item.name
            }),
            rankingDetails(
              item, Math.max(3, Math.abs(value) / maxAmount * 100),
              item.color || (positive ? 'var(--down)' : 'var(--up)'),
              `${positive ? '' : '−'}${money(Math.abs(value))}`,
              valueLabelTone(item, positive)
            )
          ])
        })),
        props.rows.length > 5
          ? h('button', {
              type: 'button',
              class: ['resource-ranking-expand', { expanded: expanded.value }],
              onClick: () => { expanded.value = !expanded.value }
            }, [expanded.value ? '收起' : '点击展开', h(ArrowDown)])
          : null
      ])
    }
  }
})
const CashflowCompare = defineComponent({
  props: {
    rows: { type: Array, default: () => [] },
    type: { type: String, default: 'member' }
  },
  emits: ['select'],
  setup(props, { emit }) {
    return () => {
      if (!props.rows.length) return h(Empty, { description: props.type === 'project' ? '当前没有项目数据' : '当前没有成员数据' })
      const maxValue = Math.max(...props.rows.flatMap(item => [Number(item.income || 0), Number(item.expense || 0)]), 1)
      return h('div', { class: 'cashflow-compare' }, props.rows.slice(0, 12).map(item => {
        const entityId = props.type === 'project' ? item.projectId : item.memberId
        const clickable = Boolean(entityId)
        const rowTag = clickable ? 'button' : 'div'
        return h(rowTag, {
          class: ['cashflow-compare-row', { clickable }],
          key: `${props.type}-${entityId || item.name}`,
          type: rowTag === 'button' ? 'button' : undefined,
          onClick: clickable ? () => emit('select', item) : undefined
        }, [
          h(LedgerResourceIcon, { icon: item.icon, type: props.type, color: item.color, title: item.name }),
          h('div', { class: 'cashflow-compare-main' }, [
            h('div', { class: 'cashflow-compare-name' }, [h('b', { title: item.name }, item.name), h('span', `${item.count} 笔`)]),
            h('div', { class: 'cashflow-compare-bars' }, [
              h('span', { class: 'cashflow-bar income-bar', style: { width: `${Math.max(item.income ? 4 : 0, Number(item.income || 0) / maxValue * 100)}%` } }),
              h('span', { class: 'cashflow-bar expense-bar', style: { width: `${Math.max(item.expense ? 4 : 0, Number(item.expense || 0) / maxValue * 100)}%` } })
            ]),
            h('div', { class: 'cashflow-compare-values' }, [
              h('span', [h('i', { class: 'income-dot' }), `总收入 ${money(item.income)}`]),
              h('span', [h('i', { class: 'expense-dot' }), `总支出 ${money(item.expense)}`])
            ])
          ])
        ])
      }))
    }
  }
})
const AccountOverviewRow = defineComponent({
  props: { label: String, value: [Number, String], tone: String, icon: String },
  setup(props) {
    return () => h('div', { class: 'account-overview-row' }, [
      h(LedgerResourceIcon, { icon: props.icon, type: 'account', color: props.tone === 'expense' ? '#58bfb8' : '#ef856e' }),
      h('b', props.label),
      h('span', [
        h('small', props.label.replace('账本', '').replace('当期', '')),
        h('strong', { class: props.tone }, `¥${money(props.value)}`)
      ])
    ])
  }
})
const TransactionSpotlight = defineComponent({
  props: {
    rows: { type: Array, default: () => [] },
    kind: { type: String, default: 'expense' }
  },
  emits: ['select-entity'],
  setup(props, { emit }) {
    return () => props.rows.length
      ? h('div', { class: 'transaction-spotlight-list' }, props.rows.map(item => h('div', { class: 'transaction-spotlight-row', key: item.id }, [
          h(LedgerResourceIcon, {
            icon: item.categoryIcon || item.parentCategoryIcon,
            type: 'category',
            color: item.categoryColor || item.parentCategoryColor
          }),
          h('div', { class: 'transaction-spotlight-main' }, [
            h('button', {
              type: 'button',
              onClick: () => emit('select-entity', {
                type: 'category',
                item: {
                  categoryId: item.categoryId,
                  primaryCategoryId: item.parentCategoryId,
                  name: categoryDisplayName(item)
                }
              })
            }, categoryDisplayName(item)),
            h('span', [
              h('button', {
                type: 'button',
                onClick: () => emit('select-entity', { type: 'account', item: { id: item.accountId, name: item.accountName } })
              }, item.accountName || '未知账户'),
              item.merchantId
                ? h('button', {
                    type: 'button',
                    onClick: () => emit('select-entity', { type: 'merchant', item: { merchantId: item.merchantId, name: item.merchantName || item.payee } })
                  }, item.merchantName || item.payee || '')
                : null,
              h('small', String(item.occurredOn || '').slice(5).replace('-', '/'))
            ])
          ]),
          h('strong', { class: props.kind === 'income' ? 'income' : 'expense' }, `¥${money(item.amount)}`)
        ])))
      : h(Empty, { description: props.kind === 'income' ? '本期没有收入流水' : '本期没有支出流水' })
  }
})
const RankingTable = defineComponent({
  props: { rows: Array, firstLabel: String },
  setup(props) {
    return () => props.rows?.length
      ? h(Table, null, { default: () => h('tbody', [
          h('tr', { class: 'report-table-header' }, [h('th', props.firstLabel), h('th', '笔数'), h('th', '收入'), h('th', '支出'), h('th', '结余')]),
          ...props.rows.slice(0, 30).map(item => h('tr', { key: item.name }, [
            h('td', { title: item.name }, item.name),
            h('td', item.count),
            h('td', { class: 'income' }, item.income ? `¥${money(item.income)}` : '—'),
            h('td', { class: 'expense' }, item.expense ? `¥${money(item.expense)}` : '—'),
            h('td', { class: item.net >= 0 ? 'income' : 'expense' }, `¥${money(item.net)}`)
          ]))
        ]) })
      : h(Empty, { description: '当前范围没有可统计的数据' })
  }
})
const TransactionTable = defineComponent({
  props: { rows: Array },
  setup(props) {
    return () => props.rows?.length
      ? h(Table, null, { default: () => h('tbody', [
          h('tr', { class: 'report-table-header' }, [h('th', '日期'), h('th', '分类'), h('th', '账户'), h('th', '备注'), h('th', '金额')]),
          ...props.rows.map(item => h('tr', { key: item.id }, [
            h('td', String(item.occurredOn || '').replaceAll('-', '/')),
            h('td', [h(LedgerResourceIcon, { icon: item.categoryIcon || item.parentCategoryIcon, type: 'category', color: item.categoryColor || item.parentCategoryColor, compact: true }), h('span', categoryDisplayName(item))]),
            h('td', item.accountName || '—'),
            h('td', { title: item.note || item.payee || '' }, item.note || item.payee || '—'),
            h('td', { class: isIncome(item) ? 'income' : isExpense(item) ? 'expense' : '' }, `${isIncome(item) ? '+' : isExpense(item) ? '−' : ''}¥${money(item.amount)}`)
          ]))
        ]) })
      : h(Empty, { description: '当前范围没有流水' })
  }
})

function loadReportTabs() {
  try {
    const saved = JSON.parse(localStorage.getItem('ledger-report-tabs-v1') || '[]')
    if (!Array.isArray(saved)) return [...defaultReportTabs]
    const valid = [...new Set(saved.map(String).filter(key => reportKeys.has(key)))]
    return valid.length ? valid : [...defaultReportTabs]
  } catch {
    return [...defaultReportTabs]
  }
}
function persistReportTabs() {
  try {
    localStorage.setItem('ledger-report-tabs-v1', JSON.stringify(visibleReportKeys.value))
  } catch {
    // Storage can be unavailable in privacy mode; the in-memory tabs still work.
  }
}
function toggleLibrary() {
  libraryOpen.value = !libraryOpen.value
  datePickerOpen.value = false
}
function toggleDatePicker() {
  datePickerOpen.value = !datePickerOpen.value
  libraryOpen.value = false
  pickerMode.value = rangeScope.value
  draftCustomFrom.value = customFrom.value
  draftCustomTo.value = customTo.value
  monthPickerYear.value = Number(period.value.slice(0, 4))
  decadeStart.value = Math.floor(Number(period.value.slice(0, 4)) / 10) * 10
}
function toggleReportTab(key) {
  if (visibleReportKeys.value.includes(key)) {
    removeReportTab(key)
    return
  }
  visibleReportKeys.value.push(key)
  selectedReport.value = key
}
function removeReportTab(key) {
  if (visibleReportKeys.value.length <= 1) return
  const index = visibleReportKeys.value.indexOf(key)
  if (index < 0) return
  visibleReportKeys.value.splice(index, 1)
  if (selectedReport.value === key) {
    selectedReport.value = visibleReportKeys.value[Math.min(index, visibleReportKeys.value.length - 1)]
  }
}
function handleReportDragStart(key, event) {
  draggingReportKey.value = key
  dragOverReportKey.value = ''
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', key)
  }
}
function handleReportDragOver(key, event) {
  if (!draggingReportKey.value || draggingReportKey.value === key) return
  dragOverReportKey.value = key
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
}
function handleReportDrop(targetKey, event) {
  const sourceKey = draggingReportKey.value || event.dataTransfer?.getData('text/plain')
  if (!sourceKey || sourceKey === targetKey) {
    handleReportDragEnd()
    return
  }
  const sourceIndex = visibleReportKeys.value.indexOf(sourceKey)
  const targetIndex = visibleReportKeys.value.indexOf(targetKey)
  if (sourceIndex < 0 || targetIndex < 0) {
    handleReportDragEnd()
    return
  }
  const nextKeys = [...visibleReportKeys.value]
  nextKeys.splice(sourceIndex, 1)
  nextKeys.splice(nextKeys.indexOf(targetKey), 0, sourceKey)
  visibleReportKeys.value = nextKeys
  handleReportDragEnd()
}
function handleReportDragEnd() {
  draggingReportKey.value = ''
  dragOverReportKey.value = ''
}
function shiftPeriod(step) {
  if (rangeScope.value === 'year') {
    period.value = String(Number(period.value) + step)
    return
  }
  if (rangeScope.value === 'custom') {
    const duration = daysBetween(rangeDates.value.from, rangeDates.value.to) + 1
    customFrom.value = shiftDate(rangeDates.value.from, duration * step)
    customTo.value = shiftDate(rangeDates.value.to, duration * step)
    draftCustomFrom.value = customFrom.value
    draftCustomTo.value = customTo.value
    syncRangeQuery()
    return
  }
  const [year, month] = period.value.split('-').map(Number)
  const target = new Date(year, month - 1 + step, 1)
  period.value = `${target.getFullYear()}-${String(target.getMonth() + 1).padStart(2, '0')}`
  monthPickerYear.value = target.getFullYear()
}
function setPickerMode(mode) {
  pickerMode.value = mode
  if (mode === 'year') decadeStart.value = Math.floor(Number(period.value.slice(0, 4)) / 10) * 10
  else if (mode === 'month') monthPickerYear.value = Number(period.value.slice(0, 4))
}
function cancelCustomRange() {
  draftCustomFrom.value = customFrom.value
  draftCustomTo.value = customTo.value
  datePickerOpen.value = false
}
function applyCustomRange() {
  if (!validCustomRange.value) return
  customFrom.value = draftCustomFrom.value
  customTo.value = draftCustomTo.value
  rangeScope.value = 'custom'
  datePickerOpen.value = false
  syncRangeQuery()
}
function selectYear(year) {
  rangeScope.value = 'year'
  period.value = String(year)
  datePickerOpen.value = false
}
function selectMonth(month) {
  rangeScope.value = 'month'
  period.value = `${monthPickerYear.value}-${String(month).padStart(2, '0')}`
  datePickerOpen.value = false
}
function handleDocumentPointerDown(event) {
  if (libraryOpen.value && libraryRef.value && !libraryRef.value.contains(event.target)) libraryOpen.value = false
  if (datePickerOpen.value && datePickerRef.value && !datePickerRef.value.contains(event.target)) datePickerOpen.value = false
}
function handleDocumentKeydown(event) {
  if (event.key !== 'Escape') return
  libraryOpen.value = false
  datePickerOpen.value = false
}
function openCategoryTransactions(kind, level, item) {
  const categoryId = String(item?.categoryId || '')
  if (!categoryId) return
  router.push({
    path: '/ledger/transactions',
    query: {
      from: rangeDates.value.from,
      to: rangeDates.value.to,
      ...(level === 'primary'
        ? { primaryCategoryId: categoryId }
        : { secondaryCategoryId: categoryId }),
      source: kind === 'INCOME' ? 'income-category-report' : 'expense-category-report'
    }
  })
}
function openEntityTransactions(type, item) {
  if (!item) return
  const query = {
    from: rangeDates.value.from,
    to: rangeDates.value.to,
    source: `${selectedReport.value}-report`
  }
  if (type === 'category') {
    const categoryId = String(item.categoryId || item.id || '')
    if (!categoryId) return
    if (item.primaryCategoryId && String(item.primaryCategoryId) !== categoryId) query.secondaryCategoryId = categoryId
    else query.primaryCategoryId = categoryId
  } else if (type === 'account') {
    const id = String(item.accountId || item.id || '')
    if (!id) return
    query.accountId = id
  } else if (type === 'merchant') {
    const id = String(item.merchantId || item.id || '')
    if (id) query.merchantId = id
    else if (item.name && item.name !== '无商家') query.payee = item.name
    else return
  } else if (type === 'member') {
    const id = String(item.memberId || item.id || '')
    if (id) query.memberId = id
    else if (item.name) query.member = item.name
    else return
  } else if (type === 'project') {
    const id = String(item.projectId || item.id || '')
    if (id) query.projectId = id
    else if (item.name) query.project = item.name
    else return
  } else {
    return
  }
  router.push({ path: '/ledger/transactions', query })
}
function comparisonText(current, previous) {
  const currentValue = Number(current || 0)
  const previousValue = Number(previous || 0)
  if (!previousValue) return currentValue ? '上期无同类数据' : '与上期持平'
  const percent = (currentValue - previousValue) / previousValue * 100
  return `比${rangeScope.value === 'year' ? '上年' : rangeScope.value === 'custom' ? '上个同长度周期' : '上月'} ${percent >= 0 ? '+' : ''}${percent.toFixed(2)}%`
}
async function analyzeMonth() {
  if (!ledger.online) {
    ElMessage.warning('离线状态下暂不可调用 DeepSeek')
    return
  }
  if (rangeScope.value !== 'month') {
    ElMessage.warning('请先切换到按月查看')
    return
  }
  if (!ledger.currentBookId) return
  aiLoading.value = true
  aiError.value = ''
  try {
    aiAnalysis.value = await apiLedgerMonthlyAnalysis(ledger.currentBookId, {
      period: period.value,
      summary: summary.value,
      previousMonth: previousSummary.value,
      expenseCategories: monthExpenseRows.value,
      incomeCategories: monthIncomeRows.value,
      merchants: merchantExpenseRows.value,
      topExpenses: topExpenses.value.map(aiTransactionPayload),
      topIncomes: topIncomes.value.map(aiTransactionPayload),
      budget: { total: budgetTotal.value, spent: budgetSpent.value }
    })
  } catch (error) {
    aiError.value = error.response?.data?.detail || 'DeepSeek 暂时无法完成分析，请稍后重试'
    ElMessage.error(aiError.value)
  } finally {
    aiLoading.value = false
  }
}
function aiTransactionPayload(item) {
  return {
    amount: Number(item.amount || 0),
    date: item.occurredOn,
    category: categoryDisplayName(item),
    account: item.accountName || '',
    merchant: item.merchantName || item.payee || ''
  }
}
function normalizePeriod(value, scope) {
  const text = String(value || '')
  if (scope === 'year') return /^\d{4}$/.test(text) ? text : currentMonth.slice(0, 4)
  if (!/^\d{4}-\d{2}$/.test(text)) return currentMonth
  const month = Number(text.slice(5))
  return month >= 1 && month <= 12 ? text : currentMonth
}
function todayIso() {
  const date = new Date()
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}
function formatChineseDate(value) {
  const [year, month, day] = String(value || '').split('-').map(Number)
  return `${year} 年 ${month} 月 ${day} 日`
}
function queryDate(value) {
  const text = Array.isArray(value) ? value[0] : String(value || '')
  return /^\d{4}-\d{2}-\d{2}$/.test(text) ? text : ''
}
function shiftDate(value, days) {
  const date = new Date(`${value}T00:00:00`)
  date.setDate(date.getDate() + days)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}
function daysBetween(from, to) {
  return Math.max(0, Math.round((new Date(`${to}T00:00:00`) - new Date(`${from}T00:00:00`)) / 86400000))
}
function syncRangeQuery() {
  router.replace({
    path: route.path,
    query: {
      ...route.query,
      report: selectedReport.value,
      scope: rangeScope.value,
      period: period.value,
      ...(rangeScope.value === 'custom' ? { from: customFrom.value, to: customTo.value } : { from: undefined, to: undefined })
    }
  })
}
function endOfMonth(month) {
  const [year, value] = month.split('-').map(Number)
  return `${month}-${String(new Date(year, value, 0).getDate()).padStart(2, '0')}`
}
function money(value) {
  return Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function isIncome(item) { return incomeKinds.has(item.kind) }
function isExpense(item) { return expenseKinds.has(item.kind) }
function summarize(rows) {
  const incomeRows = rows.filter(isIncome)
  const expenseRows = rows.filter(isExpense)
  const income = incomeRows.reduce((total, item) => total + Number(item.amount || 0), 0)
  const expense = expenseRows.reduce((total, item) => total + Number(item.amount || 0), 0)
  return { income, expense, net: income - expense, incomeCount: incomeRows.length, expenseCount: expenseRows.length }
}
function summarizeAccount(rows, accountId) {
  let income = 0
  let expense = 0
  let incomeCount = 0
  let expenseCount = 0
  rows.forEach(item => {
    const amount = Number(item.amount || 0)
    const storedKind = String(item.storedKind || '')
    const isTarget = String(item.targetAccountId) === String(accountId)
    if (isIncome(item) || storedKind === 'TRANSFER_IN' || (item.kind === 'TRANSFER' && isTarget)) {
      income += amount
      incomeCount++
    } else if (isExpense(item) || storedKind === 'TRANSFER_OUT' || item.kind === 'TRANSFER') {
      expense += amount
      expenseCount++
    }
  })
  return { income, expense, net: income - expense, incomeCount, expenseCount }
}
function categoryById(id) {
  return activeCategories.value.find(item => String(item.id) === String(id))
}
function categoryPath(item) {
  const parent = categoryById(item.parentId)
  return parent ? `${parent.name} / ${item.name}` : item.name
}
function categoryName(id) { return categoryById(id)?.name || '未分类' }
function categoryDisplayName(item) {
  const category = categoryById(item.categoryId)
  if (categoryLevel.value === 'secondary') return item.categoryName || category?.name || '未分类'
  return item.parentCategoryName || categoryById(category?.parentId)?.name || item.categoryName || category?.name || '未分类'
}
function resourceName(items, id, fallback, field = 'name') {
  return items.find(item => String(item.id) === String(id))?.[field] || fallback
}
function groupRows(rows, keyGetter) {
  const map = new Map()
  rows.forEach(item => {
    const name = keyGetter(item) || '未分类'
    const current = map.get(name) || { name, amount: 0, count: 0, income: 0, expense: 0, net: 0 }
    const amount = Number(item.amount || 0)
    current.count++
    if (isIncome(item)) current.income += amount
    if (isExpense(item)) current.expense += amount
    current.amount += amount
    current.net = current.income - current.expense
    map.set(name, current)
  })
  return [...map.values()].sort((left, right) => right.amount - left.amount)
}
function categoryRows(kind) {
  return categoryRankingRows(kind, categoryLevel.value)
}
function categoryPaletteIndex(category) {
  if (!category) return -1
  const index = activeCategories.value
    .filter(item => !item.parentId)
    .findIndex(item => String(item.id) === String(category.id))
  return index >= 0 ? index : undefined
}
function categoryRankingRows(kind, level) {
  const rows = reportRows.value.filter(kind === 'INCOME' ? isIncome : isExpense)
  const groups = new Map()
  rows.forEach(item => {
    const currentCategory = categoryById(item.categoryId)
    const category = level === 'primary'
      ? categoryById(item.parentCategoryId || currentCategory?.parentId) || currentCategory
      : currentCategory
    const name = level === 'primary'
      ? item.parentCategoryName || category?.name || item.categoryName || '未分类'
      : item.categoryName || category?.name || '未分类'
    const categoryId = category?.id || (level === 'primary' ? item.parentCategoryId : item.categoryId) || ''
    const groupKey = categoryId ? String(categoryId) : `${level}-${name}`
    const parentCategory = level === 'secondary'
      ? categoryById(category?.parentId || item.parentCategoryId)
      : null
    const current = groups.get(groupKey) || {
      categoryId,
      name,
      icon: level === 'primary' ? item.parentCategoryIcon || category?.icon : item.categoryIcon || category?.icon,
      color: level === 'primary' ? item.parentCategoryColor || category?.color : item.categoryColor || category?.color,
      parentId: level === 'secondary' ? category?.parentId || item.parentCategoryId : '',
      parentCategoryName: level === 'secondary' ? item.parentCategoryName : '',
      parentColor: parentCategory?.color || item.parentCategoryColor,
      paletteIndex: level === 'primary' ? categoryPaletteIndex(category) : undefined,
      parentPaletteIndex: level === 'secondary' ? categoryPaletteIndex(parentCategory) : undefined,
      amount: 0,
      count: 0,
      share: 0
    }
    current.amount += Number(item.amount || 0)
    current.count++
    groups.set(groupKey, current)
  })
  const result = [...groups.values()].sort((left, right) => right.amount - left.amount)
  const total = result.reduce((sum, item) => sum + item.amount, 0)
  result.forEach((item, index) => {
    item.share = total ? item.amount / total * 100 : 0
    item.color = categoryColor(item, index)
  })
  return result
}
function merchantRankingRows(rows) {
  const groups = new Map()
  rows.forEach(transaction => {
    const merchant = activeMerchants.value.find(item => String(item.id) === String(transaction.merchantId || ''))
    const name = transaction.merchantName || transaction.payee || merchant?.name || '无商家'
    const key = transaction.merchantId ? String(transaction.merchantId) : `name:${name}`
    const current = groups.get(key) || {
      key,
      merchantId: transaction.merchantId || merchant?.id || '',
      name,
      icon: transaction.merchantIcon || merchant?.icon || (name === '无商家' ? 'other' : 'shop'),
      color: '',
      amount: 0,
      count: 0,
      share: 0
    }
    current.amount += Number(transaction.amount || 0)
    current.count++
    groups.set(key, current)
  })
  const result = [...groups.values()].sort((left, right) => right.amount - left.amount)
  const total = result.reduce((sum, item) => sum + item.amount, 0)
  result.forEach((item, index) => {
    item.share = total ? item.amount / total * 100 : 0
    item.color = item.name === '无商家' ? '#e1b455' : stableResourceColor(item.key, index)
  })
  return result
}
function resourceRankingRows(rows, type) {
  const isProject = type === 'project'
  const groups = new Map()
  rows.forEach(transaction => {
    const id = isProject ? transaction.projectId : transaction.memberId
    const collection = isProject ? activeProjects.value : ledger.activeMembers
    const resource = collection.find(item => String(item.id) === String(id || ''))
    const name = isProject
      ? transaction.projectName || transaction.project || resource?.name || '未指定项目'
      : transaction.memberName || transaction.member || resource?.displayName || resource?.name || '未指定成员'
    const key = id ? String(id) : `name:${name}`
    const current = groups.get(key) || {
      id: id || '',
      [isProject ? 'projectId' : 'memberId']: id || '',
      name,
      icon: transaction[isProject ? 'projectIcon' : 'memberIcon'] || resource?.icon || (isProject ? 'folder' : 'user'),
      color: transaction[isProject ? 'projectColor' : 'memberColor'] || resource?.color || stableResourceColor(id || name, groups.size),
      amount: 0,
      income: 0,
      expense: 0,
      net: 0,
      count: 0,
      share: 0
    }
    const amount = Number(transaction.amount || 0)
    current.amount += amount
    current.count++
    if (isIncome(transaction)) current.income += amount
    if (isExpense(transaction)) current.expense += amount
    current.net = current.income - current.expense
    groups.set(key, current)
  })
  const result = [...groups.values()].sort((left, right) => right.amount - left.amount)
  const total = result.reduce((sum, item) => sum + item.amount, 0)
  result.forEach(item => { item.share = total ? item.amount / total * 100 : 0 })
  return result
}
function rankingSummary(rows) {
  const amount = rows.reduce((total, item) => total + item.amount, 0)
  const count = rows.reduce((total, item) => total + item.count, 0)
  return { amount, count, average: count ? amount / count : 0 }
}
function accountBalanceRows(rows) {
  return rows.map((item, index) => ({
    id: item.id,
    name: item.name,
    icon: item.icon,
    color: item.color || stableResourceColor(item.id || item.name, index),
    amount: Math.abs(Number(item.balance || 0))
  })).sort((a, b) => b.amount - a.amount)
}
function accountRankingRows(rows) {
  const result = rows
    .map((item, index) => ({
      ...item,
      amount: item.liability ? Math.abs(Number(item.balance || 0)) : Math.max(0, Number(item.balance || 0)),
      color: item.color || stableResourceColor(item.id || item.name, index),
      share: 0
    }))
    .filter(item => item.amount > 0)
    .sort((left, right) => right.amount - left.amount)
  const total = result.reduce((sum, item) => sum + item.amount, 0)
  result.forEach(item => { item.share = total ? item.amount / total * 100 : 0 })
  return result
}
function accountDonutOption(rows) {
  return {
    tooltip: { trigger: 'item', formatter: item => `${item.name}<br/>¥${money(item.value)} · ${item.percent}%` },
    series: [{
      type: 'pie',
      cursor: 'pointer',
      radius: ['43%', '67%'],
      center: ['50%', '48%'],
      minAngle: 2,
      padAngle: 1,
      avoidLabelOverlap: true,
      label: {
        color: '$text',
        fontSize: 11,
        formatter: item => `${item.name} ${item.percent.toFixed(2)}%`,
        width: 118,
        overflow: 'truncate'
      },
      labelLine: { length: 13, length2: 9, smooth: true },
      itemStyle: { borderColor: '$card', borderWidth: 2, borderRadius: 3 },
      emphasis: { scaleSize: 7 },
      data: rows.map(item => ({
        name: item.name,
        value: item.amount,
        accountId: item.id,
        itemStyle: { color: item.color, borderColor: '$card', borderWidth: 2 }
      }))
    }]
  }
}
function accountFlowBarOption(rows, field, color, signed = false) {
  return {
    grid: { left: 12, right: 18, top: 24, bottom: 18, containLabel: true },
    tooltip: { trigger: 'axis', valueFormatter: value => `¥${money(value)}` },
    xAxis: {
      type: 'category',
      data: rows.map(item => item.name),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '$line' } },
      axisLabel: { color: '$muted', fontSize: 10, width: 72, overflow: 'truncate' }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '$muted', fontSize: 10 },
      splitLine: { lineStyle: { color: '$line', type: 'dashed' } }
    },
    series: [{
      type: 'bar',
      cursor: 'pointer',
      barMaxWidth: 38,
      data: rows.map((item, index) => ({
        value: Number(item[field] || 0),
        accountId: item.id,
        name: item.name,
        itemStyle: {
          color: signed && Number(item[field] || 0) < 0 ? '$expense' : item.color || (String(color).startsWith('$') ? stableResourceColor(item.id || item.name, index) : color),
          borderRadius: Number(item[field] || 0) >= 0 ? [4, 4, 0, 0] : [0, 0, 4, 4]
        }
      }))
    }]
  }
}
function timeSeries(rows, granularity) {
  const map = new Map()
  rows.forEach(item => {
    const key = granularity === 'month' ? String(item.occurredOn).slice(0, 7) : String(item.occurredOn)
    const current = map.get(key) || { key, income: 0, expense: 0, net: 0 }
    if (isIncome(item)) current.income += Number(item.amount || 0)
    if (isExpense(item)) current.expense += Number(item.amount || 0)
    current.net = current.income - current.expense
    map.set(key, current)
  })
  return [...map.values()].sort((left, right) => left.key.localeCompare(right.key))
}
function completeTimeSeries(rows) {
  const granularity = trendGranularity.value
  const values = new Map(timeSeries(rows, granularity).map(item => [item.key, item]))
  const keys = []
  if (rangeScope.value === 'custom') {
    if (granularity === 'month') {
      let [year, month] = rangeDates.value.from.slice(0, 7).split('-').map(Number)
      const end = rangeDates.value.to.slice(0, 7)
      while (`${year}-${String(month).padStart(2, '0')}` <= end) {
        keys.push(`${year}-${String(month).padStart(2, '0')}`)
        month++
        if (month > 12) {
          year++
          month = 1
        }
      }
    } else {
      const duration = daysBetween(rangeDates.value.from, rangeDates.value.to)
      for (let day = 0; day <= duration; day++) keys.push(shiftDate(rangeDates.value.from, day))
    }
    return keys.map(key => values.get(key) || { key, income: 0, expense: 0, net: 0 })
  }
  if (granularity === 'month') {
    for (let month = 1; month <= 12; month++) keys.push(`${period.value}-${String(month).padStart(2, '0')}`)
  } else {
    const days = Number(endOfMonth(period.value).slice(-2))
    for (let day = 1; day <= days; day++) keys.push(`${period.value}-${String(day).padStart(2, '0')}`)
  }
  return keys.map(key => values.get(key) || { key, income: 0, expense: 0, net: 0 })
}
function timeSeriesForAccount(rows, accountId) {
  const map = new Map()
  rows.forEach(item => {
    const key = trendGranularity.value === 'month' ? String(item.occurredOn).slice(0, 7) : String(item.occurredOn)
    const current = map.get(key) || { key, income: 0, expense: 0, net: 0 }
    const flow = summarizeAccount([item], accountId)
    current.income += flow.income
    current.expense += flow.expense
    current.net = current.income - current.expense
    map.set(key, current)
  })
  return [...map.values()].sort((left, right) => left.key.localeCompare(right.key))
}
function axisChartBase(categories) {
  return {
    grid: { left: 12, right: 22, top: 18, bottom: 12, containLabel: true },
    tooltip: { trigger: 'axis', valueFormatter: value => `¥${money(value)}` },
    xAxis: { type: 'value', axisLabel: { color: '$text', fontSize: 10 }, splitLine: { lineStyle: { color: '$line', type: 'dashed' } } },
    yAxis: { type: 'category', data: categories, axisTick: { show: false }, axisLine: { show: false }, axisLabel: { color: '$text', fontSize: 11, width: 104, overflow: 'truncate' } }
  }
}
function horizontalBarOption(rows, color, limit = 10) {
  const values = rows.slice(0, limit).reverse()
  return {
    ...axisChartBase(values.map(item => item.name)),
    series: [{
      type: 'bar',
      data: values.map((item, index) => ({
        ...item,
        value: item.amount,
        itemStyle: {
          color: item.color || (color && !String(color).startsWith('$') ? color : stableResourceColor(item.id || item.name, index)),
          borderRadius: [0, 5, 5, 0]
        }
      })),
      barMaxWidth: 14
    }]
  }
}
function pieOption(rows, colors = resourceSeries(rows)) {
  const values = rows.slice(0, 10)
  return {
    color: colors,
    tooltip: { trigger: 'item', formatter: item => `${item.name}<br/>¥${money(item.value)} · ${item.percent}%` },
    series: [{
      type: 'pie',
      radius: ['44%', '66%'],
      center: ['50%', '46%'],
      minAngle: 2,
      padAngle: 1,
      avoidLabelOverlap: true,
      label: {
        color: '$muted',
        formatter: item => `${item.name}  ${item.percent.toFixed(1)}%`,
        fontSize: 11,
        width: 116,
        overflow: 'truncate'
      },
      labelLine: { length: 15, length2: 10, smooth: 0.2 },
      itemStyle: { borderColor: '$card', borderWidth: 2, borderRadius: 3 },
      emphasis: { scaleSize: 7 },
      data: values.map((item, index) => ({
        name: item.name,
        value: item.amount,
        itemStyle: { color: item.color || colors[index % colors.length], borderColor: '$card', borderWidth: 2 }
      }))
    }]
  }
}
function categoryDonutOption(rows) {
  const colors = categorySeries(rows)
  return {
    color: colors,
    tooltip: {
      trigger: 'item',
      formatter: item => `${item.name}<br/>¥${money(item.value)} · ${item.percent}%`
    },
    series: [{
      type: 'pie',
      cursor: 'pointer',
      radius: ['45%', '67%'],
      center: ['50%', '47%'],
      minAngle: 2,
      padAngle: 1,
      avoidLabelOverlap: true,
      label: {
        show: true,
        color: '$muted',
        fontSize: 11,
        formatter: item => `${item.name}  ${item.percent.toFixed(1)}%`,
        overflow: 'truncate',
        width: 116
      },
      labelLine: { length: 15, length2: 10, smooth: 0.2 },
      itemStyle: { borderColor: '$card', borderWidth: 2, borderRadius: 3 },
      emphasis: { scaleSize: 7 },
      data: rows.map((item, index) => ({
        name: item.name,
        value: item.amount,
        categoryId: item.categoryId,
        itemStyle: { color: item.color || colors[index % colors.length], borderColor: '$card', borderWidth: 2 }
      }))
    }]
  }
}
function merchantDonutOption(rows) {
  const colors = resourceSeries(rows)
  return {
    color: colors,
    tooltip: {
      trigger: 'item',
      formatter: item => `${item.name}<br/>¥${money(item.value)} · ${item.percent}%`
    },
    series: [{
      type: 'pie',
      radius: ['45%', '67%'],
      center: ['50%', '47%'],
      minAngle: 2,
      padAngle: 1,
      avoidLabelOverlap: true,
      label: {
        show: true,
        color: '$muted',
        fontSize: 11,
        formatter: item => `${item.name}  ${item.percent.toFixed(1)}%`,
        overflow: 'truncate',
        width: 116
      },
      labelLine: { length: 15, length2: 10, smooth: 0.2 },
      itemStyle: { borderColor: '$card', borderWidth: 2, borderRadius: 3 },
      emphasis: { scaleSize: 7 },
      data: rows.map((item, index) => ({
        name: item.name,
        value: item.amount,
        merchantId: item.merchantId,
        itemStyle: { color: item.color || colors[index % colors.length], borderColor: '$card', borderWidth: 2 }
      }))
    }]
  }
}
function resourceDonutOption(rows, idField) {
  const colors = resourceSeries(rows)
  return {
    color: colors,
    tooltip: { trigger: 'item', formatter: item => `${item.name}<br/>¥${money(item.value)} · ${item.percent}%` },
    series: [{
      type: 'pie',
      cursor: 'pointer',
      radius: ['45%', '67%'],
      center: ['50%', '47%'],
      minAngle: 2,
      padAngle: 1,
      avoidLabelOverlap: true,
      label: {
        show: true,
        color: '$muted',
        fontSize: 11,
        formatter: item => `${item.name}  ${item.percent.toFixed(1)}%`,
        overflow: 'truncate',
        width: 116
      },
      labelLine: { length: 15, length2: 10, smooth: 0.2 },
      itemStyle: { borderColor: '$card', borderWidth: 2, borderRadius: 3 },
      emphasis: { scaleSize: 7 },
      data: rows.map((item, index) => ({
        name: item.name,
        value: Math.abs(Number(item.amount || item.displayAmount || 0)),
        [idField]: item[idField],
        itemStyle: { color: item.color || colors[index % colors.length], borderColor: '$card', borderWidth: 2 }
      }))
    }]
  }
}
function resourceCountBarOption(rows, idField) {
  const values = rows.slice(0, 10)
  return {
    grid: { left: 16, right: 18, top: 28, bottom: 30, containLabel: true },
    tooltip: { trigger: 'axis', valueFormatter: value => `${value} 笔` },
    xAxis: {
      type: 'category',
      data: values.map(item => item.name),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '$line' } },
      axisLabel: { color: '$muted', fontSize: 11, width: 90, overflow: 'truncate' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: '$muted', fontSize: 10 },
      splitLine: { lineStyle: { color: '$line', type: 'dashed' } }
    },
    series: [{
      type: 'bar',
      cursor: 'pointer',
      barMaxWidth: 42,
      data: values.map((item, index) => ({
        value: item.count,
        [idField]: item[idField],
        name: item.name,
        label: { show: true, position: 'top', color: '$text', fontSize: 11 },
        itemStyle: { color: item.color || stableResourceColor(item[idField] || item.name, index), borderRadius: [4, 4, 0, 0] }
      }))
    }]
  }
}
function merchantCountBarOption(rows, color) {
  const values = rows.slice(0, 10)
  return {
    grid: { left: 16, right: 18, top: 28, bottom: 30, containLabel: true },
    tooltip: { trigger: 'axis', valueFormatter: value => `${value} 笔` },
    xAxis: {
      type: 'category',
      data: values.map(item => item.name),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '$line' } },
      axisLabel: { color: '$muted', fontSize: 11, width: 90, overflow: 'truncate' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: '$muted', fontSize: 10 },
      splitLine: { lineStyle: { color: '$line', type: 'dashed' } }
    },
    series: [{
      type: 'bar',
      cursor: 'pointer',
      barMaxWidth: 42,
      data: values.map((item, index) => ({
        value: item.count,
        merchantId: item.merchantId,
        name: item.name,
        label: { show: true, position: 'top', color: '$text', fontSize: 11 },
        itemStyle: { color: item.color || (String(color).startsWith('$') ? stableResourceColor(item.merchantId || item.name, index) : color), borderRadius: [4, 4, 0, 0] }
      }))
    }]
  }
}
function valueLabelTone(item, positive) {
  if (item.displayAmount != null) return positive ? 'income' : 'expense'
  if (item.income && !item.expense) return 'income'
  if (item.expense && !item.income) return 'expense'
  return positive ? 'income' : 'expense'
}
function netWorthTrendOption(rows) {
  const singlePoint = rows.length === 1
  return {
    grid: { left: 18, right: 18, top: 58, bottom: 28, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: '$line', width: 1.5 } },
      valueFormatter: value => `¥${money(value)}`
    },
    xAxis: {
      type: 'category',
      boundaryGap: singlePoint,
      data: rows.map(item => `${Number(item.key.slice(5))}月`),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { color: '$muted', fontSize: 11, margin: 14 }
    },
    yAxis: {
      type: 'value',
      min: value => value.min < 0 ? Math.floor(value.min * 1.15) : 0,
      axisLabel: {
        color: '$muted',
        fontSize: 10,
        formatter: value => Math.abs(value) >= 10000 ? `${money(value / 10000).replace('.00', '')}w` : money(value).replace('.00', '')
      },
      splitLine: { lineStyle: { color: '$line', type: 'dashed' } }
    },
    series: [{
      name: '净资产',
      type: 'line',
      smooth: !singlePoint,
      symbol: 'circle',
      symbolSize: 10,
      showSymbol: singlePoint,
      data: rows.map(item => item.value),
      lineStyle: { color: '#7e879f', width: 3 },
      itemStyle: { color: '#7e879f', borderColor: '$card', borderWidth: 4 },
      emphasis: { scale: 1.35 }
    }]
  }
}
function trendOption(rows) {
  return {
    grid: { left: 12, right: 18, top: 42, bottom: 22, containLabel: true },
    tooltip: { trigger: 'axis', valueFormatter: value => `¥${money(value)}` },
    legend: { data: ['收入', '支出', '结余'], right: 0, textStyle: { color: '$text', fontSize: 10 } },
    xAxis: { type: 'category', boundaryGap: false, data: rows.map(item => item.key.slice(5)), axisLine: { lineStyle: { color: '$line' } }, axisLabel: { color: '$text', fontSize: 10 } },
    yAxis: { type: 'value', axisLabel: { color: '$text', fontSize: 10 }, splitLine: { lineStyle: { color: '$line', type: 'dashed' } } },
    series: [
      { name: '收入', type: 'line', smooth: true, symbol: 'none', data: rows.map(item => item.income), lineStyle: { color: '$income', width: 2 }, itemStyle: { color: '$income' } },
      { name: '支出', type: 'line', smooth: true, symbol: 'none', data: rows.map(item => item.expense), lineStyle: { color: '$expense', width: 2 }, itemStyle: { color: '$expense' } },
      { name: '结余', type: 'line', smooth: true, symbol: 'none', data: rows.map(item => item.net), lineStyle: { color: '$accent', width: 2, type: 'dashed' }, itemStyle: { color: '$accent' } }
    ]
  }
}
function singleTrendOption(rows, name, color) {
  const values = timeSeries(rows, trendGranularity.value)
  return {
    grid: { left: 12, right: 18, top: 24, bottom: 22, containLabel: true },
    tooltip: { trigger: 'axis', valueFormatter: value => `¥${money(value)}` },
    xAxis: { type: 'category', boundaryGap: false, data: values.map(item => item.key.slice(5)), axisLine: { lineStyle: { color: '$line' } }, axisLabel: { color: '$text', fontSize: 10 } },
    yAxis: { type: 'value', axisLabel: { color: '$text', fontSize: 10 }, splitLine: { lineStyle: { color: '$line', type: 'dashed' } } },
    series: [{ name, type: 'line', smooth: true, symbol: 'circle', symbolSize: 5, data: values.map(item => item.income + item.expense), lineStyle: { color, width: 2 }, itemStyle: { color }, areaStyle: { color, opacity: 0.08 } }]
  }
}
function accountTypeName(type) {
  return ({ cash: '现金', bank: '银行卡', card: '信用卡', credit: '信用账户', credit_card: '信用卡', loan: '贷款', wallet: '电子钱包' })[type] || '其他账户'
}
function budgetPercent(item) {
  return Math.min(100, Number(item.spent || 0) / Math.max(1, Number(item.budget || 0)) * 100)
}
async function loadBudgets() {
  const requestId = ++budgetRequestId
  const bookId = ledger.currentBookId
  if (!ledger.online || !bookId) {
    remoteBudgets.value = []
    return
  }
  try {
    const rows = await apiListLedgerBudgets(bookId, budgetMonth.value)
    if (requestId === budgetRequestId && String(bookId) === String(ledger.currentBookId)) {
      remoteBudgets.value = rows
    }
  } catch {
    if (requestId === budgetRequestId) remoteBudgets.value = []
  }
}

watch(rangeScope, scope => { period.value = normalizePeriod(period.value, scope) })
watch(visibleReportKeys, persistReportTabs, { deep: true })
watch([selectedReport, rangeScope, period], syncRangeQuery)
watch([rangeScope, period, () => ledger.currentBookId], () => {
  aiAnalysis.value = null
  aiError.value = ''
})
watch([budgetMonth, () => ledger.currentBookId], loadBudgets)
watch(activeAccounts, rows => {
  if (!rows.some(item => String(item.id) === selectedAccountId.value)) selectedAccountId.value = rows[0] ? String(rows[0].id) : ''
}, { immediate: true })
watch(selectableCategories, rows => {
  if (selectedCategoryId.value && !rows.some(item => String(item.id) === selectedCategoryId.value)) selectedCategoryId.value = ''
})
watch(activeProjects, rows => {
  if (selectedProjectId.value && !rows.some(item => String(item.id) === selectedProjectId.value)) selectedProjectId.value = ''
}, { immediate: true })

onMounted(async () => {
  startReportsLoading()
  try {
    await ledger.init({ waitForRemote: false })
  } finally {
    finishReportsLoading()
  }
  void loadBudgets()
  document.addEventListener('pointerdown', handleDocumentPointerDown)
  document.addEventListener('keydown', handleDocumentKeydown)
})
onBeforeUnmount(() => {
  finishReportsLoading()
  budgetRequestId++
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
  document.removeEventListener('keydown', handleDocumentKeydown)
})
</script>

<style scoped>
.ledger-reports,
.ledger-reports *,
.report-grid,
.report-grid > *,
.summary-grid,
.summary-grid > * {
  min-width: 0;
  box-sizing: border-box;
}
.ledger-reports {
  width: 100%;
  max-width: 1540px;
  margin: 0 auto;
  overflow-x: clip;
}
.report-tabbar {
  position: relative;
  z-index: 8;
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  min-height: 58px;
  margin-bottom: 22px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: var(--card);
}
.report-tabs {
  display: flex;
  flex: 1;
  align-items: stretch;
  overflow-x: auto;
  scrollbar-width: none;
}
.report-tabs::-webkit-scrollbar { display: none; }
.report-tab-item {
  position: relative;
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  min-height: 56px;
  cursor: grab;
  user-select: none;
}
.report-tab-item:active { cursor: grabbing; }
.report-tab-item.dragging { opacity: .45; }
.report-tab-item.drag-over { border-radius: 5px; background: var(--accent-soft); }
.report-tab-item::after {
  position: absolute;
  right: 14px;
  bottom: 0;
  left: 14px;
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: var(--accent);
  content: "";
  opacity: 0;
  transform: scaleX(.5);
  transition: opacity .16s ease, transform .16s ease;
}
.report-tab-item.active::after { opacity: 1; transform: scaleX(1); }
.report-tab-label {
  height: 100%;
  padding: 0 12px 0 18px;
  border: 0;
  background: transparent;
  color: var(--ink2);
  font-size: 14px;
  white-space: nowrap;
}
.report-tab-item.active .report-tab-label { color: var(--ink); font-weight: 700; }
.report-tab-remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-right: 9px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--muted);
  opacity: 0;
  transition: opacity .14s ease, background .14s ease, color .14s ease;
}
.report-tab-remove svg { width: 11px; height: 11px; }
.report-tab-item:hover .report-tab-remove,
.report-tab-item:focus-within .report-tab-remove { opacity: 1; }
.report-tab-remove:hover { background: var(--paper); color: var(--down); }
.report-library-anchor {
  position: relative;
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  padding: 9px 12px;
  border-left: 1px solid var(--line);
}
.report-library-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 38px;
  padding: 0 14px;
  border: 1px solid var(--line2);
  border-radius: 5px;
  background: var(--card);
  color: var(--ink2);
  font-weight: 650;
  white-space: nowrap;
}
.report-library-button:hover,
.report-library-button[aria-expanded="true"] { border-color: var(--accent); color: var(--accent); }
.report-library-button svg { width: 17px; height: 17px; }
.report-library-button{width:38px;min-width:38px;height:38px;padding:0;gap:0}
.report-library-button svg{display:block;margin:auto;flex:0 0 18px;width:18px;height:18px}
.report-library-popover {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 40;
  width: min(720px, calc(100vw - 40px));
  padding: 18px;
  border: 1px solid var(--line2);
  border-radius: 9px;
  background: var(--card);
  box-shadow: 0 24px 60px color-mix(in srgb, var(--ink) 17%, transparent);
}
.library-popover-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 15px;
}
.library-popover-head > div { display: flex; flex-direction: column; gap: 3px; }
.library-popover-head b { font-size: 17px; }
.library-popover-head span { color: var(--muted); font-size: 11px; }
.library-popover-head button {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--paper);
  color: var(--muted);
}
.library-popover-head svg { width: 13px; height: 13px; }
.library-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.library-report-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 70px;
  padding: 12px 13px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--paper);
  color: var(--ink);
  text-align: left;
}
.library-report-item:hover { border-color: var(--line2); transform: translateY(-1px); }
.library-report-item.added { border-color: color-mix(in srgb, var(--accent) 42%, var(--line)); background: var(--accent-soft); }
.library-report-item > span:first-child { display: flex; flex-direction: column; gap: 4px; overflow: hidden; }
.library-report-item b { font-size: 13px; }
.library-report-item small { overflow: hidden; color: var(--muted); font-size: 10px; line-height: 1.4; text-overflow: ellipsis; white-space: nowrap; }
.library-report-action {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 1px solid var(--line2);
  border-radius: 5px;
  background: var(--card);
  gap: 4px;
  color: var(--accent);
}
.library-report-action svg { width: 14px; height: 14px; }
.report-content-heading {
  position: relative;
  z-index: 7;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 22px;
}
.report-heading-actions {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}
.report-heading-actions .ledger-action-icon { width: 34px; height: 34px; }
.report-content-heading h1 { margin: 0; font: 700 clamp(25px, 3vw, 34px)/1.2 Georgia, "Songti SC", serif; }
.report-content-heading span { display: block; margin-top: 5px; color: var(--muted); font-size: 11px; }
.date-picker-anchor { position: relative; flex: 0 0 auto; }
.date-trigger {
  display: grid;
  grid-template-columns: 38px minmax(110px, auto) 38px;
  height: 40px;
  overflow: hidden;
  border: 1px solid var(--line2);
  border-radius: 5px;
  background: var(--card);
}
.date-trigger button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink2);
}
.date-trigger button:hover { background: var(--accent-soft); color: var(--accent); }
.date-trigger svg { width: 15px; height: 15px; }
.date-trigger .date-trigger-label {
  padding: 0 15px;
  border-right: 1px solid var(--line);
  border-left: 1px solid var(--line);
  color: var(--ink);
  font-size: 14px;
  font-weight: 650;
}
.date-picker-popover {
  position: absolute;
  top: calc(100% + 9px);
  right: 0;
  z-index: 35;
  width: 390px;
  max-width: calc(100vw - 32px);
  padding: 20px;
  border: 1px solid var(--line2);
  border-radius: 9px;
  background: var(--card);
  box-shadow: 0 24px 60px color-mix(in srgb, var(--ink) 17%, transparent);
}
.date-mode-switch {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
  padding: 4px;
  border-radius: 6px;
  background: var(--paper);
}
.date-mode-switch button {
  height: 38px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--ink2);
  font-weight: 650;
}
.date-mode-switch button.active { background: var(--card); color: var(--ink); box-shadow: 0 3px 10px color-mix(in srgb, var(--ink) 10%, transparent); }
.picker-period-head {
  display: grid;
  grid-template-columns: 34px 1fr 34px;
  align-items: center;
  margin: 16px 0 11px;
  text-align: center;
}
.picker-period-head button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--muted);
}
.picker-period-head button:hover { background: var(--paper); color: var(--ink); }
.picker-period-head svg { width: 14px; height: 14px; }
.picker-period-head b { font-size: 14px; }
.picker-year-grid,
.picker-month-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 7px;
}
.picker-year-grid button,
.picker-month-grid button {
  height: 50px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--ink2);
  font-size: 13px;
}
.picker-year-grid button:hover,
.picker-month-grid button:hover { background: var(--paper); color: var(--ink); }
.picker-year-grid button.active,
.picker-month-grid button.active {
  border-color: color-mix(in srgb, var(--accent) 42%, var(--line));
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 700;
}
.picker-year-grid button.current:not(.active) { color: var(--accent); }
.custom-date-range {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: end;
  gap: 10px;
  margin-top: 18px;
}
.custom-date-range > span { padding-bottom: 10px; color: var(--muted); }
.custom-date-range label {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
  color: var(--muted);
  font-size: 11px;
}
.custom-date-range input {
  width: 100%;
  min-width: 0;
  height: 38px;
  padding: 0 9px;
  border: 1px solid var(--line2);
  border-radius: 5px;
  background: var(--card);
  color: var(--ink);
}
.custom-date-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
.custom-date-actions button {
  height: 34px;
  padding: 0 13px;
  border: 1px solid var(--line2);
  border-radius: 5px;
  background: var(--card);
  color: var(--ink2);
}
.custom-date-actions button.primary { border-color: var(--accent); background: var(--accent); color: #fff; }
.custom-date-actions button:disabled { cursor: not-allowed; opacity: .45; }
.report-panel-enter-active,
.report-panel-leave-active { transition: opacity .2s ease, transform .2s ease }
.report-panel-enter-from { opacity: 0; transform: translateY(7px) }
.report-panel-leave-to { opacity: 0; transform: translateY(-4px) }
.report-sub-controls {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
  margin: -8px 0 15px;
}
.report-sub-controls label { display: flex; align-items: center; gap: 8px; color: var(--muted); font-size: 11px; }
.report-sub-controls select { width: auto; min-width: 150px; height: 34px; padding: 0 28px 0 10px; }
.basic-dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: 18px;
}
.category-statistics-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: 18px;
}
.category-statistics-card {
  overflow: hidden;
  padding: 26px 28px 28px;
  border-radius: 10px;
}
.category-statistics-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 18px;
}
.category-statistics-head h2 { margin: 0; font: 700 20px/1.2 Georgia, "Songti SC", serif; white-space: nowrap; }
.category-statistics-total { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px 18px; color: var(--muted); font-size: 11px; }
.category-statistics-total strong,
.category-statistics-total b { margin-left: 4px; font-size: 14px; font-weight: 650; font-variant-numeric: tabular-nums; }
.category-level-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 9px;
  margin-top: 16px;
}
.category-level-row > span { color: var(--muted); font-size: 10px; }
.category-statistics-card :deep(.category-ranking) {
  margin-top: 12px;
  padding-top: 22px;
  border-top: 1px solid var(--line);
}
.account-dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: 18px;
}
.account-overview-card {
  grid-column: 1;
  overflow: hidden;
  padding: 0;
  border-radius: 10px;
}
.account-summary-banner {
  position: relative;
  min-height: 148px;
  overflow: hidden;
  padding: 18px 28px;
  background:
    radial-gradient(circle at 78% 16%, rgba(255,255,255,.42) 0 13%, transparent 13.5%),
    radial-gradient(circle at 94% 32%, rgba(255,255,255,.2) 0 25%, transparent 25.5%),
    linear-gradient(118deg, #ff832c 0%, #ff6140 45%, #f8a073 72%, #f5d1c1 100%);
  color: #fff;
}
.account-summary-banner::before,
.account-summary-banner::after {
  position: absolute;
  border: 1px solid rgba(255,255,255,.3);
  border-radius: 50%;
  content: "";
}
.account-summary-banner::before { top: -130px; left: 33%; width: 330px; height: 420px; }
.account-summary-banner::after { right: -80px; bottom: -110px; width: 310px; height: 270px; }
.account-summary-banner > * { position: relative; z-index: 1; }
.account-summary-banner > span { display: block; margin-bottom: 11px; font-size: 18px; font-weight: 700; }
.account-summary-banner > small { display: block; margin-bottom: 4px; color: rgba(255,255,255,.83); font-size: 12px; }
.account-summary-banner > strong {
  display: block;
  overflow: hidden;
  color: #fff !important;
  font: 750 clamp(32px, 3.6vw, 42px)/1 Georgia, serif;
  letter-spacing: -.02em;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.account-summary-values { display: flex; align-items: center; gap: 17px; margin-top: 11px; color: rgba(255,255,255,.85); font-size: 12px; }
.account-summary-values span { display: flex; align-items: baseline; gap: 7px; }
.account-summary-values b { color: #fff; font-size: 15px; font-weight: 700; }
.account-summary-values i { width: 1px; height: 18px; background: rgba(255,255,255,.38); }
.account-trend-panel { padding: 0 20px 8px; }
.account-ranking-card {
  min-height: 0;
  padding: 28px 30px 24px;
  border-radius: 10px;
}
.asset-ranking-card { grid-column: 2; grid-row: 1; min-height: 384px; }
.liability-ranking-card { grid-column: 1; grid-row: 2; }
.account-ranking-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 26px;
}
.account-ranking-head h2 { margin: 0; font: 700 20px/1.2 Georgia, "Songti SC", serif; }
.account-ranking-head > span { display: inline-flex; align-items: baseline; gap: 5px; color: var(--muted); font-size: 11px; }
.account-ranking-head strong { margin-left: 5px; color: var(--ink2); font-size: 14px; font-variant-numeric: tabular-nums; }
.account-ranking-head small { color: var(--muted); font-size: 10px; white-space: nowrap; }
.account-ranking-list { display: flex; flex-direction: column; gap: 26px; }
.account-ranking-row {
  display: grid;
  grid-template-columns: 20px 34px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  text-align: left;
  animation: ranking-row-in .2s ease both;
}
.account-ranking-row.clickable,
.merchant-ranking-row.clickable {
  padding: 7px 8px;
  margin: -7px -8px;
  border-radius: 6px;
  cursor: pointer;
}
.account-ranking-row.clickable:hover,
.account-ranking-row.clickable:focus-visible,
.merchant-ranking-row.clickable:hover,
.merchant-ranking-row.clickable:focus-visible { background: var(--accent-soft); outline: none; }
.account-rank { color: var(--muted); font: 500 15px Georgia, serif; text-align: center; }
.account-ranking-main { overflow: hidden; }
.account-ranking-label { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
.account-ranking-label b { overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.account-ranking-label > span { display: flex; flex: 0 0 auto; align-items: center; gap: 6px; font-variant-numeric: tabular-nums; }
.account-ranking-label small { color: var(--muted); font-size: 10px; }
.account-ranking-label i { width: 3px; height: 3px; border-radius: 50%; background: var(--line2); }
.account-ranking-label strong { font-size: 13px; font-weight: 600; }
.account-ranking-track { height: 4px; overflow: hidden; border-radius: 4px; background: var(--line); }
.account-ranking-track span { display: block; height: 100%; border-radius: inherit; }
.account-ranking-expand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  margin-top: 32px;
  padding: 6px;
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 11px;
}
.account-ranking-expand:hover { color: var(--ink); }
.account-ranking-expand svg { width: 14px; height: 14px; transition: transform .16s ease; }
.account-ranking-expand.expanded svg { transform: rotate(180deg); }
.merchant-dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: 18px;
}
.merchant-summary-card {
  grid-column: 1;
  min-height: 184px;
  padding: 28px 30px;
  border-radius: 10px;
}
.merchant-summary-card h2 { margin: 0 0 28px; font: 700 20px/1.2 Georgia, "Songti SC", serif; }
.merchant-summary-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}
.merchant-summary-identity { display: flex; min-width: 0; align-items: center; gap: 13px; }
.merchant-summary-identity > span { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.merchant-summary-identity b { overflow: hidden; color: var(--ink); font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }
.merchant-summary-identity small { color: var(--muted); font-size: 11px; }
.merchant-summary-values { display: flex; flex: 0 0 auto; flex-direction: column; align-items: flex-end; gap: 5px; }
.merchant-summary-values span { color: var(--muted); font-size: 11px; }
.merchant-summary-values strong { display: inline-block; min-width: 110px; margin-left: 7px; font-size: 15px; font-variant-numeric: tabular-nums; text-align: right; }
.merchant-distribution-card {
  padding: 28px 30px 24px;
  border-radius: 10px;
}
.merchant-expense-card { grid-column: 1; grid-row: 2; }
.merchant-income-card { grid-column: 2; grid-row: 1 / span 2; min-height: 700px; }
.merchant-distribution-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}
.merchant-distribution-head h2 { margin: 0; font: 700 20px/1.2 Georgia, "Songti SC", serif; white-space: nowrap; }
.merchant-distribution-head > div { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 7px 18px; color: var(--muted); font-size: 11px; }
.merchant-distribution-head strong,
.merchant-distribution-head b { margin-left: 5px; font-size: 14px; font-weight: 650; font-variant-numeric: tabular-nums; }
.merchant-ranking { margin-top: 8px; padding-top: 22px; border-top: 1px solid var(--line); }
.merchant-ranking-list { display: flex; flex-direction: column; gap: 24px; }
.merchant-ranking-row {
  display: grid;
  grid-template-columns: 20px 34px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  text-align: left;
  animation: ranking-row-in .2s ease both;
}
.merchant-ranking-row.clickable:disabled { cursor: default; opacity: .66; }
.merchant-rank { color: var(--muted); font: 500 15px Georgia, serif; text-align: center; }
.merchant-ranking-main { overflow: hidden; }
.merchant-ranking-label { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
.merchant-ranking-label b { overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.merchant-ranking-label > span { display: flex; flex: 0 0 auto; align-items: center; gap: 6px; font-variant-numeric: tabular-nums; }
.merchant-ranking-label small { color: var(--muted); font-size: 10px; }
.merchant-ranking-label i { width: 3px; height: 3px; border-radius: 50%; background: var(--line2); }
.merchant-ranking-label strong { font-size: 13px; font-weight: 600; }
.merchant-ranking-track { height: 4px; overflow: hidden; border-radius: 4px; background: var(--line); }
.merchant-ranking-track span { display: block; height: 100%; border-radius: inherit; }
.merchant-ranking-expand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  margin-top: 30px;
  padding: 6px;
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 11px;
}
.merchant-ranking-expand:hover { color: var(--ink); }
.merchant-ranking-expand svg { width: 14px; height: 14px; transition: transform .16s ease; }
.merchant-ranking-expand.expanded svg { transform: rotate(180deg); }
.month-dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: 18px;
}
.month-column { display: flex; min-width: 0; flex-direction: column; gap: 18px; }
.month-summary-card { overflow: hidden; padding: 0; border-radius: 10px; }
.month-summary-banner {
  min-height: 205px;
  padding: 25px 30px;
  background:
    radial-gradient(circle at 82% 30%, rgba(255,255,255,.22) 0 11%, transparent 11.5%),
    linear-gradient(130deg, color-mix(in srgb, var(--accent) 58%, #376ff2), #479de9 52%, #b9dfee);
  color: #fff;
}
.month-summary-head { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.month-summary-head h2 { margin: 0; color: #fff; font: 700 20px/1.2 Georgia, "Songti SC", serif; }
.month-summary-head .ui-button {
  border-color: rgba(255,255,255,.45);
  background: rgba(255,255,255,.14);
  color: #fff;
}
.month-summary-head svg { width: 15px; height: 15px; }
.month-summary-banner > small { display: block; margin-top: 22px; color: rgba(255,255,255,.82); font-size: 11px; }
.month-summary-banner > strong { display: block; margin-top: 5px; color: #fff !important; font: 750 clamp(38px, 5vw, 54px)/1 Georgia, serif; }
.month-summary-values { display: flex; align-items: center; gap: 16px; margin-top: 23px; color: rgba(255,255,255,.84); font-size: 11px; }
.month-summary-values span { display: flex; align-items: baseline; gap: 6px; }
.month-summary-values b { color: #fff !important; font-size: 14px; }
.month-summary-values i { width: 1px; height: 17px; background: rgba(255,255,255,.38); }
.month-narrative { min-height: 198px; padding: 28px 30px; }
.month-narrative h3 { margin: 0 0 20px; font: 700 16px/1.4 Georgia, "Songti SC", serif; }
.month-narrative p { margin: 0; color: var(--ink2); font-size: 13px; line-height: 1.9; }
.month-narrative small { display: block; margin-top: 18px; color: var(--muted); line-height: 1.6; }
.month-ai-error { margin-top: 14px !important; color: var(--up) !important; font-size: 11px !important; }
.ai-advice-list { display: flex; flex-direction: column; gap: 7px; margin-top: 18px; }
.ai-advice-list span { position: relative; padding-left: 15px; color: var(--muted); font-size: 11px; line-height: 1.6; }
.ai-advice-list span::before { position: absolute; top: .65em; left: 2px; width: 5px; height: 5px; border-radius: 50%; background: var(--accent); content: ""; }
.month-top-card { padding: 25px 30px 28px; border-radius: 10px; }
.month-top-card h2,
.month-distribution-card h2,
.flow-category-ranking-card h2,
.book-account-overview h2,
.account-distribution-card h2,
.account-flow-card > header h2 { margin: 0; font: 700 20px/1.2 Georgia, "Songti SC", serif; }
.month-top-card > div > p { margin: 22px 0 28px; color: var(--ink2); font-size: 13px; line-height: 1.8; }
.transaction-spotlight-list { display: flex; flex-direction: column; gap: 25px; }
.transaction-spotlight-row { display: grid; grid-template-columns: 36px minmax(0, 1fr) auto; align-items: center; gap: 11px; }
.transaction-spotlight-main { min-width: 0; }
.transaction-spotlight-main > button { display: block; max-width: 100%; overflow: hidden; padding: 0; border: 0; background: transparent; color: var(--ink); font-size: 14px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.transaction-spotlight-main > span { display: flex; min-width: 0; align-items: center; gap: 5px; margin-top: 5px; color: var(--muted); font-size: 10px; }
.transaction-spotlight-main > span button { max-width: 100px; overflow: hidden; padding: 0; border: 0; background: transparent; color: var(--muted); font-size: inherit; text-overflow: ellipsis; white-space: nowrap; }
.transaction-spotlight-main button:hover { color: var(--accent); text-decoration: underline; }
.transaction-spotlight-main > span > * + *::before { margin-right: 5px; color: var(--line2); content: "·"; }
.transaction-spotlight-row > strong { font: 500 16px/1 Georgia, serif; font-variant-numeric: tabular-nums; white-space: nowrap; }
.month-compare-card { overflow: hidden; padding: 0; border-radius: 10px; }
.month-compare-row { display: flex; align-items: center; justify-content: space-between; gap: 20px; min-height: 92px; padding: 20px 26px; border-bottom: 1px solid var(--line); }
.month-compare-row:last-child { border-bottom: 0; }
.month-compare-row > span:first-child { display: flex; align-items: center; gap: 11px; font-size: 16px; }
.month-compare-row > span:last-child { display: grid; grid-template-columns: auto auto; align-items: baseline; justify-items: end; gap: 3px 7px; color: var(--muted); font-size: 10px; }
.month-compare-row strong { font: 650 16px Georgia, serif; }
.month-compare-row small { grid-column: 1 / -1; color: var(--muted); font-size: 11px; }
.expense-dot,
.income-dot,
.balance-dot { display: inline-grid; width: 27px; height: 27px; place-items: center; border: 1px solid currentColor; border-radius: 50%; font-style: normal; }
.expense-dot { color: var(--up); }.income-dot { color: var(--down); }.balance-dot { color: #d19b3f; }
.month-distribution-card { padding: 27px 30px 28px; border-radius: 10px; }
.month-distribution-card > header { margin-bottom: 3px; }
.month-distribution-card :deep(.category-ranking) { margin-top: 10px; padding-top: 22px; border-top: 1px solid var(--line); }
.flow-category-summary-card {
  display: flex;
  min-height: 74px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
  padding: 18px 26px;
  border-radius: 10px;
}
.flow-category-summary-card h2 { margin: 0; font: 700 20px/1.2 Georgia, "Songti SC", serif; }
.flow-category-summary-card > div { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px 20px; color: var(--muted); font-size: 11px; }
.flow-category-summary-card strong,
.flow-category-summary-card b { margin-left: 5px; font-size: 15px; font-variant-numeric: tabular-nums; }
.flow-category-ranking-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: start; gap: 18px; }
.flow-category-ranking-card { min-height: 480px; padding: 27px 30px 30px; border-radius: 10px; }
.flow-category-ranking-card > header { margin-bottom: 31px; }
.account-detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: start; gap: 18px; }
.account-detail-column { display: flex; min-width: 0; flex-direction: column; gap: 18px; }
.book-account-overview { padding: 27px 30px 12px; border-radius: 10px; }
.book-account-overview > header { margin-bottom: 18px; }
.account-overview-row { display: grid; grid-template-columns: 38px minmax(0, 1fr) auto; align-items: center; gap: 12px; min-height: 82px; padding: 13px 7px; border-bottom: 1px solid var(--line); }
.account-overview-row:last-child { border-bottom: 0; }
.account-overview-row > b { font-size: 15px; }
.account-overview-row > span { display: flex; align-items: baseline; gap: 6px; }
.account-overview-row small { color: var(--muted); font-size: 10px; }
.account-overview-row strong { color: var(--ink2); font: 650 15px Georgia, serif; font-variant-numeric: tabular-nums; }
.account-distribution-card { padding: 27px 30px 28px; border-radius: 10px; }
.account-distribution-card > header { margin-bottom: 5px; }
.account-distribution-card :deep(.account-ranking) { margin-top: 8px; padding-top: 24px; border-top: 1px solid var(--line); }
.asset-detail-card { min-height: 630px; }
.debt-summary-card { padding: 24px 30px; border-radius: 10px; }
.debt-summary-card > header > div { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; }
.debt-summary-card h2 { margin: 0; font: 700 19px/1.2 Georgia, "Songti SC", serif; }
.debt-summary-card header span { color: var(--muted); font-size: 10px; }
.debt-summary-card header strong { margin-left: 4px; font-size: 14px; }
.debt-summary-row { display: grid; grid-template-columns: 38px minmax(0, 1fr) auto; align-items: center; gap: 12px; margin-top: 24px; }
.debt-summary-row > span { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.debt-summary-row > span:last-child { align-items: flex-end; }
.debt-summary-row b { overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.debt-summary-row small { color: var(--muted); font-size: 10px; }
.account-flow-card { padding: 27px 30px 10px; border-radius: 10px; }
.account-flow-card > header { margin-bottom: 17px; }
.account-flow-chart { padding: 20px 0 14px; border-top: 1px solid var(--line); }
.account-flow-chart h3 { display: flex; align-items: center; gap: 10px; margin: 0 0 4px; font-size: 15px; font-weight: 650; }
.account-flow-chart h3 i { width: 25px; height: 25px; font-size: 12px; }
.management-dashboard-grid,
.member-dashboard-grid,
.project-category-dashboard-grid,
.project-dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: 18px;
}
.statistics-column { display: flex; min-width: 0; flex-direction: column; gap: 18px; }
.statistics-distribution-card,
.statistics-count-card,
.statistics-debt-card,
.cashflow-compare-card,
.project-ranking-card,
.project-margin-card {
  min-width: 0;
  padding: 27px 30px 24px;
  border-radius: 10px;
}
.statistics-card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 8px;
}
.statistics-card-head h2 { margin: 0; font: 700 20px/1.2 Georgia, "Songti SC", serif; }
.statistics-card-head > span { flex: 0 0 auto; color: var(--muted); font-size: 11px; }
.statistics-card-head strong,
.statistics-card-head b { margin-left: 5px; color: var(--ink2); font-size: 15px; font-variant-numeric: tabular-nums; }
.statistics-card-head strong.income { color: var(--down) !important; }
.statistics-card-head strong.expense { color: var(--up) !important; }
.statistics-count-card { min-height: 320px; }
.statistics-count-card :deep(.ledger-report-chart) { margin-top: 10px; }
.statistics-distribution-card :deep(.resource-ranking),
.statistics-distribution-card :deep(.merchant-ranking) { margin-top: 8px; padding-top: 20px; border-top: 1px solid var(--line); }
.statistics-debt-card { min-height: 150px; }
.statistics-debt-row { display: grid; grid-template-columns: 34px minmax(0, 1fr) auto; align-items: center; gap: 12px; margin-top: 24px; }
.statistics-debt-row > span { display: flex; min-width: 0; flex-direction: column; align-items: flex-end; gap: 3px; }
.statistics-debt-row small { color: var(--muted); font-size: 10px; }
.statistics-debt-row strong { color: var(--ink2); font-size: 14px; font-variant-numeric: tabular-nums; }
.project-balance-card {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 104px;
  padding: 22px 28px;
  border-radius: 10px;
}
.project-balance-card b { font: 700 20px/1.2 Georgia, "Songti SC", serif; }
.project-balance-card > span { display: flex; align-items: baseline; gap: 7px; margin-left: auto; color: var(--muted); font-size: 11px; }
.project-balance-card strong { font: 650 16px Georgia, serif; font-variant-numeric: tabular-nums; }
.cashflow-compare-card { min-height: 260px; }
.cashflow-compare-card > header { margin-bottom: 15px; }
.cashflow-compare-card h2 { margin: 0; font: 700 20px/1.2 Georgia, "Songti SC", serif; }
.cashflow-compare { display: flex; flex-direction: column; gap: 22px; }
.cashflow-compare-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  align-items: center;
  gap: 11px;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  text-align: left;
}
.cashflow-compare-row.clickable { cursor: pointer; }
.cashflow-compare-row.clickable:hover { border-radius: 7px; background: var(--accent-soft); }
.cashflow-compare-main { min-width: 0; }
.cashflow-compare-name { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; margin-bottom: 7px; }
.cashflow-compare-name b { overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.cashflow-compare-name span { flex: 0 0 auto; color: var(--muted); font-size: 10px; }
.cashflow-compare-bars { display: flex; flex-direction: column; gap: 4px; }
.cashflow-bar { display: block; height: 5px; min-width: 0; border-radius: 5px; }
.cashflow-bar.income-bar { background: var(--down); }
.cashflow-bar.expense-bar { background: var(--up); }
.cashflow-compare-values { display: flex; flex-wrap: wrap; gap: 7px 16px; margin-top: 7px; color: var(--muted); font-size: 10px; }
.cashflow-compare-values span { display: inline-flex; align-items: center; gap: 5px; }
.cashflow-compare-values i { display: inline-block; width: 6px; height: 6px; border-radius: 50%; }
.cashflow-compare-values .income-dot { background: var(--down); }
.cashflow-compare-values .expense-dot { background: var(--up); }
.resource-ranking { display: flex; flex-direction: column; gap: 20px; }
.resource-ranking-list { display: flex; flex-direction: column; gap: 20px; }
.resource-ranking-row {
  display: grid;
  grid-template-columns: 20px 34px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  text-align: left;
  animation: ranking-row-in .2s ease both;
}
@keyframes ranking-row-in {
  from { opacity: 0; transform: translateY(-5px) }
  to { opacity: 1; transform: translateY(0) }
}
.ranking-expand-enter-active,
.ranking-expand-leave-active {
  max-height: 88px;
  overflow: hidden;
  transition: max-height .24s ease, opacity .18s ease, transform .24s ease;
}
.ranking-expand-enter-from,
.ranking-expand-leave-to {
  max-height: 0;
  opacity: 0;
  transform: translateY(-8px);
}
.ranking-expand-move { transition: transform .24s ease; }
.resource-ranking-row.clickable { padding: 7px 8px; margin: -7px -8px; border-radius: 6px; cursor: pointer; }
.resource-ranking-row.clickable:hover,
.resource-ranking-row.clickable:focus-visible { background: var(--accent-soft); outline: none; }
.resource-rank { color: var(--muted); font: 500 14px Georgia, serif; text-align: center; }
.resource-ranking-main { min-width: 0; overflow: hidden; }
.resource-ranking-label { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; margin-bottom: 7px; }
.resource-ranking-label b { overflow: hidden; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.resource-ranking-label > span { display: flex; flex: 0 0 auto; align-items: center; gap: 6px; font-variant-numeric: tabular-nums; }
.resource-ranking-label small { color: var(--muted); font-size: 10px; }
.resource-ranking-label i { width: 3px; height: 3px; border-radius: 50%; background: var(--line2); }
.resource-ranking-label strong { font-size: 13px; font-weight: 600; }
.resource-ranking-track { height: 4px; overflow: hidden; border-radius: 4px; background: var(--line); }
.resource-ranking-track span { display: block; height: 100%; border-radius: inherit; }
.resource-ranking-expand { display: flex; align-items: center; justify-content: center; gap: 6px; padding: 6px; border: 0; background: transparent; color: var(--muted); font-size: 11px; }
.resource-ranking-expand svg { width: 14px; height: 14px; transition: transform .16s ease; }
.resource-ranking-expand.expanded svg { transform: rotate(180deg); }
.basic-report-column { display: flex; flex-direction: column; gap: 18px; }
.flow-overview-card { overflow: hidden; padding: 0; border-radius: 10px; }
.flow-summary-banner {
  position: relative;
  min-height: 200px;
  overflow: hidden;
  padding: 28px 30px;
  background:
    radial-gradient(circle at 76% 45%, rgba(255,255,255,.24) 0 9%, transparent 9.5%),
    radial-gradient(circle at 86% 18%, rgba(255,255,255,.14) 0 15%, transparent 15.5%),
    linear-gradient(125deg, color-mix(in srgb, var(--accent) 80%, #18311f), color-mix(in srgb, var(--accent) 66%, #9fb83e) 58%, #9c8735);
  color: #fff;
}
.flow-summary-banner::before,
.flow-summary-banner::after {
  position: absolute;
  border: 1px solid rgba(255,255,255,.2);
  border-radius: 52% 48% 40% 60%;
  content: "";
  transform: rotate(-18deg);
}
.flow-summary-banner::before { right: 8%; bottom: -74px; width: 220px; height: 190px; }
.flow-summary-banner::after { top: -60px; right: 28%; width: 130px; height: 150px; }
.flow-summary-banner > * { position: relative; z-index: 1; }
.flow-summary-banner > span { display: block; margin-bottom: 25px; font-size: 18px; font-weight: 700; }
.flow-summary-banner > small { display: block; margin-bottom: 4px; color: rgba(255,255,255,.8); font-size: 11px; }
.flow-summary-banner > strong {
  display: block;
  overflow: hidden;
  color: #fff !important;
  font: 750 clamp(36px, 5vw, 56px)/1 Georgia, serif;
  letter-spacing: -.02em;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.flow-summary-values { display: flex; align-items: center; gap: 16px; margin-top: 22px; color: rgba(255,255,255,.8); font-size: 11px; }
.flow-summary-values span { display: flex; align-items: baseline; gap: 7px; }
.flow-summary-values b { color: #fff !important; font-size: 14px; }
.flow-summary-values i { width: 1px; height: 17px; background: rgba(255,255,255,.35); }
.milestone-row {
  display: grid;
  grid-template-columns: 34px auto 1fr;
  align-items: center;
  gap: 10px;
  min-height: 76px;
  padding: 17px 28px;
}
.milestone-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: 1px solid color-mix(in srgb, var(--accent) 36%, var(--line));
  border-radius: 50%;
  color: var(--accent);
}
.milestone-row b { font-size: 15px; }
.milestone-row > span:last-child { justify-self: end; color: var(--muted); font-size: 11px; }
.milestone-row strong { margin-left: 5px; color: var(--ink); font: 700 17px Georgia, serif; }
.ranking-card { min-height: 430px; padding: 24px 28px 20px; border-radius: 10px; }
.ranking-card-head,
.report-card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}
.ranking-card-head h2,
.report-card-heading h2 { margin: 0; font: 700 20px/1.2 Georgia, "Songti SC", serif; }
.level-switch { display: inline-flex; padding: 3px; border-radius: 5px; background: var(--paper); }
.level-switch button {
  height: 29px;
  padding: 0 11px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--muted);
  font-size: 11px;
}
.level-switch button.active { background: var(--card); color: var(--accent); font-weight: 700; box-shadow: 0 2px 8px color-mix(in srgb, var(--ink) 9%, transparent); }
.category-ranking { display: flex; flex-direction: column; gap: 19px; }
.category-ranking-row {
  display: grid;
  grid-template-columns: 20px 34px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  text-align: left;
}
.category-ranking-row.clickable {
  padding: 7px 8px;
  margin: -7px -8px;
  border-radius: 6px;
  cursor: pointer;
}
.category-ranking-row.clickable:hover,
.category-ranking-row.clickable:focus-visible { background: var(--accent-soft); outline: none; }
.category-ranking-row.clickable:disabled { cursor: default; opacity: .7; }
.category-rank { color: var(--muted); font: 500 14px Georgia, serif; text-align: center; }
.category-ranking-main { overflow: hidden; }
.category-ranking-label { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; margin-bottom: 7px; }
.category-ranking-label b { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.category-ranking-label > span { display: flex; flex: 0 0 auto; align-items: center; gap: 6px; font-variant-numeric: tabular-nums; }
.category-ranking-label small { color: var(--muted); font-size: 10px; }
.category-ranking-label i { width: 3px; height: 3px; border-radius: 50%; background: var(--line2); }
.category-ranking-label strong { font-size: 12px; font-weight: 600; }
.category-ranking-track { height: 4px; overflow: hidden; border-radius: 4px; background: var(--line); }
.category-ranking-track span { display: block; height: 100%; border-radius: inherit; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 12px; }
.summary-grid.three { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.summary-card,
.milestone-card { min-height: 126px; }
.summary-label { color: var(--muted); font-size: 11px; }
.summary-value,
.milestone-value { display: block; margin: 8px 0 11px; overflow: hidden; font: 700 clamp(24px, 3vw, 34px)/1 Georgia, serif; text-overflow: ellipsis; white-space: nowrap; }
.summary-meta { display: block; color: var(--muted); font-size: 11px; }
.income { color: var(--down) !important; }
.expense { color: var(--up) !important; }
.report-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
.report-card { overflow: hidden; padding: 24px 28px; border-radius: 10px; }
.report-wide { grid-column: 1 / -1; }
.report-card-heading span { overflow: hidden; color: var(--muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.budget-layout { display: grid; grid-template-columns: minmax(240px, .75fr) minmax(0, 1.25fr); align-items: center; gap: 24px; }
.budget-list { display: flex; flex-direction: column; gap: 14px; }
.budget-row > div:first-child { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 6px; font-size: 11px; }
.budget-row span { color: var(--muted); }
.progress-track { height: 5px; overflow: hidden; border-radius: 5px; background: var(--line); }
.progress-track i { display: block; height: 100%; background: var(--accent); }
.progress-track i.over { background: var(--up); }
.report-card :deep(.ui-table) { min-width: 660px; table-layout: fixed; }
.report-card :deep(.ui-table th),
.report-card :deep(.ui-table td) { padding: 10px 12px; }
.report-card :deep(.ui-table td:first-child) { overflow: hidden; color: var(--ink); font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.report-card :deep(.ui-table td:nth-child(n+2)) { font-variant-numeric: tabular-nums; }
.report-card :deep(.ui-table td:last-child),
.report-card :deep(.ui-table th:last-child) { text-align: right; }
.report-card :deep(.ui-table td:nth-child(2)) { display: table-cell; }
.report-card :deep(.ui-table tr:not(.report-table-header) td:nth-child(2):has(.ledger-resource-icon)) { display: flex; align-items: center; gap: 7px; }
.report-table-header th { background: var(--paper); color: var(--muted); font-size: 10px; font-weight: 650; letter-spacing: .08em; }
.category-ranking-row,
.account-ranking-row,
.merchant-ranking-row,
.resource-ranking-row {
  grid-template-columns: 20px 34px minmax(0, 1fr);
  column-gap: 10px;
}
.category-ranking-row :deep(.ledger-resource-icon),
.account-ranking-row :deep(.ledger-resource-icon),
.merchant-ranking-row :deep(.ledger-resource-icon),
.resource-ranking-row :deep(.ledger-resource-icon) {
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 0;
  background: transparent;
}
.category-ranking-row :deep(.ledger-resource-icon svg),
.account-ranking-row :deep(.ledger-resource-icon svg),
.merchant-ranking-row :deep(.ledger-resource-icon svg),
.resource-ranking-row :deep(.ledger-resource-icon svg) { width: 24px; height: 24px; }
.ranking-details {
  min-width: 0;
  display: grid;
  gap: 7px;
}
.ranking-heading {
  display: flex;
  min-width: 0;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}
.ranking-values {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: baseline;
  gap: 7px;
  white-space: nowrap;
}
.ranking-divider {
  width: 4px;
  height: 4px;
  align-self: center;
  border-radius: 50%;
  background: var(--line2);
}
.ranking-track {
  height: 3px;
  overflow: hidden;
  border-radius: 3px;
  background: var(--line);
}
.ranking-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
}
.ranking-name {
  min-width: 0;
  overflow: hidden;
  color: var(--ink);
  font-size: 15px;
  font-weight: 650;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ranking-share {
  color: var(--muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.ranking-amount {
  color: var(--ink2);
  font-size: 15px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
@media (max-width: 1100px) {
  .report-tab-label { padding-left: 14px; }
  .basic-dashboard-grid { gap: 12px; }
  .category-statistics-grid { gap: 12px; }
  .account-dashboard-grid { gap: 12px; }
  .merchant-dashboard-grid { gap: 12px; }
  .month-dashboard-grid,
  .flow-category-ranking-grid,
  .account-detail-grid,
  .management-dashboard-grid,
  .member-dashboard-grid,
  .project-category-dashboard-grid,
  .project-dashboard-grid { gap: 12px; }
  .basic-report-column { gap: 12px; }
  .ranking-card,
  .report-card { padding: 20px; }
}
@media (max-width: 767px) {
  .report-tabbar { min-height: 52px; margin-bottom: 16px; }
  .report-tab-item { min-height: 50px; }
  .report-tab-label { padding: 0 7px 0 13px; font-size: 12px; }
  .report-tab-remove { width: 19px; height: 19px; margin-right: 6px; opacity: 1; }
  .report-library-anchor { padding: 7px; }
  .report-library-button { width: 38px; height: 36px; padding: 0; }
  .report-library-button svg { width: 18px; height: 18px; }
  .report-library-button { font-size: 0; }
  .report-library-popover { right: -1px; width: min(560px, calc(100vw - 28px)); padding: 14px; }
  .library-grid { grid-template-columns: minmax(0, 1fr); max-height: 54vh; overflow-y: auto; }
  .report-content-heading { align-items: flex-start; margin-bottom: 16px; }
  .report-content-heading h1 { font-size: 25px; }
  .report-content-heading span { max-width: 44vw; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .date-trigger { grid-template-columns: 34px minmax(82px, auto) 34px; height: 38px; }
  .date-trigger .date-trigger-label { padding: 0 8px; font-size: 12px; }
  .date-picker-popover { padding: 15px; }
  .custom-date-range { grid-template-columns: minmax(0, 1fr); align-items: stretch; }
  .custom-date-range > span { display: none; }
  .picker-year-grid button,
  .picker-month-grid button { height: 44px; }
  .report-sub-controls { justify-content: stretch; }
  .report-sub-controls label { flex: 1 1 150px; flex-direction: column; align-items: stretch; gap: 4px; }
  .report-sub-controls select { width: 100%; }
  .basic-dashboard-grid { grid-template-columns: minmax(0, 1fr); }
  .category-statistics-grid { grid-template-columns: minmax(0, 1fr); }
  .account-dashboard-grid { grid-template-columns: minmax(0, 1fr); }
  .merchant-dashboard-grid { grid-template-columns: minmax(0, 1fr); }
  .month-dashboard-grid,
  .flow-category-ranking-grid,
  .account-detail-grid,
  .management-dashboard-grid,
  .member-dashboard-grid,
  .project-category-dashboard-grid,
  .project-dashboard-grid { grid-template-columns: minmax(0, 1fr); }
  .month-column,
  .account-detail-column { gap: 10px; }
  .account-overview-card,
  .asset-ranking-card,
  .liability-ranking-card { grid-column: 1; grid-row: auto; }
  .account-summary-banner { min-height: 138px; padding: 17px 22px; }
  .account-summary-banner > span { margin-bottom: 9px; }
  .account-summary-banner > strong { font-size: 34px; }
  .account-summary-values { gap: 10px; margin-top: 10px; }
  .account-summary-values span { gap: 4px; }
  .account-trend-panel { padding: 0 6px 10px; }
  .account-ranking-card { min-height: 0; padding: 20px 16px; }
  .account-ranking-head { margin-bottom: 20px; }
  .account-ranking-list { gap: 20px; }
  .merchant-summary-card,
  .merchant-expense-card,
  .merchant-income-card { grid-column: 1; grid-row: auto; min-height: 0; }
  .merchant-summary-card,
  .merchant-distribution-card { padding: 20px 16px; }
  .merchant-summary-card h2 { margin-bottom: 20px; }
  .merchant-distribution-head { align-items: flex-start; flex-direction: column; gap: 8px; }
  .merchant-distribution-head > div { justify-content: flex-start; }
  .merchant-ranking-list { gap: 20px; }
  .month-summary-banner { min-height: 186px; padding: 21px 18px; }
  .month-summary-head { align-items: flex-start; }
  .month-summary-head .ui-button { padding: 0 8px; }
  .month-summary-banner > small { margin-top: 18px; }
  .month-summary-banner > strong { font-size: 39px; }
  .month-summary-values { gap: 10px; margin-top: 18px; }
  .month-narrative { min-height: 0; padding: 22px 18px; }
  .month-top-card,
  .month-distribution-card,
  .flow-category-ranking-card,
  .book-account-overview,
  .account-distribution-card,
  .debt-summary-card,
  .account-flow-card { min-height: 0; padding: 20px 16px; }
  .month-compare-row { min-height: 82px; padding: 17px 16px; }
  .flow-category-summary-card { align-items: flex-start; flex-direction: column; margin-bottom: 10px; padding: 18px 16px; }
  .flow-category-summary-card > div { justify-content: flex-start; }
  .flow-category-ranking-card > header { margin-bottom: 24px; }
  .account-overview-row { grid-template-columns: 34px minmax(0, 1fr); min-height: 72px; padding: 10px 2px; }
  .account-overview-row > span { grid-column: 2; justify-content: space-between; }
  .asset-detail-card { min-height: 0; }
  .debt-summary-card > header > div { align-items: flex-start; flex-direction: column; gap: 6px; }
  .debt-summary-row { grid-template-columns: 34px minmax(0, 1fr); margin-top: 19px; }
  .debt-summary-row > span:last-child { grid-column: 2; align-items: flex-start; }
  .account-flow-card { padding-bottom: 2px; }
  .category-statistics-card { padding: 20px 16px; }
  .statistics-distribution-card,
  .statistics-count-card,
  .statistics-debt-card,
  .cashflow-compare-card,
  .project-ranking-card,
  .project-margin-card { padding: 20px 16px; }
  .statistics-card-head { align-items: flex-start; flex-direction: column; gap: 7px; }
  .statistics-card-head > span { align-self: flex-start; }
  .statistics-debt-row { grid-template-columns: 34px minmax(0, 1fr); margin-top: 19px; }
  .statistics-debt-row > span { grid-column: 2; align-items: flex-start; }
  .project-balance-card { min-height: 86px; padding: 18px 16px; }
  .project-balance-card b { font-size: 17px; }
  .project-balance-card > span { font-size: 10px; }
  .cashflow-compare-card { min-height: 0; }
  .cashflow-compare { gap: 18px; }
  .category-statistics-head { align-items: flex-start; flex-direction: column; gap: 8px; }
  .category-statistics-total { justify-content: flex-start; }
  .category-level-row { justify-content: space-between; }
  .flow-summary-banner { min-height: 184px; padding: 24px 22px; }
  .flow-summary-banner > span { margin-bottom: 20px; }
  .flow-summary-banner > strong { font-size: 40px; }
  .flow-summary-values { gap: 10px; margin-top: 19px; }
  .flow-summary-values span { gap: 4px; }
  .milestone-row { padding: 15px 20px; }
  .ranking-card { min-height: 0; padding: 18px 16px; }
  .category-ranking { gap: 16px; }
  .category-ranking-row,
  .account-ranking-row,
  .merchant-ranking-row,
  .resource-ranking-row {
    grid-template-columns: 18px 30px minmax(0, 1fr);
    gap: 7px;
  }
  .ranking-name,
  .ranking-amount { font-size: 12px; }
  .ranking-share { font-size: 10px; }
  .ranking-values { gap: 4px; }
  .summary-grid,
  .summary-grid.three { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
  .summary-card,
  .milestone-card { min-height: 112px; padding: 15px; }
  .summary-value,
  .milestone-value { font-size: 25px; }
  .report-grid { grid-template-columns: minmax(0, 1fr); gap: 10px; }
  .report-wide { grid-column: 1; }
  .report-card { padding: 15px; }
  .report-card-heading { align-items: flex-start; flex-direction: column; gap: 3px; }
  .budget-layout { grid-template-columns: minmax(0, 1fr); gap: 12px; }
}
@media (prefers-reduced-motion: reduce) {
  .report-panel-enter-active,
  .report-panel-leave-active,
  .ranking-expand-enter-active,
  .ranking-expand-leave-active,
  .ranking-expand-move { transition: none }
  .account-ranking-row,
  .merchant-ranking-row,
  .resource-ranking-row { animation: none }
}
@media (max-width: 420px) {
  .summary-grid,
  .summary-grid.three { grid-template-columns: minmax(0, 1fr); }
  .report-content-heading { gap: 10px; }
  .report-content-heading span { display: none; }
  .date-trigger { grid-template-columns: 31px minmax(74px, auto) 31px; }
  .date-trigger .date-trigger-label { padding: 0 5px; }
  .flow-summary-values { align-items: flex-start; flex-direction: column; gap: 5px; }
  .flow-summary-values i { display: none; }
  .account-summary-values { align-items: flex-start; flex-direction: column; gap: 5px; }
  .account-summary-values i { display: none; }
  .month-summary-head { flex-direction: column; }
  .month-summary-values { align-items: flex-start; flex-direction: column; gap: 5px; }
  .month-summary-values i { display: none; }
  .month-compare-row { align-items: flex-start; flex-direction: column; gap: 12px; }
  .month-compare-row > span:last-child { width: 100%; justify-content: start; justify-items: start; }
  .transaction-spotlight-row { grid-template-columns: 31px minmax(0, 1fr); }
  .transaction-spotlight-row > strong { grid-column: 2; }
  .merchant-summary-content { align-items: flex-start; flex-direction: column; gap: 17px; }
  .merchant-summary-values { width: 100%; align-items: stretch; }
  .merchant-summary-values span { display: flex; justify-content: space-between; }
  .milestone-row { grid-template-columns: 30px 1fr; }
  .milestone-row > span:last-child { grid-column: 1 / -1; justify-self: start; padding-left: 40px; }
  .category-ranking-row,
  .account-ranking-row,
  .merchant-ranking-row,
  .resource-ranking-row {
    grid-template-columns: 17px 28px minmax(0, 1fr);
  }
  .ranking-name,
  .ranking-amount { font-size: 11px; }
  .ranking-share { font-size: 9px; }
}
</style>

<!-- The rankings are defined with defineComponent in this SFC. Their inner nodes
     do not inherit this view's scoped attribute, so these selectors are
     namespaced to the report root instead of relying on scoped CSS. -->
<style>
.ledger-reports .category-ranking,
.ledger-reports .account-ranking-list,
.ledger-reports .merchant-ranking-list,
.ledger-reports .resource-ranking-list {
  display: flex;
  flex-direction: column;
  gap: 22px;
  min-width: 0;
}
.ledger-reports .account-ranking,
.ledger-reports .merchant-ranking,
.ledger-reports .resource-ranking { min-width: 0; }
.ledger-reports .category-ranking-row,
.ledger-reports .account-ranking-row,
.ledger-reports .merchant-ranking-row,
.ledger-reports .resource-ranking-row {
  display: grid;
  grid-template-columns: 22px 34px minmax(0, 1fr);
  align-items: center;
  column-gap: 16px;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  text-align: left;
}
.ledger-reports .category-ranking-row.clickable,
.ledger-reports .account-ranking-row.clickable,
.ledger-reports .merchant-ranking-row.clickable,
.ledger-reports .resource-ranking-row.clickable {
  padding: 7px 8px;
  margin: -7px -8px;
  border-radius: 6px;
  cursor: pointer;
}
.ledger-reports .category-ranking-row.clickable:hover,
.ledger-reports .category-ranking-row.clickable:focus-visible,
.ledger-reports .account-ranking-row.clickable:hover,
.ledger-reports .account-ranking-row.clickable:focus-visible,
.ledger-reports .merchant-ranking-row.clickable:hover,
.ledger-reports .merchant-ranking-row.clickable:focus-visible,
.ledger-reports .resource-ranking-row.clickable:hover,
.ledger-reports .resource-ranking-row.clickable:focus-visible {
  background: var(--accent-soft);
  outline: none;
}
.ledger-reports .category-ranking-row.clickable:disabled,
.ledger-reports .merchant-ranking-row.clickable:disabled {
  cursor: default;
  opacity: .7;
}
.ledger-reports .category-rank,
.ledger-reports .account-rank,
.ledger-reports .merchant-rank,
.ledger-reports .resource-rank {
  color: var(--muted);
  font: 500 15px Georgia, serif;
  text-align: left;
}
.ledger-reports .category-ranking-row .ledger-resource-icon,
.ledger-reports .account-ranking-row .ledger-resource-icon,
.ledger-reports .merchant-ranking-row .ledger-resource-icon,
.ledger-reports .resource-ranking-row .ledger-resource-icon {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border: 0;
  border-radius: 0;
  background: transparent;
}
.ledger-reports .category-ranking-row .ledger-resource-icon svg,
.ledger-reports .account-ranking-row .ledger-resource-icon svg,
.ledger-reports .merchant-ranking-row .ledger-resource-icon svg,
.ledger-reports .resource-ranking-row .ledger-resource-icon svg { width: 25px; height: 25px; }
.ledger-reports .ranking-details {
  display: grid;
  min-width: 0;
  gap: 7px;
}
.ledger-reports .ranking-heading {
  display: flex;
  min-width: 0;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.ledger-reports .ranking-name {
  min-width: 0;
  overflow: hidden;
  color: var(--ink);
  font-size: 16px;
  font-weight: 650;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ledger-reports .ranking-values {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: baseline;
  gap: 7px;
  white-space: nowrap;
}
.ledger-reports .ranking-share {
  color: var(--muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.ledger-reports .ranking-divider {
  width: 4px;
  height: 4px;
  align-self: center;
  border-radius: 50%;
  background: var(--line2);
}
.ledger-reports .ranking-amount {
  color: var(--ink2);
  font-size: 15px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}
.ledger-reports .ranking-track {
  height: 4px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--line);
}
.ledger-reports .ranking-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
}
.ledger-reports .account-ranking-expand,
.ledger-reports .merchant-ranking-expand,
.ledger-reports .resource-ranking-expand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  margin-top: 24px;
  padding: 6px;
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 11px;
  cursor: pointer;
}
.ledger-reports .account-ranking-expand svg,
.ledger-reports .merchant-ranking-expand svg,
.ledger-reports .resource-ranking-expand svg {
  width: 14px;
  height: 14px;
  transition: transform .16s ease;
}
.ledger-reports .account-ranking-expand.expanded svg,
.ledger-reports .merchant-ranking-expand.expanded svg,
.ledger-reports .resource-ranking-expand.expanded svg { transform: rotate(180deg); }
.ledger-reports .ranking-expand-enter-active,
.ledger-reports .ranking-expand-leave-active {
  max-height: 88px;
  overflow: hidden;
  transition: max-height .24s ease, opacity .18s ease, transform .24s ease;
}
.ledger-reports .ranking-expand-enter-from,
.ledger-reports .ranking-expand-leave-to {
  max-height: 0;
  opacity: 0;
  transform: translateY(-8px);
}
.ledger-reports .ranking-expand-move { transition: transform .24s ease; }
@media (max-width: 767px) {
  .ledger-reports .category-ranking-row,
  .ledger-reports .account-ranking-row,
  .ledger-reports .merchant-ranking-row,
  .ledger-reports .resource-ranking-row {
    grid-template-columns: 18px 30px minmax(0, 1fr);
    column-gap: 9px;
  }
  .ledger-reports .ranking-name,
  .ledger-reports .ranking-amount { font-size: 12px; }
  .ledger-reports .ranking-share { font-size: 10px; }
  .ledger-reports .ranking-values { gap: 4px; }
}
@media (max-width: 420px) {
  .ledger-reports .ranking-name,
  .ledger-reports .ranking-amount { font-size: 11px; }
  .ledger-reports .ranking-share { font-size: 9px; }
}
@media (prefers-reduced-motion: reduce) {
  .ledger-reports .ranking-expand-enter-active,
  .ledger-reports .ranking-expand-leave-active,
  .ledger-reports .ranking-expand-move { transition: none; }
}
</style>
