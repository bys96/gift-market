import Image from "next/image";
import Link from "next/link";

import { BUYER_DELIVERY_STATUS_LABELS } from "@/lib/order-status";
import type { OrderSummary } from "@/types/order";
import { resolveImageUrl } from "@/utils/image-url";

interface OrderHistoryCardProps {
  order: OrderSummary;
}

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
  if (order.items.length === 0) return null;

  return (
    <article className="order-history-card">
      <div className="order-history-header">
        <div>
          {order.orderedAt ? (
            <time className="order-history-date" dateTime={order.orderedAt}>
              {formatDateTime(order.orderedAt)}
            </time>
          ) : (
            <span className="order-history-date">결제 준비</span>
          )}

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

      {order.items.map((item, index) => {
        const imageUrl = resolveImageUrl(item.representativeImageKey);
        return <div className="order-history-body" key={item.id}>
        <Link href={`/products/${item.productId}`} className="order-history-image-wrapper" aria-label={`${item.productName} 상품 상세`}>
          {imageUrl ? (
            <Image
              src={imageUrl}
              alt={item.productName}
              fill
              sizes="112px"
              className="order-history-image"
            />
          ) : (
            <div className="order-history-image-empty">이미지 없음</div>
          )}
        </Link>

        <div className="order-history-product-info">
          <span
            className={[
              "order-history-status",
              `order-history-status-${order.deliveryStatus.toLowerCase()}`,
            ].join(" ")}
          >
            {BUYER_DELIVERY_STATUS_LABELS[order.deliveryStatus]}
          </span>

          <h2 className="order-history-product-name">
            <Link href={`/products/${item.productId}`}>{item.productName}</Link>
          </h2>

          <p className="order-history-product-option">
            {item.optionSnapshot
              ? `${item.optionSnapshot} · `
              : ""}
            수량 {item.quantity}개
          </p>

          {index === 0 && <strong className="order-history-price">
            {formatPrice(order.totalAmount)}
          </strong>}
        </div>
      </div>;
      })}
    </article>
  );
}
