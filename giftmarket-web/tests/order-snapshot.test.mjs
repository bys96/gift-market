import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";
import ts from "typescript";

// Exercise the actual checkout comparator rather than duplicating its logic.
const source = readFileSync(new URL("../app/order/page.tsx", import.meta.url), "utf8");
const tree = ts.createSourceFile("order.tsx", source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
const declaration = tree.statements.find(node => ts.isFunctionDeclaration(node) && node.name?.text === "hasMatchingOrderSnapshot");
assert.ok(declaration);
const exports = {};
vm.runInNewContext(ts.transpileModule(`${declaration.getText(tree)}\nexports.matches = hasMatchingOrderSnapshot;`, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
}).outputText, { exports });
const matches = exports.matches;
const amounts = { totalProductAmount: 20000, totalShippingFee: 3000, totalAmount: 23000 };
const expected = [{ productId: 1, variantId: null, quantity: 2, unitPrice: 10000 }];
const detail = { ...amounts, items: [{ ...expected[0], variantId: 421, optionSnapshot: null }] };
const plainProduct = { id: 1, hasOptions: false };
const compare = (actual = detail, items = expected, context = plainProduct, prepared = amounts) =>
  matches(prepared, actual, items, 20000, 3000, 23000, context);

test("무옵션 바로 구매의 NULL 요청과 서버 기본 Variant ID를 같은 선택으로 비교한다", () => {
  assert.equal(compare(), true);
  // Legacy unconverted snapshots still retain their original NULL reference.
  assert.equal(compare({ ...detail, items: [{ ...detail.items[0], variantId: null }] }), true);
  assert.equal(detail.items[0].variantId, 421); // Never mutate the server snapshot.
});

test("무옵션 보정은 서버 상품 정보가 무옵션인 단일 바로 구매에만 적용한다", () => {
  assert.equal(compare(detail, expected, { id: 1, hasOptions: true }), false);
  assert.equal(compare(detail, expected, { id: 2, hasOptions: false }), false);
  assert.equal(compare(detail, expected, null), false);
  assert.equal(compare({ ...detail, items: [{ ...detail.items[0], optionSnapshot: "색상: 빨강" }] }), false);
  assert.equal(compare({ ...detail, items: [...detail.items, ...detail.items] }), false);
  assert.equal(compare(detail, [...expected, ...expected]), false);
  for (const variantId of [0, -1, undefined]) {
    assert.equal(compare({ ...detail, items: [{ ...detail.items[0], variantId }] }), false);
  }
});

test("상품·수량·단가와 주문/결제 준비 금액 차이는 계속 차단한다", () => {
  for (const patch of [{ productId: 2 }, { quantity: 1 }, { unitPrice: 9999 }]) {
    assert.equal(compare({ ...detail, items: [{ ...detail.items[0], ...patch }] }), false);
  }
  for (const field of Object.keys(amounts)) {
    assert.equal(compare({ ...detail, [field]: amounts[field] + 1 }), false);
    assert.equal(compare(detail, expected, plainProduct, { ...amounts, [field]: amounts[field] + 1 }), false);
  }
});

test("장바구니 기본 Variant와 명시한 Variant는 ID를 엄격하게 비교한다", () => {
  const cartItems = [{ ...expected[0], variantId: 421 }];
  assert.equal(compare(detail, cartItems, null), true);
  assert.equal(compare(detail, [{ ...cartItems[0], variantId: 422 }], null), false);
  assert.equal(compare(detail, [{ ...cartItems[0], variantId: 422 }]), false);
});

test("기존 단일 옵션과 다중 옵션은 순서와 무관하게 정확한 Variant를 검증한다", () => {
  const optionItems = [{ ...expected[0], variantId: 10 }];
  const optionDetail = { ...detail, items: [{ ...optionItems[0], optionSnapshot: "크기: 소형" }] };
  const optionProduct = { id: 1, hasOptions: true };
  assert.equal(compare(optionDetail, optionItems, optionProduct), true);
  assert.equal(compare(optionDetail, [{ ...optionItems[0], variantId: 20 }], optionProduct), false);
  const multi = [
    { productId: 1, variantId: 10, quantity: 1, unitPrice: 8000 },
    { productId: 1, variantId: 20, quantity: 1, unitPrice: 12000 },
  ];
  const multiDetail = { ...amounts, items: [...multi].reverse().map(item => ({ ...item, optionSnapshot: "크기" })) };
  assert.equal(compare(multiDetail, multi, optionProduct), true);
  assert.equal(compare({ ...multiDetail, items: [multiDetail.items[0]] }, multi, optionProduct), false);
});

test("신규 주문과 기존 READY 주문 재개 모두 바로 구매 상품 문맥을 전달한다", () => {
  const calls = [];
  function visit(node) {
    if (ts.isCallExpression(node) && node.expression.getText(tree) === "hasMatchingOrderSnapshot") calls.push(node);
    ts.forEachChild(node, visit);
  }
  visit(tree);
  assert.equal(calls.length, 2);
  for (const call of calls) {
    assert.equal(call.arguments.length, 7);
    assert.equal(call.arguments[6].getText(tree), "isDirectOrder ? directProduct : null");
  }
});
