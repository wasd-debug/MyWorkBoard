/**
 * 房贷摊销计算。所有中间金额用「分」为单位的整数推进，避免浮点累加导致
 * 末期余额不为 0 或本金合计对不上；只在输出时转成两位小数的元。
 *
 * 不变量（单测覆盖）：
 *   1. 每期本金之和 === 贷款本金
 *   2. 最后一期结束后剩余本金 === 0
 *   3. 总还款额 === 本金 + 总利息
 */

export const METHOD = {
  EQUAL_INSTALLMENT: 'EQUAL_INSTALLMENT',
  EQUAL_PRINCIPAL: 'EQUAL_PRINCIPAL'
}

export const STRATEGY = {
  SHORTEN_TERM: 'SHORTEN_TERM',
  REDUCE_PAYMENT: 'REDUCE_PAYMENT'
}

export function toCents(yuan) {
  const value = Number(yuan)
  if (!Number.isFinite(value)) throw new RangeError('金额无效')
  return Math.round(value * 100)
}

export function fromCents(cents) {
  return Math.round(cents) / 100
}

function assertLoan({ principal, annualRatePct, months, method }) {
  if (!(principal > 0)) throw new RangeError('贷款金额需要大于 0')
  if (!(months >= 1) || months > 600) throw new RangeError('贷款期限需要在 1 到 600 期之间')
  if (!(annualRatePct >= 0) || annualRatePct > 30) throw new RangeError('年利率需要在 0% 到 30% 之间')
  if (method !== METHOD.EQUAL_INSTALLMENT && method !== METHOD.EQUAL_PRINCIPAL) {
    throw new RangeError('还款方式无效')
  }
}

function planCents(principalCents, annualRatePct, months, method) {
  const monthlyRate = annualRatePct / 100 / 12
  const schedule = []
  let balance = principalCents

  if (method === METHOD.EQUAL_PRINCIPAL) {
    const basePrincipal = Math.floor(principalCents / months)
    for (let period = 1; period <= months; period += 1) {
      const interest = Math.round(balance * monthlyRate)
      const principalPart = period === months ? balance : Math.min(basePrincipal, balance)
      balance -= principalPart
      schedule.push({ period, payment: principalPart + interest, principal: principalPart, interest, balance })
    }
  } else {
    const factor = Math.pow(1 + monthlyRate, months)
    const fixedPayment = monthlyRate === 0
      ? Math.round(principalCents / months)
      : Math.round((principalCents * monthlyRate * factor) / (factor - 1))
    for (let period = 1; period <= months; period += 1) {
      const interest = Math.round(balance * monthlyRate)
      let principalPart = fixedPayment - interest
      let payment = fixedPayment
      if (period === months || principalPart >= balance) {
        principalPart = balance
        payment = principalPart + interest
      } else if (principalPart <= 0) {
        // 月供不足以覆盖当期利息（极端参数）：只还息不还本，由调用方看到「本金未减少」。
        principalPart = 0
        payment = interest
      }
      balance -= principalPart
      schedule.push({ period, payment, principal: principalPart, interest, balance })
    }
  }

  const totalInterestCents = schedule.reduce((sum, row) => sum + row.interest, 0)
  const totalPaymentCents = schedule.reduce((sum, row) => sum + row.payment, 0)
  return {
    method,
    months,
    principalCents,
    fixedPaymentCents: schedule[0] ? schedule[0].payment : 0,
    totalInterestCents,
    totalPaymentCents,
    schedule
  }
}

function toView(plan) {
  return {
    method: plan.method,
    months: plan.months,
    principal: fromCents(plan.principalCents),
    monthlyPayment: fromCents(plan.fixedPaymentCents),
    firstPayment: fromCents(plan.schedule[0] ? plan.schedule[0].payment : 0),
    lastPayment: fromCents(plan.schedule.length ? plan.schedule[plan.schedule.length - 1].payment : 0),
    totalInterest: fromCents(plan.totalInterestCents),
    totalPayment: fromCents(plan.totalPaymentCents),
    schedule: plan.schedule.map(row => ({
      period: row.period,
      payment: fromCents(row.payment),
      principal: fromCents(row.principal),
      interest: fromCents(row.interest),
      balance: fromCents(row.balance)
    }))
  }
}

/** 单笔贷款（商贷或公积金）。 */
export function amortize({ principal, annualRatePct, months, method = METHOD.EQUAL_INSTALLMENT }) {
  assertLoan({ principal, annualRatePct, months, method })
  return toView(planCents(toCents(principal), Number(annualRatePct), Math.round(months), method))
}

/**
 * 组合贷：各段独立摊销后按月序相加。期限较短的段结束后该段月供归零，
 * 因此总月供会在该时点自然下降。
 */
