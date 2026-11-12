import { addDate, todayKey } from './dateMath.js'

/**
 * 戒烟日记的纯展示逻辑。与后端 QuitSmokingSummaryCalculator 保持同一口径：
 *  - 当天吸烟支数 > 0 视为断签；
 *  - 没有记录的日期视为「未记录」而不是「复吸」，仍计入连续天数。
 */

export const DAY_STATUS = {
  CLEAN: 'clean',
  RELAPSE: 'relapse',
  UNLOGGED: 'unlogged'
}

export function dayStatus(smokedCount) {
  if (smokedCount === null || smokedCount === undefined || smokedCount === '') return DAY_STATUS.UNLOGGED
  return Number(smokedCount) > 0 ? DAY_STATUS.RELAPSE : DAY_STATUS.CLEAN
}

/** 最近 count 天（含今天）的三态序列，按时间升序，供热力条渲染。 */
export function timeline(days = {}, today = todayKey(), count = 30) {
  const result = []
  for (let offset = count - 1; offset >= 0; offset -= 1) {
    const date = addDate(today, { days: -offset })
    const entry = days[date]
    const smoked = entry === undefined || entry === null
      ? null
      : Number(entry.smokedCount ?? entry)
    result.push({ date, smoked, status: dayStatus(smoked) })
  }
  return result
}

/** 从今天往前数连续无烟天数；今天已吸烟返回 0。start 用于限制回溯范围（YYYY-MM-DD）。 */
export function computeStreak(days = {}, today = todayKey(), { start = '', maxDays = 3650 } = {}) {
  let streak = 0
  for (let offset = 0; offset < maxDays; offset += 1) {
    const date = addDate(today, { days: -offset })
    if (start && date < start) break
    const entry = days[date]
    const smoked = entry === undefined || entry === null ? 0 : Number(entry.smokedCount ?? entry)
    if (smoked > 0) break
    streak += 1
  }
  return streak
}

/** 区间内最长连续无烟天数（用于本地乐观展示，服务端返回后以服务端为准）。 */
export function computeLongestStreak(days = {}, start, today = todayKey()) {
  let longest = 0
  let run = 0
  let cursor = start
  let guard = 0
  while (cursor && cursor <= today && guard < 3650) {
    const entry = days[cursor]
    const smoked = entry === undefined || entry === null ? 0 : Number(entry.smokedCount ?? entry)
    if (smoked > 0) {
      run = 0
    } else {
      run += 1
      if (run > longest) longest = run
    }
    cursor = addDate(cursor, { days: 1 })
    guard += 1
  }
  return longest
}

/** 省下的钱 =（基线日消耗 × 已戒烟天数 − 实际支数）× 单支价格，允许负数。 */
export function estimateSaved({
  elapsedDays = 0, baselinePerDay = 0, pricePerPack = 0, cigarettesPerPack = 20, totalSmoked = 0
} = {}) {
  const perCigarette = Number(cigarettesPerPack) > 0 ? Number(pricePerPack) / Number(cigarettesPerPack) : 0
  const avoided = Number(elapsedDays) * Number(baselinePerDay) - Number(totalSmoked)
  return Math.round(avoided * perCigarette * 100) / 100
}

export const CRAVING_TRIGGERS = [
  { key: 'after-meal', label: '饭后' },
  { key: 'stress', label: '压力' },
  { key: 'social', label: '社交' },
  { key: 'coffee', label: '喝咖啡' },
  { key: 'bored', label: '无聊' },
  { key: 'alcohol', label: '喝酒' },
  { key: 'other', label: '其他' }
]

export function triggerLabel(key) {
  return CRAVING_TRIGGERS.find(item => item.key === key)?.label || key || '其他'
}

/**
 * 香烟种类预设：选中即自动填充每支焦油/一氧化碳毫克数，用户仍可手改。
 * 数值与后端 `CigaretteTypes` 必须一致（普通卷烟按国标限量 11 mg 焦油附近的常见值）。
 * `other` 没有预设数值，表示沿用当前填写值。
 */
