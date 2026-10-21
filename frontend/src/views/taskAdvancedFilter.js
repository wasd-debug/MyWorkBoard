import { serializeFilterRules } from './taskFilterRules.js'

export const ADVANCED_FILTER_OPTIONS = [
  { value: 'LIST', label: '清单' },
  { value: 'TAG', label: '标签' },
  { value: 'DATE', label: '日期' },
  { value: 'PRIORITY', label: '优先级' },
  { value: 'CONTENT', label: '内容包含' },
  { value: 'TASK_TYPE', label: '任务类型' },
]

export function newAdvancedFilterCondition(type = '') {
  const id = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`
  if (type === 'LIST') return { id, type, logic: 'OR', listIds: [] }
  if (type === 'TAG') return { id, type, tagIds: [] }
  if (type === 'DATE') return { id, type, logic: 'OR', start: '', end: '' }
  if (type === 'PRIORITY') return { id, type, priorities: [] }
  if (type === 'CONTENT') return { id, type, keyword: '' }
  if (type === 'TASK_TYPE') return { id, type, taskTypes: [] }
  return { id, type: '' }
}

function isValidCondition(condition) {
  if (condition.type === 'LEGACY') return Boolean(condition.rule)
  if (condition.type === 'LIST') return condition.listIds?.length > 0
  if (condition.type === 'TAG') return condition.tagIds?.length > 0
  if (condition.type === 'DATE') return Boolean(condition.start && condition.end && condition.end >= condition.start)
  if (condition.type === 'PRIORITY') return condition.priorities?.length > 0
  if (condition.type === 'CONTENT') return Boolean(condition.keyword?.trim())
  if (condition.type === 'TASK_TYPE') return condition.taskTypes?.length > 0
  return false
}

function criterionRule(condition) {
  if (condition.type === 'LEGACY') return condition.rule
  if (condition.type === 'LIST') {
    if (condition.logic === 'NOT') return { op: 'AND', children: condition.listIds.map(value => ({ field: 'listId', operator: 'NE', value })) }
    return { field: 'listId', operator: 'IN', value: [...condition.listIds] }
  }
  if (condition.type === 'TAG') return { op: 'AND', children: condition.tagIds.map(value => ({ field: 'tagId', operator: 'EQ', value })) }
  if (condition.type === 'DATE') {
    if (condition.logic === 'NOT') return { op: 'OR', children: [
      { field: 'hasDueDate', operator: 'EQ', value: false },
      { field: 'dueAt', operator: 'LT', value: condition.start },
      { field: 'dueAt', operator: 'GT', value: condition.end },
    ] }
    return { op: 'AND', children: [
      { field: 'dueAt', operator: 'GTE', value: condition.start },
      { field: 'dueAt', operator: 'LTE', value: condition.end },
    ] }
  }
  if (condition.type === 'PRIORITY') return { field: 'priority', operator: 'IN', value: [...condition.priorities] }
  if (condition.type === 'CONTENT') return { op: 'OR', children: [
    { field: 'title', operator: 'CONTAINS', value: condition.keyword.trim() },
    { field: 'description', operator: 'CONTAINS', value: condition.keyword.trim() },
  ] }
  if (condition.type === 'TASK_TYPE') return { field: 'taskType', operator: 'IN', value: [...condition.taskTypes] }
  return null
}

export function buildAdvancedFilterRules(conditions, joins = []) {
  const valid = conditions.filter(isValidCondition).map(criterionRule)
  if (!valid.length) return { op: 'AND', children: [] }
  if (valid.length === 1) return { op: 'AND', children: [valid[0]] }

  // AND binds more tightly than OR, matching the visible connector sequence.
  const andGroups = []
  let current = [valid[0]]
  for (let index = 1; index < valid.length; index += 1) {
    if ((joins[index - 1] || 'AND') === 'AND') current.push(valid[index])
    else {
      andGroups.push(current.length === 1 ? current[0] : { op: 'AND', children: current })
      current = [valid[index]]
    }
  }
  andGroups.push(current.length === 1 ? current[0] : { op: 'AND', children: current })
  return andGroups.length === 1 ? { op: 'AND', children: andGroups } : { op: 'OR', children: andGroups }
}

function conditionFromRule(node) {
  if (!node) return null
  if (!node.op && node.field === 'listId' && ['IN', 'EQ', 'NE'].includes(node.operator)) {
    return { ...newAdvancedFilterCondition('LIST'), logic: node.operator === 'NE' ? 'NOT' : 'OR', listIds: Array.isArray(node.value) ? [...node.value] : [node.value] }
  }
  if (!node.op && node.field === 'priority' && ['IN', 'EQ'].includes(node.operator)) {
    return { ...newAdvancedFilterCondition('PRIORITY'), priorities: Array.isArray(node.value) ? [...node.value] : [node.value] }
  }
  if (!node.op && node.field === 'taskType' && ['IN', 'EQ'].includes(node.operator)) {
    return { ...newAdvancedFilterCondition('TASK_TYPE'), taskTypes: Array.isArray(node.value) ? [...node.value] : [node.value] }
  }
  if (!node.op && ['title', 'description'].includes(node.field) && node.operator === 'CONTAINS') {
    return { ...newAdvancedFilterCondition('CONTENT'), keyword: node.value || '' }
  }
  if (node.op === 'AND' && node.children?.length && node.children.every(child => !child.op && child.field === 'tagId' && child.operator === 'EQ')) {
    return { ...newAdvancedFilterCondition('TAG'), tagIds: node.children.map(child => child.value) }
  }
  if (node.op === 'AND' && node.children?.every(child => !child.op && child.field === 'listId' && child.operator === 'NE')) {
    return { ...newAdvancedFilterCondition('LIST'), logic: 'NOT', listIds: node.children.map(child => child.value) }
  }
  if (node.op === 'AND' && node.children?.length === 2) {
    const start = node.children.find(child => child.field === 'dueAt' && child.operator === 'GTE')
    const end = node.children.find(child => child.field === 'dueAt' && child.operator === 'LTE')
    if (start && end) return { ...newAdvancedFilterCondition('DATE'), start: start.value, end: end.value }
  }
  if (node.op === 'OR' && node.children?.some(child => !child.op && child.field === 'hasDueDate' && child.operator === 'EQ' && child.value === false)) {
    const start = node.children.find(child => child.field === 'dueAt' && child.operator === 'LT')
    const end = node.children.find(child => child.field === 'dueAt' && child.operator === 'GT')
    if (start && end) return { ...newAdvancedFilterCondition('DATE'), logic: 'NOT', start: start.value, end: end.value }
  }
  if (node.op === 'OR' && node.children?.length === 2 && node.children.every(child => !child.op && ['title', 'description'].includes(child.field) && child.operator === 'CONTAINS' && child.value === node.children[0].value)) {
    return { ...newAdvancedFilterCondition('CONTENT'), keyword: node.children[0].value || '' }
  }
  return null
}

function flatten(node, conditions, joins) {
  const recognized = conditionFromRule(node)
  if (recognized) {
    conditions.push(recognized)
    return
  }
  if (node?.op && ['AND', 'OR'].includes(node.op) && node.children?.length) {
    node.children.forEach((child, index) => {
      if (index) joins.push(node.op)
      flatten(child, conditions, joins)
    })
    return
  }
  conditions.push({ ...newAdvancedFilterCondition(), type: 'LEGACY', rule: node })
}

export function advancedFilterConditionsFromRules(root) {
  const conditions = []
  const joins = []
  if (root?.op && Array.isArray(root.children)) flatten(root, conditions, joins)
  return { conditions, joins }
}

export function advancedFilterConditionsValid(conditions, joins = []) {
  if (!conditions.length || conditions.some(condition => !isValidCondition(condition))) return false
  try { return Boolean(serializeFilterRules(buildAdvancedFilterRules(conditions, joins))) }
  catch { return false }
}
