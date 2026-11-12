import assert from "node:assert/strict";
import test from "node:test";
import { getBuyerOrderStatus } from "../lib/buyer-order-status.ts";

const item = (id = 1, quantity = 1, canceledQuantity = 0) => ({ id, quantity, canceledQuantity });
const request = (status, quantity = 1, orderItemId = 1, sellerOrderId = 70) => ({
  status, sellerOrderId,
  items: [{ orderItemId, quantity, returnedQuantity: 999 }],
});
const display = (returns = [], items = [item()], status = "DELIVERED") =>
  getBuyerOrderStatus(status, status === "DELIVERED" ? "배송완료" : "기존 상태", items, returns);

test("배송완료, 일반 부분취소, 구매확정은 반품 완료로 표시하지 않는다", () => {
  assert.deepEqual(display(), { label: "배송완료", tone: "delivered" });
  assert.equal(display([], [item(1, 3, 1)]).label, "배송완료");
  assert.equal(display([], [{ ...item(), confirmedQuantity: 1 }]).label, "배송완료");
});

test("신청 및 승인부터 환불까지 진행 상태를 구분한다", () => {
  assert.equal(display([request("REQUESTED")]).label, "반품요청");
  for (const status of ["APPROVED", "COLLECTING", "RECEIVED", "INSPECTED", "REFUNDING"]) {
    assert.deepEqual(display([request(status)]), { label: "반품진행중", tone: "pending" });
  }
});

test("환불 완료 요청의 수량만 집계하고 누적 returnedQuantity를 중복 합산하지 않는다", () => {
  assert.equal(display([request("COMPLETED")]).label, "반품완료");
  assert.equal(display([request("COMPLETED")], [item(1, 3)]).label, "부분반품완료");
  assert.equal(display([request("COMPLETED"), request("COMPLETED")], [item(1, 3)]).label, "부분반품완료");
  assert.equal(display([request("COMPLETED"), request("COMPLETED", 2)], [item(1, 3)]).label, "반품완료");
  assert.equal(display([request("COMPLETED")], [item(1, 3, 2)]).label, "반품완료");
});

test("복수 판매자 및 상품의 전체 상태와 개별 상태를 각각 계산한다", () => {
  const returns = [request("COMPLETED")];
  assert.equal(display(returns, [item(), item(2)]).label, "부분반품완료");
  assert.equal(display(returns, [item()]).label, "반품완료");
  assert.equal(display(returns, [item(2)]).label, "배송완료");
  assert.equal(display([request("COLLECTING")], [item(2)]).label, "배송완료");
  assert.equal(display(returns, [item(), item(2)], "SHIPPING").label, "부분반품완료");
});

test("진행 및 완료 혼재 시 미완료 반품을 우선 표시한다", () => {
  assert.equal(display([request("COMPLETED"), request("REQUESTED", 1, 2)], [item(), item(2)]).label, "반품요청");
  assert.equal(display([request("COMPLETED"), request("COLLECTING", 1, 2)], [item(), item(2)]).label, "반품진행중");
});

test("거절, 철회, 실패 이력으로 반품 완료를 추정하지 않는다", () => {
  for (const status of ["REJECTED", "CANCELED", "FAILED"]) {
    assert.equal(display([request(status)]).label, "배송완료");
  }
});

test("기존 전체취소가 반품이나 조회 오류보다 우선한다", () => {
  assert.equal(display([request("REQUESTED")], [item()], "CANCELLED").label, "취소완료");
  assert.equal(getBuyerOrderStatus("CANCELLED", "", [item()], [], true, true).label, "취소완료");
});

test("조회 중이나 실패 시 빈 반품 이력으로 확정하지 않는다", () => {
  assert.equal(getBuyerOrderStatus("DELIVERED", "배송완료", [item()], [], true).label, "상태 확인 중");
  assert.equal(getBuyerOrderStatus("DELIVERED", "배송완료", [item()], [], false, true).label, "상태 확인 필요");
});
