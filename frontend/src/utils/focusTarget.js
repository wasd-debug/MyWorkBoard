/**
 * 专注记录关联对象统一口径：
 * 一条记录要么挂在任务上（taskId/taskTitle），要么挂在习惯上（habitId/habitTitle），不会同时有值。
 */
export function focusTargetLabel(session, emptyLabel = '未关联任务') {
  if (!session) return emptyLabel
  return session.taskTitle || session.habitTitle || emptyLabel
}

/** 当前关联对象的 id（用于选择器高亮），没有关联时返回空串 */
export function focusTargetId(session) {
  if (!session) return ''
  return session.taskId || session.habitId || ''
}

/** 是否已经关联上某个任务或习惯 */
export function isFocusLinked(session) {
  return Boolean(focusTargetId(session))
}
