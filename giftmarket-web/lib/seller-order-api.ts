import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type {
  SellerOrderDetail,
  SellerOrderPage,
  SellerOrderShipRequest,
  SellerOrderStatus,
} from "@/types/seller-order";

const JSON_HEADERS = { "Content-Type": "application/json" };

interface GetSellerOrdersParams {
  status?: Exclude<SellerOrderStatus, "PENDING_PAYMENT">;
  keyword?: string;
  page?: number;
  size?: number;
}

function requireData<T>(response: ApiResponse<T>, message: string): T {
  if (!response.data) {
    throw new Error(message);
  }
  return response.data;
}

export async function getSellerOrders({
  status,
  keyword,
  page = 0,
  size = 20,
}: GetSellerOrdersParams = {}): Promise<SellerOrderPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set("status", status);
  if (keyword?.trim()) params.set("keyword", keyword.trim());

  const response = await apiFetch<ApiResponse<SellerOrderPage>>(
    `/api/seller/orders?${params.toString()}`,
  );
  return requireData(response, "주문 목록을 확인할 수 없습니다.");
}

export async function getSellerOrder(
  sellerOrderId: number,
): Promise<SellerOrderDetail> {
  const response = await apiFetch<ApiResponse<SellerOrderDetail>>(
    `/api/seller/orders/${sellerOrderId}`,
  );
  return requireData(response, "주문 정보를 확인할 수 없습니다.");
}

async function patchSellerOrder(
  sellerOrderId: number,
  action: "prepare" | "deliver",
): Promise<SellerOrderDetail> {
  const response = await apiFetch<ApiResponse<SellerOrderDetail>>(
    `/api/seller/orders/${sellerOrderId}/${action}`,
    { method: "PATCH" },
  );
  return requireData(response, "주문 처리 결과를 확인할 수 없습니다.");
}

export function prepareSellerOrder(
  sellerOrderId: number,
): Promise<SellerOrderDetail> {
  return patchSellerOrder(sellerOrderId, "prepare");
}

export async function shipSellerOrder(
  sellerOrderId: number,
  request: SellerOrderShipRequest,
): Promise<SellerOrderDetail> {
  const response = await apiFetch<ApiResponse<SellerOrderDetail>>(
    `/api/seller/orders/${sellerOrderId}/ship`,
    {
      method: "PATCH",
      headers: JSON_HEADERS,
      body: JSON.stringify(request),
    },
  );
  return requireData(response, "배송 처리 결과를 확인할 수 없습니다.");
}

export function deliverSellerOrder(
  sellerOrderId: number,
): Promise<SellerOrderDetail> {
  return patchSellerOrder(sellerOrderId, "deliver");
}
