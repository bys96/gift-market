"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useCallback, useEffect, useState } from "react";

import { getSellerOrders } from "@/lib/seller-order-api";
import Pagination from "@/components/common/Pagination";
import { useAuthStore } from "@/stores/auth-store";
import {
  SELLER_ORDER_STATUS_LABEL,
  type SellerOrderPage,
  type SellerOrderStatus,
} from "@/types/seller-order";

const PAGE_SIZE = 20;
type FilterStatus = Exclude<SellerOrderStatus, "PENDING_PAYMENT"> | "ALL";

const FILTERS: { value: FilterStatus; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "PAID", label: "신규 주문" },
  { value: "PREPARING", label: "상품 준비중" },
  { value: "SHIPPED", label: "배송중" },
  { value: "DELIVERED", label: "배송완료" },
  { value: "CANCELLED", label: "취소" },
];

function formatPrice(value: number) {
  return `${new Intl.NumberFormat("ko-KR").format(value)}원`;
}

function formatDate(value: string | null) {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function friendlyError(error: unknown) {
  const message = error instanceof Error ? error.message : "";
  if (message.includes("로그인")) return message;
  return "주문 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.";
}

export default function SellerOrdersPage() {
  const router = useRouter();
  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [orderPage, setOrderPage] = useState<SellerOrderPage | null>(null);
  const [status, setStatus] = useState<FilterStatus>("ALL");
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadOrders = useCallback(async () => {
    await Promise.resolve();

    try {
      setLoading(true);
      setError("");
      const result = await getSellerOrders({
        status: status === "ALL" ? undefined : status,
        keyword,
        page,
        size: PAGE_SIZE,
      });
      setOrderPage(result);
    } catch (loadError) {
      setError(friendlyError(loadError));
    } finally {
      setLoading(false);
    }
  }, [keyword, page, status]);

  useEffect(() => {
    if (!initialized) return;
    if (!isAuthenticated || !user) {
      router.replace("/login");
      return;
    }
    const requestId = window.setTimeout(() => void loadOrders(), 0);

    return () => window.clearTimeout(requestId);
  }, [initialized, isAuthenticated, loadOrders, router, user]);

  const submitSearch = (event: FormEvent) => {
    event.preventDefault();
    setPage(0);
    setKeyword(keywordInput.trim());
  };

  if (!initialized || !isAuthenticated || !user) {
    return <div className="seller-orders-auth-loading">판매자 정보를 확인하고 있습니다.</div>;
  }

  return (
    <main className="seller-orders-page">
      <div className="common-inner seller-orders-container">
        <header className="seller-orders-header">
          <p>ORDER MANAGEMENT</p>
          <h1>주문 관리</h1>
          <span>결제 완료된 주문의 상품 준비와 배송 상태를 관리합니다.</span>
        </header>

        <section className="seller-orders-panel">
          <div className="seller-orders-toolbar">
            <div className="seller-orders-tabs" role="tablist" aria-label="주문 상태 필터">
              {FILTERS.map((filter) => (
                <button
                  key={filter.value}
                  type="button"
                  role="tab"
                  aria-selected={status === filter.value}
                  className={status === filter.value ? "is-active" : ""}
                  onClick={() => {
                    setStatus(filter.value);
                    setPage(0);
                  }}
                >
                  {filter.label}
                </button>
              ))}
            </div>

            <form className="seller-orders-search" onSubmit={submitSearch}>
              <input
                value={keywordInput}
                maxLength={100}
                placeholder="주문번호 또는 상품명 검색"
                aria-label="주문 검색어"
                onChange={(event) => setKeywordInput(event.target.value)}
              />
              <button type="submit">검색</button>
            </form>
          </div>

          <div className="seller-orders-count">
            총 <strong>{orderPage?.totalElements ?? 0}</strong>건
            {loading && orderPage && <span>목록 갱신 중...</span>}
          </div>

          {loading && !orderPage && <div className="seller-orders-state">주문 목록을 불러오고 있습니다.</div>}
          {error && (
            <div className="seller-orders-state seller-orders-state-error">
              <p>{error}</p>
              <button type="button" onClick={() => void loadOrders()}>다시 시도</button>
            </div>
          )}
          {!error && orderPage?.orders.length === 0 && (
            <div className="seller-orders-state">조건에 맞는 주문이 없습니다.</div>
          )}

          {!error && orderPage && orderPage.orders.length > 0 && (
            <>
              <div className={`seller-orders-table-wrap ${loading ? "is-refreshing" : ""}`}>
                <table className="seller-orders-table">
                  <thead><tr><th>주문번호</th><th>주문일시</th><th>상품</th><th>수량</th><th>판매금액</th><th>수령인</th><th>배송상태</th><th>배송정보</th><th>관리</th></tr></thead>
                  <tbody>
                    {orderPage.orders.map((order) => (
                      <tr key={order.sellerOrderId}>
                        <td data-label="주문번호"><strong>{order.merchantOrderId}</strong></td>
                        <td data-label="주문일시">{formatDate(order.orderedAt)}</td>
                        <td data-label="상품"><span className="seller-orders-product-name">{order.representativeProductName}{order.productTypeCount > 1 ? ` 외 ${order.productTypeCount - 1}건` : ""}</span></td>
                        <td data-label="수량">{order.totalQuantity}개</td>
                        <td data-label="판매금액"><strong>{formatPrice(order.totalProductAmount)}</strong></td>
                        <td data-label="수령인">{order.recipientName}</td>
                        <td data-label="배송상태"><span className={`seller-order-status seller-order-status-${order.status.toLowerCase()}`}>{SELLER_ORDER_STATUS_LABEL[order.status]}</span></td>
                        <td data-label="배송정보">{order.shippingCompany && order.trackingNumber ? <span className="seller-orders-shipping">{order.shippingCompany}<small>{order.trackingNumber}</small></span> : "-"}</td>
                        <td data-label="관리"><Link className="seller-orders-detail-link" href={`/seller/orders/${order.sellerOrderId}`}>상세보기</Link></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <Pagination currentPage={orderPage.page} totalPages={orderPage.totalPages} ariaLabel="주문 목록 페이지" disabled={loading} onPageChange={setPage} className="seller-orders-pagination" />
            </>
          )}
        </section>
      </div>
    </main>
  );
}
