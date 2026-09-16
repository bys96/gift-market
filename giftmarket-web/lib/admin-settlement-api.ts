import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type {
  AdminSettlement,
  AdminSettlementDetail,
  AdminSettlementGenerateRequest,
  AdminSettlementGenerateResult,
  AdminSettlementPage,
  AdminSettlementSearchParams,
} from "@/types/admin-settlement";

const JSON_HEADERS = { "Content-Type": "application/json" };

function requireData<T>(response: ApiResponse<T>, message: string): T {
  if (!response.data) throw new Error(message);
  return response.data;
}

export async function getAdminSettlements(search: AdminSettlementSearchParams): Promise<AdminSettlementPage> {
  const params = new URLSearchParams({
    page: String(search.page ?? 0),
    size: String(search.size ?? 20),
  });
  if (search.sellerId) params.set("sellerId", String(search.sellerId));
  if (search.status) params.set("status", search.status);
  if (search.periodStart) params.set("periodStart", search.periodStart);
  if (search.periodEnd) params.set("periodEnd", search.periodEnd);
  const response = await apiFetch<ApiResponse<AdminSettlementPage>>(
    `/api/admin/settlements?${params.toString()}`,
  );
  return requireData(response, "정산 목록을 불러오지 못했습니다.");
}

export async function getAdminSettlement(id: number): Promise<AdminSettlementDetail> {
  const response = await apiFetch<ApiResponse<AdminSettlementDetail>>(
    `/api/admin/settlements/${id}`,
  );
  return requireData(response, "정산 상세를 불러오지 못했습니다.");
}

export async function generateAdminSettlement(
  request: AdminSettlementGenerateRequest,
): Promise<AdminSettlementGenerateResult> {
  const response = await apiFetch<ApiResponse<AdminSettlementGenerateResult>>(
    "/api/admin/settlements/generate",
    { method: "POST", headers: JSON_HEADERS, body: JSON.stringify(request) },
  );
  return requireData(response, "정산 생성 결과를 확인하지 못했습니다.");
}

export async function holdAdminSettlement(id: number, reason: string): Promise<AdminSettlement> {
  const response = await apiFetch<ApiResponse<AdminSettlement>>(
    `/api/admin/settlements/${id}/hold`,
    { method: "POST", headers: JSON_HEADERS, body: JSON.stringify({ reason }) },
  );
  return requireData(response, "정산 보류 결과를 확인하지 못했습니다.");
}

export async function releaseAdminSettlement(id: number): Promise<AdminSettlement> {
  const response = await apiFetch<ApiResponse<AdminSettlement>>(
    `/api/admin/settlements/${id}/release`, { method: "POST" },
  );
  return requireData(response, "정산 보류 해제 결과를 확인하지 못했습니다.");
}

export async function confirmAdminSettlement(id: number): Promise<AdminSettlement> {
  const response = await apiFetch<ApiResponse<AdminSettlement>>(
    `/api/admin/settlements/${id}/confirm`, { method: "POST" },
  );
  return requireData(response, "정산 확정 결과를 확인하지 못했습니다.");
}
