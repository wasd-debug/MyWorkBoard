import test from "node:test"
import assert from "node:assert/strict"
import { quickAddTokenAtCaret, replaceQuickAddToken } from "./taskQuickAddSuggestions.js"

test("quick add token suggestions recognize priority, tag, and list markers at the caret", () => {
  assert.deepEqual(quickAddTokenAtCaret("Prepare !h"), { kind: "priority", query: "h", start: 8, end: 10 })
  assert.deepEqual(quickAddTokenAtCaret("Prepare #ja later", 11), { kind: "tag", query: "ja", start: 8, end: 11 })
  assert.equal(quickAddTokenAtCaret("Call ~wo", 8)?.kind, "list")
  assert.equal(quickAddTokenAtCaret("literal!word"), null)
})

test("selecting a quick add token replaces only its marker token", () => {
  const value = "Prepare #ja today"
  const token = quickAddTokenAtCaret(value, 11)
  assert.equal(replaceQuickAddToken(value, token), "Prepare today")
})
