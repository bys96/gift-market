"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import Pagination from "@/components/common/Pagination";
import { getSellerSettlements, getSellerSettlementSummary } from "@/lib/seller-settlement-api";
import { formatSettlementAmount, formatSettlementDate } from "@/lib/seller-settlement-format";
import { getLoginRedirectUrl } from "@/lib/login-redirect";
import { useAuthStore } from "@/stores/auth-store";
import {
  SELLER_SETTLEMENT_STATUS_LABEL,
  type SellerSettlementPage,
  type SellerSettlementStatus,
  type SellerSettlementSummary,
} from "@/types/seller-settlement";

const PAGE_SIZE = 20;
type StatusFilter = SellerSettlementStatus | "ALL";

const FILTERS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "READY", label: "정산 대기" },
  { value: "ON_HOLD", label: "정산 보류" },
  { value: "CONFIRMED", label: "정산 확정" },
];

export default function SellerSettlementsPage() {
  const router = useRouter();
  const initialized = useAuthStore((state) => state.initialized);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const user = useAuthStore((state) => state.user);
  const [summary, setSummary] = useState<SellerSettlementSummary | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [summaryError, setSummaryError] = useState(false);
  const [settlementPage, setSettlementPage] = useState<SellerSettlementPage | null>(null);
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const listRequestId = useRef(0);

  const loadSummary = useCallback(async () => {
    setSummaryLoading(true);
    setSummaryError(false);
    try {
      setSummary(await getSellerSettlementSummary());
    } catch {
      setSummaryError(true);
    } finally {
      setSummaryLoading(false);
    }
  }, []);

  const loadSettlements = useCallback(async () => {
    const requestId = ++listRequestId.current;
    setLoading(true);
    setError(false);
    try {
      const result = await getSellerSettlements({
        status: status === "ALL" ? undefined : status,
        page,
        size: PAGE_SIZE,
      });
      if (requestId === listRequestId.current) setSettlementPage(result);
    } catch {
      if (requestId === listRequestId.current) setError(true);
    } finally {
      if (requestId === listRequestId.current) setLoading(false);
    }
  }, [page, status]);

  useEffect(() => {
    if (!initialized) return;
    if (!isAuthenticated || !user) {
      router.replace(getLoginRedirectUrl());
      return;
    }
    const requestId = window.setTimeout(() => void loadSummary(), 0);
    return () => window.clearTimeout(requestId);
  }, [initialized, isAuthenticated, loadSummary, router, user]);

  useEffect(() => {
    if (!initialized || !isAuthenticated || !user) return;
    const requestId = window.setTimeout(() => void loadSettlements(), 0);
    return () => window.clearTimeout(requestId);
  }, [initialized, isAuthenticated, loadSettlements, user]);

  if (!initialized || !isAuthenticated || !user) {
    return <div className="seller-orders-auth-loading">판매자 정보를 확인하고 있습니다.</div>;
  }

  return (
    <main className="seller-orders-page seller-settlements-page">
      <div className="common-inner seller-orders-container">
        <header className="seller-orders-header">
          <p>SETTLEMENT</p>
          <h1>정산 관리</h1>
          <span>판매 완료 금액과 수수료, 취소·반품을 반영한 정산 내역을 확인합니다.</span>
        </header>

        <section className="seller-settlements-summary" aria-labelledby="settlements-summary-title">
          <div className="seller-settlements-section-heading">
            <div>
              <h2 id="settlements-summary-title">현재 미정산 금액</h2>
              <p>아직 정산 회차에 포함되지 않은 원장의 순금액입니다.</p>
            </div>
          </div>
          {summaryLoading && !summary && <div className="seller-settlements-summary-state">미정산 금액을 불러오고 있습니다.</div>}
          {summaryError && <div className="seller-settlements-summary-state is-error" role="alert"><span>미정산 금액을 불러오지 못했습니다.</span><button type="button" onClick={() => void loadSummary()}>다시 시도</button></div>}
          {!summaryError && summary && (
            <div className="seller-settlements-summary-grid" aria-busy={summaryLoading}>
              <article><span>정산 가능</span><strong>{formatSettlementAmount(summary.eligibleAmount)}</strong><small>정산 가능 시각이 지난 원장</small></article>
              <article><span>정산 대기</span><strong>{formatSettlementAmount(summary.holdAmount)}</strong><small>정산 가능 시각을 기다리는 원장</small></article>
              <article><span>정산일 미확정</span><strong>{formatSettlementAmount(summary.awaitingEligibilityAmount)}</strong><small>배송 등 기준 시각이 아직 정해지지 않은 원장</small></article>
            </div>
          )}
          <p className="seller-settlements-summary-note">정산 가능 금액은 시간 기준입니다. 진행 중인 취소·반품·교환 등이 있으면 해당 주문은 정산 회차에서 제외될 수 있습니다.</p>
        </section>

        <section className="seller-orders-panel" aria-labelledby="settlements-list-title">
          <div className="seller-settlements-list-heading">
            <h2 id="settlements-list-title">기간별 정산 내역</h2>
            <span>확정된 정산을 포함해 생성된 정산 회차를 조회합니다.</span>
          </div>
          <div className="seller-orders-toolbar">
            <div className="seller-orders-tabs" role="tablist" aria-label="정산 상태 필터">
              {FILTERS.map((filter) => (
                <button key={filter.value} type="button" role="tab"
                  aria-selected={status === filter.value}
                  className={status === filter.value ? "is-active" : ""}
                  onClick={() => { setStatus(filter.value); setPage(0); }}>
                  {filter.label}
                </button>
              ))}
            </div>
          </div>
          <div className="seller-orders-count">총 <strong>{settlementPage?.totalElements ?? 0}</strong>건{loading && settlementPage && <span>목록 갱신 중...</span>}</div>
          {loading && !settlementPage && <div className="seller-orders-state">정산 내역을 불러오고 있습니다.</div>}
          {error && <div className="seller-orders-state seller-orders-state-error" role="alert"><p>정산 내역을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.</p><button type="button" onClick={() => void loadSettlements()}>다시 시도</button></div>}
          {!error && !loading && settlementPage?.settlements.length === 0 && <div className="seller-orders-state">{status === "ALL" ? "아직 생성된 정산 내역이 없습니다." : "선택한 상태의 정산 내역이 없습니다."}</div>}
          {!error && settlementPage && settlementPage.settlements.length > 0 && (
            <>
              <div className={`seller-orders-table-wrap ${loading ? "is-refreshing" : ""}`}>
                <table className="seller-orders-table seller-settlements-table">
                  <thead><tr><th>정산번호</th><th>정산 기간</th><th>상품 판매</th><th>배송비</th><th>취소·반품</th><th>수수료</th><th>조정</th><th>정산금액</th><th>상태</th><th>상세</th></tr></thead>
                  <tbody>{settlementPage.settlements.map((settlement) => (
                    <tr key={settlement.id}>
                      <td data-label="정산번호"><strong className="seller-settlements-number">{settlement.settlementNumber}</strong></td>
                      <td data-label="정산 기간">{formatSettlementDate(settlement.periodStart)} ~ {formatSettlementDate(settlement.periodEnd)}</td>
                      <td data-label="상품 판매">{formatSettlementAmount(settlement.totalProductSalesAmount)}</td>
                      <td data-label="배송비">{formatSettlementAmount(settlement.totalShippingSalesAmount)}</td>
                      <td data-label="취소·반품">{formatSettlementAmount(settlement.totalCancellationAmount + settlement.totalReturnAmount)}</td>
                      <td data-label="수수료">{formatSettlementAmount(settlement.totalCommissionAmount)}</td>
                      <td data-label="조정">{formatSettlementAmount(settlement.totalAdjustmentAmount)}</td>
                      <td data-label="정산금액"><strong>{formatSettlementAmount(settlement.settlementAmount)}</strong></td>
                      <td data-label="상태"><span className={`seller-settlement-status seller-settlement-status-${settlement.status.toLowerCase()}`}>{SELLER_SETTLEMENT_STATUS_LABEL[settlement.status]}</span></td>
                      <td data-label="상세"><Link className="seller-orders-detail-link" href={`/seller/settlements/${settlement.id}`}>상세보기</Link></td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
              <Pagination currentPage={settlementPage.page} totalPages={settlementPage.totalPages} ariaLabel="정산 내역 페이지" disabled={loading} onPageChange={setPage} className="seller-orders-pagination" />
            </>
          )}
        </section>
      </div>
    </main>
  );
}
