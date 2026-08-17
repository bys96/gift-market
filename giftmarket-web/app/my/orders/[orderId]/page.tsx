"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";

import OrderDetailInfo from "@/components/order/OrderDetailInfo";
import OrderDetailProductList from "@/components/order/OrderDetailProductList";
import OrderDetailSellerGroups from "@/components/order/OrderDetailSellerGroups";
import OrderDetailSummary from "@/components/order/OrderDetailSummary";
import { cancelOrder, getMyOrder } from "@/lib/order-api";
import { BUYER_DELIVERY_STATUS_LABELS } from "@/lib/order-status";
import { useAuthStore } from "@/stores/auth-store";
import type { OrderDetail } from "@/types/order";

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
  const params = useParams<{
    orderId: string;
  }>();

  const router = useRouter();

  const authInitialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const [order, setOrder] = useState<OrderDetail | null>(null);

  const [isCancelling, setIsCancelling] = useState(false);

  const [isLoading, setIsLoading] = useState(true);

  const [errorMessage, setErrorMessage] = useState("");

  const orderId = useMemo(() => Number(params.orderId), [params.orderId]);
  const isValidOrderId = Number.isInteger(orderId) && orderId > 0;

  useEffect(() => {
    if (!authInitialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace("/login");
    }
  }, [authInitialized, isAuthenticated, user, router]);

  useEffect(() => {
    if (!authInitialized || !isAuthenticated || !user) {
      return;
    }

    if (!isValidOrderId) {
      return;
    }

    let cancelled = false;

    const loadOrder = async () => {
      try {
        setIsLoading(true);
        setErrorMessage("");

        const response = await getMyOrder(orderId);

        if (cancelled) {
          return;
        }

        setOrder(response);
      } catch (error) {
        if (cancelled) {
          return;
        }

        setErrorMessage(
          error instanceof Error
            ? error.message
            : "주문 정보를 불러오지 못했습니다.",
        );
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    };

    void loadOrder();

    return () => {
      cancelled = true;
    };
  }, [authInitialized, isAuthenticated, user, orderId, isValidOrderId]);

  const handleCancelOrder = async () => {
    if (!order || !["ORDERED", "PAID"].includes(order.status) || isCancelling) {
      return;
    }

    const cancelReason = window.prompt("취소 사유를 입력해주세요.", "고객 요청");

    if (!cancelReason?.trim()) {
      return;
    }

    try {
      setIsCancelling(true);
      setErrorMessage("");

      await cancelOrder(order.id, {
        clientCancelRequestKey: crypto.randomUUID(),
        cancelReason: cancelReason.trim(),
      });

      router.replace("/my/orders");
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "주문을 취소하지 못했습니다.",
      );
    } finally {
      setIsCancelling(false);
    }
  };

  if (!authInitialized) {
    return null;
  }

  if (!isAuthenticated || !user) {
    return null;
  }

  if (!isValidOrderId) {
    return (
      <div className="order-detail-page">
        <section className="order-history-empty">
          <h2>주문 정보를 확인할 수 없습니다.</h2>

          <p>올바르지 않은 주문 번호입니다.</p>

          <Link href="/my/orders" className="order-history-empty-link">
            주문 내역으로
          </Link>
        </section>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="order-detail-page">
        <section className="order-history-empty">
          <h2>주문 정보를 불러오고 있습니다.</h2>
        </section>
      </div>
    );
  }

  if (errorMessage || !order) {
    return (
      <div className="order-detail-page">
        <section className="order-history-empty">
          <h2>주문 정보를 확인할 수 없습니다.</h2>

          <p>{errorMessage || "주문 정보를 찾을 수 없습니다."}</p>

          <Link href="/my/orders" className="order-history-empty-link">
            주문 내역으로
          </Link>
        </section>
      </div>
    );
  }

  const statusChangedAt =
    order.status === "CANCELLED" && order.cancelledAt
      ? order.cancelledAt
      : order.orderedAt;

  return (
    <div className="order-detail-page">
      <div className="order-detail-header">
        <div>
          <p className="order-detail-eyebrow">주문번호 {order.orderNumber}</p>

          <h1 className="order-detail-title">주문 상세</h1>

          {order.orderedAt && (
            <p className="order-detail-ordered-at">
              {formatDateTime(order.orderedAt)}
            </p>
          )}
        </div>

        <Link href="/my/orders" className="order-detail-back-link">
          주문 내역
        </Link>
      </div>

      <section className="order-detail-status-section">
        <div>
          <p className="order-detail-status-label">현재 주문 상태</p>

          <div className="order-detail-status-row">
            <strong className="order-detail-status">
              {BUYER_DELIVERY_STATUS_LABELS[order.deliveryStatus]}
            </strong>

            {statusChangedAt && (
              <span className="order-detail-status-at">
                {formatDateTime(statusChangedAt)}
              </span>
            )}
          </div>
        </div>

        {["ORDERED", "PAID"].includes(order.status) && (
          <button
            type="button"
            className="order-detail-cancel-button"
            onClick={() => void handleCancelOrder()}
            disabled={isCancelling}
          >
            {isCancelling ? "취소 중..." : "주문 취소"}
          </button>
        )}
      </section>

      {order.sellerOrders.length > 0 ? (
        <OrderDetailSellerGroups sellerOrders={order.sellerOrders} />
      ) : (
        <OrderDetailProductList items={order.items} />
      )}

      <OrderDetailInfo order={order} />

      <OrderDetailSummary order={order} />
    </div>
  );
}