export function amortizeCombined(segments) {
  if (!Array.isArray(segments) || segments.length === 0) throw new RangeError('至少需要一段贷款')
  const plans = segments.map(segment => {
    assertLoan({
      principal: segment.principal,
      annualRatePct: segment.annualRatePct,
      months: segment.months,
      method: segment.method || METHOD.EQUAL_INSTALLMENT
    })
    const plan = planCents(toCents(segment.principal), Number(segment.annualRatePct),
      Math.round(segment.months), segment.method || METHOD.EQUAL_INSTALLMENT)
    return { ...plan, label: segment.label || '', annualRatePct: Number(segment.annualRatePct) }
  })
  const maxMonths = Math.max(...plans.map(plan => plan.months))
  const schedule = []
  for (let period = 1; period <= maxMonths; period += 1) {
    let payment = 0
    let principalPart = 0
    let interest = 0
    let balance = 0
    for (const plan of plans) {
      const row = plan.schedule[period - 1]
      if (!row) continue
      payment += row.payment
      principalPart += row.principal
      interest += row.interest
      balance += row.balance
    }
    schedule.push({ period, payment, principal: principalPart, interest, balance })
  }
  const totalInterestCents = schedule.reduce((sum, row) => sum + row.interest, 0)
  const totalPaymentCents = schedule.reduce((sum, row) => sum + row.payment, 0)
  const principalCents = plans.reduce((sum, plan) => sum + plan.principalCents, 0)
  return {
    segments: plans.map(plan => ({
      label: plan.label,
      principal: fromCents(plan.principalCents),
      months: plan.months,
      annualRatePct: plan.annualRatePct,
      method: plan.method,
      monthlyPayment: fromCents(plan.fixedPaymentCents),
      totalInterest: fromCents(plan.totalInterestCents)
    })),
    months: maxMonths,
    principal: fromCents(principalCents),
    monthlyPayment: fromCents(schedule[0] ? schedule[0].payment : 0),
    firstPayment: fromCents(schedule[0] ? schedule[0].payment : 0),
    lastPayment: fromCents(schedule.length ? schedule[schedule.length - 1].payment : 0),
    totalInterest: fromCents(totalInterestCents),
    totalPayment: fromCents(totalPaymentCents),
    schedule: schedule.map(row => ({
      period: row.period,
      payment: fromCents(row.payment),
      principal: fromCents(row.principal),
      interest: fromCents(row.interest),
      balance: fromCents(row.balance)
    }))
  }
}

/**
 * 提前还款测算。
 * @param paidPeriods 已还期数（0 表示放款后立即还款）
 * @param amount 提前还款金额（元）
 * @param strategy SHORTEN_TERM 保持月供、缩短期限；REDUCE_PAYMENT 保持期限、降低月供
 */
export function prepaymentPlan({
  principal, annualRatePct, months, method = METHOD.EQUAL_INSTALLMENT,
  paidPeriods = 0, amount = 0, strategy = STRATEGY.SHORTEN_TERM
}) {
  assertLoan({ principal, annualRatePct, months, method })
  if (!(amount > 0)) throw new RangeError('提前还款金额需要大于 0')
  if (strategy !== STRATEGY.SHORTEN_TERM && strategy !== STRATEGY.REDUCE_PAYMENT) {
    throw new RangeError('提前还款方式无效')
  }
  const totalMonths = Math.round(months)
  const paid = Math.round(paidPeriods)
  if (!(paid >= 0) || paid > totalMonths) throw new RangeError('已还期数超出范围')

  const plan = planCents(toCents(principal), Number(annualRatePct), totalMonths, method)
  const balanceBeforeCents = paid === 0 ? plan.principalCents
    : (plan.schedule[paid - 1] ? plan.schedule[paid - 1].balance : 0)
  const remainingMonths = totalMonths - paid
  const interestBeforeCents = plan.schedule.slice(paid).reduce((sum, row) => sum + row.interest, 0)
  const monthlyRate = Number(annualRatePct) / 100 / 12

  const payCents = Math.min(toCents(amount), balanceBeforeCents)
  const balanceAfterCents = balanceBeforeCents - payCents

  if (balanceAfterCents <= 0) {
    return {
      strategy,
      payoff: true,
      amount: fromCents(payCents),
      balanceBefore: fromCents(balanceBeforeCents),
      balanceAfter: 0,
      interestBefore: fromCents(interestBeforeCents),
      interestAfter: 0,
      interestSaved: fromCents(interestBeforeCents),
      monthsBefore: remainingMonths,
      monthsAfter: 0,
      monthsSaved: remainingMonths,
      monthlyPaymentBefore: fromCents(plan.fixedPaymentCents),
      monthlyPaymentAfter: 0
    }
  }

  let newMonths = remainingMonths
  let newPlan
  if (strategy === STRATEGY.SHORTEN_TERM) {
    if (method === METHOD.EQUAL_PRINCIPAL) {
      const basePrincipal = Math.floor(plan.principalCents / totalMonths)
      newMonths = basePrincipal > 0 ? Math.ceil(balanceAfterCents / basePrincipal) : remainingMonths
      newPlan = planCents(balanceAfterCents, Number(annualRatePct), newMonths, method)
    } else {
      const payment = plan.fixedPaymentCents
      if (monthlyRate === 0) {
        newMonths = Math.ceil(balanceAfterCents / payment)
      } else if (payment <= balanceAfterCents * monthlyRate) {
        // 固定月供还不上利息，缩期限无解，退化为「一次性结清」的提示。
        newMonths = remainingMonths
      } else {
        newMonths = Math.ceil(
          Math.log(payment / (payment - balanceAfterCents * monthlyRate)) / Math.log(1 + monthlyRate)
        )
      }
      newMonths = Math.max(1, Math.min(newMonths, remainingMonths))
      newPlan = planCents(balanceAfterCents, Number(annualRatePct), newMonths, method)
    }
  } else {
    newPlan = planCents(balanceAfterCents, Number(annualRatePct), remainingMonths, method)
  }

  const interestAfterCents = newPlan.totalInterestCents
  return {
    strategy,
    payoff: false,
    amount: fromCents(payCents),
    balanceBefore: fromCents(balanceBeforeCents),
    balanceAfter: fromCents(balanceAfterCents),
    interestBefore: fromCents(interestBeforeCents),
    interestAfter: fromCents(interestAfterCents),
    interestSaved: fromCents(Math.max(0, interestBeforeCents - interestAfterCents)),
    monthsBefore: remainingMonths,
    monthsAfter: newMonths,
    monthsSaved: Math.max(0, remainingMonths - newMonths),
    monthlyPaymentBefore: fromCents(plan.fixedPaymentCents),
    monthlyPaymentAfter: fromCents(newPlan.fixedPaymentCents)
  }
}
