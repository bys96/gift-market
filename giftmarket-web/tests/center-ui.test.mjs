import assert from "node:assert/strict";
import test from "node:test";
import fs from "node:fs";
import vm from "node:vm";
import { createRequire } from "node:module";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import ts from "typescript";
import { getBuyerOrderStatus } from "../lib/buyer-order-status.ts";
import { getProductSaveErrorKind } from "../lib/product-save-error.ts";

const require = createRequire(import.meta.url);
function component(path, mocks) {
  const code = ts.transpileModule(fs.readFileSync(new URL(path, import.meta.url), "utf8"), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true },
  }).outputText;
  const exports = {};
  vm.runInNewContext(code, { exports, require: (id) => mocks[id] ?? require(id) });
  return exports.default;
}

test("order history, detail and seller groups render the same named and colored badge", () => {
  const Badge = component("../components/order/BuyerOrderStatusBadge.tsx", {
    "@/lib/buyer-order-status": { getBuyerOrderStatus },
  });
  for (const [status, label, tone] of [
    ["PAID", "결제 완료", "paid"], ["PREPARING", "상품준비중", "pending"],
    ["SHIPPING", "배송중", "shipping"], ["SHIPPED", "배송중", "shipping"],
    ["DELIVERED", "배송완료", "delivered"], ["CANCELLED", "취소완료", "closed"],
    ["PAYMENT_PENDING", "결제 대기", "neutral"], ["PENDING_PAYMENT", "결제 대기", "neutral"],
    ["PAYMENT_FAILED", "결제 실패", "closed"], ["PAYMENT_EXPIRED", "결제 만료", "neutral"],
  ]) {
    for (const className of ["order-history-status", "order-detail-status", "order-detail-status-badge"]) {
      const html = renderToStaticMarkup(React.createElement(Badge, {
        status, label: "screen-specific label must not override the shared mapping", className, items: [], returns: [],
      }));
      assert.match(html, /^<span /);
      assert.ok(html.includes(`buyer-order-status-${tone}`));
      assert.ok(html.endsWith(`>${label}</span>`));
    }
  }
});

test("admin menu order, active child route and super-admin visibility survive removal of branding", () => {
  let role = "SUPER_ADMIN";
  const Sidebar = component("../components/admin/AdminSidebar.tsx", {
    "next/link": { __esModule: true, default: ({ children, ...props }) => React.createElement("a", props, children) },
    "next/navigation": { usePathname: () => "/admin/products/123" },
    "@/components/common/modal/Modal": { __esModule: true, default: () => null },
    "@/stores/auth-store": { useAuthStore: (selector) => selector({ user: { role } }) },
  });
  const html = renderToStaticMarkup(React.createElement(Sidebar));
  const menu = html.slice(html.indexOf('<nav'), html.indexOf('</nav>'));
  const routes = [...menu.matchAll(/href="([^"]+)"/g)].map((match) => match[1]);
  assert.deepEqual(routes, ["/admin", "/admin/users", "/admin/administrators", "/admin/sellers",
    "/admin/seller-applications", "/admin/gift-occasions", "/admin/products", "/admin/orders",
    "/admin/cancellations", "/admin/returns", "/admin/exchanges", "/admin/settlements"]);
  assert.match(menu, /href="\/admin\/products"[^>]*aria-current="page"/);
  assert.ok(!html.includes("admin-center-brand"));
  assert.ok(html.includes("쇼핑몰로 돌아가기"));
  role = "ADMIN";
  assert.ok(!renderToStaticMarkup(React.createElement(Sidebar)).includes('href="/admin/administrators"'));
});

test("SKU validation is distinct from stale data and other server conflicts", () => {
  assert.equal(getProductSaveErrorKind(400, "옵션 관리 코드를 입력해주세요."), "validation");
  assert.equal(getProductSaveErrorKind(409, "동일 상품의 옵션 관리 코드는 중복될 수 없습니다."), "validation");
  assert.equal(getProductSaveErrorKind(409, "상품 또는 재고가 변경되었습니다. 최신 값을 다시 조회한 후 저장해주세요."), "stale");
  assert.equal(getProductSaveErrorKind(409, "이전 형식의 상품 요청입니다. 최신 화면에서 확인해주세요."), "stale");
  assert.equal(getProductSaveErrorKind(409, "옵션 매핑 대사가 필요합니다."), "conflict");
});
