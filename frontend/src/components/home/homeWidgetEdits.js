/**
 * 首页卡片的「就地编辑」载荷口径。
 *
 * 卡片本身不重造编辑规则：任务改期交给 `TaskDatePanel`，流水交给
 * `LedgerTransactionEditor`，这里只负责把界面输入整理成各域 store 需要的
 * 载荷，并挡掉空值/非法值。抽成纯函数是为了能在 node --test 里直接断言，
 * 字段名写错会静默 400，靠 UI 走查很难发现。
 */

export const TASK_PRIORITIES = ['NONE', 'LOW', 'MEDIUM', 'HIGH']

export const TASK_PRIORITY_LABELS = {
  NONE: '无',
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高'
}

const TITLE_LIMIT = 200

/** 标题就地改名：空白与「没改」都返回 null，调用方据此跳过写请求 */
export function taskTitleUpdate(currentTitle, input) {
  const title = String(input ?? '').replace(/\s+/g, ' ').trim().slice(0, TITLE_LIMIT)
  if (!title) return null
  if (title === String(currentTitle ?? '').trim()) return null
  return { title }
}

/**
 * 日期面板回传的载荷直接透传给任务更新。
 * 面板在「时间段」模式会同时给出 startAt；单一日期模式只给 dueAt。
 */
export function taskDateUpdate(panel) {
  const dueAt = String(panel?.dueAt ?? '').trim()
  if (!dueAt) return null
  const payload = {
    dueAt,
    startAt: String(panel?.startAt ?? '').trim(),
    allDay: Boolean(panel?.allDay)
  }
  // 只有用户动过重复设置时面板才带 rrule，没带就不要覆盖任务原有规则
  if (typeof panel?.rrule === 'string') {
    payload.rrule = panel.rrule
    payload.recurrenceAnchor = panel.recurrenceAnchor === 'COMPLETION_DATE' ? 'COMPLETION_DATE' : 'DUE_DATE'
  }
  return payload
}

/** 清除日期：三个字段一起清，否则会留下「有开始没结束」的残状态 */
export function taskDateClear() {
  return { dueAt: '', startAt: '', allDay: false }
}

export function taskPriorityUpdate(current, priority) {
  if (!TASK_PRIORITIES.includes(priority)) return null
  if (String(current || 'NONE') === priority) return null
  return { priority }
}

/**
 * 预算改额：沿用 `ledgerStore.saveResource('budget', ...)` 的既有载荷。
 * 已有预算必须带 id + revision（离线引擎按 revision 合并），新增只带 monthKey。
 */
export function budgetAmountUpdate(budget, input, monthKey) {
  const amount = Number(input)
  if (!Number.isFinite(amount) || amount <= 0) return null
  const month = String(monthKey || budget?.monthKey || budget?.month || '').slice(0, 7)
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) return null
  const payload = { monthKey: month, budget: Math.round(amount * 100) / 100 }
  if (budget?.categoryId) payload.categoryId = String(budget.categoryId)
  if (budget?.id) {
    payload.id = budget.id
    payload.revision = Number(budget.revision || 0)
  }
  return payload
}

/** 工时补记：允许跨夜（下班早于上班），只校验格式与休息时长上限 */
export function punchUpdate(date, draft) {
  const key = String(date ?? '').trim()
  if (!/^\d{4}-\d{2}-\d{2}$/.test(key)) return null
  const start = String(draft?.start ?? '').trim()
  const end = String(draft?.end ?? '').trim()
  if (!/^\d{2}:\d{2}$/.test(start) || !/^\d{2}:\d{2}$/.test(end)) return null
  const rest = Math.min(600, Math.max(0, Math.round(Number(draft?.rest) || 0)))
  return { date: key, start, end, rest }
}
