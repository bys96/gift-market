import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";
import { createRequire } from "node:module";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import ts from "typescript";

const require = createRequire(import.meta.url);
const component = new URL("../components/seller/SellerOrderItemProcessing.tsx", import.meta.url);
const exports = {};
vm.runInNewContext(ts.transpileModule(readFileSync(component, "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
}).outputText, { require, exports });

function render(quantity, canceledQuantity, remainingQuantity) {
  return renderToStaticMarkup(React.createElement(exports.default, {
    item: { quantity, canceledQuantity, remainingQuantity },
  }));
}

test("구매확정으로 잔여 수량이 0인 상품은 처리완료로 표시한다", () => {
  assert.equal(render(1, 0, 0), "처리완료");
  assert.equal(render(5, 0, 0), "처리완료");
});

test("취소 수량이 주문 수량과 같을 때만 취소완료 배지를 표시한다", () => {
  assert.equal(render(1, 1, 0), '<span class="seller-order-item-cancelled">취소완료</span>');
  assert.equal(render(5, 5, 0), '<span class="seller-order-item-cancelled">취소완료</span>');
  assert.equal(render(0, 0, 0), "처리완료");
});

test("부분취소 후 나머지 수량이 구매확정되어도 전체 취소로 표시하지 않는다", () => {
  assert.equal(render(5, 2, 0), "취소 2개 · 처리완료");
});

test("미처리 및 일부 구매확정 상품의 처리 예정 수량을 유지한다", () => {
  assert.equal(render(5, 0, 5), "처리 예정 5개");
  assert.equal(render(5, 0, 3), "처리 예정 3개");
  assert.equal(render(5, 2, 3), "취소 2개 · 처리 예정 3개");
  assert.equal(render(5, 2, 1), "취소 2개 · 처리 예정 1개");
});
