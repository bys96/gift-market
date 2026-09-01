"use client";

import Image from "next/image";
import Link from "next/link";
import { useParams } from "next/navigation";
import { type FormEvent, useCallback, useEffect, useRef, useState } from "react";

import Modal from "@/components/common/modal/Modal";
import { getAdminUser, reactivateAdminUser, suspendAdminUser } from "@/lib/admin-api";
import type { AdminUserDetail } from "@/types/admin";
import { resolveImageUrl } from "@/utils/image-url";

const labels = {
  role: { USER: "일반 회원", SELLER: "판매자", ADMIN: "관리자" },
  provider: { GOOGLE: "Google", KAKAO: "Kakao" },
  userStatus: { ACTIVE: "활성", SUSPENDED: "정지", WITHDRAWN: "탈퇴" },
  sellerStatus: { ACTIVE: "운영 중", SUSPENDED: "운영 정지", WITHDRAWN: "탈퇴" },
  applicationStatus: { PENDING: "심사 대기", APPROVED: "승인", REJECTED: "거절" },
} as const;

function formatDateTime(value: string | null) {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit",
  }).format(new Date(value));
}

export default function AdminUserDetailPage() {
  const params = useParams<{ userId: string }>();
  const userId = Number(params.userId);
  const [user, setUser] = useState<AdminUserDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  const [imageFailed, setImageFailed] = useState(false);
  const [action, setAction] = useState<"suspend" | "reactivate" | null>(null);
  const [reason, setReason] = useState("");
  const [actionError, setActionError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const reasonRef = useRef<HTMLTextAreaElement>(null);

  const loadUser = useCallback(async () => {
    if (!Number.isSafeInteger(userId) || userId < 1) {
      setError("올바르지 않은 회원 번호입니다.");
      setIsLoading(false);
      return;
    }
    try {
      setIsLoading(true);
      setError("");
      setImageFailed(false);
      setUser(await getAdminUser(userId));
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "회원 정보를 불러오지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  }, [userId]);

  useEffect(() => {
    // URL의 회원 번호에 맞춰 상세 정보를 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadUser();
  }, [loadUser]);

  const imageUrl = user ? resolveImageUrl(user.profileImageUrl) : null;

  const closeActionModal = () => {
    if (isSubmitting) return;
    setAction(null);
    setReason("");
    setActionError("");
  };

  const submitStatusChange = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const normalizedReason = reason.trim();
    if (!normalizedReason) {
      setActionError("사유를 입력해주세요.");
      return;
    }

    try {
      setIsSubmitting(true);
      setActionError("");
      if (action === "suspend") {
        await suspendAdminUser(userId, { reason: normalizedReason });
      } else if (action === "reactivate") {
        await reactivateAdminUser(userId, { reason: normalizedReason });
      }
      await loadUser();
      setAction(null);
      setReason("");
    } catch (failure) {
      setActionError(failure instanceof Error ? failure.message : "회원 상태를 변경하지 못했습니다.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="admin-user-detail-page">
      <Link href="/admin/users" className="admin-user-back-link">← 회원 목록으로 돌아가기</Link>

      {error && <div className="admin-dashboard-error" role="alert"><span>{error}</span><button type="button" onClick={loadUser}>다시 시도</button></div>}
      {isLoading && !user ? <div className="admin-user-state">회원 정보를 불러오고 있습니다.</div> : user ? (
        <>
          <header className="admin-user-detail-header">
            <div className="admin-user-profile-image">{imageUrl && !imageFailed ? <Image src={imageUrl} alt={`${user.name} 프로필 이미지`} fill sizes="72px" onError={() => setImageFailed(true)} /> : <span>{user.name.slice(0, 1)}</span>}</div>
            <div><p>USER DETAIL · #{user.id}</p><h1>{user.name}</h1><span>{user.email ?? "이메일 정보 없음"}</span></div>
            <div className="admin-user-detail-actions">
              <span className={`admin-user-status admin-user-status-${user.status.toLowerCase()}`}>{labels.userStatus[user.status]}</span>
              {user.role !== "ADMIN" && user.status === "ACTIVE" && <button type="button" className="admin-user-suspend-button" onClick={() => setAction("suspend")}>이용 정지</button>}
              {user.role !== "ADMIN" && user.status === "SUSPENDED" && <button type="button" className="admin-user-reactivate-button" onClick={() => setAction("reactivate")}>정지 해제</button>}
            </div>
          </header>

          <div className="admin-user-detail-grid">
            <section className="admin-user-detail-card admin-user-basic-card"><header><p>BASIC INFORMATION</p><h2>기본 정보</h2></header><dl>
              <div><dt>회원 번호</dt><dd>#{user.id}</dd></div><div><dt>이름</dt><dd>{user.name}</dd></div><div><dt>이메일</dt><dd>{user.email ?? "-"}</dd></div><div><dt>역할</dt><dd>{labels.role[user.role]}</dd></div><div><dt>가입 방식</dt><dd>{labels.provider[user.provider]}</dd></div><div><dt>계정 상태</dt><dd>{labels.userStatus[user.status]}</dd></div><div><dt>가입일</dt><dd>{formatDateTime(user.createdAt)}</dd></div><div><dt>최근 수정일</dt><dd>{formatDateTime(user.updatedAt)}</dd></div>
            </dl></section>

            <section className="admin-user-detail-card"><header><p>ACTIVITY SUMMARY</p><h2>활동 요약</h2></header><dl className="admin-user-activity"><div><dt>주문</dt><dd>{user.activity.orders.toLocaleString("ko-KR")}</dd></div><div><dt>리뷰</dt><dd>{user.activity.reviews.toLocaleString("ko-KR")}</dd></div><div><dt>상품 문의</dt><dd>{user.activity.inquiries.toLocaleString("ko-KR")}</dd></div></dl></section>

            <section className="admin-user-detail-card"><header><p>SELLER INFORMATION</p><h2>판매자 정보</h2></header>{user.seller ? <dl><div><dt>판매자 번호</dt><dd>#{user.seller.sellerId}</dd></div><div><dt>스토어명</dt><dd>{user.seller.storeName}</dd></div><div><dt>상태</dt><dd>{labels.sellerStatus[user.seller.status]}</dd></div><div><dt>생성일</dt><dd>{formatDateTime(user.seller.createdAt)}</dd></div></dl> : <p className="admin-user-detail-empty">등록된 판매자 정보가 없습니다.</p>}</section>

            <section className="admin-user-detail-card"><header><p>SELLER APPLICATION</p><h2>최근 판매자 신청</h2></header>{user.latestSellerApplication ? <dl><div><dt>신청 번호</dt><dd>#{user.latestSellerApplication.applicationId}</dd></div><div><dt>신청 스토어명</dt><dd>{user.latestSellerApplication.storeName}</dd></div><div><dt>상태</dt><dd>{labels.applicationStatus[user.latestSellerApplication.status]}</dd></div><div><dt>신청일</dt><dd>{formatDateTime(user.latestSellerApplication.createdAt)}</dd></div><div><dt>처리일</dt><dd>{formatDateTime(user.latestSellerApplication.reviewedAt)}</dd></div></dl> : <p className="admin-user-detail-empty">판매자 신청 이력이 없습니다.</p>}</section>
          </div>
        </>
      ) : null}

      {action && (
        <Modal
          onClose={closeActionModal}
          overlayClassName="admin-user-modal-backdrop"
          contentClassName="admin-user-modal"
          ariaLabelledBy="admin-user-action-title"
          ariaDescribedBy="admin-user-action-description"
          initialFocusRef={reasonRef}
          closeOnEscape={!isSubmitting}
          closeOnBackdrop={!isSubmitting}
        >
          <form onSubmit={submitStatusChange}>
            <header>
              <h2 id="admin-user-action-title">{action === "suspend" ? "회원 이용 정지" : "회원 정지 해제"}</h2>
              <button type="button" aria-label="닫기" onClick={closeActionModal} disabled={isSubmitting}>×</button>
            </header>
            <p id="admin-user-action-description">
              {action === "suspend"
                ? "정지 사유를 입력해주세요. 정지 후 기존 Access Token 및 Refresh Token을 통한 인증이 차단됩니다."
                : "정지 해제 사유를 입력해주세요."}
            </p>
            <label htmlFor="admin-user-action-reason">사유</label>
            <textarea
              ref={reasonRef}
              id="admin-user-action-reason"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              maxLength={500}
              disabled={isSubmitting}
              required
            />
            <div className="admin-user-reason-meta"><span>{reason.length}/500</span></div>
            {actionError && <p className="admin-user-action-error" role="alert">{actionError}</p>}
            <footer>
              <button type="button" onClick={closeActionModal} disabled={isSubmitting}>취소</button>
              <button type="submit" className={action === "suspend" ? "danger" : "primary"} disabled={isSubmitting || !reason.trim()}>{isSubmitting ? "처리 중..." : "확인"}</button>
            </footer>
          </form>
        </Modal>
      )}
    </main>
  );
}
