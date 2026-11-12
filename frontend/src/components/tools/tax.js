/**
 * 个人所得税测算（中国境内工资薪金 + 全年一次性奖金）。
 *
 * 口径：
 *  - 工资薪金按「累计预扣法」逐月计算，起征点 5000 元/月；
 *  - 年度综合所得税率表与月度税率表写死为常量，随政策调整需要人工复核；
 *  - 年终奖单独计税属于阶段性政策（执行至 2027-12-31），到期后应重新评估。
 *
 * 结果仅用于测算，实际以扣缴义务人申报与税务机关口径为准。
 */

/** 政策复核日期：税率表或年终奖优惠调整后请同步更新此常量与页面文案。 */
export const TAX_POLICY_REVIEW = '2027-12-31'

export const YEARLY_BRACKETS = [
  { upTo: 36000, rate: 0.03, deduct: 0 },
  { upTo: 144000, rate: 0.10, deduct: 2520 },
  { upTo: 300000, rate: 0.20, deduct: 16920 },
  { upTo: 420000, rate: 0.25, deduct: 31920 },
  { upTo: 660000, rate: 0.30, deduct: 52920 },
  { upTo: 960000, rate: 0.35, deduct: 85920 },
  { upTo: Infinity, rate: 0.45, deduct: 181920 }
]

export const MONTHLY_BRACKETS = [
  { upTo: 3000, rate: 0.03, deduct: 0 },
  { upTo: 12000, rate: 0.10, deduct: 210 },
  { upTo: 25000, rate: 0.20, deduct: 1410 },
  { upTo: 35000, rate: 0.25, deduct: 2660 },
  { upTo: 55000, rate: 0.30, deduct: 4410 },
  { upTo: 80000, rate: 0.35, deduct: 7160 },
  { upTo: Infinity, rate: 0.45, deduct: 15160 }
]

const round2 = value => Math.round(value * 100) / 100

function bracketOf(brackets, value) {
  return brackets.find(bracket => value <= bracket.upTo) || brackets[brackets.length - 1]
}

/** 年度综合所得应纳税额（元）。 */
export function annualTax(taxable) {
  const value = Number(taxable)
  if (!Number.isFinite(value) || value <= 0) return 0
  const bracket = bracketOf(YEARLY_BRACKETS, value)
  return round2(Math.max(0, value * bracket.rate - bracket.deduct))
}

/** 全年一次性奖金按「单独计税」计算（奖金 ÷ 12 查月度税率表）。 */
export function bonusTaxSeparate(bonus) {
  const value = Number(bonus)
  if (!Number.isFinite(value) || value <= 0) return 0
  const bracket = bracketOf(MONTHLY_BRACKETS, value / 12)
  return round2(Math.max(0, value * bracket.rate - bracket.deduct))
}

/**
 * 累计预扣法逐月测算。
 * @returns rows: [{ month, income, taxable, tax, net }]，均为元
 */
export function monthlyWithholding({
  monthlyIncome = 0,
  socialInsuranceMonthly = 0,
  specialAdditionalMonthly = 0,
  otherMonthly = 0,
  months = 12
} = {}) {
  const income = Number(monthlyIncome)
  if (!Number.isFinite(income) || income < 0) throw new RangeError('月收入无效')
  const count = Math.round(months)
  if (!(count >= 1) || count > 12) throw new RangeError('月份数量需要在 1 到 12 之间')

  const social = Number(socialInsuranceMonthly) || 0
  const special = Number(specialAdditionalMonthly) || 0
  const other = Number(otherMonthly) || 0
  const threshold = 5000

  const rows = []
  let paidTax = 0
  for (let month = 1; month <= count; month += 1) {
    const cumulativeIncome = income * month
    const cumulativeDeduction = (threshold + social + special + other) * month
    const cumulativeTaxable = Math.max(0, cumulativeIncome - cumulativeDeduction)
    const cumulativeTax = annualTax(cumulativeTaxable)
    const tax = round2(Math.max(0, cumulativeTax - paidTax))
    paidTax = round2(paidTax + tax)
    rows.push({
      month,
      income: round2(income),
      taxable: round2(cumulativeTaxable),
      tax,
      net: round2(income - social - tax)
    })
  }

  return {
    rows,
    threshold,
    totalTax: paidTax,
    totalIncome: round2(income * count),
    totalNet: round2(rows.reduce((sum, row) => sum + row.net, 0))
  }
}

/**
 * 年终奖两种计税方式对比。
 * @param annualTaxable 不含年终奖的年度应纳税所得额（已扣起征点、五险一金、专项附加）
 */
export function compareBonus({ annualTaxable = 0, bonus = 0 } = {}) {
  const taxable = Math.max(0, Number(annualTaxable) || 0)
  const bonusValue = Math.max(0, Number(bonus) || 0)
  if (bonusValue === 0) {
    const salaryTax = annualTax(taxable)
    return {
      separate: { salaryTax, bonusTax: 0, total: salaryTax },
      combined: { total: salaryTax },
      better: 'SAME',
      delta: 0
    }
  }
  const salaryTax = annualTax(taxable)
  const bonusTax = bonusTaxSeparate(bonusValue)
  const separateTotal = round2(salaryTax + bonusTax)
  const combinedTotal = annualTax(taxable + bonusValue)
  const delta = round2(Math.abs(separateTotal - combinedTotal))
  return {
    separate: { salaryTax, bonusTax, total: separateTotal },
    combined: { total: combinedTotal },
    better: separateTotal === combinedTotal ? 'SAME' : (separateTotal < combinedTotal ? 'SEPARATE' : 'COMBINED'),
    delta
  }
}

/** 五险一金按比例估算。仅做常见比例，不做各城市基数上下限。 */
export function socialInsuranceEstimate({
  salaryBase = 0,
  pension = 0.08,
  medical = 0.02,
  medicalFixed = 3,
  unemployment = 0.005,
  housingFund = 0.12
} = {}) {
  const base = Number(salaryBase)
  if (!Number.isFinite(base) || base < 0) throw new RangeError('缴费基数无效')
  const pensionValue = base * Number(pension || 0)
  const medicalValue = base * Number(medical || 0) + Number(medicalFixed || 0)
  const unemploymentValue = base * Number(unemployment || 0)
  const housingValue = base * Number(housingFund || 0)
  return {
    pension: round2(pensionValue),
    medical: round2(medicalValue),
    unemployment: round2(unemploymentValue),
    housingFund: round2(housingValue),
    total: round2(pensionValue + medicalValue + unemploymentValue + housingValue)
  }
}
