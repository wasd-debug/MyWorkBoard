import test from "node:test";
import assert from "node:assert/strict";
import { buildTaskSidebarCounts } from "./taskSidebarCounts.js";

test("aggregates sidebar counts in one pass while preserving task state semantics", () => {
  const now = new Date(2026, 9, 21, 14);
  const todayAtNoon = new Date(2026, 9, 21, 12).toISOString();
  const tomorrowAtNoon = new Date(2026, 9, 22, 12).toISOString();
  const counts = buildTaskSidebarCounts([
    { publicId: "open-today", listId: "inbox", dueAt: todayAtNoon, tagIds: ["work"] },
    { publicId: "open-no-date", listId: "archive", dueAt: "", tagIds: [] },
    { publicId: "open-tomorrow", listId: "other", dueAt: tomorrowAtNoon, tagIds: ["work", "home"] },
    { publicId: "done", listId: "inbox", status: "COMPLETED", tagIds: ["work"] },
    { publicId: "abandoned", listId: "archive", status: "ABANDONED", tagIds: ["home"] },
    { publicId: "deleted", listId: "inbox", deleted: true, status: "COMPLETED", tagIds: ["deleted-tag"] },
  ], ["archive"], "inbox", now);

  assert.deepEqual(counts.counts, {
    all: 3,
    today: 1,
    tomorrow: 1,
    next7: 2,
    assigned: 0,
    inbox: 1,
    summary: 1,
    completed: 1,
    abandoned: 1,
    trash: 1,
    archived: 2,
    "this-week": 2,
    "next-week": 0,
    "no-date": 1,
    "no-tag": 1,
  });
  assert.deepEqual([...counts.openByList], [["inbox", 1], ["archive", 1], ["other", 1]]);
  assert.deepEqual([...counts.openByTag], [["work", 2], ["home", 1]]);
  assert.deepEqual([...counts.taggedTasks].sort(), ["home", "work"]);
});

test("counts tasks without list or tags without creating empty map entries", () => {
  const counts = buildTaskSidebarCounts([{ publicId: "open", listId: "", tagIds: [] }], [], "inbox", new Date(2026, 9, 21));

  assert.equal(counts.counts.all, 1);
  assert.equal(counts.counts["no-date"], 1);
  assert.equal(counts.counts["no-tag"], 1);
  assert.equal(counts.openByList.size, 0);
  assert.equal(counts.openByTag.size, 0);
});
