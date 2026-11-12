<template>
  <div>
    <section class="tool-card">
      <header><h2>收入与扣除</h2><small>按工资薪金累计预扣法测算</small></header>
      <div class="tool-grid">
        <label class="tool-field"><span>月收入（元）</span><input v-model.number="monthlyIncome" type="number" min="0" step="1000" /></label>
        <label class="tool-field"><span>专项附加扣除（元/月）</span><input v-model.number="specialAdditional" type="number" min="0" step="500" /></label>
        <label class="tool-field"><span>其他扣除（元/月）</span><input v-model.number="otherMonthly" type="number" min="0" step="100" /></label>
        <label class="tool-field"><span>测算月数</span><input v-model.number="months" type="number" min="1" max="12" step="1" /></label>
      </div>

      <div class="tool-row">
        <div class="tool-segmented" role="tablist" aria-label="五险一金输入方式">
          <button type="button" role="tab" :aria-selected="socialMode === 'estimate'" :class="{ on: socialMode === 'estimate' }" @click="socialMode = 'estimate'">按比例估算</button>
          <button type="button" role="tab" :aria-selected="socialMode === 'manual'" :class="{ on: socialMode === 'manual' }" @click="socialMode = 'manual'">直接填合计</button>
        </div>
      </div>

      <div class="tool-grid">
        <label v-if="socialMode === 'estimate'" class="tool-field">
          <span>社保缴费基数（元）</span>
          <input v-model.number="salaryBase" type="number" min="0" step="1000" />
        </label>
        <label v-else class="tool-field">
          <span>五险一金合计（元/月）</span>
          <input v-model.number="socialManual" type="number" min="0" step="100" />
        </label>
        <div class="tool-field">
          <span>五险一金（元/月）</span>
          <div class="tool-stat" style="padding:8px 10px"><b>{{ money(social) }}</b><span>{{ socialMode === 'estimate' ? '养老 8% · 医疗 2%+3 · 失业 0.5% · 公积金 12%' : '按填写的合计计算' }}</span></div>
        </div>
      </div>

      <p class="tool-hint">专项附加扣除常见额度：子女教育 2000/孩、3 岁以下照护 2000/孩、房贷利息 1000、住房租金 1500、赡养老人 3000（独生）。仅作提示，请按实际申报填写。</p>
      <p v-if="result.error" class="tool-error" role="alert">{{ result.error }}</p>
    </section>

    <section v-if="result.withholding" class="tool-card">
      <header><h2>个税测算</h2><small>起征点 {{ result.withholding.threshold }} 元/月</small></header>
      <div class="tool-stats">
        <div class="tool-stat is-accent"><b>{{ money(result.averageNet) }}</b><span>税后月均（元）</span></div>
        <div class="tool-stat"><b>{{ money(result.withholding.totalTax) }}</b><span>个税合计（元）</span></div>
        <div class="tool-stat"><b>{{ money(social) }}</b><span>五险一金（元/月）</span></div>
        <div class="tool-stat"><b>{{ result.effectiveRate }}%</b><span>实际税负率</span></div>
      </div>

      <div class="tool-table-wrap">
        <table class="tool-table">
          <thead><tr><th>月份</th><th>累计应纳税所得额</th><th>当月预扣</th><th>税后到手</th></tr></thead>
          <tbody>
            <tr v-for="row in result.withholding.rows" :key="row.month">
              <td>第 {{ row.month }} 月</td>
              <td>{{ money(row.taxable) }}</td>
              <td>{{ money(row.tax) }}</td>
              <td>{{ money(row.net) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="tool-disclaimer">测算结果仅供参考，实际以扣缴义务人申报与税务机关口径为准。</p>
    </section>

    <section class="tool-card">
      <header><h2>年终奖计税对比</h2><small>单独计税 vs 并入综合所得</small></header>
      <div class="tool-grid">
        <label class="tool-field"><span>年终奖（元）</span><input v-model.number="bonus" type="number" min="0" step="1000" /></label>
      </div>
      <template v-if="result.bonus">
        <div class="tool-table-wrap">
          <table class="tool-table">
            <thead><tr><th>方案</th><th>工资个税</th><th>年终奖个税</th><th>合计</th></tr></thead>
            <tbody>
              <tr>
                <td>单独计税</td>
                <td>{{ money(result.bonus.separate.salaryTax) }}</td>
                <td>{{ money(result.bonus.separate.bonusTax) }}</td>
                <td>{{ money(result.bonus.separate.total) }}</td>
              </tr>
              <tr>
                <td>并入综合所得</td>
                <td colspan="2">—</td>
                <td>{{ money(result.bonus.combined.total) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="tool-hint">
          <template v-if="result.bonus.better === 'SAME'">两种口径税额相同。</template>
          <template v-else-if="result.bonus.better === 'SEPARATE'">单独计税少缴 {{ money(result.bonus.delta) }} 元。</template>
          <template v-else>并入综合所得少缴 {{ money(result.bonus.delta) }} 元。</template>
        </p>
      </template>
      <p v-else class="tool-hint">填入年终奖后显示两种计税口径的差额。</p>
      <p class="tool-disclaimer">年终奖单独计税为阶段性政策，执行至 {{ policyReview }}，到期后请以当年公告为准。</p>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import {
  TAX_POLICY_REVIEW, compareBonus, monthlyWithholding, socialInsuranceEstimate
} from './tax.js'

const monthlyIncome = ref(30_000)
const specialAdditional = ref(3_000)
const otherMonthly = ref(0)
const months = ref(12)
const socialMode = ref('estimate')
const salaryBase = ref(30_000)
const socialManual = ref(5_000)
const bonus = ref(60_000)
const policyReview = TAX_POLICY_REVIEW

const social = computed(() => {
  if (socialMode.value === 'manual') return Math.max(0, Number(socialManual.value) || 0)
  try {
    return socialInsuranceEstimate({ salaryBase: Number(salaryBase.value) }).total
  } catch {
    return 0
  }
})

const result = computed(() => {
  try {
    const withholding = monthlyWithholding({
      monthlyIncome: Number(monthlyIncome.value),
      socialInsuranceMonthly: social.value,
      specialAdditionalMonthly: Number(specialAdditional.value) || 0,
      otherMonthly: Number(otherMonthly.value) || 0,
      months: Math.round(Number(months.value))
    })
    const totalIncome = Number(monthlyIncome.value) * Math.round(Number(months.value))
    const averageNet = withholding.rows.length
      ? withholding.totalNet / withholding.rows.length
      : 0
    const annualTaxable = Math.max(0,
      (Number(monthlyIncome.value) - social.value - (Number(specialAdditional.value) || 0)
        - (Number(otherMonthly.value) || 0) - withholding.threshold) * Math.round(Number(months.value)))
    return {
      withholding,
      averageNet,
      effectiveRate: totalIncome > 0
        ? ((withholding.totalTax / totalIncome) * 100).toFixed(1)
        : '0.0',
      bonus: compareBonus({ annualTaxable, bonus: Number(bonus.value) || 0 }),
      error: ''
    }
  } catch (error) {
    return { withholding: null, error: error.message }
  }
})

function money(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '—'
  return number.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
</script>

<style scoped>
.tool-field .tool-stat { background: var(--paper); }
</style>
