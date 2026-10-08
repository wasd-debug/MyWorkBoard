export function parseAgentMetadata(value) {
  if (!value) return {}
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch { return {} }
}

export function normalizeAgentActions(value) {
  if (!Array.isArray(value)) return []
  return value.filter(action => action && typeof action === 'object' && typeof action.actionId === 'string' && action.actionId)
    .map(action => ({ ...action, form: JSON.parse(JSON.stringify(action.structuredContent?.input || {})) }))
}

export function recoverAgentMetadata(value, existing) {
  const metadata = parseAgentMetadata(value)
  // Only reuse data from the same matched server message, never infer an action from prose.
  if (!Object.hasOwn(metadata, 'actions') && existing?.actions?.length) {
    return { ...metadata, actions: existing.actions }
  }
  return metadata
}

export function missingActionCard(message) {
  if (message?.role !== 'assistant' || message.typing || normalizeAgentActions(message.actions).length) return false
  return (Array.isArray(message.toolExecutions) ? message.toolExecutions : []).some(tool => ['NEEDS_INPUT', 'NEEDS_CONFIRMATION'].includes(tool?.status))
    || /已生成[^\n]*待确认|站内操作卡片|在卡片中[^\n]*确认/.test(message.content || '')
}
