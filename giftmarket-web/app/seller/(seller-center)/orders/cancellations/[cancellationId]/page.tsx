"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import {
  approveSellerOrderCancellation,
  getSellerOrderCancellation,
  rejectSellerOrderCancellation,
} from "@/lib/seller-order-cancellation-api";
import { useAuthStore } from "@/stores/auth-store";
import {
  SELLER_ORDER_CANCELLATION_STATUS_LABEL,
  type SellerOrderCancellation,
} from "@/types/seller-order-cancellation";

const MAX_REJECT_REASON_LENGTH = 500;

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

function friendlyActionError(error: unknown) {
  const message = error instanceof Error ? error.message : "";
  if (message.includes("로그인")) return message;
  return "취소 요청 상태가 변경되었거나 처리하지 못했습니다. 최신 상태를 확인해주세요.";
}

export default function SellerOrderCancellationDetailPage() {
  const params = useParams<{ cancellationId: string }>();
  const router = useRouter();
  const cancellationId = Number(params.cancellationId);
  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [cancellation, setCancellation] = useState<SellerOrderCancellation | null>(null);
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [confirmApproval, setConfirmApproval] = useState(false);
  const [showRejectForm, setShowRejectForm] = useState(false);
  const [rejectReason, setRejectReason] = useState("");

  const loadCancellation = useCallback(async (quiet = false) => {
    await Promise.resolve();
    if (!Number.isInteger(cancellationId) || cancellationId <= 0) {
      setError("올바른 취소 요청 정보가 아닙니다.");
      setLoading(false);
      return;
    }
    try {
      if (!quiet) setLoading(true);
      setError("");
      setCancellation(await getSellerOrderCancellation(cancellationId));
    } catch {
      setError("취소 요청을 불러오지 못했습니다. 접근 권한이나 요청 상태를 확인해주세요.");
    } finally {
      if (!quiet) setLoading(false);
    }
  }, [cancellationId]);

  useEffect(() => {
    if (!initialized) return;
    if (!isAuthenticated || !user) {
      router.replace("/login");
      return;
    }
    if (user.role !== "SELLER" && user.role !== "ADMIN") {
      router.replace("/seller");
      return;
    }
    const requestId = window.setTimeout(() => void loadCancellation(), 0);
    return () => window.clearTimeout(requestId);
  }, [initialized, isAuthenticated, loadCancellation, router, user]);

  const applyResult = (result: SellerOrderCancellation) => {
    setCancellation(result);
    setConfirmApproval(false);
    setShowRejectForm(false);
    setRejectReason("");
  };

  const approve = async () => {
    if (processing || cancellation?.status !== "REQUESTED") return;
    try {
      setProcessing(true);
      setActionError("");
      applyResult(await approveSellerOrderCancellation(cancellationId));
    } catch (actionFailure) {
      setActionError(friendlyActionError(actionFailure));
      await loadCancellation(true);
    } finally {
      setProcessing(false);
    }
  };

  const reject = async () => {
    if (processing || cancellation?.status !== "REQUESTED") return;
    const reason = rejectReason.trim();
    if (!reason) {
      setActionError("취소 거절 사유를 입력해주세요.");
      return;
    }
    if (reason.length > MAX_REJECT_REASON_LENGTH) {
      setActionError("취소 거절 사유는 500자 이내로 입력해주세요.");
      return;
    }
    try {
      setProcessing(true);
      setActionError("");
      applyResult(await rejectSellerOrderCancellation(cancellationId, reason));
    } catch (actionFailure) {
      setActionError(friendlyActionError(actionFailure));
      await loadCancellation(true);
    } finally {
      setProcessing(false);
    }
  };

  if (!initialized || !isAuthenticated || !user || loading) {
    return <div className="seller-orders-auth-loading">취소 요청을 확인하고 있습니다.</div>;
  }

  if (error || !cancellation) {
    return (
      <main className="seller-orders-page"><div className="common-inner seller-orders-container">
        <div className="seller-orders-state seller-orders-state-error"><p>{error || "취소 요청을 확인할 수 없습니다."}</p><button type="button" onClick={() => void loadCancellation()}>다시 시도</button><Link href="/seller/orders/cancellations">목록으로</Link></div>
      </div></main>
    );
  }

  return (
    <main className="seller-orders-page seller-cancellations-page">
      <div className="common-inner seller-orders-container seller-order-detail-container">
        <header className="seller-order-detail-header">
          <div><p>CANCELLATION DETAIL</p><h1>취소 요청 상세</h1><span>{cancellation.orderNumber} · 요청 #{cancellation.cancellationId}</span></div>
          <Link href="/seller/orders/cancellations">목록으로</Link>
        </header>

        <section className="seller-order-detail-summary seller-cancellation-summary">
          <div><span>요청 상태</span><strong className={`seller-cancellation-status seller-cancellation-status-${cancellation.status.toLowerCase()}`}>{SELLER_ORDER_CANCELLATION_STATUS_LABEL[cancellation.status]}</strong></div>
          <div><span>요청일시</span><strong>{formatDate(cancellation.requestedAt)}</strong></div>
          <div><span>수령인</span><strong>{cancellation.recipientName}</strong></div>
        </section>

        {cancellation.status === "PROCESSING" && <p className="seller-cancellation-state-message processing">환불 처리 결과를 확인 중입니다. 처리는 자동으로 복구되며 새로고침 또는 재진입 시 최신 상태를 확인할 수 있습니다.</p>}
        {cancellation.status === "FAILED" && <p className="seller-cancellation-state-message failed">환불 처리에 실패했습니다. 이 화면에서는 다시 승인할 수 없습니다.</p>}

        <section className="seller-order-detail-section">
          <h2>취소 요청 정보</h2>
          <dl className="seller-order-detail-info-list seller-cancellation-info-list">
            <div><dt>주문번호</dt><dd>{cancellation.orderNumber}</dd></div>
            <div><dt>취소 사유</dt><dd>{cancellation.reason}</dd></div>
            <div><dt>환불 처리 시작</dt><dd>{formatDate(cancellation.processingAt)}</dd></div>
            <div><dt>거절 처리</dt><dd>{formatDate(cancellation.rejectedAt)}</dd></div>
            {cancellation.rejectedReason && <div><dt>거절 사유</dt><dd>{cancellation.rejectedReason}</dd></div>}
          </dl>
        </section>

        <section className="seller-order-detail-section">
          <h2>취소 대상 상품</h2>
          <div className="seller-cancellation-items">
            {cancellation.items.map((item) => (
              <article key={item.orderItemId} className="seller-cancellation-item">
                <div><strong>{item.productName}</strong>{item.optionSnapshot && <span>{item.optionSnapshot}</span>}</div>
                <dl><div><dt>원 주문수량</dt><dd>{item.orderedQuantity}개</dd></div><div><dt>기취소수량</dt><dd>{item.canceledQuantity}개</dd></div><div><dt>이번 요청수량</dt><dd><strong>{item.requestedQuantity}개</strong></dd></div></dl>
              </article>
            ))}
          </div>
        </section>

        <section className="seller-order-detail-section seller-order-action-section seller-cancellation-action-section">
          <h2>취소 요청 처리</h2>
          {cancellation.status === "REQUESTED" && (
            <>
              <p className="seller-cancellation-shipping-notice">이 요청을 처리하기 전에는 해당 판매자 주문의 배송 시작이 제한됩니다.</p>
              {!confirmApproval && !showRejectForm && (
                <div className="seller-cancellation-action-buttons"><button type="button" disabled={processing} onClick={() => setConfirmApproval(true)}>취소 승인</button><button type="button" className="danger-secondary" disabled={processing} onClick={() => setShowRejectForm(true)}>취소 거절</button></div>
              )}
              {confirmApproval && (
                <div className="seller-cancellation-confirm-box"><strong>취소를 승인하면 구매자 환불 처리가 시작됩니다.</strong><p>대상 상품과 요청 수량을 다시 확인해주세요.</p><div><button type="button" className="secondary" disabled={processing} onClick={() => setConfirmApproval(false)}>돌아가기</button><button type="button" disabled={processing} onClick={() => void approve()}>{processing ? "환불 처리 중..." : "승인하고 환불 시작"}</button></div></div>
              )}
              {showRejectForm && (
                <div className="seller-cancellation-reject-form"><label htmlFor="rejectReason">거절 사유 <span>{rejectReason.length}/{MAX_REJECT_REASON_LENGTH}</span></label><textarea id="rejectReason" value={rejectReason} maxLength={MAX_REJECT_REASON_LENGTH} disabled={processing} placeholder="구매자에게 전달할 거절 사유를 입력해주세요." onChange={(event) => setRejectReason(event.target.value)} /><div><button type="button" className="secondary" disabled={processing} onClick={() => { setShowRejectForm(false); setRejectReason(""); setActionError(""); }}>돌아가기</button><button type="button" className="danger" disabled={processing || !rejectReason.trim()} onClick={() => void reject()}>{processing ? "처리 중..." : "거절 확정"}</button></div></div>
              )}
            </>
          )}
          {cancellation.status === "PROCESSING" && <p className="seller-order-action-notice">환불 처리 결과를 확인 중입니다. 승인 또는 거절을 다시 실행할 수 없습니다.</p>}
          {cancellation.status === "COMPLETED" && <p className="seller-order-action-notice success">취소와 환불 처리가 완료되었습니다.</p>}
          {cancellation.status === "REJECTED" && <p className="seller-order-action-notice cancelled">거절 처리된 취소 요청입니다.</p>}
          {cancellation.status === "FAILED" && <p className="seller-order-action-notice cancelled">환불 처리에 실패했습니다. 별도 운영 확인이 필요합니다.</p>}
          {actionError && <p className="seller-order-action-error">{actionError}</p>}
        </section>
      </div>
    </main>
  );
}
