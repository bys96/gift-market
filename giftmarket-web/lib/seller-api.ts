import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type {
  Seller,
  SellerApplication,
  SellerApplicationCreateRequest,
  SellerApplicationRejectRequest,
} from "@/types/seller";

export async function getPendingSellerApplications(): Promise<
  SellerApplication[]
> {
  const response = await apiFetch<ApiResponse<SellerApplication[]>>(
    "/api/admin/seller-applications/pending",
  );

  return response.data ?? [];
}

export async function approveSellerApplication(
  applicationId: number,
): Promise<SellerApplication> {
  const response = await apiFetch<ApiResponse<SellerApplication>>(
    `/api/admin/seller-applications/${applicationId}/approve`,
    {
      method: "PATCH",
    },
  );

  if (!response.data) {
    throw new Error("판매자 승인 결과를 확인할 수 없습니다.");
  }

  return response.data;
}

export async function rejectSellerApplication(
  applicationId: number,
  request: SellerApplicationRejectRequest,
): Promise<SellerApplication> {
  const response = await apiFetch<ApiResponse<SellerApplication>>(
    `/api/admin/seller-applications/${applicationId}/reject`,
    {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!response.data) {
    throw new Error("판매자 거절 결과를 확인할 수 없습니다.");
  }

  return response.data;
}

export async function createSellerApplication(
  request: SellerApplicationCreateRequest,
): Promise<SellerApplication> {
  const response = await apiFetch<ApiResponse<SellerApplication>>(
    "/api/seller-applications",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!response.data) {
    throw new Error("판매자 신청 결과를 확인할 수 없습니다.");
  }

  return response.data;
}

export async function getMyLatestSellerApplication(): Promise<SellerApplication | null> {
  const response = await apiFetch<ApiResponse<SellerApplication | null>>(
    "/api/seller-applications/me/latest",
  );

  return response.data;
}

export async function getMySeller(): Promise<Seller> {
  const response = await apiFetch<ApiResponse<Seller>>("/api/sellers/me");

  if (!response.data) {
    throw new Error("판매자 정보를 확인할 수 없습니다.");
  }

  return response.data;
}
