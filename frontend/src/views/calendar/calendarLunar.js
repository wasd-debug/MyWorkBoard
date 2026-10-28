const MONTH_PATTERN = /^(闰?.+?月)(.*)$/

/**
 * 把后端返回的农历标签压成日历格子里能放下的短文本：
 * - 节日/节气优先（如「万圣节」「寒露」）
 * - 农历初一显示月份（「九月」），其余只显示农历日（「十六」）
 */
export function formatLunarLabel(item) {
  if (!item) return ''
  const festival = String(item.festival || '').trim()
  // 超过三个字的节日名在日历格里会截断，退回农历日更易读（如「万圣节前夜」→「廿一」）
  if (festival && festival.length <= 3) return festival
  const solarTerm = String(item.solarTerm || '').trim()
  if (solarTerm) return solarTerm
  const raw = String(item.lunarDate || '').trim()
  if (!raw) return ''
  const match = raw.match(MONTH_PATTERN)
  if (!match) return raw
  const [, month, day] = match
  if (!day) return month
  return day === '初一' ? month : day
}

/** 节假日（休/班）优先于农历，用于迷你月历与月格副标。 */
export function formatDaySubLabel({ holiday, lunar } = {}) {
  if (holiday?.name) return holiday.name
  return formatLunarLabel(lunar)
}
