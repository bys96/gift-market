import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";
import vm from "node:vm";
import ts from "typescript";

const root = resolve(import.meta.dirname, "..");
const exports = {};
vm.runInNewContext(ts.transpileModule(readFileSync(resolve(root, "lib/money.ts"), "utf8"), {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText, { exports });
const { MAX_MONEY_AMOUNT, parseIntegerInput, validateMoneyInput, validateVariantPrice, calculateCheckoutAmounts, validatePaymentAmount } = exports;
const item = (price, quantity = 1, shippingFee = 0) => ({ price, quantity, shippingFee, freeShipping: false });

test("frontend and backend use the same new-sale ceiling", () => {
  const backend = readFileSync(resolve(root, "../giftmarket-api/src/main/java/com/giftmarket/global/util/MoneyLimits.java"), "utf8");
  const literal = backend.match(/MAX_AMOUNT\s*=\s*([\d_]+)L/)[1];
  assert.equal(MAX_MONEY_AMOUNT, Number(literal.replaceAll("_", "")));
});

test("input text is checked before Number can silently round a fraction", () => {
  assert.equal(parseIntegerInput("1000", "가격"), 1000);
  assert.equal(parseIntegerInput("-1000", "추가금"), -1000);
  for (const value of ["1000.5", "1.0000000000000000001", "1e3", "", "9223372036854775807"]) {
    assert.throws(() => parseIntegerInput(value, "가격"));
  }
});

test("price and shipping boundaries reject negatives, decimals, huge and unsafe numbers", () => {
  assert.equal(validateMoneyInput(MAX_MONEY_AMOUNT, "가격", 1), MAX_MONEY_AMOUNT);
  assert.equal(validateMoneyInput(0, "배송비"), 0);
  for (const value of [-1, 0.5, MAX_MONEY_AMOUNT + 1, Number.MAX_SAFE_INTEGER + 1, Infinity, NaN]) {
    assert.throws(() => validateMoneyInput(value, "금액"));
  }
});

test("negative option additional prices remain supported only with a valid final price", () => {
  assert.equal(validateVariantPrice(10_000, -9_999), 1);
  assert.equal(validateVariantPrice(MAX_MONEY_AMOUNT, 0), MAX_MONEY_AMOUNT);
  assert.throws(() => validateVariantPrice(10_000, -10_000));
  assert.throws(() => validateVariantPrice(MAX_MONEY_AMOUNT, 1));
});

test("multiple products keep per-item shipping and exact checkout boundary", () => {
  const normal = calculateCheckoutAmounts([item(10_000, 2, 3_000), item(20_000, 1, 5_000)]);
  assert.equal(normal.productAmount, 40_000);
  assert.equal(normal.shippingFee, 8_000);
  assert.equal(normal.totalAmount, 48_000);
  assert.equal(calculateCheckoutAmounts([item(MAX_MONEY_AMOUNT - 1, 1, 1)]).totalAmount, MAX_MONEY_AMOUNT);
  assert.throws(() => calculateCheckoutAmounts([item(MAX_MONEY_AMOUNT, 1, 1)]));
});

test("quantity multiplication and invalid values cannot reach payment preparation", () => {
  assert.throws(() => calculateCheckoutAmounts([item(MAX_MONEY_AMOUNT, 2_147_483_647)]));
  assert.throws(() => calculateCheckoutAmounts([item(100, -1)]));
  assert.throws(() => calculateCheckoutAmounts([item(100, 0.5)]));
  assert.throws(() => calculateCheckoutAmounts([item(Number.MAX_SAFE_INTEGER + 1)]));
  const checkout = readFileSync(resolve(root, "app/order/page.tsx"), "utf8");
  assert.ok(checkout.includes("if (moneyError) throw new Error(moneyError)"));
  assert.ok(checkout.includes("if (moneyError ||"));
});

test("legacy prepared payments retain safe-integer transport support", () => {
  assert.equal(validatePaymentAmount(MAX_MONEY_AMOUNT + 1), MAX_MONEY_AMOUNT + 1);
  assert.throws(() => validatePaymentAmount(Number.MAX_SAFE_INTEGER + 1));
  assert.throws(() => validatePaymentAmount(0.5));
});
