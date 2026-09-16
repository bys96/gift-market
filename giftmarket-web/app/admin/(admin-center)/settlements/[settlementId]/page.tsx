"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { type FormEvent, useCallback, useEffect, useRef, useState } from "react";

import Modal from "@/components/common/modal/Modal";
import {
  confirmAdminSettlement,
  getAdminSettlement,
  holdAdminSettlement,
  releaseAdminSettlement,
} from "@/lib/admin-settlement-api";
import { settlementDate, settlementSignedWon, settlementWon } from "@/lib/admin-settlement-format";
import {
  ADMIN_SETTLEMENT_LEDGER_LABEL,
  ADMIN_SETTLEMENT_SOURCE_LABEL,
  ADMIN_SETTLEMENT_STATUS_LABEL,
  type AdminSettlementDetail,
} from "@/types/admin-settlement";

type SettlementAction = "hold" | "release" | "confirm";

export default function AdminSettlementDetailPage() {
  const settlementId = Number(useParams<{ settlementId: string }>().settlementId);
  const [detail, setDetail] = useState<AdminSettlementDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [action, setAction] = useState<SettlementAction | null>(null);
  const [reason, setReason] = useState("");
  const [actionError, setActionError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState("");
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);

  const load = useCallback(async () => {
    if (!Number.isSafeInteger(settlementId) || settlementId <= 0) {
      setError("올바르지 않은 정산 번호입니다.");
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      setError("");
      setDetail(await getAdminSettlement(settlementId));
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "정산 상세를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [settlementId]);

  useEffect(() => {
    // URL의 정산 ID에 맞춰 상세와 원장을 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  const openAction = (next: SettlementAction) => {
    setAction(next);
    setReason("");
    setActionError("");
  };

  const closeAction = () => {
    if (submitting) return;
    setAction(null);
    setReason("");
    setActionError("");
  };

  const submitAction = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitting || !action || !detail) return;
    const normalizedReason = reason.trim();
    if (action === "hold" && !normalizedReason) {
      setActionError("보류 사유를 입력해주세요.");
      return;
    }
    try {
      setSubmitting(true);
      setActionError("");
      const updated = action === "hold"
        ? await holdAdminSettlement(settlementId, normalizedReason)
        : action === "release"
          ? await releaseAdminSettlement(settlementId)
          : await confirmAdminSettlement(settlementId);
      setDetail({ ...detail, settlement: updated });
      setSuccess(action === "hold" ? "정산을 보류했습니다."
        : action === "release" ? "정산 보류를 해제했습니다." : "정산 금액을 확정했습니다.");
      setAction(null);
      setReason("");
      void load();
    } catch (failure) {
      setActionError(failure instanceof Error ? failure.message : "정산 상태를 변경하지 못했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  const settlement = detail?.settlement;

  return <main className="admin-user-detail-page admin-settlement-detail-page">
    <Link href="/admin/settlements" className="admin-user-back-link">← 정산 목록으로 돌아가기</Link>
    {error && <div className="admin-dashboard-error" role="alert"><span>{error}</span><button type="button" onClick={() => void load()}>다시 시도</button></div>}
    {success && <div className="admin-settlement-result" role="status">{success}</div>}
    {loading && !detail ? <div className="admin-user-state">정산 상세를 불러오고 있습니다.</div> : detail && settlement ? <>
      <header className="admin-user-detail-header admin-settlement-detail-header">
        <div><p>SETTLEMENT DETAIL · #{settlement.settlementId}</p><h1>{settlement.settlementNumber}</h1><span><Link href={`/admin/sellers/${settlement.sellerId}`}>{settlement.storeName}</Link> · 판매자 #{settlement.sellerId}</span></div>
        <div className="admin-settlement-detail-actions">
          <span className={`admin-settlement-status is-${settlement.status.toLowerCase()}`}>{ADMIN_SETTLEMENT_STATUS_LABEL[settlement.status]}</span>
          {settlement.status === "READY" && <><button type="button" onClick={() => openAction("hold")}>정산 보류</button><button type="button" className="primary" onClick={() => openAction("confirm")}>정산 확정</button></>}
          {settlement.status === "ON_HOLD" && <button type="button" className="primary" onClick={() => openAction("release")}>보류 해제</button>}
        </div>
      </header>

      <p className="admin-settlement-meaning">정산 확정은 금액과 원장 구성이 확정된 상태입니다. 실제 송금 상태를 뜻하지 않습니다.</p>

      <div className="admin-user-detail-grid">
        <section className="admin-user-detail-card"><header><p>SETTLEMENT</p><h2>정산 기본 정보</h2></header><dl>
          <div><dt>정산번호</dt><dd>{settlement.settlementNumber}</dd></div>
          <div><dt>상태</dt><dd>{ADMIN_SETTLEMENT_STATUS_LABEL[settlement.status]}</dd></div>
          <div><dt>기간 시작 (포함)</dt><dd>{settlementDate(settlement.periodStart, true)}</dd></div>
          <div><dt>기간 종료 (미포함)</dt><dd>{settlementDate(settlement.periodEnd, true)}</dd></div>
          <div><dt>통화</dt><dd>{settlement.currency}</dd></div>
          <div><dt>원장 건수</dt><dd>{settlement.ledgerEntryCount.toLocaleString("ko-KR")}건</dd></div>
          <div><dt>생성일</dt><dd>{settlementDate(settlement.createdAt, true)}</dd></div>
          <div><dt>확정일</dt><dd>{settlementDate(settlement.confirmedAt, true)}</dd></div>
        </dl></section>
        <section className="admin-user-detail-card"><header><p>AMOUNT</p><h2>정산 금액 구성</h2></header><dl>
          <div><dt>상품 판매금액</dt><dd>{settlementWon(settlement.productSalesAmount)}</dd></div>
          <div><dt>배송비 매출</dt><dd>{settlementWon(settlement.shippingSalesAmount)}</dd></div>
          <div><dt>취소 환불</dt><dd>{settlementSignedWon(-settlement.cancellationAmount)}</dd></div>
          <div><dt>반품 환불</dt><dd>{settlementSignedWon(-settlement.returnAmount)}</dd></div>
          <div><dt>순 수수료</dt><dd>{settlementSignedWon(-settlement.commissionAmount)}</dd></div>
          <div><dt>조정금액</dt><dd>{settlementSignedWon(settlement.adjustmentAmount)}</dd></div>
          <div className="admin-settlement-total"><dt>최종 정산금액</dt><dd>{settlementWon(settlement.settlementAmount)}</dd></div>
        </dl></section>
        <section className="admin-user-detail-card admin-settlement-audit-card"><header><p>AUDIT</p><h2>상태 변경 이력</h2></header><dl>
          <div><dt>보류 사유</dt><dd>{detail.holdReason || "-"}</dd></div>
          <div><dt>보류 시각</dt><dd>{settlementDate(detail.heldAt, true)}</dd></div>
          <div><dt>보류 관리자 ID</dt><dd>{detail.heldByAdminUserId ? `#${detail.heldByAdminUserId}` : "-"}</dd></div>
          <div><dt>보류 해제 시각</dt><dd>{settlementDate(detail.holdReleasedAt, true)}</dd></div>
          <div><dt>해제 관리자 ID</dt><dd>{detail.holdReleasedByAdminUserId ? `#${detail.holdReleasedByAdminUserId}` : "-"}</dd></div>
          <div><dt>확정 관리자 ID</dt><dd>{detail.confirmedByAdminUserId ? `#${detail.confirmedByAdminUserId}` : "-"}</dd></div>
        </dl></section>
        <section className="admin-user-detail-card admin-settlement-ledger-card"><header><p>LEDGER</p><h2>포함된 정산 원장</h2></header>
          {detail.ledgerEntries.length === 0 ? <p className="admin-user-detail-empty">표시할 원장이 없습니다.</p> : <div className="admin-settlement-ledger-wrap"><table className="admin-settlement-ledger-table"><thead><tr><th>발생일</th><th>유형</th><th>판매자 주문</th><th>금액</th><th>정산 가능일</th><th>근거</th><th>사유·수수료 기준</th></tr></thead><tbody>{detail.ledgerEntries.map((entry) => <tr key={entry.id}>
            <td data-label="발생일">{settlementDate(entry.occurredAt, true)}</td>
            <td data-label="유형"><strong>{ADMIN_SETTLEMENT_LEDGER_LABEL[entry.type]}</strong><small>원장 #{entry.id}</small></td>
            <td data-label="판매자 주문">#{entry.sellerOrderId}</td>
            <td data-label="금액" className={entry.amount < 0 ? "is-negative" : "is-positive"}>{settlementSignedWon(entry.amount)}</td>
            <td data-label="정산 가능일">{entry.eligibleAt ? settlementDate(entry.eligibleAt, true) : "미확정"}</td>
            <td data-label="근거">{ADMIN_SETTLEMENT_SOURCE_LABEL[entry.sourceType]} #{entry.sourceId}<small>{entry.sourceDetailKey}</small></td>
            <td data-label="사유·수수료 기준">{entry.reason || "-"}{entry.commissionRateBps !== null && <small>수수료율 {entry.commissionRateBps}bp · 기준 {settlementWon(entry.commissionBaseAmount ?? 0)}</small>}{entry.adminUserId && <small>관리자 #{entry.adminUserId}</small>}{entry.reversalOfEntryId && <small>원본 원장 #{entry.reversalOfEntryId}</small>}</td>
          </tr>)}</tbody></table></div>}
        </section>
      </div>
    </> : null}

    {action && <Modal onClose={closeAction} overlayClassName="admin-user-modal-backdrop" contentClassName="admin-user-modal" ariaLabelledBy="admin-settlement-action-title" ariaDescribedBy="admin-settlement-action-description" initialFocusRef={action === "hold" ? reasonRef : confirmRef} closeOnEscape={!submitting} closeOnBackdrop={!submitting}>
      <form onSubmit={submitAction}>
        <header><h2 id="admin-settlement-action-title">{action === "hold" ? "정산 보류" : action === "release" ? "정산 보류 해제" : "정산 확정"}</h2><button type="button" aria-label="닫기" onClick={closeAction} disabled={submitting}>×</button></header>
        <p id="admin-settlement-action-description">{action === "hold" ? "보류 사유를 기록합니다. 원장 구성과 정산금액은 변경되지 않습니다." : action === "release" ? "보류를 해제하고 정산 대기 상태로 되돌립니다. 계속하시겠습니까?" : "정산 금액과 포함 원장을 확정합니다. 확정 후에는 되돌릴 수 없습니다. 실제 송금은 수행되지 않습니다."}</p>
        {action === "hold" && <><label htmlFor="admin-settlement-hold-reason">보류 사유</label><textarea ref={reasonRef} id="admin-settlement-hold-reason" value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} required disabled={submitting} /><div className="admin-user-reason-meta">{reason.length}/500</div></>}
        {actionError && <p className="admin-user-action-error" role="alert">{actionError}</p>}
        <footer><button type="button" onClick={closeAction} disabled={submitting}>취소</button><button ref={confirmRef} type="submit" className={action === "confirm" ? "danger" : "primary"} disabled={submitting || action === "hold" && !reason.trim()}>{submitting ? "처리 중..." : "확인"}</button></footer>
      </form>
    </Modal>}
  </main>;
}
