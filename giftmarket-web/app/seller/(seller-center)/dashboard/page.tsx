"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

import { getMySeller } from "@/lib/seller-api";
import { useAuthStore } from "@/stores/auth-store";
import type { Seller } from "@/types/seller";

interface SellerDashboardSummary {
  productCount: number;
  orderCount: number;
  salesAmount: number;
  inquiryCount: number;
}

const INITIAL_SUMMARY: SellerDashboardSummary = {
  productCount: 0,
  orderCount: 0,
  salesAmount: 0,
  inquiryCount: 0,
};

function formatPrice(price: number): string {
  return new Intl.NumberFormat("ko-KR").format(price);
}

export default function SellerDashboardPage() {
  const router = useRouter();

  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  const [seller, setSeller] = useState<Seller | null>(null);
  const [summary] = useState<SellerDashboardSummary>(INITIAL_SUMMARY);
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

    if (user.role !== "SELLER" && user.role !== "ADMIN") {
      router.replace("/seller");
      return;
    }

    const loadSeller = async () => {
      try {
        setIsLoading(true);
        setErrorMessage("");

        const sellerResponse = await getMySeller();

        setSeller(sellerResponse);
      } catch (error) {
        setErrorMessage(
          error instanceof Error
            ? error.message
            : "판매자 정보를 불러오지 못했습니다.",
        );
      } finally {
        setIsLoading(false);
      }
    };

    loadSeller();
  }, [initialized, isAuthenticated, user, router]);

  if (
    !initialized ||
    !isAuthenticated ||
    !user ||
    (user.role !== "SELLER" && user.role !== "ADMIN") ||
    isLoading
  ) {
    return (
      <main className="seller-dashboard-page">
        <div className="common-inner">
          <div className="seller-application-loading">
            <span className="seller-application-loading-spinner" />
            <p>판매자센터 정보를 불러오고 있습니다.</p>
          </div>
        </div>
      </main>
    );
  }

  if (errorMessage || !seller) {
    return (
      <main className="seller-dashboard-page">
        <div className="common-inner">
          <section className="seller-application-error">
            <h1>판매자 정보를 확인하지 못했습니다.</h1>

            <p>
              {errorMessage || "현재 계정에 연결된 판매자 정보가 없습니다."}
            </p>

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

  return (
    <main className="seller-dashboard-page">
      <div className="common-inner">
        <div className="seller-dashboard-container">
          <header className="seller-dashboard-header">
            <div>
              <p className="seller-dashboard-header-label">SELLER CENTER</p>

              <h1 className="seller-dashboard-title">판매자센터</h1>

              <p className="seller-dashboard-description">
                상품과 주문, 문의 및 정산 현황을 관리할 수 있습니다.
              </p>
            </div>

            <div className="seller-dashboard-store">
              <div className="seller-dashboard-store-icon">
                {seller.storeName.charAt(0)}
              </div>

              <div>
                <strong>{seller.storeName}</strong>
                <span>
                  {seller.status === "ACTIVE"
                    ? "정상 운영 중"
                    : seller.status === "SUSPENDED"
                      ? "운영 정지"
                      : "판매자 탈퇴"}
                </span>
              </div>
            </div>
          </header>

          <section
            className="seller-dashboard-summary"
            aria-label="판매 현황 요약"
          >
            <article className="seller-dashboard-summary-card">
              <p className="seller-dashboard-summary-label">등록 상품</p>

              <strong className="seller-dashboard-summary-value">
                {summary.productCount}
                <span className="seller-dashboard-summary-unit">개</span>
              </strong>
            </article>

            <article className="seller-dashboard-summary-card">
              <p className="seller-dashboard-summary-label">신규 주문</p>

              <strong className="seller-dashboard-summary-value">
                {summary.orderCount}
                <span className="seller-dashboard-summary-unit">건</span>
              </strong>
            </article>

            <article className="seller-dashboard-summary-card">
              <p className="seller-dashboard-summary-label">총 판매 금액</p>

              <strong className="seller-dashboard-summary-value">
                {formatPrice(summary.salesAmount)}
                <span className="seller-dashboard-summary-unit">원</span>
              </strong>
            </article>

            <article className="seller-dashboard-summary-card">
              <p className="seller-dashboard-summary-label">미답변 문의</p>

              <strong className="seller-dashboard-summary-value">
                {summary.inquiryCount}
                <span className="seller-dashboard-summary-unit">건</span>
              </strong>
            </article>
          </section>

          <div className="seller-dashboard-grid">
            <section className="seller-dashboard-panel">
              <header className="seller-dashboard-panel-header">
                <h2 className="seller-dashboard-panel-title">최근 주문</h2>

                <button
                  type="button"
                  className="seller-dashboard-panel-link"
                  onClick={() => router.push("/seller/orders")}
                >
                  전체 보기
                </button>
              </header>

              <div className="seller-dashboard-empty">
                <div className="seller-dashboard-empty-icon">✓</div>

                <strong>아직 접수된 주문이 없습니다.</strong>

                <p>
                  상품을 등록하고 판매를 시작하면 최근 주문이 이곳에 표시됩니다.
                </p>
              </div>
            </section>

            <section className="seller-dashboard-panel">
              <header className="seller-dashboard-panel-header">
                <h2 className="seller-dashboard-panel-title">빠른 메뉴</h2>
              </header>

              <div className="seller-dashboard-quick-menu">
                <button
                  type="button"
                  onClick={() => router.push("/seller/products/new")}
                >
                  <strong>상품 등록</strong>
                  <span aria-hidden="true">›</span>
                </button>

                <button
                  type="button"
                  onClick={() => router.push("/seller/products")}
                >
                  <strong>상품 관리</strong>
                  <span aria-hidden="true">›</span>
                </button>

                <button
                  type="button"
                  onClick={() => router.push("/seller/orders")}
                >
                  <strong>주문 관리</strong>
                  <span aria-hidden="true">›</span>
                </button>

                <button
                  type="button"
                  onClick={() => router.push("/seller/inquiries")}
                >
                  <strong>문의 관리</strong>
                  <span aria-hidden="true">›</span>
                </button>

                <button
                  type="button"
                  onClick={() => router.push("/seller/settings")}
                >
                  <strong>스토어 설정</strong>
                  <span aria-hidden="true">›</span>
                </button>
              </div>
            </section>
          </div>
        </div>
      </div>
    </main>
  );
}
