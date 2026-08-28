"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { getSellerReturnRequests } from "@/lib/seller-return-api";
import Pagination from "@/components/common/Pagination";
import { useAuthStore } from "@/stores/auth-store";
import {
  RETURN_REASON_LABELS,
  RETURN_RESPONSIBILITY_LABELS,
  RETURN_STATUS_LABELS,
  type ReturnRequestStatus,
  type SellerReturnRequestPage,
} from "@/types/return";

const PAGE_SIZE = 20;
type FilterStatus = ReturnRequestStatus | "ALL";
const FILTERS: Array<{ value: FilterStatus; label: string }> = [
  { value: "ALL", label: "전체" }, { value: "REQUESTED", label: "반품 요청" },
  { value: "APPROVED", label: "승인" }, { value: "COLLECTING", label: "회수 중" },
  { value: "RECEIVED", label: "입고 완료" }, { value: "INSPECTED", label: "검수 완료" },
  { value: "REFUNDING", label: "환불 중" }, { value: "COMPLETED", label: "완료" },
  { value: "REJECTED", label: "거절" }, { value: "CANCELED", label: "철회" },
  { value: "FAILED", label: "실패" },
];

const formatDate = (value: string | null) => value ? new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value)) : "-";
const formatPrice = (value: number) => `${new Intl.NumberFormat("ko-KR").format(value)}원`;

export default function SellerReturnsPage() {
  const router = useRouter();
  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [returnPage, setReturnPage] = useState<SellerReturnRequestPage | null>(null);
  const [status, setStatus] = useState<FilterStatus>("ALL");
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadReturns = useCallback(async () => {
    await Promise.resolve();
    try {
      setLoading(true); setError("");
      setReturnPage(await getSellerReturnRequests({ status: status === "ALL" ? undefined : status, page, size: PAGE_SIZE }));
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : "";
      setError(message.includes("로그인") ? message : "반품 요청 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
    } finally { setLoading(false); }
  }, [page, status]);

  useEffect(() => {
    if (!initialized) return;
    if (!isAuthenticated || !user) { router.replace("/login"); return; }
    const requestId = window.setTimeout(() => void loadReturns(), 0);
    return () => window.clearTimeout(requestId);
  }, [initialized, isAuthenticated, loadReturns, router, user]);

  if (!initialized || !isAuthenticated || !user) return <div className="seller-orders-auth-loading">판매자 정보를 확인하고 있습니다.</div>;

  return <main className="seller-orders-page seller-returns-page"><div className="common-inner seller-orders-container">
    <header className="seller-orders-header"><p>RETURN MANAGEMENT</p><h1>반품 관리</h1><span>반품 요청부터 회수, 입고, 검수와 환불 완료 상태를 관리합니다.</span></header>
    <section className="seller-orders-panel">
      <div className="seller-orders-toolbar seller-returns-toolbar"><div className="seller-orders-tabs" role="tablist" aria-label="반품 상태 필터">{FILTERS.map((filter) => <button key={filter.value} type="button" role="tab" aria-selected={status === filter.value} className={status === filter.value ? "is-active" : ""} onClick={() => { setStatus(filter.value); setPage(0); }}>{filter.label}</button>)}</div></div>
      <div className="seller-orders-count">총 <strong>{returnPage?.totalElements ?? 0}</strong>건{loading && returnPage && <span>목록 갱신 중...</span>}</div>
      {loading && !returnPage && <div className="seller-orders-state">반품 요청을 불러오고 있습니다.</div>}
      {error && <div className="seller-orders-state seller-orders-state-error"><p>{error}</p><button type="button" onClick={() => void loadReturns()}>다시 시도</button></div>}
      {!error && returnPage?.returns.length === 0 && <div className="seller-orders-state">조건에 맞는 반품 요청이 없습니다.</div>}
      {!error && returnPage && returnPage.returns.length > 0 && <>
        <div className={`seller-orders-table-wrap ${loading ? "is-refreshing" : ""}`}><table className="seller-orders-table seller-returns-table"><thead><tr><th>요청번호</th><th>주문</th><th>요청일시</th><th>요청 상품</th><th>반품 사유</th><th>귀책</th><th>환불금액</th><th>상태</th><th>관리</th></tr></thead><tbody>{returnPage.returns.map((request) => {
          const firstItem = request.items[0];
          return <tr key={request.returnRequestId}><td data-label="요청번호"><strong>#{request.returnRequestId}</strong></td><td data-label="주문"><Link className="seller-return-order-link" href={`/seller/orders/${request.sellerOrderId}`}>주문 ID #{request.orderId}</Link></td><td data-label="요청일시">{formatDate(request.requestedAt)}</td><td data-label="요청 상품"><span className="seller-orders-product-name">{firstItem?.productName ?? "상품 정보 없음"}{request.items.length > 1 ? ` 외 ${request.items.length - 1}건` : ""}</span></td><td data-label="반품 사유"><span className="seller-return-reason">{RETURN_REASON_LABELS[request.reasonType]}<small>{request.reason}</small></span></td><td data-label="귀책">{request.responsibility ? RETURN_RESPONSIBILITY_LABELS[request.responsibility] : "확인 전"}</td><td data-label="환불금액">{request.refundAmount === null ? "계산 전" : <strong>{formatPrice(request.refundAmount)}</strong>}</td><td data-label="상태"><span className={`seller-return-status seller-return-status-${request.status.toLowerCase()}`}>{RETURN_STATUS_LABELS[request.status]}</span></td><td data-label="관리"><Link className="seller-orders-detail-link" href={`/seller/orders/returns/${request.returnRequestId}`}>{request.status === "REQUESTED" ? "요청 확인" : "상세보기"}</Link></td></tr>;
        })}</tbody></table></div>
        <Pagination currentPage={returnPage.page} totalPages={returnPage.totalPages} ariaLabel="반품 요청 목록 페이지" disabled={loading} onPageChange={setPage} className="seller-orders-pagination" />
      </>}
    </section>
  </div></main>;
}
