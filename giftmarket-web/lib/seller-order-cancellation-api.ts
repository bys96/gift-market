import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type {
  SellerOrderCancellation,
  SellerOrderCancellationPage,
  SellerOrderCancellationStatus,
} from "@/types/seller-order-cancellation";

const JSON_HEADERS = { "Content-Type": "application/json" };

interface GetSellerOrderCancellationsParams {
  status?: SellerOrderCancellationStatus;
  page?: number;
  size?: number;
}

function requireData<T>(response: ApiResponse<T>, message: string): T {
  if (!response.data) throw new Error(message);
  return response.data;
}

export async function getSellerOrderCancellations({
  status,
  page = 0,
  size = 20,
}: GetSellerOrderCancellationsParams = {}): Promise<SellerOrderCancellationPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set("status", status);

  const response = await apiFetch<ApiResponse<SellerOrderCancellationPage>>(
    `/api/seller/orders/cancellations?${params.toString()}`,
  );
  return requireData(response, "취소 요청 목록을 확인할 수 없습니다.");
}

export async function getSellerOrderCancellation(
  cancellationId: number,
): Promise<SellerOrderCancellation> {
  const response = await apiFetch<ApiResponse<SellerOrderCancellation>>(
    `/api/seller/orders/cancellations/${cancellationId}`,
  );
  return requireData(response, "취소 요청을 확인할 수 없습니다.");
}

export async function approveSellerOrderCancellation(
  cancellationId: number,
): Promise<SellerOrderCancellation> {
  const response = await apiFetch<ApiResponse<SellerOrderCancellation>>(
    `/api/seller/orders/cancellations/${cancellationId}/approve`,
    { method: "PATCH" },
  );
  return requireData(response, "취소 승인 결과를 확인할 수 없습니다.");
}

export async function rejectSellerOrderCancellation(
  cancellationId: number,
  reason: string,
): Promise<SellerOrderCancellation> {
  const response = await apiFetch<ApiResponse<SellerOrderCancellation>>(
    `/api/seller/orders/cancellations/${cancellationId}/reject`,
    {
      method: "PATCH",
      headers: JSON_HEADERS,
      body: JSON.stringify({ reason }),
    },
  );
  return requireData(response, "취소 거절 결과를 확인할 수 없습니다.");
}
