import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";
import ts from "typescript";

const source = readFileSync(new URL("../components/order/OrderExchangePanel.tsx", import.meta.url), "utf8");
const tree = ts.createSourceFile("panel.tsx", source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
function find(node, predicate) {
  if (predicate(node)) return node;
  return ts.forEachChild(node, (child) => find(child, predicate));
}
function declaration(name) {
  return find(tree, (node) => ts.isVariableDeclaration(node) && node.name.getText(tree) === name).initializer;
}
function evaluate(expression, context) {
  const exports = {};
  vm.runInNewContext(ts.transpileModule(`exports.value = (${expression});`, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText, { exports, ...context });
  return exports.value;
}
const variant = (id, stockQuantity = 5, price = 25000, available = true) => ({
  id, stockQuantity, price, available, optionValueIds: [id],
});
function options(name, variants, quantity = 1) {
  return evaluate(declaration(name).getText(tree), {
    product: { hasOptions: true, variants }, item: { variantId: 501, unitPrice: 25000 },
    selected: { quantity }, quantity,
    count: evaluate(declaration("count").getText(tree), {}),
  });
}

test("initial selection and option lists include both the original and another equal-price variant", () => {
  const variants = [variant(501), variant(502)];
  assert.equal(options("firstTarget", variants), 501);
  for (const name of ["variants", "nextVariants"]) {
    assert.deepEqual(Array.from(options(name, variants), (value) => value.id), [501, 502]);
  }
});

test("same-option availability still requires active stock, requested quantity and matching price", () => {
  const variants = [variant(501, 1), variant(502, 5), variant(503, 0), variant(504, 5, 26000), variant(505, 5, 25000, false)];
  assert.equal(options("firstTarget", variants), 501);
  for (const name of ["variants", "nextVariants"]) {
    assert.deepEqual(Array.from(options(name, variants, 2), (value) => value.id), [502]);
  }
  assert.equal(options("firstTarget", [variant(501, 0)]), null);
});

test("the purchased variant is labeled while a different variant keeps its existing option label", () => {
  const label = find(tree, (node) => ts.isConditionalExpression(node)
    && node.getText(tree).includes("(현재 구매한 옵션)")
    && node.condition.getText(tree) === "variant.id === item.variantId").getText(tree);
  for (const [id, expected] of [[501, "플로럴 (현재 구매한 옵션)"], [502, "머스크"]]) {
    assert.equal(evaluate(label, {
      variant: variant(id), item: { variantId: 501 }, product: {},
      variantLabel: (_product, ids) => ids[0] === 501 ? "플로럴" : "머스크",
    }), expected);
  }
});

test("changing the exchange reason preserves either selected target option", () => {
  for (const targetVariantId of [501, 502]) {
    const selection = { 71: { quantity: 1, targetVariantId } };
    const reasons = [];
    let invalidated = 0;
    const changeReason = evaluate(declaration("changeReasonType").getText(tree), {
      selection, invalidate: () => invalidated++, setReasonType: (reason) => reasons.push(reason),
      setSelection: () => assert.fail("Changing reason must preserve the selected option"),
    });
    for (const reason of ["DEFECTIVE", "CHANGE_OF_MIND", "OPTION_MISTAKE", "OTHER"]) changeReason(reason);
    assert.equal(selection[71].targetVariantId, targetVariantId);
    assert.equal(invalidated, 4);
    assert.equal(reasons.length, 4);
  }
});
