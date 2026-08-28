"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { getSellerExchangeRequests } from "@/lib/seller-exchange-api";
import Pagination from "@/components/common/Pagination";
import { useAuthStore } from "@/stores/auth-store";
import { EXCHANGE_REASON_LABELS, EXCHANGE_RESPONSIBILITY_LABELS, EXCHANGE_STATUS_LABELS, type ExchangeRequestStatus, type SellerExchangeRequestPage } from "@/types/exchange";

const PAGE_SIZE = 20;
type Filter = ExchangeRequestStatus | "ALL";
const FILTERS: Array<{ value: Filter; label: string }> = [
  { value: "ALL", label: "전체" }, { value: "REQUESTED", label: "교환 요청" },
  { value: "APPROVED", label: "승인" }, { value: "PAYMENT_PENDING", label: "배송비 결제 대기" },
  { value: "COLLECTING", label: "회수 중" }, { value: "RECEIVED", label: "입고 완료" },
  { value: "INSPECTED", label: "검수 완료" }, { value: "RESHIPPING", label: "교환품 배송 중" },
  { value: "COMPLETED", label: "완료" }, { value: "REJECTED", label: "거절" },
  { value: "CANCELED", label: "취소" }, { value: "FAILED", label: "실패" },
];
const date = (value: string | null | undefined) => value ? new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value)) : "-";

export default function SellerExchangesPage() {
  const router = useRouter(); const initialized = useAuthStore((s) => s.initialized); const user = useAuthStore((s) => s.user); const authenticated = useAuthStore((s) => s.isAuthenticated);
  const [result, setResult] = useState<SellerExchangeRequestPage | null>(null); const [status, setStatus] = useState<Filter>("ALL"); const [page, setPage] = useState(0); const [loading, setLoading] = useState(true); const [error, setError] = useState("");
  const load = useCallback(async () => { await Promise.resolve(); try { setLoading(true); setError(""); setResult(await getSellerExchangeRequests({ status: status === "ALL" ? undefined : status, page, size: PAGE_SIZE })); } catch (e) { setError(e instanceof Error ? e.message : "교환 요청 목록을 불러오지 못했습니다."); } finally { setLoading(false); } }, [page, status]);
  useEffect(() => { if (!initialized) return; if (!authenticated || !user) { router.replace("/login"); return; } const id = window.setTimeout(() => void load(), 0); return () => window.clearTimeout(id); }, [authenticated, initialized, load, router, user]);
  if (!initialized || !authenticated || !user) return <div className="seller-orders-auth-loading">판매자 정보를 확인하고 있습니다.</div>;
  return <main className="seller-orders-page seller-exchanges-page"><div className="common-inner seller-orders-container">
    <header className="seller-orders-header"><p>EXCHANGE MANAGEMENT</p><h1>교환 관리</h1><span>승인부터 회수, 검수, 교환품 재배송과 완료까지 관리합니다.</span></header>
    <section className="seller-orders-panel"><div className="seller-orders-toolbar seller-exchanges-toolbar"><div className="seller-orders-tabs" role="tablist" aria-label="교환 상태 필터">{FILTERS.map((filter) => <button key={filter.value} type="button" role="tab" aria-selected={status === filter.value} className={status === filter.value ? "is-active" : ""} onClick={() => { setStatus(filter.value); setPage(0); }}>{filter.label}</button>)}</div></div>
      <div className="seller-orders-count">총 <strong>{result?.totalElements ?? 0}</strong>건 {loading && result && <span>목록 갱신 중...</span>}</div>
      {loading && !result && <div className="seller-orders-state">교환 요청을 불러오고 있습니다.</div>}
      {error && <div className="seller-orders-state seller-orders-state-error"><p>{error}</p><button type="button" onClick={() => void load()}>다시 시도</button></div>}
      {!error && result?.exchanges.length === 0 && <div className="seller-orders-state">조건에 맞는 교환 요청이 없습니다.</div>}
      {!error && result && result.exchanges.length > 0 && <><div className={`seller-orders-table-wrap ${loading ? "is-refreshing" : ""}`}><table className="seller-orders-table seller-exchanges-table"><thead><tr><th>요청번호</th><th>주문</th><th>요청일</th><th>원 상품 / 교환 대상</th><th>수량</th><th>사유</th><th>귀책</th><th>상태</th><th>관리</th></tr></thead><tbody>{result.exchanges.map((request) => { const item = request.items[0]; return <tr key={request.exchangeRequestId}><td data-label="요청번호"><strong>#{request.exchangeRequestId}</strong></td><td data-label="주문"><Link className="seller-return-order-link" href={`/seller/orders/${request.sellerOrderId}`}>주문 ID #{request.orderId}</Link></td><td data-label="요청일">{date(request.requestedAt)}</td><td data-label="상품"><span className="seller-exchange-product"><strong>{item?.originalProductName ?? "상품 정보 없음"}</strong><small>{item?.originalOptionSnapshot ?? "기본 상품"} → {item?.targetOptionSnapshot ?? "기본 상품"}{request.items.length > 1 ? ` 외 ${request.items.length - 1}건` : ""}</small></span></td><td data-label="수량">{request.items.reduce((sum, value) => sum + (Number.isFinite(value.quantity) ? value.quantity : 0), 0)}개</td><td data-label="사유"><span className="seller-return-reason">{EXCHANGE_REASON_LABELS[request.reasonType]}<small>{request.reason}</small></span></td><td data-label="귀책">{request.responsibility ? EXCHANGE_RESPONSIBILITY_LABELS[request.responsibility] : "확정 전"}</td><td data-label="상태"><span className={`seller-return-status seller-exchange-status-${request.status.toLowerCase()}`}>{EXCHANGE_STATUS_LABELS[request.status]}</span></td><td data-label="관리"><Link className="seller-orders-detail-link" href={`/seller/orders/exchanges/${request.exchangeRequestId}`}>{request.status === "REQUESTED" ? "요청 확인" : "상세보기"}</Link></td></tr>; })}</tbody></table></div>
        <Pagination currentPage={result.page} totalPages={result.totalPages} ariaLabel="교환 요청 목록 페이지" disabled={loading} onPageChange={setPage} className="seller-orders-pagination" /></>}
    </section>
  </div></main>;
}
