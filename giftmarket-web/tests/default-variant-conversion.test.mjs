import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";
import ts from "typescript";

const source = readFileSync(new URL("../components/seller/ProductOptionManager.tsx", import.meta.url), "utf8");
const tree = ts.createSourceFile("editor.tsx", source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
let handler;
function visit(node) {
  if (ts.isVariableDeclaration(node) && node.name.getText(tree) === "handleEnabledChange") handler = node;
  ts.forEachChild(node, visit);
}
visit(tree);

function attempt({ available = true, acknowledged = true, existingOptions = false } = {}) {
  const state = { enabled: false, error: "", groups: [] };
  const context = {
    productId: 1, fixedModeRef: { current: existingOptions },
    conversion: { available, reason: available ? null : "결제 대기 주문이 있습니다." },
    retainDefaultStock: acknowledged, optionGroups: [],
    setEnabled: value => { state.enabled = value; },
    setErrorMessage: value => { state.error = value; },
    setOptionGroups: value => { state.groups = value; },
    setVariants: () => {}, createEmptyOptionGroup: () => ({ id: null, values: [] }),
  };
  vm.runInNewContext(ts.transpileModule(`const ${handler.getText(tree)}; handleEnabledChange(true);`, {
    compilerOptions: { target: ts.ScriptTarget.ES2022 },
  }).outputText, context);
  return state;
}

test("conversion requires explicit retained-stock acknowledgement", () => {
  assert.equal(attempt({ acknowledged: false }).enabled, false);
  assert.match(attempt({ acknowledged: false }).error, /동의/);
  const accepted = attempt();
  assert.equal(accepted.enabled, true);
  assert.equal(accepted.groups.length, 1);
});

test("pending server conditions block conversion and show their reason", () => {
  const blocked = attempt({ available: false });
  assert.equal(blocked.enabled, false);
  assert.equal(blocked.groups.length, 0);
  assert.match(blocked.error, /결제 대기/);
});
