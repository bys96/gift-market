"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

import { getMyLatestSellerApplication, getMySeller } from "@/lib/seller-api";
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

export default function SellerApplicationPage() {
  const router = useRouter();

  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const [application, setApplication] = useState<SellerApplication | null>(
    null,
  );
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    if (!initialized) {
      return;
    }

    if (!isAuthenticated || !user) {
      router.replace("/login");
      return;
    }

    const loadApplication = async () => {
      try {
        setIsLoading(true);
        setErrorMessage("");

        const seller = await getMySeller();
        if (seller?.status === "ACTIVE" || seller?.status === "SALES_SUSPENDED") {
          router.replace("/seller/dashboard");
          return;
        }
        if (seller) {
          setErrorMessage("현재 판매자 상태에서는 판매자센터를 이용할 수 없습니다.");
          return;
        }

        const latestApplication = await getMyLatestSellerApplication();

        if (!latestApplication) {
          router.replace("/seller/apply");
          return;
        }

        if (latestApplication.status === "REJECTED") {
          router.replace("/seller/apply");
          return;
        }

        if (latestApplication.status === "APPROVED") {
          router.replace("/seller/dashboard");
          return;
        }

        setApplication(latestApplication);
      } catch (error) {
        setErrorMessage(
          error instanceof Error
            ? error.message
            : "판매자 신청 정보를 불러오지 못했습니다.",
        );
      } finally {
        setIsLoading(false);
      }
    };

    loadApplication();
  }, [initialized, isAuthenticated, user, router]);

  if (
    !initialized ||
    !isAuthenticated ||
    !user ||
    isLoading
  ) {
    return (
      <main className="seller-application-page">
        <div className="common-inner">
          <div className="seller-application-loading">
            <span className="seller-application-loading-spinner" />

            <p>판매자 신청 상태를 확인하고 있습니다.</p>
          </div>
        </div>
      </main>
    );
  }

  if (errorMessage) {
    return (
      <main className="seller-application-page">
        <div className="common-inner">
          <section className="seller-application-error">
            <h1>신청 정보를 확인하지 못했습니다.</h1>

            <p>{errorMessage}</p>

            <button
              type="button"
              onClick={() => {
                window.location.reload();
              }}
            >
              다시 시도
            </button>
          </section>
        </div>
      </main>
    );
  }

  if (!application) {
    return null;
  }

  return (
    <main className="seller-application-page">
      <div className="common-inner">
        <div className="seller-application-container">
          <header className="seller-application-header">
            <p className="seller-application-header-label">
              SELLER APPLICATION
            </p>

            <h1 className="seller-application-title">판매자 신청 현황</h1>

            <p className="seller-application-description">
              제출한 판매자 신청 내용과 현재 심사 상태를 확인할 수 있습니다.
            </p>
          </header>

          <section className="seller-application-status-card">
            <div className="seller-application-status-hero">
              <div className="seller-application-status-icon">···</div>

              <span className="seller-application-status-badge">심사 대기</span>

              <h2 className="seller-application-status-title">
                판매자 신청을 검토하고 있습니다.
              </h2>

              <p className="seller-application-status-description">
                관리자 심사가 완료되면 판매자 권한이 부여됩니다. 심사 결과는
                판매자 메뉴에서 다시 확인할 수 있습니다.
              </p>
            </div>

            <div className="seller-application-detail">
              <h3 className="seller-application-detail-title">신청 정보</h3>

              <dl className="seller-application-detail-list">
                <div className="seller-application-detail-item">
                  <dt>신청 번호</dt>
                  <dd>#{application.id}</dd>
                </div>

                <div className="seller-application-detail-item">
                  <dt>신청일</dt>
                  <dd>{formatDateTime(application.createdAt)}</dd>
                </div>

                <div className="seller-application-detail-item">
                  <dt>스토어명</dt>
                  <dd>{application.storeName}</dd>
                </div>

                <div className="seller-application-detail-item">
                  <dt>심사 상태</dt>
                  <dd>심사 대기</dd>
                </div>

                <div className="seller-application-detail-item seller-application-detail-item-full">
                  <dt>스토어 소개</dt>

                  <dd>
                    {application.introduction ||
                      "등록된 스토어 소개가 없습니다."}
                  </dd>
                </div>
              </dl>
            </div>
          </section>

          <section
            className="seller-application-process"
            aria-label="판매자 입점 진행 단계"
          >
            <div className="seller-application-process-item seller-application-process-item-active">
              <span className="seller-application-process-number">1</span>

              <div className="seller-application-process-content">
                <strong>신청 완료</strong>
                <p>판매자 신청이 정상적으로 접수되었습니다.</p>
              </div>
            </div>

            <div className="seller-application-process-item seller-application-process-item-active">
              <span className="seller-application-process-number">2</span>

              <div className="seller-application-process-content">
                <strong>관리자 심사</strong>
                <p>제출한 스토어 정보를 검토하고 있습니다.</p>
              </div>
            </div>

            <div className="seller-application-process-item">
              <span className="seller-application-process-number">3</span>

              <div className="seller-application-process-content">
                <strong>판매 시작</strong>
                <p>승인 후 판매자센터를 이용할 수 있습니다.</p>
              </div>
            </div>
          </section>

          <div className="seller-application-actions">
            <button
              type="button"
              className="seller-application-home-button"
              onClick={() => router.push("/")}
            >
              홈으로
            </button>
          </div>
        </div>
      </div>
    </main>
  );
}
