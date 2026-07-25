"use client";

import Link from "next/link";
import { notFound, useParams, useRouter } from "next/navigation";
import { useEffect } from "react";
import OrderDetailInfo from "@/components/order/OrderDetailInfo";
import OrderDetailProductList from "@/components/order/OrderDetailProductList";
import OrderDetailSummary from "@/components/order/OrderDetailSummary";
import { useAuthStore } from "@/stores/auth-store";
import type { OrderDetail, OrderStatus } from "@/types/order";

const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PAYMENT_COMPLETED: "결제 완료",
  PREPARING: "상품 준비 중",
  SHIPPING: "배송 중",
  DELIVERED: "배송 완료",
  CANCELED: "주문 취소",
};

const MOCK_ORDERS: OrderDetail[] = [
  {
    id: 1,
    orderNumber: "202607250001",
    orderedAt: "2026-07-25T11:30:00+09:00",
    status: "DELIVERED",
    items: [
      {
        id: 1,
        productId: 1,
        productName: "스타벅스 카페 아메리카노 T",
        productImageUrl: "/images/products/product-1.jpg",
        quantity: 1,
        price: 4500,
      },
      {
        id: 2,
        productId: 2,
        productName: "프리미엄 디저트 세트",
        productImageUrl: "/images/products/product-2.jpg",
        quantity: 1,
        price: 29000,
      },
    ],
    customer: {
      name: "홍길동",
      email: "hong@example.com",
      phoneNumber: "010-1234-5678",
    },
    recipient: {
      name: "김선물",
      phoneNumber: "010-9876-5432",
      zipCode: "06236",
      address: "서울특별시 강남구 테헤란로 123",
      addressDetail: "101동 1203호",
      deliveryMessage: "문 앞에 놓아주세요.",
    },
    payment: {
      productAmount: 33500,
      deliveryFee: 3000,
      discountAmount: 0,
      totalAmount: 36500,
      paymentMethod: "카카오페이",
      paidAt: "2026-07-25T11:31:00+09:00",
    },
  },
  {
    id: 2,
    orderNumber: "202607200003",
    orderedAt: "2026-07-20T14:20:00+09:00",
    status: "SHIPPING",
    items: [
      {
        id: 3,
        productId: 3,
        productName: "센트럴파크 향수 기프트 세트",
        productImageUrl: "/images/products/product-3.jpg",
        quantity: 1,
        price: 24900,
      },
    ],
    customer: {
      name: "홍길동",
      email: "hong@example.com",
      phoneNumber: "010-1234-5678",
    },
    recipient: {
      name: "이마음",
      phoneNumber: "010-1111-2222",
      zipCode: "04524",
      address: "서울특별시 중구 세종대로 110",
      addressDetail: "5층",
      deliveryMessage: "도착 전에 연락해주세요.",
    },
    payment: {
      productAmount: 24900,
      deliveryFee: 3000,
      discountAmount: 0,
      totalAmount: 27900,
      paymentMethod: "신용카드",
      paidAt: "2026-07-20T14:21:00+09:00",
    },
  },
];

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

export default function MyOrderDetailPage() {
  const params = useParams<{ orderId: string }>();
  const router = useRouter();

  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  useEffect(() => {
    if (!isAuthenticated || !user) {
      router.replace("/login");
    }
  }, [isAuthenticated, user, router]);

  if (!user) {
    return null;
  }

  const orderId = Number(params.orderId);

  if (!Number.isInteger(orderId) || orderId < 1) {
    notFound();
  }

  const order = MOCK_ORDERS.find((mockOrder) => mockOrder.id === orderId);

  if (!order) {
    notFound();
  }

  const canCancel =
    order.status === "PAYMENT_COMPLETED" || order.status === "PREPARING";

  return (
    <div className="order-detail-page">
      <div className="order-detail-header">
        <div>
          <p className="order-detail-eyebrow">주문번호 {order.orderNumber}</p>

          <h1 className="order-detail-title">주문 상세</h1>

          <p className="order-detail-ordered-at">
            {formatDateTime(order.orderedAt)}
          </p>
        </div>

        <Link href="/my/orders" className="order-detail-back-link">
          주문 내역
        </Link>
      </div>

      <section className="order-detail-status-section">
        <div>
          <p className="order-detail-status-label">현재 주문 상태</p>

          <strong className="order-detail-status">
            {ORDER_STATUS_LABELS[order.status]}
          </strong>
        </div>

        {canCancel && (
          <button
            type="button"
            className="order-detail-cancel-button"
            onClick={() => {
              window.alert("주문 취소 API 연결 후 동작할 예정입니다.");
            }}
          >
            주문 취소
          </button>
        )}
      </section>

      <OrderDetailProductList items={order.items} />

      <OrderDetailInfo customer={order.customer} recipient={order.recipient} />

      <OrderDetailSummary payment={order.payment} />
    </div>
  );
}
