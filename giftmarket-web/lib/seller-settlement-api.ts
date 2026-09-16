import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type {
  SellerSettlementDetail,
  SellerSettlementPage,
  SellerSettlementStatus,
  SellerSettlementSummary,
} from "@/types/seller-settlement";

function requireData<T>(response: ApiResponse<T>, message: string): T {
  if (!response.data) throw new Error(message);
  return response.data;
}

export async function getSellerSettlements({
  status,
  page = 0,
  size = 20,
}: {
  status?: SellerSettlementStatus;
  page?: number;
  size?: number;
} = {}): Promise<SellerSettlementPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set("status", status);
  const response = await apiFetch<ApiResponse<SellerSettlementPage>>(
    `/api/seller/settlements?${params.toString()}`,
  );
  return requireData(response, "정산 목록을 확인할 수 없습니다.");
}

export async function getSellerSettlement(id: number): Promise<SellerSettlementDetail> {
  const response = await apiFetch<ApiResponse<SellerSettlementDetail>>(
    `/api/seller/settlements/${id}`,
  );
  return requireData(response, "정산 상세를 확인할 수 없습니다.");
}

export async function getSellerSettlementSummary(): Promise<SellerSettlementSummary> {
  const response = await apiFetch<ApiResponse<SellerSettlementSummary>>(
    "/api/seller/settlements/summary",
  );
  return requireData(response, "미정산 금액을 확인할 수 없습니다.");
}
