import test from "node:test";
import assert from "node:assert/strict";
import { addVariantSelection, changeSelectionQuantity, selectionTotal, parsePurchaseSelections, isDirectPurchaseUnavailable } from "../lib/product-selection.ts";

const small = { id: 10, available: true, stockQuantity: 3, price: 10000 };
const large = { id: 20, available: true, stockQuantity: 5, price: 12000 };

test("소형 2개와 대형 2개 선택, 중복 옵션 수량 병합 및 합계", () => {
  let items = [];
  for (const variant of [small, small, large, large]) items = addVariantSelection(items, variant);
  assert.deepEqual(items, [{ variantId: 10, quantity: 2 }, { variantId: 20, quantity: 2 }]);
  assert.equal(selectionTotal(items, [small, large]), 44000);
  const params = new URLSearchParams({ productId: "1", items: JSON.stringify(items) });
  assert.deepEqual(parsePurchaseSelections(new URLSearchParams(params.toString()).get("items")), items);
});

test("옵션별 증감, 최저 수량과 재고 상한, 삭제 후 나머지 옵션 유지", () => {
  let items = [{ variantId: 10, quantity: 2 }, { variantId: 20, quantity: 2 }];
  items = changeSelectionQuantity(items, 10, 1, 3);
  items = addVariantSelection(items, small);
  assert.equal(items[0].quantity, 3);
  items = changeSelectionQuantity(items, 20, -10, 5);
  assert.equal(items[1].quantity, 1);
  assert.equal(items[0].quantity, 3);
  items = items.filter(item => item.variantId !== 10);
  assert.deepEqual(items, [{ variantId: 20, quantity: 1 }]);
});

test("보관·품절 옵션을 추가하지 않는다", () => {
  for (const variant of [{ ...small, available: false }, { ...large, stockQuantity: 0 }]) {
    assert.deepEqual(addVariantSelection([], variant), []);
  }
});

test("위조·중복·빈 URL 항목은 전체 거부, 무옵션 항목은 지원", () => {
  for (const value of ["bad", "[]", "{}", '[{"variantId":10,"quantity":0}]',
    '[{"variantId":10,"quantity":2},{"variantId":10,"quantity":2}]',
    '[{"variantId":20,"quantity":1},{"quantity":2}]']) {
    assert.deepEqual(parsePurchaseSelections(value), []);
  }
  assert.deepEqual(parsePurchaseSelections('[{"variantId":null,"quantity":2}]'), [{ variantId: null, quantity: 2 }]);
});

test("신규 주문은 옵션별 재고 검증, 서버 확인된 기존 예약 주문은 품절이어도 재개", () => {
  const product = { status: "ON_SALE", hasOptions: true, stockQuantity: 8, variants: [small, large] };
  const items = [{ variantId: 10, quantity: 2 }, { variantId: 20, quantity: 2 }];
  assert.equal(isDirectPurchaseUnavailable(product, items), false);
  assert.equal(isDirectPurchaseUnavailable(product, [...items, { variantId: 99, quantity: 1 }]), true);
  const soldOut = { ...product, status: "SOLD_OUT", stockQuantity: 0,
    variants: product.variants.map(v => ({ ...v, stockQuantity: 0, available: false })) };
  assert.equal(isDirectPurchaseUnavailable(soldOut, items), true);
  assert.equal(isDirectPurchaseUnavailable(soldOut, items, true), false);
  assert.equal(isDirectPurchaseUnavailable({ ...soldOut, variants: [soldOut.variants[0]] }, items, true), true);
  assert.equal(isDirectPurchaseUnavailable({ ...soldOut, status: "HIDDEN" }, items, true), true);
  const plain = { status: "ON_SALE", hasOptions: false, stockQuantity: 2, variants: [] };
  assert.equal(isDirectPurchaseUnavailable(plain, [{ variantId: null, quantity: 2 }]), false);
  assert.equal(isDirectPurchaseUnavailable(plain, [{ variantId: null, quantity: 3 }]), true);
});
