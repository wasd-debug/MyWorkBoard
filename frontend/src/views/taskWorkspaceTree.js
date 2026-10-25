export function buildTaskListGroups(groups, tasks, collapsedIds = new Set()) {
  const visibleById = new Map(tasks.map((task) => [task.publicId, task]));
  const childrenByParent = new Map();

  for (const task of tasks) {
    if (!task.parentId || !visibleById.has(task.parentId)) continue;
    const children = childrenByParent.get(task.parentId) || [];
    children.push(task);
    childrenByParent.set(task.parentId, children);
  }

  for (const children of childrenByParent.values()) {
    children.sort((left, right) => Number(left.sortOrder || 0) - Number(right.sortOrder || 0));
  }

  function appendBranch(task, depth, path, rows) {
    if (path.has(task.publicId)) return;
    const children = childrenByParent.get(task.publicId) || [];
    rows.push({ ...task, treeDepth: depth, hasVisibleChildren: children.length > 0 });
    if (collapsedIds.has(task.publicId)) return;

    const nextPath = new Set(path);
    nextPath.add(task.publicId);
    for (const child of children) appendBranch(child, depth + 1, nextPath, rows);
  }

  return groups.map((group) => {
    const rows = [];
    const roots = group.tasks.filter((task) => !visibleById.has(task.parentId));
    for (const task of roots) appendBranch(task, 0, new Set(), rows);
    return { ...group, rows, visibleCount: rows.length };
  });
}
