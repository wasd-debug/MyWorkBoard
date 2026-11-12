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
