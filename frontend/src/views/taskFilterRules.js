export const FILTER_RULE_FIELDS = [
  { value: 'title', label: '标题' }, { value: 'description', label: '描述' },
  { value: 'status', label: '状态' }, { value: 'priority', label: '优先级' },
  { value: 'listId', label: '清单' }, { value: 'tagId', label: '标签' },
  { value: 'dueAt', label: '截止日期' }, { value: 'hasDueDate', label: '有无截止日期' },
  { value: 'parentId', label: '父任务' },
]

export const FILTER_RULE_OPERATORS = [
  { value: 'EQ', label: '等于' }, { value: 'NE', label: '不等于' },
  { value: 'CONTAINS', label: '包含' }, { value: 'NOT_CONTAINS', label: '不包含' },
  { value: 'IN', label: '属于' }, { value: 'IS_SET', label: '已设置' },
  { value: 'IS_NOT_SET', label: '未设置' }, { value: 'LT', label: '早于 / 小于' },
  { value: 'LTE', label: '不晚于 / 不大于' }, { value: 'GT', label: '晚于 / 大于' },
  { value: 'GTE', label: '不早于 / 不小于' },
]

const OPERATORS_BY_FIELD = {
  title: ['EQ', 'NE', 'CONTAINS', 'NOT_CONTAINS', 'IS_SET', 'IS_NOT_SET'],
  description: ['EQ', 'NE', 'CONTAINS', 'NOT_CONTAINS', 'IS_SET', 'IS_NOT_SET'],
  status: ['EQ', 'NE', 'IN', 'IS_SET', 'IS_NOT_SET'],
  priority: ['EQ', 'NE', 'IN', 'IS_SET', 'IS_NOT_SET'],
  listId: ['EQ', 'NE', 'IN', 'IS_SET', 'IS_NOT_SET'],
  tagId: ['EQ', 'NE', 'IN', 'IS_SET', 'IS_NOT_SET'],
  dueAt: ['EQ', 'NE', 'IN', 'IS_SET', 'IS_NOT_SET', 'LT', 'LTE', 'GT', 'GTE'],
  hasDueDate: ['EQ', 'NE'],
  parentId: ['EQ', 'NE', 'CONTAINS', 'NOT_CONTAINS', 'IS_SET', 'IS_NOT_SET'],
}

export function operatorsForRule(field) {
  return FILTER_RULE_OPERATORS.filter(item => OPERATORS_BY_FIELD[field]?.includes(item.value))
}

function ruleId() {
  return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`
}

export function normalizeFilterRules(rules) {
  const visit = node => {
    if (!node || typeof node !== 'object') return { id: ruleId(), field: 'title', operator: 'CONTAINS', value: '' }
    if (node.op) return { id: ruleId(), op: String(node.op).toUpperCase(), children: Array.isArray(node.children) ? node.children.map(visit) : [] }
    return { id: ruleId(), field: node.field, operator: node.operator, ...(node.value === undefined ? {} : { value: node.value }) }
  }
  return rules?.op ? visit(rules) : { id: ruleId(), op: 'AND', children: [] }
}

export function serializeFilterRules(root) {
  if (!root?.children?.length) return null
  let nodes = 0
  const visit = (node, depth) => {
    if (++nodes > 60 || depth > 5) throw new Error('筛选条件树最多 60 个节点、5 层')
    if (node.op) {
      if (!['AND', 'OR'].includes(node.op) || !Array.isArray(node.children) || !node.children.length || node.children.length > 20) throw new Error('每个条件组需包含 1-20 个条件')
      return { op: node.op, children: node.children.map(child => visit(child, depth + 1)) }
    }
    if (!FILTER_RULE_FIELDS.some(item => item.value === node.field) || !operatorsForRule(node.field).some(item => item.value === node.operator)) throw new Error('筛选字段或操作符不受支持')
    const leaf = { field: node.field, operator: node.operator }
    if (!['IS_SET', 'IS_NOT_SET'].includes(node.operator)) {
      if (node.field === 'hasDueDate') leaf.value = Boolean(node.value)
      else if (node.operator === 'IN') {
        const values = Array.isArray(node.value) ? node.value : []
        if (!values.length || values.length > 20) throw new Error('IN 条件需要 1-20 个值')
        leaf.value = values
      } else leaf.value = node.value ?? ''
    }
    return leaf
  }
  return visit(root, 0)
}

export function countFilterRuleNodes(node) {
  return node ? 1 + (node.children || []).reduce((total, child) => total + countFilterRuleNodes(child), 0) : 0
}

export function matchesFilterRules(task, root) {
  if (!root?.children?.length) return true
  const evaluate = node => {
    if (node.op) return node.op === 'OR' ? node.children.some(evaluate) : node.children.every(evaluate)
    const due = task.dueAt ? new Date(task.dueAt) : null
    const actual = ({ title: task.title, description: task.description || '', status: task.status, priority: task.priority,
      listId: task.listId, tagId: task.tagIds || [], dueAt: due ? `${due.getFullYear()}-${String(due.getMonth() + 1).padStart(2, '0')}-${String(due.getDate()).padStart(2, '0')}` : '',
      hasDueDate: Boolean(due), parentId: task.parentId || '' })[node.field]
    const expected = node.value
    const text = value => String(value ?? '').toLocaleLowerCase()
    switch (node.operator) {
      case 'EQ': return Array.isArray(actual) ? actual.includes(expected) : text(actual) === text(expected)
      case 'NE': return Array.isArray(actual) ? !actual.includes(expected) : text(actual) !== text(expected)
      case 'CONTAINS': return text(actual).includes(text(expected))
      case 'NOT_CONTAINS': return !text(actual).includes(text(expected))
      case 'IN': return Array.isArray(expected) && expected.some(value => Array.isArray(actual) ? actual.includes(value) : text(actual) === text(value))
      case 'IS_SET': return Array.isArray(actual) ? actual.length > 0 : actual !== '' && actual !== null
      case 'IS_NOT_SET': return Array.isArray(actual) ? actual.length === 0 : actual === '' || actual === null
      case 'LT': return text(actual) < text(expected)
      case 'LTE': return text(actual) <= text(expected)
      case 'GT': return text(actual) > text(expected)
      case 'GTE': return text(actual) >= text(expected)
      default: return false
    }
  }
  return evaluate(root)
}
