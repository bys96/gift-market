"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import OrderHistoryCard from "@/components/order/OrderHistoryCard";
import { getMyOrders } from "@/lib/order-api";
import { useAuthStore } from "@/stores/auth-store";
import type { OrderSummary } from "@/types/order";

export default function MyOrdersPage() {
  const router = useRouter();

  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace("/login");
    }
  }, [initialized, isAuthenticated, user, router]);

  useEffect(() => {
    if (!initialized || !isAuthenticated || !user) {
      return;
    }

    let cancelled = false;

    const loadOrders = async () => {
      try {
        setIsLoading(true);
        setErrorMessage("");

        const response = await getMyOrders();

        if (cancelled) {
          return;
        }

        setOrders(response);
      } catch (error) {
        if (cancelled) {
          return;
        }

        setErrorMessage(
          error instanceof Error
            ? error.message
            : "주문 내역을 불러오지 못했습니다.",
        );
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    };

    void loadOrders();

    return () => {
      cancelled = true;
    };
  }, [initialized, isAuthenticated, user]);

  if (!initialized) {
    return null;
  }

  if (!isAuthenticated || !user) {
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

      {isLoading ? (
        <section className="order-history-empty">
          <h2>주문 내역을 불러오고 있습니다.</h2>

          <p>잠시 후 주문 정보를 확인할 수 있습니다.</p>
        </section>
      ) : errorMessage ? (
        <section className="order-history-empty">
          <h2>주문 내역을 불러오지 못했습니다.</h2>

          <p>{errorMessage}</p>

          <button
            type="button"
            className="order-history-empty-link"
            onClick={() => window.location.reload()}
          >
            다시 시도
          </button>
        </section>
      ) : orders.length > 0 ? (
        <div className="order-history-list">
          {orders.map((order) => (
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
