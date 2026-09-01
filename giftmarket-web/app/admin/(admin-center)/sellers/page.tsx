"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useCallback, useEffect, useState } from "react";

import Pagination from "@/components/common/Pagination";
import { getAdminSellers } from "@/lib/admin-api";
import type { AdminSellerPage, AdminSellerStatus } from "@/types/admin";

const PAGE_SIZE = 20;
const statuses: AdminSellerStatus[] = ["ACTIVE", "SALES_SUSPENDED", "SUSPENDED", "WITHDRAWN"];
const statusLabel: Record<AdminSellerStatus, string> = { ACTIVE: "정상", SALES_SUSPENDED: "판매 정지", SUSPENDED: "계정 정지", WITHDRAWN: "탈퇴" };

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(value));
}

function SellerSearch({ initialKeyword, onSearch }: { initialKeyword: string; onSearch: (value: string) => void }) {
  const [value, setValue] = useState(initialKeyword);
  return <form className="admin-user-search" onSubmit={(event: FormEvent) => { event.preventDefault(); onSearch(value.trim()); }}><label className="admin-user-search-input"><span className="sr-only">판매자 검색</span><input value={value} onChange={(event) => setValue(event.target.value)} placeholder="스토어명, 회원 이름 또는 이메일 검색" /></label><button type="submit">검색</button></form>;
}

function AdminSellersContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const page = Math.max(0, Number(searchParams.get("page")) || 0);
  const keyword = searchParams.get("keyword")?.trim() ?? "";
  const statusValue = searchParams.get("status");
  const status = statusValue && statuses.includes(statusValue as AdminSellerStatus) ? statusValue as AdminSellerStatus : undefined;
  const [sellerPage, setSellerPage] = useState<AdminSellerPage | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");

  const buildHref = useCallback((changes: Record<string, string | number | undefined>) => {
    const next = new URLSearchParams(searchParams.toString());
    Object.entries(changes).forEach(([key, value]) => {
      if (value === undefined || value === "" || (key === "page" && value === 0)) next.delete(key);
      else next.set(key, String(value));
    });
    const query = next.toString();
    return query ? `/admin/sellers?${query}` : "/admin/sellers";
  }, [searchParams]);

  const loadSellers = useCallback(async () => {
    try {
      setIsLoading(true);
      setError("");
      const result = await getAdminSellers({ page, size: PAGE_SIZE, keyword: keyword || undefined, status });
      if (page > 0 && (result.totalPages === 0 || page >= result.totalPages)) {
        router.replace(buildHref({ page: Math.max(0, result.totalPages - 1) }), { scroll: false });
        return;
      }
      setSellerPage(result);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "판매자 목록을 불러오지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  }, [buildHref, keyword, page, router, status]);

  useEffect(() => {
    // URL 검색 조건에 맞춰 판매자 목록을 동기화한다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadSellers();
  }, [loadSellers]);

  return <main className="admin-users-page admin-sellers-page">
    <header className="admin-users-header"><div><p>SELLER MANAGEMENT</p><h1>판매자 관리</h1><span>생성 완료된 판매자의 운영 상태를 조회합니다.</span></div><div><small>검색 결과</small><strong>{isLoading || error || !sellerPage ? "-" : sellerPage.totalElements.toLocaleString("ko-KR")}</strong><span>개</span></div></header>
    <section className="admin-user-filter-panel" aria-label="판매자 검색 및 필터"><SellerSearch key={keyword} initialKeyword={keyword} onSearch={(value) => router.push(buildHref({ keyword: value || undefined, page: 0 }), { scroll: false })} /><div className="admin-user-filters"><label><span>상태</span><select value={status ?? ""} onChange={(event) => router.push(buildHref({ status: event.target.value || undefined, page: 0 }), { scroll: false })}><option value="">전체</option>{statuses.map((value) => <option key={value} value={value}>{statusLabel[value]}</option>)}</select></label></div></section>
    {error && <div className="admin-dashboard-error" role="alert"><span>{error}</span><button type="button" onClick={loadSellers}>다시 시도</button></div>}
    <section className="admin-user-list-section"><div className="admin-user-list-heading"><h2>판매자 목록</h2>{sellerPage && !error && <span>총 {sellerPage.totalElements.toLocaleString("ko-KR")}개</span>}</div>
      {isLoading && !sellerPage ? <div className="admin-user-state">판매자 목록을 불러오고 있습니다.</div> : sellerPage?.content.length ? <div className="admin-user-table-wrap"><table className="admin-user-table admin-seller-table"><thead><tr><th>스토어</th><th>판매자 회원</th><th>이메일</th><th>상태</th><th>판매중 상품</th><th>등록일</th><th><span className="sr-only">상세</span></th></tr></thead><tbody>{sellerPage.content.map((seller) => <tr key={seller.sellerId}><td data-label="스토어"><Link href={`/admin/sellers/${seller.sellerId}`} className="admin-user-name"><span>{seller.storeName.slice(0, 1)}</span><strong>{seller.storeName}</strong></Link></td><td data-label="판매자 회원"><Link href={`/admin/users/${seller.userId}`} className="admin-seller-owner-link">{seller.userName} · #{seller.userId}</Link></td><td data-label="이메일">{seller.userEmail ?? "-"}</td><td data-label="상태"><span className={`admin-user-status admin-user-status-${seller.status.toLowerCase()}`}>{statusLabel[seller.status]}</span></td><td data-label="판매중 상품">{seller.onSaleProductCount.toLocaleString("ko-KR")}개</td><td data-label="등록일">{formatDate(seller.createdAt)}</td><td><Link href={`/admin/sellers/${seller.sellerId}`} className="admin-user-detail-link">상세</Link></td></tr>)}</tbody></table></div> : <div className="admin-user-state"><strong>조건에 맞는 판매자가 없습니다.</strong><span>검색어나 상태 조건을 변경해 보세요.</span></div>}
      <Pagination currentPage={sellerPage?.page ?? page} totalPages={sellerPage?.totalPages ?? 0} ariaLabel="판매자 목록 페이지" mode="numbers" disabled={isLoading} getPageHref={(target) => buildHref({ page: target })} scroll={false} className="admin-user-pagination" />
    </section>
  </main>;
}

export default function AdminSellersPage() {
  return <Suspense fallback={<div className="admin-user-state">판매자 목록을 준비하고 있습니다.</div>}><AdminSellersContent /></Suspense>;
}
