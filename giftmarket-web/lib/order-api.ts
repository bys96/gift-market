import { apiFetch } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type {
  OrderCreateRequest,
  OrderCreateResponse,
  DirectOrderCreateRequest,
  OrderDetail,
  OrderSummary,
  OrderCancelResponse,
  OrderCancellation,
  OrderCancellationCreateRequest,
  PurchaseConfirmation,
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
    throw new Error(result.message || "결제 준비를 완료하지 못했습니다.");
  }

  return result.data;
}

export async function confirmPurchase(
  orderId: number,
  orderItemId: number,
): Promise<PurchaseConfirmation> {
  const result = await apiFetch<ApiResponse<PurchaseConfirmation>>(
    `/api/orders/${orderId}/items/${orderItemId}/confirm`,
    { method: "POST" },
  );
  if (!result.success || !result.data) {
    throw new Error(result.message || "구매확정을 처리하지 못했습니다.");
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
    throw new Error(result.message || "바로구매 결제 준비를 완료하지 못했습니다.");
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

export async function cancelOrder(
  orderId: number,
  request: { clientCancelRequestKey: string; cancelReason: string },
): Promise<OrderCancelResponse> {
  const result = await apiFetch<ApiResponse<OrderCancelResponse>>(
    `/api/orders/${orderId}/cancel`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    },
  );

  if (!result.success || !result.data) {
    throw new Error(result.message || "주문을 취소하지 못했습니다.");
  }

  return result.data;
}

export async function getOrderCancellations(orderId: number): Promise<OrderCancellation[]> {
  const result = await apiFetch<ApiResponse<OrderCancellation[]>>(
    `/api/orders/${orderId}/cancellations`, { method: "GET" },
  );
  if (!result.success || !result.data) throw new Error(result.message || "취소 내역을 불러오지 못했습니다.");
  return result.data;
}

export async function createOrderCancellation(
  orderId: number,
  request: OrderCancellationCreateRequest,
): Promise<OrderCancellation> {
  const result = await apiFetch<ApiResponse<OrderCancellation>>(
    `/api/orders/${orderId}/cancellations`,
    { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(request) },
  );
  if (!result.success || !result.data) throw new Error(result.message || "취소 요청을 처리하지 못했습니다.");
  return result.data;
}
