const LIABILITY_TYPES = new Set(['card', 'credit', 'credit_card', 'loan'])

export function isLiabilityAccount(account) {
  return LIABILITY_TYPES.has(account.accountType)
}

// Borrowed cash remains in assets; the corresponding liability is deducted
// separately in net assets. Outstanding lent principal is a receivable.
export function totalLedgerAssets(accounts, transactions, asOf) {
  const assets = accounts.filter(account => !isLiabilityAccount(account))
  const assetIds = new Set(assets.map(account => String(account.id)))
  const balances = assets.reduce((sum, account) => sum + Math.max(0, Number(account.balance || 0)), 0)
  const principal = { lent: 0, collected: 0 }
  for (const transaction of transactions) {
    if (transaction.deleted || !assetIds.has(String(transaction.accountId))) continue
    if (asOf && String(transaction.occurredOn || '') > asOf) continue
    const amount = Number(transaction.amount || 0)
    if (transaction.kind === 'LEND_OUT') principal.lent += amount
    if (transaction.kind === 'COLLECT_DEBT') principal.collected += amount
  }
  return balances + Math.max(0, principal.lent - principal.collected)
}

export function accountBalancesAt(accounts, transactions, date, { today } = {}) {
  const cutoff = String(date || '')
  const liveDate = today || new Date().toISOString().slice(0, 10)
  const useLiveBalances = cutoff >= liveDate
  const transferOutGroups = new Set(transactions
    .filter(transaction => String(transaction.storedKind || '') === 'TRANSFER_OUT' && transaction.transferGroupId)
    .map(transaction => String(transaction.transferGroupId)))
  return accounts.map(account => {
    let balance = useLiveBalances && account.balance != null
      ? Number(account.balance || 0)
      : Number(account.openingBalance || 0)
    if (!useLiveBalances) {
      for (const transaction of transactions) {
        if (transaction.deleted || String(transaction.occurredOn || '') > cutoff) continue
        const storedKind = String(transaction.storedKind || '')
        if (storedKind === 'TRANSFER_IN' && transferOutGroups.has(String(transaction.transferGroupId))) continue
        const amount = Number(transaction.amount || 0)
        const accountMatches = String(transaction.accountId || '') === String(account.id)
        const targetMatches = String(transaction.targetAccountId || '') === String(account.id)
        if (accountMatches) {
          if (storedKind === 'TRANSFER_IN') balance += amount
          else if (storedKind === 'TRANSFER_OUT' || transaction.kind === 'TRANSFER') balance -= amount
          else if (['INCOME', 'BORROW_IN', 'COLLECT_DEBT'].includes(transaction.kind)) balance += amount
          else if (['EXPENSE', 'LEND_OUT', 'REPAY_DEBT'].includes(transaction.kind)) balance -= amount
        }
        if (targetMatches && transaction.kind === 'TRANSFER') balance += amount
      }
    }
    return { ...account, balance, liability: isLiabilityAccount(account) }
  })
}
