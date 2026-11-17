import type { OrderHistoryItem } from "@/types/order";
import type { ReturnRequest } from "@/types/return";

type StatusItem = Pick<OrderHistoryItem, "id" | "quantity" | "canceledQuantity">;

// Delivery summary and seller-order enums use different names for the same phase.
const STATUS_DISPLAY: Record<string, { label: string; tone: string }> = {
  ORDERED: { label: "주문 완료", tone: "neutral" },
  PAYMENT_PENDING: { label: "결제 대기", tone: "neutral" },
  PENDING_PAYMENT: { label: "결제 대기", tone: "neutral" },
  PAYMENT_FAILED: { label: "결제 실패", tone: "closed" },
  PAYMENT_EXPIRED: { label: "결제 만료", tone: "neutral" },
  PAID: { label: "결제 완료", tone: "paid" },
  PREPARING: { label: "상품준비중", tone: "pending" },
  SHIPPING: { label: "배송중", tone: "shipping" },
  SHIPPED: { label: "배송중", tone: "shipping" },
  DELIVERED: { label: "배송완료", tone: "delivered" },
  CANCELLED: { label: "취소완료", tone: "closed" },
};

// Presentation only. Completed request quantities are per-request, whereas
// returnedQuantity in the return API is cumulative for the original order item.
export function getBuyerOrderStatus(
  status: string,
  label: string,
  items: StatusItem[],
  returns: ReturnRequest[],
  loading = false,
  error = false,
) {
  if (status === "CANCELLED") return { label: "취소완료", tone: "closed" };
  if (loading) return { label: "상태 확인 중", tone: "neutral" };
  if (error) return { label: "상태 확인 필요", tone: "neutral" };

  const ids = new Set(items.map((item) => item.id));
  const relevant = returns.filter((request) =>
    request.items.some((item) => ids.has(item.orderItemId) && item.quantity > 0),
  );
  const progressing = new Set(["APPROVED", "COLLECTING", "RECEIVED", "INSPECTED", "REFUNDING"]);
  if (relevant.some((request) => progressing.has(request.status))) {
    return { label: "반품진행중", tone: "pending" };
  }
  if (relevant.some((request) => request.status === "REQUESTED")) {
    return { label: "반품요청", tone: "pending" };
  }

  const completed = relevant.filter((request) => request.status === "COMPLETED");
  const completedQuantity = (id: number) => completed
    .flatMap((request) => request.items)
    .filter((item) => item.orderItemId === id)
    .reduce((sum, item) => sum + item.quantity, 0);
  if (items.some((item) => completedQuantity(item.id) > 0)) {
    const full = items.every((item) =>
      completedQuantity(item.id) === item.quantity - item.canceledQuantity,
    );
    return { label: full ? "반품완료" : "부분반품완료", tone: "closed" };
  }
  return STATUS_DISPLAY[status] ?? { label, tone: "neutral" };
}
