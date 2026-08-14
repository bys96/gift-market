import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type {
  OrderCreateRequest,
  OrderCreateResponse,
  DirectOrderCreateRequest,
  OrderDetail,
  OrderSummary,
} from "@/types/order";

export async function createOrder(
  request: OrderCreateRequest,
): Promise<OrderCreateResponse> {
  const result = await apiFetch<ApiResponse<OrderCreateResponse>>(
    "/api/orders",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!result.success || !result.data) {
    throw new Error(result.message || "주문을 생성하지 못했습니다.");
  }

  return result.data;
}

export async function createDirectOrder(
  request: DirectOrderCreateRequest,
): Promise<OrderCreateResponse> {
  const result = await apiFetch<ApiResponse<OrderCreateResponse>>(
    "/api/orders/direct",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    },
  );

  if (!result.success || !result.data) {
    throw new Error(result.message || "바로구매 주문을 생성하지 못했습니다.");
  }

  return result.data;
}

export async function getMyOrders(): Promise<OrderSummary[]> {
  const result = await apiFetch<ApiResponse<OrderSummary[]>>("/api/orders", {
    method: "GET",
  });

  if (!result.success || !result.data) {
    throw new Error(result.message || "주문 내역을 불러오지 못했습니다.");
  }

  return result.data;
}

export async function getMyOrder(orderId: number): Promise<OrderDetail> {
  const result = await apiFetch<ApiResponse<OrderDetail>>(
    `/api/orders/${orderId}`,
    {
      method: "GET",
    },
  );

  if (!result.success || !result.data) {
    throw new Error(result.message || "주문 정보를 불러오지 못했습니다.");
  }

  return result.data;
}

export async function cancelOrder(orderId: number): Promise<OrderDetail> {
  const result = await apiFetch<ApiResponse<OrderDetail>>(
    `/api/orders/${orderId}/cancel`,
    {
      method: "PATCH",
    },
  );

  if (!result.success || !result.data) {
    throw new Error(result.message || "주문을 취소하지 못했습니다.");
  }

  return result.data;
}
