"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";

import { BUYER_DELIVERY_STATUS_LABELS } from "@/lib/order-status";
import type { OrderSummary } from "@/types/order";
import { resolveImageUrl } from "@/utils/image-url";
import BuyerOrderStatusBadge from "@/components/order/BuyerOrderStatusBadge";
import { getOrderReturnRequests } from "@/lib/return-api";
import type { ReturnRequest } from "@/types/return";

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
  const [returnState, setReturnState] = useState<{
    orderId: number;
    requests: ReturnRequest[];
    error: boolean;
  } | null>(null);
  const needsReturns = ["PAID", "PREPARING", "SHIPPING", "DELIVERED"].includes(order.deliveryStatus);

  useEffect(() => {
    if (!needsReturns) return;
    let cancelled = false;
    void getOrderReturnRequests(order.id).then((requests) => {
      if (!cancelled) setReturnState({ orderId: order.id, requests, error: false });
    }).catch(() => {
      if (!cancelled) setReturnState({ orderId: order.id, requests: [], error: true });
    });
    return () => { cancelled = true; };
  }, [order.id, order.deliveryStatus, needsReturns]);

  const currentReturns = returnState?.orderId === order.id ? returnState : null;
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
          <BuyerOrderStatusBadge
            className={`order-history-status order-history-status-${order.deliveryStatus.toLowerCase()}`}
            status={order.deliveryStatus}
            label={BUYER_DELIVERY_STATUS_LABELS[order.deliveryStatus]}
            items={order.items}
            returns={currentReturns?.requests ?? []}
            loading={needsReturns && !currentReturns}
            error={needsReturns && currentReturns?.error}
          />
        </div>

        <Link
          href={`/my/orders/${order.id}`}
          className="order-history-detail-link"
        >
          주문 상세
          <span aria-hidden="true">›</span>
        </Link>
      </div>

      {order.items.map((item) => {
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
          <BuyerOrderStatusBadge
            className={`order-history-status order-history-status-${order.deliveryStatus.toLowerCase()}`}
            status={order.deliveryStatus}
            label={BUYER_DELIVERY_STATUS_LABELS[order.deliveryStatus]}
            items={[item]}
            returns={currentReturns?.requests ?? []}
            loading={needsReturns && !currentReturns}
            error={needsReturns && currentReturns?.error}
          />

          <h2 className="order-history-product-name">
            <Link href={`/products/${item.productId}`}>{item.productName}</Link>
          </h2>

          <p className="order-history-product-option">
            {item.optionSnapshot
              ? `${item.optionSnapshot} · `
              : ""}
            수량 {item.quantity}개
          </p>
        </div>
      </div>;
      })}

      {order.orderedAt && (
        <footer className="order-history-total">
          <span>총 결제금액</span>
          <strong>{formatPrice(order.totalAmount)}</strong>
        </footer>
      )}
    </article>
  );
}
