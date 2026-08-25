import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type { ExchangeRequest, ExchangeRequestStatus, ExchangeResponsibility, SellerExchangeInspectRequest, SellerExchangeRequestPage } from "@/types/exchange";

const JSON_HEADERS = { "Content-Type": "application/json" };
function requireData<T>(response: ApiResponse<T>, message: string): T {
  if (!response.success || response.data == null) throw new Error(response.message || message);
  return response.data;
}
export async function getSellerExchangeRequests(params: { status?: ExchangeRequestStatus; page?: number; size?: number } = {}) {
  const query = new URLSearchParams({ page: String(params.page ?? 0), size: String(params.size ?? 20) });
  if (params.status) query.set("status", params.status);
  return requireData(await apiFetch<ApiResponse<SellerExchangeRequestPage>>(`/api/seller/orders/exchanges?${query}`), "교환 요청 목록을 확인할 수 없습니다.");
}
export async function getSellerExchangeRequest(id: number) {
  return requireData(await apiFetch<ApiResponse<ExchangeRequest>>(`/api/seller/orders/exchanges/${id}`), "교환 요청을 확인할 수 없습니다.");
}
async function patch(id: number, action: string, body?: unknown) {
  return requireData(await apiFetch<ApiResponse<ExchangeRequest>>(`/api/seller/orders/exchanges/${id}/${action}`, { method: "PATCH", ...(body === undefined ? {} : { headers: JSON_HEADERS, body: JSON.stringify(body) }) }), "교환 요청 처리 결과를 확인할 수 없습니다.");
}
export const approveSellerExchangeRequest = (id: number, responsibility: ExchangeResponsibility | null) => patch(id, "approve", { responsibility });
export const rejectSellerExchangeRequest = (id: number, reason: string) => patch(id, "reject", { reason });
export const collectSellerExchange = (id: number, shippingCompany: string, trackingNumber: string) => patch(id, "collect", { shippingCompany, trackingNumber });
export const receiveSellerExchange = (id: number) => patch(id, "receive");
export const inspectSellerExchange = (id: number, request: SellerExchangeInspectRequest) => patch(id, "inspect", request);
export const reshipSellerExchange = (id: number, shippingCompany: string, trackingNumber: string) => patch(id, "reship", { shippingCompany, trackingNumber });
export const deliverSellerExchange = (id: number) => patch(id, "deliver");
