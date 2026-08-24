import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type { PaymentConfirmRequest } from "@/types/payment";
import type { ExchangeRequest, ExchangeRequestCreateRequest, ExchangeShippingPayment } from "@/types/exchange";

function unwrap<T>(response: ApiResponse<T>, fallback: string): T {
  if (!response.success || response.data == null) throw new Error(response.message || fallback);
  return response.data;
}
export async function createExchangeRequest(orderId: number, sellerOrderId: number, request: ExchangeRequestCreateRequest) {
  return unwrap(await apiFetch<ApiResponse<ExchangeRequest>>(`/api/orders/${orderId}/seller-orders/${sellerOrderId}/exchanges`, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(request),
  }), "교환 요청을 접수하지 못했습니다.");
}
export async function getOrderExchangeRequests(orderId: number) {
  return unwrap(await apiFetch<ApiResponse<ExchangeRequest[]>>(`/api/orders/${orderId}/exchanges`), "교환 이력을 불러오지 못했습니다.");
}
export async function getExchangeShippingPayment(exchangeRequestId: number) {
  return unwrap(await apiFetch<ApiResponse<ExchangeShippingPayment>>(`/api/exchanges/${exchangeRequestId}/shipping-payment`), "교환 배송비 결제 정보를 불러오지 못했습니다.");
}
export async function prepareExchangeShippingPayment(exchangeRequestId: number) {
  return unwrap(await apiFetch<ApiResponse<ExchangeShippingPayment>>(`/api/exchanges/${exchangeRequestId}/shipping-payment/prepare`, { method: "POST" }), "교환 배송비 결제를 준비하지 못했습니다.");
}
export async function confirmExchangeShippingPayment(exchangeRequestId: number, request: PaymentConfirmRequest) {
  return unwrap(await apiFetch<ApiResponse<ExchangeShippingPayment>>(`/api/exchanges/${exchangeRequestId}/shipping-payment/confirm`, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(request),
  }), "교환 배송비 결제를 확인하지 못했습니다.");
}
