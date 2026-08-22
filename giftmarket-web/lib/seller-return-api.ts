import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type {
  ReturnRequest,
  ReturnRequestStatus,
  ReturnResponsibility,
  SellerReturnInspectRequest,
  SellerReturnRequestPage,
} from "@/types/return";

const JSON_HEADERS = { "Content-Type": "application/json" };

function requireData<T>(response: ApiResponse<T>, message: string): T {
  if (!response.success || !response.data) throw new Error(response.message || message);
  return response.data;
}

export async function getSellerReturnRequests(params: {
  status?: ReturnRequestStatus;
  page?: number;
  size?: number;
} = {}): Promise<SellerReturnRequestPage> {
  const query = new URLSearchParams({ page: String(params.page ?? 0), size: String(params.size ?? 20) });
  if (params.status) query.set("status", params.status);
  const response = await apiFetch<ApiResponse<SellerReturnRequestPage>>(`/api/seller/orders/returns?${query.toString()}`);
  return requireData(response, "반품 요청 목록을 확인할 수 없습니다.");
}

export async function getSellerReturnRequest(returnRequestId: number): Promise<ReturnRequest> {
  const response = await apiFetch<ApiResponse<ReturnRequest>>(`/api/seller/orders/returns/${returnRequestId}`);
  return requireData(response, "반품 요청을 확인할 수 없습니다.");
}

async function patchReturn(path: string, body?: unknown): Promise<ReturnRequest> {
  const response = await apiFetch<ApiResponse<ReturnRequest>>(path, {
    method: "PATCH",
    ...(body === undefined ? {} : { headers: JSON_HEADERS, body: JSON.stringify(body) }),
  });
  return requireData(response, "반품 요청 처리 결과를 확인할 수 없습니다.");
}

export function approveSellerReturnRequest(id: number, responsibility: ReturnResponsibility | null) {
  return patchReturn(`/api/seller/orders/returns/${id}/approve`, { responsibility });
}

export function rejectSellerReturnRequest(id: number, reason: string) {
  return patchReturn(`/api/seller/orders/returns/${id}/reject`, { reason });
}

export function startSellerReturnCollection(id: number, shippingCompany: string, trackingNumber: string) {
  return patchReturn(`/api/seller/orders/returns/${id}/collect`, { shippingCompany, trackingNumber });
}

export function receiveSellerReturn(id: number) {
  return patchReturn(`/api/seller/orders/returns/${id}/receive`);
}

export function inspectSellerReturn(id: number, request: SellerReturnInspectRequest) {
  return patchReturn(`/api/seller/orders/returns/${id}/inspect`, request);
}
