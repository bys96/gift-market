"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { getLoginRedirectUrl } from "@/lib/login-redirect";
import { getSellerSettlement } from "@/lib/seller-settlement-api";
import { formatSettlementAmount, formatSettlementDate, formatSignedSettlementAmount } from "@/lib/seller-settlement-format";
import { useAuthStore } from "@/stores/auth-store";
import {
  SELLER_SETTLEMENT_LEDGER_LABEL,
  SELLER_SETTLEMENT_STATUS_LABEL,
  type SellerSettlementDetail,
} from "@/types/seller-settlement";

export default function SellerSettlementDetailPage() {
  const params = useParams<{ settlementId: string }>();
  const router = useRouter();
  const settlementId = Number(params.settlementId);
  const initialized = useAuthStore((state) => state.initialized);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const user = useAuthStore((state) => state.user);
  const [detail, setDetail] = useState<SellerSettlementDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadDetail = useCallback(async () => {
    if (!Number.isSafeInteger(settlementId) || settlementId <= 0) {
      setError("올바른 정산 내역 번호가 아닙니다.");
      setLoading(false);
      return;
    }
    setLoading(true);
    setError("");
    try {
      setDetail(await getSellerSettlement(settlementId));
    } catch {
      setError("정산 내역을 확인할 수 없습니다. 접근 권한이나 내역 번호를 확인해주세요.");
    } finally {
      setLoading(false);
    }
  }, [settlementId]);

  useEffect(() => {
    if (!initialized) return;
    if (!isAuthenticated || !user) {
      router.replace(getLoginRedirectUrl());
      return;
    }
    const requestId = window.setTimeout(() => void loadDetail(), 0);
    return () => window.clearTimeout(requestId);
  }, [initialized, isAuthenticated, loadDetail, router, user]);

  if (!initialized || !isAuthenticated || !user || loading) {
    return <div className="seller-orders-auth-loading">정산 내역을 확인하고 있습니다.</div>;
  }
  if (error || !detail) {
    return <main className="seller-orders-page seller-settlements-page"><div className="common-inner seller-orders-container"><div className="seller-orders-state seller-orders-state-error" role="alert"><p>{error || "정산 내역을 확인할 수 없습니다."}</p><button type="button" onClick={() => void loadDetail()}>다시 시도</button><Link href="/seller/settlements">목록으로</Link></div></div></main>;
  }

  const settlement = detail.settlement;

  return (
    <main className="seller-orders-page seller-settlements-page">
      <div className="common-inner seller-orders-container seller-settlements-detail-container">
        <header className="seller-order-detail-header">
          <div><p>SETTLEMENT DETAIL</p><h1>정산 상세</h1><span className="seller-settlements-number">{settlement.settlementNumber}</span></div>
          <Link href="/seller/settlements">목록으로</Link>
        </header>

        <section className="seller-order-detail-summary seller-settlements-detail-summary" aria-label="정산 요약">
          <div><span>상태</span><strong><span className={`seller-settlement-status seller-settlement-status-${settlement.status.toLowerCase()}`}>{SELLER_SETTLEMENT_STATUS_LABEL[settlement.status]}</span></strong></div>
          <div><span>정산 기간</span><strong>{formatSettlementDate(settlement.periodStart)} ~ {formatSettlementDate(settlement.periodEnd)}</strong></div>
          <div><span>정산금액</span><strong>{formatSettlementAmount(settlement.settlementAmount)}</strong></div>
        </section>

        <section className="seller-order-detail-section">
          <h2>정산 금액 구성</h2>
          <dl className="seller-settlements-breakdown">
            <div><dt>상품 판매금액</dt><dd>{formatSettlementAmount(settlement.totalProductSalesAmount)}</dd></div>
            <div><dt>배송비</dt><dd>{formatSettlementAmount(settlement.totalShippingSalesAmount)}</dd></div>
            <div><dt>취소금액</dt><dd>{formatSignedSettlementAmount(-settlement.totalCancellationAmount)}</dd></div>
            <div><dt>반품금액</dt><dd>{formatSignedSettlementAmount(-settlement.totalReturnAmount)}</dd></div>
            <div><dt>수수료</dt><dd>{formatSignedSettlementAmount(-settlement.totalCommissionAmount)}</dd></div>
            <div><dt>조정금액</dt><dd>{formatSignedSettlementAmount(settlement.totalAdjustmentAmount)}</dd></div>
            <div className="seller-settlements-breakdown-total"><dt>최종 정산금액</dt><dd>{formatSettlementAmount(settlement.settlementAmount)}</dd></div>
          </dl>
          <p className="seller-settlements-detail-meta">원장 {settlement.ledgerEntryCount}건{settlement.confirmedAt && ` · 확정일 ${formatSettlementDate(settlement.confirmedAt, true)}`}</p>
        </section>

        <section className="seller-orders-panel" aria-labelledby="settlement-ledger-title">
          <div className="seller-settlements-list-heading"><h2 id="settlement-ledger-title">포함된 정산 원장</h2><span>판매·환불·수수료 등 정산금액을 구성하는 기록입니다.</span></div>
          {detail.ledgerEntries.length === 0 ? (
            <div className="seller-orders-state">표시할 원장 기록이 없습니다.</div>
          ) : (
            <div className="seller-orders-table-wrap">
              <table className="seller-orders-table seller-settlements-ledger-table">
                <thead><tr><th>발생일</th><th>판매자 주문</th><th>유형</th><th>금액</th><th>정산 가능일</th><th>사유</th></tr></thead>
                <tbody>{detail.ledgerEntries.map((entry) => (
                  <tr key={entry.id}>
                    <td data-label="발생일">{formatSettlementDate(entry.occurredAt, true)}</td>
                    <td data-label="판매자 주문"><Link href={`/seller/orders/${entry.sellerOrderId}`}>주문 #{entry.sellerOrderId}</Link></td>
                    <td data-label="유형">{SELLER_SETTLEMENT_LEDGER_LABEL[entry.type]}</td>
                    <td data-label="금액"><strong className={entry.amount < 0 ? "seller-settlements-negative" : "seller-settlements-positive"}>{formatSignedSettlementAmount(entry.amount)}</strong></td>
                    <td data-label="정산 가능일">{entry.eligibleAt ? formatSettlementDate(entry.eligibleAt, true) : "미확정"}</td>
                    <td data-label="사유">{entry.reason || "-"}</td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </main>
  );
}
