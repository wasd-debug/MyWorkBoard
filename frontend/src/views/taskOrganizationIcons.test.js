import test from "node:test";
import assert from "node:assert/strict";
import {
  organizationIconCatalog,
  organizationIconCategories,
  resolveOrganizationIcon,
  searchOrganizationIcons,
} from "./taskOrganizationIcons.js";

test("organization icon catalog includes broad categories and stable Lucide names", () => {
  assert.ok(organizationIconCatalog.length >= 120);
  assert.ok(organizationIconCategories.some(({ value }) => value === "work"));
  assert.ok(resolveOrganizationIcon("Briefcase"));
  assert.equal(resolveOrganizationIcon("📋"), null);
  assert.equal(resolveOrganizationIcon("UnknownIcon"), null);
});

test("organization icon search matches Chinese labels, keywords, names, and category", () => {
  assert.ok(searchOrganizationIcons("工作").some(({ value }) => value === "Briefcase"));
  assert.ok(searchOrganizationIcons("Briefcase").some(({ value }) => value === "Briefcase"));
  assert.ok(searchOrganizationIcons("文件夹", "work").some(({ value }) => value === "Folder"));
  assert.equal(searchOrganizationIcons("", "classic").length, 36);
  assert.deepEqual(searchOrganizationIcons("no match"), []);
});
