import test from "node:test";
import assert from "node:assert/strict";
import { commonTaskColors, isHexColor, taskColorFamilies } from "./taskColors.js";

test("task color palette offers common colors before detailed hue families", () => {
  assert.equal(commonTaskColors[0].name, "无颜色");
  assert.equal(commonTaskColors[0].value, "");
  assert.equal(commonTaskColors.length, 9);
  assert.equal(taskColorFamilies.length, 9);
  assert.ok(taskColorFamilies.every(family => family.shades.length === 7));
});

test("custom task colors must be six-digit hex values", () => {
  assert.equal(isHexColor("#a1B2c3"), true);
  assert.equal(isHexColor("#abc"), false);
  assert.equal(isHexColor("red"), false);
});
