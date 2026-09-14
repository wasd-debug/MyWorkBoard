export function recentLedgerTransactions(transactions, limit = 200) {
  return [...(transactions || [])]
    .filter(item => !item.deleted)
    .sort((left, right) =>
      String(right.occurredOn || '').localeCompare(String(left.occurredOn || '')) ||
      String(right.createdAt || '').localeCompare(String(left.createdAt || '')) ||
      String(right.id || '').localeCompare(String(left.id || '')))
    .slice(0, limit)
}

export function rankLedgerOptions(options, recentTransactions, idsForTransaction) {
  const scores = new Map()
  recentTransactions.forEach((transaction, index) => {
    for (const id of new Set(idsForTransaction(transaction).filter(Boolean).map(String))) {
      const score = scores.get(id) || { count: 0, recent: index }
      score.count++
      scores.set(id, score)
    }
  })
  return [...options].sort((left, right) => {
    const a = scores.get(String(left.id))
    const b = scores.get(String(right.id))
    return (b?.count || 0) - (a?.count || 0) || (a?.recent ?? Infinity) - (b?.recent ?? Infinity)
  })
}

export function currentMemberName(members, user) {
  if (!user) return ''
  const member = members.find(item => String(item.userId) === String(user.id))
  return member?.displayName || member?.username || ''
}

export function selectedResourceId(options, selected, nameKey = 'name') {
  if (!selected) return null
  return options.find(item => String(item.id) === String(selected)
    || item[nameKey] === selected || item.username === selected)?.id || null
}

export function ledgerTransactionDraft(form, { categories, merchants, members, projects }) {
  const category = categories.find(item => String(item.id) === String(form.categoryId))
  if (['INCOME', 'EXPENSE'].includes(form.kind) &&
    (!category?.parentId || category.kind !== form.kind)) throw new Error('请选择对应类型的二级分类')
  const resource = (options, selected, nameKey, label) => {
    const id = selectedResourceId(options, selected, nameKey)
    if (selected && !id) throw new Error(`请选择当前账本中存在的${label}`)
    return id
  }
  return {
    ...form,
    amount: Number(form.amount),
    accountId: String(form.accountId),
    targetAccountId: form.kind === 'TRANSFER' ? String(form.targetAccountId) : null,
    categoryId: category?.parentId && ['INCOME', 'EXPENSE'].includes(form.kind) ? String(category.id) : null,
    merchantId: resource(merchants, form.payee, 'name', '商家'),
    memberId: resource(members, form.member, 'displayName', '成员'),
    projectId: resource(projects, form.project, 'name', '项目')
  }
}
