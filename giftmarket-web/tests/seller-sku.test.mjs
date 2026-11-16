import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";
import ts from "typescript";
import { normalizeSellerSkuCode } from "../lib/seller-sku.ts";

test("seller SKU normalization preserves allowed punctuation and detects case/space duplicates", () => {
  assert.equal(normalizeSellerSkuCode(" f1 "), "F1");
  assert.equal(normalizeSellerSkuCode("red_%--"), "RED_%--");
  assert.equal(normalizeSellerSkuCode(" red "), normalizeSellerSkuCode("RED"));
  assert.throws(() => normalizeSellerSkuCode(" "));
  assert.throws(() => normalizeSellerSkuCode("ß".repeat(51)));
  // No global cache: identical codes can be submitted for independent products.
  assert.equal(normalizeSellerSkuCode("F1"), normalizeSellerSkuCode("F1"));
});

test("editor loads seller code, keeps immutable system display and preserves existing draft field", () => {
  const source = readFileSync(new URL("../components/seller/ProductOptionManager.tsx", import.meta.url), "utf8");
  assert.match(source, /skuCode: variant\.sellerSkuCode/);
  assert.match(source, /systemSkuCode: variant\.skuCode/);
  assert.match(source, /시스템 SKU: \{variant\.systemSkuCode\}/);
  assert.doesNotMatch(source, /value=\{variant\.systemSkuCode\}/);
  assert.match(source, /placeholder="예: F1, RED, BLUE"/);
});

test("registration and modification transmit explicit seller code rather than system SKU", () => {
  const source = readFileSync(new URL("../components/seller/ProductForm.tsx", import.meta.url), "utf8");
  assert.equal([...source.matchAll(/sellerSkuCode: normalizeSellerSkuCode\(skuCode\)/g)].length, 2);
  assert.doesNotMatch(source, /systemSkuCode:/);
  assert.match(source, /skuCodes\.has\(normalizedSkuCode\)/);
});

function editorFunctions() {
  const source = readFileSync(new URL("../components/seller/ProductOptionManager.tsx", import.meta.url), "utf8");
  const tree = ts.createSourceFile("editor.tsx", source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const names = ["createVariantClientKey", "createOptionCombinations", "createVariantDrafts", "synchronizeVariants"];
  const declarations = tree.statements.filter((node) => ts.isFunctionDeclaration(node) && names.includes(node.name?.text));
  assert.equal(declarations.length, names.length);
  const exports = {};
  vm.runInNewContext(ts.transpileModule(declarations.map((node) => node.getText(tree)).join("\n")
    + "\nexports.load = createVariantDrafts; exports.sync = synchronizeVariants;", {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText, { exports });
  return exports;
}

const group = { clientId: "g", id: 11, name: "색상", values: [{ clientId: "r", id: 21, value: "빨강" }] };
const saved = { id: 123, sellerSkuCode: "F1", skuCode: "GM-system", additionalPrice: 100, stockQuantity: 7,
  active: true, optionValues: [{ optionValueId: 21, optionGroupSortOrder: 0, optionValueSortOrder: 0 }] };

for (const count of [2, 3]) {
  test(`${count} groups retain Variant identity, seller code and stock when restored in another order`, () => {
    const { load, sync } = editorFunctions();
    const groups = Array.from({ length: count }, (_, i) => ({ clientId: `g${i}`, id: i + 1,
      name: `group${i}`, values: [{ clientId: `v${i}`, id: i + 21, value: `value${i}` }] }));
    const variant = { ...saved, mappingValid: true, optionValues: groups.map((g, i) => ({
      optionValueId: g.values[0].id, optionGroupSortOrder: i, optionValueSortOrder: 0,
    })) };
    const history = load([variant], groups);
    const removedIndex = count === 2 ? 0 : 1;
    assert.equal(sync(groups.filter((_, i) => i !== removedIndex), history)[0].id, null);
    const restored = sync([...groups].reverse(), history);
    assert.equal(restored[0].id, 123);
    assert.equal(restored[0].skuCode, "F1");
    assert.equal(restored[0].systemSkuCode, "GM-system");
    assert.equal(restored[0].stockQuantity, "7");
    const extended = groups.map((g, i) => i === 0
      ? { ...g, values: [...g.values, { clientId: "new", id: null, value: "new" }] } : g);
    assert.equal(sync(extended, history).filter(v => v.id === null).length, 1);
    assert.equal(sync(extended, history).filter(v => v.id === 123).length, 1);
  });
}

test("server-detected partial mapping damage is excluded rather than treated as a smaller valid combination", () => {
  const { load } = editorFunctions();
  assert.equal(load([{ ...saved, mappingValid: false }], [group]).length, 0);
});

test("archived groups do not discard older variants and restoring a combination reuses its stock and identity", () => {
  const { load, sync } = editorFunctions();
  const archivedGroup = { clientId: "old", id: 12, name: "이전 옵션", values: [{ clientId: "old-value", id: 22, value: "이전 값" }] };
  const history = load([{ ...saved, active: false, optionValues: [...saved.optionValues] }], [group, archivedGroup]);
  assert.equal(history.length, 1);
  assert.equal(sync([], history).length, 0);
  const restored = sync([group], history);
  assert.equal(restored[0].id, 123);
  assert.equal(restored[0].stockQuantity, "7");
  assert.equal(restored[0].systemSkuCode, "GM-system");
  assert.equal(restored[0].active, false);
});

test("editing loads seller input separately from system SKU and retains existing variant identity", () => {
  const { load, sync } = editorFunctions();
  const drafts = load([{ ...saved, optionValues: [...saved.optionValues] }], [group]);
  assert.equal(drafts[0].skuCode, "F1");
  assert.equal(drafts[0].systemSkuCode, "GM-system");
  drafts[0].skuCode = "RED";
  const synchronized = sync([group], drafts);
  assert.equal(synchronized[0].id, 123);
  assert.equal(synchronized[0].skuCode, "RED");
  assert.equal(synchronized[0].systemSkuCode, "GM-system");
  assert.equal(synchronized[0].stockQuantity, "7");
});

test("adding an option retains old identity and leaves system SKU issuance to the server", () => {
  const { load, sync } = editorFunctions();
  const drafts = load([{ ...saved, optionValues: [...saved.optionValues] }], [group]);
  const extended = { ...group, values: [...group.values, { clientId: "b", id: null, value: "파랑" }] };
  const result = sync([extended], drafts);
  assert.equal(result[0].id, 123);
  assert.equal(result[0].systemSkuCode, "GM-system");
  assert.equal(result[1].id, null);
  assert.equal(result[1].skuCode, "");
  assert.equal(result[1].systemSkuCode, undefined);
});

test("deactivation and reactivation preserve the existing draft code and system identity", () => {
  const { load, sync } = editorFunctions();
  const drafts = load([{ ...saved, active: false, optionValues: [...saved.optionValues] }], [group]);
  const inactive = sync([group], drafts);
  assert.equal(inactive[0].active, false);
  inactive[0].active = true;
  const reactivated = sync([group], inactive);
  assert.equal(reactivated[0].id, 123);
  assert.equal(reactivated[0].systemSkuCode, "GM-system");
  assert.equal(reactivated[0].skuCode, "F1");
});
