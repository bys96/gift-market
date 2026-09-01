"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { type FormEvent, useCallback, useEffect, useRef, useState } from "react";

import Modal from "@/components/common/modal/Modal";
import { getAdminSeller, reactivateAdminSellerSales, suspendAdminSellerSales } from "@/lib/admin-api";
import type { AdminSellerDetail } from "@/types/admin";

const labels = {
  sellerStatus: { ACTIVE: "정상", SALES_SUSPENDED: "판매 정지", SUSPENDED: "계정 정지", WITHDRAWN: "탈퇴" },
  userStatus: { ACTIVE: "활성", SUSPENDED: "정지", WITHDRAWN: "탈퇴" },
  role: { USER: "일반 회원", SELLER: "판매자", ADMIN: "관리자" },
  provider: { GOOGLE: "Google", KAKAO: "Kakao" },
  application: { PENDING: "심사 대기", APPROVED: "승인", REJECTED: "거절" },
  order: { PENDING_PAYMENT: "결제 대기", PAID: "결제 완료", PREPARING: "상품 준비", SHIPPED: "배송 중", DELIVERED: "배송 완료", CANCELLED: "취소" },
} as const;

function formatDateTime(value: string | null) {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

export default function AdminSellerDetailPage() {
  const params = useParams<{ sellerId: string }>();
  const sellerId = Number(params.sellerId);
  const [seller, setSeller] = useState<AdminSellerDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [action, setAction] = useState<"suspend" | "reactivate" | null>(null);
  const [reason, setReason] = useState("");
  const [actionError, setActionError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const reasonRef = useRef<HTMLTextAreaElement>(null);

  const loadSeller = useCallback(async () => {
    if (!Number.isSafeInteger(sellerId) || sellerId < 1) {
      setError("올바르지 않은 판매자 번호입니다.");
      setIsLoading(false);
      return;
    }
    try {
      setIsLoading(true);
      setError("");
      setSeller(await getAdminSeller(sellerId));
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "판매자 정보를 불러오지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  }, [sellerId]);

  useEffect(() => {
    // URL의 판매자 번호에 맞춰 상세 정보를 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadSeller();
  }, [loadSeller]);

  const closeModal = () => {
    if (isSubmitting) return;
    setAction(null);
    setReason("");
    setActionError("");
  };

  const submitAction = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const normalizedReason = reason.trim();
    if (!normalizedReason) { setActionError("사유를 입력해주세요."); return; }
    try {
      setIsSubmitting(true);
      setActionError("");
      if (action === "suspend") await suspendAdminSellerSales(sellerId, { reason: normalizedReason });
      else if (action === "reactivate") await reactivateAdminSellerSales(sellerId, { reason: normalizedReason });
      await loadSeller();
      setAction(null);
      setReason("");
    } catch (failure) {
      setActionError(failure instanceof Error ? failure.message : "판매자 판매 상태를 변경하지 못했습니다.");
    } finally { setIsSubmitting(false); }
  };

  return <main className="admin-user-detail-page admin-seller-detail-page">
    <Link href="/admin/sellers" className="admin-user-back-link">← 판매자 목록으로 돌아가기</Link>
    {error && <div className="admin-dashboard-error" role="alert"><span>{error}</span><button type="button" onClick={loadSeller}>다시 시도</button></div>}
    {isLoading && !seller ? <div className="admin-user-state">판매자 정보를 불러오고 있습니다.</div> : seller ? <>
      <header className="admin-user-detail-header admin-seller-detail-header"><div className="admin-seller-detail-mark">{seller.storeName.slice(0, 1)}</div><div><p>SELLER DETAIL · #{seller.sellerId}</p><h1>{seller.storeName}</h1><span>{seller.introduction || "등록된 스토어 소개가 없습니다."}</span></div><div className="admin-user-detail-actions"><span className={`admin-user-status admin-user-status-${seller.status.toLowerCase()}`}>{labels.sellerStatus[seller.status]}</span>{seller.status === "ACTIVE" && <button type="button" className="admin-user-suspend-button" onClick={() => setAction("suspend")}>판매 정지</button>}{seller.status === "SALES_SUSPENDED" && <button type="button" className="admin-user-reactivate-button" onClick={() => setAction("reactivate")}>판매 정지 해제</button>}</div></header>
      <div className="admin-user-detail-grid">
        <section className="admin-user-detail-card"><header><p>SELLER INFORMATION</p><h2>기본 정보</h2></header><dl><div><dt>판매자 번호</dt><dd>#{seller.sellerId}</dd></div><div><dt>스토어명</dt><dd>{seller.storeName}</dd></div><div><dt>상태</dt><dd>{labels.sellerStatus[seller.status]}</dd></div><div><dt>승인일</dt><dd>{formatDateTime(seller.approvedAt)}</dd></div><div><dt>생성일</dt><dd>{formatDateTime(seller.createdAt)}</dd></div><div><dt>최근 수정일</dt><dd>{formatDateTime(seller.updatedAt)}</dd></div></dl></section>
        <section className="admin-user-detail-card"><header><p>OWNER ACCOUNT</p><h2>소유 회원</h2></header><dl><div><dt>회원</dt><dd>{seller.owner.name} · #{seller.owner.userId}</dd></div><div><dt>이메일</dt><dd>{seller.owner.email ?? "-"}</dd></div><div><dt>역할</dt><dd>{labels.role[seller.owner.role]}</dd></div><div><dt>가입 방식</dt><dd>{labels.provider[seller.owner.provider]}</dd></div><div><dt>회원 상태</dt><dd>{labels.userStatus[seller.owner.status]}</dd></div><div><dt>가입일</dt><dd>{formatDateTime(seller.owner.createdAt)}</dd></div></dl><Link href={`/admin/users/${seller.owner.userId}`} className="admin-seller-owner-button">회원 상세 보기 →</Link></section>
        <section className="admin-user-detail-card"><header><p>ACTIVITY SUMMARY</p><h2>활동 요약</h2></header><dl className="admin-user-activity"><div><dt>전체 상품</dt><dd>{seller.activity.totalProducts.toLocaleString("ko-KR")}</dd></div><div><dt>판매중 상품</dt><dd>{seller.activity.onSaleProducts.toLocaleString("ko-KR")}</dd></div><div><dt>전체 주문</dt><dd>{seller.activity.totalOrders.toLocaleString("ko-KR")}</dd></div></dl></section>
        <section className="admin-user-detail-card"><header><p>SELLER APPLICATION</p><h2>판매자 신청 정보</h2></header>{seller.sellerApplication ? <dl><div><dt>신청 번호</dt><dd>#{seller.sellerApplication.applicationId}</dd></div><div><dt>상태</dt><dd>{labels.application[seller.sellerApplication.status]}</dd></div><div><dt>신청일</dt><dd>{formatDateTime(seller.sellerApplication.appliedAt)}</dd></div><div><dt>처리일</dt><dd>{formatDateTime(seller.sellerApplication.reviewedAt)}</dd></div><div><dt>처리 관리자 ID</dt><dd>{seller.sellerApplication.reviewedBy ? `#${seller.sellerApplication.reviewedBy}` : "-"}</dd></div></dl> : <p className="admin-user-detail-empty">판매자 신청 이력이 없습니다. 기존 데이터에서 직접 생성된 판매자일 수 있습니다.</p>}</section>
        <section className="admin-user-detail-card admin-seller-orders-card"><header><p>RECENT ORDERS</p><h2>최근 주문</h2><span>최신 5건</span></header>{seller.recentOrders.length ? <div className="admin-seller-recent-orders">{seller.recentOrders.map((order) => <article key={order.sellerOrderId}><div><strong>{order.orderNumber}</strong><span>판매자 주문 #{order.sellerOrderId} · {formatDateTime(order.orderedAt)}</span></div><div><strong>{order.totalProductAmount.toLocaleString("ko-KR")}원</strong><span>{labels.order[order.status]}</span></div></article>)}</div> : <p className="admin-user-detail-empty">최근 주문이 없습니다.</p>}</section>
      </div>
    </> : null}
    {action && <Modal onClose={closeModal} overlayClassName="admin-user-modal-backdrop" contentClassName="admin-user-modal" ariaLabelledBy="admin-seller-action-title" ariaDescribedBy="admin-seller-action-description" initialFocusRef={reasonRef} closeOnEscape={!isSubmitting} closeOnBackdrop={!isSubmitting}>
      <form onSubmit={submitAction}>
        <header><h2 id="admin-seller-action-title">{action === "suspend" ? "판매자 판매 정지" : "판매자 판매 정지 해제"}</h2><button type="button" aria-label="닫기" onClick={closeModal} disabled={isSubmitting}>×</button></header>
        <p id="admin-seller-action-description">{action === "suspend" ? "판매 정지 사유를 입력해주세요. 적용 즉시 신규 상품 판매 및 신규 주문이 차단됩니다. 기존 주문 및 클레임 처리는 계속할 수 있습니다." : "판매 정지 해제 사유를 입력해주세요. 해제 후 판매자의 기존 상품 상태에 따라 Buyer 판매가 다시 가능해집니다."}</p>
        <label htmlFor="admin-seller-action-reason">사유</label>
        <textarea ref={reasonRef} id="admin-seller-action-reason" value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} disabled={isSubmitting} required />
        <div className="admin-user-reason-meta"><span>{reason.length}/500</span></div>
        {actionError && <p className="admin-user-action-error" role="alert">{actionError}</p>}
        <footer><button type="button" onClick={closeModal} disabled={isSubmitting}>취소</button><button type="submit" className={action === "suspend" ? "danger" : "primary"} disabled={isSubmitting || !reason.trim()}>{isSubmitting ? "처리 중..." : "확인"}</button></footer>
      </form>
    </Modal>}
  </main>;
}
