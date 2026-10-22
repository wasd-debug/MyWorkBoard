import assert from "node:assert/strict";
import test from "node:test";
import { formatAttachmentSize, MAX_TASK_ATTACHMENT_BYTES, uploadQuickAttachments, validateQuickAttachmentFiles } from "./taskQuickAddAttachments.js";

test("validates quick-add attachments against the server size and empty-file rules", () => {
  const { accepted, rejected } = validateQuickAttachmentFiles([
    { name: "ok.txt", size: 1 },
    { name: "empty.txt", size: 0 },
    { name: "large.bin", size: MAX_TASK_ATTACHMENT_BYTES + 1 },
  ]);
  assert.deepEqual(accepted.map(file => file.name), ["ok.txt"]);
  assert.deepEqual(rejected.map(item => item.reason), ["附件不能为空", "附件不能超过 25 MB"]);
  assert.equal(formatAttachmentSize(1024), "1 KB");
});

test("uploads sequentially and retains the failed and not-yet-uploaded files for retry", async () => {
  const items = [{ id: "a", file: { name: "a" } }, { id: "b", file: { name: "b" } }, { id: "c", file: { name: "c" } }];
  const calls = [];
  const result = await uploadQuickAttachments("task-1", items, async (taskId, file, progress, key) => {
    calls.push([taskId, file.name, key]);
    progress(50);
    if (file.name === "b") throw new Error("network down");
  });
  assert.deepEqual(calls, [["task-1", "a", "a"], ["task-1", "b", "b"]]);
  assert.deepEqual(result.uploaded, ["a"]);
  assert.deepEqual(result.remaining, items.slice(1));
  assert.match(result.error.message, /network down/);
});
