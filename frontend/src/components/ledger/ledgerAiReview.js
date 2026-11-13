export const requiresAiCategory = kind => kind === 'INCOME' || kind === 'EXPENSE'

export function aiCategoryNeedsReview(draft) {
  return requiresAiCategory(draft.kind) && Boolean(draft.categoryReviewRequired || draft.categoryEdited)
}

export function aiDraftCanConfirm(draft) {
  return Number(draft.amount) > 0 && Boolean(draft.occurredOn) && Boolean(draft.accountId)
    && Boolean(draft.memberId) && (!requiresAiCategory(draft.kind) || Boolean(draft.categoryId))
    && (draft.kind !== 'TRANSFER' || Boolean(draft.targetAccountId) && draft.targetAccountId !== draft.accountId)
    && !draft.warnings?.length && (!aiCategoryNeedsReview(draft) || Boolean(draft.categoryReviewed))
    && (!requiresAiCategory(draft.kind) || !draft.rememberCategory || validRememberKeyword(draft))
}

export function validRememberKeyword(draft) {
  const normalize = value => String(value || '').normalize('NFKC').toLowerCase().trim().replace(/\s+/g, ' ')
  const keyword = normalize(draft.rememberKeyword)
  return keyword.length >= 2 && keyword.length <= 80
    && [draft.originalNote ?? draft.note, draft.originalMerchantName ?? draft.merchantName]
      .some(value => normalize(value).includes(keyword))
}

export function aiCategoryReviewPayload(drafts) {
  return drafts.map(draft => ({
    draftId: draft.id,
    confirmed: Boolean(draft.categoryReviewed),
    rememberKeyword: requiresAiCategory(draft.kind) && draft.rememberCategory
      ? String(draft.rememberKeyword || '').trim() : null
  }))
}

export const aiRuleTogglePayload = rule => ({ enabled: !rule.enabled })
