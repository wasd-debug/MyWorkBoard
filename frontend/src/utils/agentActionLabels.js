// 待确认动作的中文标签：对话页、首页 AI 卡片、审批中心共用一份，避免三处各写一张表
const ACTION_TYPE_LABELS = {
  'task.create': '新建任务', 'task.update': '修改任务', 'task.reschedule': '任务改期',
  'task.complete': '完成任务', 'task.delete': '删除任务', 'task.reopen': '重开任务',
  'task.abandon': '放弃任务', 'task.batch.complete': '批量完成任务', 'habit.checkin': '习惯打卡',
  'focus.start': '开始专注', 'focus.finish': '结束专注', 'focus.update': '修改专注记录',
  'focus.delete': '删除专注记录', 'task.restore': '恢复任务', 'task.batch.move': '批量修改任务',
  'task.comment.create': '写评论', 'task.settings.update': '修改任务设置',
  'habit.create': '新建习惯', 'habit.update': '修改习惯', 'habit.delete': '删除习惯',
  'countdown.create': '新建倒数日', 'countdown.update': '修改倒数日', 'countdown.delete': '删除倒数日',
  'task.batch.delete': '批量删除任务', 'task.lists.delete': '删除清单', 'task.purge': '永久清除任务',
  'task.import.confirm': '导入任务',
  'worktime.settings.update': '修改工时设置', 'worktime.record.create': '新增工时',
  'worktime.record.update': '修改工时', 'worktime.record.delete': '删除工时',
  'ledger.book.create': '新增账本', 'ledger.book.update': '修改账本', 'ledger.book.delete': '删除账本',
  'ledger.account.create': '新增账户', 'ledger.account.update': '修改账户', 'ledger.account.delete': '删除账户',
  'ledger.category.create': '新增分类', 'ledger.category.update': '修改分类', 'ledger.category.delete': '删除分类',
  'ledger.merchant.create': '新增商家', 'ledger.merchant.update': '修改商家', 'ledger.merchant.delete': '删除商家',
  'ledger.project.create': '新增项目', 'ledger.project.update': '修改项目', 'ledger.project.delete': '删除项目',
  'ledger.budget.upsert': '设置预算', 'ledger.budget.delete': '删除预算',
  'ledger.schedule.create': '创建周期任务', 'ledger.schedule.update': '修改周期任务',
  'ledger.schedule.delete': '删除周期任务', 'ledger.schedule.run': '立即执行周期任务',
  'ledger.recycle.restore': '恢复回收站项目', 'ledger.recycle.purge': '永久清除回收站项目',
  'ledger.export': '导出流水', 'ledger.import.preview': '导入文件预览', 'ledger.import.confirm': '导入流水',
  'ledger.transaction.create': '单笔记账', 'ledger.transaction.update': '修改流水',
  'ledger.transaction.delete': '删除流水', 'ledger.transactions.batch.create': '批量记账',
  'ledger.transactions.batch.delete': '批量删除流水'
}

export function agentActionTypeLabel(type, fallback = '待确认操作') {
  return ACTION_TYPE_LABELS[type] || fallback
}

/** 动作是否必须去审批中心：R4 只生成审批，不在对话里直接执行 */
export function actionNeedsApproval(action) {
  const content = action?.structuredContent || {}
  return content.webApprovalRequired === true || content.commitAvailable === false
}
