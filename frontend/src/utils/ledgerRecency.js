/**
 * 「最近流水」排序口径：录入表单的账户/分类/商家排序、默认账户推断都依赖它。
 *
 * 独立成纯模块是为了让 Pinia store 也能用同一份实现（以前只在组件层有，
 * 每个组件都各自对整本流水拷贝+排序一遍）。数组大时那是 8000+ 条的
 * `localeCompare` 排序，一次保存能触发好几次，直接卡住主线程。
 */

/**
 * 按发生日期、录入时间倒序取最近 N 条。
 *
 * 比较直接用字符串 `<`/`>` 而不是 `localeCompare`：`occurredOn` 是 `yyyy-MM-dd`
 * 定长格式，字符串序就是日期序，`localeCompare` 在 8000 条上要慢一个量级。
 */
export function recentLedgerTransactions(transactions, limit = 200) {
  const rows = []
  for (const item of transactions || []) {
    if (item && !item.deleted) rows.push(item)
  }
  rows.sort((left, right) => {
    const leftDate = String(left.occurredOn || '')
    const rightDate = String(right.occurredOn || '')
    if (leftDate !== rightDate) return leftDate < rightDate ? 1 : -1
    const leftCreated = String(left.createdAt || '')
    const rightCreated = String(right.createdAt || '')
    if (leftCreated !== rightCreated) return leftCreated < rightCreated ? 1 : -1
    const leftId = String(left.id || '')
    const rightId = String(right.id || '')
    if (leftId === rightId) return 0
    return leftId < rightId ? 1 : -1
  })
  return rows.slice(0, limit)
}
