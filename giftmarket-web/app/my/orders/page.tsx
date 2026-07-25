"use client";

import { useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import OrderHistoryCard from "@/components/order/OrderHistoryCard";
import { useAuthStore } from "@/stores/auth-store";
import type { OrderSummary } from "@/types/order";

const MOCK_ORDERS: OrderSummary[] = [
  {
    id: 1,
    orderNumber: "202607250001",
    orderedAt: "2026-07-25T11:30:00+09:00",
    status: "DELIVERED",
    totalPrice: 36500,
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
        price: 32000,
      },
    ],
  },
  {
    id: 2,
    orderNumber: "202607200003",
    orderedAt: "2026-07-20T14:20:00+09:00",
    status: "SHIPPING",
    totalPrice: 27900,
    items: [
      {
        id: 3,
        productId: 3,
        productName: "센트럴파크 향수 기프트 세트",
        productImageUrl: "/images/products/product-3.jpg",
        quantity: 1,
        price: 27900,
      },
    ],
  },
];

export default function MyOrdersPage() {
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

  return (
    <div className="my-orders-page">
      <div className="my-orders-header">
        <div>
          <p className="my-orders-eyebrow">나의 쇼핑</p>
          <h1 className="my-orders-title">주문 내역</h1>
        </div>

        <Link href="/my" className="my-orders-back-link">
          마이페이지
        </Link>
      </div>

      {MOCK_ORDERS.length > 0 ? (
        <div className="order-history-list">
          {MOCK_ORDERS.map((order) => (
            <OrderHistoryCard key={order.id} order={order} />
          ))}
        </div>
      ) : (
        <section className="order-history-empty">
          <div className="order-history-empty-icon" aria-hidden="true">
            📦
          </div>

          <h2>아직 주문 내역이 없습니다.</h2>

          <p>마음을 전할 상품을 찾아보세요.</p>

          <Link href="/products" className="order-history-empty-link">
            상품 보러 가기
          </Link>
        </section>
      )}
    </div>
  );
}
