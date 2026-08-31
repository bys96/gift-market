"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { getAdminDashboard } from "@/lib/admin-api";
import type { AdminDashboard, AdminOrderStatus, AdminSellerApplicationStatus } from "@/types/admin";

const orderStatusLabel: Record<AdminOrderStatus, string> = {
  ORDERED: "주문 접수",
  PENDING_PAYMENT: "결제 대기",
  PAID: "결제 완료",
  PAYMENT_FAILED: "결제 실패",
  PAYMENT_EXPIRED: "결제 만료",
  CANCELLED: "주문 취소",
};

const applicationStatusLabel: Record<AdminSellerApplicationStatus, string> = {
  PENDING: "심사 대기",
  APPROVED: "승인",
  REJECTED: "거절",
};

function formatDateTime(value: string | null) {
  if (!value) return "-";
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit",
  }).format(new Date(value));
}

function formatPrice(value: number) {
  return `${value.toLocaleString("ko-KR")}원`;
}

export default function AdminDashboardPage() {
  const [dashboard, setDashboard] = useState<AdminDashboard | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  const loadDashboard = useCallback(async () => {
    try {
      setIsLoading(true);
      setError("");
      setDashboard(await getAdminDashboard());
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "관리자 대시보드를 불러오지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // 최초 진입 시 서버 Dashboard 상태를 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadDashboard();
  }, [loadDashboard]);

  const number = (value: number | undefined) => isLoading || error || value === undefined ? "-" : value.toLocaleString("ko-KR");

  return (
    <main className="admin-dashboard">
      <header className="admin-dashboard-header">
        <div><p>ADMIN CENTER</p><h1>관리자 대시보드</h1><span>Gift Market의 주요 운영 현황을 한눈에 확인합니다.</span></div>
        <time>{new Intl.DateTimeFormat("ko-KR", { dateStyle: "long" }).format(new Date())}</time>
      </header>

      {error && <div className="admin-dashboard-error" role="alert"><span>{error}</span><button type="button" onClick={loadDashboard}>다시 시도</button></div>}

      <section className="admin-dashboard-section">
        <div className="admin-dashboard-section-title"><div><p>ACTION CENTER</p><h2>오늘 관리할 항목</h2></div><span>플랫폼 전체 접수 현황</span></div>
        <div className="admin-action-grid">
          <Link href="/admin/seller-applications" className="admin-action-card admin-action-card-primary"><span>판매자 신청 대기</span><strong>{number(dashboard?.actionCenter.pendingSellerApplications)}</strong><small>신청 목록으로 이동 →</small></Link>
          <div className="admin-action-card"><span>취소 처리 대기</span><strong>{number(dashboard?.actionCenter.pendingCancellations)}</strong><small>관리 기능 준비 중</small></div>
          <div className="admin-action-card"><span>반품 처리 대기</span><strong>{number(dashboard?.actionCenter.pendingReturns)}</strong><small>관리 기능 준비 중</small></div>
          <div className="admin-action-card"><span>교환 처리 대기</span><strong>{number(dashboard?.actionCenter.pendingExchanges)}</strong><small>관리 기능 준비 중</small></div>
        </div>
      </section>

      <section className="admin-dashboard-section">
        <div className="admin-dashboard-section-title"><div><p>SERVICE SUMMARY</p><h2>서비스 현황</h2></div></div>
        <dl className="admin-summary-grid">
          <div><dt>전체 회원</dt><dd>{number(dashboard?.summary.totalUsers)}</dd><small>탈퇴 회원 제외</small></div>
          <div><dt>활성 판매자</dt><dd>{number(dashboard?.summary.activeSellers)}</dd><small>ACTIVE 상태</small></div>
          <div><dt>판매중 상품</dt><dd>{number(dashboard?.summary.sellingProducts)}</dd><small>삭제되지 않은 ON_SALE</small></div>
          <div><dt>전체 주문</dt><dd>{number(dashboard?.summary.totalOrders)}</dd><small>누적 주문</small></div>
        </dl>
      </section>

      <div className="admin-recent-grid">
        <section className="admin-dashboard-section admin-recent-section">
          <div className="admin-dashboard-section-title"><div><p>RECENT ORDERS</p><h2>최근 주문</h2></div><span>최신 5건</span></div>
          <div className="admin-recent-list">
            {isLoading && !dashboard ? <p className="admin-recent-state">불러오는 중입니다.</p> : dashboard?.recentOrders.length ? dashboard.recentOrders.map((order) => (
              <article key={order.id} className="admin-recent-row"><div><strong>{order.orderNumber}</strong><span>{formatDateTime(order.orderedAt ?? order.createdAt)}</span></div><div><strong>{formatPrice(order.totalAmount)}</strong><span>{orderStatusLabel[order.status]}</span></div></article>
            )) : <p className="admin-recent-state">최근 주문이 없습니다.</p>}
          </div>
        </section>
        <section className="admin-dashboard-section admin-recent-section">
          <div className="admin-dashboard-section-title"><div><p>SELLER APPLICATIONS</p><h2>최근 판매자 신청</h2></div><Link href="/admin/seller-applications">전체 보기</Link></div>
          <div className="admin-recent-list">
            {isLoading && !dashboard ? <p className="admin-recent-state">불러오는 중입니다.</p> : dashboard?.recentSellerApplications.length ? dashboard.recentSellerApplications.map((application) => (
              <article key={application.id} className="admin-recent-row"><div><strong>{application.storeName}</strong><span>{application.applicantName} · {formatDateTime(application.createdAt)}</span></div><span className={`admin-application-status admin-application-status-${application.status.toLowerCase()}`}>{applicationStatusLabel[application.status]}</span></article>
            )) : <p className="admin-recent-state">최근 판매자 신청이 없습니다.</p>}
          </div>
        </section>
      </div>
    </main>
  );
}
