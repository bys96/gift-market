import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type { ReturnRequest, ReturnRequestCreateRequest } from "@/types/return";

export async function createReturnRequest(
  orderId: number,
  sellerOrderId: number,
  request: ReturnRequestCreateRequest,
): Promise<ReturnRequest> {
  const result = await apiFetch<ApiResponse<ReturnRequest>>(
    `/api/orders/${orderId}/seller-orders/${sellerOrderId}/returns`,
    { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(request) },
  );
  if (!result.success || !result.data) throw new Error(result.message || "반품 요청을 접수하지 못했습니다.");
  return result.data;
}

export async function getOrderReturnRequests(orderId: number): Promise<ReturnRequest[]> {
  const result = await apiFetch<ApiResponse<ReturnRequest[]>>(`/api/orders/${orderId}/returns`, { method: "GET" });
  if (!result.success || !result.data) throw new Error(result.message || "반품 내역을 불러오지 못했습니다.");
  return result.data;
}

export async function getReturnRequest(returnRequestId: number): Promise<ReturnRequest> {
  const result = await apiFetch<ApiResponse<ReturnRequest>>(`/api/returns/${returnRequestId}`, { method: "GET" });
  if (!result.success || !result.data) throw new Error(result.message || "반품 정보를 불러오지 못했습니다.");
  return result.data;
}
