"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";

import {
  createSellerApplication,
  getMyLatestSellerApplication,
} from "@/lib/seller-api";
import { useAuthStore } from "@/stores/auth-store";
import type { SellerApplication } from "@/types/seller";

const STORE_NAME_MAX_LENGTH = 100;
const INTRODUCTION_MAX_LENGTH = 1000;

export default function SellerApplyPage() {
  const router = useRouter();

  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const [latestApplication, setLatestApplication] =
    useState<SellerApplication | null>(null);

  const [storeName, setStoreName] = useState("");
  const [introduction, setIntroduction] = useState("");

  const [isChecking, setIsChecking] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [pageError, setPageError] = useState("");
  const [storeNameError, setStoreNameError] = useState("");

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace("/login");
      return;
    }

    if (user.role === "ADMIN") {
      router.replace("/admin/seller-applications");
      return;
    }

    if (user.role === "SELLER") {
      router.replace("/seller/dashboard");
      return;
    }

    const checkApplication = async () => {
      try {
        setIsChecking(true);
        setPageError("");

        const application = await getMyLatestSellerApplication();

        if (!application) {
          return;
        }

        if (application.status === "PENDING") {
          router.replace("/seller/application");
          return;
        }

        if (application.status === "APPROVED") {
          router.replace("/seller/dashboard");
          return;
        }

        if (application.status === "REJECTED") {
          setLatestApplication(application);
          setStoreName(application.storeName);
          setIntroduction(application.introduction ?? "");
        }
      } catch (error) {
        setPageError(
          error instanceof Error
            ? error.message
            : "판매자 신청 정보를 불러오지 못했습니다.",
        );
      } finally {
        setIsChecking(false);
      }
    };

    checkApplication();
  }, [initialized, isAuthenticated, user, router]);

  const validateForm = (): boolean => {
    const trimmedStoreName = storeName.trim();

    if (!trimmedStoreName) {
      setStoreNameError("스토어명을 입력해 주세요.");
      return false;
    }

    if (trimmedStoreName.length > STORE_NAME_MAX_LENGTH) {
      setStoreNameError(
        `스토어명은 ${STORE_NAME_MAX_LENGTH}자 이하로 입력해 주세요.`,
      );
      return false;
    }

    setStoreNameError("");

    return true;
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!validateForm() || isSubmitting) {
      return;
    }

    try {
      setIsSubmitting(true);
      setPageError("");

      await createSellerApplication({
        storeName: storeName.trim(),
        introduction: introduction.trim(),
      });

      router.replace("/seller/application");
    } catch (error) {
      setPageError(
        error instanceof Error ? error.message : "판매자 신청에 실패했습니다.",
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  if (
    !initialized ||
    isChecking ||
    !isAuthenticated ||
    !user ||
    user.role !== "USER"
  ) {
    return (
      <main className="seller-apply-page">
        <div className="common-inner">
          <div className="seller-apply-loading">
            <span className="seller-apply-loading-spinner" />

            <p>판매자 신청 정보를 확인하고 있습니다.</p>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="seller-apply-page">
      <div className="common-inner">
        <div className="seller-apply-container">
          <header className="seller-apply-header">
            <p className="seller-apply-header-label">SELLER APPLICATION</p>

            <h1 className="seller-apply-title">
              Gift Market에서 판매를 시작해 보세요
            </h1>

            <p className="seller-apply-description">
              스토어 정보를 입력해 판매자 신청을 완료하면 관리자 심사 후 상품을
              등록하고 판매할 수 있습니다.
            </p>
          </header>

          {latestApplication?.status === "REJECTED" && (
            <section className="seller-apply-rejection">
              <div className="seller-apply-rejection-heading">
                <span className="seller-apply-rejection-badge">
                  재신청 안내
                </span>

                <h2>이전 신청 내용을 보완해 주세요.</h2>
              </div>

              <div className="seller-apply-rejection-reason">
                <p className="seller-apply-rejection-label">거절 사유</p>

                <p>
                  {latestApplication.rejectionReason ??
                    "관리자 심사 결과 신청이 반려되었습니다."}
                </p>
              </div>
            </section>
          )}

          {pageError && (
            <div className="seller-apply-error" role="alert">
              {pageError}
            </div>
          )}

          <div className="seller-apply-layout">
            <form
              className="seller-apply-form"
              onSubmit={handleSubmit}
              noValidate
            >
              <section className="seller-apply-form-section">
                <div className="seller-apply-section-heading">
                  <span className="seller-apply-section-number">01</span>

                  <div>
                    <h2>스토어 기본 정보</h2>
                    <p>고객에게 표시될 스토어 정보를 입력해 주세요.</p>
                  </div>
                </div>

                <div className="seller-apply-field">
                  <div className="seller-apply-label-row">
                    <label htmlFor="storeName">
                      스토어명 <span aria-hidden="true">*</span>
                    </label>

                    <span>
                      {storeName.length}/{STORE_NAME_MAX_LENGTH}
                    </span>
                  </div>

                  <input
                    id="storeName"
                    className={`seller-apply-input ${
                      storeNameError ? "seller-apply-input-error" : ""
                    }`}
                    type="text"
                    value={storeName}
                    maxLength={STORE_NAME_MAX_LENGTH}
                    placeholder="스토어명을 입력해 주세요."
                    autoComplete="organization"
                    disabled={isSubmitting}
                    aria-invalid={Boolean(storeNameError)}
                    aria-describedby={
                      storeNameError ? "storeNameError" : undefined
                    }
                    onChange={(event) => {
                      setStoreName(event.target.value);

                      if (storeNameError) {
                        setStoreNameError("");
                      }
                    }}
                  />

                  {storeNameError && (
                    <p id="storeNameError" className="seller-apply-field-error">
                      {storeNameError}
                    </p>
                  )}

                  <p className="seller-apply-field-help">
                    승인 후 판매자센터와 상품 페이지에 표시됩니다.
                  </p>
                </div>

                <div className="seller-apply-field">
                  <div className="seller-apply-label-row">
                    <label htmlFor="introduction">스토어 소개</label>

                    <span>
                      {introduction.length}/{INTRODUCTION_MAX_LENGTH}
                    </span>
                  </div>

                  <textarea
                    id="introduction"
                    className="seller-apply-textarea"
                    value={introduction}
                    maxLength={INTRODUCTION_MAX_LENGTH}
                    placeholder="판매할 상품과 스토어의 특징을 소개해 주세요."
                    disabled={isSubmitting}
                    onChange={(event) => {
                      setIntroduction(event.target.value);
                    }}
                  />

                  <p className="seller-apply-field-help">
                    판매 상품과 운영 방향을 구체적으로 작성하면 심사에 도움이
                    됩니다.
                  </p>
                </div>
              </section>

              <section className="seller-apply-agreement">
                <div className="seller-apply-agreement-icon">✓</div>

                <div>
                  <h2>신청 전 확인해 주세요.</h2>

                  <ul>
                    <li>입력한 내용은 관리자 심사에 사용됩니다.</li>
                    <li>심사 결과는 판매자 메뉴에서 확인할 수 있습니다.</li>
                    <li>승인 후 판매자센터에서 상품을 등록할 수 있습니다.</li>
                  </ul>
                </div>
              </section>

              <div className="seller-apply-actions">
                <button
                  type="button"
                  className="seller-apply-cancel-button"
                  disabled={isSubmitting}
                  onClick={() => router.push("/")}
                >
                  취소
                </button>

                <button
                  type="submit"
                  className="seller-apply-submit-button"
                  disabled={isSubmitting}
                >
                  {isSubmitting
                    ? "신청 중..."
                    : latestApplication?.status === "REJECTED"
                      ? "판매자 재신청"
                      : "판매자 신청"}
                </button>
              </div>
            </form>

            <aside className="seller-apply-guide">
              <div className="seller-apply-guide-card">
                <p className="seller-apply-guide-label">입점 절차</p>

                <ol className="seller-apply-steps">
                  <li className="seller-apply-step-active">
                    <span>1</span>

                    <div>
                      <strong>판매자 신청</strong>
                      <p>스토어 정보를 작성합니다.</p>
                    </div>
                  </li>

                  <li>
                    <span>2</span>

                    <div>
                      <strong>관리자 심사</strong>
                      <p>신청 내용을 검토합니다.</p>
                    </div>
                  </li>

                  <li>
                    <span>3</span>

                    <div>
                      <strong>판매 시작</strong>
                      <p>상품을 등록하고 판매합니다.</p>
                    </div>
                  </li>
                </ol>
              </div>

              <div className="seller-apply-guide-notice">
                <strong>정확한 정보를 작성해 주세요.</strong>

                <p>
                  부정확하거나 확인하기 어려운 내용은 심사 과정에서 반려될 수
                  있습니다.
                </p>
              </div>
            </aside>
          </div>
        </div>
      </div>
    </main>
  );
}
