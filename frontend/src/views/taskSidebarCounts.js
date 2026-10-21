const OPEN_EXCLUDED_STATUSES = new Set(["COMPLETED", "ABANDONED"]);

function startOfDay(value) {
  const date = new Date(value);
  date.setHours(0, 0, 0, 0);
  return date;
}

function addDays(value, days) {
  const date = new Date(value);
  date.setDate(date.getDate() + days);
  return date;
}

function inRange(value, start, end) {
  return value && new Date(value) >= start && new Date(value) < end;
}

export function buildTaskSidebarCounts(tasks, archivedListIds, inboxId, now = new Date()) {
  const counts = Object.fromEntries([
    "all", "today", "tomorrow", "next7", "assigned", "inbox", "summary",
    "completed", "abandoned", "trash", "archived", "this-week", "next-week",
    "no-date", "no-tag",
  ].map(key => [key, 0]));
  const openByList = new Map();
  const openByTag = new Map();
  const taggedTasks = new Set();
  const archivedIds = new Set(archivedListIds);
  const today = startOfDay(now);
  const tomorrow = addDays(today, 1);
  const weekStart = addDays(today, -((today.getDay() + 6) % 7));
  const nextWeekStart = addDays(weekStart, 7);
  const nextNextWeekStart = addDays(nextWeekStart, 7);

  for (const task of tasks) {
    if (task.deleted) {
      counts.trash += 1;
      continue;
    }

    if (task.status === "COMPLETED") counts.completed += 1;
    if (task.status === "ABANDONED") counts.abandoned += 1;
    if (archivedIds.has(task.listId)) counts.archived += 1;
    for (const tagId of task.tagIds || []) taggedTasks.add(tagId);

    if (OPEN_EXCLUDED_STATUSES.has(task.status)) continue;

    counts.all += 1;
    if (task.listId) openByList.set(task.listId, (openByList.get(task.listId) || 0) + 1);
    for (const tagId of task.tagIds || []) openByTag.set(tagId, (openByTag.get(tagId) || 0) + 1);
    if (task.dueAt) {
      if (new Date(task.dueAt) < tomorrow) counts.summary += 1;
      if (inRange(task.dueAt, today, tomorrow)) counts.today += 1;
      if (inRange(task.dueAt, tomorrow, addDays(tomorrow, 1))) counts.tomorrow += 1;
      if (inRange(task.dueAt, today, addDays(today, 8))) counts.next7 += 1;
      if (inRange(task.dueAt, weekStart, nextWeekStart)) counts["this-week"] += 1;
      if (inRange(task.dueAt, nextWeekStart, nextNextWeekStart)) counts["next-week"] += 1;
    } else {
      counts["no-date"] += 1;
    }
    if (!(task.tagIds || []).length) counts["no-tag"] += 1;
    if (task.listId === inboxId) counts.inbox += 1;
  }

  return { counts, openByList, openByTag, taggedTasks };
}
