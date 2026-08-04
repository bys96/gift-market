"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";

import {
  approveSellerApplication,
  getPendingSellerApplications,
  rejectSellerApplication,
} from "@/lib/seller-api";
import { useAuthStore } from "@/stores/auth-store";
import type { SellerApplication } from "@/types/seller";

function formatDateTime(dateTime: string): string {
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(dateTime));
}

export default function AdminSellerApplicationsPage() {
  const router = useRouter();

  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const [applications, setApplications] = useState<SellerApplication[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const [processingApplicationId, setProcessingApplicationId] = useState<
    number | null
  >(null);

  const [rejectTarget, setRejectTarget] = useState<SellerApplication | null>(
    null,
  );
  const [rejectionReason, setRejectionReason] = useState("");
  const [rejectionError, setRejectionError] = useState("");

  const loadPendingApplications = useCallback(async () => {
    try {
      setIsLoading(true);
      setErrorMessage("");

      const pendingApplications = await getPendingSellerApplications();

      setApplications(pendingApplications);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "판매자 신청 목록을 불러오지 못했습니다.",
      );
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace("/login");
      return;
    }

    if (user.role !== "ADMIN") {
      router.replace("/");
      return;
    }

    loadPendingApplications();
  }, [initialized, isAuthenticated, user, router, loadPendingApplications]);

  const removeApplication = (applicationId: number) => {
    setApplications((currentApplications) =>
      currentApplications.filter(
        (application) => application.id !== applicationId,
      ),
    );
  };

  const handleApprove = async (application: SellerApplication) => {
    const isConfirmed = window.confirm(
      `"${application.storeName}"의 판매자 신청을 승인하시겠습니까?`,
    );

    if (!isConfirmed) {
      return;
    }

    try {
      setProcessingApplicationId(application.id);
      setErrorMessage("");

      await approveSellerApplication(application.id);

      removeApplication(application.id);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "판매자 신청 승인에 실패했습니다.",
      );
    } finally {
      setProcessingApplicationId(null);
    }
  };

  const handleOpenRejectModal = (application: SellerApplication) => {
    setRejectTarget(application);
    setRejectionReason("");
    setRejectionError("");
  };

  const handleCloseRejectModal = () => {
    if (processingApplicationId !== null) {
      return;
    }

    setRejectTarget(null);
    setRejectionReason("");
    setRejectionError("");
  };

  const handleReject = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!rejectTarget) {
      return;
    }

    const trimmedReason = rejectionReason.trim();

    if (!trimmedReason) {
      setRejectionError("거절 사유를 입력해 주세요.");
      return;
    }

    try {
      setProcessingApplicationId(rejectTarget.id);
      setRejectionError("");
      setErrorMessage("");

      await rejectSellerApplication(rejectTarget.id, {
        rejectionReason: trimmedReason,
      });

      removeApplication(rejectTarget.id);
      setRejectTarget(null);
      setRejectionReason("");
    } catch (error) {
      setRejectionError(
        error instanceof Error
          ? error.message
          : "판매자 신청 거절에 실패했습니다.",
      );
    } finally {
      setProcessingApplicationId(null);
    }
  };

  if (!initialized || !isAuthenticated || !user || user.role !== "ADMIN") {
    return null;
  }

  return (
    <main className="admin-seller-main">
      <div className="common-inner">
        <section className="admin-seller-content">
          <header className="admin-seller-header">
            <div>
              <p className="admin-seller-header-label">SELLER MANAGEMENT</p>

              <h1 className="admin-seller-title">판매자 신청 관리</h1>

              <p className="admin-seller-description">
                입점 신청 내용을 확인하고 판매자 승인 여부를 결정합니다.
              </p>
            </div>

            <div className="admin-seller-count">
              <span className="admin-seller-count-label">심사 대기</span>
              <strong className="admin-seller-count-value">
                {applications.length}
              </strong>
              <span className="admin-seller-count-unit">건</span>
            </div>
          </header>

          {errorMessage && (
            <div className="admin-seller-error" role="alert">
              <p>{errorMessage}</p>

              <button
                type="button"
                className="admin-seller-retry-button"
                onClick={loadPendingApplications}
              >
                다시 시도
              </button>
            </div>
          )}

          {isLoading ? (
            <div className="admin-seller-loading">
              <span className="admin-seller-loading-spinner" />
              <p>판매자 신청 목록을 불러오고 있습니다.</p>
            </div>
          ) : applications.length > 0 ? (
            <div className="admin-seller-list">
              {applications.map((application) => {
                const isProcessing = processingApplicationId === application.id;

                return (
                  <article key={application.id} className="admin-seller-card">
                    <div className="admin-seller-card-top">
                      <div className="admin-seller-store">
                        <div className="admin-seller-store-icon">
                          {application.storeName.charAt(0)}
                        </div>

                        <div>
                          <div className="admin-seller-store-title-row">
                            <h2 className="admin-seller-store-name">
                              {application.storeName}
                            </h2>

                            <span className="admin-seller-status-badge">
                              심사 대기
                            </span>
                          </div>

                          <p className="admin-seller-application-date">
                            신청일 {formatDateTime(application.createdAt)}
                          </p>
                        </div>
                      </div>

                      <span className="admin-seller-application-number">
                        신청번호 #{application.id}
                      </span>
                    </div>

                    <div className="admin-seller-card-body">
                      <dl className="admin-seller-applicant-info">
                        <div className="admin-seller-info-item">
                          <dt>신청자</dt>
                          <dd>{application.userName}</dd>
                        </div>

                        <div className="admin-seller-info-item">
                          <dt>이메일</dt>
                          <dd>{application.userEmail}</dd>
                        </div>
                      </dl>

                      <div className="admin-seller-introduction">
                        <p className="admin-seller-introduction-label">
                          스토어 소개
                        </p>

                        <p className="admin-seller-introduction-content">
                          {application.introduction}
                        </p>
                      </div>
                    </div>

                    <div className="admin-seller-card-actions">
                      <button
                        type="button"
                        className="admin-seller-reject-button"
                        disabled={isProcessing}
                        onClick={() => handleOpenRejectModal(application)}
                      >
                        거절
                      </button>

                      <button
                        type="button"
                        className="admin-seller-approve-button"
                        disabled={isProcessing}
                        onClick={() => handleApprove(application)}
                      >
                        {isProcessing ? "처리 중..." : "판매자 승인"}
                      </button>
                    </div>
                  </article>
                );
              })}
            </div>
          ) : (
            <div className="admin-seller-empty">
              <div className="admin-seller-empty-icon">✓</div>

              <h2 className="admin-seller-empty-title">
                대기 중인 판매자 신청이 없습니다.
              </h2>

              <p className="admin-seller-empty-description">
                새로운 입점 신청이 접수되면 이곳에 표시됩니다.
              </p>
            </div>
          )}
        </section>
      </div>

      {rejectTarget && (
        <div
          className="admin-seller-modal-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) {
              handleCloseRejectModal();
            }
          }}
        >
          <section
            className="admin-seller-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="seller-reject-modal-title"
          >
            <div className="admin-seller-modal-header">
              <div>
                <p className="admin-seller-modal-label">APPLICATION REJECT</p>

                <h2
                  id="seller-reject-modal-title"
                  className="admin-seller-modal-title"
                >
                  판매자 신청 거절
                </h2>
              </div>

              <button
                type="button"
                className="admin-seller-modal-close"
                aria-label="거절 모달 닫기"
                disabled={processingApplicationId !== null}
                onClick={handleCloseRejectModal}
              >
                ×
              </button>
            </div>

            <p className="admin-seller-modal-description">
              <strong>{rejectTarget.storeName}</strong> 신청을 거절하는 이유를
              입력해 주세요. 작성한 내용은 신청자에게 표시됩니다.
            </p>

            <form className="admin-seller-reject-form" onSubmit={handleReject}>
              <label
                className="admin-seller-rejection-label"
                htmlFor="rejectionReason"
              >
                거절 사유
              </label>

              <textarea
                id="rejectionReason"
                className="admin-seller-rejection-textarea"
                value={rejectionReason}
                maxLength={500}
                placeholder="예: 스토어 소개가 부족합니다. 판매 상품과 운영 계획을 구체적으로 작성해 다시 신청해 주세요."
                disabled={processingApplicationId !== null}
                onChange={(event) => {
                  setRejectionReason(event.target.value);

                  if (rejectionError) {
                    setRejectionError("");
                  }
                }}
              />

              <div className="admin-seller-rejection-meta">
                <p className="admin-seller-rejection-error">{rejectionError}</p>

                <span>{rejectionReason.length}/500</span>
              </div>

              <div className="admin-seller-modal-actions">
                <button
                  type="button"
                  className="admin-seller-modal-cancel-button"
                  disabled={processingApplicationId !== null}
                  onClick={handleCloseRejectModal}
                >
                  취소
                </button>

                <button
                  type="submit"
                  className="admin-seller-modal-submit-button"
                  disabled={processingApplicationId !== null}
                >
                  {processingApplicationId !== null ? "처리 중..." : "거절하기"}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </main>
  );
}
