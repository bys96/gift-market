// Gift Market new-sale policy; historical refunds/settlements keep their original snapshots.
export const MAX_MONEY_AMOUNT = 10_000_000;

export function parseIntegerInput(value: string, field: string): number {
  const normalized = value.trim();
  if (!normalized) throw new Error(`${field}을(를) 입력해주세요.`);
  // Check the text before Number(): a fractional value near an integer can otherwise round to it.
  if (!/^-?\d+$/.test(normalized)) throw new Error(`${field}은(는) 정수로 입력해주세요.`);
  const parsed = Number(normalized);
  if (!Number.isSafeInteger(parsed)) throw new Error(`${field}을(를) 올바르게 입력해주세요.`);
  return parsed;
}

export function validateMoneyInput(value: number, field: string, minimum = 0): number {
  if (!Number.isSafeInteger(value) || value < minimum || value > MAX_MONEY_AMOUNT) {
    throw new Error(`${field}은(는) ${minimum}원 이상 1,000만원 이하의 정수로 입력해주세요.`);
  }
  return value;
}

export function validateVariantPrice(price: number, additional: number, label = "옵션"): number {
  validateMoneyInput(price, "판매가", 1);
  if (!Number.isSafeInteger(additional) || Math.abs(additional) > MAX_MONEY_AMOUNT) {
    throw new Error(`${label} 추가금은 -1,000만원부터 1,000만원까지의 정수로 입력해주세요.`);
  }
  return validateMoneyInput(price + additional, `${label} 적용 후 최종 판매가격`, 1);
}

interface CheckoutMoneyItem {
  price: number;
  quantity: number;
  freeShipping: boolean;
  shippingFee: number;
}

export function calculateCheckoutAmounts(items: readonly CheckoutMoneyItem[]) {
  let productAmount = 0;
  let shippingFee = 0;
  for (const item of items) {
    validateMoneyInput(item.price, "상품 단가", 1);
    if (!Number.isSafeInteger(item.quantity) || item.quantity < 1 || item.quantity > 2_147_483_647) {
      throw new Error("주문 수량을 확인해주세요.");
    }
    productAmount = validateMoneyInput(productAmount + item.price * item.quantity, "상품 합계", 1);
    shippingFee = validateMoneyInput(shippingFee + (item.freeShipping ? 0 : validateMoneyInput(item.shippingFee, "배송비")), "배송비 합계");
  }
  const totalAmount = validateMoneyInput(productAmount + shippingFee, "주문 합계", items.length ? 1 : 0);
  return { productAmount, shippingFee, totalAmount };
}

// Callback/session transport validation also allows already-prepared legacy payments.
export function validatePaymentAmount(value: number): number {
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error("결제 금액을 확인해주세요.");
  return value;
}
