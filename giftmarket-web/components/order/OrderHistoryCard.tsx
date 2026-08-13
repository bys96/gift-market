import Image from "next/image";
import Link from "next/link";

import type { OrderStatus, OrderSummary } from "@/types/order";
import { resolveImageUrl } from "@/utils/image-url";

interface OrderHistoryCardProps {
  order: OrderSummary;
}

const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  ORDERED: "주문 완료",
  CANCELLED: "주문 취소",
};

function formatPrice(price: number) {
  return `${price.toLocaleString("ko-KR")}원`;
}

function formatDateTime(date: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(date));
}
export default function OrderHistoryCard({ order }: OrderHistoryCardProps) {
  const representativeItem = order.items[0];
  const additionalItemCount = order.items.length - 1;

  if (!representativeItem) {
    return null;
  }

  const imageUrl = resolveImageUrl(representativeItem.representativeImageKey);

  return (
    <article className="order-history-card">
      <div className="order-history-header">
        <div>
          <time className="order-history-date" dateTime={order.orderedAt}>
            {formatDateTime(order.orderedAt)}
          </time>

          <p className="order-history-number">주문번호 {order.orderNumber}</p>
        </div>

        <Link
          href={`/my/orders/${order.id}`}
          className="order-history-detail-link"
        >
          주문 상세
          <span aria-hidden="true">›</span>
        </Link>
      </div>

      <div className="order-history-body">
        <div className="order-history-image-wrapper">
          {imageUrl ? (
            <Image
              src={imageUrl}
              alt={representativeItem.productName}
              fill
              sizes="112px"
              className="order-history-image"
            />
          ) : (
            <div className="order-history-image-empty">이미지 없음</div>
          )}
        </div>

        <div className="order-history-product-info">
          <span
            className={[
              "order-history-status",
              `order-history-status-${order.status.toLowerCase()}`,
            ].join(" ")}
          >
            {ORDER_STATUS_LABELS[order.status]}
          </span>

          <h2 className="order-history-product-name">
            {representativeItem.productName}

            {additionalItemCount > 0 && (
              <span> 외 {additionalItemCount}건</span>
            )}
          </h2>

          <p className="order-history-product-option">
            {representativeItem.optionSnapshot
              ? `${representativeItem.optionSnapshot} · `
              : ""}
            수량 {representativeItem.quantity}개
          </p>

          <strong className="order-history-price">
            {formatPrice(order.totalAmount)}
          </strong>
        </div>
      </div>
    </article>
  );
}
