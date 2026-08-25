import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type { SellerDashboard } from "@/types/seller-dashboard";

export async function getSellerDashboard(): Promise<SellerDashboard> {
  const response = await apiFetch<ApiResponse<SellerDashboard>>(
    "/api/seller/dashboard",
  );
  if (!response.data) {
    throw new Error("대시보드 정보를 불러오지 못했습니다.");
  }
  return response.data;
}
