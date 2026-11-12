import type { OrderCreateResponse } from "@/types/order";
import type { PaymentSession } from "@/types/payment";

const PAYMENT_SESSION_KEY = "gift-market-payment-session-v1";
export const ORDER_PREPARATION_STORAGE_KEY =
  "gift-market-order-preparation-v2";
const CHECKOUT_PREPARATION_PREFIX = "gift-market-checkout-preparation-v1-";

export function saveCheckoutPreparation(preparation: OrderCreateResponse) {
  window.sessionStorage.setItem(
    `${CHECKOUT_PREPARATION_PREFIX}${preparation.paymentId}`,
    JSON.stringify(preparation),
  );
}

export function getCheckoutPreparation(paymentId: number): OrderCreateResponse | null {
  try {
    const value = window.sessionStorage.getItem(`${CHECKOUT_PREPARATION_PREFIX}${paymentId}`);
    const preparation = value ? JSON.parse(value) as OrderCreateResponse : null;
    return preparation?.paymentId === paymentId ? preparation : null;
  } catch {
    return null;
  }
}

export function checkoutReturnPath(paymentId: number | null) {
  const url = new URL(window.location.href);
  if (paymentId === null) url.searchParams.delete("paymentId");
  else url.searchParams.set("paymentId", String(paymentId));
  return `${url.pathname}${url.search}`;
}

export function observeCheckoutRestoration(): () => void {
  const reloadCheckout = () => {
    const url = new URL(window.location.href);
    if (url.pathname === "/order" && url.searchParams.has("paymentId")) {
      window.location.reload();
    }
  };
  const restore = (event: PageTransitionEvent) => {
    // React cleanup does not run when the document is frozen in BFCache.
    // Reload only a restored payment checkout, removing the SDK iframe state.
    if (event.persisted) reloadCheckout();
  };
  window.addEventListener("pageshow", restore);
  window.addEventListener("popstate", reloadCheckout);
  return () => {
    window.removeEventListener("pageshow", restore);
    window.removeEventListener("popstate", reloadCheckout);
  };
}

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
