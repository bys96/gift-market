"use client";

import { getLoginRedirectUrl } from "@/lib/login-redirect";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useState } from "react";
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

function parsePage(value: string | null) { const parsed = Number(value); return Number.isInteger(parsed) && parsed >= 0 ? parsed : 0; }
function parseStatus(value: string | null): FilterStatus { return FILTERS.find((filter) => filter.value === value)?.value ?? "ALL"; }

const formatDate = (value: string | null) => value ? new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value)) : "-";
const formatPrice = (value: number) => `${new Intl.NumberFormat("ko-KR").format(value)}원`;

function SellerReturnsContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [returnPage, setReturnPage] = useState<SellerReturnRequestPage | null>(null);
  const status = parseStatus(searchParams.get("status"));
  const page = parsePage(searchParams.get("page"));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const createListUrl = (next: { status?: FilterStatus; page?: number } = {}) => { const nextStatus = next.status ?? status; const nextPage = next.page ?? page; const params = new URLSearchParams(); if (nextStatus !== "ALL") params.set("status", nextStatus); if (nextPage > 0) params.set("page", String(nextPage)); const query = params.toString(); return query ? `/seller/orders/returns?${query}` : "/seller/orders/returns"; };
  const detailQuery = createListUrl().split("?")[1] ?? "";

  const loadReturns = useCallback(async (requestStatus: FilterStatus, requestPage: number) => {
    await Promise.resolve();
    try {
      setLoading(true); setError("");
      setReturnPage(await getSellerReturnRequests({ status: requestStatus === "ALL" ? undefined : requestStatus, page: requestPage, size: PAGE_SIZE }));
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : "";
      setError(message.includes("로그인") ? message : "반품 요청 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
    } finally { setLoading(false); }
  }, []);

  useEffect(() => {
    if (!initialized) return;
    if (!isAuthenticated || !user) { router.replace(getLoginRedirectUrl()); return; }
    const requestId = window.setTimeout(() => void loadReturns(status, page), 0);
    return () => window.clearTimeout(requestId);
  }, [initialized, isAuthenticated, loadReturns, page, router, status, user]);

  if (!initialized || !isAuthenticated || !user) return <div className="seller-orders-auth-loading">판매자 정보를 확인하고 있습니다.</div>;

  return <main className="seller-orders-page seller-returns-page"><div className="common-inner seller-orders-container">
    <header className="seller-orders-header"><p>RETURN MANAGEMENT</p><h1>반품 관리</h1><span>반품 요청부터 회수, 입고, 검수와 환불 완료 상태를 관리합니다.</span></header>
    <section className="seller-orders-panel">
      <div className="seller-orders-toolbar seller-returns-toolbar"><div className="seller-orders-tabs" role="tablist" aria-label="반품 상태 필터">{FILTERS.map((filter) => <button key={filter.value} type="button" role="tab" aria-selected={status === filter.value} className={status === filter.value ? "is-active" : ""} onClick={() => router.push(createListUrl({ status: filter.value, page: 0 }), { scroll: false })}>{filter.label}</button>)}</div></div>
      <div className="seller-orders-count">총 <strong>{returnPage?.totalElements ?? 0}</strong>건{loading && returnPage && <span>목록 갱신 중...</span>}</div>
      {loading && !returnPage && <div className="seller-orders-state">반품 요청을 불러오고 있습니다.</div>}
      {error && <div className="seller-orders-state seller-orders-state-error"><p>{error}</p><button type="button" onClick={() => void loadReturns(status, page)}>다시 시도</button></div>}
      {!error && returnPage?.returns.length === 0 && <div className="seller-orders-state">조건에 맞는 반품 요청이 없습니다.</div>}
      {!error && returnPage && returnPage.returns.length > 0 && <>
        <div className={`seller-orders-table-wrap ${loading ? "is-refreshing" : ""}`}><table className="seller-orders-table seller-returns-table"><thead><tr><th>요청번호</th><th>주문</th><th>요청일시</th><th>요청 상품</th><th>반품 사유</th><th>귀책</th><th>환불금액</th><th>상태</th><th>관리</th></tr></thead><tbody>{returnPage.returns.map((request) => {
          const firstItem = request.items[0];
          return <tr key={request.returnRequestId}><td data-label="요청번호"><strong>#{request.returnRequestId}</strong></td><td data-label="주문"><Link className="seller-return-order-link" href={`/seller/orders/${request.sellerOrderId}`}>주문 ID #{request.orderId}</Link></td><td data-label="요청일시">{formatDate(request.requestedAt)}</td><td data-label="요청 상품"><span className="seller-orders-product-name">{firstItem?.productName ?? "상품 정보 없음"}{request.items.length > 1 ? ` 외 ${request.items.length - 1}건` : ""}</span></td><td data-label="반품 사유"><span className="seller-return-reason">{RETURN_REASON_LABELS[request.reasonType]}<small>{request.reason}</small></span></td><td data-label="귀책">{request.responsibility ? RETURN_RESPONSIBILITY_LABELS[request.responsibility] : "확인 전"}</td><td data-label="환불금액">{request.refundAmount === null ? "계산 전" : <strong>{formatPrice(request.refundAmount)}</strong>}</td><td data-label="상태"><span className={`seller-return-status seller-return-status-${request.status.toLowerCase()}`}>{RETURN_STATUS_LABELS[request.status]}</span></td><td data-label="관리"><Link className="seller-orders-detail-link" href={`/seller/orders/returns/${request.returnRequestId}${detailQuery ? `?${detailQuery}` : ""}`}>{request.status === "REQUESTED" ? "요청 확인" : "상세보기"}</Link></td></tr>;
        })}</tbody></table></div>
        <Pagination currentPage={returnPage.page} totalPages={returnPage.totalPages} ariaLabel="반품 요청 목록 페이지" disabled={loading} onPageChange={(nextPage) => router.push(createListUrl({ page: nextPage }), { scroll: false })} className="seller-orders-pagination" />
      </>}
    </section>
  </div></main>;
}

export default function SellerReturnsPage() { return <Suspense fallback={<div className="seller-orders-auth-loading">반품 요청 목록을 준비하고 있습니다.</div>}><SellerReturnsContent /></Suspense>; }
