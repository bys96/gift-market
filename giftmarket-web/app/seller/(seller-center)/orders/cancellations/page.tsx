"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { getSellerOrderCancellations } from "@/lib/seller-order-cancellation-api";
import Pagination from "@/components/common/Pagination";
import { useAuthStore } from "@/stores/auth-store";
import {
  SELLER_ORDER_CANCELLATION_STATUS_LABEL,
  type SellerOrderCancellationPage,
  type SellerOrderCancellationStatus,
} from "@/types/seller-order-cancellation";

const PAGE_SIZE = 20;
type FilterStatus = SellerOrderCancellationStatus | "ALL";

const FILTERS: { value: FilterStatus; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "REQUESTED", label: "승인 대기" },
  { value: "PROCESSING", label: "환불 처리 중" },
  { value: "COMPLETED", label: "취소 완료" },
  { value: "REJECTED", label: "취소 거절" },
  { value: "FAILED", label: "처리 실패" },
];

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
  return "취소 요청 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.";
}

export default function SellerOrderCancellationsPage() {
  const router = useRouter();
  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [cancellationPage, setCancellationPage] =
    useState<SellerOrderCancellationPage | null>(null);
  const [status, setStatus] = useState<FilterStatus>("ALL");
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadCancellations = useCallback(async () => {
    await Promise.resolve();
    try {
      setLoading(true);
      setError("");
      const result = await getSellerOrderCancellations({
        status: status === "ALL" ? undefined : status,
        page,
        size: PAGE_SIZE,
      });
      setCancellationPage(result);
    } catch (loadError) {
      setError(friendlyError(loadError));
    } finally {
      setLoading(false);
    }
  }, [page, status]);

  useEffect(() => {
    if (!initialized) return;
    if (!isAuthenticated || !user) {
      router.replace("/login");
      return;
    }
    const requestId = window.setTimeout(() => void loadCancellations(), 0);
    return () => window.clearTimeout(requestId);
  }, [initialized, isAuthenticated, loadCancellations, router, user]);

  if (!initialized || !isAuthenticated || !user) {
    return <div className="seller-orders-auth-loading">판매자 정보를 확인하고 있습니다.</div>;
  }

  return (
    <main className="seller-orders-page seller-cancellations-page">
      <div className="common-inner seller-orders-container">
        <header className="seller-orders-header">
          <p>CANCELLATION MANAGEMENT</p>
          <h1>취소 요청</h1>
          <span>구매자가 요청한 상품 취소를 확인하고 승인 또는 거절합니다.</span>
        </header>

        <section className="seller-orders-panel">
          <div className="seller-orders-toolbar seller-cancellations-toolbar">
            <div className="seller-orders-tabs" role="tablist" aria-label="취소 요청 상태 필터">
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
          </div>

          <div className="seller-orders-count">
            총 <strong>{cancellationPage?.totalElements ?? 0}</strong>건
            {loading && cancellationPage && <span>목록 갱신 중...</span>}
          </div>

          {loading && !cancellationPage && (
            <div className="seller-orders-state">취소 요청을 불러오고 있습니다.</div>
          )}
          {error && (
            <div className="seller-orders-state seller-orders-state-error">
              <p>{error}</p>
              <button type="button" onClick={() => void loadCancellations()}>다시 시도</button>
            </div>
          )}
          {!error && cancellationPage?.cancellations.length === 0 && (
            <div className="seller-orders-state">조건에 맞는 취소 요청이 없습니다.</div>
          )}

          {!error && cancellationPage && cancellationPage.cancellations.length > 0 && (
            <>
              <div className={`seller-orders-table-wrap ${loading ? "is-refreshing" : ""}`}>
                <table className="seller-orders-table seller-cancellations-table">
                  <thead>
                    <tr><th>주문번호</th><th>요청일시</th><th>요청 상품</th><th>요청 수량</th><th>취소 사유</th><th>상태</th><th>관리</th></tr>
                  </thead>
                  <tbody>
                    {cancellationPage.cancellations.map((cancellation) => {
                      const firstItem = cancellation.items[0];
                      const totalQuantity = cancellation.items.reduce(
                        (sum, item) => sum + item.requestedQuantity,
                        0,
                      );
                      return (
                        <tr key={cancellation.cancellationId}>
                          <td data-label="주문번호"><strong>{cancellation.orderNumber}</strong><small className="seller-cancellation-id">요청 #{cancellation.cancellationId}</small></td>
                          <td data-label="요청일시">{formatDate(cancellation.requestedAt)}</td>
                          <td data-label="요청 상품"><span className="seller-orders-product-name">{firstItem?.productName ?? "상품 정보 없음"}{cancellation.items.length > 1 ? ` 외 ${cancellation.items.length - 1}건` : ""}</span></td>
                          <td data-label="요청 수량">{totalQuantity}개</td>
                          <td data-label="취소 사유"><span className="seller-cancellation-reason">{cancellation.reason}</span></td>
                          <td data-label="상태"><span className={`seller-cancellation-status seller-cancellation-status-${cancellation.status.toLowerCase()}`}>{SELLER_ORDER_CANCELLATION_STATUS_LABEL[cancellation.status]}</span></td>
                          <td data-label="관리"><Link className="seller-orders-detail-link" href={`/seller/orders/cancellations/${cancellation.cancellationId}`}>{cancellation.status === "REQUESTED" ? "요청 확인" : "상세보기"}</Link></td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              <Pagination currentPage={cancellationPage.page} totalPages={cancellationPage.totalPages} ariaLabel="취소 요청 목록 페이지" disabled={loading} onPageChange={setPage} className="seller-orders-pagination" />
            </>
          )}
        </section>
      </div>
    </main>
  );
}
