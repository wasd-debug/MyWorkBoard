// 任务日期口径：时间段任务（开始与截止跨天）在区间内的**每一天**都算「当天有安排」。
// 今天/明天/未来 7 天清单、侧栏计数、按时间分组、搜索日期条件都走这里，避免各处各写一套。

/** 解析出跨天的时间段；同一天（或没设开始）返回 null，仍按单日期处理 */
export function taskRange(task) {
  if (!task?.startAt || !task?.dueAt) return null;
  const start = new Date(task.startAt);
  const end = new Date(task.dueAt);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return null;
  if (start.getTime() >= end.getTime()) return null;
  return { start, end };
}

/** 时间段是否跨天：列表里用区间标签、日历里按多天铺开都依赖这个判断 */
export function isRangeTask(task) {
  return Boolean(taskRange(task));
}

/**
 * 任务与 [dayStart, dayEnd) 这一天有没有交集。
 * - 单日期任务：到期时间落在这天就算命中（原有口径）
 * - 时间段任务：只要这一天的窗口和区间有重叠就算命中（首尾两天都算）
 */
export function taskHitsDay(task, dayStart, dayEnd) {
  if (!task) return false;
  const range = taskRange(task);
  if (range) return range.end >= dayStart && range.start < dayEnd;
  if (!task.dueAt) return false;
  const due = new Date(task.dueAt);
  if (Number.isNaN(due.getTime())) return false;
  return due >= dayStart && due < dayEnd;
}

/** 按 yyyy-MM-dd 日期键判断：任务在 [startKey, endKey] 这段日期里有没有落点 */
export function taskOverlapsKeys(task, startKey, endKey, parseKey) {
  if (!startKey || !endKey || !parseKey) return false;
  const dayStart = parseKey(startKey);
  dayStart.setHours(0, 0, 0, 0);
  const lastDay = parseKey(endKey);
  lastDay.setHours(0, 0, 0, 0);
  const dayEnd = new Date(lastDay.getTime() + 24 * 60 * 60 * 1000);
  return taskHitsDay(task, dayStart, dayEnd);
}

/** 任务的「生效日」：时间段任务从开始那天起就该出现在清单里 */
export function taskStartMoment(task) {
  if (!task) return null;
  const range = taskRange(task);
  if (range) return range.start;
  return task.dueAt ? new Date(task.dueAt) : null;
}
