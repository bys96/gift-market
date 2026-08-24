interface ExchangePaymentSession { exchangeRequestId: number; orderId: number; providerOrderId: string; amount: number; }
const KEY = "gift-market-exchange-payment-session-v1";
export function saveExchangePaymentSession(value: ExchangePaymentSession) { sessionStorage.setItem(KEY, JSON.stringify(value)); }
export function getExchangePaymentSession(): ExchangePaymentSession | null {
  try { const value = sessionStorage.getItem(KEY); return value ? JSON.parse(value) as ExchangePaymentSession : null; }
  catch { sessionStorage.removeItem(KEY); return null; }
}
export function clearExchangePaymentSession() { sessionStorage.removeItem(KEY); }
