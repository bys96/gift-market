"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { getSellerDashboard } from "@/lib/seller-dashboard-api";
import { useAuthStore } from "@/stores/auth-store";
import type { SellerDashboard } from "@/types/seller-dashboard";
import { SELLER_ORDER_STATUS_LABEL } from "@/types/seller-order";

function formatDate(value: string | null): string {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function formatPrice(value: number): string {
  return `${value.toLocaleString("ko-KR")}원`;
}

interface ActionCardProps {
  label: string;
  description: string;
  count: number;
  href: string;
  details?: string;
}

function ActionCard({ label, description, count, href, details }: ActionCardProps) {
  return (
    <Link
      href={href}
      className={`seller-dashboard-action-card ${count > 0 ? "is-required" : ""}`}
    >
      <div>
        <span className="seller-dashboard-action-label">{label}</span>
        <strong className="seller-dashboard-action-count">{count}</strong>
      </div>
      <p>{count > 0 ? description : "현재 처리할 항목이 없습니다."}</p>
      {count > 0 && details && <small>{details}</small>}
      <span className="seller-dashboard-action-link">관리 화면으로 이동</span>
    </Link>
  );
}

export default function SellerDashboardPage() {
  const router = useRouter();
  const initialized = useAuthStore((state) => state.initialized);
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [dashboard, setDashboard] = useState<SellerDashboard | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  const loadDashboard = useCallback(async (refresh = false) => {
    if (refresh) setIsRefreshing(true);
    else setIsLoading(true);
    setErrorMessage("");
    try {
      setDashboard(await getSellerDashboard());
    } catch {
      setErrorMessage("대시보드 정보를 불러오지 못했습니다.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    if (!initialized) return;
    if (!isAuthenticated || !user) {
      router.replace("/login");
      return;
    }
    if (user.role !== "SELLER") {
      router.replace("/seller");
      return;
    }
    // 인증 초기화가 완료된 시점에 서버 집계 데이터를 조회하는 의도된 effect입니다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadDashboard();
  }, [initialized, isAuthenticated, loadDashboard, router, user]);

  if (!initialized || isLoading) {
    return (
      <main className="seller-dashboard-page">
        <div className="common-inner">
          <div className="seller-orders-auth-loading">
            판매자 대시보드 정보를 불러오고 있습니다.
          </div>
        </div>
      </main>
    );
  }

  if (!dashboard) {
    return (
      <main className="seller-dashboard-page">
        <div className="common-inner">
          <section className="seller-dashboard-load-error">
            <strong>대시보드 정보를 불러오지 못했습니다.</strong>
            <p>잠시 후 다시 시도해주세요.</p>
            <button type="button" onClick={() => void loadDashboard()}>
              다시 시도
            </button>
          </section>
        </div>
      </main>
    );
  }

  const returns = dashboard.actionRequired.returns;
  const exchanges = dashboard.actionRequired.exchanges;

  return (
    <main className="seller-dashboard-page">
      <div className="common-inner">
        <div className="seller-dashboard-container">
          <header className="seller-dashboard-header">
            <div>
              <p className="seller-dashboard-header-label">SELLER DASHBOARD</p>
              <h1 className="seller-dashboard-title">판매자 대시보드</h1>
              <p className="seller-dashboard-description">
                지금 처리할 주문과 클레임, 상품 운영 현황을 확인합니다.
              </p>
            </div>
            <div className="seller-dashboard-header-actions">
              <div className="seller-dashboard-store">
                <div className="seller-dashboard-store-icon">
                  {dashboard.storeName.charAt(0)}
                </div>
                <div><strong>{dashboard.storeName}</strong><span>정상 운영 중</span></div>
              </div>
              <button
                type="button"
                className="seller-dashboard-refresh"
                disabled={isRefreshing}
                onClick={() => void loadDashboard(true)}
              >
                {isRefreshing ? "새로고침 중" : "새로고침"}
              </button>
            </div>
          </header>

          {errorMessage && (
            <p className="seller-dashboard-inline-error" role="alert">
              최신 정보를 불러오지 못했습니다. 기존 정보를 표시합니다.
            </p>
          )}

          <section className="seller-dashboard-section" aria-labelledby="action-center-title">
            <div className="seller-dashboard-section-heading">
              <div><p>오늘의 업무</p><h2 id="action-center-title">처리 필요</h2></div>
              <span>판매자가 직접 처리할 수 있는 항목만 집계합니다.</span>
            </div>
            <div className="seller-dashboard-actions">
              <ActionCard label="배송 처리" count={dashboard.actionRequired.orders} description="상품 준비, 발송 또는 배송 완료 처리가 필요합니다." href="/seller/orders" />
              <ActionCard label="취소 요청" count={dashboard.actionRequired.cancellations} description="승인 또는 거절할 취소 요청이 있습니다." href="/seller/orders/cancellations" />
              <ActionCard label="반품 처리" count={returns.total} description="승인, 회수, 입고 또는 검수가 필요합니다." details={`승인 ${returns.approvalRequired} · 회수 ${returns.collectionRequired} · 입고/검수 ${returns.receivingRequired + returns.inspectionRequired}`} href="/seller/orders/returns" />
              <ActionCard label="교환 처리" count={exchanges.total} description="승인, 회수, 검수 또는 재배송 처리가 필요합니다." details={`승인 ${exchanges.approvalRequired} · 회수/입고 ${exchanges.collectionOrReceivingRequired} · 검수/발송 ${exchanges.inspectionRequired + exchanges.outboundRequired}`} href="/seller/orders/exchanges" />
            </div>
          </section>

          <div className="seller-dashboard-grid">
            <section className="seller-dashboard-panel">
              <header className="seller-dashboard-panel-header">
                <div><p>ORDER</p><h2 className="seller-dashboard-panel-title">최근 주문</h2></div>
                <Link href="/seller/orders" className="seller-dashboard-panel-link">주문 전체보기</Link>
              </header>
              {dashboard.recentOrders.length === 0 ? (
                <div className="seller-dashboard-empty"><strong>아직 확인할 주문이 없습니다.</strong><p>결제가 완료된 주문부터 최근 순서대로 표시됩니다.</p></div>
              ) : (
                <div className="seller-dashboard-order-table-wrap">
                  <table className="seller-dashboard-order-table">
                    <thead><tr><th>주문</th><th>상품</th><th>주문금액</th><th>상태</th><th>요청일</th></tr></thead>
                    <tbody>
                      {dashboard.recentOrders.map((order) => (
                        <tr key={order.sellerOrderId}>
                          <td data-label="주문"><Link href={`/seller/orders/${order.sellerOrderId}`}>{order.orderNumber}</Link></td>
                          <td data-label="상품"><strong>{order.representativeProductName}</strong>{order.additionalProductCount > 0 && <small> 외 {order.additionalProductCount}종</small>}<span>총 {order.totalQuantity}개</span></td>
                          <td data-label="주문금액">{formatPrice(order.totalProductAmount)}</td>
                          <td data-label="상태"><span className={`seller-order-status seller-order-status-${order.status.toLowerCase()}`}>{SELLER_ORDER_STATUS_LABEL[order.status]}</span></td>
                          <td data-label="요청일">{formatDate(order.orderedAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <section className="seller-dashboard-panel seller-dashboard-products">
              <header className="seller-dashboard-panel-header">
                <div><p>PRODUCT</p><h2 className="seller-dashboard-panel-title">상품 현황</h2></div>
                <Link href="/seller/products" className="seller-dashboard-panel-link">상품 관리</Link>
              </header>
              <div className="seller-dashboard-product-summary">
                <Link href="/seller/products"><span>판매 중</span><strong>{dashboard.products.onSale}<small>개</small></strong></Link>
                <Link href="/seller/products"><span>품절</span><strong>{dashboard.products.soldOut}<small>개</small></strong></Link>
              </div>
              <button type="button" onClick={() => router.push("/seller/products/new")}>새 상품 등록</button>
            </section>
          </div>
        </div>
      </div>
    </main>
  );
}
