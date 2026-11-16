import assert from "node:assert/strict";
import test from "node:test";
import { createVariantStockUpdate } from "../lib/variant-stock.ts";

test("metadata saves omit stock even when the server stock changed after loading", () => {
  assert.deepEqual(createVariantStockUpdate(1, 10, 10, 3), {});
});
test("intentional stock edits carry the version from the loaded screen", () => {
  assert.deepEqual(createVariantStockUpdate(1, 12, 10, 3), { stockQuantity: 12, expectedVersion: 3 });
});
test("new variants include initial stock and old drafts cannot silently change stock", () => {
  assert.deepEqual(createVariantStockUpdate(null, 10), { stockQuantity: 10 });
  assert.throws(() => createVariantStockUpdate(1, 12), /최신/);
});
