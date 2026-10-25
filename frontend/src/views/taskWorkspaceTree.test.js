import test from "node:test";
import assert from "node:assert/strict";
import { buildTaskListGroups } from "./taskWorkspaceTree.js";

const parent = { publicId: "parent", title: "父任务", sortOrder: 0 };
const child = { publicId: "child", title: "子任务", parentId: "parent", sortOrder: 2 };
const grandchild = { publicId: "grandchild", title: "孙任务", parentId: "child", sortOrder: 0 };
const other = { publicId: "other", title: "其他任务", sortOrder: 1 };

test("nests visible descendants under their root without duplicating them across groups", () => {
  const groups = buildTaskListGroups([
    { key: "first", tasks: [parent, other] },
    { key: "second", tasks: [child, grandchild] },
  ], [parent, child, grandchild, other]);

  assert.deepEqual(groups[0].rows.map(({ publicId, treeDepth }) => [publicId, treeDepth]), [
    ["parent", 0], ["child", 1], ["grandchild", 2], ["other", 0],
  ]);
  assert.equal(groups[0].visibleCount, 4);
  assert.deepEqual(groups[1].rows, []);
});

test("collapses a branch while retaining the root row", () => {
  const groups = buildTaskListGroups([{ key: "all", tasks: [parent, child, grandchild] }], [parent, child, grandchild], new Set(["parent"]));
  assert.deepEqual(groups[0].rows.map(({ publicId }) => publicId), ["parent"]);
  assert.equal(groups[0].rows[0].hasVisibleChildren, true);
});

test("keeps a child at the top level when its parent is outside the visible task set", () => {
  const groups = buildTaskListGroups([{ key: "all", tasks: [child] }], [child]);
  assert.deepEqual(groups[0].rows.map(({ publicId, treeDepth }) => [publicId, treeDepth]), [["child", 0]]);
});