export const CIGARETTE_TYPES = [
  { key: 'regular', label: '普通卷烟', tarMg: 11, coMg: 12 },
  { key: 'fine', label: '细支烟', tarMg: 8, coMg: 8 },
  { key: 'medium', label: '中支烟', tarMg: 9, coMg: 9 },
  { key: 'thick', label: '粗支烟', tarMg: 13, coMg: 14 },
  { key: 'hand-rolled', label: '手卷烟', tarMg: 12, coMg: 14 },
  { key: 'cigar', label: '雪茄', tarMg: 15, coMg: 15 },
  { key: 'ecig', label: '电子烟', tarMg: 0, coMg: 0 },
  { key: 'other', label: '自定义', tarMg: null, coMg: null }
]

export function cigaretteType(key) {
  return CIGARETTE_TYPES.find(item => item.key === key) || CIGARETTE_TYPES[0]
}

/** 恢复里程碑的四个维度；顺序与后端 `categoryOrder` 一致。 */
export const MILESTONE_CATEGORIES = [
  { key: 'physiological', label: '生理依赖', hint: '尼古丁戒断' },
  { key: 'psychological', label: '心理依赖', hint: '场景与习惯' },
  { key: 'vascular', label: '血管与心脏', hint: '循环与心血管风险' },
  { key: 'lung', label: '肺部与呼吸', hint: '肺功能与气道' }
]

/** 把后端返回的里程碑按四个维度分组，空分组不返回。 */
export function groupMilestones(milestones = []) {
  const order = MILESTONE_CATEGORIES.map(item => item.key)
  const groups = new Map()
  for (const item of milestones) {
    const key = item?.category || 'other'
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(item)
  }
  return [...groups.keys()]
    .sort((left, right) => {
      const a = order.indexOf(left)
      const b = order.indexOf(right)
      return (a === -1 ? 99 : a) - (b === -1 ? 99 : b)
    })
    .map(key => ({
      key,
      label: MILESTONE_CATEGORIES.find(item => item.key === key)?.label || key,
      items: groups.get(key)
    }))
}

/** 剩余分钟数 → 中文倒计时；已达成（<=0）显示「已达成」。 */
export function formatRemaining(minutes) {
  const value = Number(minutes)
  if (!Number.isFinite(value)) return '—'
  if (value <= 0) return '已达成'
  if (value < 60) return `${Math.round(value)} 分钟`
  if (value < 1440) {
    let hours = Math.floor(value / 60)
    let rest = Math.round(value % 60)
    // 四舍五入可能进位到 60 分钟，必须并进小时，否则会出现「2 小时 60 分」。
    if (rest === 60) {
      hours += 1
      rest = 0
    }
    return rest === 0 ? `${hours} 小时` : `${hours} 小时 ${rest} 分`
  }
  let days = Math.floor(value / 1440)
  let hours = Math.round((value % 1440) / 60)
  // 同理，进位到 24 小时要并进天数，否则显示成「10 天 24 小时」。
  if (hours === 24) {
    days += 1
    hours = 0
  }
  if (days >= 100) return `${days} 天`
  return hours === 0 ? `${days} 天` : `${days} 天 ${hours} 小时`
}

/** 毫克数 → 可读量级：超过 1000 mg 换成克，避免出现「12340.0 mg」。 */
export function formatMg(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '0 mg'
  const sign = number < 0 ? '-' : ''
  const absolute = Math.abs(number)
  if (absolute >= 1000) return `${sign}${(absolute / 1000).toFixed(2)} g`
  return `${sign}${absolute.toFixed(1)} mg`
}

/** 今日实际吸入的焦油/一氧化碳，用于 store 的乐观更新。 */
export function estimateSubstance({
  todaySmoked = 0, elapsedDays = 0, baselinePerDay = 0, totalSmoked = 0,
  tarPerCigarette = 0, coPerCigarette = 0
} = {}) {
  const avoidedCount = Number(elapsedDays) * Number(baselinePerDay) - Number(totalSmoked)
  const round = value => Math.round(value * 10) / 10
  return {
    todayTarMg: round(Number(todaySmoked) * Number(tarPerCigarette)),
    todayCoMg: round(Number(todaySmoked) * Number(coPerCigarette)),
    avoidedTarMg: round(avoidedCount * Number(tarPerCigarette)),
    avoidedCoMg: round(avoidedCount * Number(coPerCigarette))
  }
}
