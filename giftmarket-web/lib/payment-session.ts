import type { OrderCreateResponse } from "@/types/order";
import type { PaymentSession } from "@/types/payment";

const PAYMENT_SESSION_KEY = "gift-market-payment-session-v1";
export const ORDER_PREPARATION_STORAGE_KEY =
  "gift-market-order-preparation-v2";

function readSessions(): Record<string, PaymentSession> {
  if (typeof window === "undefined") {
    return {};
  }

  try {
    const value = window.sessionStorage.getItem(PAYMENT_SESSION_KEY);
    return value ? (JSON.parse(value) as Record<string, PaymentSession>) : {};
  } catch {
    window.sessionStorage.removeItem(PAYMENT_SESSION_KEY);
    return {};
  }
}

function writeSessions(sessions: Record<string, PaymentSession>) {
  if (typeof window === "undefined") {
    return;
  }

  window.sessionStorage.setItem(PAYMENT_SESSION_KEY, JSON.stringify(sessions));
}

export function createCustomerKey() {
  return `customer-${crypto.randomUUID()}`;
}

export function savePaymentSession(
  preparation: OrderCreateResponse,
  customerKey: string,
  returnPath: string,
) {
  const sessions = readSessions();
  sessions[preparation.merchantPaymentId] = {
    paymentId: preparation.paymentId,
    orderId: preparation.orderId,
    merchantPaymentId: preparation.merchantPaymentId,
    amount: preparation.amount,
    orderName: preparation.orderName,
    expiresAt: preparation.expiresAt,
    customerKey,
    returnPath,
  };
  writeSessions(sessions);
}

export function getPaymentSession(merchantPaymentId: string) {
  return readSessions()[merchantPaymentId] ?? null;
}

export function removePaymentSession(merchantPaymentId: string) {
  const sessions = readSessions();
  delete sessions[merchantPaymentId];
  writeSessions(sessions);
}

export function clearCompletedPaymentSession(merchantPaymentId: string) {
  removePaymentSession(merchantPaymentId);
  if (typeof window === "undefined") {
    return;
  }

  window.sessionStorage.removeItem(ORDER_PREPARATION_STORAGE_KEY);
}
